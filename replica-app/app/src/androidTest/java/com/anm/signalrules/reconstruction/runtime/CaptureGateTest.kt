package com.anm.signalrules.reconstruction.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureGateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun reset() {
        context.getSharedPreferences("capture_gate", Context.MODE_PRIVATE).edit().clear().commit()
        CaptureGate.load(context)
    }

    @After
    fun restoreDefault() {
        CaptureGate.setPaused(context, false)
    }

    @Test
    fun pauseStateIsPersistedAndRestoredWithoutChangingListenerBinding() {
        CaptureGate.setPaused(context, true)
        assertTrue(CaptureGate.isPaused())

        CaptureGate.load(context)

        assertTrue(CaptureGate.isPaused())
        CaptureGate.setPaused(context, false)
        assertFalse(CaptureGate.isPaused())
    }
}
