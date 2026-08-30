package com.sysadmindoc.nono.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.WorkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UiState

private data class ArticleItem(val title: String, val description: String, val icon: ImageVector)
private val articles = listOf(
    ArticleItem("How I stay focused and in the moment", "A practical guide to reducing distracting notifications and being present.", Icons.AutoMirrored.Rounded.Article),
    ArticleItem("Quiet out-of-hours work notifications", "Keep work alerts available without letting them interrupt personal time.", Icons.Rounded.WorkOff),
    ArticleItem("Avoid missing package deliveries", "Surface delivery updates while cooling down repetitive status alerts.", Icons.Rounded.Inventory2),
    ArticleItem("Build a healthier bedtime routine", "Reduce late-night checking while allowing genuinely important alerts.", Icons.Rounded.Bedtime),
)

@Composable
fun ExploreScreen(state: UiState, model: MainViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val target = when (state.auditState.substringBefore('_').toIntOrNull()) {
        77 -> 1; 78 -> 2; 79 -> 3; 80, 81 -> 4; else -> 0
    }
    LaunchedEffect(state.auditState) { listState.scrollToItem(target) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Articles", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) }
        articles.forEachIndexed { index, article ->
            item {
                ArticleCard(article, index) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.android.com/develop/ui/views/notifications")))
                    }
                }
            }
        }
        item { Text("Suggestions", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) }
        item {
            SuggestionCard(
                title = "Flash for urgent messages",
                description = "Turn on the flashlight when a message contains an urgent phrase.",
                color = SignalColors.SuggestionPurple,
                icon = Icons.Rounded.FlashlightOn,
            ) {
                model.updateDraft { SignalRule(name = "Flashlight suggestion", app = "Messages", phrase = "urgent", action = "Flashlight") }
                model.navigate(Route.RULE_BUILDER)
            }
        }
        item {
            SuggestionCard(
                title = "Batch repetitive updates",
                description = "Collect frequent updates and show them together at a calmer time.",
                color = SignalColors.SuggestionGreen,
                icon = Icons.AutoMirrored.Rounded.Article,
            ) {
                model.updateDraft { SignalRule(name = "Batch updates", app = "any app", phrase = "update", action = "Batch") }
                model.navigate(Route.RULE_BUILDER)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ArticleCard(article: ArticleItem, index: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().border(5.dp, SignalColors.Border, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp).background(articleAccent(index), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(article.icon, contentDescription = null, tint = SignalColors.Background)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(article.title, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
            Text(article.description, color = SignalColors.Secondary, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun SuggestionCard(title: String, description: String, color: Color, icon: ImageVector, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(color, RoundedCornerShape(22.dp)).padding(5.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SignalColors.Background, modifier = Modifier.size(34.dp))
            Text(title, color = SignalColors.Background, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.fillMaxWidth().background(SignalColors.Background, RoundedCornerShape(18.dp)).padding(18.dp)) {
            Text(description, color = SignalColors.White, lineHeight = 22.sp)
            Row(
                Modifier.align(Alignment.End).padding(top = 18.dp).background(color, RoundedCornerShape(30.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = SignalColors.Background)
                Text("Add to my rules", color = SignalColors.Background, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * Accent for an article card. Wraps rather than indexes, so adding an article cannot throw
 * IndexOutOfBoundsException the way a fixed four-element lookup did.
 */
private fun articleAccent(index: Int) = ARTICLE_ACCENTS[index.mod(ARTICLE_ACCENTS.size)]

private val ARTICLE_ACCENTS = listOf(
    SignalColors.RuleBlue,
    SignalColors.SuggestionGreen,
    SignalColors.Yellow,
    SignalColors.SuggestionPurple,
)
