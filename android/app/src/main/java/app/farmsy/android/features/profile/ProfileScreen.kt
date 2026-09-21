package app.farmsy.android.features.profile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.farmsy.android.core.NotificationRowState
import app.farmsy.android.core.PushRegistrar
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import app.farmsy.android.core.SearchRadius
import app.farmsy.android.features.survey.SurveyMode
import app.farmsy.android.features.survey.SurveyScreen
import app.farmsy.android.ui.theme.Badge
import app.farmsy.android.ui.theme.IconButton
import app.farmsy.android.ui.theme.ListRow
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.RowGroup
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.rememberTapHaptic
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.ui
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.BuildConfig
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch
import app.farmsy.android.ui.theme.FitText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.CheckCircle
import app.farmsy.android.core.LanguageStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.farmsy.android.core.AvatarStore
import app.farmsy.android.core.BadgeKind
import app.farmsy.android.core.Contributions
import coil.compose.AsyncImage

/// Profile — opened from the Home header (iOS ProfileScreen). Identity,
/// membership, what Farmsy knows about you (radius, alerts), preferences, legal,
/// and the way out.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onClose: () -> Unit, onOpenPlus: () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val requestAuth = LocalRequestAuth.current
    val scope = rememberCoroutineScope()
    val tap = rememberTapHaptic()

    val currentSession by session.session.collectAsState()
    val profile by session.profile.collectAsState()
    val isAuthenticated = currentSession != null
    val radiusKm by SearchRadius.km.collectAsState()

    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteInfo by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAccessibility by remember { mutableStateOf(false) }
    var badgeSheet by remember { mutableStateOf<BadgeKind?>(null) }
    val avatarUrl by AvatarStore.url.collectAsState()
    val avatarBusy by AvatarStore.busy.collectAsState()
    val stats by Contributions.stats.collectAsState()
    val earned by Contributions.earned.collectAsState()
    val userId = currentSession?.user?.id
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val uid = userId ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            if (AvatarStore.upload(context, uri, uid)) tap()
        }
    }
    var showSurvey by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    // After the server confirms the erase we replace the whole screen with a plain
    // "account deleted" state. Non-null once deleted; the value is the rail that
    // charged them ("google"/"apple"/"stripe"), or null when there's nothing left
    // for them to cancel.
    var deletedState by remember { mutableStateOf<DeletedState?>(null) }

    // Notifications row. `permanentlyDenied` only turns true once a request this
    // session came back denied with `shouldShowRequestPermissionRationale` false —
    // that's the one signal that distinguishes "just said no" from "chose don't
    // ask again". Before that we have no record, so a not-granted state defaults
    // to "Turn on" rather than sending someone straight to Settings.
    // ponytail: this resets each time Profile is recomposed from scratch (no
    // SharedPreferences record of "was asked before"), so a permission denied
    // permanently in an earlier session can briefly show "Turn on" again until
    // the launcher round-trips once more. Persist the flag if that proves annoying.
    var permanentlyDenied by remember { mutableStateOf(false) }
    var notificationState by remember { mutableStateOf(NotificationRowState.TURN_ON) }
    fun refreshNotificationState() {
        val canAsk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !permanentlyDenied
        notificationState = PushRegistrar.notificationRowState(
            granted = PushRegistrar.notificationsAllowed(context),
            enabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            canAsk = canAsk,
        )
    }
    // Reuses PushRegistrar.sync — the same registration call onboarding's notify
    // step makes after its own grant — so there is one path to a posted token.
    val notifPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch { PushRegistrar.sync(currentSession?.user?.id, session.accessToken()) }
        } else {
            val activity = context as? Activity
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        }
        refreshNotificationState()
    }
    fun onNotificationRowTap() {
        when (notificationState) {
            // Only reachable on API 33+ — see notificationRowState.
            NotificationRowState.TURN_ON -> notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            NotificationRowState.OPEN_SETTINGS -> context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
            NotificationRowState.ON -> {}
        }
    }
    LaunchedEffect(Unit) { refreshNotificationState() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshNotificationState() }

    LaunchedEffect(Unit) { session.refreshProfile() }
    LaunchedEffect(userId) {
        val uid = userId ?: return@LaunchedEffect
        val token = currentSession?.accessToken ?: return@LaunchedEffect
        AvatarStore.load(uid)
        Contributions.refreshMine(token)
    }

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // Terminal state: the account is gone and the session is signed out. There's
    // nothing left to show, so the whole screen becomes a confirmation. The user is
    // now a guest — the rest of the app treats them as one the moment they leave.
    deletedState?.let { state ->
        AccountDeletedScreen(state = state)
        return
    }

    // Key the badge off *access*, not the raw status word. A "canceled" status whose
    // period has already lapsed still reads "canceled" in the DB, but the user has no
    // access — labelling them Canceled while the section below says "no membership"
    // is a contradiction on one screen. hasFullAccess is the truth both halves share.
    val access = profile?.hasFullAccess == true
    val (badgeRes, badgeColor) = when {
        // A lapsed sub is not the same as never having subscribed — flag it in red.
        !access && profile?.subscriptionStatus in setOf("canceled", "expired") -> R.string.expired to FarmsyColors.warnRed
        !access -> R.string.free to FarmsyColors.inkMuted
        profile?.subscriptionStatus == "trialing" -> R.string.trial to FarmsyColors.farmGreen
        profile?.subscriptionStatus == "canceled" -> R.string.canceled to FarmsyColors.inkMuted
        else -> R.string.member to FarmsyColors.farmGreen
    }
    val initials = session.displayName.split(" ").mapNotNull { it.firstOrNull() }.take(2)
        .joinToString("").uppercase().ifEmpty { "?" }

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
        Box(Modifier.padding(top = Space.s2)) {
            ScreenHeader(stringResource(R.string.profile), compact = true) {
                IconButton(Icons.Filled.Close, stringResource(R.string.close), small = true, onClick = onClose)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.s4).padding(top = Space.s4, bottom = Space.s8),
            verticalArrangement = Arrangement.spacedBy(Space.s8),
        ) {
            // MARK: Identity
            if (isAuthenticated) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    val changePhoto = stringResource(R.string.change_photo)
                    Box(
                        Modifier.size(96.dp).semantics { contentDescription = changePhoto }
                            .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Two dashed rings around the picture (iOS StrokeStyle dash).
                        Canvas(Modifier.size(96.dp)) {
                            val w = 2.dp.toPx()
                            drawCircle(FarmsyColors.vivid.copy(alpha = 0.9f), radius = (96.dp.toPx() - w) / 2,
                                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
                            drawCircle(FarmsyColors.farmGreen.copy(alpha = 0.35f), radius = (84.dp.toPx() - w) / 2,
                                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))))
                        }
                        Box(Modifier.size(72.dp).clip(CircleShape).background(FarmsyColors.surface), contentAlignment = Alignment.Center) {
                            Text(initials, style = ui(26.sp, FontWeight.Bold), color = FarmsyColors.farmGreen)
                            avatarUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp)) }
                        }
                        // The camera badge says "this is tappable" without a label.
                        Box(
                            Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(32.dp)
                                .background(FarmsyColors.cream, CircleShape).padding(2.dp).background(FarmsyColors.ink, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarBusy) CircularProgressIndicator(Modifier.size(14.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Filled.CameraAlt, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Text(session.displayName, style = role(TextRole.HEADING), color = FarmsyColors.ink)
                        Badge(stringResource(badgeRes), fill = badgeColor)
                    }
                    if (session.email.isNotEmpty()) Text(session.email, style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    Icon(Icons.Outlined.AccountCircle, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(56.dp))
                    Text(stringResource(R.string.you_re_browsing_as_a_guest), style = role(TextRole.HEADING), color = FarmsyColors.ink, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.guest_sign_in_sub), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center)
                    PillButton(stringResource(R.string.sign_in), PillVariant.PRIMARY, PillSize.MEDIUM) { onClose(); requestAuth() }
                }
            }

            // Membership. Only for signed-in users — a guest has no subscription to
            // manage, and the paywall is where they'd start one.
            if (isAuthenticated) {
                MembershipSection(profile = profile)
                ContributionsCard(stats = stats, earned = { kind -> earned.any { it.badge == kind.wire } }) { tap(); badgeSheet = it }
            }

            RowGroup(stringResource(R.string.farmsy)) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    ListRow(Icons.Outlined.LocationOn, stringResource(R.string.search_radius), value = "${radiusKm.toInt()} km", chevron = false) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = FarmsyColors.surface) {
                        SearchRadius.choices.forEach { km ->
                            DropdownMenuItem(
                                text = { Text("${km.toInt()} km", style = ui(16.sp), color = FarmsyColors.ink) },
                                onClick = { SearchRadius.set(context, km); menu = false },
                            )
                        }
                    }
                }
                ListRow(
                    Icons.Outlined.NotificationsActive, stringResource(R.string.notifications),
                    value = stringResource(
                        when (notificationState) {
                            NotificationRowState.TURN_ON -> R.string.turn_on
                            NotificationRowState.OPEN_SETTINGS -> R.string.open_settings
                            NotificationRowState.ON -> R.string.on
                        }
                    ),
                    chevron = false,
                ) { onNotificationRowTap() }
                ListRow(
                    Icons.Outlined.Notifications, stringResource(R.string.product_alerts),
                    subtitle = if (session.hasFullAccess) null else stringResource(R.string.farmsy_plus),
                ) {
                    if (session.hasFullAccess) open("https://www.farmsy.app/alerts") else onOpenPlus()
                }
            }

            // Language — lets a user on a differently-set phone run the app in one of
            // the languages Farmsy is translated into (or back to the system default).
            val currentLang = LanguageStore.current(context)
            RowGroup(stringResource(R.string.preferences)) {
                ListRow(
                    Icons.Outlined.Language, stringResource(R.string.language),
                    value = if (currentLang == LanguageStore.Lang.SYSTEM) stringResource(R.string.system_default) else currentLang.displayName,
                ) { showLanguage = true }
                ListRow(Icons.Outlined.DirectionsWalk, stringResource(R.string.accessibility)) { showAccessibility = true }
            }

            RowGroup(stringResource(R.string.community)) {
                ListRow(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.give_feedback)) { showSurvey = true }
                // Refer friends, for signed-in users only — a guest has no code to
                // share. Opens the web invite page rather than duplicating the whole
                // referral dashboard natively.
                if (isAuthenticated) {
                    ListRow(Icons.Outlined.CardGiftcard, stringResource(R.string.refer_friends)) { open("https://www.farmsy.app/invite") }
                }
                ListRow(Icons.Outlined.Email, stringResource(R.string.contact_us)) { open("https://www.farmsy.app/messages") }
            }

            RowGroup(stringResource(R.string.legal)) {
                ListRow(Icons.Outlined.PrivacyTip, stringResource(R.string.privacy_policy)) { open("https://farmsy.app/privacy") }
                ListRow(Icons.Outlined.Article, stringResource(R.string.terms_of_service)) { open("https://farmsy.app/terms") }
            }

            if (isAuthenticated) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    PillButton(stringResource(R.string.sign_out), PillVariant.GHOST, PillSize.MEDIUM, block = true) { showSignOutConfirm = true }
                    Text(
                        stringResource(R.string.delete_account), style = ui(15.sp, FontWeight.SemiBold), color = FarmsyColors.critical,
                        modifier = Modifier.clickable { tap(); showDeleteInfo = true }.padding(Space.s2),
                    )
                }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Farmsy for Android ${BuildConfig.VERSION_NAME}", style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint)
                // OpenStreetMap attribution (P0-5): the farm records carry OSM ids,
                // so the ODbL credit has to be reachable in the app.
                Text(stringResource(R.string.osm_attribution), style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint)
            }
        }
    }

    if (showAccessibility) AccessibilitySheet(onDismiss = { showAccessibility = false })
    badgeSheet?.let { kind -> BadgeSheet(kind, earned = earned.any { it.badge == kind.wire }, onDismiss = { badgeSheet = null }) }

    // Feedback opens the survey in feedback mode, as a modal over the profile.
    if (showSurvey) {
        ModalBottomSheet(
            onDismissRequest = { showSurvey = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FarmsyColors.cream,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()) {
                SurveyScreen(mode = SurveyMode.FEEDBACK, onClose = { showSurvey = false })
            }
        }
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    LanguageStore.Lang.entries.forEach { lang ->
                        val selected = LanguageStore.current(context) == lang
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                LanguageStore.set(context, lang)
                                showLanguage = false
                                // recreate() re-runs attachBaseContext with the new locale.
                                (context as? android.app.Activity)?.recreate()
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(lang.flag, style = geist(18.sp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (lang == LanguageStore.Lang.SYSTEM) stringResource(R.string.system_default) else lang.displayName,
                                style = geist(16.sp, if (selected) FontWeight.Bold else FontWeight.Medium),
                                color = FarmsyColors.ink, modifier = Modifier.weight(1f),
                            )
                            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguage = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(stringResource(R.string.sign_out_of_farmsy)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    scope.launch { session.signOut(); onClose() }
                }) { Text(stringResource(R.string.sign_out), color = FarmsyColors.warnRed) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteInfo) {
        AlertDialog(
            // Block dismissal mid-request — a half-cancelled delete is a bad state to
            // leave someone guessing about.
            onDismissRequest = { if (!isDeleting) { showDeleteInfo = false; deleteFailed = false } },
            title = { Text(stringResource(R.string.delete_account)) },
            text = {
                Column {
                    Text(stringResource(R.string.deleting_your_account_removes_your_profile_favourites_and_su))
                    if (deleteFailed) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.delete_account_failed),
                            style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed
                        )
                    }
                }
            },
            confirmButton = {
                if (isDeleting) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp).padding(end = 8.dp),
                        color = FarmsyColors.warnRed, strokeWidth = 2.dp
                    )
                } else {
                    TextButton(onClick = {
                        deleteFailed = false
                        isDeleting = true
                        scope.launch {
                            val result = session.deleteAccount()
                            isDeleting = false
                            if (result.ok) {
                                showDeleteInfo = false
                                deletedState = DeletedState(
                                    remindStore = result.storeSubscriptionReminder,
                                    source = result.subscriptionSource,
                                )
                            } else {
                                deleteFailed = true
                            }
                        }
                    }) {
                        Text(stringResource(R.string.delete_account), color = FarmsyColors.warnRed)
                    }
                }
            },
            dismissButton = {
                if (!isDeleting) {
                    TextButton(onClick = { showDeleteInfo = false; deleteFailed = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

private data class DeletedState(val remindStore: Boolean, val source: String?)

/// Three numbers and the eight circles. Locked ones are tappable too: the
/// rule is the invitation.
@Composable
private fun ContributionsCard(
    stats: app.farmsy.android.core.ContributionStats?,
    earned: (BadgeKind) -> Boolean,
    onBadge: (BadgeKind) -> Unit,
) {
    Column(Modifier.fillMaxWidth().card(), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        Text(stringResource(R.string.your_contributions), style = role(TextRole.HEADING), color = FarmsyColors.ink)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Stat(stats?.reports?.toString() ?: "—", stringResource(R.string.stat_reports), Icons.Filled.Flag, Modifier.weight(1f))
            VerticalDivider(Modifier.height(36.dp), color = FarmsyColors.hairline)
            Stat(stats?.farms?.toString() ?: "—", stringResource(R.string.stat_farms), Icons.Filled.Map, Modifier.weight(1f))
            VerticalDivider(Modifier.height(36.dp), color = FarmsyColors.hairline)
            Stat(stats?.badges?.toString() ?: "—", stringResource(R.string.stat_badges), Icons.Filled.EmojiEvents, Modifier.weight(1f))
        }
        HorizontalDivider(color = FarmsyColors.hairline)
        // Eight badges in four columns: two rows of a fixed height, so the grid
        // lays out inside the scrolling column without its own scrolling.
        LazyVerticalGrid(
            GridCells.Fixed(4), Modifier.fillMaxWidth().height(200.dp), userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            items(BadgeKind.entries) { kind ->
                val on = earned(kind)
                Column(
                    Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onBadge(kind) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(44.dp).background(if (on) FarmsyColors.vivid else FarmsyColors.creamFill, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(kind.icon, null, tint = if (on) FarmsyColors.ink else FarmsyColors.inkFaint, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        stringResource(kind.titleRes), style = role(TextRole.CAPTION), color = if (on) FarmsyColors.ink else FarmsyColors.inkFaint,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(16.dp))
        Text(value, style = role(TextRole.HEADING), color = FarmsyColors.ink)
        Text(label, style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
    }
}

/// Shown once the server confirms the account is erased. The person is signed out
/// and can't do anything here but acknowledge — so it's a dead-simple confirmation,
/// plus the store-cancellation nudge when they still have a live sub the app can't
/// touch on their behalf.
@Composable
private fun AccountDeletedScreen(state: DeletedState) {
    Column(
        Modifier.fillMaxSize().background(FarmsyColors.cream).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CheckCircle, null,
            tint = FarmsyColors.farmGreen, modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.account_deleted_title),
            style = ui(24.sp, FontWeight.Bold), color = FarmsyColors.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.account_deleted_body),
            style = geist(15.sp), color = FarmsyColors.inkMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (state.remindStore) {
            // Name the rail that actually charged them, not the phone they happen to
            // be holding — an Android user who subscribed on iOS has to cancel in the
            // App Store, and sending them to Play would leave the billing running.
            val where = when (state.source) {
                "apple" -> stringResource(R.string.store_apple)
                "stripe" -> stringResource(R.string.store_web)
                else -> stringResource(R.string.store_google)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.account_deleted_store_reminder_arg, where),
                style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(
                    FarmsyColors.creamCard, RoundedCornerShape(14.dp)
                ).padding(16.dp)
            )
        }
    }
}

/// Membership status, and where to go to change it.
///
/// Two rules shape this. **Lifetime is the top of the ladder** — someone who paid
/// once, forever, must never see an upsell, because there is nothing above it and
/// dangling an offer would be dishonest. And **billing lives with whoever took the
/// money**: Play, Apple and Stripe are three separate contracts, and neither store
/// lets us cancel on a user's behalf. So "manage" has to send them to the rail that
/// actually charged them — pointing a web subscriber at Google Play, where they'd
/// find nothing, reads as hiding the cancel button.
@Composable
private fun MembershipSection(profile: app.farmsy.android.core.Profile?) {
    val context = LocalContext.current
    val session = LocalSession.current

    val plan = profile?.subscriptionPlan
    val status = profile?.subscriptionStatus
    val hasAccess = profile?.hasFullAccess == true
    val isLifetime = plan == "lifetime" && hasAccess
    val isTrialing = status == "trialing"
    // Cancelled but still inside the period they already paid for — they keep access
    // to the end, and deserve to be told when that is rather than sold a renewal.
    val isCanceled = status == "canceled"

    fun openBilling() {
        val url = when (profile?.subscriptionSource) {
            "apple" -> "https://apps.apple.com/account/subscriptions"
            "stripe" -> "https://www.farmsy.app/account/subscription"
            else -> "https://play.google.com/store/account/subscriptions"
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // Had a membership that has now lapsed (canceled/expired past its end date).
    // "You don't have a membership yet" is wrong for them — they had one, it ended.
    val isExpired = !hasAccess && (status == "canceled" || status == "expired")

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.membership),
            style = ui(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
            modifier = Modifier.padding(start = 4.dp)
        )
        Column(Modifier.fillMaxWidth().card()) {
            when {
                // Top of the ladder. Nothing to sell, and nothing to cancel — but say
                // so plainly: an empty section reads as broken, a clear statement
                // reads as deliberate.
                isLifetime -> {
                    FitText(
                        stringResource(R.string.you_have_lifetime),
                        style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.lifetime_never_expires),
                        style = geist(14.sp), color = FarmsyColors.inkMuted
                    )
                }

                hasAccess -> {
                    val ends = profile?.subscriptionEndDate?.let { formatDate(it) }
                    FitText(
                        stringResource(
                            when {
                                isCanceled -> R.string.membership_ending
                                isTrialing -> R.string.trial_active
                                else -> R.string.youre_on_yearly
                            }
                        ),
                        style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink
                    )
                    Spacer(Modifier.height(4.dp))
                    // Say the true thing about what happens next.
                    //
                    // A cancelled plan was being told it "renews yearly" — flatly
                    // false, and it buried the one fact the person actually needs:
                    // the day their access stops. A trial was told nothing about the
                    // charge that's coming. Both are the same failure: describing the
                    // happy path to someone who isn't on it.
                    Text(
                        when {
                            isCanceled && ends != null -> stringResource(R.string.access_until_arg, ends)
                            isCanceled -> stringResource(R.string.wont_renew)
                            isTrialing && ends != null -> stringResource(R.string.trial_converts_on_arg, ends)
                            isTrialing -> stringResource(R.string.trial_then_charged)
                            else -> stringResource(R.string.yearly_renews)
                        },
                        style = geist(14.sp), color = FarmsyColors.inkMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    // No "Upgrade to Lifetime" here. The server now refuses to sell a
                    // second subscription to someone who already has one (it used to
                    // happily charge them twice), so an upgrade button would only
                    // produce a 409 on a paying customer's screen. A real upgrade has
                    // to cancel the running subscription first — that's a feature, not
                    // a button.
                    Text(
                        stringResource(R.string.manage_subscription),
                        style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen,
                        modifier = Modifier.clickable { openBilling() }
                    )
                }

                isExpired -> {
                    FitText(
                        stringResource(R.string.membership_expired),
                        style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.renew_to_unlock),
                        style = geist(14.sp), color = FarmsyColors.inkMuted
                    )
                }

                // No membership. Point at the paywall rather than growing a second
                // purchase surface here.
                else -> {
                    FitText(
                        stringResource(R.string.no_membership_yet),
                        style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink
                    )
                    Spacer(Modifier.height(4.dp))
                    // Farm details are free now (P0-2). Reuse the Plus paywall's own
                    // planning/alerts line rather than "unlock every farm".
                    Text(
                        stringResource(R.string.pro_unlock_sub),
                        style = geist(14.sp), color = FarmsyColors.inkMuted
                    )
                }
            }
        }
    }
}

/// "2026-07-17T…" -> "17 July 2026", in the user's locale.
private fun formatDate(iso: String): String? = runCatching {
    val d = java.time.OffsetDateTime.parse(iso)
    d.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))
}.getOrNull()
