package com.classpulse.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.classpulse.app.data.model.CreateAssignmentRequest
import com.classpulse.app.data.model.CreateAttendanceRequest
import com.classpulse.app.data.model.CreateQuestionRequest
import com.classpulse.app.ui.components.ClassPulseField
import com.classpulse.app.ui.components.SecondaryButton
import com.classpulse.app.ui.components.StatusBadge
import com.classpulse.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

@Composable
fun CreateAttendanceDialog(
    onDismiss: () -> Unit,
    onSubmit: (CreateAttendanceRequest, (String?) -> Unit) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableIntStateOf(10) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(26.dp),
        containerColor = Cream,
        title = { Text("发起课堂签到", style = MaterialTheme.typography.headlineMedium, color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ClassPulseField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = "签到标题",
                    placeholder = "例如：第 5 讲 · 图与遍历",
                    leadingIcon = { Icon(Icons.Outlined.Campaign, null, tint = Teal) },
                )
                Spacer(Modifier.height(18.dp))
                Text("有效时长", style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(8.dp))
                val options = listOf(1 to "1 分钟", 5 to "5 分钟", 10 to "10 分钟", 15 to "15 分钟", 30 to "30 分钟")
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    options.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (value, label) ->
                                FilterChip(
                                    selected = minutes == value,
                                    onClick = { minutes = value },
                                    label = { Text(label) },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Teal,
                                        selectedLabelColor = Cream,
                                        containerColor = Paper,
                                        labelColor = InkSoft,
                                    ),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "发起后会生成 6 位随机签到码，学生在有效期内输入即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                InlineDialogError(error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    if (title.isBlank()) {
                        error = "请输入签到标题"
                        return@TextButton
                    }
                    submitting = true
                    onSubmit(CreateAttendanceRequest(title.trim(), minutes)) { submitError ->
                        submitting = false
                        if (submitError == null) onDismiss() else error = submitError
                    }
                },
            ) {
                Text(if (submitting) "发起中…" else "发起签到", color = if (submitting) Muted else Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消", color = Muted) } },
    )
}

