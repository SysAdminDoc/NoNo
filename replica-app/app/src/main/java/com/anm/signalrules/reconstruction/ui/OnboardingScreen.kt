package com.anm.signalrules.reconstruction.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anm.signalrules.reconstruction.MainViewModel
import com.anm.signalrules.reconstruction.model.UiState

@Composable
fun OnboardingScreen(state: UiState, model: MainViewModel) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.refreshOnboardingCapabilities()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(44.dp))
        Box(Modifier.size(38.dp).background(SignalColors.White, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = SignalColors.Background, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Welcome to Signal Rules", style = MaterialTheme.typography.headlineLarge, fontSize = 27.sp, lineHeight = 34.sp)
        Text(
            "Signal Rules is a collection of tools to help you keep your notifications under control. You create rules that decide which notifications to act on, and what to do with them.",
            color = Color(0xFFD5D5E0), fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "Your data is kept locally. Signal Rules has no ads, no tracking, and no in-app purchases.",
            color = Color(0xFFD5D5E0), fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 18.dp, bottom = 24.dp)
        )

        CapabilityCard(
            step = 1,
            title = "Allow notifications",
            description = "This allows Signal Rules to send or update notifications.",
            complete = state.onboardingStep >= 1,
        ) {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else model.refreshOnboardingCapabilities()
        }
        Spacer(Modifier.height(12.dp))
        CapabilityCard(
            step = 2,
            title = "Allow Signal Rules to run in background",
            description = "This allows automated rules to run when the app isn't open.",
            complete = state.onboardingStep >= 2,
        ) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        CapabilityCard(
            step = 3,
            title = "Enable Signal Rules",
            description = "This allows Signal Rules to react to notifications you receive.",
            complete = state.onboardingStep >= 3,
        ) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        Text(
            "Contact support",
            color = Color(0xFFD5D5E0),
            fontSize = 16.sp,
            modifier = Modifier.clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@example.invalid"))) }
                    .onFailure { model.showMessage("No email app is available") }
            }.padding(horizontal = 8.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun SignalMark() {
    Box(Modifier.size(48.dp).background(SignalColors.Yellow, CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(32.dp)) {
            drawCircle(SignalColors.Background, radius = 4.dp.toPx(), center = center)
            drawCircle(SignalColors.Background, radius = 10.dp.toPx(), center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawCircle(SignalColors.Background, radius = 15.dp.toPx(), center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun CapabilityCard(step: Int, title: String, description: String, complete: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (complete) SignalColors.Surface else SignalColors.Yellow, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).background(Color.Transparent, CircleShape).border(2.dp, if (complete) SignalColors.Yellow else SignalColors.Background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (complete) Icon(Icons.Rounded.Check, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(18.dp))
            else Text(step.toString(), color = SignalColors.Background, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = if (complete) SignalColors.White else SignalColors.Background, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(description, color = if (complete) SignalColors.Secondary else SignalColors.Background, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
