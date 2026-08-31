package com.sysadmindoc.nono.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The picker has to see other apps without asking for the permission that would let it see
 * everything. This checks both halves on a real device: the only platform permission supports
 * the user-triggered capture self-test, and the `<queries>` element still returns launchable apps.
 */
@RunWith(AndroidJUnit4::class)
class AppCatalogVisibilityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theAppDeclaresOnlyTheSelfTestPostingPermission() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val declared = info.requestedPermissions?.toList().orEmpty()

        // The merged manifest is not empty: AGP adds
        // <applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION, a signature permission the app
        // defines for itself so androidx can register a non-exported receiver on API 33+. It
        // grants nothing outside this app. POST_NOTIFICATIONS is declared for the explicit capture
        // self-test and requested only when the user runs it. No other platform permission belongs
        // in the merged manifest.
        val fromElsewhere = declared.filterNot { it.startsWith(context.packageName) }

        assertEquals(
            "unexpected platform permissions: $fromElsewhere",
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            fromElsewhere,
        )
        assertTrue(
            "package visibility must come from <queries>, never a permission",
            declared.none { it.contains("QUERY_ALL_PACKAGES") },
        )
    }

    @Test
    fun launchableAppsAreVisibleThroughTheQueriesElement() {
        // Without <queries>, Android 11 and newer return nothing here. Any device running this
        // test has a launcher and a settings app, so an empty result means visibility is broken.
        val launchable = loadLaunchableApps(context.packageManager)

        assertTrue("no launchable apps were visible", launchable.isNotEmpty())
        assertTrue(launchable.all { it.installed })
        assertTrue(launchable.all { it.packageName.isNotBlank() })
        assertEquals(launchable.size, launchable.map { it.packageName }.distinct().size)
    }

    @Test
    fun theCatalogNeverOffersThisApp() {
        val catalog = mergeAppCatalog(
            loadLaunchableApps(context.packageManager),
            listOf(context.packageName, "com.example.observed"),
            context.packageName,
        )

        assertTrue(catalog.none { it.packageName == context.packageName })
        assertTrue(catalog.any { it.packageName == "com.example.observed" })
    }
}
