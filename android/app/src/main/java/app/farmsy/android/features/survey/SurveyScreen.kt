package app.farmsy.android.features.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.BuildConfig
import app.farmsy.android.LocalSession
import app.farmsy.android.core.SurveyApi
import app.farmsy.android.core.SurveyDefinition
import app.farmsy.android.core.SurveyKind
import app.farmsy.android.core.SurveyQuestion
import app.farmsy.android.core.SurveyRefused
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch

/// The "why-farm" survey — a 1:1 port of iOS `SurveyView`. All seven questions on ONE
/// scrolling screen (never a wizard). Questions come from `SurveyApi` localized; only
/// the local chrome (title, counters, buttons, the post-answer feedback box) lives
/// here. Chrome copy is en-only, matching iOS: the new survey's strings are not in
/// messages/*.json (only the old 2-step map survey's are).
///
/// Presented as a ModalBottomSheet from the map's floating survey button (MainScreen).
/// What the entry point opens (Aviah's spec — the button never goes away, it changes
/// what it opens): the seven questions (not-answered), or the feedback box directly
/// (already answered).
enum class SurveyMode { QUESTIONS, FEEDBACK }

/// FEEDBACK is reached two ways: after submitting the questions, or directly when an
/// already-answered person taps the button. Both render the same feedback screen.
private enum class Phase { LOADING, FAILED, READY, FEEDBACK }

@Composable
fun SurveyScreen(mode: SurveyMode = SurveyMode.QUESTIONS, onClose: () -> Unit) {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val signedIn = session.isAuthenticated

    val localeCode = LocalConfiguration.current.locales[0].language
    val locale = SurveyApi.clampLocale(localeCode)

    var phase by remember { mutableStateOf(Phase.LOADING) }
    var def by remember { mutableStateOf<SurveyDefinition?>(null) }
    /// DEBUG only: the gate said hide (admin / already answered) but a Debug build
    /// showed the survey anyway for testing. Always false in Release.
    var gateWouldHide by remember { mutableStateOf(false) }

    val single = remember { mutableStateMapOf<String, String>() }        // one → option id
    val multi = remember { mutableStateMapOf<String, List<String>>() }    // many → ordered ids
    val texts = remember { mutableStateMapOf<String, String>() }          // text → free text

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    fun load() {
        phase = Phase.LOADING
        gateWouldHide = false
        scope.launch {
            val token = session.accessToken()
            // Gate first. Admin is never shown in either mode. In QUESTIONS mode an
            // already-answered user is also excluded (they get FEEDBACK from the button
            // instead); in FEEDBACK mode `answered` is expected, so it is not a hide.
            // A FAILED gate call is not a hide — `gate()` is best-effort and returns
            // all-false on any error, so a blip falls through to load, not close.
            val gate = SurveyApi.gate(token)
            val excluded = gate.isAdmin || (mode == SurveyMode.QUESTIONS && gate.answered)
            if (excluded) {
                @Suppress("KotlinConstantConditions")
                if (BuildConfig.DEBUG) {
                    // Debug renders the survey anyway (with a banner) so a developer —
                    // whose account is role=admin, so the gate hides it — can test the
                    // UI. RELEASE closes here unchanged. Submit still returns is_admin
                    // for an admin, so test submit with a non-admin account.
                    gateWouldHide = true
                } else {
                    onClose(); return@launch
                }
            }
            // Feedback mode skips the questions fetch entirely — straight to the box.
            if (mode == SurveyMode.FEEDBACK) {
                phase = Phase.FEEDBACK
                return@launch
            }
            runCatching { SurveyApi.questions(locale) }
                .onSuccess { def = it; phase = Phase.READY }
                .onFailure { phase = Phase.FAILED }
        }
    }

    LaunchedEffect(Unit) { load() }

    Box(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
        when (phase) {
            Phase.LOADING -> CircularProgressIndicator(
                color = FarmsyColors.farmGreenMap, modifier = Modifier.align(Alignment.Center),
            )
            Phase.FAILED -> LoadError(onRetry = { load() }, modifier = Modifier.align(Alignment.Center))
            Phase.READY -> Form(
                def = def, signedIn = signedIn, gateWouldHide = gateWouldHide,
                single = single, multi = multi, texts = texts,
                name = name, onName = { name = it }, email = email, onEmail = { email = it },
                submitting = submitting, submitError = submitError, onClose = onClose,
                onSubmit = {
                    submitError = null; submitting = true
                    scope.launch {
                        val answers = collectAnswers(def, single, multi, texts)
                        runCatching {
                            SurveyApi.submit(
                                locale = locale, answers = answers,
                                name = if (signedIn) null else name,
                                email = if (signedIn) null else email,
                                accessToken = session.accessToken(),
                            )
                        }.onSuccess {
                            submitting = false; phase = Phase.FEEDBACK
                        }.onFailure { e ->
                            submitting = false
                            submitError = if (e is SurveyRefused && e.reason == "incomplete")
                                "It looks like a question is still unanswered."
                            else "We couldn't save that. Please try again."
                        }
                    }
                },
            )
            Phase.FEEDBACK -> Feedback(signedIn = signedIn, onClose = onClose)
        }
    }
}

