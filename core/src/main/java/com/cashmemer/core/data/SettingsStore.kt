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

/** How a single unit is labelled when showing the per-unit price on a memo. */
enum class PriceUnit(val unitLabel: String) {
    PIECE("Qty"),
    KILOGRAM("Kg"),
    GRAM("g"),
    LITRE("L");

    companion object {
        fun from(raw: String?): PriceUnit = entries.firstOrNull { it.name == raw } ?: PIECE
    }
}

/** Mirrors the Settings screen: appearance, general, print, and lock. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: String = "en",
    val autoSend: Boolean = false,
    val saveSignature: Boolean = true,
    /** When on, the signature pad ignores touches so it can't be marked by accident. */
    val signatureLocked: Boolean = false,
    val defaultSignatureBase64: String? = null,
    val autoPrint: Boolean = false,
    val showPage1: Boolean = true,
    val showPage2: Boolean = true,
    val massPrint: MassPrintOption = MassPrintOption.BOTH,
    val priceUnit: PriceUnit = PriceUnit.PIECE,
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
    // Connected Devices & Integrations
    val paymentTerminalEnabled: Boolean = true,
    val ocrCompanionEnabled: Boolean = true,
    val autoReconnect: Boolean = true,
    val autoConnectDefault: Boolean = true,
    val askBeforeNewDevice: Boolean = true,
    val showStatusInBar: Boolean = true,
    val connectionNotifications: Boolean = true,
    val confirmationSounds: Boolean = true,
    val vibrationFeedback: Boolean = true,
    val defaultDeviceAddress: String? = null,
) {
    val signedIn: Boolean get() = accountEmail != null
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_SEND = booleanPreferencesKey("auto_send")
        val SAVE_SIGNATURE = booleanPreferencesKey("save_signature")
        val SIGNATURE_LOCKED = booleanPreferencesKey("signature_locked")
        val DEFAULT_SIGNATURE = stringPreferencesKey("default_signature")
        val AUTO_PRINT = booleanPreferencesKey("auto_print")
        val SHOW_PAGE_1 = booleanPreferencesKey("show_page_1")
        val SHOW_PAGE_2 = booleanPreferencesKey("show_page_2")
        val MASS_PRINT = stringPreferencesKey("mass_print")
        val PRICE_UNIT = stringPreferencesKey("price_unit")
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
        val PAYMENT_TERMINAL = booleanPreferencesKey("payment_terminal_enabled")
        val OCR_COMPANION = booleanPreferencesKey("ocr_companion_enabled")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val AUTO_CONNECT_DEFAULT = booleanPreferencesKey("auto_connect_default")
        val ASK_BEFORE_NEW = booleanPreferencesKey("ask_before_new_device")
        val SHOW_STATUS_BAR = booleanPreferencesKey("show_status_in_bar")
        val CONNECTION_NOTIFICATIONS = booleanPreferencesKey("connection_notifications")
        val CONFIRMATION_SOUNDS = booleanPreferencesKey("confirmation_sounds")
        val VIBRATION_FEEDBACK = booleanPreferencesKey("vibration_feedback")
        val DEFAULT_DEVICE = stringPreferencesKey("default_device_address")
    }

    /** Every device toggle, keyed by the enum the Devices screen renders from. */
    enum class DeviceToggle(internal val key: Preferences.Key<Boolean>, val label: String) {
        PAYMENT_TERMINAL(Keys.PAYMENT_TERMINAL, "Payment Terminal Integration"),
        OCR_COMPANION(Keys.OCR_COMPANION, "Android OCR Companion"),
        AUTO_RECONNECT(Keys.AUTO_RECONNECT, "Auto-reconnect to paired devices"),
        AUTO_CONNECT_DEFAULT(Keys.AUTO_CONNECT_DEFAULT, "Auto-connect to default device"),
        ASK_BEFORE_NEW(Keys.ASK_BEFORE_NEW, "Ask before connecting new device"),
        SHOW_STATUS_BAR(Keys.SHOW_STATUS_BAR, "Show status in status bar"),
        CONNECTION_NOTIFICATIONS(Keys.CONNECTION_NOTIFICATIONS, "Enable connection notifications"),
        CONFIRMATION_SOUNDS(Keys.CONFIRMATION_SOUNDS, "Scan/payment confirmation sounds"),
        VIBRATION_FEEDBACK(Keys.VIBRATION_FEEDBACK, "Vibration feedback"),
    }

    fun AppSettings.isEnabled(toggle: DeviceToggle): Boolean = when (toggle) {
        DeviceToggle.PAYMENT_TERMINAL -> paymentTerminalEnabled
        DeviceToggle.OCR_COMPANION -> ocrCompanionEnabled
        DeviceToggle.AUTO_RECONNECT -> autoReconnect
        DeviceToggle.AUTO_CONNECT_DEFAULT -> autoConnectDefault
        DeviceToggle.ASK_BEFORE_NEW -> askBeforeNewDevice
        DeviceToggle.SHOW_STATUS_BAR -> showStatusInBar
        DeviceToggle.CONNECTION_NOTIFICATIONS -> connectionNotifications
        DeviceToggle.CONFIRMATION_SOUNDS -> confirmationSounds
        DeviceToggle.VIBRATION_FEEDBACK -> vibrationFeedback
    }

    suspend fun setDeviceToggle(toggle: DeviceToggle, value: Boolean) =
        put(toggle.key, value)

    suspend fun setDefaultDevice(address: String?) {
        context.dataStore.edit { prefs ->
            if (address == null) prefs.remove(Keys.DEFAULT_DEVICE)
            else prefs[Keys.DEFAULT_DEVICE] = address
        }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        themeMode = enumValueOrDefault(this[Keys.THEME], ThemeMode.SYSTEM),
        language = this[Keys.LANGUAGE] ?: "en",
        autoSend = this[Keys.AUTO_SEND] ?: false,
        saveSignature = this[Keys.SAVE_SIGNATURE] ?: true,
        signatureLocked = this[Keys.SIGNATURE_LOCKED] ?: false,
        defaultSignatureBase64 = this[Keys.DEFAULT_SIGNATURE],
        autoPrint = this[Keys.AUTO_PRINT] ?: false,
        showPage1 = this[Keys.SHOW_PAGE_1] ?: true,
        showPage2 = this[Keys.SHOW_PAGE_2] ?: true,
        massPrint = enumValueOrDefault(this[Keys.MASS_PRINT], MassPrintOption.BOTH),
        priceUnit = PriceUnit.from(this[Keys.PRICE_UNIT]),
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
        paymentTerminalEnabled = this[Keys.PAYMENT_TERMINAL] ?: true,
        ocrCompanionEnabled = this[Keys.OCR_COMPANION] ?: true,
        autoReconnect = this[Keys.AUTO_RECONNECT] ?: true,
        autoConnectDefault = this[Keys.AUTO_CONNECT_DEFAULT] ?: true,
        askBeforeNewDevice = this[Keys.ASK_BEFORE_NEW] ?: true,
        showStatusInBar = this[Keys.SHOW_STATUS_BAR] ?: true,
        connectionNotifications = this[Keys.CONNECTION_NOTIFICATIONS] ?: true,
        confirmationSounds = this[Keys.CONFIRMATION_SOUNDS] ?: true,
        vibrationFeedback = this[Keys.VIBRATION_FEEDBACK] ?: true,
        defaultDeviceAddress = this[Keys.DEFAULT_DEVICE],
    )

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.THEME, mode.name)
    suspend fun setLanguage(tag: String) = put(Keys.LANGUAGE, tag)
    suspend fun setAutoSend(value: Boolean) = put(Keys.AUTO_SEND, value)

    suspend fun setSignatureLocked(value: Boolean) = put(Keys.SIGNATURE_LOCKED, value)
    suspend fun setSaveSignature(value: Boolean) = put(Keys.SAVE_SIGNATURE, value)
    suspend fun setAutoPrint(value: Boolean) = put(Keys.AUTO_PRINT, value)
    suspend fun setShowPage1(value: Boolean) = put(Keys.SHOW_PAGE_1, value)
    suspend fun setShowPage2(value: Boolean) = put(Keys.SHOW_PAGE_2, value)
    suspend fun setMassPrint(option: MassPrintOption) = put(Keys.MASS_PRINT, option.name)

    suspend fun setPriceUnit(unit: PriceUnit) = put(Keys.PRICE_UNIT, unit.name)
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
