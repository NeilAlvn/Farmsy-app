package app.farmsy.android.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.AuthException
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch

/// Free sign-up / log-in — mirrors iOS AuthView (presented as a bottom sheet
/// over guest browsing; steps aside on its own once signed in).
@Composable
fun AuthSheet(onDone: () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("farmsy", android.content.Context.MODE_PRIVATE) }

    var isSignUp by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showVerifyNote by remember { mutableStateOf(false) }

    val currentSession by session.session.collectAsState()
    LaunchedEffect(currentSession) { if (currentSession != null) onDone() }

    val canSubmit = email.contains("@") && password.length >= 8 &&
        (!isSignUp || confirm == password) && !isWorking

    fun submit() {
        if (!canSubmit) return
        isWorking = true
        errorMessage = null
        scope.launch {
            try {
                if (isSignUp) {
                    val refCode = prefs.getString("pendingRefCode", "") ?: ""
                    session.signUp(email.trim(), password, refCode.ifEmpty { null })
                    prefs.edit().putString("pendingRefCode", "").apply()
                    showVerifyNote = true
                } else {
                    session.logIn(email.trim(), password)
                }
            } catch (e: AuthException) {
                errorMessage = when (e) {
                    is AuthException.InvalidCredentials -> context.getString(R.string.invalid_email_or_password)
                    is AuthException.EmailTaken -> context.getString(R.string.an_account_with_this_email_already_exists)
                    is AuthException.Throttled -> context.getString(R.string.too_many_attempts_please_wait_a_few_minutes_and_try_again)
                    is AuthException.Server -> e.serverMessage
                }
            } catch (e: Exception) {
                errorMessage = context.getString(R.string.something_went_wrong_please_try_again)
            } finally {
                isWorking = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 30.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            Image(painterResource(R.drawable.farmsy_logo), null, Modifier.height(54.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Farmsy", style = display(30.sp), color = FarmsyColors.ink)
                Text(
                    stringResource(R.string.local_farms_fresh_finds),
                    style = geist(13.sp), color = FarmsyColors.inkMuted
                )
            }
        }

        Text(
            stringResource(if (isSignUp) R.string.create_your_free_account else R.string.welcome_back),
            style = display(28.sp), color = FarmsyColors.ink
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(
                if (isSignUp) R.string.see_every_farm_on_the_map_in_seconds
                else R.string.log_in_to_pick_up_where_you_left_off
            ),
            style = geist(15.sp), color = FarmsyColors.inkMuted
        )
        Spacer(Modifier.height(20.dp))

        AuthField(stringResource(R.string.email), email, { email = it }, KeyboardType.Email)
        Spacer(Modifier.height(14.dp))
        AuthField(
            stringResource(R.string.password), password, { password = it },
            KeyboardType.Password, isSecure = true
        )
        if (isSignUp) {
            Spacer(Modifier.height(14.dp))
            AuthField(
                stringResource(R.string.confirm_password), confirm, { confirm = it },
                KeyboardType.Password, isSecure = true
            )
        }

        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed)
        }
        if (showVerifyNote) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.we_ve_sent_a_verification_link_to_your_email_you_can_keep_ex),
                style = geist(14.sp), color = FarmsyColors.farmGreen
            )
        }

        Spacer(Modifier.height(22.dp))
        if (isWorking) {
            CircularProgressIndicator(
                color = FarmsyColors.farmGreen,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            PrimaryButton(
                stringResource(if (isSignUp) R.string.create_account else R.string.log_in),
                enabled = canSubmit
            ) { submit() }
        }

        Row(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(if (isSignUp) R.string.already_have_an_account else R.string.new_to_farmsy),
                style = geist(15.sp), color = FarmsyColors.inkMuted
            )
            TextButton(onClick = { isSignUp = !isSignUp; errorMessage = null }) {
                Text(
                    stringResource(if (isSignUp) R.string.log_in else R.string.create_one),
                    style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen
                )
            }
        }
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isSecure: Boolean = false,
) {
    Column {
        Text(label, style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isSecure) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FarmsyColors.farmGreen,
                unfocusedBorderColor = FarmsyColors.inkMuted.copy(alpha = 0.25f),
            )
        )
    }
}
