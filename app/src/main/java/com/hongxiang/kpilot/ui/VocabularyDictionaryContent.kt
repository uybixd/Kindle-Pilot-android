package com.hongxiang.kpilot.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.i18n.localizedText
import com.hongxiang.kpilot.vocabulary.VocabularyDictionaryEntry

@Composable
internal fun VocabularyDictionaryContent(entry: VocabularyDictionaryEntry, word: String) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val pronunciation = if (entry.headword.equals(word, ignoreCase = true)) entry.ipa
            else "${entry.headword}  ${entry.ipa}"
            Text(pronunciation, style = MaterialTheme.typography.titleMedium)
            for ((partOfSpeech, senses) in entry.senses.groupBy { it.partOfSpeech }) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        partOfSpeech,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    senses.forEach { Text(it.gloss, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }

    val credit = localizedText("Wiktionary 贡献者")
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    val sourceUrl = "https://${entry.edition.domain}/wiki/${Uri.encode(entry.headword)}"
    Text(
        buildAnnotatedString {
            withLink(LinkAnnotation.Url(sourceUrl, linkStyle)) { append(credit) }
            append(" · ")
            withLink(LinkAnnotation.Url("https://creativecommons.org/licenses/by-sa/4.0/", linkStyle)) {
                append("CC BY-SA 4.0")
            }
        },
        style = style
    )
}
