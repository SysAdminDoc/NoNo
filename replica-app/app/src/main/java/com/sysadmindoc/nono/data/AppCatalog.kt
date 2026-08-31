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
        get() = when {
            !installed -> "$packageName · not installed"
            duplicateLabel -> packageName
            else -> packageName
        }
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
): List<CatalogedApp> {
    val byPackage = LinkedHashMap<String, CatalogedApp>()
    launchable.forEach { app ->
        if (app.packageName == selfPackage) return@forEach
        byPackage[app.packageName] = app
    }
    observedPackages.forEach { packageName ->
        if (packageName.isBlank() || packageName == selfPackage) return@forEach
        // An observed package the launcher query already covered keeps its label.
        byPackage.getOrPut(packageName) { CatalogedApp(label = packageName, packageName = packageName, installed = false) }
    }

    val labelCounts = byPackage.values.groupingBy { it.label.lowercase() }.eachCount()
    return byPackage.values
        .map { it.copy(duplicateLabel = (labelCounts[it.label.lowercase()] ?: 0) > 1) }
        .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
}

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
