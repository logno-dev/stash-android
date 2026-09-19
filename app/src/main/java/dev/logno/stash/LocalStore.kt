package dev.logno.stash

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private preferences; bearer tokens encrypted with a device-bound Android Keystore key. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("stash", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    var server: String
        get() = prefs.getString("server", "https://stash.bunch.codes")!!
        set(value) { prefs.edit().putString("server", value).apply() }
    var token: String?
        get() = runCatching {
            val stored = prefs.getString("token", null) ?: return null
            val (iv, encrypted) = stored.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
        set(value) {
            if (value == null) { prefs.edit().remove("token").apply(); return }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            prefs.edit().putString("token", "$iv:$encrypted").apply()
        }
    var draft: Draft?
        get() = runCatching { json.decodeFromString<Draft>(prefs.getString("draft", null) ?: return null) }.getOrNull()
        set(value) { prefs.edit().putString("draft", value?.let { json.encodeToString(it) }).apply() }
    var queuedShares: List<Draft>
        get() = runCatching { json.decodeFromString<List<Draft>>(prefs.getString("shares", "[]")!!) }.getOrDefault(emptyList())
        set(value) { prefs.edit().putString("shares", json.encodeToString(value)).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("stash-token", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("stash-token", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
