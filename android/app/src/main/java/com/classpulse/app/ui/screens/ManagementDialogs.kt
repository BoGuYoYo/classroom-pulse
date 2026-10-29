package com.classpulse.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classpulse.app.data.model.CourseSummary
import com.classpulse.app.data.model.Question
import com.classpulse.app.data.model.StudentCourseStats
import com.classpulse.app.data.model.Submission
import com.classpulse.app.data.model.UpdateQuestionRequest
import com.classpulse.app.ui.components.ClassPulseField
import com.classpulse.app.ui.components.PulseCard
import com.classpulse.app.ui.components.StatusBadge
import com.classpulse.app.ui.theme.*
import com.classpulse.app.ui.util.Formats
import java.time.Instant

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, (String?) -> Unit) -> Unit,
) {
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = Cream,
        title = { Text("修改密码", style = MaterialTheme.typography.headlineMedium, color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ClassPulseField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; error = null },
                    label = "当前密码",
                    leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = Teal) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(10.dp))
                ClassPulseField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = "新密码（至少 8 位）",
                    leadingIcon = { Icon(Icons.Outlined.Key, null, tint = Teal) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(10.dp))
                ClassPulseField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = "确认新密码",
                    leadingIcon = { Icon(Icons.Outlined.Check, null, tint = Teal) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                InlineDialogError(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !submitting, onClick = {
                when {
                    currentPassword.isBlank() -> error = "请输入当前密码"
                    newPassword.length < 8 -> error = "新密码至少 8 位"
                    newPassword != confirmPassword -> error = "两次输入的新密码不一致"
                    else -> {
                        submitting = true
                        onSubmit(currentPassword, newPassword) { submitError ->
                            submitting = false
                            if (submitError == null) onDismiss() else error = submitError
                        }
                    }
                }
            }) {
                Text(if (submitting) "保存中…" else "保存", color = if (submitting) Muted else Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消", color = Muted) } },
    )
}

@Composable
fun EditCourseDialog(
    course: CourseSummary,
    onDismiss: () -> Unit,
    onSubmit: (String, String, (String?) -> Unit) -> Unit,
) {
    var name by rememberSaveable(course.id) { mutableStateOf(course.name) }
    var description by rememberSaveable(course.id) { mutableStateOf(course.description) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = Cream,
        title = { Text("编辑课程", style = MaterialTheme.typography.headlineMedium, color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ClassPulseField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = "课程名称",
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                ClassPulseField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = "课程简介",
                    singleLine = false,
                    minLines = 3,
                )
                InlineDialogError(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !submitting, onClick = {
                if (name.isBlank() || description.isBlank()) {
                    error = "请完整填写课程名称和简介"
                } else {
                    submitting = true
                    onSubmit(name.trim(), description.trim()) { submitError ->
                        submitting = false
                        if (submitError == null) onDismiss() else error = submitError
                    }
                }
            }) {
                Text(if (submitting) "保存中…" else "保存", color = if (submitting) Muted else Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消", color = Muted) } },
    )
}

@Composable
fun CourseSettingsDialog(
    course: CourseSummary,
    isTeacher: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onResetInvite: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onLeave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Cream,
        title = {
            Column {
                Text("课程设置", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Text(course.name, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isTeacher) {
                    SettingsAction(Icons.Outlined.Edit, "编辑课程名称和简介", onEdit)
                    SettingsAction(Icons.Outlined.Refresh, "重置邀请码", onResetInvite)
                    SettingsAction(
                        if (course.archived) Icons.Outlined.Refresh else Icons.Outlined.Lock,
                        if (course.archived) "取消归档" else "归档课程",
                        onArchive,
                    )
                    Text(
                        if (course.archived) "已归档课程只能查看，不能发布或提交。" else "归档后课程变为只读，可随时取消归档。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Line)
                    SettingsAction(Icons.Outlined.Delete, "删除课程", onDelete, danger = true)
                } else {
                    SettingsAction(Icons.AutoMirrored.Outlined.ExitToApp, "退出课程", onLeave, danger = true)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = Teal) } },
    )
}

@Composable
private fun SettingsAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = if (danger) Danger else Teal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (danger) Danger else Ink, modifier = Modifier.weight(1f))
    }
}

@Composable
fun GradeSubmissionDialog(
    submission: Submission,
    studentName: String,
    onDismiss: () -> Unit,
    onSubmit: (Int, String?, (String?) -> Unit) -> Unit,
) {
    var score by rememberSaveable(submission.id) { mutableStateOf(submission.score?.toString().orEmpty()) }
    var feedback by rememberSaveable(submission.id) { mutableStateOf(submission.feedback.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = Cream,
        title = {
            Column {
                Text("作业评分", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Text(studentName, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ClassPulseField(
                    value = score,
                    onValueChange = { value ->
                        score = value.filter { it.isDigit() }.take(3)
                        error = null
                    },
                    label = "分数（0–100）",
                    leadingIcon = { Icon(Icons.Outlined.Star, null, tint = Teal) },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                ClassPulseField(
                    value = feedback,
                    onValueChange = { feedback = it.take(2000); error = null },
                    label = "评语（可选）",
                    singleLine = false,
                    minLines = 3,
                )
                InlineDialogError(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !submitting, onClick = {
                val number = score.toIntOrNull()
                if (number == null || number !in 0..100) {
                    error = "请输入 0–100 的分数"
                } else {
                    submitting = true
                    onSubmit(number, feedback.trim().ifBlank { null }) { submitError ->
                        submitting = false
                        if (submitError == null) onDismiss() else error = submitError
                    }
                }
            }) {
                Text(if (submitting) "保存中…" else "保存评分", color = if (submitting) Muted else Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消", color = Muted) } },
    )
}

@Composable
fun EditQuestionDialog(
    question: Question,
    onDismiss: () -> Unit,
    onSubmit: (UpdateQuestionRequest, (String?) -> Unit) -> Unit,
) {
    val hasAnswers = (question.submissionCount ?: 0) > 0
    var prompt by rememberSaveable(question.id) { mutableStateOf(question.prompt) }
    var options by remember(question.id) {
        mutableStateOf(question.options.map { it.text }.ifEmpty { listOf("", "", "", "") })
    }
    var correctKeys by remember(question.id) {
        mutableStateOf(
            question.correctAnswer
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?.ifEmpty { setOf("A") }
                ?: setOf("A"),
        )
    }
    var fillAnswer by rememberSaveable(question.id) { mutableStateOf(question.correctAnswer.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val isChoice = question.isChoice

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = Cream,
        title = {
            Column {
                Text("编辑题目", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Text(
                    "${Formats.questionType(question.type)} · ${if (hasAnswers) "已有作答，仅可改题干" else "可修改选项和答案"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 430.dp),
            ) {
                ClassPulseField(
                    value = prompt,
                    onValueChange = { prompt = it; error = null },
                    label = "题干",
                    singleLine = false,
                    minLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                if (isChoice) {
                    Text("选项", style = MaterialTheme.typography.titleSmall, color = Ink)
                    options.forEachIndexed { index, value ->
                        val key = ('A'.code + index).toChar().toString()
                        val selected = key in correctKeys
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            TextButton(
                                enabled = !hasAnswers,
                                onClick = {
                                    correctKeys = if (question.isMultipleChoice) {
                                        if (selected) correctKeys - key else correctKeys + key
                                    } else {
                                        setOf(key)
                                    }
                                },
                            ) {
                                Text(
                                    key,
                                    color = if (selected) Teal else Muted,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            ClassPulseField(
                                value = value,
                                onValueChange = { newValue ->
                                    options = options.toMutableList().also { it[index] = newValue }
                                    error = null
                                },
                                label = "选项 $key",
                                enabled = !hasAnswers,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (!hasAnswers && options.size < 6) {
                        TextButton(onClick = { options = options + "" }) { Text("增加选项", color = Teal) }
                    }
                    Spacer(Modifier.height(6.dp))
                    StatusBadge(
                        text = "正确答案 ${correctKeys.sorted().joinToString(",").ifBlank { "未选择" }}",
                        containerColor = Mint,
                        contentColor = TealDeep,
                        dotColor = Teal,
                    )
                } else {
                    ClassPulseField(
                        value = fillAnswer,
                        onValueChange = { fillAnswer = it; error = null },
                        label = "参考答案",
                        enabled = !hasAnswers,
                    )
                }
                if (hasAnswers) {
                    Spacer(Modifier.height(8.dp))
                    Text("已有学生提交，选项和标准答案已锁定。", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                InlineDialogError(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !submitting, onClick = {
                val request = buildUpdateQuestion(question, prompt, options, correctKeys, fillAnswer, hasAnswers, { error = it })
                    ?: return@TextButton
                submitting = true
                onSubmit(request) { submitError ->
                    submitting = false
                    if (submitError == null) onDismiss() else error = submitError
                }
            }) {
                Text(if (submitting) "保存中…" else "保存", color = if (submitting) Muted else Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消", color = Muted) } },
    )
}

private fun buildUpdateQuestion(
    question: Question,
    prompt: String,
    options: List<String>,
    correctKeys: Set<String>,
    fillAnswer: String,
    hasAnswers: Boolean,
    onError: (String) -> Unit,
): UpdateQuestionRequest? {
    if (prompt.isBlank()) {
        onError("请输入题干")
        return null
    }
    val correctAnswer = if (question.isChoice) {
        if (!hasAnswers) {
            if (options.any { it.isBlank() }) {
                onError("请填写全部选项")
                return null
            }
            if (correctKeys.isEmpty()) {
                onError("请选择正确答案")
                return null
            }
        }
        correctKeys.sorted().joinToString(",")
    } else {
        if (!hasAnswers && fillAnswer.isBlank()) {
            onError("请输入参考答案")
            return null
        }
        fillAnswer.trim()
    }
    return UpdateQuestionRequest(
        prompt = prompt.trim(),
        options = if (question.isChoice) options.map { it.trim() } else null,
        correctAnswer = correctAnswer,
        deadlineAt = question.deadlineAt,
    )
}

@Composable
fun StudentStatsContent(stats: StudentCourseStats?, loading: Boolean) {
    if (loading && stats == null) {
        Text("正在加载个人成绩…", color = Muted, modifier = Modifier.padding(18.dp))
        return
    }
    if (stats == null) {
        Text("暂无个人成绩数据", color = Muted, modifier = Modifier.padding(18.dp))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryMetric("签到率", Formats.percent(stats.attendanceRate), "${stats.attendanceCount}/${stats.attendanceTotal}", Modifier.weight(1f))
            SummaryMetric("答题正确率", Formats.percent(stats.correctnessRate), "${stats.correctCount}/${stats.answeredCount}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryMetric("作业提交", "${stats.submittedCount}/${stats.assignmentTotal}", "已评分 ${stats.gradedCount}", Modifier.weight(1f))
            SummaryMetric("平均得分", stats.averageScore?.let { String.format(java.util.Locale.CHINA, "%.1f", it) } ?: "—", "仅统计已评分作业", Modifier.weight(1f))
        }
        PulseCard(contentPadding = PaddingValues(16.dp)) {
            Text("课堂活动", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(8.dp))
            stats.attendance.forEach { item ->
                SummaryRow(
                    title = item.title,
                    subtitle = Formats.dateTime(item.startsAt),
                    value = if (item.checkedIn) "已签到" else "未签到",
                    success = item.checkedIn,
                )
            }
            stats.questions.forEach { item ->
                SummaryRow(
                    title = item.prompt,
                    subtitle = Formats.questionType(item.type),
                    value = when {
                        !item.answered -> "未作答"
                        item.correct == true -> "正确"
                        else -> "未正确"
                    },
                    success = item.correct == true,
                )
            }
            stats.assignments.forEach { item ->
                SummaryRow(
                    title = item.title,
                    subtitle = Formats.dateTime(item.deadlineAt),
                    value = when {
                        !item.submitted -> "未提交"
                        item.score != null -> "${item.score} 分"
                        else -> "已提交"
                    },
                    success = item.submitted,
                )
                if (!item.feedback.isNullOrBlank()) {
                    Text("教师评语：${item.feedback}", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    PulseCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun SummaryRow(title: String, subtitle: String, value: String, success: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Text(value, style = MaterialTheme.typography.labelMedium, color = if (success) Success else Muted)
    }
}
