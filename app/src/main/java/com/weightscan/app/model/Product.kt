package com.weightscan.app.model

data class Product(
    val name: String,
    val warehouseIndex: String,
    val manufacturerIndex: String,
    val barcodePrefix: String,
    val weightStart: Int,
    val weightLength: Int,
    val weightDivisor: Int
)