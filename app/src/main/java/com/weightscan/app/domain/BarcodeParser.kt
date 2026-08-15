package com.weightscan.app.domain

import com.weightscan.app.model.Product
import com.weightscan.app.model.ScanResult

object BarcodeParser {

    fun parse(
        barcode: String,
        product: Product
    ): ScanResult? {

        if (barcode.length != 13) return null
        if (!barcode.all { it.isDigit() }) return null
        if (!barcode.startsWith(product.barcodePrefix)) return null
        if (!isValidEan13(barcode)) return null

        val weightEnd =
            product.weightStart + product.weightLength

        if (weightEnd > barcode.length) return null

        val weightRaw = barcode.substring(
            product.weightStart,
            weightEnd
        )

        val weightNumber =
            weightRaw.toIntOrNull() ?: return null

        val weightKg =
            weightNumber.toDouble() / product.weightDivisor

        return ScanResult(
            product = product,
            weightKg = weightKg
        )
    }

    private fun isValidEan13(barcode: String): Boolean {

        if (barcode.length != 13) return false

        val digits = barcode.map { it.digitToInt() }

        var sum = 0

        for (i in 0 until 12) {
            sum += if (i % 2 == 0) {
                digits[i]
            } else {
                digits[i] * 3
            }
        }

        val calculatedCheckDigit =
            (10 - (sum % 10)) % 10

        return calculatedCheckDigit == digits[12]
    }
}