@Composable
fun CreateQuestionDialog(
    onDismiss: () -> Unit,
    onSubmit: (CreateQuestionRequest, (String?) -> Unit) -> Unit,
) {
    var questionType by rememberSaveable { mutableStateOf("SINGLE_CHOICE") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "", "", "")) }
    var correctKeys by remember { mutableStateOf(setOf("A")) }
    var fillAnswer by rememberSaveable { mutableStateOf("") }
    var deadlineMinutes by rememberSaveable { mutableLongStateOf(24 * 60L) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val isChoice = questionType != "FILL_BLANK"
    val isMultiple = questionType == "MULTIPLE_CHOICE"
    val presetMinutes = listOf(15L to "15 分钟", 30L to "30 分钟", 60L to "1 小时", 1440L to "1 天", 4320L to "3 天")

    Dialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .imePadding(),
            shape = RoundedCornerShape(26.dp),
            color = Cream,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text("发布课堂问题", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text("系统按参考答案自动判分；多选题全对才计分", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SegmentedChoice(
                        options = listOf(
                            "SINGLE_CHOICE" to "单选题",
                            "MULTIPLE_CHOICE" to "多选题",
                            "FILL_BLANK" to "填空题",
                        ),
                        selected = questionType,
                        onSelect = { selected ->
                            questionType = selected
                            error = null
                            if (selected == "SINGLE_CHOICE" && correctKeys.size != 1) {
                                correctKeys = setOf(correctKeys.firstOrNull() ?: "A")
                            }
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    ClassPulseField(
                        value = prompt,
                        onValueChange = { prompt = it; error = null },
                        label = "题干",
                        placeholder = if (isChoice) "输入选择题题干" else "输入填空题题干",
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = Teal) },
                        singleLine = false,
                        minLines = 2,
                    )
                    Spacer(Modifier.height(14.dp))
                    if (isChoice) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("选项", style = MaterialTheme.typography.titleSmall, color = Ink)
                                Text(
                                    if (isMultiple) "点击字母选择正确项，可多选" else "点击左侧字母设为正确答案",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Muted,
                                )
                            }
                            if (options.size < 6) {
                                TextButton(onClick = { options = options + "" }) {
                                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                                    Text("增加")
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        options.forEachIndexed { index, value ->
                            val key = ('A'.code + index).toChar().toString()
                            val selected = key in correctKeys
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) Teal else Paper)
                                        .clickable {
                                            correctKeys = if (isMultiple) {
                                                if (selected) correctKeys - key else correctKeys + key
                                            } else {
                                                setOf(key)
                                            }
                                            error = null
                                        }
                                        .padding(1.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        key,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) Cream else InkSoft,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                ClassPulseField(
                                    value = value,
                                    onValueChange = { newValue ->
                                        options = options.toMutableList().also { it[index] = newValue }
                                        error = null
                                    },
                                    label = "选项 $key",
                                    modifier = Modifier.weight(1f),
                                )
                                if (options.size > 2) {
                                    IconButton(onClick = {
                                        options = options.toMutableList().also { it.removeAt(index) }
                                        val validKeys = options.indices
                                            .map { ('A'.code + it).toChar().toString() }
                                            .toSet()
                                        correctKeys = correctKeys.filter { it in validKeys }.toSet()
                                        if (questionType == "SINGLE_CHOICE" && correctKeys.isEmpty()) {
                                            correctKeys = setOf("A")
                                        }
                                    }) {
                                        Icon(Icons.Outlined.Close, null, tint = Muted, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        StatusBadge(
                            text = "正确答案 ${correctKeys.sorted().joinToString(",").ifBlank { "未选择" }}",
                            containerColor = Mint,
                            contentColor = TealDeep,
                            dotColor = Teal,
                        )
                        if (isMultiple) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "至少选择两个正确项；学生提交的选项集合与正确答案完全一致才计分。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                    } else {
                        ClassPulseField(
                            value = fillAnswer,
                            onValueChange = { fillAnswer = it; error = null },
                            label = "参考答案",
                            placeholder = "例如：O(n log n)",
                            leadingIcon = { Icon(Icons.Outlined.Check, null, tint = Teal) },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "比较时去除首尾空格，并忽略英文字母大小写。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("作答截止", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        presetMinutes.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (value, label) ->
                                    FilterChip(
                                        selected = deadlineMinutes == value,
                                        onClick = { deadlineMinutes = value },
                                        label = { Text(label) },
                                        shape = CircleShape,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Ink,
                                            selectedLabelColor = Cream,
                                            containerColor = Paper,
                                            labelColor = InkSoft,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                InlineDialogError(error)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !submitting) {
                        Text("取消", color = Muted)
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        enabled = !submitting,
                        onClick = {
                            submitQuestion(
                                questionType = questionType,
                                prompt = prompt,
                                options = options,
                                correctKeys = correctKeys,
                                fillAnswer = fillAnswer,
                                deadlineMinutes = deadlineMinutes,
                                onError = { error = it },
                                onSubmit = { request ->
                                    submitting = true
                                    onSubmit(request) { submitError ->
                                        submitting = false
                                        if (submitError == null) onDismiss() else error = submitError
                                    }
                                },
                            )
                        },
                    ) {
                        Text(
                            if (submitting) "发布中…" else "发布问题",
                            color = if (submitting) Muted else Teal,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun submitQuestion(
    questionType: String,
    prompt: String,
    options: List<String>,
    correctKeys: Set<String>,
    fillAnswer: String,
    deadlineMinutes: Long,
    onError: (String) -> Unit,
    onSubmit: (CreateQuestionRequest) -> Unit,
) {
    if (prompt.isBlank()) {
        onError("请输入题干")
        return
    }
    val deadlineAt = Instant.now().plusSeconds(deadlineMinutes * 60).toString()
    if (questionType == "FILL_BLANK") {
        if (fillAnswer.isBlank()) {
            onError("请输入参考答案")
            return
        }
        onSubmit(
            CreateQuestionRequest(
                type = questionType,
                prompt = prompt.trim(),
                options = null,
                correctAnswer = fillAnswer.trim(),
                deadlineAt = deadlineAt,
            ),
        )
        return
    }
    if (options.any { it.isBlank() }) {
        onError("请填写全部选项")
        return
    }
    if (options.map { it.trim().lowercase() }.distinct().size != options.size) {
        onError("选项内容不能重复")
        return
    }
    if (correctKeys.isEmpty()) {
        onError("请选择正确答案")
        return
    }
    if (questionType == "MULTIPLE_CHOICE" && correctKeys.size < 2) {
        onError("多选题至少需要两个正确答案")
        return
    }
    if (questionType == "SINGLE_CHOICE" && correctKeys.size != 1) {
        onError("单选题只能有一个正确答案")
        return
    }
    onSubmit(
        CreateQuestionRequest(
            type = questionType,
            prompt = prompt.trim(),
            options = options.map { it.trim() },
            correctAnswer = correctKeys.sorted().joinToString(","),
            deadlineAt = deadlineAt,
        ),
    )
}

@Composable
fun CreateAssignmentDialog(
    onDismiss: () -> Unit,
    onSubmit: (CreateAssignmentRequest, (String?) -> Unit) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var deadlineMinutes by rememberSaveable { mutableLongStateOf(3 * 24 * 60L) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val options = listOf(1L to "1 天", 3L to "3 天", 7L to "7 天", 14L to "14 天")

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(26.dp),
        containerColor = Cream,
        title = { Text("发布拍照作业", style = MaterialTheme.typography.headlineMedium, color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ClassPulseField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = "作业标题",
                    placeholder = "例如：手写快速排序的划分过程",
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Assignment, null, tint = Teal) },
                )
                Spacer(Modifier.height(12.dp))
                ClassPulseField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = "作业说明",
                    placeholder = "说明提交内容、拍照要求和注意事项",
                    singleLine = false,
                    minLines = 3,
                )
                Spacer(Modifier.height(18.dp))
                Text("提交截止", style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    options.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (value, label) ->
                                FilterChip(
                                    selected = deadlineMinutes == value * 60,
                                    onClick = { deadlineMinutes = value * 60 },
                                    label = { Text(label) },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Ink,
                                        selectedLabelColor = Cream,
                                        containerColor = Paper,
                                        labelColor = InkSoft,
                                    ),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "每次提交 1 张 JPEG 或 PNG 图片，最大 10 MB；截止前可替换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                InlineDialogError(error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    if (title.isBlank() || description.isBlank()) {
                        error = "请完整填写作业标题和说明"
                        return@TextButton
                    }
                    submitting = true
                    onSubmit(
                        CreateAssignmentRequest(
                            title = title.trim(),
                            description = description.trim(),
                            deadlineAt = Instant.now().plusSeconds(deadlineMinutes * 60).toString(),
                        ),
                    ) { submitError ->
                        submitting = false
                        if (submitError == null) onDismiss() else error = submitError
                    }
                },
            ) {
                Text(if (submitting) "发布中…" else "发布作业", color = if (submitting) Muted else Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消", color = Muted) } },
    )
}

@Composable
fun AssignmentSubmitDialog(
    assignmentTitle: String,
    existingNote: String?,
    onDismiss: () -> Unit,
    onSubmit: (ByteArray, String, String, String, (String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var note by rememberSaveable { mutableStateOf(existingNote.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var pickedName by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            pickedName = queryFileName(context, uri)
            error = null
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            selectedUri = capturedUri
            pickedName = queryFileName(context, capturedUri)
            error = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(26.dp),
        containerColor = Cream,
        title = {
            Column {
                Text("提交作业", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(3.dp))
                Text(assignmentTitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val previewUri = selectedUri
                if (previewUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Paper),
                    ) {
                        AsyncImage(
                            model = previewUri,
                            contentDescription = "待提交作业图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        StatusBadge(
                            text = "已选择",
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            containerColor = Ink.copy(alpha = 0.85f),
                            contentColor = Cream,
                            dotColor = Gold,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        pickedName ?: "所选图片",
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                        maxLines = 1,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(138.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Paper),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.AddAPhoto,
                                null,
                                tint = Teal.copy(alpha = 0.75f),
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "拍摄或选择一张作业图片",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SecondaryButton(
                        text = "拍照",
                        onClick = {
                            runCatching {
                                val uri = createCaptureUri(context)
                                capturedUri = uri
                                cameraLauncher.launch(uri)
                            }.onFailure {
                                error = "无法打开相机，请检查设备相机是否可用"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Outlined.CameraAlt, null, modifier = Modifier.size(17.dp)) },
                    )
                    SecondaryButton(
                        text = "从相册选择",
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(17.dp)) },
                    )
                }
                Spacer(Modifier.height(13.dp))
                ClassPulseField(
                    value = note,
                    onValueChange = { note = it.take(500); error = null },
                    label = "提交说明（可选）",
                    placeholder = "给老师留一句话",
                    singleLine = false,
                    minLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${note.length}/500 · 仅支持 JPEG / PNG，最大 10 MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                )
                InlineDialogError(error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val uri = selectedUri ?: run {
                        error = "请先拍照或选择图片"
                        return@TextButton
                    }
                    submitting = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { readAndValidateImage(context, uri) }
                        if (result.error != null) {
                            submitting = false
                            error = result.error
                            return@launch
                        }
                        onSubmit(
                            result.bytes!!,
                            result.filename!!,
                            result.contentType!!,
                            note,
                        ) { submitError ->
                            submitting = false
                            if (submitError == null) onDismiss() else error = submitError
                        }
                    }
                },
            ) {
                Text(if (submitting) "上传中…" else "确认提交", color = if (submitting) Muted else Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消", color = Muted) } },
    )
}

@Composable
private fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Paper)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected == value) Ink else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected == value) Cream else Muted,
                )
            }
        }
    }
}

@Composable
private fun InlineDialogError(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DangerSoft)
                .padding(10.dp),
        ) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = Danger)
        }
    }
}

