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
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
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

            val nowCal = Calendar.getInstance().apply { timeInMillis = now }
            val currentYear = nowCal.get(Calendar.YEAR)
            val currentMonth = nowCal.get(Calendar.MONTH)
            val currentDay = nowCal.get(Calendar.DAY_OF_MONTH)
            val currentDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)

            // 本地今天 00:00:00 的時間戳
            val todayStartLocal = Calendar.getInstance().apply {
                clear()
                set(currentYear, currentMonth, currentDay, 0, 0, 0)
            }.timeInMillis

            // Android 全天行程在底層統一以 UTC 00:00:00 記錄，計算對應的 UTC 今日時間點
            val todayUtcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(currentYear, currentMonth, currentDay, 0, 0, 0)
            }.timeInMillis

            while (cursor.moveToNext() && eventList.size < 3) {
                signal.throwIfCanceled()
                val title = cursor.getString(titleIdx) ?: context.getString(R.string.event_untitled)
                val beginMillis = cursor.getLong(beginIdx)
                val endMillis = cursor.getLong(endIdx)
                val isAllDay = cursor.getInt(allDayIdx) == 1

                if (isAllDay) {
                    // 全天行程的 endMillis 是結束日的隔天 00:00 UTC（Exclusive）
                    // 若結束時間小於等於今日起點，代表昨天或更早以前就結束了，跳過
                    if (endMillis <= todayUtcMidnight) {
                        continue
                    }

                    // 只要「開始時間 <= 今天」且「結束時間 > 今天」，代表今天正在該多日行程中
                    val isCoveringToday = (beginMillis <= todayUtcMidnight && endMillis > todayUtcMidnight)

                    val label = if (isCoveringToday) {
                        context.getString(R.string.event_all_day_today)
                    } else {
                        dayFormat.timeZone = TimeZone.getTimeZone("UTC")
                        "${dayFormat.format(Date(beginMillis))} ${context.getString(R.string.event_all_day)}"
                    }

                    eventList.add(UpcomingCalendarEvent(title = title, timeLabel = label))
                } else {
                    // 非全天一般行程：已結束則跳過
                    if (endMillis <= now) {
                        continue
                    }

                    val eventCal = Calendar.getInstance().apply { timeInMillis = beginMillis }
                    val isToday = (eventCal.get(Calendar.DAY_OF_YEAR) == currentDayOfYear &&
                            eventCal.get(Calendar.YEAR) == currentYear)

                    dayFormat.timeZone = TimeZone.getDefault()
                    timeFormat.timeZone = TimeZone.getDefault()

                    val label = when {
                        // 跨天且今天仍在進行中的行程
                        beginMillis < todayStartLocal && endMillis > now -> {
                            context.getString(R.string.event_all_day_today)
                        }
                        // 今天內的行程顯示具體開始時間（如 14:00）
                        isToday -> {
                            timeFormat.format(Date(beginMillis))
                        }
                        // 明後天的行程顯示日期與時間（如 11/9 10:00）
                        else -> {
                            "${dayFormat.format(Date(beginMillis))} ${timeFormat.format(Date(beginMillis))}"
                        }
                    }

                    eventList.add(UpcomingCalendarEvent(title = title, timeLabel = label))
                }
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
