package com.weightscan.app.data

import com.weightscan.app.model.Product

object ProductRepository {

    private val products = mutableListOf(
        Product(
            name = "SER TWARDY EMILGRANA",
            warehouseIndex = "Не указан",
            manufacturerIndex = "Не указан",
            barcodePrefix = "2943079",
            weightStart = 7,
            weightLength = 5,
            weightDivisor = 1000
        )
    )

    fun getAllProducts(): List<Product> {
        return products.toList()
    }

    fun addProduct(product: Product) {
        products.add(product)
    }

    fun findProduct(barcode: String): Product? {
        return products
            .filter { barcode.startsWith(it.barcodePrefix) }
            .maxByOrNull { it.barcodePrefix.length }
    }
}