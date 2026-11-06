package com.elcomp.medicationorganizer.features.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.elcomp.medicationorganizer.core.network.ApiError
import com.elcomp.medicationorganizer.core.network.ApiException
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.session.SessionStorageException
import com.elcomp.medicationorganizer.core.session.SignOutReason
import com.elcomp.medicationorganizer.core.ui.AppActionButton
import com.elcomp.medicationorganizer.core.ui.AppErrorState
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import com.elcomp.medicationorganizer.core.ui.AppTextField
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import com.elcomp.medicationorganizer.features.auth.data.dto.RegisterRequest
import com.elcomp.medicationorganizer.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class AuthScreen(val tag: String, val title: StringResource) {
    Welcome("welcome-screen", Res.string.welcome_title),
    Login("auth-login", Res.string.auth_login_title),
    Register("auth-register", Res.string.auth_register_title),
}

@Composable
internal fun AuthFlow(
    services: AppServices,
    reason: SignOutReason?,
    status: @Composable () -> Unit,
) {
    val initialScreen = if (reason == null) AuthScreen.Welcome else AuthScreen.Login
    var screenName by rememberSaveable { mutableStateOf(initialScreen.name) }
    var email by rememberSaveable { mutableStateOf("") }
    var registrationComplete by rememberSaveable { mutableStateOf(false) }
    val screen = AuthScreen.entries.firstOrNull { it.name == screenName } ?: initialScreen
    val isDemo = services.settings is ApiSettings.Mock

    Surface(modifier = Modifier.fillMaxSize()) {
        key(screen) {
            val title = stringResource(screen.title)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .testTag(screen.tag)
                    .semantics { paneTitle = title },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                status()
                if (screen == AuthScreen.Welcome) {
                    Welcome(
                        demoSignIn = if (isDemo) {
                            { services.signInWithDemoAccount() }
                        } else {
                            null
                        },
                        onLogin = { screenName = AuthScreen.Login.name },
                    )
                } else {
                    val notice = when {
                        screen != AuthScreen.Login -> null
                        registrationComplete -> Res.string.auth_registered
                        reason == SignOutReason.EXPIRED || reason == SignOutReason.UNAUTHORIZED -> Res.string.auth_expired
                        reason == SignOutReason.STORAGE_ERROR -> Res.string.auth_storage_unavailable
                        else -> null
                    }
                    if (notice != null) {
                        Text(
                            stringResource(notice),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    val register = screen == AuthScreen.Register
                    if (register && isDemo) {
                        Text(stringResource(Res.string.auth_demo_register_hint))
                    }
                    AuthForm(
                        register = register,
                        email = email,
                        onEmailChange = { email = it },
                        onSwitch = {
                            registrationComplete = false
                            screenName = if (screen == AuthScreen.Register) AuthScreen.Login.name else AuthScreen.Register.name
                        },
                        onBackToWelcome = if (reason == null && !register) {
                            { screenName = AuthScreen.Welcome.name }
                        } else {
                            null
                        },
                        onLogin = services::signIn,
                        onRegister = { request ->
                            services.auth.register(request)
                            email = request.email
                            registrationComplete = true
                            screenName = AuthScreen.Login.name
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Welcome(demoSignIn: (suspend () -> Unit)?, onLogin: () -> Unit) {
    Text(stringResource(Res.string.welcome_description), style = MaterialTheme.typography.bodyLarge)
    if (demoSignIn != null) {
        var busy by remember { mutableStateOf(false) }
        var failure by remember { mutableStateOf<StringResource?>(null) }
        if (failure != null) {
            AppErrorState(message = stringResource(requireNotNull(failure)))
        }
        AppSubmitButton(
            text = stringResource(Res.string.auth_try_demo),
            onSubmit = {
                busy = true
                failure = null
                try {
                    demoSignIn()
                } catch (error: ApiException) {
                    failure = error.authMessage()
                } catch (_: SessionStorageException) {
                    failure = Res.string.auth_storage_unavailable
                } finally {
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("welcome-start"),
        )
        AuthSecondaryAction(Res.string.auth_login_title, "welcome-login", enabled = !busy, onClick = onLogin)
    } else {
        AppActionButton(
            text = stringResource(Res.string.welcome_start),
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().testTag("welcome-start"),
        )
    }
    Text(stringResource(Res.string.app_data_note), style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AuthForm(
    register: Boolean,
    email: String,
    onEmailChange: (String) -> Unit,
    onSwitch: () -> Unit,
    onBackToWelcome: (() -> Unit)?,
    onLogin: suspend (LoginRequest) -> Unit,
    onRegister: suspend (RegisterRequest) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<StringResource?>(null) }

    BackHandler(enabled = !busy && (register || onBackToWelcome != null)) {
        if (register) onSwitch() else onBackToWelcome?.invoke()
    }

    val address = email.trim()
    val name = displayName.trim()
    val emailIssue = when {
        conflict -> Res.string.auth_email_conflict
        address.length > 254 || !emailFormat.matches(address) -> Res.string.auth_email_required
        else -> null
    }
    val passwordIssue = when {
        password.isBlank() -> Res.string.auth_password_required
        password.length !in 8..32 -> Res.string.auth_password_length
        else -> null
    }
    val nameIssue = Res.string.profile_name_too_long.takeIf { register && name.length > 32 }

    AppTextField(
        value = email,
        onValueChange = {
            conflict = false
            onEmailChange(it)
        },
        label = stringResource(Res.string.auth_email_label),
        modifier = Modifier.testTag("auth-email"),
        enabled = !busy,
        errorMessage = if (submitted && emailIssue != null) stringResource(emailIssue) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
    )
    AppTextField(
        value = password,
        onValueChange = { password = it },
        label = stringResource(Res.string.auth_password_label),
        modifier = Modifier.testTag("auth-password"),
        enabled = !busy,
        errorMessage = if (submitted && passwordIssue != null) stringResource(passwordIssue) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        visualTransformation = PasswordVisualTransformation(),
    )
    if (register) {
        AppTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = stringResource(Res.string.auth_display_name_label),
            modifier = Modifier.testTag("auth-display-name"),
            enabled = !busy,
            errorMessage = if (submitted && nameIssue != null) stringResource(nameIssue) else null,
        )
    }
    if (failure != null) {
        AppErrorState(message = stringResource(requireNotNull(failure)))
    }
    AppSubmitButton(
        text = stringResource(if (register) Res.string.auth_register_title else Res.string.auth_login_title),
        onSubmit = {
            submitted = true
            failure = null
            if (emailIssue == null && passwordIssue == null && nameIssue == null) {
                busy = true
                try {
                    if (register) {
                        onRegister(RegisterRequest(address, password, name.ifBlank { null }))
                    } else {
                        onLogin(LoginRequest(address, password))
                    }
                    password = ""
                } catch (error: ApiException) {
                    if (register && error.error == ApiError.CONFLICT) {
                        conflict = true
                    } else {
                        failure = error.authMessage()
                    }
                } catch (_: SessionStorageException) {
                    failure = Res.string.auth_storage_unavailable
                } finally {
                    busy = false
                }
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("auth-submit"),
    )
    AuthSecondaryAction(
        if (register) Res.string.auth_back_to_login else Res.string.auth_register_title,
        if (register) "auth-back-login" else "auth-open-register",
        enabled = !busy,
        onClick = onSwitch,
    )
    if (onBackToWelcome != null) {
        TextButton(
            onClick = onBackToWelcome,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            enabled = !busy,
        ) {
            Text(stringResource(Res.string.auth_back_to_welcome))
        }
    }
}

@Composable
private fun AuthSecondaryAction(text: StringResource, tag: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
        enabled = enabled,
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.titleMedium)
    }
}

private fun ApiException.authMessage(): StringResource = when (error) {
    ApiError.UNAUTHORIZED -> Res.string.auth_wrong_credentials
    ApiError.INVALID_REQUEST -> Res.string.auth_invalid_request
    ApiError.NETWORK -> Res.string.auth_network_error
    ApiError.TIMEOUT -> Res.string.auth_timeout
    ApiError.SERVER -> Res.string.auth_server_error
    else -> Res.string.auth_request_failed
}

private val emailFormat = Regex("[^@\\s]+@[^@\\s]+")
