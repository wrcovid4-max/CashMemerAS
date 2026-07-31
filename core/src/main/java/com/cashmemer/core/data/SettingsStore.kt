package com.cashmemer.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cashmemer_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class MassPrintOption { PAGE_1, PAGE_2, BOTH }

/** Mirrors the Settings screen: appearance, general, print, and lock. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "en",
    val autoSend: Boolean = false,
    val saveSignature: Boolean = true,
    val defaultSignatureBase64: String? = null,
    val autoPrint: Boolean = false,
    val showPage1: Boolean = true,
    val showPage2: Boolean = true,
    val massPrint: MassPrintOption = MassPrintOption.BOTH,
    val appLock: Boolean = false,
    val passcode: String? = null,
    val defaultCurrency: String = "PKR",
    /** SAF tree uri of the folder auto-backups are written into. */
    val backupFolderUri: String? = null,
    val autoBackup: Boolean = false,
    val lastBackupAt: Long = 0L,
    val lastBackupError: String? = null,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val accountPhotoUrl: String? = null,
) {
    val signedIn: Boolean get() = accountEmail != null
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_SEND = booleanPreferencesKey("auto_send")
        val SAVE_SIGNATURE = booleanPreferencesKey("save_signature")
        val DEFAULT_SIGNATURE = stringPreferencesKey("default_signature")
        val AUTO_PRINT = booleanPreferencesKey("auto_print")
        val SHOW_PAGE_1 = booleanPreferencesKey("show_page_1")
        val SHOW_PAGE_2 = booleanPreferencesKey("show_page_2")
        val MASS_PRINT = stringPreferencesKey("mass_print")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val PASSCODE = stringPreferencesKey("passcode")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val BACKUP_FOLDER = stringPreferencesKey("backup_folder_uri")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_ERROR = stringPreferencesKey("last_backup_error")
        val ACCOUNT_NAME = stringPreferencesKey("account_name")
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val ACCOUNT_PHOTO = stringPreferencesKey("account_photo")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        themeMode = enumValueOrDefault(this[Keys.THEME], ThemeMode.SYSTEM),
        language = this[Keys.LANGUAGE] ?: "en",
        autoSend = this[Keys.AUTO_SEND] ?: false,
        saveSignature = this[Keys.SAVE_SIGNATURE] ?: true,
        defaultSignatureBase64 = this[Keys.DEFAULT_SIGNATURE],
        autoPrint = this[Keys.AUTO_PRINT] ?: false,
        showPage1 = this[Keys.SHOW_PAGE_1] ?: true,
        showPage2 = this[Keys.SHOW_PAGE_2] ?: true,
        massPrint = enumValueOrDefault(this[Keys.MASS_PRINT], MassPrintOption.BOTH),
        appLock = this[Keys.APP_LOCK] ?: false,
        passcode = this[Keys.PASSCODE],
        defaultCurrency = this[Keys.DEFAULT_CURRENCY] ?: "PKR",
        backupFolderUri = this[Keys.BACKUP_FOLDER],
        autoBackup = this[Keys.AUTO_BACKUP] ?: false,
        lastBackupAt = this[Keys.LAST_BACKUP_AT] ?: 0L,
        lastBackupError = this[Keys.LAST_BACKUP_ERROR],
        accountName = this[Keys.ACCOUNT_NAME],
        accountEmail = this[Keys.ACCOUNT_EMAIL],
        accountPhotoUrl = this[Keys.ACCOUNT_PHOTO],
    )

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.THEME, mode.name)
    suspend fun setLanguage(tag: String) = put(Keys.LANGUAGE, tag)
    suspend fun setAutoSend(value: Boolean) = put(Keys.AUTO_SEND, value)
    suspend fun setSaveSignature(value: Boolean) = put(Keys.SAVE_SIGNATURE, value)
    suspend fun setAutoPrint(value: Boolean) = put(Keys.AUTO_PRINT, value)
    suspend fun setShowPage1(value: Boolean) = put(Keys.SHOW_PAGE_1, value)
    suspend fun setShowPage2(value: Boolean) = put(Keys.SHOW_PAGE_2, value)
    suspend fun setMassPrint(option: MassPrintOption) = put(Keys.MASS_PRINT, option.name)
    suspend fun setAppLock(value: Boolean) = put(Keys.APP_LOCK, value)
    suspend fun setDefaultCurrency(code: String) = put(Keys.DEFAULT_CURRENCY, code)
    suspend fun setAutoBackup(value: Boolean) = put(Keys.AUTO_BACKUP, value)

    suspend fun setBackupFolder(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.BACKUP_FOLDER)
            else prefs[Keys.BACKUP_FOLDER] = uri
        }
    }

    /** Stores the signed-in Google identity, or clears it on sign-out. */
    suspend fun setAccount(name: String?, email: String?, photoUrl: String?) {
        context.dataStore.edit { prefs ->
            if (email == null) {
                prefs.remove(Keys.ACCOUNT_NAME)
                prefs.remove(Keys.ACCOUNT_EMAIL)
                prefs.remove(Keys.ACCOUNT_PHOTO)
            } else {
                prefs[Keys.ACCOUNT_EMAIL] = email
                name?.let { prefs[Keys.ACCOUNT_NAME] = it }
                photoUrl?.let { prefs[Keys.ACCOUNT_PHOTO] = it }
            }
        }
    }

    /** Records the outcome of a backup run so Settings can show it. */
    suspend fun recordBackupResult(succeededAt: Long?, error: String?) {
        context.dataStore.edit { prefs ->
            succeededAt?.let { prefs[Keys.LAST_BACKUP_AT] = it }
            if (error == null) prefs.remove(Keys.LAST_BACKUP_ERROR)
            else prefs[Keys.LAST_BACKUP_ERROR] = error
        }
    }

    suspend fun setDefaultSignature(base64: String?) {
        context.dataStore.edit { prefs ->
            if (base64 == null) prefs.remove(Keys.DEFAULT_SIGNATURE)
            else prefs[Keys.DEFAULT_SIGNATURE] = base64
        }
    }

    suspend fun setPasscode(passcode: String?) {
        context.dataStore.edit { prefs ->
            if (passcode == null) prefs.remove(Keys.PASSCODE)
            else prefs[Keys.PASSCODE] = passcode
        }
    }

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: default
}
