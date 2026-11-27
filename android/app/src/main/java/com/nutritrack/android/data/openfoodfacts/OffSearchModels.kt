package com.nutritrack.android.data.openfoodfacts

/** Response of Open Food Facts "cgi/search.pl" (text search). */
data class OffSearchResponse(
    val count: Int? = null,
    val products: List<OffProduct>? = null
)
