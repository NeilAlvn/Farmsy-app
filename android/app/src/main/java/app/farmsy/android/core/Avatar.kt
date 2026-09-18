package app.farmsy.android.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/// The profile picture: read from the own profiles row, uploaded straight to
/// the public `avatars` bucket under the user's id (migration 070), the URL
/// written onto the profile. The Android twin of iOS Core/Avatar.swift.
object AvatarStore {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    @Serializable
    private data class Row(val avatar_url: String? = null)

    suspend fun load(userId: String) {
        _url.value = runCatching {
            supabase.from("profiles").select(Columns.list("avatar_url")) { filter { eq("id", userId) } }
                .decodeSingleOrNull<Row>()?.avatar_url
        }.getOrNull()
    }

    /// Resizes to 512 on the long side, JPEG at 80, uploads, and points the
    /// profile at the new file. Old files are left; a sweep can come later.
    suspend fun upload(context: Context, uri: Uri, userId: String): Boolean {
        _busy.value = true
        try {
            val data = withContext(Dispatchers.IO) { encode(context, uri) } ?: return false
            val path = "$userId/avatar-${System.currentTimeMillis()}.jpg"
            return runCatching {
                val bucket = supabase.storage.from("avatars")
                bucket.upload(path, data) { upsert = true; contentType = ContentType.Image.JPEG }
                val publicUrl = bucket.publicUrl(path)
                supabase.from("profiles").update(buildJsonObject { put("avatar_url", publicUrl) }) { filter { eq("id", userId) } }
                _url.value = publicUrl
            }.isSuccess
        } finally {
            _busy.value = false
        }
    }

    private fun encode(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val long = maxOf(bounds.outWidth, bounds.outHeight)
        if (long <= 0) return null
        // Power-of-two subsample down to the smallest size still ≥ 512, then scale exactly.
        var sample = 1
        while (long / (sample * 2) >= 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = minOf(1f, 512f / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }
}
