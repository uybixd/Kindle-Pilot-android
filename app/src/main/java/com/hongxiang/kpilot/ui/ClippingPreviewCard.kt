package com.hongxiang.kpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as ContentText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.i18n.localizedText
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.ui.theme.KPilotTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val clippingDate = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault())

/** A short reading preview; the complete text and reference remain in the detail view. */
@Composable
internal fun ClippingPreviewCard(
    note: KindleClipping,
    showBook: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                note.text.ifBlank { note.metadata },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 30.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (showBook || note.addedAt != null || note.kindleNotes.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (showBook) Text(
                            note.bookTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // The date helps orientation within a book, without adding another line to every card.
                        if (!showBook) note.addedAt?.let {
                            Text(clippingDate.format(it), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (note.kindleNotes.isNotEmpty()) Icon(
                        Icons.AutoMirrored.Filled.Notes,
                        contentDescription = localizedText("含批注"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
internal fun ClippingPreviewRow(
    note: KindleClipping,
    showBook: Boolean,
    hasAnnotation: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val date = remember(note.addedAt, locale, zone) {
        note.addedAt?.let {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(it)
        }
    }

    ReadingListRow(first = first, last = last, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContentText(
                note.text.ifBlank { note.metadata },
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (hasAnnotation) Icon(
                Icons.Filled.ChatBubble,
                contentDescription = localizedText("含批注"),
                modifier = Modifier.padding(top = 4.dp).size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        if (showBook || date != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBook) ContentText(
                    note.bookTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = readingListSecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                date?.let {
                    ContentText(it, style = MaterialTheme.typography.bodySmall, color = readingListSecondaryText, maxLines = 1)
                }
            }
        }
    }
}

@Preview(name = "Clippings · grouped list", locale = "zh", widthDp = 390, showBackground = true)
@Preview(name = "Clippings · large text", locale = "en", widthDp = 320, fontScale = 1.5f, showBackground = true)
@Composable
private fun ClippingRowsPreview() {
    val note = KindleClipping(
        id = "preview", bookId = "preview", bookTitle = "阅读随记 · 关于日常生活的观察", author = null,
        kind = ClippingKind.HIGHLIGHT, location = "128", locationStart = 128, locationEnd = 128,
        page = null, addedAt = Instant.parse("2026-11-10T03:00:00Z"), metadata = "", raw = "",
        text = "把阅读的脚步放慢一些，让一段文字在心里多停留片刻。那些细小的发现，常常藏在我们匆匆翻过的一页里。"
    )
    KPilotTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(readingListInset)) {
                ClippingPreviewRow(note, showBook = true, hasAnnotation = true, first = true, last = false, onClick = {})
                ClippingPreviewRow(
                    note.copy(bookTitle = "A reading journal",
                        text = "Leave a little room between one thought and the next. A quiet page can make a familiar idea feel new again."),
                    showBook = true, hasAnnotation = false, first = false, last = true, onClick = {}
                )
            }
        }
    }
}
