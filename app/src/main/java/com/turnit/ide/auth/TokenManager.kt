package com.turnit.ide.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey = buildMasterKey(appContext)

    private val encryptedPreferences = EncryptedSharedPreferences.create(
        appContext,
        PREF_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun saveAccessToken(accessToken: String) {
        encryptedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .apply()
    }

    fun saveRefreshToken(refreshToken: String) {
        encryptedPreferences.edit()
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = encryptedPreferences.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = encryptedPreferences.getString(KEY_REFRESH_TOKEN, null)

    fun clearTokens() {
        encryptedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    fun clearAccessToken() {
        encryptedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .apply()
    }

    fun clearRefreshToken() {
        encryptedPreferences.edit()
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    private fun buildMasterKey(context: Context): MasterKey {
        return try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
        } catch (_: Exception) {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
    }

    companion object {
        private const val PREF_FILE_NAME = "secure_tokens"
        private const val KEY_ACCESS_TOKEN = "ACCESS_TOKEN"
        private const val KEY_REFRESH_TOKEN = "REFRESH_TOKEN"
    }
}
