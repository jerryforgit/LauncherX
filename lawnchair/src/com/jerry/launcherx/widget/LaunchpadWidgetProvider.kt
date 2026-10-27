package com.jerry.launcherx.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.PathShapeDelegate
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.jerry.launcherx.preferences2.firstCached
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R

/**
 * 1x1 "Launchpad" widget: a shortcut tile that opens the app drawer.
 *
 * The tile is rendered to a square bitmap and shown with `fitCenter`, so a non-square dock
 * or workspace cell uses `min(width, height)` as the diameter instead of stretching it into
 * an ellipse. The background uses the launcher's current icon shape (circle, squircle,
 * custom path, …).
 */
class LaunchpadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = RemoteViews(context.packageName, R.layout.launchpad_widget)
        val intent = Intent(ACTION_OPEN_DRAWER).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.launchpad_widget_image, pending)
        views.setImageViewBitmap(
            R.id.launchpad_widget_image,
            buildTile(context, currentIconSizePx(context)),
        )
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    private fun buildTile(context: Context, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.density = context.resources.displayMetrics.densityDpi
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ContextCompat.getColor(context, R.color.launchpad_widget_bg)
        PathShapeDelegate(currentIconShape(context))
            .drawShape(canvas, 0f, 0f, size / 2f, paint)

        val iconSize = (size * ICON_FRACTION).toInt()
        val inset = (size - iconSize) / 2
        ContextCompat.getDrawable(context, R.drawable.ic_launchpad)?.apply {
            setBounds(inset, inset, inset + iconSize, inset + iconSize)
            setTint(ContextCompat.getColor(context, R.color.launchpad_widget_icon_tint))
            draw(canvas)
        }
        return bitmap
    }

    private fun currentIconSizePx(context: Context): Int {
        val size = runCatching {
            LauncherAppState.getIDP(context).getDeviceProfile(context).iconSizePx
        }.getOrDefault(0)
        if (size > 0) return size
        return (56 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun currentIconShape(context: Context): IconShape {
        val key = runCatching {
            PreferenceManager2.getInstance(context).iconShape.firstCached()
        }.getOrNull() as? String ?: IconShape.Circle.key
        return IconShape.fromString(key, context) ?: IconShape.Circle
    }

    companion object {
        const val ACTION_OPEN_DRAWER = "com.jerry.launcherx.widget.OPEN_APP_DRAWER"
        private const val ICON_FRACTION = 0.52f

        /** Re-renders every placed Launchpad tile, e.g. after the icon shape changed. */
        @JvmStatic
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, LaunchpadWidgetProvider::class.java),
            )
            if (ids.isNotEmpty()) {
                LaunchpadWidgetProvider().onUpdate(context, manager, ids)
            }
        }
    }
}
