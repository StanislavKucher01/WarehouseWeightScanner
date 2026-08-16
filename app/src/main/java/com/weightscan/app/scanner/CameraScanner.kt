package com.weightscan.app.scanner

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun CameraScanner(
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,

    // Такие же размеры, как у нашей рамки
    scanAreaWidthFraction: Float = 0.88f,
    scanAreaHeightFraction: Float = 95f / 210f
) {

    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

    val latestCallback =
        rememberUpdatedState(
            onBarcodeScanned
        )

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCameraPermission = granted
        }

    if (!hasCameraPermission) {

        Column(
            modifier = modifier
        ) {

            Text(
                "Для сканирования нужен доступ к камере"
            )

            Button(
                onClick = {
                    permissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                }
            ) {
                Text("Разрешить камеру")
            }
        }

        return
    }

    val mainExecutor =
        remember(context) {
            ContextCompat.getMainExecutor(context)
        }

    val scanner =
        remember {

            val options =
                BarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13
                    )
                    .build()

            BarcodeScanning.getClient(options)
        }

    val cameraController =
        remember {

            LifecycleCameraController(
                context.applicationContext
            ).apply {

                cameraSelector =
                    CameraSelector.DEFAULT_BACK_CAMERA
            }
        }

    var torchEnabled by remember {
        mutableStateOf(false)
    }

    var hasFlash by remember {
        mutableStateOf(false)
    }

    var blockedBarcode by remember {
        mutableStateOf<String?>(null)
    }

    var emptyFrames by remember {
        mutableIntStateOf(0)
    }

    DisposableEffect(Unit) {

        onDispose {

            cameraController.enableTorch(false)

            cameraController
                .clearImageAnalysisAnalyzer()

            cameraController.unbind()

            scanner.close()
        }
    }

    Box(
        modifier = modifier
    ) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),

            factory = { viewContext ->

                val previewView =
                    PreviewView(viewContext).apply {

                        scaleType =
                            PreviewView.ScaleType.FILL_CENTER
                    }

                val analyzer =
                    MlKitAnalyzer(
                        listOf(scanner),

                        ImageAnalysis
                            .COORDINATE_SYSTEM_VIEW_REFERENCED,

                        mainExecutor
                    ) { analysisResult ->

                        val barcodes =
                            analysisResult
                                .getValue(scanner)
                                ?: emptyList()

                        val previewWidth =
                            previewView.width.toFloat()

                        val previewHeight =
                            previewView.height.toFloat()

                        if (
                            previewWidth <= 0 ||
                            previewHeight <= 0
                        ) {
                            return@MlKitAnalyzer
                        }

                        // Точные границы нашей рамки
                        // уже в координатах PreviewView.

                        val scanWidth =
                            previewWidth *
                                    scanAreaWidthFraction

                        val scanHeight =
                            previewHeight *
                                    scanAreaHeightFraction

                        val scanLeft =
                            (previewWidth - scanWidth) / 2f

                        val scanRight =
                            scanLeft + scanWidth

                        val scanTop =
                            (previewHeight - scanHeight) / 2f

                        val scanBottom =
                            scanTop + scanHeight

                        val barcode =
                            barcodes.firstOrNull { detected ->

                                val box =
                                    detected.boundingBox
                                        ?: return@firstOrNull false

                                val centerX =
                                    box.exactCenterX()

                                val centerY =
                                    box.exactCenterY()

                                centerX >= scanLeft &&
                                        centerX <= scanRight &&
                                        centerY >= scanTop &&
                                        centerY <= scanBottom
                            }

                        val value =
                            barcode?.rawValue

                        if (value != null) {

                            emptyFrames = 0

                            if (value != blockedBarcode) {

                                blockedBarcode = value

                                latestCallback.value(
                                    value
                                )
                            }

                        } else {

                            emptyFrames++

                            if (emptyFrames >= 12) {
                                blockedBarcode = null
                            }
                        }
                    }

                // Анализатор устанавливаем
                // до привязки камеры.
                cameraController
                    .setImageAnalysisAnalyzer(
                        mainExecutor,
                        analyzer
                    )

                cameraController
                    .bindToLifecycle(
                        lifecycleOwner
                    )

                previewView.controller =
                    cameraController

                previewView.post {

                    hasFlash =
                        cameraController
                            .cameraInfo
                            ?.hasFlashUnit()
                            ?: false
                }

                previewView
            }
        )

        Button(
            onClick = {

                val newState =
                    !torchEnabled

                cameraController.enableTorch(
                    newState
                )

                torchEnabled =
                    newState
            },

            enabled = hasFlash,

            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {

            Text(
                if (torchEnabled) {
                    "🔦 Выкл"
                } else {
                    "🔦 Вкл"
                }
            )
        }
    }
}