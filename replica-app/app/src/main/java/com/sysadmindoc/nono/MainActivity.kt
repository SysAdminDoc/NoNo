package com.sysadmindoc.nono

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sysadmindoc.nono.audit.readAuditState
import com.sysadmindoc.nono.ui.SignalApp
import com.sysadmindoc.nono.ui.SignalTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val requestedAuditState = MutableStateFlow("")

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
                DisposableEffect(lifecycleOwner, model) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) model.refreshCapabilities()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    model.refreshCapabilities()
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                SignalApp(model)
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
