package app.farmsy.android.features.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.farmsy.android.core.FarmPin

// Phase 5 stub — replaced by the full-bleed Google Map with floating controls
// once the Maps API key is wired.
@Composable
fun MapScreen(onOpenFarm: (FarmPin) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Map — coming in phase 5")
    }
}
