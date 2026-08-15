package com.weightscan.app.data

import com.weightscan.app.data.local.ProductDao
import com.weightscan.app.data.local.ProductEntity
import com.weightscan.app.model.Product

class ProductRepository(
    private val productDao: ProductDao
) {

    suspend fun ensureDefaultProduct() {

        val existing =
            productDao.findByPrefix("2943079")

        if (existing == null) {

            productDao.insert(
                ProductEntity(
                    name = "SER TWARDY EMILGRANA",
                    warehouseIndex = "Не указан",
                    manufacturerIndex = "Не указан",
                    barcodePrefix = "2943079",
                    weightStart = 7,
                    weightLength = 5,
                    weightDivisor = 1000
                )
            )
        }
    }

    suspend fun getAllProducts(): List<Product> {

        return productDao
            .getAll()
            .map { entity ->
                entity.toProduct()
            }
    }

    suspend fun addProduct(
        product: Product
    ) {

        productDao.insert(
            product.toEntity()
        )
    }

    suspend fun prefixExists(
        prefix: String
    ): Boolean {

        return productDao.findByPrefix(prefix) != null
    }

    suspend fun findProduct(
        barcode: String
    ): Product? {

        return getAllProducts()
            .filter {
                barcode.startsWith(
                    it.barcodePrefix
                )
            }
            .maxByOrNull {
                it.barcodePrefix.length
            }
    }
}

private fun ProductEntity.toProduct(): Product {

    return Product(
        name = name,
        warehouseIndex = warehouseIndex,
        manufacturerIndex = manufacturerIndex,
        barcodePrefix = barcodePrefix,
        weightStart = weightStart,
        weightLength = weightLength,
        weightDivisor = weightDivisor
    )
}

private fun Product.toEntity(): ProductEntity {

    return ProductEntity(
        name = name,
        warehouseIndex = warehouseIndex,
        manufacturerIndex = manufacturerIndex,
        barcodePrefix = barcodePrefix,
        weightStart = weightStart,
        weightLength = weightLength,
        weightDivisor = weightDivisor
    )
}