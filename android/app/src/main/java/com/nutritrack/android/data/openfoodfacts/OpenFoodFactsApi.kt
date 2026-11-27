package com.nutritrack.android.data.openfoodfacts

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS
    ): Response<OffProductResponse>

    @GET("cgi/search.pl?search_simple=1&action=process&json=1&page_size=12")
    suspend fun searchProducts(
        @Query("search_terms") searchTerms: String,
        @Query("fields") fields: String = FIELDS
    ): Response<OffSearchResponse>

    companion object {
        const val FIELDS = "code,product_name,brands,quantity,serving_size,serving_quantity," +
            "image_front_url,ingredients_text,allergens_tags,nutriscore_grade,nova_group,nutriments"
    }
}
