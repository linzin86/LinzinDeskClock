package com.linzin.linzindeskclock

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.CalendarContract
import android.util.Log
import com.linzin.clockcore.CalendarEventLabels
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 查詢未來 48 小時內的日曆行程（支援單日、跨日全天行程與進行中事件）
 */
internal suspend fun fetchUpcomingCalendarEvents(
    context: Context,
    hasPermission: () -> Boolean = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    },
    query: (Uri, Array<String>, String, CancellationSignal) -> Cursor? = { uri, columns, order, signal ->
        context.contentResolver.query(uri, columns, null, null, order, signal)
    }
): List<UpcomingCalendarEvent> = suspendCancellableCoroutine { continuation ->
    val signal = CancellationSignal()
    val worker = CoroutineScope(continuation.context).launch(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()
            val events = readCalendarEvents(context, hasPermission, query, signal)
            coroutineContext.ensureActive()
            continuation.resume(events)
        } catch (cancelled: CancellationException) {
            continuation.cancel(cancelled)
        } catch (error: Exception) {
            Log.e("DeskClockCalendar", "Unable to read calendar", error)
            continuation.resume(emptyList())
        }
    }
    continuation.invokeOnCancellation {
        signal.cancel()
        worker.cancel()
    }
}

private fun readCalendarEvents(
    context: Context,
    hasPermission: () -> Boolean,
    query: (Uri, Array<String>, String, CancellationSignal) -> Cursor?,
    signal: CancellationSignal
): List<UpcomingCalendarEvent> {
    if (!hasPermission()) return emptyList()

    val eventList = mutableListOf<UpcomingCalendarEvent>()
    val now = System.currentTimeMillis()
    val endWindow = now + (48 * 60 * 60 * 1000L)

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, now)
    ContentUris.appendId(builder, endWindow)

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY
    )
    val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

    try {
        query(builder.build(), projection, sortOrder, signal)?.use { cursor ->
            signal.throwIfCanceled()
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)

            val timeFormat = SimpleDateFormat("H:mm", Locale.US)
            val dayFormat = SimpleDateFormat("M/d", Locale.US)

            while (cursor.moveToNext() && eventList.size < 3) {
                signal.throwIfCanceled()
                val title = cursor.getString(titleIdx) ?: context.getString(R.string.event_untitled)
                val beginMillis = cursor.getLong(beginIdx)
                val endMillis = cursor.getLong(endIdx)
                val isAllDay = cursor.getInt(allDayIdx) == 1

                val kind = CalendarEventLabels.classify(beginMillis, endMillis, isAllDay, now, TimeZone.getDefault())
                if (kind == CalendarEventLabels.Kind.HIDDEN) continue
                dayFormat.timeZone = if (kind == CalendarEventLabels.Kind.FUTURE_ALL_DAY) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
                timeFormat.timeZone = TimeZone.getDefault()
                val label = when (kind) {
                    CalendarEventLabels.Kind.TODAY_ALL_DAY -> context.getString(R.string.event_all_day_today)
                    CalendarEventLabels.Kind.FUTURE_ALL_DAY -> "${dayFormat.format(Date(beginMillis))} ${context.getString(R.string.event_all_day)}"
                    CalendarEventLabels.Kind.TODAY_TIME -> timeFormat.format(Date(beginMillis))
                    else -> "${dayFormat.format(Date(beginMillis))} ${timeFormat.format(Date(beginMillis))}"
                }
                eventList.add(UpcomingCalendarEvent(title = title, timeLabel = label))
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (cancelled: android.os.OperationCanceledException) {
        throw CancellationException("Calendar query cancelled", cancelled)
    } catch (e: Exception) {
        Log.e("DeskClockCalendar", "Error querying calendar instances", e)
    }

    return eventList
}
