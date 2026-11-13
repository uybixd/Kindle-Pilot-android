package com.hongxiang.kpilot.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.SizeF
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.hongxiang.kpilot.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Renders in KPilot so previews and launchers use the same layout and typefaces. */
internal object FavoriteWidgetCard {
    val defaultSize = SizeF(338f, 158f)
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US)

    fun render(
        context: Context,
        note: FavoriteWidgetNote?,
        style: FavoriteWidgetStyle,
        size: FavoriteWidgetSize,
        widthPx: Int,
        heightPx: Int,
        bitmapScale: Float = 1f,
        forLauncher: Boolean = false,
        blurText: Boolean = false
    ): RenderedFavoriteCard {
        val card = layout(context, note, style, size, widthPx, heightPx)
        val bitmap = createBitmap(
            (widthPx * bitmapScale).toInt().coerceAtLeast(1),
            (heightPx * bitmapScale).toInt().coerceAtLeast(1)
        )
        val canvas = Canvas(bitmap)
        canvas.scale(bitmap.width.toFloat() / widthPx, bitmap.height.toFloat() / heightPx)
        val dividerBitmap = if (forLauncher) {
            card.background = null
            val divider = card.findViewById<View>(R.id.widget_divider)
            val bounds = Rect(0, 0, divider.width, divider.height)
            card.offsetDescendantRectToMyCoords(divider, bounds)
            createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ALPHA_8).also { layer ->
                val dividerCanvas = Canvas(layer)
                dividerCanvas.scale(bitmap.width.toFloat() / widthPx, bitmap.height.toFloat() / heightPx)
                dividerCanvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
                divider.draw(dividerCanvas)
                divider.visibility = View.INVISIBLE
            }
        } else null
        card.draw(canvas)
        val blurredText = if (forLauncher && blurText) {
            val offsets = IntArray(2)
            val radius = 2.6f * context.resources.displayMetrics.density * bitmapScale
            val blur = Paint().apply { maskFilter = BlurMaskFilter(radius.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL) }
            val alpha = bitmap.extractAlpha(blur, offsets)
            createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ALPHA_8).also { layer ->
                Canvas(layer).drawBitmap(alpha, offsets[0].toFloat(), offsets[1].toFloat(), null)
                alpha.recycle()
            }
        } else null
        return RenderedFavoriteCard(bitmap, card.contentDescription.toString(), dividerBitmap, blurredText)
    }

    internal fun layout(
        context: Context,
        note: FavoriteWidgetNote?,
        style: FavoriteWidgetStyle,
        size: FavoriteWidgetSize,
        widthPx: Int,
        heightPx: Int
    ): ViewGroup {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_KPilot)
        val terminal = style.isTerminal
        val layout = if (terminal) size.terminalLayout else R.layout.widget_paper_card
        val card = LayoutInflater.from(themedContext).inflate(layout, null) as ViewGroup
        card.layoutDirection = context.resources.configuration.layoutDirection
        val source = card.findViewById<TextView>(R.id.widget_source)
        val author = card.findViewById<TextView>(R.id.widget_author)
        val body = card.findViewById<TextView>(R.id.widget_body)
        val date = card.findViewById<TextView>(R.id.widget_date)
        val nextSpace = card.findViewById<View>(R.id.widget_next_space)
        val density = context.resources.displayMetrics.density

        if (terminal) {
            card.setBackgroundResource(style.background)
            val foreground = context.getColor(style.dividerColor)
            card.findViewById<View>(R.id.widget_divider).setBackgroundColor(foreground)
            val regular = ResourcesCompat.getFont(context, R.font.terminus_regular) ?: Typeface.MONOSPACE
            val bold = ResourcesCompat.getFont(context, R.font.terminus_bold)
                ?: Typeface.create(regular, Typeface.BOLD)
            source.typeface = bold
            author.typeface = bold
            body.typeface = regular
            date.typeface = regular
            for (textView in listOf(source, author, body, date)) textView.setTextColor(foreground)
        } else {
            val padding = (size.paddingDp * density).roundToInt()
            val arrow = (size.nextSizeDp * density).roundToInt()
            card.setPadding(padding, padding, padding, padding)
            nextSpace.layoutParams = nextSpace.layoutParams.apply {
                width = arrow
                height = arrow
            }
            if (size == FavoriteWidgetSize.SMALL) {
                source.textSize = 11f
                date.textSize = 10f
            }
        }

        val book = note?.bookTitle.orEmpty()
            .replace("《", "")
            .replace("》", "")
            .singleLine()
            .ifBlank { if (note == null) context.getString(R.string.widget_empty_book) else "Kindle" }
        val authorName = note?.author.orEmpty().singleLine().take(MAX_AUTHOR_CHARS)
        val quote = note?.text ?: context.getString(R.string.widget_empty_text)
        val descriptionQuote = quote.take(MAX_BODY_CHARS)
        val dateText = note?.addedAtMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
        }.orEmpty()

        val chineseTypeface = ResourcesCompat.getFont(context, R.font.zhuque_fangsong_regular)
        if (!terminal && note != null) {
            // Keep the paper divider in the same position when metadata changes.
            source.layoutParams.height = source.metadataLineHeight(chineseTypeface)
            val footerHeight = maxOf(author.metadataLineHeight(chineseTypeface), date.lineHeight)
            author.layoutParams.height = footerHeight
            date.layoutParams.height = footerHeight
        }
        source.text = (if (terminal) context.getString(R.string.widget_terminal_prompt, book) else book)
            .take(MAX_HEADER_CHARS).withChineseTypeface(chineseTypeface)
        author.text = authorName.withChineseTypeface(chineseTypeface)
        val showAuthor = authorName.isNotBlank() && (widthPx / density >= 160f || (!terminal && dateText.isBlank()))
        author.visibility = when {
            showAuthor -> View.VISIBLE
            !terminal && note != null -> View.INVISIBLE
            else -> View.GONE
        }
        date.text = if (terminal && dateText.isNotBlank()) {
            context.getString(R.string.widget_terminal_date, dateText)
        } else dateText
        date.visibility = when {
            dateText.isNotBlank() -> View.VISIBLE
            terminal || note != null -> View.INVISIBLE
            else -> View.GONE
        }
        nextSpace.visibility = if (note == null) View.GONE else View.INVISIBLE
        card.contentDescription = listOf(book.take(MAX_HEADER_CHARS), authorName, descriptionQuote, dateText)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        card.measure(widthSpec, heightSpec)
        fitDate(date)
        if (terminal) card.measure(widthSpec, heightSpec)
        val bodyText = (if (terminal) quote else descriptionQuote).withChineseTypeface(chineseTypeface)
        fitBody(body, bodyText, size, terminal)
        card.measure(widthSpec, heightSpec)
        card.layout(0, 0, widthPx, heightPx)
        return card
    }

    private fun fitDate(date: TextView) {
        val available = date.measuredWidth - 1
        val text = date.text.toString()
        val required = date.paint.measureText(text)
        if (available <= 0 || required <= available) return

        // Terminus can round glyph advances at small sizes. Recheck the measured width.
        val candidate = (date.textSize * available / required).toInt().coerceAtLeast(1)
        for (sizePx in candidate downTo 1) {
            date.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx.toFloat())
            if (date.paint.measureText(text) <= available) return
        }
    }

    private fun fitBody(body: TextView, quote: CharSequence, size: FavoriteWidgetSize, terminal: Boolean) {
        val width = body.measuredWidth
        val height = body.measuredHeight
        body.text = quote
        if (width <= 0 || height <= 0) {
            body.maxLines = 1
            return
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val textSizes = if (terminal) size.terminalTextSp else size.paperTextSp
        for (fontSize in textSizes.reversed()) {
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
            body.maxLines = height / body.lineHeight.coerceAtLeast(1) + 1
            body.measure(widthSpec, heightSpec)
            if (body.measuredHeight <= height && body.layout.fitsWidth(allowEllipsis = false)) return
        }

        val layout = body.layout ?: return
        val line = layout.getLineForVertical(height)
        if (terminal) {
            // Capping the layout can change the last line's height and spacing.
            for (lineCount in (line + 1) downTo 1) {
                body.maxLines = lineCount
                body.measure(widthSpec, heightSpec)
                if (body.layout.height <= height && body.layout.fitsWidth(allowEllipsis = true)) return
            }
            // A fallback glyph can exceed the line height on a compact card.
            for (sizePx in (body.textSize.toInt() - 1) downTo 1) {
                body.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx.toFloat())
                body.measure(widthSpec, heightSpec)
                if (body.layout.height <= height && body.layout.fitsWidth(allowEllipsis = true)) return
            }
            body.visibility = View.INVISIBLE
            return
        }

        val visibleLines = if (layout.getLineBottom(line) <= height) line + 1 else line
        body.maxLines = visibleLines.coerceAtLeast(1)
        body.measure(widthSpec, heightSpec)
        if (body.measuredHeight <= height) return

        // At minimum widget height, large system fonts or CJK fallback can make even one line too tall.
        val fittedSize = (body.textSize * height / body.measuredHeight).toInt().coerceAtLeast(1)
        for (sizePx in fittedSize downTo 1) {
            body.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx.toFloat())
            body.measure(widthSpec, heightSpec)
            if (body.measuredHeight <= height) return
        }
    }

    private fun Layout.fitsWidth(allowEllipsis: Boolean): Boolean =
        (0 until lineCount).all { line ->
            getLineMax(line) <= width && (allowEllipsis || getEllipsisCount(line) == 0)
        }

    private fun String.singleLine(): String = replace('\n', ' ').replace('\r', ' ').trim()

    private fun TextView.metadataLineHeight(chineseTypeface: Typeface?): Int {
        val metrics = Paint(paint).apply { typeface = chineseTypeface }.fontMetricsInt
        return maxOf(lineHeight, metrics.bottom - metrics.top)
    }

    private fun String.withChineseTypeface(typeface: Typeface?): CharSequence {
        if (typeface == null || !chineseRuns.containsMatchIn(this)) return this
        return SpannableString(this).also { styled ->
            chineseRuns.findAll(this).forEach { match ->
                styled.setSpan(WidgetTypefaceSpan(typeface), match.range.first, match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private class WidgetTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: TextPaint) { textPaint.typeface = typeface }
        override fun updateDrawState(textPaint: TextPaint) { textPaint.typeface = typeface }
    }

    private val chineseRuns = Regex("[\\p{IsHan}\\u3000-\\u303f\\uff00-\\uffef]+")
    private const val MAX_BODY_CHARS = 4_000
    private const val MAX_HEADER_CHARS = 400
    private const val MAX_AUTHOR_CHARS = 200
}

internal data class RenderedFavoriteCard(
    val bitmap: Bitmap,
    val description: String,
    val divider: Bitmap?,
    val blurredText: Bitmap?
)

internal enum class FavoriteWidgetStyle(
    val key: String,
    val label: String,
    @get:DrawableRes val background: Int,
    @get:DrawableRes val nextIcon: Int,
    @get:ColorRes val dividerColor: Int
) {
    PAPER("paper", "默认", R.drawable.widget_paper_background, R.drawable.ic_widget_paper_next,
        R.color.widget_paper_divider),
    TERMINAL("terminal", "终端绿", R.drawable.widget_terminal_background, R.drawable.ic_widget_next,
        R.color.widget_terminal_foreground),
    TERMINAL_AMBER("terminal_amber", "终端琥珀", R.drawable.widget_terminal_amber_background,
        R.drawable.ic_widget_next, R.color.widget_terminal_amber_foreground);

    val isTerminal: Boolean get() = this != PAPER

    companion object {
        fun fromKey(key: String?): FavoriteWidgetStyle = entries.firstOrNull { it.key == key } ?: PAPER
    }
}

internal enum class FavoriteWidgetSize(
    val terminalLayout: Int,
    val paddingDp: Int,
    val nextSizeDp: Int,
    val paperTextSp: IntRange,
    val terminalTextSp: IntRange
) {
    SMALL(R.layout.widget_terminal_small, 10, 24, 13..23, 10..23),
    MEDIUM(R.layout.widget_terminal_medium, 14, 24, 14..24, 11..24),
    LARGE(R.layout.widget_terminal_large, 18, 30, 17..44, 11..44);

    companion object {
        fun forDimensions(widthDp: Float, heightDp: Float): FavoriteWidgetSize = when {
            widthDp < 220f || heightDp < 130f -> SMALL
            heightDp >= 220f -> LARGE
            else -> MEDIUM
        }
    }
}
