package com.anm.signalrules.reconstruction

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
import com.anm.signalrules.reconstruction.ui.SignalApp
import com.anm.signalrules.reconstruction.ui.SignalTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val requestedAuditState = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(10, 11, 13)),
        )
        requestedAuditState.value = intent.getStringExtra(EXTRA_REPLICA_STATE).orEmpty()
        setContent {
            SignalTheme {
                val model: MainViewModel = viewModel()
                val requested by requestedAuditState.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(requested) {
                    model.applyAuditState(requested)
                }
                DisposableEffect(lifecycleOwner, model) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) model.refreshOnboardingCapabilities()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    model.refreshOnboardingCapabilities()
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                SignalApp(model)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedAuditState.value = intent.getStringExtra(EXTRA_REPLICA_STATE).orEmpty()
    }

    companion object {
        const val EXTRA_REPLICA_STATE = "replica_state"
    }
}
