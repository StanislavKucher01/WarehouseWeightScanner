package com.weightscan.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(

    @PrimaryKey
    val barcodePrefix: String,

    val name: String,

    val warehouseIndex: String,

    val manufacturerIndex: String,

    val weightStart: Int,

    val weightLength: Int,

    val weightDivisor: Int
)