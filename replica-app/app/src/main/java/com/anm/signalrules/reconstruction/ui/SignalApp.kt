package com.anm.signalrules.reconstruction.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.anm.signalrules.reconstruction.MainViewModel
import com.anm.signalrules.reconstruction.model.Overlay
import com.anm.signalrules.reconstruction.model.RootTab
import com.anm.signalrules.reconstruction.model.Route

@Composable
fun SignalApp(model: MainViewModel) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.transientMessage) {
        state.transientMessage?.let {
            snackbar.showSnackbar(it)
            model.clearTransient()
        }
    }

    BackHandler(enabled = state.overlay != Overlay.NONE) { model.dismissOverlay() }
    BackHandler(enabled = state.overlay == Overlay.NONE && state.route !in listOf(Route.ROOT, Route.ONBOARDING)) {
        when (state.route) {
            Route.APP_SELECTOR, Route.PHRASE_EDITOR, Route.FILTER_GROUP, Route.ACTION_SELECTOR -> model.navigate(Route.RULE_BUILDER)
            Route.HISTORY_ACTIVITY -> model.selectRoot(RootTab.HISTORY)
            Route.SHORTCUT_EDITOR -> model.selectRoot(RootTab.SETTINGS)
            else -> model.selectRoot(RootTab.RULES)
        }
    }

    Box(Modifier.fillMaxSize().background(SignalColors.Background).statusBarsPadding()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = SignalColors.Background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (state.route == Route.ROOT) {
                    SignalBottomNavigation(state.rootTab, model::selectRoot)
                }
            },
        ) { padding ->
            // Only the ROOT route gets a bottom bar, so every other route must apply the
            // navigation-bar inset itself or its bottom-anchored controls render underneath it.
            val bottomInset = if (state.route == Route.ROOT) Modifier else Modifier.navigationBarsPadding()
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).then(bottomInset)) {
                when (state.route) {
                    Route.ONBOARDING -> OnboardingScreen(state, model)
                    Route.ROOT -> when (state.rootTab) {
                        RootTab.RULES -> RulesHomeScreen(state, model)
                        RootTab.HISTORY -> HistoryScreen(state, model)
                        RootTab.EXPLORE -> ExploreScreen(state, model)
                        RootTab.SETTINGS -> SettingsScreen(state, model)
                    }
                    Route.RULE_BUILDER -> RuleBuilderScreen(state, model)
                    Route.APP_SELECTOR -> AppSelectorScreen(state, model)
                    Route.PHRASE_EDITOR -> PhraseEditorScreen(state, model)
                    Route.FILTER_GROUP -> FilterGroupScreen(state, model)
                    Route.ACTION_SELECTOR -> ActionSelectorScreen(state, model)
                    Route.HISTORY_ACTIVITY -> HistoryActivityScreen(state, model)
                    Route.SHORTCUT_EDITOR -> ShortcutEditorScreen(state, model)
                }
                if (state.overlay != Overlay.NONE) SignalOverlay(state, model)
            }
        }
    }
}

private data class NavEntry(val tab: RootTab, val icon: ImageVector)

@Composable
private fun SignalBottomNavigation(selected: RootTab, onSelect: (RootTab) -> Unit) {
    val entries = listOf(
        NavEntry(RootTab.RULES, Icons.Rounded.Tune),
        NavEntry(RootTab.HISTORY, Icons.Rounded.History),
        NavEntry(RootTab.EXPLORE, Icons.Rounded.Explore),
        NavEntry(RootTab.SETTINGS, Icons.Rounded.Settings),
    )
    NavigationBar(
        containerColor = SignalColors.Background,
        contentColor = SignalColors.White,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        entries.forEach { entry ->
            NavigationBarItem(
                selected = selected == entry.tab,
                onClick = { onSelect(entry.tab) },
                icon = { Icon(entry.icon, contentDescription = entry.tab.label) },
                label = { Text(entry.tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SignalColors.Background,
                    selectedTextColor = SignalColors.Yellow,
                    indicatorColor = SignalColors.Yellow,
                    unselectedIconColor = SignalColors.Muted,
                    unselectedTextColor = SignalColors.Secondary,
                    disabledIconColor = SignalColors.Muted,
                    disabledTextColor = SignalColors.Muted,
                ),
            )
        }
    }
}
