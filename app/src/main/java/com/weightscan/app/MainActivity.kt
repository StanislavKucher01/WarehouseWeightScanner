package com.weightscan.app

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
                ScannerScreen()
            }
        }
    }
}

@Composable
fun ScannerScreen() {

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
                        error = "Штрихкод повреждён или имеет неправильный формат"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Проверить")
        }

        Spacer(modifier = Modifier.height(32.dp))

        result?.let {

            Text(
                text = it.product.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Штрихкод: $barcode"
            )
        }

        error?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}