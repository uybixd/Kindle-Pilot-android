package com.hongxiang.kpilot.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.hongxiang.kpilot.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class FavoriteWidgetRenderingTest {
    @Test
    fun everyLayoutKeepsTheQuoteInsideTheBody() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val text = "事实上，一个人愈健康、愈接近正常，当他的努力在某一特殊方向受到阻挠时，他愈能另外找寻新的门路。只有神经病患者才会认为他的目标的具体表现是：“我必须如此，否则我便无路可走了。”".repeat(2)
        val note = FavoriteWidgetNote("note", text, "自卑与超越", "阿尔弗雷德·阿德勒", 1_800_000_000_000)

        for (style in FavoriteWidgetStyle.entries) {
            assertFits(context, note, style, FavoriteWidgetSize.SMALL, 110, 110)
            assertFits(context, note, style, FavoriteWidgetSize.SMALL, 150, 150)
            assertFits(context, note, style, FavoriteWidgetSize.MEDIUM, 355, 158)
            assertFits(context, note, style, FavoriteWidgetSize.LARGE, 355, 596)
        }
    }

    @Test
    fun terminalFitsChineseEnglishAndMixedQuotesBeforeEllipsizing() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = target.createConfigurationContext(
            Configuration(target.resources.configuration).apply { fontScale = 1f }
        )
        val style = FavoriteWidgetStyle.TERMINAL
        val note = FavoriteWidgetNote("fit", "", "自卑与超越", "阿尔弗雷德·阿德勒", 1_800_000_000_000)
        val chinese = "事实上，一个人愈健康、愈接近正常，他愈能另外找寻新的门路。"
        val quotes = listOf(20, 100, 300, 560).map { chinese.repeat(30).take(it) }
        val english = "Reading gives familiar words fresh meaning. ".repeat(7).take(300)
        val mixed = "KPilot brings favorite passages back. 中文和 English 放在同一段。\n\n保留原文换行，a second paragraph."
        val size = FavoriteWidgetSize.LARGE
        val bodies = quotes.map { assertFits(context, note.copy(text = it), style, size, 355, 334) }
        val (short, medium, long, overflow) = bodies

        assertEquals(context.textPixels(size.terminalTextSp.last), short.textSize, 0.01f)
        assertTrue(short.textSize > medium.textSize)
        assertTrue(medium.textSize > long.textSize)
        assertTrue(overflow.textSize in context.textPixels(10)..context.textPixels(size.terminalTextSp.first))
        bodies.take(3).zip(quotes).forEach { (body, quote) -> assertComplete(body, quote) }
        val overflowLayout = overflow.layout
        assertTrue(overflowLayout.getEllipsisCount(overflowLayout.lineCount - 1) > 0)

        val englishBody = assertFits(context, note.copy(text = english), style, size, 355, 334)
        assertComplete(englishBody, english)
        assertTrue(englishBody.textSize > long.textSize)
        val mixedBody = assertFits(context, note.copy(text = mixed), style, size, 355, 334)
        assertComplete(mixedBody, mixed)
        val blankLineStart = mixed.indexOf("\n\n") + 1
        val blankLine = mixedBody.layout.getLineForOffset(blankLineStart)
        assertEquals(blankLineStart, mixedBody.layout.getLineStart(blankLine))
        assertEquals(blankLineStart + 1, mixedBody.layout.getLineEnd(blankLine))

        for ((width, height) in listOf(110 to 110, 150 to 150, 355 to 158, 355 to 596)) {
            val layoutSize = FavoriteWidgetSize.forDimensions(width.toFloat(), height.toFloat())
            for (quote in quotes + mixed) {
                assertFits(context, note.copy(text = quote), style, layoutSize, width, height)
            }
        }
    }

    @Test
    fun shortQuotesUseLargerTypeInBothStyles() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = target.createConfigurationContext(
            Configuration(target.resources.configuration).apply { fontScale = 1f }
        )
        val note = FavoriteWidgetNote("short", "先生曰：“为学大病在好名。”", "传习录", "王阳明")
        val compactNote = note.copy(text = "为学大病在好名。")

        for (style in FavoriteWidgetStyle.entries) {
            val small = assertFits(context, compactNote, style, FavoriteWidgetSize.SMALL, 150, 150)
            val medium = assertFits(context, note, style, FavoriteWidgetSize.MEDIUM, 355, 158)
            val large = assertFits(context, note, style, FavoriteWidgetSize.LARGE, 335, 364)
            assertEquals(context.textPixels(23), small.textSize, 0.01f)
            assertEquals(context.textPixels(24), medium.textSize, 0.01f)
            assertEquals(context.textPixels(44), large.textSize, 0.01f)
            assertComplete(small, compactNote.text)
            listOf(medium, large).forEach { assertComplete(it, note.text) }
        }
    }

    @Test
    fun terminalStylesKeepBlackEdgesAndRoundedCornersInBothSystemThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = instrumentation.context
        val note = FavoriteWidgetNote("color", "终端摘抄", "书名", "作者", 1_800_000_000_000)
        val width = context.pixels(338)
        val bitmapHeight = context.pixels(158)
        val hostHeight = context.pixels(174)
        for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val config = Configuration().apply { uiMode = nightMode }
            val themedContext = context.createConfigurationContext(config)
            val themedHost = host.createConfigurationContext(config)
            for (style in listOf(FavoriteWidgetStyle.TERMINAL, FavoriteWidgetStyle.TERMINAL_AMBER)) {
                val preview = FavoriteWidgetCard.render(
                    themedContext, note, style, FavoriteWidgetSize.MEDIUM, width, bitmapHeight
                )
                assertEquals(Color.BLACK, preview.bitmap.getPixel(width / 2, 0))
                assertEquals(Color.TRANSPARENT, preview.bitmap.getPixel(0, 0))

                val root = FavoriteWidgetProvider.buildRemoteViews(themedContext, note, options(338, 158), style)
                    .apply(themedHost, FrameLayout(themedHost))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) assertFalse(root.isForceDarkAllowed)
                val image = root.findViewById<ImageView>(R.id.widget_card)
                val quote = (image.drawable as BitmapDrawable).bitmap
                assertEquals(Color.TRANSPARENT, quote.getPixel(width / 2, 0))
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(hostHeight, View.MeasureSpec.EXACTLY)
                )
                root.layout(0, 0, width, hostHeight)
                val hostBitmap = createBitmap(width, hostHeight)
                root.draw(Canvas(hostBitmap))
                assertEquals(Color.BLACK, hostBitmap.getPixel(width / 2, 0))
                assertEquals(Color.BLACK, hostBitmap.getPixel(width / 2, hostHeight - 1))
                assertEquals(Color.BLACK, hostBitmap.getPixel(0, hostHeight / 2))
                assertEquals(Color.BLACK, hostBitmap.getPixel(width - 1, hostHeight / 2))
                assertEquals(Color.TRANSPARENT, hostBitmap.getPixel(0, 0))
            }
        }
    }

    @Test
    fun launcherReceivesTheAppRenderedTerminusGlyphs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = instrumentation.context
        assertNotEquals(context.packageName, host.packageName)
        val note = FavoriteWidgetNote(
            id = "mixed",
            text = "KPilot 0123456789\n中文和 ABC xyz",
            bookTitle = "《Terminal 101》",
            author = "Linus Torvalds",
            addedAtMillis = LocalDate.of(2026, 7, 2).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        val width = context.pixels(338)
        val height = context.pixels(158)
        val style = FavoriteWidgetStyle.TERMINAL
        val size = FavoriteWidgetSize.MEDIUM
        val layout = FavoriteWidgetCard.layout(context, note, style, size, width, height)
        val regular = ResourcesCompat.getFont(context, R.font.terminus_regular)
        val bold = ResourcesCompat.getFont(context, R.font.terminus_bold)
        val source = layout.findViewById<TextView>(R.id.widget_source)
        val author = layout.findViewById<TextView>(R.id.widget_author)
        val body = layout.findViewById<TextView>(R.id.widget_body)
        val date = layout.findViewById<TextView>(R.id.widget_date)
        assertEquals(bold, source.typeface)
        assertEquals(bold, author.typeface)
        assertEquals(regular, body.typeface)
        assertEquals(regular, date.typeface)
        assertEquals("$ Terminal 101", source.text.toString())
        assertEquals("# 2026.07.02", date.text.toString())

        val content = FavoriteWidgetCard.render(context, note, style, size, width, height, forLauncher = true)
        val views = FavoriteWidgetProvider.buildRemoteViews(context, note, options(338, 158), style)
        val parcel = Parcel.obtain()
        try {
            views.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val root = RemoteViews.CREATOR.createFromParcel(parcel).apply(host, FrameLayout(host))
            val image = root.findViewById<ImageView>(R.id.widget_card)
            val bitmap = (image.drawable as BitmapDrawable).bitmap
            assertTrue(content.bitmap.sameAs(bitmap))
            val openTouch = root.findViewById<View>(R.id.widget_open_touch)
            assertEquals(content.description, openTouch.contentDescription.toString())
            assertTrue(openTouch.hasOnClickListeners())
            assertTrue(!root.hasOnClickListeners())
            assertTrue(root.findViewById<View>(R.id.widget_next_touch).hasOnClickListeners())
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun switchingStylesUpdatesTheLauncherView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = instrumentation.context
        val note = FavoriteWidgetNote("note", "A passage to revisit", "Reading journal", "Author")
        val options = options(338, 158)
        val paper = FavoriteWidgetProvider.buildRemoteViews(context, note, options, FavoriteWidgetStyle.PAPER)
        val terminal = FavoriteWidgetProvider.buildRemoteViews(context, note, options, FavoriteWidgetStyle.TERMINAL)
        val amber = FavoriteWidgetProvider.buildRemoteViews(context, note, options, FavoriteWidgetStyle.TERMINAL_AMBER)
        instrumentation.runOnMainSync {
            val hostView = AppWidgetHostView(host)
            hostView.updateAppWidget(paper)
            val paperRoot = hostView.findViewById<View>(R.id.widget_root)
            val paperBitmap = (paperRoot.findViewById<ImageView>(R.id.widget_card).drawable as BitmapDrawable).bitmap
            assertEquals(context.getColor(R.color.widget_paper_background), (paperRoot.background as GradientDrawable).color?.defaultColor)

            hostView.updateAppWidget(terminal)
            val terminalRoot = hostView.findViewById<View>(R.id.widget_root)
            val image = terminalRoot.findViewById<ImageView>(R.id.widget_card)
            val terminalBitmap = (image.drawable as BitmapDrawable).bitmap
            assertEquals(Color.BLACK, (terminalRoot.background as GradientDrawable).color?.defaultColor)
            assertFalse(paperBitmap.sameAs(terminalBitmap))

            amber.reapply(host, terminalRoot)
            assertFalse(terminalBitmap.sameAs((image.drawable as BitmapDrawable).bitmap))
            assertTrue(terminalRoot.findViewById<View>(R.id.widget_next_touch).hasOnClickListeners())
        }
    }

    @Test
    fun advancingReplacesTheDrawableAndKeepsTheCurrentQuoteInTheDefaultChild() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = instrumentation.context
        val first = FavoriteWidgetNote("first", "First passage", "Reading journal")
        val nextNotes = listOf(
            first.copy(id = "second", text = "先生曰：“为学大病在好名。”"),
            first.copy(id = "third", text = "The third passage replaces the second drawable."),
            first.copy(id = "fourth", text = "第四条收藏继续替换同一个图片位置。")
        )
        val options = options(335, 364)

        for (style in FavoriteWidgetStyle.entries) {
            val firstViews = FavoriteWidgetProvider.buildRemoteViews(context, first, options, style)
            val nextViews = nextNotes.map { note ->
                FavoriteWidgetProvider.buildRemoteViews(context, note, options, style, animate = true)
            }
            instrumentation.runOnMainSync {
                val root = firstViews.apply(host, FrameLayout(host))
                val flipper = root.findViewById<ViewFlipper>(R.id.widget_text_flipper)
                val divider = root.findViewById<ImageView>(R.id.widget_static_divider)
                val arrow = root.findViewById<View>(R.id.widget_next_touch)
                val sharp = flipper.findViewById<ImageView>(R.id.widget_card)
                val blurred = flipper.findViewById<ImageView>(R.id.widget_card_blur)
                val oldDivider = (divider.drawable as BitmapDrawable).bitmap

                for ((note, views) in nextNotes.zip(nextViews)) {
                    val previous = sharp.drawable as BitmapDrawable
                    views.reapply(host, root)
                    val current = sharp.drawable as BitmapDrawable
                    assertEquals(0, flipper.displayedChild)
                    assertEquals(sharp, flipper.currentView)
                    assertNotSame(previous, current)
                    assertFalse(previous.bitmap.sameAs(current.bitmap))
                    assertEquals(flipper.inAnimation, sharp.animation)
                    assertEquals(flipper.outAnimation, blurred.animation)
                    assertEquals(android.graphics.Bitmap.Config.ALPHA_8, (blurred.drawable as BitmapDrawable).bitmap.config)
                    assertTrue(root.findViewById<View>(R.id.widget_open_touch).contentDescription.contains(note.text))
                }
                assertTrue(oldDivider.sameAs((divider.drawable as BitmapDrawable).bitmap))
                for (view in listOf(root, divider, arrow)) assertEquals(null, view.animation)

                val freshRoot = nextViews.last().apply(host, FrameLayout(host))
                val freshFlipper = freshRoot.findViewById<ViewFlipper>(R.id.widget_text_flipper)
                assertEquals(0, freshFlipper.displayedChild)
                val freshText = (freshFlipper.currentView as ImageView).drawable as BitmapDrawable
                assertTrue((sharp.drawable as BitmapDrawable).bitmap.sameAs(freshText.bitmap))
            }
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    fun launcherInsetsKeepThePortraitCardAndItsProportions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = instrumentation.context
        val provider = AppWidgetManager.getInstance(context).installedProviders.first {
            it.provider.className == FavoriteWidgetProvider::class.java.name
        }
        val options = Bundle().apply {
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(303f, 285f), SizeF(584f, 136f))
            )
        }
        val views = FavoriteWidgetProvider.buildRemoteViews(context, null, options, FavoriteWidgetStyle.PAPER)
        instrumentation.runOnMainSync {
            val hostView = AppWidgetHostView(host).apply {
                setAppWidget(1, provider)
                setPadding(0, 0, 0, 0)
            }
            val width = host.pixels(303)
            val height = host.pixels(274)
            hostView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            hostView.layout(0, 0, width, height)
            hostView.updateAppWidget(views)

            val image = hostView.findViewById<ImageView>(R.id.widget_card)
            val bitmap = (image.drawable as BitmapDrawable).bitmap
            assertEquals(context.pixels(303), bitmap.width)
            assertEquals(context.pixels(285), bitmap.height)
            assertEquals(ImageView.ScaleType.FIT_CENTER, image.scaleType)
        }
    }

    @Test
    fun emptyStateHidesTheNextButtonAndShowsThePrompt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (style in FavoriteWidgetStyle.entries) {
            val views = FavoriteWidgetProvider.buildRemoteViews(context, null, options(355, 158), style)
            val root = views.apply(context, FrameLayout(context))
            val nextTouch = root.findViewById<View>(R.id.widget_next_touch)

            val openTouch = root.findViewById<View>(R.id.widget_open_touch)
            assertTrue(openTouch.contentDescription.contains(context.getString(R.string.widget_empty_text)))
            assertEquals(View.GONE, nextTouch.visibility)
        }
    }

    private fun assertFits(
        context: Context,
        note: FavoriteWidgetNote,
        style: FavoriteWidgetStyle,
        size: FavoriteWidgetSize,
        widthDp: Int,
        heightDp: Int
    ): TextView {
        val root = FavoriteWidgetCard.layout(
            context, note, style, size, context.pixels(widthDp), context.pixels(heightDp)
        )
        val body = root.findViewById<TextView>(R.id.widget_body)
        val layout = requireNotNull(body.layout)
        assertTrue(
            "$style ${widthDp}x$heightDp clips the quote: ${layout.height}px for ${body.height}px at ${body.textSize}px",
            layout.height <= body.height + 1
        )
        assertTrue("$size does not ellipsize at its line limit", layout.lineCount <= body.maxLines)
        val date = root.findViewById<TextView>(R.id.widget_date)
        assertEquals("${widthDp}x$heightDp clips the date", date.text.length, date.layout.getLineEnd(0))
        assertTrue(date.layout.getLineMax(0) <= date.width)
        if (style.isTerminal) {
            val source = root.findViewById<TextView>(R.id.widget_source)
            val author = root.findViewById<TextView>(R.id.widget_author)
            val divider = root.findViewById<View>(R.id.widget_divider)
            val foreground = context.getColor(style.dividerColor)
            for (view in listOf(source, author, body, date)) assertEquals(foreground, view.currentTextColor)
            assertEquals(foreground, (divider.background as ColorDrawable).color)
            assertEquals("${widthDp}x$heightDp should show the quote at ${body.textSize}px", View.VISIBLE, body.visibility)
            assertTrue(abs(body.totalPaddingTop - body.totalPaddingBottom) <= 1)
            assertTrue(body.top >= (source.parent as View).bottom)
            assertTrue(body.bottom <= (divider.parent as View).top)
            for (line in 0 until layout.lineCount) {
                assertEquals("Line $line is left-aligned", 0f, layout.getLineLeft(line), 0.01f)
                assertTrue("Line $line overflows the body", layout.getLineMax(line) <= layout.width)
            }
        }
        return body
    }

    private fun assertComplete(body: TextView, quote: String) {
        val layout = body.layout
        assertEquals(quote, body.text.toString())
        assertEquals(quote.length, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) {
            assertEquals("Quote of ${quote.length} characters was truncated", 0, layout.getEllipsisCount(line))
        }
    }

    private fun Context.pixels(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    private fun Context.textPixels(sp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), resources.displayMetrics)

    private fun options(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
    }
}
