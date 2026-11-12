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
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.hongxiang.kpilot.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertEquals(context.textPixels(size.terminalTextSp.first), overflow.textSize, 0.01f)
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
    fun terminalDrawsOnePixelBorderAroundTheLauncherHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = instrumentation.context
        val note = FavoriteWidgetNote("color", "终端摘抄", "书名", "作者", 1_800_000_000_000)
        val width = context.pixels(338)
        val bitmapHeight = context.pixels(158)
        val hostHeight = context.pixels(174)
        val green = Color.rgb(0, 153, 0)
        val style = FavoriteWidgetStyle.TERMINAL
        val preview = FavoriteWidgetCard.render(context, note, style, FavoriteWidgetSize.MEDIUM, width, bitmapHeight)
        assertEquals(green, preview.bitmap.getPixel(width / 2, 0))
        assertEquals(Color.BLACK, preview.bitmap.getPixel(width / 2, 1))
        assertEquals(Color.TRANSPARENT, preview.bitmap.getPixel(0, 0))

        val root = FavoriteWidgetProvider.buildRemoteViews(context, note, options(338, 158), style)
            .apply(host, FrameLayout(host))
        val image = root.findViewById<ImageView>(R.id.widget_card)
        assertEquals(Color.TRANSPARENT, (image.drawable as BitmapDrawable).bitmap.getPixel(width / 2, 0))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(hostHeight, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, hostHeight)
        val hostBitmap = createBitmap(width, hostHeight)
        root.draw(Canvas(hostBitmap))
        assertEquals(green, hostBitmap.getPixel(width / 2, 0))
        assertEquals(Color.BLACK, hostBitmap.getPixel(width / 2, 1))
        assertEquals(Color.BLACK, hostBitmap.getPixel(width / 2, (hostHeight - bitmapHeight) / 2))
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

        val content = FavoriteWidgetCard.render(context, note, style, size, width, height, drawBackground = false)
        val views = FavoriteWidgetProvider.buildRemoteViews(context, note, options(338, 158), style)
        val parcel = Parcel.obtain()
        try {
            views.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val root = RemoteViews.CREATOR.createFromParcel(parcel).apply(host, FrameLayout(host))
            val image = root.findViewById<ImageView>(R.id.widget_card)
            val bitmap = (image.drawable as BitmapDrawable).bitmap
            assertTrue(content.bitmap.sameAs(bitmap))
            assertEquals(content.description, root.contentDescription.toString())
            assertTrue(root.hasOnClickListeners())
            assertTrue(root.findViewById<View>(R.id.widget_next_touch).hasOnClickListeners())
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun switchingStylesRepaintsTheExistingLauncherView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = instrumentation.context
        val note = FavoriteWidgetNote("note", "A passage to revisit", "Reading journal", "Author")
        val options = options(338, 158)
        val paper = FavoriteWidgetProvider.buildRemoteViews(context, note, options, FavoriteWidgetStyle.PAPER)
        val terminal = FavoriteWidgetProvider.buildRemoteViews(context, note, options, FavoriteWidgetStyle.TERMINAL)
        val root = paper.apply(host, FrameLayout(host))
        val image = root.findViewById<ImageView>(R.id.widget_card)
        val paperBitmap = (image.drawable as BitmapDrawable).bitmap
        assertEquals(context.getColor(R.color.widget_paper_background), (root.background as GradientDrawable).color?.defaultColor)

        terminal.reapply(host, root)
        val terminalBitmap = (image.drawable as BitmapDrawable).bitmap
        assertEquals(context.getColor(R.color.widget_terminal_background), (root.background as GradientDrawable).color?.defaultColor)
        assertTrue(!paperBitmap.sameAs(terminalBitmap))
        assertTrue(root.findViewById<View>(R.id.widget_next_touch).hasOnClickListeners())
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

            assertTrue(root.contentDescription.contains(context.getString(R.string.widget_empty_text)))
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
        if (style == FavoriteWidgetStyle.TERMINAL) {
            val source = root.findViewById<TextView>(R.id.widget_source)
            val author = root.findViewById<TextView>(R.id.widget_author)
            val divider = root.findViewById<View>(R.id.widget_divider)
            val green = Color.rgb(0, 153, 0)
            for (view in listOf(source, author, body, date)) assertEquals(green, view.currentTextColor)
            assertEquals(green, (divider.background as ColorDrawable).color)
            assertEquals(View.VISIBLE, body.visibility)
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
