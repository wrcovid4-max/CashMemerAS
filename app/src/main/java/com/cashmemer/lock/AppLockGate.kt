package com.cashmemer.lock

import com.cashmemer.ui.components.BoldGlyph

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cashmemer.R
import com.cashmemer.core.data.AppSettings

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Wraps the app in a lock screen when App Lock is on.
 *
 * Re-locks whenever the app goes to the background, which is the behaviour the
 * setting promises — locking only at cold start would leave the till readable
 * to anyone picking up an already-running phone.
 */
@Composable
fun AppLockGate(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    if (!settings.appLock) {
        content()
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var unlocked by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) unlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (unlocked) {
        content()
    } else {
        LockScreen(
            hasPasscode = settings.passcode != null,
            onCheckPasscode = { entered -> entered == settings.passcode },
            onUnlocked = { unlocked = true },
            biometricsAvailable = biometricsAvailable(context),
        )
    }
}

private fun biometricsAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

@Composable
private fun LockScreen(
    hasPasscode: Boolean,
    biometricsAvailable: Boolean,
    onCheckPasscode: (String) -> Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    var passcode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var promptShown by remember { mutableStateOf(false) }

    // Resolved up front: the click handler is not a composable scope.
    val wrongPasscodeMessage = stringResource(R.string.wrong_passcode)

    fun promptBiometric() {
        val activity = context as? FragmentActivity ?: run {
            error = context.getString(R.string.biometric_unavailable)
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) = onUnlocked()

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    // A cancel is the user choosing the passcode instead, not
                    // something to shout about.
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        error = errString.toString()
                    }
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.unlock_title))
                .setSubtitle(context.getString(R.string.unlock_subtitle))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }

    // Offer biometrics straight away; the passcode stays visible underneath.
    LaunchedEffect(Unit) {
        if (biometricsAvailable && !promptShown) {
            promptShown = true
            promptBiometric()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoldGlyph(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_locked),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            if (biometricsAvailable) {
                OutlinedButton(
                    onClick = ::promptBiometric,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BoldGlyph(Icons.Filled.Fingerprint, contentDescription = null)
                    Text(stringResource(R.string.use_biometrics))
                }
            }

            if (hasPasscode) {
                OutlinedTextField(
                    value = passcode,
                    onValueChange = {
                        if (it.length <= 4) passcode = it.filter(Char::isDigit)
                        error = null
                    },
                    label = { Text(stringResource(R.string.passcode)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                Button(
                    onClick = {
                        if (onCheckPasscode(passcode)) {
                            onUnlocked()
                        } else {
                            error = wrongPasscodeMessage
                            passcode = ""
                        }
                    },
                    enabled = passcode.length == 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) { Text(stringResource(R.string.unlock)) }
            } else if (!biometricsAvailable) {
                // Nothing to authenticate with — never strand the owner.
                Text(
                    text = stringResource(R.string.no_lock_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onUnlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) { Text(stringResource(R.string.action_continue)) }
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
