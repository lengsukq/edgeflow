package com.edgeflow.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.edgeflow.core.ClipboardHistoryPolicy
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Opt-in clipboard history encrypted with an on-device Android Keystore AES key.
 * Never log clipboard content, expose it to system_server, or back it up.
 */
internal class SecureClipboardHistory(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun entries(): List<String> {
        val blob = prefs.getString(DATA, null) ?: return emptyList()
        return runCatching {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            require(raw.size > IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_BYTES))
            val list = JSONArray(String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8))
            (0 until list.length().coerceAtMost(ClipboardHistoryPolicy.MAX_ENTRIES))
                .mapNotNull { list.optString(it).takeIf(String::isNotBlank) }
        }.getOrElse {
            // Never destroy encrypted clipboard data on a transient Keystore
            // failure (device locked / credential state unavailable). The user
            // may clear it explicitly once the app is usable again.
            emptyList()
        }
    }

    @Synchronized
    fun add(text: String) = save(ClipboardHistoryPolicy.append(entries(), text))

    @Synchronized
    fun remove(text: String) = save(ClipboardHistoryPolicy.remove(entries(), text))

    @Synchronized
    fun clear() {
        prefs.edit().remove(DATA).apply()
    }

    private fun save(items: List<String>) {
        val plain = JSONArray(items).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val raw = cipher.iv + cipher.doFinal(plain)
        check(prefs.edit().putString(DATA, Base64.encodeToString(raw, Base64.NO_WRAP)).commit()) {
            "Could not save encrypted clipboard history"
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "edgeflow_clipboard_history_key_v1"
        const val PREFS = "secure_clipboard_history"
        const val DATA = "encrypted_items"
        const val IV_BYTES = 12
    }
}
