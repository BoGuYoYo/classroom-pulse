package com.classpulse.app.data.model

data class ApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
    val code: String? = null,
)

data class ErrorEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val code: String? = null,
)

data class User(
    val id: Long = 0,
    val username: String = "",
    val name: String = "",
    val role: String = "STUDENT",
) {
    val isTeacher: Boolean get() = role == "TEACHER"
}

data class AuthData(
    val token: String = "",
    val user: User = User(),
)

data class LoginRequest(val username: String, val password: String)

data class RegisterRequest(val studentNo: String, val name: String, val password: String)

data class CourseSummary(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val inviteCode: String? = null,
    val teacherName: String = "",
    val memberCount: Long = 0,
    val createdAt: String = "",
    val joinedAt: String? = null,
)

data class Member(
    val studentId: Long = 0,
    val studentNo: String = "",
    val name: String = "",
    val joinedAt: String = "",
)

data class CreateCourseRequest(val name: String, val description: String)

data class JoinCourseRequest(val inviteCode: String)

data class Attendance(
    val id: Long = 0,
    val courseId: Long = 0,
    val title: String = "",
    val code: String? = null,
    val startsAt: String = "",
    val endsAt: String = "",
    val createdAt: String = "",
    val memberCount: Long = 0,
    val presentCount: Long = 0,
    val active: Boolean = false,
    val myCheckedIn: Boolean = false,
    val myCheckedAt: String? = null,
)

data class CreateAttendanceRequest(val title: String, val durationMinutes: Int)

data class CheckInRequest(val code: String)

data class CheckInResult(
    val attendanceId: Long = 0,
    val title: String = "",
    val checkedAt: String = "",
    val message: String = "",
)

data class AttendancePerson(
    val studentId: Long = 0,
    val studentNo: String = "",
    val name: String = "",
    val checkedAt: String? = null,
)

data class AttendanceDetail(
    val attendance: Attendance = Attendance(),
    val present: List<AttendancePerson> = emptyList(),
    val absent: List<AttendancePerson> = emptyList(),
)

data class QuestionOption(
    val key: String = "",
    val text: String = "",
)

data class Question(
    val id: Long = 0,
    val courseId: Long = 0,
    val type: String = "SINGLE_CHOICE",
    val prompt: String = "",
    val options: List<QuestionOption> = emptyList(),
    val deadlineAt: String = "",
    val createdAt: String = "",
    val submissionCount: Long? = null,
    val correctCount: Long? = null,
    val correctRate: Double? = null,
    val answered: Boolean = false,
    val myAnswer: String? = null,
    val myIsCorrect: Boolean? = null,
    val correctAnswer: String? = null,
) {
    val isChoice: Boolean get() = type == "SINGLE_CHOICE" || type == "MULTIPLE_CHOICE"
    val isMultipleChoice: Boolean get() = type == "MULTIPLE_CHOICE"
}

data class CreateQuestionRequest(
    val type: String,
    val prompt: String,
    val options: List<String>?,
    val correctAnswer: String,
    val deadlineAt: String,
)

data class AnswerRequest(val answer: String)

data class AnswerResult(
    val questionId: Long = 0,
    val answer: String = "",
    val isCorrect: Boolean = false,
    val correctAnswer: String = "",
    val submittedAt: String = "",
)

data class AnswerDetail(
    val studentId: Long = 0,
    val studentNo: String = "",
    val studentName: String = "",
    val answer: String = "",
    val isCorrect: Boolean = false,
    val submittedAt: String = "",
)

data class OptionDistribution(
    val key: String = "",
    val text: String = "",
    val count: Long = 0,
)

data class QuestionStats(
    val question: Question = Question(),
    val answerDetails: List<AnswerDetail> = emptyList(),
    val optionDistribution: List<OptionDistribution> = emptyList(),
    val conclusion: String = "",
)

data class Assignment(
    val id: Long = 0,
    val courseId: Long = 0,
    val title: String = "",
    val description: String = "",
    val deadlineAt: String = "",
    val createdAt: String = "",
    val submissionCount: Long = 0,
    val memberCount: Long = 0,
    val active: Boolean = false,
    val mySubmission: Submission? = null,
)

data class CreateAssignmentRequest(
    val title: String,
    val description: String,
    val deadlineAt: String,
)

data class Submission(
    val id: Long = 0,
    val assignmentId: Long = 0,
    val studentId: Long = 0,
    val studentName: String? = null,
    val imageUrl: String = "",
    val originalFilename: String = "",
    val contentType: String = "",
    val fileSize: Long = 0,
    val note: String? = null,
    val submittedAt: String = "",
    val updatedAt: String = "",
)

data class StudentSubmission(
    val studentId: Long = 0,
    val studentNo: String = "",
    val studentName: String = "",
    val submitted: Boolean = false,
    val submission: Submission? = null,
)

data class AssignmentSubmissions(
    val assignment: Assignment = Assignment(),
    val rows: List<StudentSubmission> = emptyList(),
    val submittedCount: Long = 0,
    val missingCount: Long = 0,
)

data class AttendanceStatItem(
    val id: Long = 0,
    val title: String = "",
    val startsAt: String = "",
    val presentCount: Long = 0,
    val expectedCount: Long = 0,
    val rate: Double = 0.0,
)

data class AttendanceStats(
    val items: List<AttendanceStatItem> = emptyList(),
    val averageRate: Double = 0.0,
    val conclusion: String = "",
)

data class QuestionStatItem(
    val id: Long = 0,
    val prompt: String = "",
    val type: String = "",
    val submissionCount: Long = 0,
    val correctCount: Long = 0,
    val correctRate: Double = 0.0,
)

data class QuestionStatsSummary(
    val items: List<QuestionStatItem> = emptyList(),
    val averageCorrectRate: Double = 0.0,
    val totalSubmissions: Long = 0,
    val conclusion: String = "",
)

data class AssignmentStatItem(
    val id: Long = 0,
    val title: String = "",
    val submissionCount: Long = 0,
    val expectedCount: Long = 0,
    val rate: Double = 0.0,
)

data class AssignmentStats(
    val items: List<AssignmentStatItem> = emptyList(),
    val averageRate: Double = 0.0,
    val conclusion: String = "",
)

data class CourseSummaryStats(
    val overallRate: Double = 0.0,
    val highlights: List<String> = emptyList(),
)

data class CourseStats(
    val courseId: Long = 0,
    val courseName: String = "",
    val memberCount: Long = 0,
    val generatedAt: String = "",
    val attendance: AttendanceStats = AttendanceStats(),
    val questions: QuestionStatsSummary = QuestionStatsSummary(),
    val assignments: AssignmentStats = AssignmentStats(),
    val summary: CourseSummaryStats = CourseSummaryStats(),
)
