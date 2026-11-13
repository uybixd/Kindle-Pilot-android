package com.hongxiang.kpilot.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import com.hongxiang.kpilot.MainActivity
import com.hongxiang.kpilot.R
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Displays a daily favorite in the style chosen in KPilot settings. */
class FavoriteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_NEXT -> updateAll(context, advance = true)
            ACTION_DAILY_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> updateAll(context)
        }
    }

    override fun onEnabled(context: Context) {
        scheduleDailyRefresh(context)
    }

    override fun onDisabled(context: Context) {
        cancelDailyRefresh(context)
    }

    internal companion object {
        const val ACTION_NEXT = "com.hongxiang.kpilot.widget.NEXT"
        const val ACTION_DAILY_REFRESH = "com.hongxiang.kpilot.widget.DAILY_REFRESH"
        private const val REQUEST_OPEN = 10
        private const val REQUEST_NEXT = 11
        private const val REQUEST_MIDNIGHT = 12
        private const val MAX_REMOTE_LAYOUTS = 16
        private const val HOST_INSETS_DP = 16f
        private const val NEXT_TOUCH_DP = 56

        /** Called by the app when favorites or the selected style changes. */
        fun updateAll(context: Context, advance: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FavoriteWidgetProvider::class.java)
            )
            updateWidgets(context, manager, ids, advance)
        }

        @Synchronized
        private fun updateWidgets(
            context: Context, manager: AppWidgetManager, ids: IntArray, advance: Boolean = false
        ) {
            if (ids.isEmpty()) return
            val store = FavoriteWidgetStore(context)
            if (advance && !store.advance()) return
            val note = store.currentNote()
            val style = store.style
            val displayedChild = store.displayedChild()
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                manager.updateAppWidget(id, buildRemoteViews(context, note, options, style, displayedChild, advance))
            }
            scheduleDailyRefresh(context)
        }

        internal fun buildRemoteViews(
            context: Context,
            note: FavoriteWidgetNote?,
            options: Bundle,
            style: FavoriteWidgetStyle,
            displayedChild: Int = 0,
            animate: Boolean = false
        ): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = BundleCompat.getParcelableArrayList(
                    options,
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    SizeF::class.java
                ).orEmpty().filter { it.width > 0 && it.height > 0 }
                    .distinct().take(MAX_REMOTE_LAYOUTS)
                if (sizes.isNotEmpty()) {
                    val scale = bitmapScale(context, sizes)
                    return RemoteViews(sizes.associate { size ->
                        // Some launchers report the grid size before applying widget insets.
                        val minimum = SizeF(
                            (size.width - HOST_INSETS_DP).coerceAtLeast(1f),
                            (size.height - HOST_INSETS_DP).coerceAtLeast(1f)
                        )
                        minimum to cardViews(context, note, style, size, scale, displayedChild, animate)
                    })
                }
            }

            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val defaultSize = FavoriteWidgetCard.defaultSize
            val portrait = SizeF(
                widgetDimension(minWidth, maxWidth, defaultSize.width),
                widgetDimension(maxHeight, minHeight, defaultSize.height)
            )
            val landscape = SizeF(
                widgetDimension(maxWidth, minWidth, defaultSize.width),
                widgetDimension(minHeight, maxHeight, defaultSize.height)
            )
            val scale = bitmapScale(context, listOf(portrait, landscape).distinct())
            val portraitViews = cardViews(context, note, style, portrait, scale, displayedChild, animate)
            return if (landscape == portrait) portraitViews else RemoteViews(
                cardViews(context, note, style, landscape, scale, displayedChild, animate),
                portraitViews
            )
        }

        private fun widgetDimension(preferred: Int, alternate: Int, fallback: Float): Float =
            preferred.takeIf { it > 0 }?.toFloat() ?: alternate.takeIf { it > 0 }?.toFloat() ?: fallback

        private fun bitmapScale(context: Context, sizes: List<SizeF>): Float {
            val metrics = context.resources.displayMetrics
            val density = metrics.density.toDouble()
            val pixels = sizes.sumOf { (it.width * density) * (it.height * density) }
            // Budget for the sharp text, blurred text, and divider layers.
            val budget = metrics.widthPixels.toDouble() * metrics.heightPixels * 4 / 6
            return sqrt(budget.coerceAtLeast(1.0) / pixels).coerceAtMost(1.0).toFloat()
        }

        private fun cardViews(
            context: Context,
            note: FavoriteWidgetNote?,
            style: FavoriteWidgetStyle,
            dimensions: SizeF,
            bitmapScale: Float,
            displayedChild: Int,
            animate: Boolean
        ): RemoteViews {
            val density = context.resources.displayMetrics.density
            val size = FavoriteWidgetSize.forDimensions(dimensions.width, dimensions.height)
            val card = FavoriteWidgetCard.render(
                context,
                note,
                style,
                size,
                (dimensions.width * density).roundToInt().coerceAtLeast(1),
                (dimensions.height * density).roundToInt().coerceAtLeast(1),
                bitmapScale,
                forLauncher = true,
                blurText = animate && note != null
            )
            // The launcher applies RemoteViews in a restricted context. Sending the
            // rendered card keeps its font independent of the launcher's typeface.
            val views = RemoteViews(context.packageName, R.layout.widget_favorite_host)
            views.setInt(R.id.widget_root, "setBackgroundResource", style.background)
            views.setImageViewBitmap(R.id.widget_static_divider, card.divider)
            views.setInt(R.id.widget_static_divider, "setColorFilter", context.getColor(style.dividerColor))
            val (textView, blurView, focusView) = if (displayedChild == 0) {
                Triple(R.id.widget_card, R.id.widget_card_blur, R.id.widget_card_focus)
            } else {
                Triple(R.id.widget_card_alternate, R.id.widget_card_alternate_blur, R.id.widget_card_alternate_focus)
            }
            views.setImageViewBitmap(textView, card.bitmap)
            if (card.blurredText != null) {
                views.setImageViewBitmap(blurView, card.blurredText)
                val blurColor = if (style.isTerminal) style.dividerColor else R.color.widget_paper_foreground
                views.setInt(blurView, "setColorFilter", context.getColor(blurColor))
            }
            // The nested flipper briefly draws the blurred bitmap while sharp text fades in.
            views.setDisplayedChild(focusView, 0)
            if (card.blurredText != null) {
                views.setDisplayedChild(focusView, 1)
                views.setDisplayedChild(focusView, 0)
            }
            views.setDisplayedChild(R.id.widget_text_flipper, displayedChild)
            views.setContentDescription(R.id.widget_open_touch, card.description)
            views.setViewVisibility(
                R.id.widget_next_touch,
                if (note == null) View.GONE else View.VISIBLE
            )
            views.setOnClickPendingIntent(R.id.widget_open_touch, openAppIntent(context))
            if (note != null) {
                views.setImageViewResource(R.id.widget_next_touch, style.nextIcon)
                views.setInt(R.id.widget_next_touch, "setColorFilter", if (style.isTerminal) {
                    context.getColor(style.dividerColor)
                } else android.graphics.Color.TRANSPARENT)
                val inset = (size.paddingDp * density).roundToInt()
                val start = (NEXT_TOUCH_DP * density).roundToInt() - inset -
                    (size.nextSizeDp * density).roundToInt()
                val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
                views.setViewPadding(
                    R.id.widget_next_touch,
                    if (rtl) inset else start,
                    start,
                    if (rtl) start else inset,
                    inset
                )
                views.setOnClickPendingIntent(R.id.widget_next_touch, nextIntent(context))
            }
            return views
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun nextIntent(context: Context): PendingIntent {
            val intent = Intent(context, FavoriteWidgetProvider::class.java).setAction(ACTION_NEXT)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_NEXT,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun scheduleDailyRefresh(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val zone = ZoneId.systemDefault()
            val nextMidnight = LocalDate.now(zone)
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                nextMidnight,
                midnightIntent(context)
            )
        }

        private fun cancelDailyRefresh(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(midnightIntent(context))
        }

        private fun midnightIntent(context: Context): PendingIntent {
            val intent = Intent(context, FavoriteWidgetProvider::class.java)
                .setAction(ACTION_DAILY_REFRESH)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_MIDNIGHT,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
