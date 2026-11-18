package com.hongxiang.kpilot.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.hongxiang.kpilot.UiState
import com.hongxiang.kpilot.i18n.localizeAppText
import com.hongxiang.kpilot.notes.ClippingKind
import com.hongxiang.kpilot.notes.KindleClipping
import com.hongxiang.kpilot.notes.KindleNote
import com.hongxiang.kpilot.review.ClippingReviewAction
import com.hongxiang.kpilot.review.ClippingReviewRecord
import com.hongxiang.kpilot.review.ClippingReviewUndo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ClippingReviewContentTest {
    @get:Rule val compose = createComposeRule()

    private val actions = mutableListOf<ClippingReviewAction>()
    private val bookActions = mutableListOf<Pair<ClippingReviewAction, String>>()
    private val focused = mutableListOf<String>()
    private val state = mutableStateOf(UiState())
    private var undoClicks = 0
    private var undoKeptBook = false
    private val card = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)

    @Test fun longClippingShrinksToFitAndShortClippingRestoresOriginalSize() {
        val long = clipping("long", 20, "计算机技术已经飞速发展，我用来工作的第一台计算机是 IBM7030 Stretch。".repeat(8))
        val current = mutableStateOf(long)
        var originalFontSize = 0f
        compose.setContent {
            MaterialTheme {
                originalFontSize = MaterialTheme.typography.titleLarge.fontSize.value
                ClippingReviewCard(current.value, false, Modifier.size(320.dp, 500.dp))
            }
        }

        val longLayout = clippingTextLayout(long.id)
        assertTrue(longLayout.layoutInput.style.fontSize.value < originalFontSize)
        assertFullClippingVisible(longLayout, long.text)

        val short = clipping("short", 30, "一条简短的摘抄。")
        compose.runOnIdle { current.value = short }
        val shortLayout = clippingTextLayout(short.id)
        assertEquals(originalFontSize, shortLayout.layoutInput.style.fontSize.value, 0.01f)
        assertFullClippingVisible(shortLayout, short.text)
    }

    @Test fun longClippingFitsAlongsideAnnotationWithLargerSystemFont() {
        val text = "我实在无法想像还有哪种生活会比热爱计算机更加激动人心。".repeat(6)
        val annotation = "这段话记录了作者对计算机的热爱。"
        val clipping = clipping("annotated", 20, text).copy(
            kindleNotes = listOf(KindleNote("note", null, null, null, null, null, "", annotation, ""))
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MaterialTheme {
                    ClippingReviewCard(clipping, false, Modifier.size(320.dp, 500.dp))
                }
            }
        }

        assertFullClippingVisible(clippingTextLayout(clipping.id), text)
        compose.onNodeWithText(annotation).assertIsDisplayed()
        compose.onNodeWithText(clipping.bookTitle).assertIsDisplayed()
    }

    @Test fun swipingInAnySupportedDirectionAdvancesALongClipping() {
        showClipping()

        compose.onNode(card).performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(listOf(ClippingReviewAction.NEXT), actions) }
        compose.onNode(card).performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(List(2) { ClippingReviewAction.NEXT }, actions) }
        compose.onNode(card).performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(List(3) { ClippingReviewAction.NEXT }, actions) }
    }

    @Test fun downwardAndShortDragsKeepTheCardAndButtonsApplyTheirOwnActions() {
        showClipping()

        compose.onNode(card).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(emptyList<ClippingReviewAction>(), actions) }
        compose.onNode(card).performTouchInput { swipe(center, center + Offset(width * 0.06f, 0f)) }
        compose.runOnIdle { assertEquals(emptyList<ClippingReviewAction>(), actions) }

        compose.onNodeWithContentDescription(label("收藏")).performClick()
        compose.runOnIdle { assertEquals(listOf(ClippingReviewAction.FAVORITE), actions) }
        compose.onNodeWithContentDescription(label("隐藏")).performClick()
        compose.runOnIdle {
            assertEquals(listOf(ClippingReviewAction.FAVORITE, ClippingReviewAction.EXCLUDE), actions)
        }
    }

    @Test fun undoAppearsAtBottomEvenWhenTheLastCardWasHidden() {
        showClipping()
        compose.onNodeWithText(label("撤销")).assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(
                clippingReviewDeck = emptyList(),
                clippingReviewUndo = ClippingReviewUndo(1, "clipping", ClippingReviewAction.EXCLUDE, null, Long.MAX_VALUE)
            )
        }
        val dialog = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val undo = compose.onNodeWithText(label("撤销")).assertIsDisplayed()
        val button = undo.fetchSemanticsNode().boundsInRoot
        assertEquals(dialog.center.x, button.center.x, 1f)
        assertTrue(button.center.y > dialog.center.y)
        undo.performClick()
        compose.runOnIdle { assertEquals(1, undoClicks) }
        compose.onNodeWithText(label("撤销")).assertDoesNotExist()
    }

    @Test fun pinchOpensTheBookAtTheCurrentPositionAndSpreadingFocusesTheCenteredClipping() {
        val earlier = clipping("earlier", 5, "The first note in this book.")
        val middle = clipping("middle", 20, "The middle note in this book.")
        val later = clipping("later", 120, "The last note in this book.")
        val otherBook = clipping("other", 42, "A note from another book.").copy(bookId = "other-book")
        showClipping(middle, listOf(later, otherBook, middle, earlier))
        val singleCard = compose.onNodeWithTag("review-card-${middle.id}").fetchSemanticsNode().boundsInRoot

        pinchScreen(inward = true)
        compose.onNodeWithText(label("同书笔记")).assertIsDisplayed()
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.onNodeWithText(middle.text).assertIsDisplayed()
        val bookCard = compose.onNodeWithTag("review-card-${middle.id}").fetchSemanticsNode().boundsInRoot
        val widthScale = bookCard.width / singleCard.width
        assertTrue(widthScale in 0.85f..0.91f)
        assertEquals(widthScale, bookCard.height / singleCard.height, 0.01f)

        compose.onNode(isDialog()).performTouchInput { swipeLeft(startX = width * 0.85f, endX = width * 0.15f) }
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.onNodeWithText(later.text).assertIsDisplayed()

        pinchScreen(inward = false)
        compose.onNodeWithText(label("回顾")).assertIsDisplayed()
        compose.onNodeWithText(later.text).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(later.id), focused)
            assertEquals(emptyList<ClippingReviewAction>(), actions)
        }
    }

    @Test fun bookActionsApplyToTheCenteredClippingAndUndoReturnsThere() {
        val first = clipping("first", 10, "The first note in this book.")
        val second = clipping("second", 20, "The second note in this book.")
        showClipping(first, listOf(first, second))
        pinchScreen(inward = true)
        compose.onNode(isDialog()).performTouchInput { swipeLeft(startX = width * 0.85f, endX = width * 0.15f) }
        compose.onNodeWithText("2 / 2").assertIsDisplayed()

        compose.onNodeWithContentDescription(label("收藏")).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(label("取消收藏")).assertIsDisplayed()
        compose.onNodeWithContentDescription(label("隐藏")).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(label("恢复回顾")).assertIsDisplayed()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()

        compose.onNode(isDialog()).performTouchInput { swipeRight(startX = width * 0.15f, endX = width * 0.85f) }
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithText(label("撤销")).performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(ClippingReviewAction.FAVORITE to second.id, ClippingReviewAction.EXCLUDE to second.id), bookActions)
            assertTrue(undoKeptBook)
        }
    }

    private fun pinchScreen(inward: Boolean) {
        compose.onNode(isDialog()).performTouchInput {
            val wide = Offset(width * 0.3f, 0f)
            val narrow = Offset(width * 0.1f, 0f)
            val start = if (inward) wide else narrow
            val end = if (inward) narrow else wide
            pinch(center - start, center - end, center + start, center + end)
        }
    }

    private fun showClipping(
        current: KindleClipping = clipping("clipping", 20, "A long passage from the book.\n".repeat(40)),
        clippings: List<KindleClipping> = listOf(current)
    ) {
        state.value = UiState(notes = clippings, clippingReviewDeck = listOf(current.id))
        compose.setContent {
            MaterialTheme {
                ClippingReviewDialog(
                    state = state.value,
                    onClose = {},
                    onClippingDisplayed = {},
                    onAction = { action, id, inBook ->
                        val currentState = state.value
                        if (inBook) {
                            bookActions += action to id
                            val previous = currentState.clippingReviewRecords[id] ?: ClippingReviewRecord(id)
                            val updated = when (action) {
                                ClippingReviewAction.FAVORITE -> previous.copy(isFavorite = true)
                                ClippingReviewAction.EXCLUDE -> previous.copy(isExcluded = true)
                                ClippingReviewAction.NEXT -> error("Paging should not use the review deck")
                            }
                            state.value = currentState.copy(
                                clippingReviewRecords = currentState.clippingReviewRecords + (id to updated),
                                clippingReviewUndo = ClippingReviewUndo(1, id, action, previous, Long.MAX_VALUE)
                            )
                        } else {
                            actions += action
                            state.value = currentState.copy(clippingReviewRevision = currentState.clippingReviewRevision + 1)
                        }
                    },
                    onFocusClipping = { id ->
                        focused += id
                        state.value = state.value.copy(clippingReviewDeck = listOf(id))
                    },
                    onUndo = { keepBook ->
                        undoClicks++
                        undoKeptBook = keepBook
                        state.value = state.value.copy(clippingReviewUndo = null)
                        true
                    },
                    onRestore = {}
                )
            }
        }
    }

    private fun clipping(id: String, location: Int, text: String) = KindleClipping(
        id = id, bookId = "book", bookTitle = "Review test book", author = null,
        kind = ClippingKind.HIGHLIGHT, location = location.toString(), locationStart = location, locationEnd = location,
        page = null, addedAt = null, metadata = "", text = text, raw = ""
    )

    private fun clippingTextLayout(id: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("review-text-$id", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertFullClippingVisible(layout: TextLayoutResult, text: String) {
        assertFalse(layout.hasVisualOverflow)
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1))
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    private fun label(value: String) = InstrumentationRegistry.getInstrumentation().targetContext.localizeAppText(value)
}
