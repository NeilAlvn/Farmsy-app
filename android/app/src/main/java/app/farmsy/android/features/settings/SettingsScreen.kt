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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PanTool
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

    LaunchedEffect(Unit) { session.refreshProfile() }

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
                    val plan = profile?.subscriptionPlan
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
                val (badgeRes, badgeColor) = when (profile?.subscriptionStatus) {
                    "active" -> R.string.member to FarmsyColors.farmGreen
                    "trialing" -> R.string.trial to FarmsyColors.farmGreen
                    "canceled" -> R.string.canceled to FarmsyColors.inkMuted
                    else -> R.string.free to FarmsyColors.inkMuted
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
            HorizontalDivider(Modifier.padding(start = 62.dp))
            SettingsRow(Icons.Filled.Email, Color(0xFF38BDF8), stringResource(R.string.contact_us)) {
                open("mailto:hello@farmsy.app")
            }
        }

        Spacer(Modifier.height(14.dp))

        // Legal
        SettingsCard {
            SettingsRow(Icons.Filled.PanTool, Color(0xFF8B5CF6), stringResource(R.string.privacy_policy)) {
                open("https://farmsy.app/privacy")
            }
            HorizontalDivider(Modifier.padding(start = 62.dp))
            SettingsRow(Icons.Filled.Description, Color(0xFF64748B), stringResource(R.string.terms_of_service)) {
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
            onDismissRequest = { showDeleteInfo = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = {
                Text(stringResource(R.string.deleting_your_account_removes_your_profile_favourites_and_su))
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteInfo = false
                    open("https://www.farmsy.app/profile")
                }) { Text(stringResource(R.string.open_my_account_page)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteInfo = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
    }
}

/// Membership status and what (if anything) is left to sell.
///
/// The rule that shapes this: **lifetime is the top of the ladder.** Someone who
/// has paid once, forever, must never see an upsell — there is nothing above it,
/// and dangling an offer at them would be dishonest. Yearly members get exactly
/// one thing to consider (lifetime); everyone else gets the plans.
///
/// Purchases themselves live on the paywall, which is reached by opening a locked
/// farm; this section links there rather than duplicating a second buy flow.
@Composable
private fun MembershipSection(profile: app.farmsy.android.core.Profile?) {
    val context = LocalContext.current
    val purchases = app.farmsy.android.LocalPurchases.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()

    val lifetimePrice = purchases.lifetimePrice
    val lifetimePkg by purchases.lifetime.collectAsState()
    val currentSession by session.session.collectAsState()
    val isPurchasing by purchases.isPurchasing.collectAsState()
    val userId = currentSession?.user?.id

    LaunchedEffect(Unit) { purchases.loadOffering() }

    val plan = profile?.subscriptionPlan
    val status = profile?.subscriptionStatus
    val hasAccess = profile?.hasFullAccess == true
    val isLifetime = plan == "lifetime" && hasAccess

    Text(
        stringResource(R.string.membership),
        style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )

    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            when {
                // Top of the ladder. Nothing to sell, nothing to manage.
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

                // Paying yearly: tell them where to manage it, and offer the one
                // genuine upgrade that exists.
                hasAccess -> {
                    FitText(
                        stringResource(
                            if (status == "trialing") R.string.trial_active else R.string.youre_on_yearly
                        ),
                        style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.yearly_renews),
                        style = geist(14.sp), color = FarmsyColors.inkMuted
                    )
                    if (lifetimePrice != null) {
                        Spacer(Modifier.height(14.dp))
                        if (isPurchasing) {
                            CircularProgressIndicator(color = FarmsyColors.farmGreen)
                        } else {
                            PrimaryButton("${stringResource(R.string.upgrade_to_lifetime)} · $lifetimePrice") {
                                val activity = context as? android.app.Activity ?: return@PrimaryButton
                                scope.launch {
                                    if (purchases.purchase(activity, lifetimePkg, userId)) {
                                        // Poll: the grant lands via the webhook a
                                        // moment after the purchase returns.
                                        repeat(10) {
                                            session.refreshProfile()
                                            if (session.hasFullAccess) return@repeat
                                            kotlinx.coroutines.delay(1500)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.manage_subscription),
                        style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/account/subscriptions")
                                )
                            )
                        }
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
