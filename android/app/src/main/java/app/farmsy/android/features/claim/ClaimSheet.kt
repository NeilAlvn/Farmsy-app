package app.farmsy.android.features.claim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.SubmissionApi
import app.farmsy.android.core.SubmissionException
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch

/// "This is my farm" — mirrors iOS ClaimFarmView. Creates a pending ownership
/// claim, verified by business email or KVK number.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimSheet(pin: FarmPin, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(session.email) }
    var phone by remember { mutableStateOf("") }
    var useKvk by remember { mutableStateOf(false) }
    var kvk by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    val canSubmit = fullName.isNotBlank() && email.isNotBlank() && phone.isNotBlank() &&
        (!useKvk || kvk.isNotBlank()) && !isSubmitting

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FarmsyColors.cream,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (done) {
                Spacer(Modifier.height(20.dp))
                DisplayTitle(
                    leading = stringResource(R.string.ob_claim) + " ",
                    emphasis = stringResource(R.string.ob_sent),
                    size = 30.sp,
                )
                Text(
                    stringResource(R.string.we_ll_be_in_touch_at_arg_once_the_team_has_verified_your_cla, email),
                    style = geist(15.sp), color = FarmsyColors.inkMuted
                )
                PrimaryButton(stringResource(R.string.done)) { onDismiss() }
                return@Column
            }

            Kicker(stringResource(R.string.for_farm_owners))
            DisplayTitle(
                leading = stringResource(R.string.ob_is) + " ",
                emphasis = pin.name,
                trailing = " " + stringResource(R.string.ob_yours_q),
                size = 26.sp
            )
            Text(
                stringResource(R.string.claim_it_to_keep_your_details_up_to_date_our_team_verifies_e),
                style = geist(14.sp), color = FarmsyColors.inkMuted
            )

            Field(stringResource(R.string.your_full_name), fullName, { fullName = it }, stringResource(R.string.ph_first_last))
            Field(stringResource(R.string.email_2), email, { email = it }, stringResource(R.string.ph_farm_email), KeyboardType.Email)
            Field(stringResource(R.string.phone_2), phone, { phone = it }, stringResource(R.string.ph_phone), KeyboardType.Phone)

            Text(stringResource(R.string.how_should_we_verify_you), style = geist(19.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !useKvk, onClick = { useKvk = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text(stringResource(R.string.business_email)) }
                SegmentedButton(
                    selected = useKvk, onClick = { useKvk = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text(stringResource(R.string.kvk_number)) }
            }
            if (useKvk) {
                Field(stringResource(R.string.kvk_number_2), kvk, { kvk = it }, stringResource(R.string.ph_kvk), KeyboardType.Number)
            } else {
                Text(
                    stringResource(R.string.use_an_email_address_on_your_farm_s_own_domain_and_we_can_ve),
                    style = geist(13.sp), color = FarmsyColors.inkMuted
                )
            }
            Field(stringResource(R.string.anything_we_should_know_optional), message, { message = it }, "")

            error?.let { Text(it, style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed) }

            if (isSubmitting) {
                CircularProgressIndicator(
                    color = FarmsyColors.farmGreen,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                PrimaryButton(stringResource(R.string.send_claim), enabled = canSubmit) {
                    isSubmitting = true; error = null
                    scope.launch {
                        try {
                            val token = session.accessToken()
                                ?: throw SubmissionException.NotSignedIn()
                            SubmissionApi.submitClaim(
                                SubmissionApi.FarmClaim(
                                    farmOsmId = pin.osmId, farmName = pin.name,
                                    fullName = fullName.trim(), email = email.trim(), phone = phone.trim(),
                                    verificationMethod = if (useKvk) "kvk" else "email",
                                    kvkNumber = if (useKvk) kvk.trim() else null,
                                    message = message.ifBlank { null }
                                ),
                                token
                            )
                            done = true
                        } catch (e: Exception) {
                            error = context.getString(R.string.something_went_wrong_please_try_again)
                        } finally {
                            isSubmitting = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    placeholder: String, keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        Text(label, style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            placeholder = { Text(placeholder, style = geist(15.sp)) },
            modifier = Modifier.fillMaxWidth(), singleLine = keyboardType != KeyboardType.Text,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FarmsyColors.farmGreen,
                unfocusedBorderColor = FarmsyColors.inkMuted.copy(alpha = 0.25f),
            )
        )
    }
}
