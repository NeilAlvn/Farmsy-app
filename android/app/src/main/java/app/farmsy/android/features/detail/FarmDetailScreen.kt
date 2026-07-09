package app.farmsy.android.features.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.farmsy.android.core.FarmPin
import app.farmsy.android.ui.theme.FarmsyColors

// Phase 6 stub — full detail + locked/members-only state comes next.
@Composable
fun FarmDetailScreen(pin: FarmPin, onBack: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(FarmsyColors.cream),
        contentAlignment = Alignment.Center
    ) {
        Text(pin.name)
    }
}