private fun collectAnswers(
    def: SurveyDefinition?,
    single: Map<String, String>,
    multi: Map<String, List<String>>,
    texts: Map<String, String>,
): Map<String, SurveyApi.Answer> {
    val out = mutableMapOf<String, SurveyApi.Answer>()
    for (q in def?.questions.orEmpty()) {
        when (q.kind) {
            SurveyKind.one -> single[q.id]?.let { out[q.id] = SurveyApi.Answer.Options(listOf(it)) }
            SurveyKind.many -> multi[q.id]?.takeIf { it.isNotEmpty() }
                ?.let { out[q.id] = SurveyApi.Answer.Options(it) }
            SurveyKind.text -> texts[q.id]?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { out[q.id] = SurveyApi.Answer.Text(it) }
        }
    }
    return out
}

@Composable
private fun LoadError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Couldn't load the survey. Check your connection and try again.",
            style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
        )
        Text(
            "Try again", style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreenMap,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { onRetry() },
        )
    }
}

@Composable
private fun Form(
    def: SurveyDefinition?,
    signedIn: Boolean,
    gateWouldHide: Boolean,
    single: MutableMap<String, String>,
    multi: MutableMap<String, List<String>>,
    texts: MutableMap<String, String>,
    name: String, onName: (String) -> Unit,
    email: String, onEmail: (String) -> Unit,
    submitting: Boolean,
    submitError: String?,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Seven questions about buying from farms",
                style = geist(18.sp, FontWeight.Bold), color = FarmsyColors.ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            Box(
                Modifier.size(32.dp).background(Color(0xFFF3F4F6), CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, null, tint = Color(0xFF6B7280), modifier = Modifier.size(14.dp)) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            if (gateWouldHide) DebugGateBanner()
            def?.questions?.forEachIndexed { i, q ->
                QuestionBlock(number = i + 1, q = q, single = single, multi = multi, texts = texts)
            }
            if (!signedIn) IdentityBlock(name, onName, email, onEmail)
        }

        // Footer: "N to go" + Send
        val remaining = requiredRemaining(def, single, multi)
        val needsEmail = !signedIn && email.trim().isEmpty()
        val canSubmit = remaining == 0 && !needsEmail
        Column(
            Modifier.fillMaxWidth().background(FarmsyColors.cream),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HorizontalDivider(color = FarmsyColors.hairline)
            if (submitError != null) {
                Text(
                    submitError, style = geist(13.sp, FontWeight.Medium), color = FarmsyColors.warnRed,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    remainingLabel(remaining, needsEmail),
                    style = geist(12.sp), color = FarmsyColors.inkMuted,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(
                            if (canSubmit) FarmsyColors.farmGreenMap else FarmsyColors.farmGreenMap.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = canSubmit && !submitting) { onSubmit() }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (submitting) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Send", style = geist(15.sp, FontWeight.SemiBold), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugGateBanner() {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Debug: the gate would hide this (admin or already answered). Shown for testing — Release excludes it.",
            style = geist(12.sp, FontWeight.Medium), color = Color(0xFF9A6B00),
        )
    }
}

@Composable
private fun QuestionBlock(
    number: Int,
    q: SurveyQuestion,
    single: MutableMap<String, String>,
    multi: MutableMap<String, List<String>>,
    texts: MutableMap<String, String>,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "$number", style = geist(12.sp, FontWeight.Bold), color = FarmsyColors.farmGreen,
            modifier = Modifier.padding(top = 1.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            Text(q.text, style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
            hint(q)?.let { Text(it, style = geist(12.sp), color = FarmsyColors.inkMuted) }
            when (q.kind) {
                SurveyKind.one -> q.options.forEach { opt ->
                    OptionRow(label = opt.label, on = single[q.id] == opt.id, multiSelect = false) {
                        single[q.id] = opt.id
                    }
                }
                SurveyKind.many -> q.options.forEach { opt ->
                    val cur = multi[q.id].orEmpty()
                    OptionRow(label = opt.label, on = opt.id in cur, multiSelect = true) {
                        multi[q.id] = if (opt.id in cur) cur - opt.id else {
                            val added = cur + opt.id
                            // Cap: a tap past `max` swaps out the OLDEST, not refused.
                            if (q.max != null && added.size > q.max) added.drop(added.size - q.max) else added
                        }
                    }
                }
                SurveyKind.text -> TextRow(value = texts[q.id] ?: "") { texts[q.id] = it }
            }
        }
    }
}

private fun hint(q: SurveyQuestion): String? = when {
    q.kind == SurveyKind.many && q.max != null -> "Choose up to ${q.max}"
    q.kind == SurveyKind.many -> "More than one answer is possible"
    else -> null
}

@Composable
private fun OptionRow(label: String, on: Boolean, multiSelect: Boolean, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (on) FarmsyColors.farmGreen.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.dp, if (on) FarmsyColors.farmGreen.copy(alpha = 0.55f) else FarmsyColors.hairline, RoundedCornerShape(12.dp))
            .clickable { onTap() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Indicator(on = on, square = multiSelect)
        Text(
            label,
            style = geist(14.sp, if (on) FontWeight.SemiBold else FontWeight.Normal),
            color = FarmsyColors.ink, modifier = Modifier.weight(1f),
        )
    }
}

