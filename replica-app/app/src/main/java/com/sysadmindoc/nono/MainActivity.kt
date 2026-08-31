package com.sysadmindoc.nono

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sysadmindoc.nono.audit.readAuditState
import com.sysadmindoc.nono.runtime.APP_LOCK_SETTING
import com.sysadmindoc.nono.ui.SignalApp
import com.sysadmindoc.nono.ui.SignalTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val requestedAuditState = MutableStateFlow("")

    /**
     * The unlock, handed to Android's own confirm screen.
     *
     * Deliberately the platform's device-credential prompt rather than a biometric library. It
     * needs no dependency and no permission, which matters for an app whose permission list is
     * one of its stated properties, and it already offers whatever the user has enrolled.
     */
    private val unlockRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val model: MainViewModel by viewModels()
        if (result.resultCode == RESULT_OK) model.onAppUnlocked() else model.onAppUnlockFailed()
    }

    private fun confirmDeviceCredential() {
        val keyguard = ContextCompat.getSystemService(this, KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            "NoNo",
            "Unlock to see your rules and history",
        )
        if (intent == null) {
            // No screen lock to confirm against. Staying locked would shut the user out of their
            // own rules with nothing they could do about it.
            val model: MainViewModel by viewModels()
            model.refreshAppLock()
            return
        }
        unlockRequest.launch(intent)
    }

    /** Rule a pinned launcher shortcut asked for, or [NO_RULE] when the app was opened normally. */
    private val requestedRuleId = MutableStateFlow(NO_RULE)

    companion object {
        const val EXTRA_RULE_ID = "com.sysadmindoc.nono.RULE_ID"
        private const val NO_RULE = -1L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(10, 11, 13)),
        )
        requestedAuditState.value = readAuditState(intent)
        requestedRuleId.value = intent.getLongExtra(EXTRA_RULE_ID, NO_RULE)
        setContent {
            val model: MainViewModel = viewModel()
            val state by model.state.collectAsState()
            SignalTheme(state.settings["Theme"] ?: "Dark") {
                val requested by requestedAuditState.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current
                val shortcutRuleId by requestedRuleId.collectAsState()
                LaunchedEffect(requested) {
                    model.applyAuditState(requested)
                }
                LaunchedEffect(shortcutRuleId, state.rulesLoaded) {
                    // Waits for the store to be read, not for the list to be non-empty: a user
                    // with no rules left would otherwise have the shortcut silently do nothing
                    // rather than say the rule is gone, and it would fire later out of nowhere.
                    if (shortcutRuleId != NO_RULE && state.rulesLoaded) {
                        model.openRuleFromShortcut(shortcutRuleId)
                        requestedRuleId.value = NO_RULE
                        // Consumed, so a configuration change that recreates the Activity does
                        // not replay it and overwrite a draft the user has since edited.
                        intent.removeExtra(EXTRA_RULE_ID)
                    }
                }
                // The recents thumbnail is taken as the app leaves, before the grace period has
                // run out, so without this the lock hides History from the user and shows it to
                // anyone who opens the task switcher. Applied only while the setting is on: it
                // also blocks screenshots, which nobody else asked for.
                val lockEnabled = state.settings[APP_LOCK_SETTING] == "On"
                DisposableEffect(lockEnabled) {
                    if (lockEnabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    onDispose { }
                }
                DisposableEffect(lifecycleOwner, model) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                model.refreshCapabilities()
                                model.refreshAppLock()
                            }
                            // The moment the app leaves the foreground is when the grace period
                            // starts. Recomputed on the way out as well as on the way back, so a
                            // process killed while backgrounded still returns locked.
                            Lifecycle.Event.ON_STOP -> model.refreshAppLock(leftForeground = true)
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    model.refreshCapabilities()
                    model.refreshAppLock()
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                SignalApp(model, onUnlockRequested = ::confirmDeviceCredential)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedAuditState.value = readAuditState(intent)
        requestedRuleId.value = intent.getLongExtra(EXTRA_RULE_ID, NO_RULE)
    }
}
