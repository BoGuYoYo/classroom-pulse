package com.classpulse.app.ui.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formats {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
        .withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
        .withZone(ZoneId.systemDefault())
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
        .withZone(ZoneId.systemDefault())

    fun parse(value: String?): Instant? =
        value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun dateTime(value: String?): String =
        parse(value)?.let { dateTimeFormatter.format(it) } ?: "—"

    fun date(value: String?): String =
        parse(value)?.let { dateFormatter.format(it) } ?: "—"

    fun fullDate(value: String?): String =
        parse(value)?.let { fullDateFormatter.format(it) } ?: "—"

    fun deadline(value: String?): String {
        val instant = parse(value) ?: return "—"
        val duration = Duration.between(Instant.now(), instant)
        if (duration.isNegative || duration.isZero) return "已截止"
        val hours = duration.toHours()
        val minutes = duration.toMinutes()
        return when {
            minutes < 60 -> "剩 $minutes 分钟"
            hours < 24 -> "剩 $hours 小时"
            else -> "剩 ${duration.toDays()} 天"
        }
    }

    fun percent(rate: Double?): String =
        String.format(Locale.CHINA, "%.1f%%", (rate ?: 0.0) * 100)

    fun fileSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(Locale.CHINA, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun role(role: String): String = if (role == "TEACHER") "教师" else "学生"

    fun questionType(type: String): String = when (type) {
        "SINGLE_CHOICE" -> "单选题"
        "MULTIPLE_CHOICE" -> "多选题"
        else -> "填空题"
    }

    fun greeting(): String {
        val hour = java.time.LocalTime.now().hour
        return when (hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..18 -> "下午好"
            else -> "晚上好"
        }
    }

    fun initial(name: String): String = name.trim().take(1).ifBlank { "课" }

    fun shortPrompt(prompt: String, max: Int = 30): String =
        if (prompt.length <= max) prompt else prompt.take(max) + "…"

    fun answerDisplay(questionType: String, answer: String?, options: List<com.classpulse.app.data.model.QuestionOption>): String {
        if (answer.isNullOrBlank()) return "未作答"
        if (questionType == "SINGLE_CHOICE" || questionType == "MULTIPLE_CHOICE") {
            val labels = answer.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { key ->
                    options.firstOrNull { it.key == key }?.let { "$key. ${it.text}" } ?: key
                }
            return labels.joinToString("；").ifBlank { answer }
        }
        return answer
    }
}
