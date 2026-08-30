package com.sysadmindoc.nono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class LauncherIconContractTest {
    private val densities = linkedMapOf(
        "mdpi" to 108,
        "hdpi" to 162,
        "xhdpi" to 216,
        "xxhdpi" to 324,
        "xxxhdpi" to 432,
    )

    @Test
    fun adaptiveLayersUseTheSuppliedRasterContract() {
        val res = findResRoot()
        val launcher = File(res, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        val round = File(res, "mipmap-anydpi-v26/ic_launcher_round.xml").readText()

        for (xml in listOf(launcher, round)) {
            assertTrue(xml.contains("@color/ic_launcher_background"))
            assertTrue(xml.contains("@mipmap/ic_launcher_foreground"))
            assertTrue(xml.contains("@mipmap/ic_launcher_monochrome"))
        }
        assertTrue(File(res, "values/ic_launcher_background.xml").readText().contains("#0F0F1F"))

        for ((density, size) in densities) {
            assertLayer(File(res, "mipmap-$density/ic_launcher_foreground.png"), size)
            assertLayer(File(res, "mipmap-$density/ic_launcher_monochrome.png"), size)
        }
    }

    @Test
    fun legacyIconsCoverApi24AndDoNotKeepPrototypeVectors() {
        val res = findResRoot()
        val legacySizes = linkedMapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192,
        )

        for ((density, size) in legacySizes) {
            assertLayer(File(res, "mipmap-$density/ic_launcher.png"), size, requireTransparency = false)
            assertLayer(File(res, "mipmap-$density/ic_launcher_round.png"), size)
        }

        assertFalse(File(res, "drawable/ic_launcher_legacy.xml").exists())
        assertFalse(File(res, "drawable/ic_signal_foreground.xml").exists())
        assertFalse(File(res, "mipmap/ic_launcher.xml").exists())
        assertFalse(File(res, "mipmap/ic_launcher_round.xml").exists())
    }

    private fun assertLayer(file: File, size: Int, requireTransparency: Boolean = true) {
        assertTrue("Missing ${file.path}", file.isFile)
        val image = ImageIO.read(file)
        assertEquals(size, image.width)
        assertEquals(size, image.height)

        var transparent = 0
        var opaque = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                when ((image.getRGB(x, y) ushr 24) and 0xff) {
                    0 -> transparent++
                    255 -> opaque++
                }
            }
        }
        if (requireTransparency) {
            assertTrue("Expected transparent pixels in ${file.path}", transparent > 100)
        }
        assertTrue("Expected opaque pixels in ${file.path}", opaque > 100)
    }

    private fun findResRoot(): File {
        val start = File(System.getProperty("user.dir")).canonicalFile
        for (directory in generateSequence(start) { it.parentFile }.take(8)) {
            for (candidate in listOf(
                File(directory, "src/main/res"),
                File(directory, "app/src/main/res"),
                File(directory, "replica-app/app/src/main/res"),
            )) {
                if (candidate.isDirectory) return candidate
            }
        }
        error("Could not locate NoNo resources from ${System.getProperty("user.dir")}")
    }
}
