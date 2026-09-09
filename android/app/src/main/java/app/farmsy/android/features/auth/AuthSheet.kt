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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import app.farmsy.android.core.SignUpDetails
import app.farmsy.android.pendingRefCode

/// Free sign-up / log-in — mirrors iOS AuthView (presented as a bottom sheet
/// over guest browsing; steps aside on its own once signed in).
@Composable
fun AuthSheet(onDone: () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("farmsy", android.content.Context.MODE_PRIVATE) }

    // Default to log in — most people reaching this already have an account, and
    // it's the screen they expect when they tap "Sign in". New users switch. (1:1
    // with iOS, which defaults to .logIn.)
    var isSignUp by remember { mutableStateOf(false) }
    // Signup is two steps, like the web: credentials, then personal details +
    // address. All of it is required by POST /api/auth/signup.
    var step by remember { mutableStateOf(1) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var postal by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    // Pre-filled from a referral deep link if we have one (and it hasn't aged out
    // of the 7-day window); still editable and still shown, so someone handed a
    // code verbally can type it in.
    var refCode by remember { mutableStateOf(context.pendingRefCode().orEmpty()) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    /// Set once the account is created. Signup no longer logs in — the account
    /// can't authenticate until the emailed link is clicked — so we park here.
    var verifySentTo by remember { mutableStateOf<String?>(null) }

    val currentSession by session.session.collectAsState()
    LaunchedEffect(currentSession) { if (currentSession != null) onDone() }

    val credentialsOk = email.contains("@") && password.length >= 8 &&
        (!isSignUp || confirm == password)
    // Apple 5.1.1(v): DOB, street, city, postal must not be required — only
    // name + country are required, the rest are optional.
    val detailsOk = listOf(firstName, lastName, country).all { it.isNotBlank() }
    val canSubmit = when {
        isWorking -> false
        !isSignUp -> credentialsOk
        step == 1 -> credentialsOk
        else -> detailsOk
    }

    fun submit() {
        if (!canSubmit) return
        // Step 1 of signup just advances the form — nothing is sent yet.
        if (isSignUp && step == 1) { step = 2; errorMessage = null; return }

        isWorking = true
        errorMessage = null
        scope.launch {
            try {
                if (isSignUp) {
                    session.signUp(
                        SignUpDetails(
                            email = email.trim(), password = password,
                            firstName = firstName.trim(), lastName = lastName.trim(),
                            dob = dob.trim(), streetAddress = street.trim(),
                            city = city.trim(), postalCode = postal.trim(),
                            country = country.trim(),
                            refCode = refCode.trim().ifEmpty { null },
                        )
                    )
                    // Consume the referral code only once it has actually been
                    // accepted, so a failed signup doesn't burn it.
                    prefs.edit().putString("pendingRefCode", "").apply()
                    verifySentTo = email.trim()
                } else {
                    session.logIn(email.trim(), password)
                }
            } catch (e: AuthException) {
                errorMessage = when (e) {
                    is AuthException.InvalidCredentials -> context.getString(R.string.invalid_email_or_password)
                    is AuthException.EmailTaken -> context.getString(R.string.an_account_with_this_email_already_exists)
                    is AuthException.Throttled -> context.getString(R.string.too_many_attempts_please_wait_a_few_minutes_and_try_again)
                    // Right password, unverified inbox. Sending them to reset a
                    // working password would be the worst possible advice.
                    is AuthException.EmailNotVerified -> context.getString(R.string.email_not_verified_msg)
                    is AuthException.InvalidDob -> context.getString(R.string.invalid_dob_msg)
                    is AuthException.MissingFields -> context.getString(R.string.please_fill_all_fields)
                    is AuthException.Server -> e.serverMessage
                }
                // A field-level rejection belongs on the field-level step.
                if (e is AuthException.MissingFields || e is AuthException.InvalidDob) step = 2
            } catch (e: Exception) {
                errorMessage = context.getString(R.string.something_went_wrong_please_try_again)
            } finally {
                isWorking = false
            }
        }
    }

    // Account created: no session yet, so show the "check your inbox" state
    // instead of dropping the user back into a form that looks like it failed.
    verifySentTo?.let { sentTo ->
        VerifyEmailNotice(email = sentTo, onDone = onDone)
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .animateContentSize()
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

        // Fields slide as the mode/step changes — 1:1 with iOS's spring transition.
        // `credentials` (step 1 / log in) slides against `details` (step 2).
        AnimatedContent(
            targetState = (!isSignUp || step == 1),
            transitionSpec = {
                val forward = !targetState // moving to details = forward
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(280)) { it * dir } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it * dir } + fadeOut(tween(200)))
            },
            label = "auth-fields",
        ) { credentials ->
            Column {
                if (credentials) {
                    AuthField(stringResource(R.string.email), email, { email = it }, KeyboardType.Email, placeholder = "you@email.com")
                    Spacer(Modifier.height(14.dp))
                    AuthField(
                        stringResource(R.string.password), password, { password = it },
                        KeyboardType.Password, isSecure = true,
                        // iOS shows this placeholder in both modes (AuthView:130). The
                        // sign-in field was left blank on Android — give it the same hint.
                        placeholder = stringResource(R.string.auth_pw_placeholder),
                    )
                    if (isSignUp) {
                        Spacer(Modifier.height(14.dp))
                        AuthField(
                            stringResource(R.string.confirm_password), confirm, { confirm = it },
                            KeyboardType.Password, isSecure = true,
                            placeholder = stringResource(R.string.auth_confirm_placeholder),
                        )
                    }
                } else {
                    // Step 2: the profile fields the API now requires.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { AuthField(stringResource(R.string.first_name), firstName, { firstName = it }) }
                        Box(Modifier.weight(1f)) { AuthField(stringResource(R.string.last_name), lastName, { lastName = it }) }
                    }
                    Spacer(Modifier.height(14.dp))
                    DobField(value = dob, onPick = { dob = it })
                    Spacer(Modifier.height(14.dp))
                    val optional = stringResource(R.string.label_optional_suffix)
                    AuthField("${stringResource(R.string.street_address)} ($optional)", street, { street = it })
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1.4f)) { AuthField("${stringResource(R.string.city_label)} ($optional)", city, { city = it }) }
                        Box(Modifier.weight(1f)) { AuthField("${stringResource(R.string.postal_code)} ($optional)", postal, { postal = it }) }
                    }
                    Spacer(Modifier.height(14.dp))
                    AuthField(stringResource(R.string.country), country, { country = it })
                    Spacer(Modifier.height(14.dp))
                    AuthField(stringResource(R.string.referral_code_optional), refCode, { refCode = it })
                }
            }
        }

        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed)
        }

        Spacer(Modifier.height(22.dp))
        if (isWorking) {
            CircularProgressIndicator(
                color = FarmsyColors.farmGreen,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            PrimaryButton(
                stringResource(
                    when {
                        !isSignUp -> R.string.log_in
                        step == 1 -> R.string.continue_label
                        else -> R.string.create_account
                    }
                ),
                enabled = canSubmit
            ) { submit() }
            if (isSignUp && step == 2) {
                TextButton(
                    onClick = { step = 1; errorMessage = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.back), style = geist(15.sp), color = FarmsyColors.inkMuted)
                }
            }
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
    keyboardType: KeyboardType = KeyboardType.Text,
    isSecure: Boolean = false,
    placeholder: String = "",
) {
    var reveal by remember { mutableStateOf(false) }
    Column {
        Text(label, style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, style = geist(15.sp), color = FarmsyColors.inkMuted.copy(alpha = 0.7f)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isSecure && !reveal) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
            // A show/hide eye on password fields, like iOS.
            trailingIcon = if (isSecure) {
                {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(
                            if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            null, tint = FarmsyColors.inkMuted,
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FarmsyColors.farmGreen,
                unfocusedBorderColor = FarmsyColors.inkMuted.copy(alpha = 0.25f),
            )
        )
    }
}

/// Date of birth, via the platform picker.
///
/// The API wants a real ISO `yyyy-MM-dd` and rejects under-16s, so free text would
/// just bounce (`invalid_dob`) after a round trip. The picker can't produce a
/// malformed date, and we cap it at today-minus-16 so the age rule is enforced
/// before the user ever submits.
@Composable
private fun DobField(value: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val today = remember { java.util.Calendar.getInstance() }
    val maxDate = remember {
        (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.YEAR, -16) }
    }

    Column {
        Text(
            "${stringResource(R.string.date_of_birth)} (${stringResource(R.string.label_optional_suffix)})",
            style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clickable {
                    val start = maxDate
                    android.app.DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            onPick("%04d-%02d-%02d".format(y, m + 1, d))
                        },
                        start.get(java.util.Calendar.YEAR),
                        start.get(java.util.Calendar.MONTH),
                        start.get(java.util.Calendar.DAY_OF_MONTH),
                    ).apply {
                        datePicker.maxDate = maxDate.timeInMillis
                    }.show()
                }
                .background(Color.White, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text(
                value.ifEmpty { stringResource(R.string.dob_hint) },
                style = geist(15.sp),
                color = if (value.isEmpty()) FarmsyColors.inkMuted else FarmsyColors.ink,
            )
        }
    }
}

/// Shown after a successful signup. There is no session yet — the account can't
/// authenticate until the emailed link is clicked — so parking the user here is
/// the honest state. Dropping them back on the form would read as a failure.
@Composable
private fun VerifyEmailNotice(email: String, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 30.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📬", style = geist(46.sp))
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.check_your_inbox),
            style = display(26.sp), color = FarmsyColors.ink
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.verification_link_sent, email),
            style = geist(15.sp), color = FarmsyColors.inkMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.log_in), onClick = onDone)
    }
}
