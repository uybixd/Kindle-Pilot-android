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
            ACTION_NEXT -> {
                if (FavoriteWidgetStore(context).advance()) updateAll(context)
            }
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
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FavoriteWidgetProvider::class.java)
            )
            updateWidgets(context, manager, ids)
        }

        @Synchronized
        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val store = FavoriteWidgetStore(context)
            val note = store.currentNote()
            val style = store.style
            ids.forEach { id ->
                manager.updateAppWidget(id, buildRemoteViews(context, note, manager.getAppWidgetOptions(id), style))
            }
            scheduleDailyRefresh(context)
        }

        internal fun buildRemoteViews(
            context: Context,
            note: FavoriteWidgetNote?,
            options: Bundle,
            style: FavoriteWidgetStyle
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
                        minimum to cardViews(context, note, style, size, scale)
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
            val portraitViews = cardViews(context, note, style, portrait, scale)
            return if (landscape == portrait) portraitViews else RemoteViews(
                cardViews(context, note, style, landscape, scale),
                portraitViews
            )
        }

        private fun widgetDimension(preferred: Int, alternate: Int, fallback: Float): Float =
            preferred.takeIf { it > 0 }?.toFloat() ?: alternate.takeIf { it > 0 }?.toFloat() ?: fallback

        private fun bitmapScale(context: Context, sizes: List<SizeF>): Float {
            val metrics = context.resources.displayMetrics
            val density = metrics.density.toDouble()
            val pixels = sizes.sumOf { (it.width * density) * (it.height * density) }
            // Android caps the combined bitmap memory of all layouts in an update.
            val budget = metrics.widthPixels.toDouble() * metrics.heightPixels
            return sqrt(budget.coerceAtLeast(1.0) / pixels).coerceAtMost(1.0).toFloat()
        }

        private fun cardViews(
            context: Context,
            note: FavoriteWidgetNote?,
            style: FavoriteWidgetStyle,
            dimensions: SizeF,
            bitmapScale: Float
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
                // Keep the terminal border on the host when the launcher scales the bitmap.
                drawBackground = style != FavoriteWidgetStyle.TERMINAL
            )
            // The launcher applies RemoteViews in a restricted context. Sending the
            // rendered card keeps its font independent of the launcher's typeface.
            val views = RemoteViews(context.packageName, R.layout.widget_favorite_host)
            views.setInt(R.id.widget_root, "setBackgroundResource", style.background)
            views.setImageViewBitmap(R.id.widget_card, card.bitmap)
            views.setContentDescription(R.id.widget_root, card.description)
            views.setViewVisibility(
                R.id.widget_next_touch,
                if (note == null) View.GONE else View.VISIBLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            if (note != null) {
                views.setImageViewResource(R.id.widget_next_touch, style.nextIcon)
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
