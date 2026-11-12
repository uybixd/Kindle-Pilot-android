package com.hongxiang.kpilot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.review.ClippingReviewRecord
import com.hongxiang.kpilot.ui.ClippingPreviewCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClippingReviewSettings(
    clippings: List<KindleClipping>,
    records: Map<String, ClippingReviewRecord>,
    enabled: Boolean,
    onOpenClipping: (KindleClipping) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onRestoreHidden: (String) -> Unit
) {
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val visible = remember(clippings, records, showHidden) {
        clippings.mapNotNull { clipping ->
            val record = records[clipping.id] ?: return@mapNotNull null
            if (if (showHidden) record.isExcluded else record.isFavorite) {
                clipping to if (showHidden) record.excludedUpdatedAt else record.favoriteUpdatedAt
            } else null
        }.sortedByDescending { it.second }.map { it.first }
    }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = if (showHidden) 1 else 0) {
            Tab(selected = !showHidden, onClick = { showHidden = false }, text = { Text("收藏") })
            Tab(selected = showHidden, onClick = { showHidden = true }, text = { Text("已隐藏") })
        }
        if (visible.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (showHidden) "还没有隐藏的摘抄" else "还没有收藏的摘抄",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else key(showHidden) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visible, key = { it.id }) { clipping ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column {
                            ClippingPreviewCard(clipping, showBook = true, onClick = { onOpenClipping(clipping) })
                            TextButton(
                                onClick = { if (showHidden) onRestoreHidden(clipping.id) else onRemoveFavorite(clipping.id) },
                                enabled = enabled,
                                modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 4.dp)
                            ) {
                                Text(if (showHidden) "恢复回顾" else "取消收藏")
                            }
                        }
                    }
                }
            }
        }
    }
}
