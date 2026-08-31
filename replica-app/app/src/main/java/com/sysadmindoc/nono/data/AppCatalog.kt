package com.sysadmindoc.nono.data

import android.content.Intent
import android.content.pm.PackageManager

/**
 * An app a rule can be pointed at.
 *
 * @property label what the user sees. For a package that is no longer installed there is no
 * label to read, so the package name stands in.
 * @property installed false for a package that only exists in history now. Those are kept: a rule
 * written against an app the user has since removed should not silently stop being editable.
 * @property duplicateLabel true when another entry shows the same label, which is how two apps
 * calling themselves "Messages" are told apart.
 */
data class CatalogedApp(
    val label: String,
    val packageName: String,
    val installed: Boolean = true,
    val duplicateLabel: Boolean = false,
) {
    /** What the row shows underneath the label. */
    val detail: String
        get() = if (installed) packageName else "$packageName · not installed"
}

/**
 * Merges what the user can launch with what has actually posted a notification.
 *
 * Neither source is enough alone. The launcher query misses apps with no launcher activity, which
 * post notifications all the same, and history misses everything the user has not heard from yet.
 *
 * @param selfPackage this app, which is excluded: the listener ignores its own notifications, so
 * a rule naming it could never match.
 */
fun mergeAppCatalog(
    launchable: List<CatalogedApp>,
    observedPackages: List<String>,
    selfPackage: String,
    describeObserved: (String) -> CatalogedApp = ::uninstalledApp,
): List<CatalogedApp> {
    val byPackage = LinkedHashMap<String, CatalogedApp>()
    launchable.forEach { app ->
        if (app.packageName == selfPackage) return@forEach
        byPackage[app.packageName] = app
    }
    observedPackages.forEach { packageName ->
        if (packageName.isBlank() || packageName == selfPackage) return@forEach
        // An observed package the launcher query already covered keeps its label. One it missed
        // is looked up: an app with no launcher activity is installed, and posts notifications,
        // which is the whole reason history is merged in here. Only a package nothing can
        // resolve is reported as gone.
        byPackage.getOrPut(packageName) { describeObserved(packageName) }
    }

    val labelCounts = byPackage.values.groupingBy { it.label.lowercase() }.eachCount()
    return byPackage.values
        .map { it.copy(duplicateLabel = (labelCounts[it.label.lowercase()] ?: 0) > 1) }
        .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
}

/** A package nothing on the device can resolve: it has been removed since it posted. */
fun uninstalledApp(packageName: String): CatalogedApp =
    CatalogedApp(label = packageName, packageName = packageName, installed = false)

/**
 * Describes a package history has seen but the launcher query missed.
 *
 * Two different things land here. An app with no launcher activity is installed and posts
 * notifications, and its real label should be shown. An app the user has removed cannot be
 * resolved at all, and is reported as gone so a rule written against it still makes sense.
 */
fun describeObservedApp(packageManager: PackageManager, packageName: String): CatalogedApp =
    runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        CatalogedApp(
            label = packageManager.getApplicationLabel(info).toString().trim().ifBlank { packageName },
            packageName = packageName,
            installed = true,
        )
    }.getOrElse { uninstalledApp(packageName) }

/** Matches a catalog entry against the picker's search box. */
fun CatalogedApp.matches(query: String): Boolean =
    query.isBlank() ||
        label.contains(query, ignoreCase = true) ||
        packageName.contains(query, ignoreCase = true)

/**
 * Reads the apps with a launcher activity.
 *
 * The manifest declares a `<queries>` element for exactly this shape, so no package-visibility
 * permission is involved: `QUERY_ALL_PACKAGES` is a policy-restricted permission and this app has
 * no permissions at all.
 */
fun loadLaunchableApps(packageManager: PackageManager): List<CatalogedApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        packageManager.queryIntentActivities(intent, 0).mapNotNull { resolved ->
            val activity = resolved.activityInfo ?: return@mapNotNull null
            CatalogedApp(
                label = resolved.loadLabel(packageManager).toString().trim()
                    .ifBlank { activity.packageName },
                packageName = activity.packageName,
                installed = true,
            )
        }.distinctBy { it.packageName }
    }.getOrDefault(emptyList())
}
