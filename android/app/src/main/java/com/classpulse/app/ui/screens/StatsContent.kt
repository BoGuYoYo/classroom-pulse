package com.classpulse.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classpulse.app.data.model.*
import com.classpulse.app.ui.components.*
import com.classpulse.app.ui.theme.*
import com.classpulse.app.ui.util.Formats
import kotlin.math.roundToInt

@Composable
fun StatsContent(
    stats: CourseStats?,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "LEARNING SIGNALS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text("教学分析", style = MaterialTheme.typography.headlineMedium, color = Ink)
            }
            RefreshIconButton(onClick = onRefresh)
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading && stats == null -> LoadingPane("正在聚合课堂数据…")
            stats == null -> EmptyState(
                title = "还没有统计数据",
                message = "发起签到、发布课堂问题或作业后，这里会自动生成分析。",
                icon = { Icon(Icons.Outlined.Insights, null, tint = Teal, modifier = Modifier.size(26.dp)) },
                action = { SecondaryButton("加载统计", onClick = onRefresh) },
            )

            else -> {
                OverallStatsCard(stats)
                Spacer(Modifier.height(14.dp))
                val hasQuestionAnswers = stats.questions.items.any { it.submissionCount > 0 }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        label = "签到率",
                        value = Formats.percent(stats.attendance.averageRate),
                        color = Teal,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "问答正确率",
                        value = if (hasQuestionAnswers) Formats.percent(stats.questions.averageCorrectRate) else "—",
                        color = Coral,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "作业提交率",
                        value = Formats.percent(stats.assignments.averageRate),
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(26.dp))
                ChartSectionTitle("签到分析", "每次签到 · 分母为课程成员数", Teal)
                Spacer(Modifier.height(12.dp))
                PulseCard(contentPadding = PaddingValues(18.dp)) {
                    AttendanceChart(stats.attendance.items)
                    Spacer(Modifier.height(16.dp))
                    AttendanceLegendList(stats.attendance.items)
                }
                Spacer(Modifier.height(10.dp))
                ConclusionCard(text = stats.attendance.conclusion, accent = Teal)

                Spacer(Modifier.height(26.dp))
                ChartSectionTitle("问答分析", "正确率按实际作答人数计算", Coral)
                Spacer(Modifier.height(12.dp))
                PulseCard(contentPadding = PaddingValues(18.dp)) {
                    if (stats.questions.items.isEmpty()) {
                        Text("暂无课堂问题", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    } else if (!hasQuestionAnswers) {
                        Text("暂无作答数据", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val answered = stats.questions.items.filter { it.submissionCount > 0 }
                        answered.forEachIndexed { index, item ->
                            QuestionMetricRow(index + 1, item, Coral)
                            if (index != answered.lastIndex) {
                                Spacer(Modifier.height(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ConclusionCard(text = stats.questions.conclusion, accent = Coral)

                Spacer(Modifier.height(26.dp))
                ChartSectionTitle("作业分析", "课程成员为提交率分母", Gold)
                Spacer(Modifier.height(12.dp))
                PulseCard(contentPadding = PaddingValues(18.dp)) {
                    if (stats.assignments.items.isEmpty()) {
                        Text("暂无作业", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        stats.assignments.items.forEachIndexed { index, item ->
                            AssignmentMetricRow(index + 1, item)
                            if (index != stats.assignments.items.lastIndex) {
                                Spacer(Modifier.height(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ConclusionCard(text = stats.assignments.conclusion, accent = Gold)

                Spacer(Modifier.height(22.dp))
                MethodNoteCard(stats)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun OverallStatsCard(stats: CourseStats) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Ink),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = Mint.copy(alpha = 0.08f),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.9f, size.height * 0.15f),
            )
            drawCircle(
                color = Gold.copy(alpha = 0.08f),
                radius = size.width * 0.25f,
                center = Offset(size.width * 0.7f, size.height * 1.1f),
            )
        }
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RateRing(rate = stats.summary.overallRate, modifier = Modifier.size(112.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "有数据指标均值",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stats.courseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Cream,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${stats.memberCount} 名学生 · 数据更新于 ${Formats.dateTime(stats.generatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cream.copy(alpha = 0.62f),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge("签到", containerColor = Cream.copy(alpha = 0.1f), contentColor = Mint, dotColor = Teal)
                    StatusBadge("问答", containerColor = Cream.copy(alpha = 0.1f), contentColor = CoralSoft, dotColor = Coral)
                    StatusBadge("作业", containerColor = Cream.copy(alpha = 0.1f), contentColor = GoldSoft, dotColor = Gold)
                }
            }
        }
    }
}

@Composable
private fun RateRing(rate: Double, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = Cream.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = Gold,
                startAngle = -90f,
                sweepAngle = (rate.coerceIn(0.0, 1.0) * 360).toFloat(),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Formats.percent(rate),
                style = MaterialTheme.typography.headlineMedium,
                color = Cream,
            )
            Text("综合达成", style = MaterialTheme.typography.labelSmall, color = Cream.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Cream)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
    }
}

@Composable
private fun ChartSectionTitle(title: String, subtitle: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(34.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun AttendanceChart(items: List<AttendanceStatItem>) {
    if (items.isEmpty()) {
        Text("暂无签到数据", color = Muted, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val colors = listOf(Teal, TealLight, Coral, Gold)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val bottomPadding = 8.dp.toPx()
            val topPadding = 24.dp.toPx()
            val chartHeight = size.height - topPadding - bottomPadding
            val slotWidth = size.width / items.size
            val barWidth = (slotWidth * 0.42f).coerceAtMost(34.dp.toPx())
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = topPadding + chartHeight * (1f - fraction)
                drawLine(
                    color = Line.copy(alpha = 0.8f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            items.forEachIndexed { index, item ->
                val centerX = slotWidth * index + slotWidth / 2f
                val barHeight = chartHeight * item.rate.toFloat().coerceIn(0f, 1f)
                val top = topPadding + chartHeight - barHeight
                drawRoundRect(
                    color = colors[index % colors.size].copy(alpha = 0.18f),
                    topLeft = Offset(centerX - barWidth / 2, topPadding),
                    size = Size(barWidth, chartHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
                drawRoundRect(
                    color = colors[index % colors.size],
                    topLeft = Offset(centerX - barWidth / 2, top),
                    size = Size(barWidth, barHeight.coerceAtLeast(3.dp.toPx())),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
                drawCircle(
                    color = Cream,
                    radius = 2.5.dp.toPx(),
                    center = Offset(centerX, top + 10.dp.toPx()),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, _ ->
            Text(
                "第 ${index + 1} 次",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AttendanceLegendList(items: List<AttendanceStatItem>) {
    Column {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(Teal.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = Teal)
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.presentCount}/${item.expectedCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    Formats.percent(item.rate),
                    style = MaterialTheme.typography.labelMedium,
                    color = Teal,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun QuestionMetricRow(index: Int, item: QuestionStatItem, accent: Color) {
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$index", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Spacer(Modifier.width(9.dp))
            Text(
                item.prompt,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                Formats.percent(item.correctRate),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(9.dp))
        ProgressBar(
            fraction = item.correctRate.toFloat(),
            barColor = accent,
            trackColor = accent.copy(alpha = 0.12f),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "提交 ${item.submissionCount} 人 · 正确 ${item.correctCount} 人 · 分母为实际作答人数",
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
        )
    }
}

@Composable
private fun AssignmentMetricRow(index: Int, item: AssignmentStatItem) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(index),
                style = MaterialTheme.typography.labelMedium,
                color = Gold,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(9.dp))
            Text(
                item.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${item.submissionCount}/${item.expectedCount}",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                Formats.percent(item.rate),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(9.dp))
        ProgressBar(
            fraction = item.rate.toFloat(),
            barColor = Gold,
            trackColor = Gold.copy(alpha = 0.15f),
        )
    }
}

@Composable
private fun ConclusionCard(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Cream)
            .padding(15.dp),
    ) {
        Box(Modifier.width(4.dp).heightIn(min = 44.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "自动结论 · 来自实时查询",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

@Composable
private fun MethodNoteCard(stats: CourseStats) {
    PulseCard(
        containerColor = Mint.copy(alpha = 0.35f),
        borderColor = MintDark.copy(alpha = 0.45f),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.BarChart, null, tint = Teal, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text("统计口径", style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "签到率 = 已签到人数 / 课程成员数；问答正确率 = 正确人数 / 实际作答人数；作业提交率 = 提交人数 / 课程成员数。题目平均正确率只统计有作答的题目。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "数据生成时间 ${Formats.dateTime(stats.generatedAt)} · 课程成员 ${stats.memberCount} 人",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                )
            }
        }
    }
}
