package com.nutritrack.android.data.openfoodfacts

import kotlinx.coroutines.CancellationException
import java.io.IOException

sealed interface ProductResult {
    data class Found(val product: ScannedProduct) : ProductResult
    data object NotFound : ProductResult
    data class Failure(val message: String) : ProductResult
}

object ProductRepository {

    suspend fun lookup(rawBarcode: String): ProductResult {
        val barcode = normalize(rawBarcode)
        return try {
            val response = OpenFoodFactsClient.api.getProduct(barcode)
            when {
                response.code() == 404 -> ProductResult.NotFound
                !response.isSuccessful ->
                    ProductResult.Failure("Open Food Facts returned an error (${response.code()}). Please try again.")
                else -> {
                    val body = response.body()
                    val product = body?.product
                    if (body == null || body.status != 1 || product == null) ProductResult.NotFound
                    else ProductResult.Found(product.toScannedProduct(barcode))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ProductResult.Failure("Couldn't reach Open Food Facts. Check your internet connection and try again.")
        } catch (e: Exception) {
            ProductResult.Failure("Something went wrong reading the product data.")
        }
    }

    fun normalize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length == 12) "0$digits" else digits
    }
}
