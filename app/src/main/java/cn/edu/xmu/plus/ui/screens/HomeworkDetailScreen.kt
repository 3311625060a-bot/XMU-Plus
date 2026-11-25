package cn.edu.xmu.plus.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.xmu.plus.data.DemoData
import cn.edu.xmu.plus.data.HomeworkDetailUiState
import cn.edu.xmu.plus.data.HomeworkItem
import cn.edu.xmu.plus.data.HomeworkStatus
import cn.edu.xmu.plus.data.PickedLocalFile
import cn.edu.xmu.plus.data.TimeFormatUtils
import cn.edu.xmu.plus.ui.components.AmbientGlowBackground
import cn.edu.xmu.plus.ui.components.DetailOverlayClipShape
import cn.edu.xmu.plus.ui.components.FrostedGlassCard
import cn.edu.xmu.plus.ui.components.FrostedIconButton
import cn.edu.xmu.plus.ui.components.SheetGrabber
import cn.edu.xmu.plus.ui.components.optionalSharedBounds
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun HomeworkDetailScreen(
    homeworkId: Long,
    sourceTag: String,
    isDemoMode: Boolean,
    detailState: HomeworkDetailUiState?,
    fallbackHomework: HomeworkItem?,
    onLoadHomeworkDetail: (Long) -> Unit,
    onOpenAttachment: (Long, String) -> Unit,
    onResolvePickedFile: (Uri) -> PickedLocalFile,
    onSubmitHomework: suspend (Long, String, List<PickedLocalFile>, Long?, (Float, String) -> Unit) -> Result<Unit>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
) {
    val containerModifier = if (sourceTag == "home" || sourceTag == "list") {
        Modifier
            .fillMaxSize()
            .optionalSharedBounds(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                key = "hw_card_${sourceTag}_${homeworkId}",
                clipShape = DetailOverlayClipShape,
            )
    } else {
        Modifier.fillMaxSize()
    }

    Surface(
        modifier = containerModifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientGlowBackground(animate = false)

            HomeworkDetailPane(
                homeworkId = homeworkId,
                isDemoMode = isDemoMode,
                detailState = detailState,
                fallbackHomework = fallbackHomework,
                onLoadHomeworkDetail = onLoadHomeworkDetail,
                onOpenAttachment = onOpenAttachment,
                onResolvePickedFile = onResolvePickedFile,
                onSubmitHomework = onSubmitHomework,
                onBack = onBack,
                showBackButton = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeworkDetailPane(
    homeworkId: Long,
    isDemoMode: Boolean,
    detailState: HomeworkDetailUiState?,
    fallbackHomework: HomeworkItem?,
    onLoadHomeworkDetail: (Long) -> Unit,
    onOpenAttachment: (Long, String) -> Unit,
    onResolvePickedFile: (Uri) -> PickedLocalFile,
    onSubmitHomework: suspend (Long, String, List<PickedLocalFile>, Long?, (Float, String) -> Unit) -> Result<Unit>,
    onBack: (() -> Unit)? = null,
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(homeworkId) {
        onLoadHomeworkDetail(homeworkId)
    }

    val homework = detailState?.detail
        ?: fallbackHomework
        ?: remember(homeworkId) {
            DemoData.homeworks.find { it.id == homeworkId } ?: DemoData.homeworks.first()
        }
    val isLoading = detailState?.isLoading == true
    var showSubmitSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (showBackButton) Modifier.statusBarsPadding() else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBackButton && onBack != null) {
                        FrostedIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            onClick = onBack,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = homework.courseName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = buildString {
                                append(homework.status.label)
                                if (homework.isGroupHomework) append(" · 小组作业")
                                if (homework.submissionCount > 0) append(" · 共 ${homework.submissionCount} 次提交")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (showBackButton) Modifier.navigationBarsPadding() else Modifier)
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "截止：${homework.deadlineText}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum",
                            ),
                        )
                        Text(
                            text = homework.remainingText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { showSubmitSheet = true },
                        enabled = homework.allowResubmit || homework.status == HomeworkStatus.PENDING,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (homework.status == HomeworkStatus.PENDING) "提交作业" else "修改提交",
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = homework.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            FrostedGlassCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("作业要求", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = homework.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (homework.attachmentFiles.isNotEmpty() || homework.attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "参考附件（点击预览/下载）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (homework.attachmentFiles.isNotEmpty()) {
                            homework.attachmentFiles.forEach { file ->
                                AssistChip(
                                    onClick = { onOpenAttachment(file.id, file.name) },
                                    label = {
                                        val sizeLabel = if (file.sizeBytes > 0) {
                                            " (${TimeFormatUtils.formatFileSize(file.sizeBytes)})"
                                        } else {
                                            ""
                                        }
                                        Text("${file.name}$sizeLabel")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.AttachFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        } else {
                            homework.attachments.forEach { fileName ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(fileName) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.AttachFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (homework.submittedAt != null || homework.submittedContent != null || homework.submittedFiles.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = homework.submittedAt?.let { "已提交 · $it" } ?: "已提交记录",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        homework.submittedContent?.takeIf { it.isNotBlank() }?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        if (homework.submittedAttachmentFiles.isNotEmpty()) {
                            homework.submittedAttachmentFiles.forEach { f ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "📎 ${f.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onOpenAttachment(f.id, f.name) },
                                )
                            }
                        } else {
                            homework.submittedFiles.forEach { f ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "📎 $f",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (homework.score != null || homework.duplicateRate != null) {
                FrostedGlassCard(
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Grade,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (homework.score != null) "批改得分" else "批改与查重信息",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            homework.score?.let { sc ->
                                val fullSuffix = homework.fullScore
                                    ?.takeIf { it.isNotBlank() && !sc.contains("/") }
                                    ?.let { " / $it" }
                                    .orEmpty()
                                Text(
                                    text = "$sc$fullSuffix",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 24.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontFeatureSettings = "tnum",
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (!homework.duplicateRate.isNullOrBlank() ||
                            !homework.graderName.isNullOrBlank() ||
                            !homework.gradedAt.isNullOrBlank()
                        ) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val metaItems = listOfNotNull(
                                homework.duplicateRate?.takeIf { it.isNotBlank() }?.let { "查重 $it" },
                                homework.graderName?.takeIf { it.isNotBlank() }?.let { "批改：$it" },
                                homework.gradedAt?.takeIf { it.isNotBlank() },
                            )
                            Text(
                                text = metaItems.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        homework.teacherComment?.let { comment ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = comment,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSubmitSheet) {
        HomeworkSubmitSheet(
            homework = homework,
            isDemoMode = isDemoMode,
            onResolvePickedFile = onResolvePickedFile,
            onSubmit = { comment, files, onProgress ->
                onSubmitHomework(homework.id, comment, files, homework.submissionId, onProgress)
            },
            onDismiss = { showSubmitSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeworkSubmitSheet(
    homework: HomeworkItem,
    isDemoMode: Boolean,
    onResolvePickedFile: (Uri) -> PickedLocalFile,
    onSubmit: suspend (String, List<PickedLocalFile>, (Float, String) -> Unit) -> Result<Unit>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var comment by remember { mutableStateOf(homework.submittedContent.orEmpty()) }
    val pickedFiles = remember { mutableStateListOf<PickedLocalFile>() }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            val resolved = onResolvePickedFile(uri)
            if (pickedFiles.none { it.uriString == resolved.uriString }) {
                pickedFiles.add(resolved)
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        uris.forEach { uri ->
            val resolved = onResolvePickedFile(uri)
            if (pickedFiles.none { it.uriString == resolved.uriString }) {
                pickedFiles.add(resolved)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetGrabber() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (homework.submissionId != null) "修改/重交作业" else "提交作业",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = homework.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(targetState = submitted, label = "hwSubmitState") { isDone ->
                if (isDone) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(52.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isDemoMode) "作业提交成功（演示模式）" else "作业提交成功",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isDemoMode) "登录后可真实上传附件并提交至畅课" else "已同步最新提交记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onDismiss) {
                                Text("完成")
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = { Text("作答说明 / 备注（可选）") },
                            minLines = 3,
                            maxLines = 5,
                            enabled = !uploading,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pickedFiles.forEach { file ->
                                AssistChip(
                                    onClick = { if (!uploading) pickedFiles.remove(file) },
                                    label = { Text("${file.name} (${TimeFormatUtils.formatFileSize(file.sizeBytes)})") },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "移除",
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                )
                            }
                            FilledTonalButton(
                                onClick = { docPickerLauncher.launch(arrayOf("*/*")) },
                                enabled = !uploading,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("选择文件")
                            }
                            FilledTonalButton(
                                onClick = {
                                    mediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                enabled = !uploading,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("相册图片")
                            }
                        }

                        if (errorMsg != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errorMsg.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }

                        if (uploading) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    LoadingIndicator(modifier = Modifier.size(26.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (statusText.isNotBlank()) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    errorMsg = null
                                    uploading = true
                                    progress = 0.05f
                                    statusText = "准备提交…"
                                    val res = onSubmit(comment.trim(), pickedFiles.toList()) { pct, msg ->
                                        progress = pct
                                        statusText = msg
                                    }
                                    uploading = false
                                    res.fold(
                                        onSuccess = { submitted = true },
                                        onFailure = { err ->
                                            errorMsg = err.message ?: "提交失败，请稍后重试"
                                        },
                                    )
                                }
                            },
                            enabled = !uploading && (comment.isNotBlank() || pickedFiles.isNotEmpty()),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            Text(if (uploading) "正在上传并提交…" else "确认提交作业")
                        }
                    }
                }
            }
        }
    }
}
