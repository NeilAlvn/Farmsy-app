package app.farmsy.android.features.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.BuildConfig
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.ui.theme.CardShape
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch
import java.util.Locale
import app.farmsy.android.ui.theme.FitText
import androidx.compose.material3.CircularProgressIndicator
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.PlanCard
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import app.farmsy.android.core.LanguageStore

/// Settings — mirrors iOS SettingsSheet (account/guest card, rows, legal,
/// sign out + delete account, version footer).
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val session = LocalSession.current
    val requestAuth = LocalRequestAuth.current
    val scope = rememberCoroutineScope()

    val currentSession by session.session.collectAsState()
    val profile by session.profile.collectAsState()
    val isAuthenticated = currentSession != null

    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteInfo by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    // After the server confirms the erase we replace the whole screen with a plain
    // "account deleted" state. Non-null once deleted; the value is the rail that
    // charged them ("google"/"apple"/"stripe"), or null when there's nothing left
    // for them to cancel.
    var deletedState by remember { mutableStateOf<DeletedState?>(null) }

    LaunchedEffect(Unit) { session.refreshProfile() }

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

    Column(
        Modifier
            .fillMaxSize()
            .background(FarmsyColors.cream)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            stringResource(R.string.settings),
            style = display(26.sp), color = FarmsyColors.ink,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 18.dp)
        )

        // Account / guest card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(FarmsyColors.creamCard, CardShape).padding(16.dp)
        ) {
            Image(painterResource(R.drawable.farmsy_logo), null, Modifier.height(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // These are one-line identity labels sitting next to a fixed avatar
                // and badge — if they wrap, the whole card grows and the badge drifts.
                // FitText shrinks them to fit instead (see Components.FitText).
                if (isAuthenticated) {
                    FitText(
                        session.email.ifEmpty { stringResource(R.string.signed_in) },
                        style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink
                    )
                    // Only name a plan while it actually grants access. After a lapsed
                    // cancellation the DB still carries subscription_plan='yearly', but
                    // the person isn't on a yearly plan any more — showing "Yearly plan"
                    // there is the same stale-state lie as the badge.
                    val plan = profile?.subscriptionPlan?.takeIf { profile?.hasFullAccess == true }
                    FitText(
                        if (plan != null)
                            stringResource(
                                R.string.arg_plan,
                                plan.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                            )
                        else stringResource(R.string.farmsy_account),
                        style = geist(13.sp), color = FarmsyColors.inkMuted
                    )
                } else {
                    FitText(
                        stringResource(R.string.you_re_browsing_as_a_guest),
                        style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink
                    )
                    FitText(
                        stringResource(R.string.sign_in_to_save_farms_and_see_details),
                        style = geist(13.sp), color = FarmsyColors.inkMuted
                    )
                }
            }
            if (isAuthenticated) {
                // Key the badge off *access*, not the raw status word. A "canceled"
                // status whose period has already lapsed still reads as "canceled" in
                // the DB — but the user has no access, so labelling them Canceled while
                // the section below correctly says "no membership" is a contradiction
                // on one screen. hasFullAccess is the same truth both halves should use.
                val access = profile?.hasFullAccess == true
                val (badgeRes, badgeColor) = when {
                    !access -> R.string.free to FarmsyColors.inkMuted
                    profile?.subscriptionStatus == "trialing" -> R.string.trial to FarmsyColors.farmGreen
                    profile?.subscriptionStatus == "canceled" -> R.string.canceled to FarmsyColors.inkMuted
                    else -> R.string.member to FarmsyColors.farmGreen
                }
                Text(
                    stringResource(badgeRes),
                    style = geist(12.sp, FontWeight.Bold), color = Color.White,
                    modifier = Modifier.background(badgeColor, CircleShape)
                        .padding(vertical = 5.dp, horizontal = 10.dp)
                )
            } else {
                Text(
                    stringResource(R.string.sign_in),
                    style = geist(13.sp, FontWeight.Bold), color = Color.White,
                    modifier = Modifier
                        .background(FarmsyColors.farmGreen, CircleShape)
                        .clickable { requestAuth() }
                        .padding(vertical = 7.dp, horizontal = 12.dp)
                )
            }
        }

        // Membership. Only for signed-in users — a guest has no subscription to
        // manage, and the paywall is where they'd start one.
        if (isAuthenticated) {
            Spacer(Modifier.height(14.dp))
            MembershipSection(profile = profile)
        }

        Spacer(Modifier.height(14.dp))

        // General rows
        SettingsCard {
            SettingsRow(Icons.Filled.Notifications, Color(0xFFF5B301), stringResource(R.string.notifications)) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
            // Refer friends, for signed-in users only — a guest has no code to
            // share. Opens the web invite page rather than duplicating the whole
            // referral dashboard natively; the signup-side code capture (the part
            // that actually earns referrals) stays in the app.
            if (isAuthenticated) {
                HorizontalDivider(Modifier.padding(start = 62.dp))
                SettingsRow(Icons.Filled.CardGiftcard, Color(0xFFEC4899), stringResource(R.string.refer_friends)) {
                    open("https://www.farmsy.app/invite")
                }
            }
            HorizontalDivider(Modifier.padding(start = 62.dp))
            SettingsRow(Icons.Filled.Email, Color(0xFF38BDF8), stringResource(R.string.contact_us)) {
                open("https://www.farmsy.app/messages")
            }
        }

        Spacer(Modifier.height(14.dp))

        // Language — lets a user on a differently-set phone run the app in one of
        // the languages Farmsy is translated into (or back to the system default).
        val currentLang = LanguageStore.current(context)
        SettingsCard {
            SettingsRow(
                Icons.Filled.Language, Color(0xFF3F5E3A), stringResource(R.string.language),
                trailing = if (currentLang == LanguageStore.Lang.SYSTEM)
                    stringResource(R.string.system_default) else currentLang.displayName,
            ) { showLanguage = true }
        }

        Spacer(Modifier.height(14.dp))

        // Legal
        SettingsCard {
            SettingsRow(Icons.Filled.PrivacyTip, Color(0xFF8B5CF6), stringResource(R.string.privacy_policy)) {
                open("https://farmsy.app/privacy")
            }
            HorizontalDivider(Modifier.padding(start = 62.dp))
            SettingsRow(Icons.Filled.Article, Color(0xFF64748B), stringResource(R.string.terms_of_service)) {
                open("https://farmsy.app/terms")
            }
        }

        if (isAuthenticated) {
            Spacer(Modifier.height(14.dp))
            SettingsCard {
                SettingsRow(
                    Icons.AutoMirrored.Filled.Logout, FarmsyColors.farmGreen,
                    stringResource(R.string.sign_out)
                ) { showSignOutConfirm = true }
                HorizontalDivider(Modifier.padding(start = 62.dp))
                SettingsRow(
                    Icons.Filled.Delete, FarmsyColors.warnRed,
                    stringResource(R.string.delete_account), tint = FarmsyColors.warnRed
                ) { showDeleteInfo = true }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Farmsy for Android ${BuildConfig.VERSION_NAME}",
            style = geist(12.sp), color = FarmsyColors.inkMuted.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
        )
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
                    scope.launch { session.signOut() }
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
            style = display(24.sp), color = FarmsyColors.ink,
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

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(FarmsyColors.creamCard, RoundedCornerShape(18.dp)).padding(4.dp)
    ) { content() }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    tint: Color = FarmsyColors.ink,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp)
    ) {
        Box(
            Modifier.size(34.dp).background(iconTint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = geist(16.sp, FontWeight.Medium), color = tint)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, style = geist(14.sp), color = FarmsyColors.inkMuted)
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

    Text(
        stringResource(R.string.membership),
        style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )

    SettingsCard {
        Column(Modifier.padding(16.dp)) {
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

                // No membership. Point at the paywall rather than growing a second
                // purchase surface here.
                else -> {
                    FitText(
                        stringResource(R.string.no_membership_yet),
                        style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.unlock_all_farms),
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
