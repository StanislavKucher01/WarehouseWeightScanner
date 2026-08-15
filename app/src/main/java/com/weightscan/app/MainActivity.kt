package com.weightscan.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.weightscan.app.domain.BarcodeRuleLearner
import com.weightscan.app.domain.LearnedBarcodeRule
import com.weightscan.app.domain.TrainingExample
import com.weightscan.app.model.Product
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weightscan.app.data.ProductRepository
import com.weightscan.app.domain.BarcodeParser
import com.weightscan.app.model.ScanResult

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                WeightScanApp()
            }
        }
    }
}

@Composable
fun WeightScanApp() {

    var currentScreen by remember {
        mutableStateOf("scanner")
    }

    when (currentScreen) {

        "scanner" -> ScannerScreen(
            onAddProductClick = {
                currentScreen = "addProduct"
            }
        )

        "addProduct" -> AddProductScreen(
            onBackClick = {
                currentScreen = "scanner"
            }
        )
    }
}

@Composable
fun ScannerScreen(
    onAddProductClick: () -> Unit
) {

    var barcode by remember {
        mutableStateOf("2943079168821")
    }

    var result by remember {
        mutableStateOf<ScanResult?>(null)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Warehouse Weight Scanner",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Товаров в базе: ${ProductRepository.getAllProducts().size}"
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = barcode,

            onValueChange = {
                barcode = it.filter { char ->
                    char.isDigit()
                }
            },

            label = {
                Text("Штрихкод")
            },

            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),

            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {

                val product =
                    ProductRepository.findProduct(barcode)

                if (product == null) {

                    result = null
                    error = "Товар не найден в базе"

                } else {

                    val scanResult =
                        BarcodeParser.parse(
                            barcode = barcode,
                            product = product
                        )

                    if (scanResult != null) {
                        result = scanResult
                        error = null
                    } else {
                        result = null
                        error =
                            "Штрихкод повреждён или имеет неправильный формат"
                    }
                }
            },

            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Проверить")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onAddProductClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Добавить товар")
        }

        Spacer(modifier = Modifier.height(28.dp))

        result?.let {

            Text(
                text = it.product.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Вес")

            Text(
                text = "%.3f кг".format(it.weightKg),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Индекс склада: ${it.product.warehouseIndex}"
            )

            Text(
                text = "Индекс производителя: ${it.product.manufacturerIndex}"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Штрихкод: $barcode"
            )
        }

        error?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}