private fun createCaptureUri(context: Context): Uri {
    val file = File.createTempFile("classpulse_capture_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun queryFileName(context: Context, uri: Uri?): String? {
    if (uri == null) return null
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()
}

private data class ImageReadResult(
    val bytes: ByteArray? = null,
    val filename: String? = null,
    val contentType: String? = null,
    val error: String? = null,
)

private fun readAndValidateImage(context: Context, uri: Uri): ImageReadResult {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return ImageReadResult(error = "无法读取所选图片，请重新选择")
    if (bytes.isEmpty()) return ImageReadResult(error = "图片内容为空")
    if (bytes.size > 10 * 1024 * 1024) return ImageReadResult(error = "图片不能超过 10 MB，请压缩后重试")
    val mime = detectImageType(bytes) ?: return ImageReadResult(error = "仅支持 JPEG 或 PNG 图片")
    val extension = if (mime == "image/png") "png" else "jpg"
    val originalName = queryFileName(context, uri)
        ?.substringAfterLast('/')
        ?.take(180)
        ?.ifBlank { null }
        ?: "assignment.$extension"
    return ImageReadResult(bytes = bytes, filename = originalName, contentType = mime)
}

private fun detectImageType(bytes: ByteArray): String? {
    if (bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
    ) return "image/jpeg"
    val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    if (bytes.size >= 8 && png.indices.all { bytes[it] == png[it] }) return "image/png"
    return null
}
