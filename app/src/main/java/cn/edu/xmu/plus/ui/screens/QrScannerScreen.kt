package cn.edu.xmu.plus.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.edu.xmu.plus.network.CheckInApiResult
import cn.edu.xmu.plus.ui.components.LocalHapticsEnabled
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface QrScanPhase {
    data object Scanning : QrScanPhase
    data class Submitting(val label: String = "正在提交签到至畅课平台…") : QrScanPhase
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
    val density = LocalDensity.current

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
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    val scanLock = remember { AtomicBoolean(false) }

    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(4f) }
    var lastManualZoomAtMs by remember { mutableLongStateOf(0L) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var focusRingNonce by remember { mutableIntStateOf(0) }

    val latestCamera by rememberUpdatedState(cameraRef)
    val latestZoomRatio by rememberUpdatedState(zoomRatio)
    val latestMinZoom by rememberUpdatedState(minZoomRatio)
    val latestMaxZoom by rememberUpdatedState(maxZoomRatio)
    val latestPhase by rememberUpdatedState(phase)

    LaunchedEffect(focusRingNonce) {
        if (focusRingNonce > 0) {
            delay(850L)
            focusTapOffset = null
        }
    }

    fun applyZoom(targetRatio: Float, manual: Boolean) {
        val clamped = targetRatio.coerceIn(latestMinZoom, latestMaxZoom)
        if (manual) {
            lastManualZoomAtMs = System.currentTimeMillis()
        }
        latestCamera?.cameraControl?.setZoomRatio(clamped)
    }

    fun triggerTapFocus(offset: Offset) {
        val pv = previewViewRef ?: return
        val cam = latestCamera ?: return
        val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
        focusTapOffset = offset
        focusRingNonce++
        if (hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun handleQrPayload(raw: String) {
        if (raw.isBlank()) return
        scope.launch {
            if (hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            phase = QrScanPhase.Submitting("正在提交签到至畅课平台…")
            phase = when (val res = onSubmitQrCode(raw)) {
                is CheckInApiResult.Success -> QrScanPhase.Success(res.message)
                is CheckInApiResult.Error -> QrScanPhase.Error(res.message)
            }
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            scanLock.set(false)
            return@rememberLauncherForActivityResult
        }
        scanLock.set(true)
        phase = QrScanPhase.Submitting("正在识别相册图片中的二维码…")
        val inputImage = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
        if (inputImage == null) {
            phase = QrScanPhase.Error("无法读取所选图片，请重新选择或对准屏幕扫码")
            return@rememberLauncherForActivityResult
        }
        val galleryScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        galleryScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (raw != null) {
                    handleQrPayload(raw)
                } else {
                    phase = QrScanPhase.Error("未在图片中识别到签到二维码，请换一张清晰截图")
                }
            }
            .addOnFailureListener { err ->
                phase = QrScanPhase.Error("识别相册二维码失败：${err.message ?: "请重试"}")
            }
            .addOnCompleteListener {
                runCatching { galleryScanner.close() }
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
                onPreviewViewReady = { previewViewRef = it },
                onCameraBound = { cameraRef = it },
                onZoomStateChanged = { ratio, minZoom, maxZoom ->
                    zoomRatio = ratio
                    minZoomRatio = minZoom
                    maxZoomRatio = maxZoom
                },
                onAutoZoomSuggested = { suggested ->
                    val sinceManual = System.currentTimeMillis() - lastManualZoomAtMs
                    if (!scanLock.get() && latestPhase is QrScanPhase.Scanning && sinceManual > 5_000L) {
                        applyZoom(suggested, manual = false)
                    }
                },
                onBarcodeDetected = { raw -> handleQrPayload(raw) },
                modifier = Modifier.fillMaxSize(),
            )

            // Gesture layer behind chrome overlays: pinch-to-zoom, tap-to-focus, double-tap zoom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(hasCameraPermission) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (latestPhase is QrScanPhase.Scanning && zoom != 1f) {
                                applyZoom(latestZoomRatio * zoom, manual = true)
                            }
                        }
                    }
                    .pointerInput(hasCameraPermission) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (latestPhase is QrScanPhase.Scanning) {
                                    triggerTapFocus(offset)
                                }
                            },
                            onDoubleTap = {
                                if (latestPhase is QrScanPhase.Scanning) {
                                    val target = if (latestZoomRatio > 1.35f) {
                                        1.0f
                                    } else {
                                        2.5f
                                    }
                                    applyZoom(target, manual = true)
                                    if (hapticsEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            },
                        )
                    },
            )
        }

        // Translucent scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (hasCameraPermission) 0.30f else 0.78f)),
        )

        // Tap-to-focus indicator ring
        AnimatedVisibility(
            visible = focusTapOffset != null && hasCameraPermission,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(240)),
        ) {
            val tapPos = focusTapOffset
            if (tapPos != null) {
                val halfSizePx = with(density) { 26.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (tapPos.x - halfSizePx).roundToInt(),
                                (tapPos.y - halfSizePx).roundToInt(),
                            )
                        }
                        .size(52.dp)
                        .border(
                            width = 1.8.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
            }
        }

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
                        text = if (isDemoMode) "演示模式 · 可识别并解析二维码" else "CameraX 1080p + ML Kit 实时识别",
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
                            text = "授权相机后即可对准教室大屏二维码秒速签到，也可以点击下方从相册扫码或手动粘贴二维码内容。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("授予相机权限")
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(236.dp)
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

                    if (phase is QrScanPhase.Scanning) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.45f),
                            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.2f)),
                        ) {
                            Text(
                                text = "双指捏合或双击画面缩放 · 当前 ${String.format(Locale.US, "%.1f×", zoomRatio)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            // Bottom Zoom Bar + Result / Action Card
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasCameraPermission && phase is QrScanPhase.Scanning) {
                    QrZoomControlBar(
                        zoomRatio = zoomRatio,
                        minZoomRatio = minZoomRatio,
                        maxZoomRatio = maxZoomRatio,
                        onSelectZoom = { ratio ->
                            applyZoom(ratio, manual = true)
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onSliderZoom = { ratio ->
                            applyZoom(ratio, manual = true)
                        },
                    )
                }

                AnimatedContent(
                    targetState = phase,
                    label = "qrPhase",
                    modifier = Modifier.fillMaxWidth(),
                ) { currentPhase ->
                    when (currentPhase) {
                        QrScanPhase.Scanning -> {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column {
                                        Text("对准课堂签到二维码", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "支持双指捏合缩放、点击对焦、相册扫码与手动粘贴",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                scanLock.set(true)
                                                galleryPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                                )
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhotoLibrary,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("相册扫码")
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                scanLock.set(true)
                                                showPasteDialog = true
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.weight(1f),
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
                        }

                        is QrScanPhase.Submitting -> {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
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
                                    Text(currentPhase.label, style = MaterialTheme.typography.titleSmall)
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

@Composable
private fun QrZoomControlBar(
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onSelectZoom: (Float) -> Unit,
    onSliderZoom: (Float) -> Unit,
) {
    val sliderMax = maxZoomRatio.coerceAtMost(8f).coerceAtLeast(minZoomRatio + 0.1f)
    val presets = remember(minZoomRatio, maxZoomRatio) {
        listOf(1f, 2f, 3f, 5f)
            .filter { it >= minZoomRatio - 0.05f && it <= maxZoomRatio + 0.05f }
            .ifEmpty { listOf(minZoomRatio) }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.56f),
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { preset ->
                        val isActive = abs(zoomRatio - preset) < 0.22f
                        Surface(
                            shape = CircleShape,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White.copy(alpha = 0.14f)
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSelectZoom(preset) },
                        ) {
                            Text(
                                text = "${preset.roundToInt()}×",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontFeatureSettings = "tnum",
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Text(
                    text = String.format(Locale.US, "%.1f×", zoomRatio),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                )
            }

            Slider(
                value = zoomRatio.coerceIn(minZoomRatio, sliderMax),
                onValueChange = onSliderZoom,
                valueRange = minZoomRatio..sliderMax,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraQrPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    torchEnabled: Boolean,
    scanLock: AtomicBoolean,
    onPreviewViewReady: (PreviewView) -> Unit,
    onCameraBound: (Camera) -> Unit,
    onZoomStateChanged: (Float, Float, Float) -> Unit,
    onAutoZoomSuggested: (Float) -> Unit,
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val latestOnAutoZoom by rememberUpdatedState(onAutoZoomSuggested)

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .setZoomSuggestionOptions(
                    ZoomSuggestionOptions.Builder { zoomRatio ->
                        latestOnAutoZoom(zoomRatio)
                        true
                    }
                        .setMaxSupportedZoomRatio(5.0f)
                        .build(),
                )
                .build(),
        )
    }

    LaunchedEffect(previewView) {
        onPreviewViewReady(previewView)
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var observedZoomState: androidx.lifecycle.LiveData<ZoomState>? = null
        val zoomObserver = Observer<ZoomState> { zs ->
            onZoomStateChanged(zs.zoomRatio, zs.minZoomRatio, zs.maxZoomRatio)
        }

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
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
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
                    observedZoomState = camera.cameraInfo.zoomState.also { ld ->
                        ld.observe(lifecycleOwner, zoomObserver)
                    }
                    onCameraBound(camera)
                }
            },
            mainExecutor,
        )

        onDispose {
            observedZoomState?.removeObserver(zoomObserver)
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