@Composable
fun AddProductScreen(
    onBackClick: () -> Unit
) {

    var name by remember {
        mutableStateOf("")
    }

    var warehouseIndex by remember {
        mutableStateOf("")
    }

    var manufacturerIndex by remember {
        mutableStateOf("")
    }

    var barcode1 by remember {
        mutableStateOf("")
    }

    var weight1 by remember {
        mutableStateOf("")
    }

    var barcode2 by remember {
        mutableStateOf("")
    }

    var weight2 by remember {
        mutableStateOf("")
    }

    var barcode3 by remember {
        mutableStateOf("")
    }

    var weight3 by remember {
        mutableStateOf("")
    }

    var learnedRule by remember {
        mutableStateOf<LearnedBarcodeRule?>(null)
    }

    var message by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {

        TextButton(
            onClick = onBackClick
        ) {
            Text("← Назад")
        }

        Text(
            text = "Новый товар",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
            },
            label = {
                Text("Название товара")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = warehouseIndex,
            onValueChange = {
                warehouseIndex = it
            },
            label = {
                Text("Индекс склада")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = manufacturerIndex,
            onValueChange = {
                manufacturerIndex = it
            },
            label = {
                Text("Индекс производителя")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "Пример №1",
            fontWeight = FontWeight.Bold
        )

        ExampleFields(
            barcode = barcode1,
            weight = weight1,

            onBarcodeChange = {
                barcode1 = it
                learnedRule = null
            },

            onWeightChange = {
                weight1 = it
                learnedRule = null
            }
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Пример №2",
            fontWeight = FontWeight.Bold
        )

        ExampleFields(
            barcode = barcode2,
            weight = weight2,

            onBarcodeChange = {
                barcode2 = it
                learnedRule = null
            },

            onWeightChange = {
                weight2 = it
                learnedRule = null
            }
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Пример №3 (необязательно)",
            fontWeight = FontWeight.Bold
        )

        ExampleFields(
            barcode = barcode3,
            weight = weight3,

            onBarcodeChange = {
                barcode3 = it
                learnedRule = null
            },

            onWeightChange = {
                weight3 = it
                learnedRule = null
            }
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = {

                message = null
                learnedRule = null

                if (
                    name.isBlank() ||
                    warehouseIndex.isBlank() ||
                    manufacturerIndex.isBlank()
                ) {

                    message =
                        "Заполни название и оба индекса"

                    return@Button
                }

                val example1 =
                    createTrainingExample(
                        barcode1,
                        weight1
                    )

                val example2 =
                    createTrainingExample(
                        barcode2,
                        weight2
                    )

                if (
                    example1 == null ||
                    example2 == null
                ) {

                    message =
                        "Проверь первые два штрихкода и веса"

                    return@Button
                }

                val examples =
                    mutableListOf(
                        example1,
                        example2
                    )

                if (
                    barcode3.isNotBlank() ||
                    weight3.isNotBlank()
                ) {

                    val example3 =
                        createTrainingExample(
                            barcode3,
                            weight3
                        )

                    if (example3 == null) {

                        message =
                            "Третий пример заполнен неправильно"

                        return@Button
                    }

                    examples.add(example3)
                }

                val rule =
                    BarcodeRuleLearner.learn(
                        examples
                    )

                if (rule == null) {

                    message =
                        "Не удалось определить правило. Попробуй добавить третий пример."

                } else {

                    learnedRule = rule
                }
            },

            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Определить правило")
        }

        learnedRule?.let { rule ->

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "Правило найдено ✓",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            Text(
                text = "Префикс: ${rule.prefix}"
            )

            Text(
                text =
                    "Вес: позиции ${rule.weightStart + 1}–" +
                            "${rule.weightStart + rule.weightLength}"
            )

            Text(
                text =
                    "Делитель: ${rule.weightDivisor}"
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                onClick = {

                    val alreadyExists =
                        ProductRepository
                            .getAllProducts()
                            .any {
                                it.barcodePrefix ==
                                        rule.prefix
                            }

                    if (alreadyExists) {

                        message =
                            "Товар с таким префиксом уже существует"

                        return@Button
                    }

                    val product =
                        Product(
                            name = name.trim(),

                            warehouseIndex =
                                warehouseIndex.trim(),

                            manufacturerIndex =
                                manufacturerIndex.trim(),

                            barcodePrefix =
                                rule.prefix,

                            weightStart =
                                rule.weightStart,

                            weightLength =
                                rule.weightLength,

                            weightDivisor =
                                rule.weightDivisor
                        )

                    ProductRepository.addProduct(
                        product
                    )

                    message =
                        "Товар сохранён ✓ Теперь нажми «Назад» и проверь его."
                },

                modifier = Modifier.fillMaxWidth()
            ) {

                Text("Сохранить товар")
            }
        }

        message?.let {

            Text(
                text = it,
                modifier = Modifier.padding(
                    top = 12.dp
                )
            )
        }
        Spacer(
            modifier = Modifier.height(40.dp)
        )
    }
}

@Composable
fun ExampleFields(
    barcode: String,
    weight: String,
    onBarcodeChange: (String) -> Unit,
    onWeightChange: (String) -> Unit
) {

    OutlinedTextField(
        value = barcode,

        onValueChange = {
            onBarcodeChange(
                it.filter { char ->
                    char.isDigit()
                }
            )
        },

        label = {
            Text("Штрихкод")
        },

        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Number
            ),

        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(
        modifier = Modifier.height(6.dp)
    )

    OutlinedTextField(
        value = weight,

        onValueChange = {
            onWeightChange(it)
        },

        label = {
            Text("Вес, кг")
        },

        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Decimal
            ),

        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

fun createTrainingExample(
    barcode: String,
    weightText: String
): TrainingExample? {

    val cleanBarcode =
        barcode.trim()

    if (
        cleanBarcode.length != 13 ||
        !cleanBarcode.all { it.isDigit() }
    ) {
        return null
    }

    val weight =
        weightText
            .trim()
            .replace(",", ".")
            .toDoubleOrNull()
            ?: return null

    if (weight <= 0) {
        return null
    }

    return TrainingExample(
        barcode = cleanBarcode,
        weightKg = weight
    )
}