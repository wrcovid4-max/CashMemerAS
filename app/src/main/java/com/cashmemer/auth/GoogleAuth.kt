package com.cashmemer.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.cashmemer.core.BuildConfig
import com.cashmemer.core.data.SettingsStore
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** The Google account the user picked. */
data class GoogleAccount(
    val name: String?,
    val email: String,
    val photoUrl: String?,
    /** Verify this server-side before trusting it for anything sensitive. */
    val idToken: String,
)

/**
 * Google sign-in over Credential Manager. Deliberately free of Firebase so the
 * project still builds without a google-services.json — this establishes
 * identity only; it does not by itself sync anything to the cloud.
 */
object GoogleAuth {

    class NotConfiguredException : Exception(
        "GOOGLE_WEB_CLIENT_ID is missing from local.properties"
    )

    class CancelledException : Exception("Sign-in cancelled")

    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /**
     * Shows the account chooser. [context] must be an Activity — Credential
     * Manager renders a bottom sheet and needs a window to attach to.
     */
    suspend fun signIn(context: Context): Result<GoogleAccount> = runCatching {
        if (!isConfigured) throw NotConfiguredException()

        // filterByAuthorizedAccounts=false so a first-time user still sees the
        // full account list rather than an empty sheet.
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (cancelled: GetCredentialCancellationException) {
            throw CancelledException()
        } catch (none: NoCredentialException) {
            throw Exception("No Google account available on this device")
        }

        val credential = GoogleIdTokenCredential.createFrom(response.credential.data)

        GoogleAccount(
            name = credential.displayName ?: credential.givenName,
            email = credential.id,
            photoUrl = credential.profilePictureUri?.toString(),
            idToken = credential.idToken,
        )
    }

    /** Signs in and persists the identity so the account card survives restarts. */
    suspend fun signInAndStore(
        context: Context,
        settingsStore: SettingsStore,
    ): Result<GoogleAccount> = signIn(context).onSuccess { account ->
        settingsStore.setAccount(account.name, account.email, account.photoUrl)
    }

    suspend fun signOut(context: Context, settingsStore: SettingsStore) {
        runCatching {
            CredentialManager.create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        }
        settingsStore.setAccount(null, null, null)
    }
}
