package com.sysadmindoc.nono.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {

    private val self = "com.sysadmindoc.nono"

    private val launchable = listOf(
        CatalogedApp("Phone", "com.google.android.dialer"),
        CatalogedApp("Messages", "com.google.android.apps.messaging"),
        CatalogedApp("NoNo", self),
    )

    @Test
    fun `an app with no launcher activity is still offered once it has posted something`() {
        // Plenty of apps post notifications and have no launcher icon. A picker built only from
        // the launcher query cannot name them at all.
        val catalog = mergeAppCatalog(launchable, listOf("com.example.headless"), self)

        assertTrue(catalog.any { it.packageName == "com.example.headless" })
    }

    @Test
    fun `a package only history knows about is marked as not installed`() {
        val catalog = mergeAppCatalog(launchable, listOf("com.example.removed"), self)
        val removed = catalog.single { it.packageName == "com.example.removed" }

        assertFalse(removed.installed)
        assertTrue(removed.detail.contains("not installed"))
        // There is no label to read for a package that is gone, so the package stands in.
        assertEquals("com.example.removed", removed.label)
    }

    @Test
    fun `an app that is both launchable and in history appears once, with its label`() {
        val catalog = mergeAppCatalog(launchable, listOf("com.google.android.dialer"), self)
        val phone = catalog.filter { it.packageName == "com.google.android.dialer" }

        assertEquals(1, phone.size)
        assertEquals("Phone", phone.single().label)
        assertTrue(phone.single().installed)
    }

    @Test
    fun `this app is never offered`() {
        // The listener ignores its own notifications, so a rule naming NoNo could never match.
        val catalog = mergeAppCatalog(launchable, listOf(self), self)

        assertTrue(catalog.none { it.packageName == self })
    }

    @Test
    fun `two apps sharing a label are told apart`() {
        val catalog = mergeAppCatalog(
            launchable + CatalogedApp("Messages", "com.example.othermessenger"),
            emptyList(),
            self,
        )
        val messengers = catalog.filter { it.label == "Messages" }

        assertEquals(2, messengers.size)
        assertTrue(messengers.all { it.duplicateLabel })
        assertEquals(2, messengers.map { it.detail }.distinct().size)
        // A label held by only one app is not flagged.
        assertFalse(catalog.single { it.packageName == "com.google.android.dialer" }.duplicateLabel)
    }

    @Test
    fun `duplicate labels are compared without case getting in the way`() {
        val catalog = mergeAppCatalog(
            listOf(
                CatalogedApp("Messages", "com.a"),
                CatalogedApp("messages", "com.b"),
            ),
            emptyList(),
            self,
        )

        assertTrue(catalog.all { it.duplicateLabel })
    }

    @Test
    fun `the order is stable and does not depend on the platform's`() {
        val forwards = mergeAppCatalog(launchable, listOf("com.example.zzz", "com.example.aaa"), self)
        val backwards = mergeAppCatalog(launchable.reversed(), listOf("com.example.aaa", "com.example.zzz"), self)

        assertEquals(forwards.map { it.packageName }, backwards.map { it.packageName })
    }

    @Test
    fun `a blank observed package is ignored rather than shown as an empty row`() {
        val catalog = mergeAppCatalog(launchable, listOf("", "   "), self)

        assertTrue(catalog.none { it.packageName.isBlank() })
    }

    @Test
    fun `search covers labels and package names`() {
        val phone = CatalogedApp("Phone", "com.google.android.dialer")

        assertTrue(phone.matches(""))
        assertTrue(phone.matches("pho"))
        assertTrue(phone.matches("PHO"))
        assertTrue(phone.matches("dialer"))
        assertFalse(phone.matches("calendar"))
    }
}
