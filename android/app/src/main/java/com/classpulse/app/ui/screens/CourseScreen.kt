package com.classpulse.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.classpulse.app.ClassPulseApplication
import com.classpulse.app.data.model.*
import com.classpulse.app.ui.components.*
import com.classpulse.app.ui.course.CourseViewModel
import com.classpulse.app.ui.theme.*
import com.classpulse.app.ui.util.Formats
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(
    courseId: Long,
    user: User,
    viewModel: CourseViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val tabs = if (user.isTeacher) listOf("签到", "问答", "作业", "分析") else listOf("签到", "问答", "作业", "成绩")
    var selectedTab by rememberSaveable(courseId) { mutableIntStateOf(0) }

    var showCreateAttendance by rememberSaveable { mutableStateOf(false) }
    var showCreateQuestion by rememberSaveable { mutableStateOf(false) }
    var showCreateAssignment by rememberSaveable { mutableStateOf(false) }
    var showCourseSettings by rememberSaveable { mutableStateOf(false) }
    var showEditCourse by rememberSaveable { mutableStateOf(false) }
    var showDeleteCourseConfirm by rememberSaveable { mutableStateOf(false) }
    var editQuestionTarget by remember { mutableStateOf<Question?>(null) }
    var deleteQuestionTarget by remember { mutableStateOf<Question?>(null) }
    var gradeTarget by remember { mutableStateOf<Submission?>(null) }
    var removeMemberTarget by remember { mutableStateOf<Member?>(null) }
    var temporaryPasswordInfo by remember { mutableStateOf<String?>(null) }
    var submissionTarget by remember { mutableStateOf<Assignment?>(null) }
    var sheetKind by remember { mutableStateOf<String?>(null) }
    var sheetTargetId by remember { mutableStateOf<Long?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(courseId) {
        viewModel.load(courseId)
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(selectedTab, state.course) {
        if (selectedTab == 3 && state.course != null) {
            if (user.isTeacher) viewModel.loadStats(courseId) else viewModel.loadStudentStats(courseId)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(courseId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(5_000)
                viewModel.refresh(courseId)
                if (selectedTab == 3) {
                    if (user.isTeacher) viewModel.loadStats(courseId, force = true) else viewModel.loadStudentStats(courseId, force = true)
                }
                val targetId = sheetTargetId
                if (targetId != null) {
                    when (sheetKind) {
                        "attendance" -> viewModel.loadAttendanceDetail(targetId, quiet = true)
                        "question" -> viewModel.loadQuestionStats(targetId, quiet = true)
                        "assignment" -> viewModel.loadAssignmentSubmissions(targetId, quiet = true)
                    }
                }
            }
        }
    }

    PulsePage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            PulseTopBar(
                title = state.course?.name ?: "课程",
                subtitle = state.course?.teacherName,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showCourseSettings = true }) {
                        Icon(Icons.Outlined.Settings, null, tint = Ink)
                    }
                    RefreshIconButton(onClick = {
                        viewModel.refresh(courseId)
                        if (selectedTab == 3) {
                            if (user.isTeacher) viewModel.loadStats(courseId, force = true) else viewModel.loadStudentStats(courseId, force = true)
                        }
                    })
                },
            )

            when {
                state.loading && state.course == null -> LoadingPane("正在打开课堂…", Modifier.weight(1f))
                state.course == null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        InlineError(state.error ?: "课程加载失败")
                        Spacer(Modifier.height(14.dp))
                        SecondaryButton("重新加载", onClick = { viewModel.load(courseId) })
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                    ) {
                        CourseHero(
                            course = requireNotNull(state.course),
                            isTeacher = user.isTeacher,
                            onCopyInvite = {
                                state.course?.inviteCode?.let {
                                    clipboard.setText(AnnotatedString(it))
                                    scope.launch { snackbarHostState.showSnackbar("课程邀请码已复制") }
                                }
                            },
                            onOpenMembers = {
                                sheetKind = "members"
                                viewModel.refresh(courseId)
                            },
                        )
                        Spacer(Modifier.height(18.dp))
                        CourseTabSwitcher(tabs = tabs, selected = selectedTab, onSelect = { selectedTab = it })
                        Spacer(Modifier.height(20.dp))

                        when (selectedTab) {
                            0 -> AttendanceSection(
                                courseId = courseId,
                                state = state,
                                isTeacher = user.isTeacher,
                                onCreate = { showCreateAttendance = true },
                                onOpenResults = {
                                    sheetKind = "attendance"
                                    sheetTargetId = it
                                    viewModel.loadAttendanceDetail(it)
                                },
                                onCheckIn = { attendanceId, code, onResult ->
                                    viewModel.checkIn(attendanceId, courseId, code, onResult)
                                },
                            )

                            1 -> QuestionSection(
                                state = state,
                                isTeacher = user.isTeacher,
                                onCreate = { showCreateQuestion = true },
                                onOpenStats = {
                                    sheetKind = "question"
                                    sheetTargetId = it
                                    viewModel.loadQuestionStats(it)
                                },
                                onEditQuestion = { editQuestionTarget = it },
                                onCloseQuestion = { question ->
                                    viewModel.closeQuestion(courseId, question.id) {}
                                },
                                onDeleteQuestion = { deleteQuestionTarget = it },
                                onAnswer = { questionId, answer, onResult ->
                                    viewModel.answerQuestion(questionId, courseId, answer, onResult)
                                },
                            )

                            2 -> AssignmentSection(
                                state = state,
                                isTeacher = user.isTeacher,
                                onCreate = { showCreateAssignment = true },
                                onOpenSubmissions = {
                                    sheetKind = "assignment"
                                    sheetTargetId = it
                                    viewModel.loadAssignmentSubmissions(it)
                                },
                                onSubmit = { submissionTarget = it },
                                onPreview = { path -> previewUrl = path },
                            )

                            else -> if (user.isTeacher) {
                                StatsContent(
                                    stats = state.stats,
                                    loading = state.statsLoading,
                                    onRefresh = { viewModel.loadStats(courseId, force = true) },
                                )
                            } else {
                                StudentStatsContent(
                                    stats = state.studentStats,
                                    loading = state.studentStatsLoading,
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        )
        if (state.busy) {
            BlockingBusyOverlay()
        }
    }

    if (showCreateAttendance) {
        CreateAttendanceDialog(
            onDismiss = { showCreateAttendance = false },
            onSubmit = { request, onResult ->
                viewModel.createAttendance(courseId, request.title, request.durationMinutes, onResult)
            },
        )
    }
    if (showCreateQuestion) {
        CreateQuestionDialog(
            onDismiss = { showCreateQuestion = false },
            onSubmit = { request, onResult ->
                viewModel.createQuestion(courseId, request, onResult)
            },
        )
    }
    if (showCreateAssignment) {
        CreateAssignmentDialog(
            onDismiss = { showCreateAssignment = false },
            onSubmit = { request, onResult ->
                viewModel.createAssignment(courseId, request, onResult)
            },
        )
    }
    submissionTarget?.let { assignment ->
        AssignmentSubmitDialog(
            assignmentTitle = assignment.title,
            existingNote = assignment.mySubmission?.note,
            onDismiss = { submissionTarget = null },
            onSubmit = { bytes, filename, mime, note, onResult ->
                viewModel.uploadAssignment(
                    assignmentId = assignment.id,
                    courseId = courseId,
                    bytes = bytes,
                    filename = filename,
                    contentType = mime,
                    note = note,
                    onResult = onResult,
                )
            },
        )
    }

    state.course?.let { course ->
        if (showCourseSettings) {
            CourseSettingsDialog(
                course = course,
                isTeacher = user.isTeacher,
                onDismiss = { showCourseSettings = false },
                onEdit = { showCourseSettings = false; showEditCourse = true },
                onResetInvite = {
                    showCourseSettings = false
                    viewModel.resetInviteCode(courseId) {}
                },
                onArchive = {
                    showCourseSettings = false
                    viewModel.setArchived(courseId, !course.archived) {}
                },
                onDelete = { showCourseSettings = false; showDeleteCourseConfirm = true },
                onLeave = {
                    showCourseSettings = false
                    viewModel.leaveCourse(courseId) { error ->
                        if (error == null) onBack()
                    }
                },
            )
        }
        if (showEditCourse) {
            EditCourseDialog(
                course = course,
                onDismiss = { showEditCourse = false },
                onSubmit = { name, description, onResult ->
                    viewModel.updateCourse(courseId, name, description, onResult)
                },
            )
        }
        if (showDeleteCourseConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteCourseConfirm = false },
                containerColor = Cream,
                shape = RoundedCornerShape(24.dp),
                title = { Text("删除课程？", style = MaterialTheme.typography.headlineMedium) },
                text = { Text("课程将从列表隐藏并停止使用，历史数据保留。此操作不可在 APP 内撤销。", color = Muted) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteCourseConfirm = false
                        viewModel.deleteCourse(courseId) { error ->
                            if (error == null) onBack()
                        }
                    }) { Text("删除", color = Danger, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showDeleteCourseConfirm = false }) { Text("取消", color = Muted) } },
            )
        }
    }

    editQuestionTarget?.let { question ->
        EditQuestionDialog(
            question = question,
            onDismiss = { editQuestionTarget = null },
            onSubmit = { request, onResult ->
                viewModel.updateQuestion(courseId, question.id, request, onResult)
            },
        )
    }

    deleteQuestionTarget?.let { question ->
        AlertDialog(
            onDismissRequest = { deleteQuestionTarget = null },
            containerColor = Cream,
            shape = RoundedCornerShape(24.dp),
            title = { Text("删除题目？", style = MaterialTheme.typography.headlineMedium) },
            text = { Text("题目将从问答列表隐藏，已有作答记录保留。", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    val target = question
                    deleteQuestionTarget = null
                    viewModel.deleteQuestion(courseId, target.id) {}
                }) { Text("删除", color = Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteQuestionTarget = null }) { Text("取消", color = Muted) } },
        )
    }

    gradeTarget?.let { submission ->
        GradeSubmissionDialog(
            submission = submission,
            studentName = submission.studentName ?: "学生",
            onDismiss = { gradeTarget = null },
            onSubmit = { score, feedback, onResult ->
                viewModel.gradeSubmission(
                    assignmentId = submission.assignmentId,
                    submissionId = submission.id,
                    expectedVersion = submission.version,
                    score = score,
                    feedback = feedback,
                    onResult = onResult,
                )
            },
        )
    }

    removeMemberTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { removeMemberTarget = null },
            containerColor = Cream,
            shape = RoundedCornerShape(24.dp),
            title = { Text("移除学生？", style = MaterialTheme.typography.headlineMedium) },
            text = { Text("将 ${member.name} 从课程成员中移除，历史签到、答案和作业保留。", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    val target = member
                    removeMemberTarget = null
                    viewModel.removeMember(courseId, target.studentId) {}
                }) { Text("移除", color = Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { removeMemberTarget = null }) { Text("取消", color = Muted) } },
        )
    }

    temporaryPasswordInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { temporaryPasswordInfo = null },
            containerColor = Cream,
            shape = RoundedCornerShape(24.dp),
            title = { Text("临时密码已生成", style = MaterialTheme.typography.headlineMedium) },
            text = { Text(info, color = Ink) },
            confirmButton = { TextButton(onClick = { temporaryPasswordInfo = null }) { Text("知道了", color = Teal) } },
        )
    }

    if (sheetKind != null) {
        ModalBottomSheet(
            onDismissRequest = {
                sheetKind = null
                sheetTargetId = null
                viewModel.clearSheet()
            },
            sheetState = sheetState,
            containerColor = Paper,
            dragHandle = {
                Box(
                    Modifier.padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 42.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Line),
                )
            },
        ) {
            SheetContent(
                kind = sheetKind.orEmpty(),
                state = state,
                onClose = {
                    sheetKind = null
                    sheetTargetId = null
                    viewModel.clearSheet()
                },
                onPreview = { path -> previewUrl = path },
                onRemoveMember = { removeMemberTarget = it },
                onResetStudentPassword = { member ->
                    viewModel.resetStudentPassword(courseId, member.studentId) { result, error ->
                        if (result != null) {
                            temporaryPasswordInfo = "学生：${result.studentNo}\n临时密码：${result.temporaryPassword}\n\n请让学生登录后立即修改密码。"
                        } else if (error != null) {
                            scope.launch { snackbarHostState.showSnackbar(error) }
                        }
                    }
                },
                onGradeSubmission = { gradeTarget = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }

    if (previewUrl != null) {
        ImagePreviewDialog(url = requireNotNull(previewUrl), onDismiss = { previewUrl = null })
    }
}

@Composable
private fun CourseHero(
    course: CourseSummary,
    isTeacher: Boolean,
    onCopyInvite: () -> Unit,
    onOpenMembers: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Ink, TealDeep))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = Gold.copy(alpha = 0.08f),
                radius = size.width * 0.32f,
                center = Offset(size.width * 0.92f, size.height * 0.12f),
            )
            val path = Path().apply {
                moveTo(size.width * 0.58f, 0f)
                cubicTo(
                    size.width * 0.72f,
                    size.height * 0.25f,
                    size.width * 0.78f,
                    size.height * 0.5f,
                    size.width,
                    size.height * 0.46f,
                )
            }
            drawPath(
                path,
                Gold.copy(alpha = 0.4f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    text = if (isTeacher) "教师工作台" else "已加入课程",
                    containerColor = Cream.copy(alpha = 0.1f),
                    contentColor = Cream,
                    dotColor = Gold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "#${course.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cream.copy(alpha = 0.35f),
                    letterSpacing = 1.2.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                course.name,
                style = MaterialTheme.typography.headlineLarge,
                color = Cream,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                course.description,
                style = MaterialTheme.typography.bodySmall,
                color = Cream.copy(alpha = 0.65f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, tint = Cream.copy(alpha = 0.65f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    course.teacherName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Cream.copy(alpha = 0.78f),
                )
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Outlined.People, null, tint = Cream.copy(alpha = 0.65f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    "${course.memberCount} 名学生",
                    style = MaterialTheme.typography.labelMedium,
                    color = Cream.copy(alpha = 0.78f),
                )
            }
            if (isTeacher) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Cream.copy(alpha = 0.09f))
                        .clickable(onClick = onOpenMembers)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Groups, null, tint = Cream.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "查看学生名单",
                        style = MaterialTheme.typography.labelMedium,
                        color = Cream.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Outlined.ChevronRight, null, tint = Cream.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }
            if (isTeacher && course.inviteCode != null) {
                Spacer(Modifier.height(16.dp))
                CodeTicket(
                    label = "COURSE INVITE · 课程邀请码",
                    code = course.inviteCode,
                    onCopy = onCopyInvite,
                    accent = Gold,
                )
            }
        }
    }
}

