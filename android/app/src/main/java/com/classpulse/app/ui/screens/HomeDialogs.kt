package com.classpulse.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.classpulse.app.ui.components.ClassPulseField
import com.classpulse.app.ui.components.InlineError
import com.classpulse.app.ui.theme.*

@Composable
fun CreateCourseDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, (String?) -> Unit) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(26.dp),
        containerColor = Cream,
        title = {
            Column {
                Text("创建新课程", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text("系统会自动生成唯一邀请码", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ClassPulseField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = "课程名称",
                    placeholder = "例如：数据结构与算法",
                    leadingIcon = { Icon(Icons.Outlined.School, null, tint = Teal) },
                )
                Spacer(Modifier.height(12.dp))
                ClassPulseField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = "课程简介",
                    placeholder = "用一两句话介绍课程内容和课堂方式",
                    leadingIcon = { Icon(Icons.Outlined.Description, null, tint = Teal) },
                    singleLine = false,
                    minLines = 3,
                )
                InlineError(error, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    if (name.isBlank() || description.isBlank()) {
                        error = "请完整填写课程名称和简介"
                        return@TextButton
                    }
                    submitting = true
                    onSubmit(name, description) { submitError ->
                        submitting = false
                        if (submitError == null) {
                            onDismiss()
                        } else {
                            error = submitError
                        }
                    }
                },
            ) {
                Text(
                    if (submitting) "创建中…" else "创建课程",
                    color = if (submitting) Muted else Teal,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("取消", color = Muted)
            }
        },
    )
}

@Composable
fun JoinCourseDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, (String?) -> Unit) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        shape = RoundedCornerShape(26.dp),
        containerColor = Cream,
        title = {
            Column {
                Text("加入课程", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text("向老师获取 7 位课程邀请码", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        text = {
            Column {
                ClassPulseField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12)
                        error = null
                    },
                    label = "课程邀请码",
                    placeholder = "例如 ABC1234",
                    leadingIcon = { Icon(Icons.Outlined.Key, null, tint = Teal) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                InlineError(error, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    if (code.isBlank()) {
                        error = "请输入课程邀请码"
                        return@TextButton
                    }
                    submitting = true
                    onSubmit(code) { submitError ->
                        submitting = false
                        if (submitError == null) onDismiss() else error = submitError
                    }
                },
            ) {
                Text(
                    if (submitting) "加入中…" else "加入课程",
                    color = if (submitting) Muted else Teal,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("取消", color = Muted)
            }
        },
    )
}

