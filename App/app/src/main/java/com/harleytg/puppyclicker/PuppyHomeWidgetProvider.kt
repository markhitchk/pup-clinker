package com.harleytg.puppyclicker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import java.io.File

class PuppyHomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, views(context))
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val component = android.content.ComponentName(app, PuppyHomeWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                ids.forEach { manager.updateAppWidget(it, views(app)) }
            }
        }

        private fun views(context: Context): RemoteViews {
            val snapshot = PuppyWidgetSnapshotStore.read(context)
            val views = RemoteViews(context.packageName, R.layout.puppy_home_widget)
            val name = snapshot?.puppyName ?: "Puppy Clicker"
            val treats = snapshot?.treats ?: 0L
            views.setTextViewText(R.id.widget_puppy_name, name)
            views.setTextViewText(R.id.widget_treats, "$treats Treats")
            views.setTextViewText(
                R.id.widget_status,
                if (snapshot == null) "Open Puppy Clicker to sync" else
                    "Care ${snapshot.careScore}% · Bond ${snapshot.bond}%"
            )

            val assetId = snapshot?.puppyStyle?.let { styleId ->
                runCatching { DynamicPuppyRoster.asset(styleId)?.assetId }.getOrNull()
            }
            val cached = assetId?.let { File(context.cacheDir, "puppy-stream-v3/$it.png") }
            val bitmap = cached?.takeIf(File::isFile)?.let { file ->
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            }
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_puppy_image, bitmap)
            } else {
                views.setImageViewResource(R.id.widget_puppy_image, R.drawable.source_logo)
            }

            val launchIntent = Intent(context, PuppyClickerV6Activity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context,
                41010,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
