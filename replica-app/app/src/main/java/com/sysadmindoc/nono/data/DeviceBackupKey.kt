package com.sysadmindoc.nono.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The AES key a scheduled backup encrypts with.
 *
 * It lives in the Android Keystore, so the bytes never reach this process and cannot be copied out
 * of it. That is the whole point: a job running with nobody present cannot ask for a passphrase,
 * and a file it wrote with a key that never leaves the device can only be restored on that device.
 * The passphrase export remains the way to move rules between phones.
 *
 * The key is created on first use. A factory reset, an uninstall, or a user clearing app data all
 * take it with them, and the backups written under it become unreadable. The UI says so.
 */
object DeviceBackupKey {

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "nono-scheduled-backup"

    /**
     * @return the existing key, a newly generated one, or null when the keystore refused. A device
     * whose keystore is unavailable gets a reported failure rather than a crashed job.
     */
    fun get(): SecretKey? = runCatching {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: generate()
    }.getOrNull()

    /** Whether a key already exists. Used to say why an old backup can no longer be read. */
    fun exists(): Boolean = runCatching {
        KeyStore.getInstance(PROVIDER).apply { load(null) }.containsAlias(ALIAS)
    }.getOrDefault(false)

    private fun generate(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // The job runs while the screen is off, so it cannot require the user to be
                    // present. Requiring authentication here would mean no backup ever ran.
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }
        .generateKey()
}
