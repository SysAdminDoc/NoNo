package com.anm.signalrules.reconstruction.ui

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anm.signalrules.reconstruction.model.UiState
import com.anm.signalrules.reconstruction.runtime.ListenerHealth
import com.anm.signalrules.reconstruction.runtime.SignalNotificationListener

/**
 * Surfaces notification-listener health.
 *
 * A listener that has been unbound looks exactly like one that has simply seen nothing yet,
 * which is why "it silently stopped working" is the most common complaint against apps in this
 * category. Rather than leave the user to guess, the disconnected case is stated and offers
 * the fastest route back - the per-app notification-access screen on API 30+, the global list
 * below that.
 */
@Composable
fun ListenerHealthBanner(state: UiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val connection by ListenerHealth.connection.collectAsState()
    val lastEventAt by ListenerHealth.lastEventAt.collectAsState()
    val ingestionMetrics by ListenerHealth.ingestionMetrics.collectAsState()

    val problem = !state.listenerAccessGranted ||
        connection == ListenerHealth.Connection.DISCONNECTED ||
        ingestionMetrics.dropped > 0L ||
        ingestionMetrics.failed > 0L
    if (!problem) return

    val detail = if (!state.listenerAccessGranted) {
        "Notification access is off, so no rule can run. Tap to turn it back on."
    } else if (ingestionMetrics.dropped > 0L || ingestionMetrics.failed > 0L) {
        "Listener queue diagnostics: ${ingestionMetrics.dropped} dropped, ${ingestionMetrics.failed} failed. " +
            "Recent metadata may be incomplete; tap to review notification access."
    } else {
        val age = lastEventAt?.let { describeAge(SystemClock.elapsedRealtime() - it) }
        val seen = if (age == null) "No notifications seen yet." else "Last notification seen $age."
        "The listener is disconnected. $seen ${vendorGuidance()} Tap to review notification access."
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(SignalColors.Surface, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { openListenerSettings(context) }
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = SignalColors.Error,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Rules are not running", color = SignalColors.Error, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(detail, color = SignalColors.Secondary, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

private fun vendorGuidance(): String = when (Build.MANUFACTURER.lowercase()) {
    "samsung" -> "On Samsung, allow unrestricted battery use for Signal Rules."
    "xiaomi", "redmi", "poco" -> "On Xiaomi, enable Autostart and remove battery restrictions."
    "oneplus" -> "On OnePlus, allow background activity and disable battery optimization."
    "oppo" -> "On Oppo, allow auto-launch and background activity."
    "vivo" -> "On Vivo, allow auto-start and high background power use."
    "huawei" -> "On Huawei, set Signal Rules to Protected in battery settings."
    else -> "Review the manufacturer's battery settings if this repeats."
}

private fun describeAge(millis: Long): String = when {
    millis < 60_000L -> "less than a minute ago"
    millis < 3_600_000L -> "${millis / 60_000L} min ago"
    millis < 86_400_000L -> "${millis / 3_600_000L} h ago"
    else -> "${millis / 86_400_000L} days ago"
}

/**
 * Opens notification-access settings, preferring the per-app screen added in API 30.
 *
 * The detail screen silently does nothing on some OEM builds, so callers must re-check the
 * granted state on resume rather than trusting that this succeeded.
 */
fun openListenerSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= 30) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                SignalNotificationListener.componentName(context).flattenToString(),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(detail) }.isSuccess) return
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
