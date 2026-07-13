package app.farmsy.android

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.farmsy.android.core.FarmPin
import app.farmsy.android.features.auth.AuthSheet
import app.farmsy.android.features.detail.FarmDetailScreen
import app.farmsy.android.features.main.MainScreen
import app.farmsy.android.features.onboarding.OnboardingScreen
import app.farmsy.android.features.splash.SplashScreen
import app.farmsy.android.ui.theme.FarmsyColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween

/// Splash → onboarding (first run) → main app. Browsing is open to everyone;
/// logging in is asked for lazily via LocalRequestAuth when a gated action is
/// tapped. Mirrors iOS RootView.swift + MainView's openFarm gate.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNav() {
    val context = LocalContext.current
    val session = LocalSession.current
    val prefs = remember { context.getSharedPreferences("farmsy", Context.MODE_PRIVATE) }

    var splashDone by remember { mutableStateOf(false) }
    var didFinishOnboarding by remember {
        mutableStateOf(prefs.getBoolean("didFinishOnboarding", false))
    }
    var showAuth by remember { mutableStateOf(false) }
    var openPin by remember { mutableStateOf<FarmPin?>(null) }

    val isBootstrapped by session.isBootstrapped.collectAsState()
    val currentSession by session.session.collectAsState()
    val isAuthenticated = currentSession != null

    // Load favorites when the signed-in user changes (iOS RootView.onChange).
    val favorites = LocalFavorites.current
    LaunchedEffect(currentSession?.user?.id) {
        val userId = currentSession?.user?.id
        if (userId != null) favorites.load(userId) else favorites.clear()
    }

    CompositionLocalProvider(LocalRequestAuth provides { showAuth = true }) {
        AnimatedContent(
            targetState = when {
                !splashDone || !isBootstrapped -> Screen.Splash
                isAuthenticated || didFinishOnboarding -> Screen.Main
                else -> Screen.Onboarding
            },
            transitionSpec = {
                fadeIn(tween(320)) togetherWith fadeOut(tween(220))
            },
            modifier = Modifier.fillMaxSize().background(FarmsyColors.cream),
            label = "root"
        ) { screen ->
            when (screen) {
                Screen.Splash -> SplashScreen { splashDone = true }
                Screen.Onboarding -> OnboardingScreen {
                    prefs.edit().putBoolean("didFinishOnboarding", true).apply()
                    didFinishOnboarding = true
                }
                Screen.Main -> MainScreen(
                    onOpenFarm = { pin ->
                        // Guests can browse freely; opening details asks for an
                        // account first. The detail screen's own subscription
                        // gate takes over after login.
                        if (session.isAuthenticated) openPin = pin else showAuth = true
                    }
                )
            }
        }

        // Farm detail as an overlay "push" (simple + state-preserving).
        //
        // It used to be a bare `openPin?.let { … }`, which snapped the whole screen
        // in and out with no animation at all — the detail just *appeared*. Slide it
        // in from the trailing edge like the iOS navigation push. `lastPin` outlives
        // `openPin` so the screen still has something to draw on the way out.
        var lastPin by remember { mutableStateOf<FarmPin?>(null) }
        LaunchedEffect(openPin) { openPin?.let { lastPin = it } }
        AnimatedVisibility(
            visible = openPin != null,
            enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            lastPin?.let { pin ->
                FarmDetailScreen(pin = pin, onBack = { openPin = null })
            }
        }

        if (showAuth) {
            ModalBottomSheet(
                onDismissRequest = { showAuth = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = FarmsyColors.cream,
            ) {
                AuthSheet(onDone = { showAuth = false })
            }
        }
    }
}

private enum class Screen { Splash, Onboarding, Main }
