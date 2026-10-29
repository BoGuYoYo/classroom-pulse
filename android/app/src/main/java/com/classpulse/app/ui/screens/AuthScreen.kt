package com.classpulse.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classpulse.app.BuildConfig
import com.classpulse.app.ui.auth.AuthViewModel
import com.classpulse.app.ui.components.*
import com.classpulse.app.ui.theme.*

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showServerDialog by rememberSaveable { mutableStateOf(false) }

    PulsePage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wordmark(compact = true)
                if (BuildConfig.DEBUG) {
                    ServerChip(
                        url = state.serverUrl,
                        onClick = { showServerDialog = true },
                    )
                } else {
                    StatusBadge(
                        text = "HTTPS 安全连接",
                        containerColor = Mint,
                        contentColor = TealDeep,
                        dotColor = Teal,
                    )
                }
            }

            Spacer(Modifier.height(42.dp))
            Box {
                Column(Modifier.fillMaxWidth(0.88f)) {
                    Text(
                        "让每一次课堂\n都有迹可循。",
                        style = MaterialTheme.typography.displayLarge,
                        color = Ink,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "签到、问答、作业与教学分析，在同一条课堂脉络里自然发生。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Muted,
                        modifier = Modifier.widthIn(max = 310.dp),
                    )
                }
                AuthSignalGraphic(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 18.dp, y = (-6).dp)
                        .size(110.dp, 86.dp),
                )
            }

            Spacer(Modifier.height(30.dp))
            PulseCard(
                contentPadding = PaddingValues(18.dp),
                cornerRadius = 28.dp,
            ) {
                AuthModeSwitcher(
                    registerMode = registerMode,
                    onChange = {
                        registerMode = it
                        viewModel.clearError()
                    },
                )
                Spacer(Modifier.height(20.dp))
                if (registerMode) {
                    ClassPulseField(
                        value = username,
                        onValueChange = { username = it.trim(); viewModel.clearError() },
                        label = "学号",
                        placeholder = "例如 S1001",
                        leadingIcon = { Icon(Icons.Outlined.Badge, null, tint = Teal) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    )
                    Spacer(Modifier.height(12.dp))
                    ClassPulseField(
                        value = name,
                        onValueChange = { name = it; viewModel.clearError() },
                        label = "姓名",
                        placeholder = "用于课堂名单展示",
                        leadingIcon = { Icon(Icons.Outlined.PersonOutline, null, tint = Teal) },
                    )
                } else {
                    ClassPulseField(
                        value = username,
                        onValueChange = { username = it.trim(); viewModel.clearError() },
                        label = "学号 / 工号",
                        placeholder = "输入你的课堂账号",
                        leadingIcon = { Icon(Icons.Outlined.PersonOutline, null, tint = Teal) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    )
                }
                Spacer(Modifier.height(12.dp))
                ClassPulseField(
                    value = password,
                    onValueChange = { password = it; viewModel.clearError() },
                    label = "密码",
                    placeholder = if (registerMode) "至少 8 位字符" else "输入登录密码",
                    leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = Teal) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                tint = Muted,
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = state.error != null,
                )
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    InlineError(state.error)
                }
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = when {
                        state.loading -> if (registerMode) "正在创建账号…" else "正在进入…"
                        registerMode -> "创建学生账号"
                        else -> "进入课堂"
                    },
                    onClick = {
                        if (registerMode) {
                            viewModel.register(username, name, password)
                        } else {
                            viewModel.login(username, password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                    leadingIcon = if (state.loading) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 2.dp,
                                color = Cream,
                            )
                        }
                    } else {
                        { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp)) }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (registerMode) {
                        "注册即表示你将使用学号作为课堂唯一身份。教师账号由系统预置。"
                    } else {
                        "教师与学生使用同一入口，登录后自动进入对应工作台。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Teal.copy(alpha = 0.5f)))
                Spacer(Modifier.width(7.dp))
                Text(
                    "密码使用安全哈希存储 · 课堂数据按课程隔离",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                )
            }
            Spacer(Modifier.height(26.dp))
        }
    }

    if (showServerDialog && BuildConfig.DEBUG) {
        ServerDialog(
            initialUrl = state.serverUrl,
            onDismiss = { showServerDialog = false },
            onSave = { url, onResult ->
                viewModel.updateServer(url) { error ->
                    if (error == null) showServerDialog = false
                    onResult(error)
                }
            },
        )
    }
}

@Composable
private fun AuthModeSwitcher(registerMode: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Paper)
            .padding(4.dp),
    ) {
        listOf(false to "登录", true to "学生注册").forEach { (mode, label) ->
            val selected = registerMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) Ink else Color.Transparent)
                    .clickable { onChange(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Cream else Muted,
                )
            }
        }
    }
}

@Composable
private fun ServerChip(url: String, onClick: () -> Unit) {
    val host = remember(url) {
        runCatching { java.net.URI(url).host }.getOrNull() ?: url
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Cream)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Dns, null, tint = Teal, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            host.take(18),
            style = MaterialTheme.typography.labelMedium,
            color = InkSoft,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.Edit, null, tint = Muted, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun AuthSignalGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.04f, h * 0.62f)
            cubicTo(w * 0.22f, h * 0.62f, w * 0.25f, h * 0.25f, w * 0.4f, h * 0.3f)
            cubicTo(w * 0.55f, h * 0.35f, w * 0.54f, h * 0.72f, w * 0.69f, h * 0.62f)
            cubicTo(w * 0.82f, h * 0.53f, w * 0.82f, h * 0.22f, w * 0.94f, h * 0.16f)
        }
        drawPath(
            path,
            color = Teal.copy(alpha = 0.65f),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(Gold, 7.dp.toPx(), Offset(w * 0.93f, h * 0.14f))
        drawCircle(Coral.copy(alpha = 0.9f), 4.dp.toPx(), Offset(w * 0.04f, h * 0.62f))
        drawCircle(
            Teal.copy(alpha = 0.1f),
            28.dp.toPx(),
            Offset(w * 0.67f, h * 0.62f),
        )
    }
}

@Composable
private fun ServerDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, (String?) -> Unit) -> Unit,
) {
    var value by remember(initialUrl) { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = Cream,
        title = {
            Text("服务器设置", style = MaterialTheme.typography.headlineMedium, color = Ink)
        },
        text = {
            Column {
                Text(
                    "模拟器默认访问 10.0.2.2，真机请填写已部署的 HTTPS 域名。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(14.dp))
                ClassPulseField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = "服务地址",
                    placeholder = "https://class.example.com/",
                    leadingIcon = { Icon(Icons.Outlined.Link, null, tint = Teal) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = error != null,
                )
                InlineError(error, modifier = Modifier.padding(top = 10.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "地址只保存到本机。修改服务器不会上传任何课堂数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(value) { saveError -> error = saveError }
            }) {
                Text("保存", color = Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
        },
    )
}

