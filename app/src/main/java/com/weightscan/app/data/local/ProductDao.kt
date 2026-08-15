package com.weightscan.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAll(): List<ProductEntity>

    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun insert(
        product: ProductEntity
    )

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM products WHERE barcodePrefix = :prefix LIMIT 1"
    )
    suspend fun findByPrefix(
        prefix: String
    ): ProductEntity?
}