package com.classpulse.service

import com.classpulse.domain.QuestionType
import com.classpulse.repository.*
import com.classpulse.web.ApiException
import com.classpulse.web.dto.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale
import kotlin.math.round

@Service
class StatsService(
    private val courseRepository: CourseRepository,
    private val courseMemberRepository: CourseMemberRepository,
    private val attendanceSessionRepository: AttendanceSessionRepository,
    private val attendanceRecordRepository: AttendanceRecordRepository,
    private val questionRepository: QuestionRepository,
    private val answerRepository: AnswerRepository,
    private val assignmentRepository: AssignmentRepository,
    private val submissionRepository: SubmissionRepository,
    private val accessGuard: AccessGuard,
) {
    @Transactional(readOnly = true)
    fun courseStats(courseId: Long): CourseStatsDto {
        val course = accessGuard.teacherCourse(courseId)
        val memberIds = courseMemberRepository.findByCourseIdOrderByJoinedAtAsc(courseId).mapNotNull { it.student.id }
        val memberCount = memberIds.size.toLong()

        val attendanceItems = attendanceSessionRepository.findByCourseIdOrderByStartsAtDesc(courseId)
            .sortedBy { it.startsAt }
            .map { session ->
                val present = attendanceRecordRepository.countBySessionIdAndStudentIdIn(
                    requireNotNull(session.id),
                    memberIds,
                )
                AttendanceStatItemDto(
                    id = requireNotNull(session.id),
                    title = session.title,
                    startsAt = session.startsAt,
                    presentCount = present,
                    expectedCount = memberCount,
                    rate = rate(present, memberCount),
                )
            }
        val attendanceAverage = average(attendanceItems.map { it.rate })
        val lowestAttendance = attendanceItems.minByOrNull { it.rate }
        val attendanceConclusion = if (attendanceItems.isEmpty()) {
            "暂无签到数据。"
        } else {
            "已进行 ${attendanceItems.size} 次签到，平均签到率 ${percent(attendanceAverage)}，分母为课程成员 $memberCount 人。" +
                (lowestAttendance?.let {
                    "最低的是「${it.title}」：${it.presentCount}/${it.expectedCount}，${percent(it.rate)}。"
                } ?: "")
        }

        val questionItems = questionRepository.findByCourseIdAndDeletedAtIsNullOrderByCreatedAtDesc(courseId).map { question ->
            val id = requireNotNull(question.id)
            val submissions = answerRepository.countByQuestionIdAndStudentIdIn(id, memberIds)
            val correct = answerRepository.countByQuestionIdAndIsCorrectTrueAndStudentIdIn(id, memberIds)
            QuestionStatItemDto(
                id = id,
                prompt = question.prompt,
                type = question.type,
                submissionCount = submissions,
                correctCount = correct,
                correctRate = rate(correct, submissions),
            )
        }.sortedBy { it.id }
        val answeredQuestionItems = questionItems.filter { it.submissionCount > 0 }
        val questionAverage = average(answeredQuestionItems.map { it.correctRate })
        val totalSubmissions = questionItems.sumOf { it.submissionCount }
        val lowestQuestion = answeredQuestionItems.minByOrNull { it.correctRate }
        val questionConclusion = when {
            questionItems.isEmpty() -> "暂无问答数据。"
            answeredQuestionItems.isEmpty() -> "暂无作答数据。"
            else -> {
                "共 ${questionItems.size} 道题、$totalSubmissions 次作答；" +
                    "已作答题目平均正确率 ${percent(questionAverage)}（按有作答的题目取算术平均，未作答题目不参与）。" +
                    (lowestQuestion?.let {
                        "正确率最低的是「${snippet(it.prompt)}」：${it.correctCount}/${it.submissionCount}，${percent(it.correctRate)}。"
                    } ?: "")
            }
        }

        val assignmentItems = assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId)
            .sortedBy { it.id }
            .map { assignment ->
                val id = requireNotNull(assignment.id)
                val submitted = submissionRepository.countByAssignmentIdAndStudentIdIn(id, memberIds)
                AssignmentStatItemDto(
                    id = id,
                    title = assignment.title,
                    submissionCount = submitted,
                    expectedCount = memberCount,
                    rate = rate(submitted, memberCount),
                )
            }
        val assignmentAverage = average(assignmentItems.map { it.rate })
        val lowestAssignment = assignmentItems.minByOrNull { it.rate }
        val assignmentConclusion = if (assignmentItems.isEmpty()) {
            "暂无作业数据。"
        } else {
            "共 ${assignmentItems.size} 项作业，平均提交率 ${percent(assignmentAverage)}，分母为课程成员 $memberCount 人。" +
                (lowestAssignment?.let {
                    "提交率最低的是「${it.title}」：${it.submissionCount}/${it.expectedCount}，${percent(it.rate)}。"
                } ?: "")
        }

        val availableAverages = buildList {
            if (attendanceItems.isNotEmpty()) add(attendanceAverage)
            if (answeredQuestionItems.isNotEmpty()) add(questionAverage)
            if (assignmentItems.isNotEmpty()) add(assignmentAverage)
        }
        val overallRate = average(availableAverages)
        val questionSummary = if (answeredQuestionItems.isEmpty()) {
            "问答暂无作答数据"
        } else {
            "问答正确率 ${percent(questionAverage)}"
        }
        val highlights = buildList {
            add("签到 ${percent(attendanceAverage)} · $questionSummary · 作业提交率 ${percent(assignmentAverage)}")
            add("综合达成 ${percent(overallRate)}（仅对有数据的模块取平均）；问答平均正确率按有作答的题目取算术平均，未作答题目不参与。")
            add("全部统计由数据库实时聚合生成，没有预置图表数值。")
        }

        return CourseStatsDto(
            courseId = courseId,
            courseName = course.name,
            memberCount = memberCount,
            generatedAt = Instant.now(),
            attendance = AttendanceStatsDto(
                items = attendanceItems,
                averageRate = attendanceAverage,
                conclusion = attendanceConclusion,
            ),
            questions = QuestionStatsSummaryDto(
                items = questionItems,
                averageCorrectRate = questionAverage,
                totalSubmissions = totalSubmissions,
                conclusion = questionConclusion,
            ),
            assignments = AssignmentStatsDto(
                items = assignmentItems,
                averageRate = assignmentAverage,
                conclusion = assignmentConclusion,
            ),
            summary = CourseSummaryStatsDto(
                overallRate = overallRate,
                highlights = highlights,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun studentCourseStats(courseId: Long): StudentCourseStatsDto {
        val principal = accessGuard.requireStudent()
        val course = accessGuard.studentCourse(courseId)
        return buildStudentStats(courseId, principal.id, course)
    }

    @Transactional(readOnly = true)
    fun studentCourseStatsForAdmin(courseId: Long, studentId: Long): StudentCourseStatsDto {
        val course = courseRepository.findById(courseId).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在")
        }
        return buildStudentStats(courseId, studentId, course)
    }

    private fun buildStudentStats(
        courseId: Long,
        studentId: Long,
        course: com.classpulse.domain.Course,
    ): StudentCourseStatsDto {
        val sessions = attendanceSessionRepository.findByCourseIdOrderByStartsAtDesc(courseId)
            .sortedBy { it.startsAt }
        val attendance = sessions.map { session ->
            val record = attendanceRecordRepository.findBySessionIdAndStudentId(
                requireNotNull(session.id),
                studentId,
            )
            StudentAttendanceItemDto(
                id = requireNotNull(session.id),
                title = session.title,
                startsAt = session.startsAt,
                checkedIn = record != null,
                checkedAt = record?.checkedAt,
            )
        }
        val questions = questionRepository.findByCourseIdAndDeletedAtIsNullOrderByCreatedAtDesc(courseId)
            .sortedBy { it.id }
        val questionItems = questions.map { question ->
            val answer = answerRepository.findByQuestionIdAndStudentId(requireNotNull(question.id), studentId)
            StudentQuestionItemDto(
                id = requireNotNull(question.id),
                prompt = question.prompt,
                type = question.type,
                answered = answer != null,
                correct = answer?.isCorrect,
                myAnswer = answer?.answerText,
            )
        }
        val assignments = assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId).sortedBy { it.id }
        val assignmentItems = assignments.map { assignment ->
            val submission = submissionRepository.findByAssignmentIdAndStudentId(
                requireNotNull(assignment.id),
                studentId,
            )
            StudentAssignmentItemDto(
                id = requireNotNull(assignment.id),
                title = assignment.title,
                deadlineAt = assignment.deadlineAt,
                submitted = submission != null,
                submittedAt = submission?.submittedAt,
                updatedAt = submission?.updatedAt,
                score = submission?.score,
                feedback = submission?.feedback,
            )
        }
        val answeredCount = questionItems.count { it.answered }.toLong()
        val correctCount = questionItems.count { it.correct == true }.toLong()
        val submittedCount = assignmentItems.count { it.submitted }.toLong()
        val gradedScores = assignmentItems.mapNotNull { it.score }
        return StudentCourseStatsDto(
            courseId = courseId,
            courseName = course.name,
            attendance = attendance,
            attendanceCount = attendance.count { it.checkedIn }.toLong(),
            attendanceTotal = attendance.size.toLong(),
            attendanceRate = rate(attendance.count { it.checkedIn }.toLong(), attendance.size.toLong()),
            questions = questionItems,
            answeredCount = answeredCount,
            correctCount = correctCount,
            correctnessRate = rate(correctCount, answeredCount),
            assignments = assignmentItems,
            submittedCount = submittedCount,
            assignmentTotal = assignmentItems.size.toLong(),
            gradedCount = gradedScores.size.toLong(),
            averageScore = if (gradedScores.isEmpty()) null else gradedScores.average(),
        )
    }

    private fun rate(numerator: Long, denominator: Long): Double =
        if (denominator <= 0) 0.0 else numerator.toDouble() / denominator

    private fun average(values: List<Double>): Double =
        if (values.isEmpty()) 0.0 else values.average()

    private fun percent(rate: Double): String =
        String.format(Locale.CHINA, "%.1f%%", round(rate * 1000) / 10)

    private fun snippet(text: String): String =
        if (text.length <= 24) text else text.take(24) + "…"
}
