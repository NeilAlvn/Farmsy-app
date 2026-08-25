package app.farmsy.android.core

import androidx.annotation.StringRes
import app.farmsy.android.R

/// The two taxonomy axes Aviah split out of the category list — "Type of place"
/// (location_types) and "How it's grown" (methods). Ids are language-neutral and
/// validated server-side; labels live client-side. They combine with the category
/// filters, they don't replace them. organic stays a category, not a method.
data class FarmAxisValue(val id: String, @StringRes val labelRes: Int)

object FarmAxis {
    val placeTypes = listOf(
        FarmAxisValue("shop", R.string.axis_shop),
        FarmAxisValue("vending-machine", R.string.axis_vending_machine),
        FarmAxisValue("stall", R.string.axis_stall),
        FarmAxisValue("milk-tap", R.string.axis_milk_tap),
        FarmAxisValue("self-picking", R.string.axis_self_picking),
        FarmAxisValue("self-picking-unstaffed", R.string.axis_self_picking_unstaffed),
    )

    val methods = listOf(
        FarmAxisValue("biodynamic", R.string.axis_biodynamic),
        FarmAxisValue("regenerative", R.string.axis_regenerative),
        FarmAxisValue("grass-fed", R.string.axis_grass_fed),
        FarmAxisValue("sustainable", R.string.axis_sustainable),
    )
}
