package com.sysadmindoc.nono.runtime

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sysadmindoc.nono.data.SignalDatabase
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureSelfTestRoundTripTest {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private var listenerWasEnabled = false

    @Before
    fun enableListenerAndPostingPermission() {
        CaptureSelfTestProbe.resetForTest()
        listenerWasEnabled = NotificationManagerCompat.getEnabledListenerPackages(application)
            .contains(application.packageName)
        val component = ComponentName(application, SignalNotificationListener::class.java)
        shell("cmd notification allow_listener ${component.flattenToString()}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shell("pm grant ${application.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
            assertEquals(
                android.content.pm.PackageManager.PERMISSION_GRANTED,
                ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS),
            )
        }
        awaitCondition("the listener never connected") {
            ListenerHealth.connection.value == ListenerHealth.Connection.CONNECTED
        }
    }

    @After
    fun restoreListenerAccess() {
        CaptureSelfTestProbe.resetForTest()
        if (!listenerWasEnabled) {
            val component = ComponentName(application, SignalNotificationListener::class.java)
            shell("cmd notification disallow_listener ${component.flattenToString()}")
        }
    }

    @Test
    fun postedTestReachesTheListenerWithoutEnteringHistoryOrCounters() = runBlocking {
        val dao = SignalDatabase.get(application).notificationDao()
        val historyBefore = dao.observeTotalCount().first()
        val eventsBefore = ListenerHealth.eventCount.value
        val ingestionBefore = ListenerHealth.ingestionMetrics.value
        val lastCaptureBefore = ListenerActivityLog.lastEventAt(application)

        val outcome = CaptureSelfTest.run(application, timeoutMillis = 5_000L)

        assertTrue("actual outcome: $outcome", outcome is CaptureSelfTestOutcome.Passed)
        delay(300L)
        assertEquals(historyBefore, dao.observeTotalCount().first())
        assertEquals(eventsBefore, ListenerHealth.eventCount.value)
        assertEquals(ingestionBefore, ListenerHealth.ingestionMetrics.value)
        assertEquals(lastCaptureBefore, ListenerActivityLog.lastEventAt(application))
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }
    }

    private fun awaitCondition(
        reason: String,
        timeoutMillis: Long = 5_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25L)
        }
        throw AssertionError(reason + " (connection: " + ListenerHealth.connection.value + ")")
    }
}