@Composable
private fun CourseTabSwitcher(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Cream)
            .border(1.dp, Line, RoundedCornerShape(17.dp))
            .padding(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected == index) Ink else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected == index) Cream else Muted,
                )
            }
        }
    }
}

@Composable
private fun TabActionRow(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Cream),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AttendanceSection(
    courseId: Long,
    state: com.classpulse.app.ui.course.CourseUiState,
    isTeacher: Boolean,
    onCreate: () -> Unit,
    onOpenResults: (Long) -> Unit,
    onCheckIn: (Long, String, (String?) -> Unit) -> Unit,
) {
    Column {
        TabActionRow(
            title = "课堂签到",
            subtitle = if (isTeacher) "发起签到并查看实时到课名单" else "输入老师公布的签到码完成签到",
            actionLabel = if (isTeacher) "发起签到" else null,
            onAction = if (isTeacher) onCreate else null,
        )
        Spacer(Modifier.height(14.dp))
        if (state.attendance.isEmpty()) {
            EmptyState(
                title = "还没有签到",
                message = if (isTeacher) "发起第一次签到，课堂出勤会在这里留下记录。" else "老师发起签到后，这里会出现签到入口。",
                icon = { Icon(Icons.Outlined.Schedule, null, tint = Teal, modifier = Modifier.size(26.dp)) },
            )
        } else {
            state.attendance.forEachIndexed { index, item ->
                AttendanceCard(
                    item = item,
                    isTeacher = isTeacher,
                    onOpen = if (isTeacher) { { onOpenResults(item.id) } } else null,
                    onCheckIn = onCheckIn,
                )
                if (index != state.attendance.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AttendanceCard(
    item: Attendance,
    isTeacher: Boolean,
    onOpen: (() -> Unit)?,
    onCheckIn: (Long, String, (String?) -> Unit) -> Unit,
) {
    var code by rememberSaveable(item.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val rate = if (item.memberCount > 0) item.presentCount.toFloat() / item.memberCount else 0f

    PulseCard(
        onClick = onOpen,
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text = when {
                    item.active -> "进行中"
                    else -> "已结束"
                },
                containerColor = if (item.active) Mint else Paper,
                contentColor = if (item.active) TealDeep else Muted,
                dotColor = if (item.active) Teal else null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${Formats.dateTime(item.startsAt)} · ${Formats.deadline(item.endsAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
            Spacer(Modifier.weight(1f))
            if (isTeacher) {
                Icon(Icons.Outlined.ChevronRight, null, tint = Muted.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${item.presentCount}/${item.memberCount}",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
            )
            Spacer(Modifier.width(7.dp))
            Text("已签到", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.weight(1f))
            Text(
                "${(rate * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = Teal,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(7.dp))
        ProgressBar(fraction = rate, barColor = if (item.active) Teal else TealLight)

        if (isTeacher && item.code != null) {
            if (item.active) {
                var copied by remember { mutableStateOf(false) }
                val clipboard = LocalClipboardManager.current
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paper)
                        .clickable {
                            clipboard.setText(AnnotatedString(item.code))
                            copied = true
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Key, null, tint = Teal, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (copied) "已复制" else "签到码",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (copied) Success else Muted,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        item.code,
                        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp),
                        color = Ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ContentCopy, null, tint = Muted, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { onOpen?.invoke() }, contentPadding = PaddingValues(0.dp)) {
                Text("查看签到与未签到名单", style = MaterialTheme.typography.labelMedium, color = Teal)
            }
        }

        if (!isTeacher) {
            when {
                item.myCheckedIn -> {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuccessSoft)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = Success, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "已签到 · ${Formats.dateTime(item.myCheckedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Success,
                        )
                    }
                }

                item.active -> {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClassPulseField(
                            value = code,
                            onValueChange = {
                                code = it.filter(Char::isDigit).take(6)
                                error = null
                            },
                            label = "输入 6 位签到码",
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Icon(Icons.Outlined.Pin, null, tint = Teal) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Spacer(Modifier.width(9.dp))
                        PrimaryButton(
                            text = if (submitting) "签到中" else "签到",
                            onClick = {
                                submitting = true
                                error = null
                                onCheckIn(item.id, code) { checkInError ->
                                    submitting = false
                                    if (checkInError == null) code = "" else error = checkInError
                                }
                            },
                            enabled = !submitting && code.length == 6,
                        )
                    }
                    InlineError(error, modifier = Modifier.padding(top = 8.dp))
                }

                else -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "本次签到已结束",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionSection(
    state: com.classpulse.app.ui.course.CourseUiState,
    isTeacher: Boolean,
    onCreate: () -> Unit,
    onOpenStats: (Long) -> Unit,
    onEditQuestion: (Question) -> Unit,
    onCloseQuestion: (Question) -> Unit,
    onDeleteQuestion: (Question) -> Unit,
    onAnswer: (Long, String, (String?) -> Unit) -> Unit,
) {
    Column {
        TabActionRow(
            title = "课堂问答",
            subtitle = if (isTeacher) "发布单选或填空题，自动统计正确率" else "截止前作答，每题只有一次机会",
            actionLabel = if (isTeacher) "发布问题" else null,
            onAction = if (isTeacher) onCreate else null,
        )
        Spacer(Modifier.height(14.dp))
        if (state.questions.isEmpty()) {
            EmptyState(
                title = "还没有课堂问题",
                message = if (isTeacher) "发布一道题，实时看到学生的理解情况。" else "老师发布问题后，你可以在这里作答。",
                icon = { Icon(Icons.Outlined.Quiz, null, tint = Teal, modifier = Modifier.size(26.dp)) },
            )
        } else {
            state.questions.forEachIndexed { index, question ->
                if (isTeacher) {
                    TeacherQuestionCard(
                        question = question,
                        onOpen = { onOpenStats(question.id) },
                        onEdit = { onEditQuestion(question) },
                        onClose = { onCloseQuestion(question) },
                        onDelete = { onDeleteQuestion(question) },
                    )
                } else {
                    StudentQuestionCard(question = question, onAnswer = onAnswer)
                }
                if (index != state.questions.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun TeacherQuestionCard(
    question: Question,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    PulseCard(onClick = onOpen, contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text = Formats.questionType(question.type),
                containerColor = if (question.isChoice) BlueSoft else Mint,
                contentColor = if (question.isChoice) BlueDeep else TealDeep,
            )
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                text = if (Formats.deadline(question.deadlineAt) == "已截止") "已截止" else Formats.deadline(question.deadlineAt),
                containerColor = Paper,
                contentColor = Muted,
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(question.prompt, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (question.isChoice && question.options.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Text(
                question.options.joinToString("  ") { "${it.key}. ${it.text}" },
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "提交 ${question.submissionCount ?: 0} 人",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "正确 ${question.correctCount ?: 0} 人",
                style = MaterialTheme.typography.labelMedium,
                color = Success,
            )
            Spacer(Modifier.weight(1f))
            Text(
                Formats.percent(question.correctRate),
                style = MaterialTheme.typography.titleLarge,
                color = Coral,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(7.dp))
        ProgressBar(
            fraction = (question.correctRate ?: 0.0).toFloat(),
            barColor = Coral,
            trackColor = Coral.copy(alpha = 0.12f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "参考答案：${Formats.answerDisplay(question.type, question.correctAnswer, question.options)}",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) { Text("编辑", color = Teal) }
            TextButton(onClick = onClose) { Text("提前截止", color = Muted) }
            TextButton(onClick = onDelete) { Text("删除", color = Danger) }
        }
    }
}

@Composable
private fun StudentQuestionCard(
    question: Question,
    onAnswer: (Long, String, (String?) -> Unit) -> Unit,
) {
    var selectedKeys by remember(question.id) { mutableStateOf(emptySet<String>()) }
    var fillAnswer by rememberSaveable(question.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val active = Formats.parse(question.deadlineAt)?.isAfter(Instant.now()) == true

    PulseCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text = Formats.questionType(question.type),
                containerColor = if (question.isChoice) BlueSoft else Mint,
                contentColor = if (question.isChoice) BlueDeep else TealDeep,
            )
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                text = when {
                    question.answered -> "已作答"
                    active -> Formats.deadline(question.deadlineAt)
                    else -> "已截止"
                },
                containerColor = when {
                    question.answered -> SuccessSoft
                    active -> Paper
                    else -> DangerSoft
                },
                contentColor = when {
                    question.answered -> Success
                    active -> Muted
                    else -> Danger
                },
                dotColor = if (question.answered) Success else null,
            )
        }
        Spacer(Modifier.height(11.dp))
        Text(question.prompt, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(13.dp))

        if (question.answered) {
            val correct = question.myIsCorrect == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (correct) SuccessSoft else CoralSoft)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (correct) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                    null,
                    tint = if (correct) Success else Coral,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (correct) "回答正确" else "回答未正确",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (correct) Success else Coral,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "我的答案：${Formats.answerDisplay(question.type, question.myAnswer, question.options)}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            if (question.correctAnswer != null) {
                Text(
                    "参考答案：${Formats.answerDisplay(question.type, question.correctAnswer, question.options)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        } else if (active) {
            if (question.isChoice) {
                if (question.isMultipleChoice) {
                    Text(
                        "可多选；选出全部正确项才计分。",
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                question.options.forEach { option ->
                    val selected = option.key in selectedKeys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (selected) Mint else Paper)
                            .border(
                                1.dp,
                                if (selected) Teal else Color.Transparent,
                                RoundedCornerShape(13.dp),
                            )
                            .clickable {
                                selectedKeys = if (question.isMultipleChoice) {
                                    if (selected) selectedKeys - option.key else selectedKeys + option.key
                                } else {
                                    setOf(option.key)
                                }
                                error = null
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (selected) Teal else Cream),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                option.key,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Cream else InkSoft,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(option.text, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    }
                }
            } else {
                ClassPulseField(
                    value = fillAnswer,
                    onValueChange = { fillAnswer = it; error = null },
                    label = "你的答案",
                    placeholder = "输入答案，忽略首尾空格与英文大小写",
                    singleLine = false,
                    minLines = 2,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "每题仅可提交一次",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                )
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    text = if (submitting) "提交中" else "提交答案",
                    onClick = {
                        val answer = if (question.isChoice) {
                            selectedKeys.sorted().joinToString(",")
                        } else {
                            fillAnswer
                        }
                        if (answer.isBlank()) {
                            error = if (question.isMultipleChoice) "请至少选择一个选项" else if (question.isChoice) "请选择一个选项" else "请填写答案"
                            return@PrimaryButton
                        }
                        submitting = true
                        error = null
                        onAnswer(question.id, answer) { answerError ->
                            submitting = false
                            if (answerError != null) error = answerError
                        }
                    },
                    enabled = !submitting,
                )
            }
            InlineError(error, modifier = Modifier.padding(top = 8.dp))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(Paper)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("本题已截止，未提交答案", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
private fun AssignmentSection(
    state: com.classpulse.app.ui.course.CourseUiState,
    isTeacher: Boolean,
    onCreate: () -> Unit,
    onOpenSubmissions: (Long) -> Unit,
    onSubmit: (Assignment) -> Unit,
    onPreview: (String) -> Unit,
) {
    Column {
        TabActionRow(
            title = "拍照作业",
            subtitle = if (isTeacher) "查看提交情况与作业图片" else "拍摄或选择图片，截止前可替换",
            actionLabel = if (isTeacher) "发布作业" else null,
            onAction = if (isTeacher) onCreate else null,
        )
        Spacer(Modifier.height(14.dp))
        if (state.assignments.isEmpty()) {
            EmptyState(
                title = "还没有作业",
                message = if (isTeacher) "发布一项拍照作业，学生提交的图片会汇总在这里。" else "老师发布作业后，你可以拍照或从相册提交。",
                icon = { Icon(Icons.AutoMirrored.Outlined.Assignment, null, tint = Teal, modifier = Modifier.size(26.dp)) },
            )
        } else {
            state.assignments.forEachIndexed { index, assignment ->
                AssignmentCard(
                    assignment = assignment,
                    isTeacher = isTeacher,
                    onOpen = { onOpenSubmissions(assignment.id) },
                    onSubmit = { onSubmit(assignment) },
                    onPreview = onPreview,
                )
                if (index != state.assignments.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AssignmentCard(
    assignment: Assignment,
    isTeacher: Boolean,
    onOpen: () -> Unit,
    onSubmit: () -> Unit,
    onPreview: (String) -> Unit,
) {
    val rate = if (assignment.memberCount > 0) assignment.submissionCount.toFloat() / assignment.memberCount else 0f
    PulseCard(
        onClick = if (isTeacher) onOpen else null,
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text = if (assignment.active) Formats.deadline(assignment.deadlineAt) else "已截止",
                containerColor = if (assignment.active) Mint else Paper,
                contentColor = if (assignment.active) TealDeep else Muted,
                dotColor = if (assignment.active) Teal else null,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "截止 ${Formats.dateTime(assignment.deadlineAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(assignment.title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            assignment.description,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${assignment.submissionCount}/${assignment.memberCount}",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
            )
            Spacer(Modifier.width(6.dp))
            Text("已提交", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.weight(1f))
            Text(
                Formats.percent(rate.toDouble()),
                style = MaterialTheme.typography.titleMedium,
                color = Gold,
                fontWeight = FontWeight.Bold,
            )
            if (isTeacher) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, null, tint = Muted.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        ProgressBar(fraction = rate, barColor = Gold, trackColor = Gold.copy(alpha = 0.14f))

        if (!isTeacher) {
            val submission = assignment.mySubmission
            if (submission != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Paper)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssignmentThumbnail(
                        path = submission.imageUrl,
                        modifier = Modifier.size(58.dp),
                        onClick = { onPreview(submission.imageUrl) },
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "已提交最新版本",
                            style = MaterialTheme.typography.titleSmall,
                            color = Success,
                        )
                        Text(
                            "${Formats.dateTime(submission.updatedAt)} · ${Formats.fileSize(submission.fileSize)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                        )
                        if (!submission.note.isNullOrBlank()) {
                            Text(
                                submission.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        submission.score?.let { score ->
                            Text("得分：$score", style = MaterialTheme.typography.labelMedium, color = Teal, fontWeight = FontWeight.Bold)
                        }
                        if (!submission.feedback.isNullOrBlank()) {
                            Text(
                                "评语：${submission.feedback}",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (assignment.active) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = if (submission == null) "拍照 / 选择图片提交" else "替换我的提交",
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            if (submission == null) Icons.Outlined.AddAPhoto else Icons.Outlined.SwapHoriz,
                            null,
                            modifier = Modifier.size(17.dp),
                        )
                    },
                )
            } else if (submission == null) {
                Spacer(Modifier.height(10.dp))
                Text("作业已截止，未提交", style = MaterialTheme.typography.bodySmall, color = Danger)
            }
        }
    }
}

@Composable
private fun AssignmentThumbnail(path: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val app = LocalContext.current.applicationContext as ClassPulseApplication
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(app.container.repository.imageUrl(path))
            .crossfade(true)
            .build(),
        imageLoader = app.container.imageLoader,
        contentDescription = "作业图片",
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun SheetContent(
    kind: String,
    state: com.classpulse.app.ui.course.CourseUiState,
    onClose: () -> Unit,
    onPreview: (String) -> Unit,
    onRemoveMember: (Member) -> Unit,
    onResetStudentPassword: (Member) -> Unit,
    onGradeSubmission: (Submission) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        when {
            state.sheetLoading -> LoadingPane("正在加载详情…")
            state.sheetError != null -> {
                InlineError(state.sheetError)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("关闭", onClick = onClose)
            }

            kind == "members" -> {
                SheetHeader(
                    title = "学生名单",
                    subtitle = "${state.members.size} 名学生 · 按加入时间排序",
                    onClose = onClose,
                )
                Spacer(Modifier.height(12.dp))
                if (state.members.isEmpty()) {
                    Text("暂无学生加入课程", style = MaterialTheme.typography.bodySmall, color = Muted)
                } else {
                    state.members.forEach { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AvatarCircle(member.name, size = 34.dp, containerColor = Teal)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(member.name, style = MaterialTheme.typography.bodyMedium, color = Ink)
                                Text(member.studentNo, style = MaterialTheme.typography.labelMedium, color = Muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "加入于 ${Formats.date(member.joinedAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Muted,
                                )
                                Row {
                                    TextButton(onClick = { onResetStudentPassword(member) }) {
                                        Text("重置密码", style = MaterialTheme.typography.labelSmall, color = Teal)
                                    }
                                    TextButton(onClick = { onRemoveMember(member) }) {
                                        Text("移除", style = MaterialTheme.typography.labelSmall, color = Danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            kind == "attendance" && state.attendanceDetail != null -> {
                val detail = requireNotNull(state.attendanceDetail)
                SheetHeader(
                    title = detail.attendance.title,
                    subtitle = "${detail.attendance.presentCount}/${detail.attendance.memberCount} 人已签到",
                    onClose = onClose,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetStat("已签到", "${detail.present.size}", Success, Modifier.weight(1f))
                    SheetStat("未签到", "${detail.absent.size}", Coral, Modifier.weight(1f))
                    SheetStat("签到率", Formats.percent(if (detail.attendance.memberCount == 0L) 0.0 else detail.attendance.presentCount.toDouble() / detail.attendance.memberCount), Teal, Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                SheetListTitle("已签到", "按签到时间排序")
                if (detail.present.isEmpty()) {
                    Text("暂无学生签到", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(vertical = 10.dp))
                } else {
                    detail.present.forEach { person ->
                        PersonRow(person, present = true)
                    }
                }
                Spacer(Modifier.height(16.dp))
                SheetListTitle("未签到", "需要关注的名单")
                if (detail.absent.isEmpty()) {
                    Text("全部学生已签到", style = MaterialTheme.typography.bodySmall, color = Success, modifier = Modifier.padding(vertical = 10.dp))
                } else {
                    detail.absent.forEach { person -> PersonRow(person, present = false) }
                }
            }

            kind == "question" && state.questionStats != null -> {
                val stats = requireNotNull(state.questionStats)
                SheetHeader(
                    title = "作答情况",
                    subtitle = "${stats.question.submissionCount ?: 0} 人提交 · 正确率 ${Formats.percent(stats.question.correctRate)}",
                    onClose = onClose,
                )
                Spacer(Modifier.height(12.dp))
                Text(stats.question.prompt, style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(stats.conclusion, style = MaterialTheme.typography.bodySmall, color = Muted)
                if (stats.optionDistribution.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SheetListTitle("选项分布", "按选择人数")
                    stats.optionDistribution.forEach { option ->
                        val total = stats.optionDistribution.sumOf { it.count }.coerceAtLeast(1)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option.key, style = MaterialTheme.typography.labelLarge, color = Teal, modifier = Modifier.width(22.dp))
                            Text(option.text, style = MaterialTheme.typography.bodySmall, color = InkSoft, modifier = Modifier.weight(1f))
                            Text("${option.count} 人", style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                        ProgressBar(fraction = option.count.toFloat() / total, barColor = Teal, height = 6.dp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                SheetListTitle("学生答案", "共 ${stats.answerDetails.size} 条")
                if (stats.answerDetails.isEmpty()) {
                    Text("暂无学生作答", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(vertical = 10.dp))
                } else {
                    stats.answerDetails.forEach { answer ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(if (answer.isCorrect) SuccessSoft else CoralSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (answer.isCorrect) Icons.Outlined.Check else Icons.Outlined.Close,
                                    null,
                                    tint = if (answer.isCorrect) Success else Coral,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(answer.studentName, style = MaterialTheme.typography.bodyMedium, color = Ink, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(6.dp))
                                    Text(answer.studentNo, style = MaterialTheme.typography.labelMedium, color = Muted)
                                }
                                Text(
                                    Formats.answerDisplay(stats.question.type, answer.answer, stats.question.options),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSoft,
                                )
                            }
                        }
                    }
                }
            }

            kind == "assignment" && state.assignmentSubmissions != null -> {
                val submissions = requireNotNull(state.assignmentSubmissions)
                SheetHeader(
                    title = submissions.assignment.title,
                    subtitle = "已提交 ${submissions.submittedCount} 人 · 未提交 ${submissions.missingCount} 人",
                    onClose = onClose,
                )
                Spacer(Modifier.height(14.dp))
                submissions.rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (row.submission != null) {
                            AssignmentThumbnail(
                                path = row.submission.imageUrl,
                                modifier = Modifier.size(54.dp),
                                onClick = { onPreview(requireNotNull(row.submission).imageUrl) },
                            )
                        } else {
                            Box(
                                Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(Paper),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.ImageNotSupported, null, tint = Muted.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(row.studentName, style = MaterialTheme.typography.bodyMedium, color = Ink, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(6.dp))
                                Text(row.studentNo, style = MaterialTheme.typography.labelMedium, color = Muted)
                            }
                            if (row.submission != null) {
                                Text(
                                    "${Formats.dateTime(row.submission.updatedAt)} · ${Formats.fileSize(row.submission.fileSize)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Muted,
                                )
                                if (!row.submission.note.isNullOrBlank()) {
                                    Text(
                                        row.submission.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSoft,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Text("未提交", style = MaterialTheme.typography.bodySmall, color = Coral)
                            }
                        }
                        if (row.submission != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                StatusBadge("已提交", containerColor = SuccessSoft, contentColor = Success, dotColor = Success)
                                TextButton(onClick = { onGradeSubmission(requireNotNull(row.submission)) }) {
                                    Text(
                                        if (row.submission.score != null) "改评分" else "评分",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Teal,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> LoadingPane()
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String, onClose: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, null, tint = Muted)
        }
    }
}

@Composable
private fun SheetStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Cream)
            .padding(12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

@Composable
private fun SheetListTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

@Composable
private fun PersonRow(person: AttendancePerson, present: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(person.name, size = 32.dp, containerColor = if (present) Teal else Muted.copy(alpha = 0.55f))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyMedium, color = Ink)
            Text(person.studentNo, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        Text(
            if (present) Formats.dateTime(person.checkedAt) else "未签到",
            style = MaterialTheme.typography.labelMedium,
            color = if (present) Success else Coral,
        )
    }
}

@Composable
private fun BlockingBusyOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.18f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Cream,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = Teal,
                )
                Spacer(Modifier.width(12.dp))
                Text("正在同步课堂数据…", style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(url: String, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as ClassPulseApplication
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE0A1618))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(app.container.repository.imageUrl(url))
                    .crossfade(true)
                    .build(),
                imageLoader = app.container.imageLoader,
                contentDescription = "作业图片预览",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            Surface(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).size(42.dp),
                shape = CircleShape,
                color = Cream.copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "关闭", tint = Cream)
                }
            }
        }
    }
}
