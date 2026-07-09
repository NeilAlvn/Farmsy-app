package app.farmsy.android.core

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/// Saved farms — same `favorites(user_id, farm_osm_id)` table the website
/// uses, so hearts stay in sync across web and app. Mirrors FavoritesStore.swift.
class FavoritesStore {

    private val _osmIds = MutableStateFlow<Set<String>>(emptySet())
    val osmIds: StateFlow<Set<String>> = _osmIds.asStateFlow()

    @Serializable
    private data class Row(@SerialName("farm_osm_id") val farmOsmId: String)

    suspend fun load(userId: String) {
        runCatching {
            val rows = supabase.from("favorites")
                .select(columns = Columns.list("farm_osm_id")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<Row>()
            _osmIds.value = rows.map { it.farmOsmId }.toSet()
        } // Non-fatal: leave the current set untouched.
    }

    fun isSaved(osmId: String): Boolean = _osmIds.value.contains(osmId)

    suspend fun toggle(osmId: String, userId: String) {
        if (_osmIds.value.contains(osmId)) {
            _osmIds.value = _osmIds.value - osmId
            runCatching {
                supabase.from("favorites").delete {
                    filter { eq("user_id", userId); eq("farm_osm_id", osmId) }
                }
            }.onFailure { _osmIds.value = _osmIds.value + osmId } // roll back
        } else {
            _osmIds.value = _osmIds.value + osmId
            runCatching {
                supabase.from("favorites").insert(
                    buildJsonObject {
                        put("user_id", JsonPrimitive(userId))
                        put("farm_osm_id", JsonPrimitive(osmId))
                    }
                )
            }.onFailure { _osmIds.value = _osmIds.value - osmId }
        }
    }

    fun clear() { _osmIds.value = emptySet() }
}
