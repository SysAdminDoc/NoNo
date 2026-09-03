package com.sysadmindoc.nono.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester

object SignalMetrics {
    val pageHorizontal = 16.dp
    val sectionGap = 24.dp
    val rowGap = 8.dp
    val rowMinHeight = 56.dp
    val controlRadius = 10.dp
    val cardRadius = 12.dp
}

@Composable
fun SignalTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionDescription: String = "Action",
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 4.dp))
        if (actionIcon != null && onAction != null) {
            IconButton(onClick = onAction, modifier = Modifier.size(48.dp)) {
                Icon(actionIcon, contentDescription = actionDescription)
            }
        }
    }
}

@Composable
fun SignalPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(SignalMetrics.controlRadius),
        colors = ButtonDefaults.buttonColors(containerColor = SignalColors.Yellow, contentColor = SignalColors.Background),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
        }
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SignalIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = SignalColors.White) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(18.dp), content: @Composable () -> Unit) {
    Box(
        modifier
            .background(SignalColors.Surface, RoundedCornerShape(SignalMetrics.cardRadius))
            .border(1.dp, SignalColors.Border, RoundedCornerShape(SignalMetrics.cardRadius))
            .padding(contentPadding),
    ) { content() }
}

@Composable
fun SignalPageHeader(
    title: String,
    subtitle: String? = null,
    actionIcon: ImageVector? = null,
    actionDescription: String = "Action",
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = SignalMetrics.pageHorizontal, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            if (actionIcon != null && onAction != null) {
                SignalIconButton(actionIcon, actionDescription, onAction)
            }
        }
        if (subtitle != null) {
            Text(subtitle, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun SignalStatusPanel(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Shield,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(SignalColors.Surface, RoundedCornerShape(SignalMetrics.cardRadius))
            .border(1.dp, SignalColors.Border, RoundedCornerShape(SignalMetrics.cardRadius))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun SignalSectionHeading(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(subtitle, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SignalGroupedSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .background(SignalColors.Surface, RoundedCornerShape(SignalMetrics.cardRadius))
            .border(1.dp, SignalColors.Border, RoundedCornerShape(SignalMetrics.cardRadius)),
    ) { content() }
}

@Composable
fun SignalDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(start = 64.dp), color = SignalColors.Border, thickness = 1.dp)
}

@Composable
fun SignalListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().then(clickModifier).heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(SignalColors.Background, RoundedCornerShape(10.dp)).border(1.dp, SignalColors.Border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) SignalColors.Yellow else if (enabled) SignalColors.White else SignalColors.Muted, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = if (enabled) SignalColors.White else SignalColors.Muted, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) Text(value, color = if (selected) SignalColors.Yellow else SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium)
        when {
            selected -> Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = SignalColors.Yellow, modifier = Modifier.size(24.dp))
            onClick != null -> Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SignalColors.Secondary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun SignalOutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .border(1.dp, SignalColors.Yellow, RoundedCornerShape(SignalMetrics.controlRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(20.dp))
        Text(label, color = SignalColors.Yellow, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
    }
}

@Composable
fun SignalStepNumber(step: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).border(1.dp, SignalColors.Border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(step.toString(), color = SignalColors.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, destructive: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp),
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
