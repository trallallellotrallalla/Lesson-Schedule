package com.shedule2

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class WidgetLessonRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return LessonFactory(applicationContext, intent)
    }

    private class LessonFactory(
        private val context: android.content.Context,
        intent: Intent
    ) : RemoteViewsFactory {

        private val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        private var lessons: List<SimpleLesson> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            lessons = ScheduleWidgetData.getLessonsForSelectedDay(context, appWidgetId)
        }

        override fun onDestroy() = Unit

        override fun getCount(): Int = lessons.size

        override fun getViewAt(position: Int): RemoteViews {
            val lesson = lessons.getOrNull(position)
                ?: return RemoteViews(context.packageName, R.layout.widget_lesson_item)

            return RemoteViews(context.packageName, R.layout.widget_lesson_item).apply {
                val lessonTime = lesson.time.ifBlank { lesson.slotLabels.joinToString(", ") }
                setTextViewText(
                    R.id.widgetLessonTime,
                    lessonTime.takeIf { it.isNotBlank() }?.let { "$CLOCK_ICON $it" }.orEmpty()
                )
                setTextViewText(R.id.widgetLessonTitle, buildLessonTitle(lesson))

                val meta = buildLessonMeta(lesson)

                setTextViewText(R.id.widgetLessonMeta, meta)
                setViewVisibility(
                    R.id.widgetLessonMeta,
                    if (meta.isBlank()) View.GONE else View.VISIBLE
                )
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = true

        private fun buildLessonTitle(lesson: SimpleLesson): CharSequence {
            val title = lesson.subject.ifBlank { "Без названия" }
            if (!lesson.isCanceled) return title

            return SpannableStringBuilder().apply {
                val titleStart = length
                append(title)
                setSpan(
                    StrikethroughSpan(),
                    titleStart,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                append(META_SEPARATOR)
                append(CANCELED_LABEL)
            }
        }

        private fun buildLessonMeta(lesson: SimpleLesson): CharSequence {
            val meta = SpannableStringBuilder()

            fun appendPart(value: String, icon: String = "", bold: Boolean = false) {
                val text = value.trim()
                if (text.isBlank()) return

                if (meta.isNotEmpty()) {
                    meta.append(META_SEPARATOR)
                }

                if (icon.isNotBlank()) {
                    meta.append(icon)
                    meta.append(" ")
                }

                val start = meta.length
                meta.append(text)
                if (bold) {
                    meta.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start,
                        meta.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            appendPart(lesson.type)
            appendPart(lesson.room, icon = PIN_ICON, bold = true)
            appendPart(lesson.teacher, icon = PERSON_ICON)
            lesson.note
                ?.removePrefix(CANCELED_LABEL)
                ?.trimStart('.', ' ')
                ?.let { appendPart(it) }

            return meta
        }

        companion object {
            private const val META_SEPARATOR = " • "
            private const val CANCELED_LABEL = "Отменено"
            private const val CLOCK_ICON = "🕒"
            private const val PIN_ICON = "📍"
            private const val PERSON_ICON = "👤"
        }
    }
}
