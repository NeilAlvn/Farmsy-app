package app.farmsy.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.ui.theme.FarmsyColors

/// A product photograph from res/drawable-nodpi, by the slug the server names —
/// the Android twin of iOS ProductImage.swift.
///
/// The files are the 512px WebPs from farmsy-web/design/product-images as
/// product_<slug>.webp (resource names cannot hold a hyphen), so a new image is
/// a file copy and nothing else. Falls back to the emoji the tile used to show,
/// so a slug the app does not ship (a product added server-side before the
/// next release) still reads as something.
@Composable
fun ProductImage(slug: String?, fallback: String, size: Dp, corner: Dp = 12.dp) {
    val context = LocalContext.current
    val id = slug?.let { ProductImageIds.resolve(context, it) } ?: 0
    val shape = RoundedCornerShape(corner)
    if (id != 0) {
        Image(painterResource(id), null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(shape).background(FarmsyColors.creamFill), contentAlignment = Alignment.Center) {
            Text(fallback, fontSize = (size.value * 0.5f).sp)
        }
    }
}

/// Resource ids by slug, looked up once: getIdentifier is a reflective string
/// search and the same eggs appear on Home, Shopping and Discover in one scroll.
object ProductImageIds {
    private val cache = HashMap<String, Int>()

    fun resolve(context: android.content.Context, slug: String): Int = cache.getOrPut(slug) {
        val name = "product_" + slug.replace('-', '_')
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }
}
