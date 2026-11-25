package cn.edu.xmu.plus.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.net.http.SslError
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import cn.edu.xmu.plus.auth.XmuAuthClient
import cn.edu.xmu.plus.data.AttachmentPreviewInfo
import cn.edu.xmu.plus.data.TimeFormatUtils
import cn.edu.xmu.plus.debug.DebugLogStore
import cn.edu.xmu.plus.ui.components.ExpressiveGlidingSegmentedBar
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.FrostedIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLConnection

private enum class ViewerMode(val label: String) {
    NATIVE("原生阅读"),
    ONLINE("在线预览"),
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "csv", "json", "xml", "log", "yaml", "yml",
    "c", "cpp", "cc", "h", "hpp", "py", "java", "kt", "js", "ts", "sql",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InAppViewerScreen(
    uploadId: Long,
    initialFileName: String,
    isDemoMode: Boolean,
    onResolvePreview: suspend (Long, String) -> Result<AttachmentPreviewInfo>,
    onDownloadFile: suspend (String, File) -> Result<File>,
    onSyncWebViewCookies: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reloadNonce by remember { mutableIntStateOf(0) }
    var isResolving by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var previewInfo by remember { mutableStateOf<AttachmentPreviewInfo?>(null) }
    var cachedFile by remember { mutableStateOf<File?>(null) }
    var cachedOriginalFile by remember { mutableStateOf<File?>(null) }
    var decodedImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var textContent by remember { mutableStateOf<String?>(null) }
    var activeMode by remember { mutableStateOf(ViewerMode.NATIVE) }

    val displayFileName = previewInfo?.fileName?.ifBlank { null } ?: initialFileName
    val ext = remember(displayFileName) { displayFileName.substringAfterLast('.', "").lowercase() }
    val isImage = remember(ext, previewInfo) {
        ext in IMAGE_EXTENSIONS || previewInfo?.fileType.equals("image", ignoreCase = true)
    }
    val isText = remember(ext) { ext in TEXT_EXTENSIONS }
    val isPdfFile = remember(ext, previewInfo, cachedFile) {
        ext == "pdf" ||
            previewInfo?.pdfPreviewUrl != null ||
            cachedFile?.extension?.equals("pdf", ignoreCase = true) == true
    }

    val hasOnlinePreview = previewInfo?.onlinePreviewUrl != null || previewInfo?.aliyunOfficeTokenJson != null
    val hasNativePreview = isImage || isText || previewInfo?.pdfPreviewUrl != null || ext == "pdf"
    val showModeSwitch = hasOnlinePreview && hasNativePreview && !isImage

    LaunchedEffect(uploadId, initialFileName, isDemoMode, reloadNonce) {
        isResolving = true
        errorMessage = null
        cachedFile = null
        cachedOriginalFile = null
        decodedImage = null
        textContent = null

        if (isDemoMode || uploadId <= 0L) {
            previewInfo = AttachmentPreviewInfo(
                uploadId = uploadId,
                fileName = initialFileName,
                fileType = ext.uppercase().ifBlank { "FILE" },
            )
            isResolving = false
            return@LaunchedEffect
        }

        val resolved = onResolvePreview(uploadId, initialFileName).getOrElse { err ->
            errorMessage = err.message ?: "无法获取文件预览信息"
            isResolving = false
            return@LaunchedEffect
        }
        previewInfo = resolved

        val resolvedExt = resolved.fileName.substringAfterLast('.', "").lowercase()
        val resolvedIsImage = resolvedExt in IMAGE_EXTENSIONS || resolved.fileType.equals("image", ignoreCase = true)
        val resolvedIsText = resolvedExt in TEXT_EXTENSIONS
        val resolvedHasOnline = resolved.onlinePreviewUrl != null || resolved.aliyunOfficeTokenJson != null

        // Native rendering (image / converted PDF / text) is crisp and instant; switch to WebView if only online preview exists
        val defaultMode = if (resolvedIsImage || resolvedIsText || resolved.pdfPreviewUrl != null || resolvedExt == "pdf") {
            ViewerMode.NATIVE
        } else if (resolvedHasOnline) {
            ViewerMode.ONLINE
        } else {
            ViewerMode.NATIVE
        }
        activeMode = defaultMode

        val downloadTargetUrl = when {
            resolvedIsImage -> resolved.directDownloadUrl
            resolvedIsText -> resolved.directDownloadUrl
            resolvedExt == "pdf" -> resolved.pdfPreviewUrl ?: resolved.directDownloadUrl
            resolved.pdfPreviewUrl != null -> resolved.pdfPreviewUrl
            else -> null
        }

        if (downloadTargetUrl != null) {
            val safeName = resolved.fileName.replace(Regex("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]"), "_")
            val cacheDir = File(context.cacheDir, "preview_cache").apply { mkdirs() }
            val isConvertedPdf = !resolvedIsImage && !resolvedIsText && resolvedExt != "pdf" && resolved.pdfPreviewUrl != null
            val destName = if (isConvertedPdf) "${uploadId}_converted.pdf" else "${uploadId}_$safeName"
            val dest = File(cacheDir, destName)
            val dlResult = if (dest.exists() && dest.length() > 0L) {
                Result.success(dest)
            } else {
                onDownloadFile(downloadTargetUrl, dest)
            }
            dlResult.onSuccess { file ->
                cachedFile = file
                if (!isConvertedPdf) cachedOriginalFile = file
                if (resolvedIsImage) {
                    decodedImage = withContext(Dispatchers.IO) { decodeSampledBitmap(file, 2560)?.asImageBitmap() }
                    if (decodedImage == null) {
                        errorMessage = "图片格式不受支持或已损坏"
                    }
                } else if (resolvedIsText) {
                    textContent = withContext(Dispatchers.IO) {
                        if (file.length() > 2 * 1024 * 1024) {
                            file.inputStream().bufferedReader().use { r ->
                                val buf = CharArray(8192)
                                val sb = StringBuilder()
                                var total = 0
                                while (total < 120_000) {
                                    val n = r.read(buf)
                                    if (n <= 0) break
                                    sb.append(buf, 0, n)
                                    total += n
                                }
                                sb.append("\n\n…（文件超过 2 MB，已截断显示前段内容）")
                                sb.toString()
                            }
                        } else {
                            file.readText(Charsets.UTF_8)
                        }
                    }
                }
            }.onFailure { err ->
                if (!resolvedHasOnline) {
                    errorMessage = err.message ?: "下载文件失败"
                } else {
                    activeMode = ViewerMode.ONLINE
                }
            }
        }
        isResolving = false
    }

    fun openExternal() {
        scope.launch {
            val existing = cachedOriginalFile
            if (existing != null && existing.exists()) {
                openLocalFileExternally(context, existing, displayFileName)
                return@launch
            }
            val url = previewInfo?.directDownloadUrl ?: previewInfo?.onlinePreviewUrl
            if (url.isNullOrBlank()) {
                Toast.makeText(context, "暂无可用下载链接", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(context, "正在准备外部打开…", Toast.LENGTH_SHORT).show()
            val safeName = displayFileName.replace(Regex("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]"), "_")
            val dest = File(File(context.cacheDir, "preview_cache"), "${uploadId}_$safeName")
            onDownloadFile(url, dest).fold(
                onSuccess = { downloaded ->
                    cachedOriginalFile = downloaded
                    openLocalFileExternally(context, downloaded, displayFileName)
                },
                onFailure = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure {
                        Toast.makeText(context, "未找到可打开该文件的系统应用", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Swiss Editorial Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrostedIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayFileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val metaLine = buildString {
                        append(ext.uppercase().ifBlank { previewInfo?.fileType?.uppercase() ?: "FILE" })
                        previewInfo?.sizeBytes?.takeIf { it > 0L }?.let {
                            append(" · ")
                            append(TimeFormatUtils.formatFileSize(it))
                        }
                        if (isDemoMode || uploadId <= 0L) append(" · 演示预览")
                    }
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FrostedIconButton(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "外部应用打开",
                    onClick = ::openExternal,
                )
            }

            if (showModeSwitch) {
                ExpressiveGlidingSegmentedBar(
                    items = ViewerMode.entries,
                    selected = activeMode,
                    onSelect = { selected ->
                        activeMode = selected
                        if (selected == ViewerMode.NATIVE && cachedFile == null && !isResolving) {
                            val pdfUrl = previewInfo?.pdfPreviewUrl ?: previewInfo?.directDownloadUrl
                            if (pdfUrl != null) {
                                scope.launch {
                                    isResolving = true
                                    val dest = File(File(context.cacheDir, "preview_cache"), "${uploadId}_converted.pdf")
                                    onDownloadFile(pdfUrl, dest).onSuccess {
                                        cachedFile = it
                                    }.onFailure { err ->
                                        errorMessage = err.message ?: "加载原生 PDF 失败"
                                    }
                                    isResolving = false
                                }
                            }
                        }
                    },
                    labelProvider = { it.label },
                    height = 36.dp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                when {
                    isResolving -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LoadingIndicator(modifier = Modifier.size(34.dp))
                            Text(
                                text = "正在加载内置浏览器…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    isDemoMode || uploadId <= 0L -> {
                        DemoAttachmentPreviewCard(
                            fileName = displayFileName,
                            ext = ext,
                            onBack = onBack,
                        )
                    }

                    errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = errorMessage ?: "预览失败",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(onClick = { reloadNonce++ }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("重试")
                                }
                                Button(onClick = ::openExternal) {
                                    Text("外部打开")
                                }
                            }
                        }
                    }

                    isImage && decodedImage != null -> {
                        ZoomableImageViewer(bitmap = decodedImage!!)
                    }

                    activeMode == ViewerMode.ONLINE && hasOnlinePreview -> {
                        OnlineDocumentWebView(
                            previewUrl = previewInfo?.onlinePreviewUrl,
                            aliyunTokenJson = previewInfo?.aliyunOfficeTokenJson,
                            canFallbackNative = hasNativePreview,
                            onFallbackToNative = { activeMode = ViewerMode.NATIVE },
                            onOpenExternal = ::openExternal,
                            onSyncCookies = onSyncWebViewCookies,
                        )
                    }

                    isText && textContent != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            FrostedGlassCard(
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = textContent.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    (isPdfFile || cachedFile?.extension?.equals("pdf", ignoreCase = true) == true) && cachedFile != null -> {
                        NativePdfViewer(file = cachedFile!!)
                    }

                    else -> {
                        UnsupportedFileCard(
                            fileName = displayFileName,
                            sizeBytes = previewInfo?.sizeBytes ?: 0L,
                            onOpenExternal = ::openExternal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImageViewer(bitmap: ImageBitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    offset = if (nextScale <= 1.02f) {
                        Offset.Zero
                    } else {
                        offset + pan
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "双指缩放 · 双击切换比例 (${(scale * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NativePdfViewer(file: File) {
    val rendererHolder = remember(file.absolutePath) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            Triple(pfd, renderer, Mutex())
        }.getOrNull()
    }

    DisposableEffect(rendererHolder) {
        onDispose {
            runCatching { rendererHolder?.second?.close() }
            runCatching { rendererHolder?.first?.close() }
        }
    }

    if (rendererHolder == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "PDF 文件解析失败，可点击右上角使用系统应用打开",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val (_, renderer, renderMutex) = rendererHolder
    val pageCount = renderer.pageCount
    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceIn(1, maxOf(1, pageCount)) }
    }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (zoomScale * zoom).coerceIn(1f, 3.2f)
                    zoomScale = next
                    panX = if (next <= 1.02f) 0f else (panX + pan.x).coerceIn(-600f, 600f)
                }
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panX
                },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(pageCount, key = { it }) { pageIndex ->
                var pageBitmap by remember(file.absolutePath, pageIndex) { mutableStateOf<ImageBitmap?>(null) }
                var aspect by remember(file.absolutePath, pageIndex) { mutableFloatStateOf(0.707f) }

                LaunchedEffect(file.absolutePath, pageIndex) {
                    val result = withContext(Dispatchers.IO) {
                        renderMutex.withLock {
                            runCatching {
                                renderer.openPage(pageIndex).use { page ->
                                    val ratio = page.width.toFloat() / page.height.coerceAtLeast(1).toFloat()
                                    val targetW = 1440.coerceAtMost(page.width * 2).coerceAtLeast(900)
                                    val targetH = (targetW / ratio).toInt().coerceAtLeast(1)
                                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(AndroidColor.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    ratio to bmp.asImageBitmap()
                                }
                            }.getOrNull()
                        }
                    }
                    if (result != null) {
                        aspect = result.first
                        pageBitmap = result.second
                    }
                }

                FrostedGlassCard(
                    shape = RoundedCornerShape(14.dp),
                    containerColor = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect),
                ) {
                    val bmp = pageBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "第 ${pageIndex + 1} 页",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }

        if (pageCount > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .border(
                        width = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) {
                Text(
                    text = "$currentPage / $pageCount 页",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private class ViewerJsBridge(
    private val onReadyCallback: () -> Unit,
    private val onErrorCallback: (String) -> Unit,
    private val onBlankCallback: (String) -> Unit,
) {
    @JavascriptInterface
    fun onReady() {
        onReadyCallback()
    }

    @JavascriptInterface
    fun onError(message: String?) {
        onErrorCallback(message?.ifBlank { null } ?: "在线预览组件初始化失败")
    }

    @JavascriptInterface
    fun onBlankDetected(reason: String?) {
        onBlankCallback(reason?.ifBlank { null } ?: "页面未渲染可见内容")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OnlineDocumentWebView(
    previewUrl: String?,
    aliyunTokenJson: String?,
    canFallbackNative: Boolean,
    onFallbackToNative: () -> Unit,
    onOpenExternal: () -> Unit,
    onSyncCookies: () -> Unit,
) {
    var progress by remember { mutableIntStateOf(15) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webError by remember { mutableStateOf<String?>(null) }
    var sdkReady by remember { mutableStateOf(false) }
    var useWpsIframeFallback by remember(previewUrl, aliyunTokenJson) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        onSyncCookies()
    }

    // Watchdog: if page never renders anything meaningful within 12s, surface clear fallback instead of blank white screen
    LaunchedEffect(previewUrl, aliyunTokenJson, useWpsIframeFallback, webError) {
        if (webError != null || sdkReady) return@LaunchedEffect
        delay(12_000L)
        if (!sdkReady && webError == null) {
            webViewRef?.evaluateJavascript(
                """
                (function() {
                    var body = document.body;
                    var textLen = body ? (body.innerText || '').trim().length : 0;
                    var hasMedia = !!document.querySelector('iframe,canvas,svg,img,embed,object,video');
                    return textLen + ':' + (hasMedia ? 1 : 0);
                })();
                """.trimIndent(),
            ) { raw ->
                val cleaned = raw?.trim('"', ' ', '\n').orEmpty()
                val parts = cleaned.split(':')
                val textLen = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val hasMedia = parts.getOrNull(1) == "1"
                DebugLogStore.log("VIEWER", "online watchdog check textLen=$textLen hasMedia=$hasMedia iframeMode=$useWpsIframeFallback")
                if (textLen < 8 && !hasMedia) {
                    if (aliyunTokenJson.isNullOrBlank() && !previewUrl.isNullOrBlank() && !useWpsIframeFallback) {
                        useWpsIframeFallback = true
                        progress = 20
                    } else {
                        webError = "在线预览长时间无响应（页面内容为空），可切回原生阅读或用系统浏览器打开"
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                removeJavascriptInterface("XmuViewerBridge")
                destroy()
            }
            webViewRef = null
        }
    }

    val loadIntoWebView: (WebView, Boolean) -> Unit = { wv, iframeMode ->
        onSyncCookies()
        val hostPrefix = previewUrl?.let { raw ->
            runCatching {
                val u = Uri.parse(raw)
                val sch = u.scheme
                val h = u.host
                if (!sch.isNullOrBlank() && !h.isNullOrBlank()) "$sch://$h" else null
            }.getOrNull()
        }
        val extraHeaders = mapOf("Referer" to "https://c-mobile.xmu.edu.cn/")
        DebugLogStore.log("VIEWER", "loadOnline aliyun=${!aliyunTokenJson.isNullOrBlank()} host=$hostPrefix iframeMode=$iframeMode")

        if (!aliyunTokenJson.isNullOrBlank()) {
            val html = buildAliyunOfficeHtml(aliyunTokenJson)
            if (html != null) {
                wv.loadDataWithBaseURL("https://c-mobile.xmu.edu.cn/", html, "text/html", "UTF-8", null)
            } else if (!previewUrl.isNullOrBlank()) {
                wv.loadUrl(previewUrl, extraHeaders)
            } else {
                webError = "未获取到在线预览地址"
            }
        } else if (!previewUrl.isNullOrBlank()) {
            if (iframeMode) {
                wv.loadDataWithBaseURL(
                    "https://c-mobile.xmu.edu.cn/",
                    buildWpsIframeHostHtml(previewUrl),
                    "text/html",
                    "UTF-8",
                    null,
                )
            } else {
                wv.loadUrl(previewUrl, extraHeaders)
            }
        } else {
            webError = "未获取到在线预览地址"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (progress in 1..99 && webError == null) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    onSyncCookies()
                    WebView(ctx).apply {
                        webViewRef = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = XmuAuthClient.MOBILE_UA
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        addJavascriptInterface(
                            ViewerJsBridge(
                                onReadyCallback = {
                                    post {
                                        sdkReady = true
                                        progress = 100
                                    }
                                },
                                onErrorCallback = { msg ->
                                    post {
                                        DebugLogStore.log("VIEWER", "JS bridge error: $msg", "ERR")
                                        webError = msg
                                    }
                                },
                                onBlankCallback = { reason ->
                                    post {
                                        DebugLogStore.log("VIEWER", "blank detected: $reason iframeMode=$useWpsIframeFallback", "WARN")
                                        if (aliyunTokenJson.isNullOrBlank() && !previewUrl.isNullOrBlank() && !useWpsIframeFallback) {
                                            useWpsIframeFallback = true
                                            progress = 20
                                        } else {
                                            webError = "在线预览未能渲染文档内容（$reason），建议切回原生阅读或用系统浏览器打开"
                                        }
                                    }
                                },
                            ),
                            "XmuViewerBridge",
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }

                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                if (cm != null && cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                    DebugLogStore.log("VIEWER", "console.error: ${cm.message()}", "WARN")
                                }
                                return super.onConsoleMessage(cm)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val scheme = request?.url?.scheme?.lowercase().orEmpty()
                                return scheme !in setOf("http", "https")
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                progress = 100
                                if (aliyunTokenJson.isNullOrBlank() && url != null && url != "about:blank") {
                                    view?.postDelayed(
                                        {
                                            if (webViewRef == null || webError != null) return@postDelayed
                                            view.evaluateJavascript(
                                                """
                                                (function() {
                                                    var body = document.body;
                                                    var textLen = body ? (body.innerText || '').trim().length : 0;
                                                    var visual = document.querySelector('iframe,canvas,svg,img,embed,object,video');
                                                    if (textLen < 8 && !visual && window.XmuViewerBridge) {
                                                        window.XmuViewerBridge.onBlankDetected('页面返回空文档');
                                                    } else if (window.XmuViewerBridge) {
                                                        window.XmuViewerBridge.onReady();
                                                    }
                                                })();
                                                """.trimIndent(),
                                                null,
                                            )
                                        },
                                        2600L,
                                    )
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    val desc = error?.description?.toString().orEmpty()
                                    DebugLogStore.log("VIEWER", "mainFrame error=${error?.errorCode} desc=$desc", "ERR")
                                    webError = "在线预览加载失败（${desc.ifBlank { "网络不可达或需要校内网络" }}）"
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    val code = errorResponse?.statusCode ?: 0
                                    DebugLogStore.log("VIEWER", "mainFrame HTTP $code", "ERR")
                                    if (code >= 400) {
                                        webError = "在线预览服务返回异常状态（HTTP $code），可切回原生阅读或外部打开"
                                    }
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?,
                            ) {
                                handler?.cancel()
                                DebugLogStore.log("VIEWER", "SSL error primary=${error?.primaryError}", "ERR")
                                webError = "预览站点证书校验未通过（可能为校内私有域名），建议使用外部浏览器打开或切回原生阅读"
                            }
                        }

                        loadIntoWebView(this, useWpsIframeFallback)
                    }
                },
                update = { wv ->
                    val tagKey = "$previewUrl|$aliyunTokenJson|$useWpsIframeFallback"
                    if (wv.tag != tagKey && webError == null) {
                        wv.tag = tagKey
                        if (useWpsIframeFallback || wv.url == null) {
                            loadIntoWebView(wv, useWpsIframeFallback)
                        }
                    }
                },
            )
        }

        if (webError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                FrostedGlassCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("在线预览暂不可用", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = webError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (canFallbackNative) {
                                Button(onClick = onFallbackToNative, modifier = Modifier.weight(1f)) {
                                    Text("切回原生阅读")
                                }
                            } else if (!previewUrl.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        runCatching { uriHandler.openUri(previewUrl) }.onFailure {
                                            Toast.makeText(context, "无法调起系统浏览器", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("浏览器打开")
                                }
                            }
                            FilledTonalButton(onClick = onOpenExternal, modifier = Modifier.weight(1f)) {
                                Text("外部打开")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoAttachmentPreviewCard(
    fileName: String,
    ext: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        FrostedGlassCard(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("内置浏览器（演示数据）", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "文件名：$fileName\n格式：${ext.uppercase().ifBlank { "未知" }}\n\n登录后点击任意课程课件或作业附件，将自动调用：\n• 图片：原生手势缩放原图浏览\n• PDF：原生高分逐页渲染\n• Word / PPT / Excel：学校 WPS / 阿里云在线预览页（同步会话凭据）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("返回")
                }
            }
        }
    }
}

@Composable
private fun UnsupportedFileCard(
    fileName: String,
    sizeBytes: Long,
    onOpenExternal: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        FrostedGlassCard(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("暂不支持内置预览该格式", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "$fileName" + if (sizeBytes > 0) " (${TimeFormatUtils.formatFileSize(sizeBytes)})" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onOpenExternal,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("下载并使用系统应用打开")
                }
            }
        }
    }
}

private fun buildAliyunOfficeHtml(tokenJson: String): String? {
    val obj = runCatching { JSONObject(tokenJson) }.getOrNull() ?: return null
    val rawUrl = obj.optString("PreviewURL")
        .ifBlank { obj.optString("WebofficeURL") }
        .ifBlank { obj.optString("WebofficeUrl") }
        .ifBlank { obj.optString("EditURL") }
    val token = obj.optString("AccessToken")
    if (rawUrl.isBlank() || token.isBlank()) return null
    val finalUrl = if (rawUrl.contains("lang=", ignoreCase = true)) {
        rawUrl
    } else {
        val sep = if (rawUrl.contains('?')) "&" else "?"
        "${rawUrl}${sep}lang=zh-CN"
    }
    val safeUrl = JSONObject.quote(finalUrl)
    val safeToken = JSONObject.quote(token)
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=3,user-scalable=yes" />
            <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: transparent; }
                #mount { position: fixed; inset: 0; width: 100%; height: 100%; }
                #mount iframe { width: 100% !important; height: 100% !important; border: 0 !important; display: block !important; }
            </style>
        </head>
        <body>
            <div id="mount"></div>
            <script src="https://g.alicdn.com/IMM/office-js/1.1.15/aliyun-web-office-sdk.min.js"
                    onload="scheduleInit(0)"
                    onerror="reportErr('阿里云在线预览脚本加载失败')"></script>
            <script>
                var _officeOpened = false;
                function reportErr(msg) {
                    if (window.XmuViewerBridge) {
                        window.XmuViewerBridge.onError(String(msg || '在线预览加载失败'));
                    }
                }
                function scheduleInit(attempt) {
                    var mount = document.getElementById('mount');
                    if (!mount || ((mount.clientWidth === 0 || mount.clientHeight === 0) && attempt < 15)) {
                        requestAnimationFrame(function() { scheduleInit(attempt + 1); });
                        return;
                    }
                    initOffice(mount);
                }
                function initOffice(mount) {
                    try {
                        if (!window.aliyun || !window.aliyun.config) {
                            reportErr('阿里云 WebOffice SDK 未就绪');
                            return;
                        }
                        var ins = window.aliyun.config({
                            mount: mount,
                            url: $safeUrl,
                            mode: 'normal',
                            commandBars: [{ cmbId: 'WPPMobileTimeStamp', attributed: { visible: false } }],
                            pptOptions: { isShowComment: false },
                            refreshToken: function() {
                                return Promise.resolve({ token: $safeToken, timeout: 1980000 });
                            }
                        });
                        ins.setToken({ token: $safeToken, timeout: 1980000 });
                        ins.on('fileOpen', function() {
                            _officeOpened = true;
                            if (window.XmuViewerBridge) window.XmuViewerBridge.onReady();
                        });
                        ins.on('error', function(err) {
                            reportErr((err && (err.message || err.reason)) || '文档在线打开失败');
                        });
                        setTimeout(function() {
                            if (!_officeOpened && !document.querySelector('#mount iframe')) {
                                reportErr('阿里云文档渲染超时（未挂载视图）');
                            }
                        }, 10000);
                    } catch (e) {
                        reportErr(e && e.message ? e.message : '在线预览异常');
                    }
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun buildWpsIframeHostHtml(wpsUrl: String): String {
    val safeUrl = JSONObject.quote(wpsUrl)
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=3,user-scalable=yes" />
            <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: transparent; }
                #office-iframe { position: fixed; inset: 0; width: 100%; height: 100%; border: 0; display: block; }
            </style>
        </head>
        <body>
            <iframe id="office-iframe" allowfullscreen="true" allow="clipboard-read; clipboard-write"></iframe>
            <script>
                var frame = document.getElementById('office-iframe');
                var wpsOpened = false;
                window.addEventListener('message', function(ev) {
                    try {
                        var data = typeof ev.data === 'string' ? JSON.parse(ev.data) : ev.data;
                        if (!data || !data.eventName) return;
                        if (data.eventName === 'ready' || data.eventName === 'open.result' || data.eventName === 'api.ready') {
                            wpsOpened = true;
                            if (window.XmuViewerBridge) window.XmuViewerBridge.onReady();
                        } else if (data.eventName === 'error') {
                            if (window.XmuViewerBridge) {
                                window.XmuViewerBridge.onError((data.data && data.data.reason) || 'WPS 在线预览返回错误');
                            }
                        }
                    } catch (_) {}
                });
                frame.onload = function() {
                    setTimeout(function() {
                        if (!wpsOpened && window.XmuViewerBridge) {
                            window.XmuViewerBridge.onReady();
                        }
                    }, 1200);
                };
                frame.onerror = function() {
                    if (window.XmuViewerBridge) window.XmuViewerBridge.onError('WPS 预览框架加载失败');
                };
                frame.src = $safeUrl;
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun decodeSampledBitmap(file: File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}

private fun openLocalFileExternally(context: Context, file: File, displayName: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val guessedMime = URLConnection.guessContentTypeFromName(displayName)
        ?: when (displayName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "*/*"
        }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, guessedMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "选择打开方式"))
    }.onFailure {
        Toast.makeText(context, "未找到可打开此文件的应用", Toast.LENGTH_SHORT).show()
    }
}
