package com.nutritrack.android.data.openfoodfacts

/** Searches packaged products by name in Open Food Facts (same database as the barcode scan). */
object ProductSearch {

    /** Throws on network errors: the caller decides how to show them. */
    suspend fun search(query: String): List<ScannedProduct> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val response = OpenFoodFactsClient.api.searchProducts(q)
        if (!response.isSuccessful) return emptyList()
        return response.body()?.products.orEmpty()
            .mapNotNull { p ->
                val code = p.code?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                p.toScannedProduct(code).takeIf { it.name != null && it.per100g.hasAny }
            }
            .take(12)
    }
}
