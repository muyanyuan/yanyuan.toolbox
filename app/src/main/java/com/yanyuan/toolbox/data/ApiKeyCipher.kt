package com.yanyuan.toolbox.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object ApiKeyCipher {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "yanyuan_api_key_aes_gcm_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "keystore-aes-gcm:v1:"
    private val aad = "com.yanyuan.toolbox:api-key:v1".toByteArray(StandardCharsets.UTF_8)

    @Synchronized
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$PREFIX$iv:$encrypted"
    }

    @Synchronized
    fun decrypt(envelope: String): String {
        require(envelope.startsWith(PREFIX)) { "Unsupported API key envelope" }
        val parts = envelope.removePrefix(PREFIX).split(':', limit = 2)
        require(parts.size == 2) { "Invalid API key envelope" }
        val key = loadKey() ?: throw IllegalStateException("API key encryption key is unavailable")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        cipher.updateAAD(aad)
        val plaintext = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
        return String(plaintext, StandardCharsets.UTF_8)
    }

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    private fun getOrCreateKey(): SecretKey {
        loadKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun loadKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }
}
