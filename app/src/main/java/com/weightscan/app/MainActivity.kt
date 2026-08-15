package com.weightscan.app

import androidx.compose.foundation.layout.Row
import android.os.SystemClock
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import kotlin.math.roundToLong
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.weightscan.app.scanner.ScanFeedback
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.weightscan.app.scanner.CameraScanner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.weightscan.app.data.local.AppDatabase
import kotlinx.coroutines.launch
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

    val context =
        LocalContext.current

    val repository =
        remember {

            ProductRepository(
                AppDatabase
                    .getDatabase(context)
                    .productDao()
            )
        }

    var currentScreen by remember {
        mutableStateOf("scanner")
    }

    var databaseReady by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {

        repository.ensureDefaultProduct()

        databaseReady = true
    }

    if (!databaseReady) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                text = "Загрузка..."
            )
        }

        return
    }

    when (currentScreen) {

        "scanner" -> ScannerScreen(
            repository = repository,

            onAddProductClick = {
                currentScreen = "addProduct"
            }
        )

        "addProduct" -> AddProductScreen(
            repository = repository,

            onBackClick = {
                currentScreen = "scanner"
            }
        )
    }
}

@Composable
fun ScannerScreen(
    repository: ProductRepository,
    onAddProductClick: () -> Unit
) {

    var barcode by remember {
        mutableStateOf("")
    }

    var result by remember {
        mutableStateOf<ScanResult?>(null)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var productCount by remember {
        mutableStateOf(0)
    }

    var successFlash by remember {
        mutableStateOf(false)
    }

    var lastScannedWeight by remember {
        mutableStateOf<Double?>(null)
    }

    var successScanId by remember {
        mutableIntStateOf(0)
    }

    val coroutineScope =
        rememberCoroutineScope()

    val context =
        LocalContext.current

    val scanFeedback =
        remember {
            ScanFeedback(context)
        }

    var totalWeightGrams by remember {
        mutableStateOf(0L)
    }

    var scanCount by remember {
        mutableStateOf(0)
    }

    val scanHistoryGrams = remember {
        mutableStateListOf<Long>()
    }

    var lastProcessedBarcode by remember {
        mutableStateOf<String?>(null)
    }

    var lastProcessedTime by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(successScanId) {

        if (successScanId > 0) {

            successFlash = true

            delay(500)

            successFlash = false
        }
    }

    DisposableEffect(Unit) {

        onDispose {
            scanFeedback.release()
        }
    }

    LaunchedEffect(Unit) {
        productCount =
            repository.getAllProducts().size
    }

    fun processBarcode(
        value: String
    ) {

        val now = SystemClock.elapsedRealtime()

        // Один и тот же код нельзя добавить повторно
        // в течение одной секунды
        if (
            value == lastProcessedBarcode &&
            now - lastProcessedTime < 1000L
        ) {
            return
        }

        lastProcessedBarcode = value
        lastProcessedTime = now

        barcode = value

        coroutineScope.launch {
            // дальше твой существующий код

        barcode = value

        coroutineScope.launch {

            val product =
                repository.findProduct(value)

            if (product == null) {

                result = null
                error = "Товар не найден"

                return@launch
            }

            val scanResult =
                BarcodeParser.parse(
                    barcode = value,
                    product = product
                )

            if (scanResult != null) {

                result = scanResult
                error = null

                lastScannedWeight =
                    scanResult.weightKg

                val weightGrams =
                    (scanResult.weightKg * 1000)
                        .roundToLong()

                totalWeightGrams += weightGrams
                scanCount++

                scanHistoryGrams.add(weightGrams)

                successScanId++

                scanFeedback.success()

            } else {

                result = null
                error = "Некорректный штрихкод"
            }
        }
    }

    fun undoLastScans(count: Int) {

        val countToRemove =
            minOf(count, scanHistoryGrams.size)

        repeat(countToRemove) {

            val lastWeight =
                scanHistoryGrams.removeAt(
                    scanHistoryGrams.lastIndex
                )

            totalWeightGrams -= lastWeight
        }

        scanCount = scanHistoryGrams.size

        if (totalWeightGrams < 0) {
            totalWeightGrams = 0
        }

        result = null
        barcode = ""
        error = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                horizontal = 16.dp,
                vertical = 16.dp
            ),
        verticalArrangement =
            Arrangement.Top
    ) {

        Text(
            text = "WeightScan",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Товаров в базе: $productCount",
            fontSize = 14.sp
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {

                CameraScanner(
                    onBarcodeScanned = {
                        processBarcode(it)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Рамка для штрихкода
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.88f)
                        .height(95.dp)
                        .border(
                            width = if (successFlash) {
                                4.dp
                            } else {
                                2.dp
                            },
                            color = if (successFlash) {
                                Color(0xFF2E7D32)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                )

                // Короткое подтверждение успешного скана
                if (successFlash) {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(
                                    alpha = 0.45f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = "✓ %.3f кг".format(
                                lastScannedWeight ?: 0.0
                            ),
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {

            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                Text(
                    text = "Текущая сессия",
                    fontSize = 13.sp
                )

                Text(
                    text = "$scanCount шт. | %.3f кг".format(
                        totalWeightGrams / 1000.0
                    ),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        result?.let { scanResult ->

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // СЛЕВА
                Button(
                    onClick = {

                        if (scanHistoryGrams.isNotEmpty()) {

                            val lastWeight =
                                scanHistoryGrams.removeAt(
                                    scanHistoryGrams.lastIndex
                                )

                            totalWeightGrams -= lastWeight
                            scanCount = scanHistoryGrams.size

                            result = null
                            barcode = ""
                            error = null
                        }
                    },
                    enabled = scanHistoryGrams.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("↶ Отменить")
                }

                // СПРАВА
                Button(
                    onClick = {
                        totalWeightGrams = 0L
                        scanCount = 0
                        scanHistoryGrams.clear()

                        result = null
                        barcode = ""
                        error = null
                    },
                    enabled = scanCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Сбросить")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            scanResult.product.name,
                        fontSize = 20.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Text(
                        text =
                            "Индекс склада: " +
                                    scanResult.product.warehouseIndex
                    )

                    Text(
                        text =
                            "Индекс производителя: " +
                                    scanResult.product.manufacturerIndex
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text = barcode,
                        fontSize = 13.sp
                    )
                }
            }
        }

        error?.let {

            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "⚠ $it",
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (barcode.isNotBlank()) {

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(
                            text = barcode
                        )
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = onAddProductClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {

            Text("+ Добавить товар")
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "Ручной ввод",
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = barcode,

            onValueChange = {
                barcode =
                    it.filter { char ->
                        char.isDigit()
                    }
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

            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Button(
            onClick = {
                processBarcode(barcode)
            },

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text("Проверить вручную")
        }

        Spacer(
            modifier = Modifier.height(40.dp)
        )
    }
}
@Composable
fun AddProductScreen(
    repository: ProductRepository,
    onBackClick: () -> Unit
) {
    val coroutineScope =
        rememberCoroutineScope()

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

                    coroutineScope.launch {

                        val alreadyExists =
                            repository.prefixExists(
                                rule.prefix
                            )

                        if (alreadyExists) {

                            message =
                                "Товар с таким префиксом уже существует"

                        } else {

                            val product =
                                Product(
                                    name =
                                        name.trim(),

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

                            repository.addProduct(
                                product
                            )

                            message =
                                "Товар сохранён ✓ Теперь нажми «Назад» и проверь его."
                        }
                    }
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