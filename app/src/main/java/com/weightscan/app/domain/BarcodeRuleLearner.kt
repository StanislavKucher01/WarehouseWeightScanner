package com.weightscan.app.domain

import kotlin.math.abs
import kotlin.math.roundToLong

data class TrainingExample(
    val barcode: String,
    val weightKg: Double
)

data class LearnedBarcodeRule(
    val prefix: String,
    val weightStart: Int,
    val weightLength: Int,
    val weightDivisor: Int
)

object BarcodeRuleLearner {

    fun learn(
        examples: List<TrainingExample>
    ): LearnedBarcodeRule? {

        if (examples.size < 2) {
            return null
        }

        if (examples.map { it.barcode }.distinct().size < 2) {
            return null
        }

        if (examples.map { it.weightKg }.distinct().size < 2) {
            return null
        }

        if (examples.any {
                it.barcode.length != 13 ||
                        !it.barcode.all { char -> char.isDigit() } ||
                        it.weightKg <= 0 ||
                        !isValidEan13(it.barcode)
            }
        ) {
            return null
        }

        val possibleDivisors = listOf(
            1000,
            100,
            10,
            1
        )

        val candidates =
            mutableListOf<LearnedBarcodeRule>()

        for (divisor in possibleDivisors) {

            val encodedWeights = examples.map {
                (it.weightKg * divisor).roundToLong()
            }

            val divisorFits = examples.indices.all { index ->

                val restoredWeight =
                    encodedWeights[index].toDouble() / divisor

                abs(
                    restoredWeight - examples[index].weightKg
                ) < 0.0005
            }

            if (!divisorFits) {
                continue
            }

            val minimumLength =
                encodedWeights.maxOf {
                    it.toString().length
                }

            for (length in minimumLength..6) {

                if (length > 12) {
                    continue
                }

                val expectedValues =
                    encodedWeights.map {
                        it.toString().padStart(
                            length,
                            '0'
                        )
                    }

                for (start in 0..(12 - length)) {

                    val matches =
                        examples.indices.all { index ->

                            val barcode =
                                examples[index].barcode

                            val expected =
                                expectedValues[index]

                            barcode.substring(
                                start,
                                start + length
                            ) == expected
                        }

                    if (!matches) {
                        continue
                    }

                    val partsBeforeWeight =
                        examples.map {
                            it.barcode.substring(0, start)
                        }

                    var prefixLength = 0

                    for (i in 0 until start) {
                        val digit = partsBeforeWeight.first()[i]

                        if (partsBeforeWeight.all { it[i] == digit }) {
                            prefixLength++
                        } else {
                            break
                        }
                    }

                    if (prefixLength < 2) {
                        continue
                    }

                    val prefix =
                        examples.first()
                            .barcode
                            .substring(0, prefixLength)

                    candidates.add(
                        LearnedBarcodeRule(
                            prefix = prefix,
                            weightStart = start,
                            weightLength = length,
                            weightDivisor = divisor
                        )
                    )
                }
            }
        }

        return candidates
            .sortedWith(
                compareByDescending<LearnedBarcodeRule> {
                    it.prefix.length
                }
                    .thenByDescending {
                        it.weightDivisor
                    }
                    .thenBy {
                        it.weightLength
                    }
            )
            .firstOrNull()
    }

    private fun isValidEan13(
        barcode: String
    ): Boolean {

        if (
            barcode.length != 13 ||
            !barcode.all { it.isDigit() }
        ) {
            return false
        }

        val digits =
            barcode.map {
                it.digitToInt()
            }

        var sum = 0

        for (i in 0 until 12) {

            sum += if (i % 2 == 0) {
                digits[i]
            } else {
                digits[i] * 3
            }
        }

        val checkDigit =
            (10 - (sum % 10)) % 10

        return checkDigit == digits[12]
    }
}