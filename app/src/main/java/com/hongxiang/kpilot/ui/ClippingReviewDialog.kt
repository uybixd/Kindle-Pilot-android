package com.hongxiang.kpilot.ui

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hongxiang.kpilot.UiState
import com.hongxiang.kpilot.i18n.AppText as Text
import com.hongxiang.kpilot.i18n.localizedText
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.review.ClippingReviewAction
import com.hongxiang.kpilot.review.ClippingsReviewPlanner
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

private val reviewDate = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault())
private const val BOOK_CARD_SCALE = 0.88f

@Composable
internal fun ClippingReviewDialog(
    state: UiState,
    onClose: () -> Unit,
    onClippingDisplayed: (String) -> Unit,
    onAction: (ClippingReviewAction, String, Boolean) -> Unit,
    onFocusClipping: (String) -> Unit,
    onUndo: (keepBook: Boolean) -> Boolean,
    onRestore: () -> Unit
) {
    val clippingId = state.clippingReviewDeck.firstOrNull()
    val clipping = remember(clippingId, state.notes) { state.notes.firstOrNull { it.id == clippingId } }
    var browsingBook by rememberSaveable(clipping?.id, state.clippingReviewRevision) { mutableStateOf(false) }
    val back = { if (browsingBook) browsingBook = false else onClose() }

    LaunchedEffect(clipping?.id, state.clippingReviewRevision) {
        clipping?.let { onClippingDisplayed(it.id) }
    }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        key(clipping?.id, state.clippingReviewRevision) {
            val scope = rememberCoroutineScope()
            var cardSize by remember { mutableStateOf(IntSize.Zero) }
            var cardOffset by remember { mutableStateOf(Offset.Zero) }
            var isSettling by remember { mutableStateOf(false) }
            val latestOnAction by rememberUpdatedState(onAction)
            val bookClippings = remember(browsingBook, clipping?.bookId, state.notes) {
                if (browsingBook && clipping != null) {
                    ClippingsReviewPlanner.bookClippings(state.notes, clipping.bookId)
                } else emptyList()
            }
            val pager = if (bookClippings.isNotEmpty()) {
                rememberPagerState(
                    initialPage = bookClippings.indexOfFirst { it.id == clippingId }.coerceAtLeast(0),
                    pageCount = { bookClippings.size }
                )
            } else null

            fun settleCard(destination: Offset, action: ClippingReviewAction? = null): Boolean {
                if (isSettling || browsingBook || clipping == null) return false
                isSettling = true
                scope.launch {
                    animate(
                        typeConverter = Offset.VectorConverter,
                        initialValue = cardOffset,
                        targetValue = destination,
                        animationSpec = tween(180)
                    ) { value, _ -> cardOffset = value }
                    if (action == null) isSettling = false else latestOnAction(action, clipping.id, false)
                }
                return true
            }

            fun openBook(): Boolean {
                if (isSettling || browsingBook || clipping == null) return false
                cardOffset = Offset.Zero
                browsingBook = true
                return true
            }

            fun focusCenteredClipping(): Boolean {
                val centered = bookClippings.getOrNull(pager?.currentPage ?: return false) ?: return false
                browsingBook = false
                onFocusClipping(centered.id)
                return true
            }

            val handlePinch by rememberUpdatedState<(Float) -> Boolean> { zoom ->
                if (zoom < 1f) openBook() else focusCenteredClipping()
            }
            Surface(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectReviewPinch { handlePinch(it) } },
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ClippingReviewHeader(
                        title = if (pager != null) "同书笔记" else "回顾",
                        status = if (pager != null) "${pager.currentPage + 1} / ${bookClippings.size}"
                        else "今日 ${reviewCountToday(state)}",
                        backLabel = if (pager != null) "返回" else "关闭",
                        onBack = back
                    )
                    BoxWithConstraints(
                        Modifier.weight(1f).widthIn(max = 810.dp).fillMaxWidth().padding(top = 12.dp, bottom = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val cardWidth = (maxWidth - 40.dp).coerceIn(0.dp, 720.dp)
                        val cardHeight = maxHeight
                        when {
                            pager != null -> {
                                val focusLabel = localizedText("回到当前笔记")
                                HorizontalPager(
                                    state = pager,
                                    modifier = Modifier.fillMaxWidth().height(cardHeight * BOOK_CARD_SCALE).semantics {
                                        customActions = listOf(CustomAccessibilityAction(focusLabel, ::focusCenteredClipping))
                                    },
                                    key = { bookClippings[it].id },
                                    contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth * BOOK_CARD_SCALE) / 2),
                                    pageSpacing = 10.dp
                                ) { index ->
                                    val note = bookClippings[index]
                                    ClippingReviewCard(
                                        clipping = note,
                                        isFavorite = state.clippingReviewRecords[note.id]?.isFavorite == true,
                                        modifier = Modifier.fillMaxSize(),
                                        inBook = true
                                    )
                                }
                            }
                            clipping != null -> {
                                val nextLabel = localizedText("下一条")
                                val bookLabel = localizedText("查看同书笔记")
                                ClippingReviewCard(
                                    clipping = clipping,
                                    isFavorite = state.clippingReviewRecords[clipping.id]?.isFavorite == true,
                                    modifier = Modifier.size(cardWidth, cardHeight)
                                        .onSizeChanged { cardSize = it }
                                        .pointerInput(Unit) {
                                            val threshold = 104.dp.toPx()
                                            detectDragGestures(
                                                onDrag = { _, amount -> if (!isSettling) cardOffset += amount },
                                                onDragCancel = { if (!isSettling) cardOffset = Offset.Zero },
                                                onDragEnd = {
                                                    val (x, y) = cardOffset
                                                    when {
                                                        abs(x) > abs(y) && abs(x) >= threshold -> settleCard(
                                                            Offset(x.sign * size.width, 0f), ClippingReviewAction.NEXT
                                                        )
                                                        y <= -threshold -> settleCard(
                                                            Offset(0f, -size.height.toFloat()), ClippingReviewAction.NEXT
                                                        )
                                                        else -> settleCard(Offset.Zero)
                                                    }
                                                }
                                            )
                                        }
                                        .semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction(nextLabel) {
                                                    settleCard(Offset(0f, -cardSize.height.toFloat()), ClippingReviewAction.NEXT)
                                                },
                                                CustomAccessibilityAction(bookLabel, ::openBook)
                                            )
                                        }
                                        .graphicsLayer {
                                            translationX = cardOffset.x
                                            translationY = cardOffset.y
                                            val horizontalProgress = cardOffset.x / size.width.coerceAtLeast(1f)
                                            val upwardProgress = -cardOffset.y / size.height.coerceAtLeast(1f)
                                            rotationZ = (horizontalProgress * 16f).coerceIn(-16f, 16f)
                                            alpha = 1f - max(abs(horizontalProgress), upwardProgress).coerceIn(0f, 1f)
                                        }
                                )
                            }
                            else -> EmptyClippingReview(
                                excludedCount = state.clippingReviewRecords.values.count { it.isExcluded },
                                onRestore = onRestore,
                                onClose = onClose,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    val actionClipping = if (pager == null) clipping else bookClippings.getOrNull(pager.currentPage)
                    val undo = state.clippingReviewUndo
                    if (actionClipping != null || undo != null) {
                        val record = state.clippingReviewRecords[actionClipping?.id]
                        ClippingReviewActions(
                            showActions = actionClipping != null,
                            inBook = pager != null,
                            isFavorite = record?.isFavorite == true,
                            isHidden = pager != null && record?.isExcluded == true,
                            enabled = !isSettling && pager?.isScrollInProgress != true,
                            canUndo = undo != null,
                            onFavorite = {
                                if (pager != null && actionClipping != null) {
                                    latestOnAction(ClippingReviewAction.FAVORITE, actionClipping.id, true)
                                } else settleCard(Offset(-cardSize.width.toFloat(), 0f), ClippingReviewAction.FAVORITE)
                            },
                            onHide = {
                                if (pager != null && actionClipping != null) {
                                    latestOnAction(ClippingReviewAction.EXCLUDE, actionClipping.id, true)
                                } else settleCard(Offset(cardSize.width.toFloat(), 0f), ClippingReviewAction.EXCLUDE)
                            },
                            onUndo = {
                                val page = bookClippings.indexOfFirst { it.id == undo?.clippingId }
                                if (onUndo(page >= 0) && pager != null && page >= 0) {
                                    scope.launch { pager.animateScrollToPage(page) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private suspend fun PointerInputScope.detectReviewPinch(onPinch: (Float) -> Boolean) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var multiTouch = false
        var handled = false
        var initialRadius = 0f
        var zoom = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) multiTouch = true
            if (multiTouch) {
                // Claim the entire gesture so the card or pager can't treat either finger as a swipe.
                event.changes.forEach { it.consume() }
                if (!handled && event.changes.count { it.pressed && it.previousPressed } >= 2) {
                    if (initialRadius == 0f) initialRadius = event.calculateCentroidSize(useCurrent = false)
                    zoom *= event.calculateZoom()
                    val scaleChange = abs(zoom - 1f)
                    if (scaleChange >= 0.18f && scaleChange * initialRadius > viewConfiguration.touchSlop) {
                        handled = onPinch(zoom)
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun ClippingReviewHeader(title: String, status: String, backLabel: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            TextButton(onClick = onBack) { Text(backLabel, maxLines = 1) }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Box(Modifier.weight(1f).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ClippingReviewCard(
    clipping: KindleClipping,
    isFavorite: Boolean,
    modifier: Modifier,
    inBook: Boolean = false
) {
    val note = clipping.kindleNotes.lastOrNull()?.text?.takeIf { it.isNotBlank() }
    Surface(
        modifier = modifier.testTag("review-card-${clipping.id}"),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CalendarToday, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    clipping.addedAt?.let { reviewDate.format(it) } ?: "日期未知",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isFavorite) Icon(Icons.Filled.Bookmark, localizedText("已收藏"), Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary)
            }
            val body = Modifier.weight(1f)
            Column(
                if (inBook) body.verticalScroll(rememberScrollState()) else body,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    clipping.text,
                    modifier = if (inBook) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(2f, fill = false),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, lineHeight = 32.sp),
                    maxLines = if (inBook) Int.MAX_VALUE else 14,
                    overflow = TextOverflow.Ellipsis
                )
                if (note != null) {
                    val noteModifier = if (inBook) Modifier else Modifier.weight(1f, fill = false)
                    Column(
                        noteModifier.fillMaxWidth().padding(top = 14.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("我的批注", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (inBook) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(clipping.bookTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                clipping.author?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, overflow = TextOverflow.Ellipsis)
                }
                if (inBook) {
                    val location = clipping.location?.let { "位置 $it" } ?: clipping.page?.let { "页 $it" }
                    if (location != null) {
                        Text(location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClippingReviewActions(
    showActions: Boolean,
    inBook: Boolean,
    isFavorite: Boolean,
    isHidden: Boolean,
    enabled: Boolean,
    canUndo: Boolean,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onUndo: () -> Unit
) {
    Box(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        if (showActions) {
            FilledTonalIconButton(
                onClick = onFavorite,
                modifier = Modifier.align(Alignment.CenterStart).size(52.dp),
                enabled = enabled,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                val label = when {
                    !isFavorite -> "收藏"
                    inBook -> "取消收藏"
                    else -> "已收藏"
                }
                Icon(if (isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, localizedText(label))
            }
            FilledTonalIconButton(
                onClick = onHide,
                modifier = Modifier.align(Alignment.CenterEnd).size(52.dp),
                enabled = enabled,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(if (isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    localizedText(if (isHidden) "恢复回顾" else "隐藏"))
            }
        }
        if (canUndo) FilledTonalButton(onClick = onUndo) { Text("撤销") }
    }
}

@Composable
private fun EmptyClippingReview(excludedCount: Int, onRestore: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(20.dp))
        Text("没有可回顾的摘抄", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.size(10.dp))
        Text("恢复隐藏的摘抄，或同步更多 Kindle 笔记后再试。", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.size(24.dp))
        if (excludedCount > 0) {
            Button(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text("恢复不再回顾的摘抄（$excludedCount 条）")
            }
            Spacer(Modifier.size(10.dp))
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
    }
}
