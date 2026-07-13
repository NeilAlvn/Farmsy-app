package app.farmsy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.farmsy.android.ui.theme.FarmsyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FarmsyApp
        setContent {
            CompositionLocalProvider(
                LocalSession provides app.session,
                LocalFarms provides app.farms,
                LocalFavorites provides app.favorites,
                LocalLocationHelper provides app.locationHelper,
                LocalPurchases provides app.purchases,
            ) {
                FarmsyTheme {
                    RootNav()
                }
            }
        }
    }
}
