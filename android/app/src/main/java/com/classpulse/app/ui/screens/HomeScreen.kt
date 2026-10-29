package com.classpulse.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.classpulse.app.data.model.CourseSummary
import com.classpulse.app.data.model.User
import com.classpulse.app.ui.components.*
import com.classpulse.app.ui.courses.CoursesViewModel
import com.classpulse.app.ui.theme.*
import com.classpulse.app.ui.util.Formats

@Composable
fun HomeScreen(
    user: User,
    viewModel: CoursesViewModel,
    onOpenCourse: (Long) -> Unit,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateCourse by rememberSaveable { mutableStateOf(false) }
    var showJoinCourse by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    var userMenuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    PulsePage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wordmark(compact = true)
                Box {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { userMenuOpen = true }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoleBadge(user.isTeacher)
                        Spacer(Modifier.width(8.dp))
                        AvatarCircle(user.name, size = 36.dp, containerColor = if (user.isTeacher) Ink else Teal)
                    }
                    DropdownMenu(
                        expanded = userMenuOpen,
                        onDismissRequest = { userMenuOpen = false },
                        containerColor = Cream,
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(user.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                            Text(
                                "${Formats.role(user.role)} · ${user.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                        HorizontalDivider(color = Line)
                        DropdownMenuItem(
                            text = { Text("修改密码", color = Ink) },
                            leadingIcon = { Icon(Icons.Outlined.Key, null, tint = Teal) },
                            onClick = {
                                userMenuOpen = false
                                showChangePassword = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("退出登录", color = Danger) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = Danger)
                            },
                            onClick = {
                                userMenuOpen = false
                                showLogoutConfirm = true
                            },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${Formats.greeting()}，${user.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Muted,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (user.isTeacher) "今天，让课堂发声。" else "今天想从哪一节课开始？",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                )
                Spacer(Modifier.height(22.dp))

                if (user.isTeacher) {
                    TeacherHero(
                        courseCount = state.courses.size,
                        memberCount = state.courses.sumOf { it.memberCount },
                        onCreate = { showCreateCourse = true },
                    )
                } else {
                    StudentHero(
                        courseCount = state.courses.size,
                        onJoin = { showJoinCourse = true },
                    )
                }

                Spacer(Modifier.height(30.dp))
                SectionHeading(
                    title = "我的课程",
                    eyebrow = "COURSES",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${state.courses.size} 门",
                                style = MaterialTheme.typography.labelMedium,
                                color = Muted,
                            )
                            Spacer(Modifier.width(8.dp))
                            RefreshIconButton(onClick = viewModel::refresh)
                        }
                    },
                )
                Spacer(Modifier.height(14.dp))

                when {
                    state.loading -> {
                        repeat(2) { CourseSkeleton() }
                    }

                    state.error != null -> {
                        PulseCard {
                            InlineError(state.error)
                            Spacer(Modifier.height(12.dp))
                            SecondaryButton("重新加载", onClick = viewModel::refresh)
                        }
                    }

                    state.courses.isEmpty() -> {
                        EmptyState(
                            title = if (user.isTeacher) "还没有课程" else "还没有加入课程",
                            message = if (user.isTeacher) {
                                "创建第一门课程，把签到、问答和作业放进同一条课堂脉络。"
                            } else {
                                "向老师获取课程邀请码，加入后即可参与课堂互动。"
                            },
                            icon = { Icon(Icons.Outlined.School, null, tint = Teal, modifier = Modifier.size(26.dp)) },
                            action = {
                                PrimaryButton(
                                    text = if (user.isTeacher) "创建课程" else "输入邀请码",
                                    onClick = {
                                        if (user.isTeacher) showCreateCourse = true else showJoinCourse = true
                                    },
                                )
                            },
                        )
                    }

                    else -> {
                        state.courses.forEachIndexed { index, course ->
                            CourseCard(
                                course = course,
                                index = index,
                                isTeacher = user.isTeacher,
                                onClick = { onOpenCourse(course.id) },
                                onCopyInvite = course.inviteCode?.let { code ->
                                    {
                                        clipboard.setText(AnnotatedString(code))
                                        scope.launch { snackbarHostState.showSnackbar("邀请码已复制") }
                                    }
                                },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "课堂数据按课程隔离 · 课脉 v1.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        )
    }

    if (showCreateCourse) {
        CreateCourseDialog(
            onDismiss = { showCreateCourse = false },
            onSubmit = { name, description, onResult ->
                viewModel.createCourse(name, description, onResult)
            },
        )
    }
    if (showJoinCourse) {
        JoinCourseDialog(
            onDismiss = { showJoinCourse = false },
            onSubmit = { inviteCode, onResult ->
                viewModel.joinCourse(inviteCode, onResult)
            },
        )
    }
    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onSubmit = { current, newPassword, onResult ->
                viewModel.changePassword(current, newPassword, onResult)
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = Cream,
            shape = RoundedCornerShape(24.dp),
            title = { Text("退出登录？", style = MaterialTheme.typography.headlineMedium) },
            text = { Text("本机登录信息将被清除，课堂数据仍保留在服务器。", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) { Text("退出", color = Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消", color = Muted) }
            },
        )
    }
}

@Composable
private fun TeacherHero(courseCount: Int, memberCount: Long, onCreate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Ink, Color(0xFF173F40), TealDeep),
                    start = Offset.Zero,
                    end = Offset(900f, 600f),
                ),
            ),
    ) {
        HeroDecoration(Modifier.matchParentSize())
        Column(Modifier.padding(24.dp)) {
            Text(
                "TEACHER STUDIO",
                style = MaterialTheme.typography.labelSmall,
                color = Gold,
                letterSpacing = 2.4.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "把课堂，\n连成一条线。",
                style = MaterialTheme.typography.displayMedium,
                color = Cream,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "从一次签到到一道题，再到一份作业，让每个课堂瞬间都有回应。",
                style = MaterialTheme.typography.bodyMedium,
                color = Cream.copy(alpha = 0.68f),
                modifier = Modifier.widthIn(max = 270.dp),
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        "$courseCount",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Cream,
                    )
                    Text("门课程", style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.6f))
                }
                Spacer(Modifier.width(24.dp))
                Box(Modifier.width(1.dp).height(35.dp).background(Cream.copy(alpha = 0.18f)))
                Spacer(Modifier.width(24.dp))
                Column {
                    Text(
                        "$memberCount",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Cream,
                    )
                    Text("人次选课", style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onCreate,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("创建新课程", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StudentHero(courseCount: Int, onJoin: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D514C), TealDeep),
                    start = Offset.Zero,
                    end = Offset(900f, 700f),
                ),
            ),
    ) {
        HeroDecoration(Modifier.matchParentSize())
        Column(Modifier.padding(24.dp)) {
            Text(
                "LEARNING TRACE",
                style = MaterialTheme.typography.labelSmall,
                color = Gold,
                letterSpacing = 2.4.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "把每一次参与，\n变成成长的刻度。",
                style = MaterialTheme.typography.displayMedium,
                color = Cream,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "你已加入 $courseCount 门课程。签到、作答和作业都会在这里留下清晰的记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = Cream.copy(alpha = 0.7f),
                modifier = Modifier.widthIn(max = 280.dp),
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onJoin,
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Ink),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Icon(Icons.Outlined.AddLink, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("用邀请码加入课程", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HeroDecoration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = Mint.copy(alpha = 0.07f),
            radius = w * 0.38f,
            center = Offset(w * 0.9f, h * 0.08f),
        )
        drawCircle(
            color = Gold.copy(alpha = 0.09f),
            radius = w * 0.22f,
            center = Offset(w * 0.84f, h * 0.72f),
        )
        val path = Path().apply {
            moveTo(w * 0.6f, h * 0.08f)
            cubicTo(w * 0.75f, h * 0.2f, w * 0.74f, h * 0.34f, w * 0.9f, h * 0.36f)
        }
        drawPath(
            path,
            color = Gold.copy(alpha = 0.55f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(Gold.copy(alpha = 0.85f), 4.dp.toPx(), Offset(w * 0.9f, h * 0.36f))
    }
}

@Composable
private fun CourseCard(
    course: CourseSummary,
    index: Int,
    isTeacher: Boolean,
    onClick: () -> Unit,
    onCopyInvite: (() -> Unit)?,
) {
    val accent = listOf(Teal, Coral, Gold, BlueDeep)[index % 4]
    PulseCard(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        cornerRadius = 22.dp,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(Modifier.padding(18.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "%02d".format(index + 1),
                            style = MaterialTheme.typography.titleMedium,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            course.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            course.teacherName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = Muted.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 8.dp).size(18.dp),
                    )
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    course.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(
                        text = "${course.memberCount} 名学生",
                        containerColor = Paper,
                        contentColor = InkSoft,
                        dotColor = accent,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isTeacher && course.inviteCode != null) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Paper)
                                .clickable(enabled = onCopyInvite != null) { onCopyInvite?.invoke() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Key, null, tint = Muted, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                course.inviteCode,
                                style = MaterialTheme.typography.labelMedium,
                                color = InkSoft,
                                letterSpacing = 1.sp,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.ContentCopy, null, tint = Muted, modifier = Modifier.size(11.dp))
                        }
                    } else if (course.joinedAt != null) {
                        Text(
                            "加入于 ${Formats.date(course.joinedAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseSkeleton() {
    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Line),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.65f).height(18.dp).clip(CircleShape).background(Line))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(CircleShape).background(Line.copy(alpha = 0.65f)))
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Line.copy(alpha = 0.65f)))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(CircleShape).background(Line.copy(alpha = 0.5f)))
    }
    Spacer(Modifier.height(12.dp))
}
