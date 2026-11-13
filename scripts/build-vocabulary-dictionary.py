#!/usr/bin/env python3
"""Build the bundled English glossary from two Wiktionary extracts."""

import argparse
from collections import defaultdict
import gzip
import json
from pathlib import Path
import re
import unicodedata
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


SIMPLE_POS = {
    "adj": "adjective", "adv": "adverb", "article": "article", "conj": "conjunction",
    "contraction": "contraction", "det": "determiner", "intj": "interjection",
    "noun": "noun", "num": "numeral", "particle": "particle", "phrase": "phrase",
    "prep": "preposition", "pron": "pronoun", "verb": "verb",
}
UNCOMMON_TAGS = {"archaic", "dated", "no-gloss", "obsolete", "rare"}
IPA = re.compile(r"/[^/\[\]]+/|\[[^/\[\]]+\]")
FORM_GLOSS = re.compile(
    r"^(?:alternative (?:[\w-]+ )?(?:form|spelling)|(?:early modern|obsolete|archaic) (?:form|spelling)|"
    r"misspelling|ellipsis|clipping|shortened form) of\b", re.I
)


def clean(text):
    return " ".join(text.split())


def headword(text):
    return unicodedata.normalize("NFC", clean(text).replace("’", "'"))


def pronunciation(text):
    # Some source rows contain the template's language code or a respelling.
    # Keep a complete IPA transcription as marked by the Wiktionary editors.
    match = IPA.match(clean(text))
    return match[0] if match else ""


def common_senses(senses):
    by_pos = defaultdict(list)
    for pos, gloss in senses:
        gloss = clean(gloss)
        if gloss and not FORM_GLOSS.match(gloss) and gloss not in by_pos[pos]:
            by_pos[pos].append(gloss)
    ranked = [(pos, glosses[index]) for index in range(2)
              for pos, glosses in by_pos.items() if index < len(glosses)]
    return ranked[:4]


def english_entries(path):
    with gzip.open(path, "rt", encoding="utf-8") as source:
        for line in source:
            fields = line.rstrip("\n").split("\t")
            if fields[0].startswith("#") or len(fields) < 4 or not fields[2]:
                continue
            word, ipa = headword(fields[1]), pronunciation(fields[2])
            senses = common_senses(sense.split("\x1f")[:2] for sense in fields[3:])
            if word and ipa and senses:
                yield word, ("en", ipa, senses)


def simple_entries(path):
    pronunciations = {}
    american_pronunciations = set()
    senses = defaultdict(list)
    with gzip.open(path, "rt", encoding="utf-8") as source:
        for line in source:
            entry = json.loads(line)
            pos = SIMPLE_POS.get(entry.get("pos"))
            if entry.get("lang_code") != "en" or not pos:
                continue
            word = headword(entry["word"])
            if not word:
                continue
            for sound in entry.get("sounds", []):
                ipa = pronunciation(sound.get("ipa", ""))
                if not ipa:
                    continue
                is_american = "US" in sound.get("tags", [])
                if word not in pronunciations or (is_american and word not in american_pronunciations):
                    pronunciations[word] = ipa
                if is_american:
                    american_pronunciations.add(word)
                    break
            for sense in entry.get("senses", []):
                if not UNCOMMON_TAGS.intersection(sense.get("tags", [])) and not sense.get("form_of"):
                    senses[word].extend((pos, gloss) for gloss in sense.get("glosses", [])[:1])
    for word, ipa in pronunciations.items():
        glosses = common_senses(senses[word])
        if glosses:
            yield word, ("simple", ipa, glosses)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("english", type=Path, help="WM Keyboard's English Wiktionary .wmdict.gz")
    parser.add_argument("simple", type=Path, help="Kaikki's Simple English Wiktionary JSONL.gz")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    entries = dict(english_entries(args.english))
    entries.update(simple_entries(args.simple))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    member = ZipInfo("wiktionary-en.tsv")
    member.compress_type = ZIP_DEFLATED
    with ZipFile(args.output, "w") as archive, archive.open(member, "w") as data:
        data.write(b"# Glosses adapted from Wiktionary contributors (https://en.wiktionary.org, https://simple.wiktionary.org)\n")
        data.write(b"# Selected senses and pronunciations, normalized and repackaged under CC BY-SA 4.0\n")
        data.write(b"# https://creativecommons.org/licenses/by-sa/4.0/\n")
        for word, (edition, ipa, senses) in sorted(entries.items()):
            fields = [word, edition, ipa]
            for pos, gloss in senses:
                fields.extend((pos, gloss))
            data.write(("\t".join(fields) + "\n").encode("utf-8"))
    print(f"Wrote {len(entries):,} entries to {args.output}")


if __name__ == "__main__":
    main()
