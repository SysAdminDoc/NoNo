package com.sysadmindoc.nono.ui

import android.os.Build
import android.os.UserManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.UiState

@Composable
fun OnboardingScreen(state: UiState, model: MainViewModel) {
    val context = LocalContext.current
    val lowRam = (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
    val managedProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.getSystemService(UserManager::class.java)?.isManagedProfile == true
    } else {
        false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).border(1.dp, SignalColors.Yellow, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(24.dp))
                }
                Text("NoNo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
        item {
            Column {
                Text("Take back your attention", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "NoNo previews notification rules on this device. Your content is never stored.",
                    color = SignalColors.Secondary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        item { PrivacySummary() }
        if (managedProfile || (lowRam && Build.VERSION.SDK_INT <= 29)) {
            item {
                SignalStatusPanel(
                    title = "Device limitation",
                    description = if (managedProfile) {
                        "Android work profiles may not deliver notification-listener events."
                    } else {
                        "Android 10 and earlier low-RAM devices may not support notification access."
                    },
                    icon = Icons.Rounded.Notifications,
                )
            }
        }
        item { Text("SETUP", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
        item {
            CapabilityCard(
                title = if (state.onboardingStep >= 1) "Notification access enabled" else "Enable notification access",
                description = "Allow NoNo to receive redacted notification metadata.",
                complete = state.onboardingStep >= 1,
                onClick = { openListenerSettings(context) },
            )
        }
        item {
            SignalPrimaryButton(
                label = if (state.onboardingStep >= 1) "Review notification settings" else "Open notification settings",
                onClick = { openListenerSettings(context) },
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            )
        }
        item {
            Text(
                "Nothing is executed. You can change access at any time.",
                color = SignalColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PrivacySummary() {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        PrivacyRow(Icons.Rounded.Shield, "On-device only")
        HorizontalDivider(color = SignalColors.Border, modifier = Modifier.padding(horizontal = 16.dp))
        PrivacyRow(Icons.Rounded.VisibilityOff, "No ads or tracking")
    }
}

@Composable
private fun PrivacyRow(icon: ImageVector, label: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun CapabilityCard(title: String, description: String, complete: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SignalColors.Surface, RoundedCornerShape(SignalMetrics.cardRadius))
            .border(1.dp, if (complete) SignalColors.Yellow else SignalColors.Border, RoundedCornerShape(SignalMetrics.cardRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).border(1.dp, if (complete) SignalColors.Yellow else SignalColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (complete) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(22.dp))
            } else {
                Text("1", color = SignalColors.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = SignalColors.Secondary)
    }
}
