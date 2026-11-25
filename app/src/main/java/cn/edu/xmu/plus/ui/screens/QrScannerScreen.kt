package cn.edu.xmu.plus.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.edu.xmu.plus.network.CheckInApiResult
import cn.edu.xmu.plus.ui.components.LocalHapticsEnabled
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private sealed interface QrScanPhase {
    data object Scanning : QrScanPhase
    data object Submitting : QrScanPhase
    data class Success(val message: String) : QrScanPhase
    data class Error(val message: String) : QrScanPhase
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QrScannerScreen(
    isDemoMode: Boolean,
    onSubmitQrCode: suspend (String) -> CheckInApiResult,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var phase by remember { mutableStateOf<QrScanPhase>(QrScanPhase.Scanning) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    val scanLock = remember { AtomicBoolean(false) }

    fun handleQrPayload(raw: String) {
        if (raw.isBlank()) return
        scope.launch {
            if (hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            phase = QrScanPhase.Submitting
            phase = when (val res = onSubmitQrCode(raw)) {
                is CheckInApiResult.Success -> QrScanPhase.Success(res.message)
                is CheckInApiResult.Error -> QrScanPhase.Error(res.message)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "qrScanLine")
    val lineOffset by infiniteTransition.animateFloat(
        initialValue = -90f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "qrScanY",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101316)),
    ) {
        if (hasCameraPermission) {
            CameraQrPreview(
                lifecycleOwner = lifecycleOwner,
                torchEnabled = torchEnabled,
                scanLock = scanLock,
                onCameraBound = { cameraRef = it },
                onBarcodeDetected = { raw -> handleQrPayload(raw) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Translucent scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (hasCameraPermission) 0.34f else 0.78f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "扫码签到",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = if (isDemoMode) "演示模式 · 可识别并解析二维码" else "CameraX + ML Kit 实时识别",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        val next = !torchEnabled
                        torchEnabled = next
                        cameraRef?.cameraControl?.enableTorch(next)
                    },
                    enabled = hasCameraPermission,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (torchEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White.copy(alpha = 0.16f)
                        },
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        contentDescription = "手电筒",
                    )
                }
            }

            // Center Scan Reticle or Permission Card
            if (!hasCameraPermission) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                        Text("需要相机权限以扫描签到二维码", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "授权相机后即可对准教室大屏二维码秒速签到，也可以点击下方手动粘贴二维码内容。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("授予相机权限")
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(32.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (phase is QrScanPhase.Scanning) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(0, lineOffset.roundToInt()) }
                                .width(184.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(88.dp),
                        )
                    }
                }
            }

            // Bottom Result / Action Card
            AnimatedContent(
                targetState = phase,
                label = "qrPhase",
                modifier = Modifier.fillMaxWidth(),
            ) { currentPhase ->
                when (currentPhase) {
                    QrScanPhase.Scanning -> {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("对准课堂签到二维码", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "支持畅课 JSON 与 /j?p= 跳转二维码",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        scanLock.set(true)
                                        showPasteDialog = true
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("手动粘贴")
                                }
                            }
                        }
                    }

                    QrScanPhase.Submitting -> {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(34.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Text("正在提交签到至畅课平台…", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }

                    is QrScanPhase.Success -> {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = currentPhase.message,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                                    Text("完成并返回")
                                }
                            }
                        }
                    }

                    is QrScanPhase.Error -> {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(38.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = currentPhase.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        scanLock.set(false)
                                        phase = QrScanPhase.Scanning
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("继续扫描")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        var pasted by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showPasteDialog = false
                scanLock.set(false)
            },
            title = { Text("手动粘贴签到二维码") },
            text = {
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text("二维码 JSON 或 /j?p= 链接") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasteDialog = false
                        scanLock.set(false)
                    },
                ) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasteDialog = false
                        handleQrPayload(pasted.trim())
                    },
                    enabled = pasted.isNotBlank(),
                ) {
                    Text("提交签到")
                }
            },
        )
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraQrPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    torchEnabled: Boolean,
    scanLock: AtomicBoolean,
    onCameraBound: (Camera) -> Unit,
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener(
            {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build()
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage == null || scanLock.get()) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees,
                                )
                                scanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        val raw = barcodes.firstOrNull()?.rawValue
                                        if (!raw.isNullOrBlank() && scanLock.compareAndSet(false, true)) {
                                            mainExecutor.execute { onBarcodeDetected(raw) }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            }
                        }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    camera.cameraControl.enableTorch(torchEnabled)
                    onCameraBound(camera)
                }
            },
            mainExecutor,
        )

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}
