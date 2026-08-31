package com.sysadmindoc.nono.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * What stands in front of every rule and every record while the lock is on.
 *
 * It shows nothing about what is behind it. Not a count, not an app name, not a timestamp: the
 * point of the lock is that someone holding the unlocked phone learns nothing from this screen.
 */
@Composable
fun AppLockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SignalColors.Background)
            .padding(horizontal = SignalMetrics.pageHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = SignalColors.Yellow,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            "NoNo is locked",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            "Unlock with your screen lock to see your rules and history.",
            color = SignalColors.Secondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        SignalPrimaryButton(
            "Unlock",
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
    }
}