/// The indicator shape is the only thing telling someone whether they may pick more
/// than one: a circle for single-answer, a rounded square for multi. Not both ticks.
@Composable
private fun Indicator(on: Boolean, square: Boolean) {
    val shape = if (square) RoundedCornerShape(5.dp) else CircleShape
    Box(
        Modifier.size(16.dp)
            .background(if (on) FarmsyColors.farmGreen else Color.Transparent, shape)
            .border(1.5.dp, if (on) FarmsyColors.farmGreen else FarmsyColors.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
    }
}

@Composable
private fun TextRow(value: String, onChange: (String) -> Unit) {
    StyledField(value = value, onChange = onChange, placeholder = "Optional", singleLine = false)
}

@Composable
private fun IdentityBlock(name: String, onName: (String) -> Unit, email: String, onEmail: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = FarmsyColors.hairline)
        Text("And who may we thank?", style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
        Text(
            "Your email lets us follow up if you asked us something. Name is optional.",
            style = geist(12.sp), color = FarmsyColors.inkMuted,
        )
        StyledField(value = name, onChange = onName, placeholder = "Name (optional)")
        StyledField(value = email, onChange = onEmail, placeholder = "Email", keyboardType = KeyboardType.Email)
    }
}

/// The app's plain field style (AuthSheet AuthField): white@60 fill, hairline border,
/// rounded 12, geist body.
@Composable
private fun StyledField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    Box(
        Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.height(64.dp)),
            singleLine = singleLine,
            textStyle = geist(14.sp).copy(color = FarmsyColors.ink),
            cursorBrush = SolidColor(FarmsyColors.farmGreen),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = geist(14.sp), color = FarmsyColors.inkMuted.copy(alpha = 0.7f))
                }
                inner()
            },
        )
    }
}

