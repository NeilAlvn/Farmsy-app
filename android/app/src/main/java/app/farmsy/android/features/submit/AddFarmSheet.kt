package app.farmsy.android.features.submit

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmCategory
import app.farmsy.android.core.SubmissionApi
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/// "Add a farm shop" — mirrors iOS AddFarmView (members-enforced server-side).
/// Condensed: the essentials + categories + optional details + photos. Map
/// pin-drop is deferred to when the Maps SDK lands (address is enough).
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFarmSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()

    var form by remember { mutableStateOf(SubmissionApi.FarmSubmission()) }
    var selected by remember { mutableStateOf(setOf<FarmCategory>()) }
    var isNetherlands by remember { mutableStateOf(true) }
    var photos by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        scope.launch {
            val out = mutableListOf<ByteArray>()
            withContext(Dispatchers.IO) {
                for (uri in uris.take(5)) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input) ?: return@use
                        val bos = ByteArrayOutputStream()
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos)
                        out.add(bos.toByteArray())
                    }
                }
            }
            photos = out
        }
    }

    val canSubmit = form.name.isNotBlank() && form.city.isNotBlank() && !isSubmitting

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
                    leading = stringResource(R.string.thanks_it_s) + " ",
                    emphasis = stringResource(R.string.in_review),
                    size = 28.sp
                )
                Text(
                    stringResource(R.string.our_team_looks_at_every_submission_once_approved_the_farm_ap),
                    style = geist(15.sp), color = FarmsyColors.inkMuted
                )
                PrimaryButton(stringResource(R.string.done)) { onDismiss() }
                return@Column
            }

            DisplayTitle(
                leading = stringResource(R.string.ob_put_a_farm) + " ",
                emphasis = stringResource(R.string.ob_on_the_map),
                size = 28.sp
            )
            Text(
                stringResource(R.string.know_a_farm_shop_that_isn_t_on_farmsy_yet_fill_in_what_you_k),
                style = geist(14.sp), color = FarmsyColors.inkMuted
            )

            Section(stringResource(R.string.the_essentials)) {
                Field(stringResource(R.string.farm_name), form.name, { form = form.copy(name = it) })
                Field(stringResource(R.string.city), form.city, { form = form.copy(city = it) })
                Text(stringResource(R.string.country), style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(isNetherlands, { isNetherlands = true; form = form.copy(country = "Netherlands") }, SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.netherlands))
                    }
                    SegmentedButton(!isNetherlands, { isNetherlands = false; form = form.copy(country = "Belgium") }, SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.belgium))
                    }
                }
            }

            Section(stringResource(R.string.what_do_they_sell)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarmCategory.entries.forEach { cat ->
                        val on = selected.contains(cat)
                        Text(
                            "${cat.emoji} ${stringResource(cat.labelRes)}",
                            style = geist(13.sp, FontWeight.SemiBold),
                            color = if (on) Color.White else FarmsyColors.ink,
                            modifier = Modifier.padding(vertical = 4.dp)
                                .background(if (on) FarmsyColors.farmGreen else FarmsyColors.cream, CircleShape)
                                .clickable { selected = if (on) selected - cat else selected + cat }
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                        )
                    }
                }
            }

            Section(stringResource(R.string.tell_us_more_optional)) {
                Field(stringResource(R.string.description), form.description, { form = form.copy(description = it) })
                Field(stringResource(R.string.street_address), form.address, { form = form.copy(address = it) })
                Field(stringResource(R.string.postal_code), form.postalCode, { form = form.copy(postalCode = it) })
                Field(stringResource(R.string.phone), form.phone, { form = form.copy(phone = it) }, KeyboardType.Phone)
                Field(stringResource(R.string.website), form.website, { form = form.copy(website = it) }, KeyboardType.Uri)
                Field(stringResource(R.string.email), form.email, { form = form.copy(email = it) }, KeyboardType.Email)
                Field(stringResource(R.string.opening_hours), form.openingHours, { form = form.copy(openingHours = it) })
            }

            Section(stringResource(R.string.photos_up_to_5)) {
                Text(
                    if (photos.isEmpty()) stringResource(R.string.choose_photos)
                    else stringResource(R.string.change_photos) + " (${photos.size})",
                    style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen,
                    modifier = Modifier.clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }

            error?.let { Text(it, style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed) }

            if (isSubmitting) {
                CircularProgressIndicator(color = FarmsyColors.farmGreen, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                PrimaryButton(stringResource(R.string.submit_farm_shop), enabled = canSubmit) {
                    isSubmitting = true; error = null
                    scope.launch {
                        try {
                            val token = session.accessToken() ?: throw IllegalStateException()
                            SubmissionApi.submitFarm(
                                form.copy(
                                    farmType = selected.map { it.raw }.sorted(),
                                    imageData = photos
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = app.farmsy.android.ui.theme.display(19.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
        content()
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        Text(label, style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FarmsyColors.farmGreen,
                unfocusedBorderColor = FarmsyColors.inkMuted.copy(alpha = 0.25f),
            )
        )
    }
}
