package com.shedule2

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PREVIOUS_DAY, ACTION_NEXT_DAY -> {
                if (isRefreshing) return

                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val currentDay = ScheduleWidgetData.getEffectiveSelectedDay(context, appWidgetId)
                    val targetDay = if (intent.action == ACTION_NEXT_DAY) {
                        ScheduleWidgetData.getNextCachedDay(context, currentDay)
                    } else {
                        ScheduleWidgetData.getPreviousCachedDay(context, currentDay)
                    }

                    ScheduleWidgetData.setSelectedDay(context, appWidgetId, targetDay)
                    val manager = AppWidgetManager.getInstance(context)
                    updateAppWidget(context, manager, appWidgetId)
                    manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetLessonList)
                }
            }

            ACTION_REFRESH_SCHEDULE -> {
                if (isRefreshing) return

                val appContext = context.applicationContext
                val pendingResult = goAsync()
                isRefreshing = true
                showCurrentDateInAllWidgets(appContext)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        refreshScheduleInBackground(appContext)
                    } catch (_: Throwable) {
                        // Background refresh: errors are silent for the widget.
                    } finally {
                        isRefreshing = false
                        showCurrentDateInAllWidgets(appContext)
                        pendingResult.finish()
                    }
                }
            }

            ACTION_REFRESH_ALL,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> refreshAllWidgets(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { ScheduleWidgetData.removeSelectedDay(context, it) }
    }

    companion object {
        const val ACTION_PREVIOUS_DAY = "com.shedule2.action.PREVIOUS_WIDGET_DAY"
        const val ACTION_NEXT_DAY = "com.shedule2.action.NEXT_WIDGET_DAY"
        const val ACTION_REFRESH_ALL = "com.shedule2.action.REFRESH_WIDGETS"
        const val ACTION_REFRESH_SCHEDULE = "com.shedule2.action.REFRESH_SCHEDULE"

        @Volatile
        private var isRefreshing: Boolean = false

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ScheduleWidgetProvider::class.java)
            )
            ids.forEach { updateAppWidget(context, manager, it) }
            if (ids.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetLessonList)
            }
        }

        fun showCurrentDateInAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ScheduleWidgetProvider::class.java)
            )
            ids.forEach { appWidgetId ->
                ScheduleWidgetData.removeSelectedDay(context, appWidgetId)
                updateAppWidget(context, manager, appWidgetId)
            }
            if (ids.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetLessonList)
            }
        }

        private fun refreshScheduleInBackground(context: Context) {
            val credentials = CredentialsStorage.load(context) ?: return

            val cookieJar = SessionCookieJar(context)
            val httpClient = AuthApi.createClient(cookieJar)
            val authApi = AuthApi(httpClient)
            val scheduleApi = ScheduleApi(httpClient)

            val authResult = authApi.ensureAuthorized(
                login = credentials.login.trim(),
                password = credentials.password.trim()
            )
            val activeGroup = when (authResult) {
                is AuthResult.Success -> authResult.activeGroup
                is AuthResult.Error -> return
            }

            val groupId = ScheduleCache.loadGroupId(context)?.takeIf { it.isNotBlank() }
                ?: activeGroup.groupId

            val formatter = DateTimeFormatter.ofPattern("d-M-yyyy")
            val requestDate = LocalDate.now().format(formatter)

            val result = scheduleApi.fetchSchedule(date = requestDate, groupId = groupId)
            if (result is ScheduleResult.Success) {
                ScheduleCache.save(
                    context = context,
                    rawJson = result.json,
                    date = requestDate,
                    groupId = groupId
                )
                refreshAllWidgets(context)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.schedule_widget)
            val selectedDay = ScheduleWidgetData.getEffectiveSelectedDay(context, appWidgetId)
            val selectedHeader = ScheduleWidgetData.getSelectedWeekDayHeader(context, appWidgetId)
            val previousHeader = ScheduleWidgetData.getPreviousCachedDayHeader(context, selectedDay)
            val nextHeader = ScheduleWidgetData.getNextCachedDayHeader(context, selectedDay)
            val hasCachedDays = ScheduleWidgetData.getCachedWeekDays(context).isNotEmpty()

            views.setTextViewText(
                R.id.widgetDateText,
                selectedHeader?.date?.replace('-', '.') ?: "Нет даты"
            )
            views.setTextViewText(
                R.id.widgetDayCaption,
                selectedHeader?.dayName ?: "Расписание не загружено"
            )
            views.setTextViewText(
                R.id.widgetPreviousDayButton,
                previousHeader?.let { "< ${it.dayName}" } ?: "< День"
            )
            views.setTextViewText(
                R.id.widgetNextDayButton,
                nextHeader?.let { "${it.dayName} >" } ?: "День >"
            )

            views.setTextViewText(
                R.id.widgetEmptyText,
                if (!hasCachedDays) {
                    "Откройте приложение и загрузите расписание, чтобы заполнить виджет."
                } else {
                    "На этот день нет занятий. Сегодня выходной!"
                }
            )

            views.setViewVisibility(
                R.id.widgetDayControls,
                if (hasCachedDays) View.VISIBLE else View.GONE
            )

            val controlsAlpha = if (isRefreshing) 0.38f else 1f
            views.setFloat(R.id.widgetPreviousDayButton, "setAlpha", controlsAlpha)
            views.setFloat(R.id.widgetNextDayButton, "setAlpha", controlsAlpha)
            views.setFloat(R.id.widgetRefreshButton, "setAlpha", controlsAlpha)

            if (isRefreshing) {
                views.setOnClickPendingIntent(R.id.widgetPreviousDayButton, null)
                views.setOnClickPendingIntent(R.id.widgetNextDayButton, null)
                views.setOnClickPendingIntent(R.id.widgetRefreshButton, null)
            } else {
                views.setOnClickPendingIntent(
                    R.id.widgetPreviousDayButton,
                    daySwitchPendingIntent(context, appWidgetId, ACTION_PREVIOUS_DAY)
                )
                views.setOnClickPendingIntent(
                    R.id.widgetNextDayButton,
                    daySwitchPendingIntent(context, appWidgetId, ACTION_NEXT_DAY)
                )
                views.setOnClickPendingIntent(
                    R.id.widgetRefreshButton,
                    refreshSchedulePendingIntent(context, appWidgetId)
                )
            }

            val lessonsServiceIntent = Intent(context, WidgetLessonRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widgetLessonList, lessonsServiceIntent)
            views.setEmptyView(R.id.widgetLessonList, R.id.widgetEmptyText)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun refreshSchedulePendingIntent(
            context: Context,
            appWidgetId: Int
        ): PendingIntent {
            val intent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_SCHEDULE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse("shedule2://widget/$appWidgetId/refresh")
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId + ACTION_REFRESH_SCHEDULE.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun daySwitchPendingIntent(
            context: Context,
            appWidgetId: Int,
            action: String
        ): PendingIntent {
            val intent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse("shedule2://widget/$appWidgetId/$action")
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
