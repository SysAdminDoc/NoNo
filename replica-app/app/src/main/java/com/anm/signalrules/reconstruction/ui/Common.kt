package com.anm.signalrules.reconstruction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester

@Composable
fun SignalTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionDescription: String = "Action",
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = if (onBack == null) 12.dp else 4.dp))
        if (actionIcon != null && onAction != null) {
            IconButton(onClick = onAction, modifier = Modifier.size(48.dp)) {
                Icon(actionIcon, contentDescription = actionDescription)
            }
        }
    }
}

@Composable
fun HeroTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SignalPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SignalColors.Yellow, contentColor = SignalColors.Background),
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun SignalIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = SignalColors.White) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(SignalColors.Surface, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (enabled) SignalColors.White else SignalColors.Muted, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(label, color = if (enabled) SignalColors.White else SignalColors.Muted, fontSize = 17.sp)
            if (value != null) Text(value, color = SignalColors.Secondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun SectionLabel(label: String) {
    Text(label, color = SignalColors.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
fun TokenButton(label: String, error: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (error) SignalColors.Error else SignalColors.Yellow,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .border(1.dp, Color.Transparent, RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp, vertical = 10.dp)
            .semantics { contentDescription = "Edit $label" },
    )
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(18.dp), content: @Composable () -> Unit) {
    Box(modifier.background(SignalColors.Surface, RoundedCornerShape(18.dp)).padding(contentPadding)) { content() }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, destructive: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (destructive) SignalColors.Error else SignalColors.White, fontSize = 17.sp)
        if (selected) Box(Modifier.size(10.dp).background(SignalColors.Yellow, CircleShape))
    }
}

@Composable
fun CloseButton(onClick: () -> Unit) {
    SignalIconButton(Icons.Rounded.Close, "Close", onClick)
}

/**
 * Focuses [focusRequester] and raises the soft keyboard once the composition has settled.
 *
 * `requestFocus()` throws IllegalStateException when the requester is not attached to a node,
 * which the previous three-attempt retry loop invited by firing before the subcomposition of a
 * Dialog existed. One attempt, guarded, after a single frame.
 */
suspend fun requestKeyboardFocus(
    focusRequester: FocusRequester,
    keyboard: SoftwareKeyboardController?,
) {
    withFrameNanos { }
    runCatching { focusRequester.requestFocus() }
    keyboard?.show()
}
