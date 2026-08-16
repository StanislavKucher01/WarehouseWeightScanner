package com.weightscan.app

import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import android.content.Context
import android.content.Intent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.appcompat.app.AppCompatActivity
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

class MainActivity : AppCompatActivity() {

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

    var currentScreen by remember { mutableStateOf("scanner") }
    var databaseReady by remember { mutableStateOf(false) }
    val sessionHistory = remember { mutableStateListOf<SavedSession>() }

    LaunchedEffect(Unit) {
        repository.ensureDefaultProduct()
        databaseReady = true
    }

    if (!databaseReady) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(text = stringResource(R.string.loading))        }
        return
    }

    when (currentScreen) {
        "scanner" -> ScannerScreen(
            repository = repository,
            onAddProductClick = { currentScreen = "addProduct" },
            onOpenProductsClick = { currentScreen = "products" },
            onOpenHistoryClick = { currentScreen = "history" },
            // Добавляем : Int и : Long вот сюда:
            onSaveSession = { records ->
                sessionHistory.add(0, SavedSession(records = records))
            }
        )
        "addProduct" -> AddProductScreen(repository = repository, onBackClick = { currentScreen = "scanner" })
        "products" -> ProductListScreen(repository = repository, onBackClick = { currentScreen = "scanner" })
        "history" -> HistoryScreen(sessions = sessionHistory, onBackClick = { currentScreen = "scanner" })
    }
}

    @Composable
    fun ScannerScreen(
        repository: ProductRepository,
        onAddProductClick: () -> Unit,
        onOpenProductsClick: () -> Unit,
        onOpenHistoryClick: () -> Unit,
        onSaveSession: (List<ScanRecord>) -> Unit
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

        val productNotFoundText = stringResource(R.string.product_not_found)
        val invalidBarcodeText = stringResource(R.string.invalid_barcode)

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

        val scanHistoryRecords = remember { mutableStateListOf<ScanRecord>() }

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

            val product = repository.findProduct(value)

            if (product == null) {
                result = null
                error = productNotFoundText
                return@launch
            }

            val scanResult = BarcodeParser.parse(
                barcode = value,
                product = product
            )

            if (scanResult != null) {
                result = scanResult
                error = null

                lastScannedWeight = scanResult.weightKg

                val weightGrams = (scanResult.weightKg * 1000).roundToLong()

                totalWeightGrams += weightGrams
                scanCount++

                // Сохраняем подробную запись о товаре
                scanHistoryRecords.add(
                    ScanRecord(
                        productName = scanResult.product.name,
                        warehouseIndex = scanResult.product.warehouseIndex,
                        manufacturerIndex = scanResult.product.manufacturerIndex,
                        weightGrams = weightGrams
                    )
                )
                successScanId++
                scanFeedback.success()
            }else {
                result = null
                error = invalidBarcodeText
            }
        }
    }

        fun undoLastScans(count: Int) {

            val countToRemove =
                minOf(count, scanHistoryRecords.size)

            repeat(countToRemove) {

                val lastRecord =
                    scanHistoryRecords.removeAt(
                        scanHistoryRecords.lastIndex
                    )

                totalWeightGrams -= lastRecord.weightGrams
            }

            scanCount = scanHistoryRecords.size

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
        LanguageSelector()

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.current_session),
                        fontSize = 13.sp
                    )

                    Text(
                        text = stringResource(
                            R.string.session_summary,
                            scanCount,
                            totalWeightGrams / 1000.0
                        ))
                }

                if (scanCount > 0) {
                    IconButton(
                        onClick = {
                            onSaveSession(scanHistoryRecords.toList())

                            totalWeightGrams = 0L
                            scanCount = 0
                            scanHistoryRecords.clear()
                            result = null
                            barcode = ""
                            error = null
                        }
                    ) {
                        Text(
                            text = "✓",
                            color = Color(0xFF2E7D32),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (scanHistoryRecords.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // СЛЕВА (Кнопка отмены)
                Button(
                    onClick = {
                        if (scanHistoryRecords.isNotEmpty()) {

                            val lastRecord =
                                scanHistoryRecords.removeAt(
                                    scanHistoryRecords.lastIndex
                                )

                            totalWeightGrams -= lastRecord.weightGrams
                            scanCount = scanHistoryRecords.size

                            result = null
                            barcode = ""
                            error = null
                        }
                    },
                    enabled = scanHistoryRecords.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.undo))
                }

                Button(
                    onClick = {
                        totalWeightGrams = 0L
                        scanCount = 0
                        scanHistoryRecords.clear()

                        result = null
                        barcode = ""
                        error = null
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.reset))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        result?.let { scanResult ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = scanResult.product.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.warehouse_index,
                            scanResult.product.warehouseIndex
                        )
                    )

                    Text(
                        text = stringResource(
                            R.string.manufacturer_index,
                            scanResult.product.manufacturerIndex
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = barcode, fontSize = 13.sp)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenProductsClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.products))
            }

            Button(
                onClick = onOpenHistoryClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.history))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddProductClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {

            Text(stringResource(R.string.add_product))
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = stringResource(R.string.manual_input),
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
                Text(stringResource(R.string.barcode))
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

            Text(stringResource(R.string.check_manually))
        }

        Spacer(
            modifier = Modifier.height(40.dp)
        )
    }
}

    @Composable
    fun ProductListScreen(
        repository: ProductRepository,
        onBackClick: () -> Unit
    ) {
        val context = LocalContext.current
        var products by remember { mutableStateOf<List<Product>>(emptyList()) }

        LaunchedEffect(Unit) {
            products = repository.getAllProducts()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBackClick) { Text("← Назад") }

                Button(onClick = { exportProductsToCsv(context, products) }) {
                    Text("Экспорт (CSV)")
                }
            }

            Text(
                text = "База товаров (${products.size})",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            LazyColumn {
                items(products) { product ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(text = "Склад: ${product.warehouseIndex} | Производитель: ${product.manufacturerIndex}")
                            Text(text = "Префикс штрихкода: ${product.barcodePrefix}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

@Composable
fun HistoryScreen(
    sessions: List<SavedSession>,
    onBackClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    // Состояние для хранения выбранной сессии (если null, то показываем список сессий)
    var selectedSession by remember { mutableStateOf<SavedSession?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    if (selectedSession != null) {
                        selectedSession = null // Возвращаемся к списку сессий
                    } else {
                        onBackClick() // Возвращаемся на главный экран
                    }
                }
            ) {
                Text("← Назад")
            }
        }

        Text(
            text = if (selectedSession == null) "История сессий" else "Детали сессии",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (selectedSession == null) {
            // ЭКРАН 1: Список всех сессий
            if (sessions.isEmpty()) {
                Text("История пока пуста", color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn {
                    items(sessions) { session ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            onClick = { selectedSession = session } // Клик по сессии открывает детали!
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = dateFormat.format(Date(session.timestamp)),
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${session.count} шт.",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Text(
                                        text = "%.3f кг".format(session.totalWeightGrams / 1000.0),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Показываем краткий список товаров, вошедших в сессию
                                val summaryText = session.records
                                    .groupBy { it.productName }
                                    .entries
                                    .joinToString(", ") { "${it.key} (${it.value.size} шт.)" }

                                Text(
                                    text = summaryText,
                                    fontSize = 13.sp,
                                    color = Color.DarkGray,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ЭКРАН 2: Детали конкретной сессии (список каждого добавленного веса и товара)
            Text(
                text = dateFormat.format(Date(selectedSession!!.timestamp)),
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn {
                items(selectedSession!!.records) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.productName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Склад: ${record.warehouseIndex} | Производитель: ${record.manufacturerIndex}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                text = "%.3f кг".format(record.weightGrams / 1000.0),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
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

// Структура одной конкретной позиции в сессии
data class ScanRecord(
    val productName: String,
    val warehouseIndex: String,
    val manufacturerIndex: String,
    val weightGrams: Long
)

// Обновленная структура сессии
data class SavedSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val records: List<ScanRecord> // Список всех сканирований в этой сессии
) {
    val count: Int get() = records.size
    val totalWeightGrams: Long get() = records.sumOf { it.weightGrams }
}

fun exportProductsToCsv(context: Context, products: List<Product>) {
    val csvData = StringBuilder()
    // Заголовки таблицы
    csvData.append("Название;Индекс склада;Индекс производителя;Префикс\n")

    products.forEach { p ->
        csvData.append("${p.name};${p.warehouseIndex};${p.manufacturerIndex};${p.barcodePrefix}\n")
    }

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Экспорт товаров WeightScan")
        putExtra(Intent.EXTRA_TEXT, csvData.toString())
    }

    val shareIntent = Intent.createChooser(sendIntent, "Сохранить или отправить файл")
    context.startActivity(shareIntent)
}

@Composable
fun LanguageSelector() {

    val currentLanguage =
        AppCompatDelegate
            .getApplicationLocales()
            .get(0)
            ?.language
            ?: "ru"

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        TextButton(
            onClick = {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags("ru")
                )
            }
        ) {
            Text(
                text = "RU",
                fontWeight =
                    if (currentLanguage == "ru")
                        FontWeight.Bold
                    else
                        FontWeight.Normal
            )
        }

        TextButton(
            onClick = {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags("uk")
                )
            }
        ) {
            Text(
                text = "UA",
                fontWeight =
                    if (currentLanguage == "uk")
                        FontWeight.Bold
                    else
                        FontWeight.Normal
            )
        }

        TextButton(
            onClick = {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags("pl")
                )
            }
        ) {
            Text(
                text = "PL",
                fontWeight =
                    if (currentLanguage == "pl")
                        FontWeight.Bold
                    else
                        FontWeight.Normal
            )
        }
    }
}