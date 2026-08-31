package com.sysadmindoc.nono.ui

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.Overlay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Device checks for the wallpaper-backed theme Android exposes from API 31 onward. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class DynamicThemeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var model: MainViewModel? = null

    @After
    fun restoreDarkTheme() {
        model?.let { active ->
            composeRule.runOnUiThread { active.setSetting("Theme", "Dark") }
            composeRule.waitUntil(5_000L) { active.state.value.settings["Theme"] == "Dark" }
        }
    }

    @Test
    fun theThemeDialogOffersAndSelectsWallpaperColors() {
        val active = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        model = active
        composeRule.setContent {
            val state by active.state.collectAsState()
            SignalTheme(state.settings["Theme"] ?: "Dark") {
                SignalOverlay(state.copy(overlay = Overlay.THEME), active)
            }
        }
        composeRule.waitUntil(5_000L) { active.state.value.rulesLoaded }

        composeRule.onNodeWithText(DYNAMIC_THEME).assertExists().performClick()

        composeRule.waitUntil(5_000L) { active.state.value.settings["Theme"] == DYNAMIC_THEME }
        assertEquals(DYNAMIC_THEME, active.state.value.settings["Theme"])
    }

    @Test
    fun theWallpaperAccentIsRenderedAndKeepsItsContrast() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val base = if (dark) DarkPalette else LightPalette
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val expectedAccent = dynamicAccentOrFallback(
            listOf(dynamic.primary, dynamic.tertiary, dynamic.secondary, dynamic.primaryContainer),
            listOf(base.background, base.surface, base.surfaceSelected),
            base.yellow,
        )
        var renderedAccent = Color.Unspecified
        var renderedOnAccent = Color.Unspecified

        composeRule.setContent {
            SignalTheme(DYNAMIC_THEME) {
                renderedAccent = MaterialTheme.colorScheme.primary
                renderedOnAccent = MaterialTheme.colorScheme.onPrimary
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(renderedAccent)
                        .testTag(DYNAMIC_SWATCH),
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(expectedAccent, renderedAccent)
        assertEquals(expectedAccent, SignalColors.Yellow)
        listOf(base.background, base.surface, base.surfaceSelected).forEach { surface ->
            assertTrue(contrastRatio(renderedAccent, surface) >= TEXT_CONTRAST_MINIMUM)
            assertTrue(contrastRatio(base.controlOutline, surface) >= COMPONENT_CONTRAST_MINIMUM)
        }
        assertTrue(contrastRatio(renderedOnAccent, renderedAccent) >= TEXT_CONTRAST_MINIMUM)

        val screenshot = composeRule.onNodeWithTag(DYNAMIC_SWATCH).captureToImage().toPixelMap()
        val center = screenshot[screenshot.width / 2, screenshot.height / 2]
        assertColorClose(renderedAccent, center)
    }

    private fun assertColorClose(expected: Color, actual: Color) {
        assertTrue("red channel differs", abs(expected.red - actual.red) < CHANNEL_TOLERANCE)
        assertTrue("green channel differs", abs(expected.green - actual.green) < CHANNEL_TOLERANCE)
        assertTrue("blue channel differs", abs(expected.blue - actual.blue) < CHANNEL_TOLERANCE)
    }

    private companion object {
        const val DYNAMIC_SWATCH = "dynamic-theme-swatch"
        const val CHANNEL_TOLERANCE = 0.01f
    }
}
