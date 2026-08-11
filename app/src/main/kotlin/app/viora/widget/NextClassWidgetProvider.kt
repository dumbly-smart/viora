package app.viora.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.viora.MainActivity
import app.viora.R
import app.viora.VioraGraph
import app.viora.database.VioraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class NextClassWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, manager, ids, goAsync())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextClassWidgetProvider::class.java))
            refresh(context, manager, ids, goAsync())
        }
    }

    companion object {
        private const val ACTION_REFRESH = "app.viora.widget.REFRESH_NEXT_CLASS"
        private val academicZone: ZoneId = ZoneId.of("Asia/Kolkata")
        private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, NextClassWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(provider)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(Intent(context, NextClassWidgetProvider::class.java).setAction(ACTION_REFRESH))
            }
        }

        private fun refresh(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            pendingResult: PendingResult,
        ) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val views = createViews(appContext)
                    ids.forEach { manager.updateAppWidget(it, views) }
                } finally {
                    pendingResult.finish()
                }
            }
        }

        private suspend fun createViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.next_class_widget)
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val refresh = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, NextClassWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_refresh, refresh)

            val settings = context.getSharedPreferences(VioraGraph.SETTINGS_NAME, Context.MODE_PRIVATE)
            val semesterId = settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
            if (semesterId == null) {
                views.setTextViewText(R.id.widget_eyebrow, "VIORA")
                views.setTextViewText(R.id.widget_title, "Open Viora to get started")
                views.setTextViewText(R.id.widget_detail, "Your cached next class will appear here")
                return views
            }

            val dao = VioraDatabase.get(context).academicDao()
            val next = NextClassResolver.resolve(
                slots = dao.timetableSnapshot(semesterId),
                calendar = dao.calendarSnapshot(semesterId),
                now = ZonedDateTime.now(academicZone),
            )
            if (next == null) {
                views.setTextViewText(R.id.widget_eyebrow, "SCHEDULE")
                views.setTextViewText(R.id.widget_title, "No class in the next 7 days")
                views.setTextViewText(R.id.widget_detail, "Tap refresh after your next VTOP sync")
            } else {
                val slot = next.slot
                views.setTextViewText(R.id.widget_eyebrow, if (next.happeningNow) "HAPPENING NOW" else "NEXT CLASS")
                views.setTextViewText(R.id.widget_title, "${slot.code} · ${slot.title}")
                val day = next.startsAt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                val whenText = if (next.happeningNow) "Until ${next.startsAt.toLocalDate().atStartOfDay(academicZone).plusMinutes(slot.endMinute.toLong()).format(timeFormatter)}" else "$day · ${next.startsAt.format(timeFormatter)}"
                views.setTextViewText(R.id.widget_detail, listOf(whenText, slot.venue).filter(String::isNotBlank).joinToString(" · "))
            }
            return views
        }
    }
}
