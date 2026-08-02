package com.anm.signalrules.reconstruction.runtime

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local fast path for the persisted capture pause setting. The listener reads this
 * value without touching disk on the notification callback thread; only the tile/settings
 * action writes the preference.
 */
object CaptureGate {
    private const val PREFERENCES = "capture_gate"
    private const val PAUSED = "paused"
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun load(context: Context) {
        _paused.value = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(PAUSED, false)
    }

    fun setPaused(context: Context, paused: Boolean) {
        _paused.value = paused
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PAUSED, paused)
            .apply()
    }

    fun isPaused(): Boolean = _paused.value
}
