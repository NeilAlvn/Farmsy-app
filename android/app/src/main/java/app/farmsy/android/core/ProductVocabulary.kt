package app.farmsy.android.core

import android.content.Context
import kotlinx.serialization.Serializable

/// R5 — the corridor's product chips and the synonym lists behind them, loaded
/// from the bundled asset `product-vocabulary.json` (Aviah generates it from the
/// web source). The app never derives this — the closed vocabulary is validated
/// server-side and would drift if guessed — so the file is the single source of
/// truth, shared verbatim with iOS.
///
/// Matching is the web's rule, not a substring `contains`: lowercase the farm's
/// product list, collapse every run of non-alphanumerics to a single space, pad
/// both ends with a space, and test for `" " + term + " "`. Whole word, so "pear"
/// never matches inside "spear". Haystack is the product list only (`produce`,
/// folded to `produce_inferred` server-side in the flags `p`) — never the
/// description. Mirrors iOS ProductVocabulary.
@Serializable
data class ProductChip(
    val id: String,
    val nl: String,
    val en: String,
    /// Every spelling/synonym counting as this chip, already normalised by the
    /// generator. A farm matches if its normalised product text contains any as a
    /// whole word.
    val terms: List<String> = emptyList(),
)

object ProductVocabulary {

    @Serializable
    private data class File(val chips: List<ProductChip> = emptyList())

    @Volatile private var cached: List<ProductChip>? = null

    /// The 29 chips in commonality order (top is the useful end). Loaded once from
    /// the asset and cached; empty if the asset is missing or unreadable.
    fun chips(context: Context): List<ProductChip> {
        cached?.let { return it }
        val loaded = runCatching {
            context.applicationContext.assets.open("product-vocabulary.json")
                .bufferedReader().use { it.readText() }
                .let { lenientJson.decodeFromString<File>(it).chips }
        }.getOrDefault(emptyList())
        cached = loaded
        return loaded
    }

    /// The chip's label in the app's current language — nl when the config locale
    /// is Dutch, else the English base (the shared fallback).
    fun label(chip: ProductChip, context: Context): String =
        if (context.resources.configuration.locales[0].language == "nl") chip.nl else chip.en

    /// Normalise the web's way: lowercase, every run of non-alphanumerics → one
    /// space, padded with a leading and trailing space so a whole-word test is a
    /// plain `" term "` substring check. Unicode letters/digits (é, ü) stay.
    fun normalise(text: String): String {
        val sb = StringBuilder(text.length + 2)
        sb.append(' ')
        var lastWasSpace = true
        for (ch in text.lowercase()) {
            if (ch.isLetterOrDigit()) { sb.append(ch); lastWasSpace = false }
            else if (!lastWasSpace) { sb.append(' '); lastWasSpace = true }
        }
        if (!lastWasSpace) sb.append(' ')
        return sb.toString()
    }

    /// True if the already-normalised (padded) haystack contains any of the chip's
    /// terms as a whole word.
    fun matches(chip: ProductChip, normalisedHaystack: String): Boolean =
        chip.terms.any { normalisedHaystack.contains(" $it ") }
}