/// The feedback surface (Aviah's spec — the entry button never goes away, it changes
/// what it opens). Reached after submitting the questions, or directly when an
/// already-answered person taps the button; the copy is the same either way. Subject
/// + message (+ name/email when signed out) → `POST /api/contact` topic=feedback. The
/// order is the point: a remark from someone who told us what they came for is worth
/// more. Copy is Aviah's verbatim from the thread.
@Composable
private fun Feedback(signedIn: Boolean, onClose: () -> Unit) {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()

    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }

    val effectiveName = if (signedIn) session.displayName else name
    val effectiveEmail = if (signedIn) session.email else email
    val canSend = message.trim().isNotEmpty() && effectiveName.trim().isNotEmpty() && effectiveEmail.trim().isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).background(Color(0xFFF3F4F6), CircleShape).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, null, tint = Color(0xFF6B7280), modifier = Modifier.size(14.dp)) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("What could be better?", style = geist(18.sp, FontWeight.Bold), color = FarmsyColors.ink, textAlign = TextAlign.Center)
            Text(
                "You have already answered the questions, thank you. Anything you write here comes straight to us.",
                style = geist(14.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
            )

            if (sent) {
                // After sending — a tick, the confirmation, and a way to add more.
                Icon(Icons.Filled.CheckCircle, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(36.dp))
                Text("Thank you, we read every one.", style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen, textAlign = TextAlign.Center)
                Text(
                    "Add something else",
                    style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.farmGreenMap,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { subject = ""; message = ""; sent = false },
                )
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = FarmsyColors.hairline)
                    // Two fields that visibly want different things (Aviah): a subject
                    // and a message, so the subject line isn't the whole report.
                    StyledField(value = subject, onChange = { subject = it }, placeholder = "Subject")
                    StyledField(value = message, onChange = { message = it }, placeholder = "Your message", singleLine = false)
                    if (!signedIn) {
                        StyledField(value = name, onChange = { name = it }, placeholder = "Name")
                        StyledField(value = email, onChange = { email = it }, placeholder = "Email", keyboardType = KeyboardType.Email)
                    }
                    error?.let { Text(it, style = geist(13.sp, FontWeight.Medium), color = FarmsyColors.warnRed) }
                    Box(
                        Modifier.fillMaxWidth()
                            .background(
                                if (canSend) FarmsyColors.farmGreenMap else FarmsyColors.farmGreenMap.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable(enabled = canSend && !sending) {
                                error = null; sending = true
                                scope.launch {
                                    runCatching {
                                        SurveyApi.sendFeedback(
                                            subject = subject, message = message,
                                            name = effectiveName, email = effectiveEmail,
                                            accessToken = session.accessToken(),
                                        )
                                    }.onSuccess { sending = false; sent = true }
                                        .onFailure { sending = false; error = "We couldn't send that. Please try again." }
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sending) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Send feedback", style = geist(15.sp, FontWeight.SemiBold), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/// Required = the non-optional questions (the five choice ones) unanswered. Text
/// questions are optional, so excluded. Email is handled separately in the footer.
private fun requiredRemaining(
    def: SurveyDefinition?,
    single: Map<String, String>,
    multi: Map<String, List<String>>,
): Int = def?.questions.orEmpty().count { q ->
    if (q.optional) return@count false
    when (q.kind) {
        SurveyKind.one -> single[q.id] == null
        SurveyKind.many -> multi[q.id].isNullOrEmpty()
        SurveyKind.text -> false
    }
}

private fun remainingLabel(remaining: Int, needsEmail: Boolean): String = when {
    remaining > 0 -> "$remaining questions to go"
    needsEmail -> "Just your email address"
    else -> ""
}
