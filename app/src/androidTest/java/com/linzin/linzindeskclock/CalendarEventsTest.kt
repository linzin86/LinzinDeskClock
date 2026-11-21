package com.linzin.linzindeskclock

import android.database.MatrixCursor
import android.database.CursorWrapper
import android.os.Looper
import android.provider.CalendarContract
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarEventsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun cursor() = MatrixCursor(arrayOf(CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY))

    @Test fun slowQueryLeavesTheUiOperable() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val queriedOnMain = AtomicBoolean(false)
        val clicks = mutableStateOf(0)
        try {
            compose.setContent {
                LaunchedEffect(Unit) {
                    fetchUpcomingCalendarEvents(context, hasPermission = { true }, query = { _, _, _, _ ->
                        queriedOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                        entered.countDown()
                        release.await(5, TimeUnit.SECONDS)
                        finished.set(true)
                        cursor()
                    })
                }
                Button(onClick = { clicks.value++ }) { Text("Ping ${clicks.value}") }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            compose.onNodeWithText("Ping 0").performClick()
            compose.onNodeWithText("Ping 1").assertExists()
            assertFalse("UI click must complete while the provider is still blocked", finished.get())
            assertFalse("Provider query must not run on the UI thread", queriedOnMain.get())
        } finally { release.countDown() }
    }

    @Test fun cancellationSignalsTheProviderClosesItsCursorAndNeverPublishes() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val signalled = CountDownLatch(1)
        val rows = cursor()
        val applied = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val job = scope.launch {
            fetchUpcomingCalendarEvents(context, hasPermission = { true }, query = { _, _, _, signal ->
                signal.setOnCancelListener { signalled.countDown(); release.countDown() }
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                rows
            })
            applied.set(true)
        }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            job.cancel()
            assertTrue("Cancellation must reach a blocked provider", signalled.await(1, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            runBlocking { job.join() }
            scope.cancel()
        }
        assertFalse(applied.get())
        assertTrue(rows.isClosed)
    }

    @Test fun deniedPermissionSkipsTheQuery() = runBlocking {
        val events = fetchUpcomingCalendarEvents(context, hasPermission = { false }, query = { _, _, _, _ ->
            fail("Denied permission must not query"); null
        })
        assertTrue(events.isEmpty())
    }

    @Test fun permissionCheckFailureIsHandledWithoutCrashingTheOwner() = runBlocking {
        assertTrue(fetchUpcomingCalendarEvents(context,
            hasPermission = { throw SecurityException("permission service unavailable") },
            query = { _, _, _, _ -> fail("Must not query"); null }).isEmpty())
    }

    @Test fun leavingTheCompositionCancelsTheQueryAndDiscardsItsResult() {
        val visible = mutableStateOf(true)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val signalled = CountDownLatch(1)
        val applied = AtomicBoolean(false)
        try {
            compose.setContent {
                if (visible.value) LaunchedEffect(Unit) {
                    fetchUpcomingCalendarEvents(context, hasPermission = { true }, query = { _, _, _, signal ->
                        signal.setOnCancelListener { signalled.countDown(); release.countDown() }
                        entered.countDown()
                        release.await(5, TimeUnit.SECONDS)
                        object : CursorWrapper(cursor()) {
                            override fun close() { super.close(); closed.countDown() }
                        }
                    })
                    applied.set(true)
                }
                Button(onClick = { visible.value = false }) { Text("Leave") }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            compose.onNodeWithText("Leave").performClick()
            compose.waitForIdle()
            assertTrue(signalled.await(2, TimeUnit.SECONDS))
            assertTrue(closed.await(2, TimeUnit.SECONDS))
            assertFalse(applied.get())
        } finally { release.countDown() }
    }

    @Test fun parsingFailureStillClosesTheCursor() = runBlocking {
        val malformed = MatrixCursor(arrayOf("unexpected"))
        assertTrue(fetchUpcomingCalendarEvents(context, hasPermission = { true }, query = { _, _, _, _ ->
            malformed
        }).isEmpty())
        assertTrue(malformed.isClosed)
    }

    @Test fun permissionRevocationDuringQueryReturnsAnEmptyResult() = runBlocking {
        assertTrue(fetchUpcomingCalendarEvents(context, hasPermission = { true }, query = { _, _, _, _ ->
            throw SecurityException("permission revoked")
        }).isEmpty())
    }

    @Test fun cancellationIsNotAnEmptySuccess() = runBlocking {
        try {
            fetchUpcomingCalendarEvents(context, hasPermission = { true }, query = { _, _, _, _ ->
                throw CancellationException("query cancelled")
            })
            fail("Cancellation must reach the caller")
        } catch (_: CancellationException) { }
    }

    @Test fun successfulQueryPreservesContentRulesAndClosesTheCursor() = runBlocking {
        val now = System.currentTimeMillis()
        val rows = cursor().apply {
            addRow(arrayOf("Expired", now - 2000, now - 1000, 0))
            addRow(arrayOf("Meeting", now + 60_000, now + 120_000, 0))
        }
        val events = fetchUpcomingCalendarEvents(context, hasPermission = { true }, query = { uri, columns, order, _ ->
            assertEquals("content", uri.scheme)
            assertEquals(4, columns.size)
            assertEquals("begin ASC", order)
            rows
        })
        assertEquals(listOf("Meeting"), events.map { it.title })
        assertTrue(events.single().timeLabel.isNotBlank())
        assertTrue(rows.isClosed)
    }
}
