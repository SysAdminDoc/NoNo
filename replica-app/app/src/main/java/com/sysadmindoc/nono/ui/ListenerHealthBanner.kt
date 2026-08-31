package com.sysadmindoc.nono.ui

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.runtime.ListenerActivity
import com.sysadmindoc.nono.runtime.ListenerActivityLog
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.runtime.ListenerHealth
import com.sysadmindoc.nono.runtime.outstandingIngestionProblems
import com.sysadmindoc.nono.runtime.listenerActivity
import com.sysadmindoc.nono.runtime.SignalNotificationListener

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
fun ListenerHealthBanner(state: UiState, model: MainViewModel? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val connection by ListenerHealth.connection.collectAsState()
    val lastEventAt by ListenerHealth.lastEventAt.collectAsState()
    val ingestionMetrics by ListenerHealth.ingestionMetrics.collectAsState()
    val durableMetrics by ListenerHealth.durableIngestionMetrics.collectAsState()
    // Counts the user already dismissed are history, not a current problem.
    val problems = outstandingIngestionProblems(ingestionMetrics, durableMetrics)
    val dropped = problems.dropped
    val failed = problems.failed

    // Keyed on the live event count so a capture arriving while this screen is open clears the
    // warning. Reading once would leave the banner insisting the listener is dead while it works.
    val eventCount by ListenerHealth.eventCount.collectAsState()
    val lastCapture = remember(eventCount) { ListenerActivityLog.lastEventAt(context) }
    val activity = listenerActivity(
        accessGranted = state.listenerAccessGranted,
        connection = connection,
        capturePaused = state.capturePaused,
        lastEventAtEpochMillis = lastCapture,
        nowEpochMillis = System.currentTimeMillis(),
    )
    val problem = !state.listenerAccessGranted ||
        connection == ListenerHealth.Connection.DISCONNECTED ||
        dropped > 0L ||
        failed > 0L ||
        activity == ListenerActivity.STALE
    if (!problem) return

    val detail = if (!state.listenerAccessGranted) {
        "Notification access is off, so NoNo cannot capture metadata or preview matches. Tap to review access."
    } else if (dropped > 0L || failed > 0L) {
        val failure = problems.lastFailureAtEpochMillis?.let { " Last failure: ${describeWallClock(it)}." }.orEmpty()
        val seenBefore = if (problems.hasAcknowledgedHistory) {
            " Not counting ${problems.acknowledgedDropped} dropped and ${problems.acknowledgedFailed} failed you have already seen."
        } else {
            ""
        }
        "Since you last dismissed this: $dropped dropped, $failed failed.$failure$seenBefore"
    } else if (connection == ListenerHealth.Connection.DISCONNECTED) {
        val age = lastEventAt?.let { describeAge(SystemClock.elapsedRealtime() - it) }
        val seen = if (age == null) "No notifications seen yet." else "Last notification seen $age."
        "The listener is disconnected. $seen ${vendorGuidance()} Tap to review notification access."
    } else {
        val age = lastCapture?.let { describeAge(System.currentTimeMillis() - it) }
        "Notification access is on and the listener says it is connected, but nothing has arrived " +
            "since ${age ?: "a long time ago"}. ${vendorGuidance()} Tap to review notification access."
    }

    // The dismiss control is a sibling of the tappable banner, not a child of it. Modifier
    // .clickable merges its descendants into one accessibility node, so a button nested inside
    // the banner would be announced as part of it and would run the banner's action instead of
    // its own: unreachable by TalkBack and by Switch Access.
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(SignalColors.Surface, RoundedCornerShape(SignalMetrics.cardRadius))
            .border(1.dp, SignalColors.Error, RoundedCornerShape(SignalMetrics.cardRadius))
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Review notification access") {
                    SignalNotificationListener.requestRebindIfPossible(context)
                    openListenerSettings(context)
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = SignalColors.Error,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Metadata capture needs attention", color = SignalColors.Error, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(detail, color = SignalColors.Secondary, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
        // Only the queue counters can be dismissed. Revoked access and a dead listener are
        // conditions, not counts, and go away by being fixed.
        if (model != null && problems.hasCurrentProblem) {
            Text(
                "Dismiss these counts",
                color = SignalColors.Yellow,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(start = 36.dp, end = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { model.acknowledgeIngestionProblems() }
                    .heightIn(min = 48.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 12.dp),
            )
        }
    }
}

private fun vendorGuidance(): String = when (Build.MANUFACTURER.lowercase()) {
    "samsung" -> "On Samsung, allow unrestricted battery use for NoNo."
    "xiaomi", "redmi", "poco" -> "On Xiaomi, enable Autostart and remove battery restrictions."
    "oneplus" -> "On OnePlus, allow background activity and disable battery optimization."
    "oppo" -> "On Oppo, allow auto-launch and background activity."
    "vivo" -> "On Vivo, allow auto-start and high background power use."
    "huawei" -> "On Huawei, set NoNo to Protected in battery settings."
    else -> "Review the manufacturer's battery settings if this repeats."
}

private fun describeAge(millis: Long): String = when {
    millis < 60_000L -> "less than a minute ago"
    millis < 3_600_000L -> "${millis / 60_000L} min ago"
    millis < 86_400_000L -> "${millis / 3_600_000L} h ago"
    else -> "${millis / 86_400_000L} days ago"
}

private fun describeWallClock(epochMillis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        .format(java.util.Date(epochMillis))

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
