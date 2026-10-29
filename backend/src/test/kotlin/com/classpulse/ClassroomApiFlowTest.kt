package com.classpulse

import com.classpulse.domain.QuestionType
import com.classpulse.domain.Role
import com.classpulse.domain.User
import com.classpulse.repository.AttendanceSessionRepository
import com.classpulse.repository.AssignmentRepository
import com.classpulse.repository.QuestionRepository
import com.classpulse.repository.UserRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:classroom-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "app.jwt.secret=classroom-pulse-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz",
        "app.upload.max-bytes=10485760",
    ],
)
@AutoConfigureMockMvc
class ClassroomApiFlowTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var questionRepository: QuestionRepository
    @Autowired lateinit var assignmentRepository: AssignmentRepository
    @Autowired lateinit var attendanceSessionRepository: AttendanceSessionRepository
    @Autowired lateinit var passwordEncoder: PasswordEncoder

    private lateinit var teacherToken: String
    private lateinit var otherTeacherToken: String
    private lateinit var studentToken: String
    private lateinit var secondStudentToken: String
    private lateinit var outsiderToken: String
    private var courseId: Long = 0
    private var inviteCode: String = ""

    @BeforeEach
    fun setUp() {
        if (userRepository.findByUsername("T9001") == null) {
            createTeacher("T9001", "测试教师")
            createTeacher("T9002", "其他教师")
        }
        teacherToken = login("T9001", "Teacher@123")
        otherTeacherToken = login("T9002", "Teacher@123")
        studentToken = registerOrLogin("S9001", "学生甲")
        secondStudentToken = registerOrLogin("S9002", "学生乙")
        outsiderToken = registerOrLogin("S9003", "旁听生")
        if (courseId == 0L) {
            val created = postJson(
                "/api/v1/courses",
                mapOf("name" to "测试课程", "description" to "用于接口验收的课程"),
                teacherToken,
            ).andExpect(status().isCreated).andReturn().response.contentAsString
            val data = objectMapper.readTree(created).path("data")
            courseId = data.path("id").asLong()
            inviteCode = data.path("inviteCode").asText()
            postJson("/api/v1/courses/join", mapOf("inviteCode" to inviteCode), studentToken)
                .andExpect(status().isOk)
            postJson("/api/v1/courses/join", mapOf("inviteCode" to inviteCode), secondStudentToken)
                .andExpect(status().isOk)
        }
    }

    @Test
    fun completeRoleAwareTeachingFlow() {
        // Course and membership
        postJson("/api/v1/courses/join", mapOf("inviteCode" to inviteCode), studentToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"))
        getJson("/api/v1/courses/$courseId", outsiderToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_MEMBER"))
        getJson("/api/v1/courses/$courseId", otherTeacherToken)
            .andExpect(status().isForbidden)
        getJson("/api/v1/courses/$courseId/members", studentToken)
            .andExpect(status().isForbidden)
        getJson("/api/v1/courses/$courseId/members", teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
        postJson("/api/v1/courses", mapOf("name" to "越权课程", "description" to "不应成功"), studentToken)
            .andExpect(status().isForbidden)

        // Attendance: wrong code, success, duplicate, expired
        val attendanceBody = postJson(
            "/api/v1/courses/$courseId/attendance",
            mapOf("title" to "验收签到", "durationMinutes" to 10),
            teacherToken,
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.code").isString)
            .andReturn().response.contentAsString
        val attendance = objectMapper.readTree(attendanceBody).path("data")
        val attendanceId = attendance.path("id").asLong()
        val attendanceCode = attendance.path("code").asText()
        postJson("/api/v1/attendance/$attendanceId/check-in", mapOf("code" to "000000"), studentToken)
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_CODE"))
        postJson("/api/v1/attendance/$attendanceId/check-in", mapOf("code" to attendanceCode), studentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.message").value("签到成功"))
        postJson("/api/v1/attendance/$attendanceId/check-in", mapOf("code" to attendanceCode), studentToken)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"))
        getJson("/api/v1/attendance/$attendanceId/results", teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.present.length()").value(1))
            .andExpect(jsonPath("$.data.absent.length()").value(1))
        getJson("/api/v1/attendance/$attendanceId/results", studentToken)
            .andExpect(status().isForbidden)

        val expiredAttendanceBody = postJson(
            "/api/v1/courses/$courseId/attendance",
            mapOf("title" to "过期签到", "durationMinutes" to 1),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val expiredId = objectMapper.readTree(expiredAttendanceBody).path("data").path("id").asLong()
        val expiredSession = attendanceSessionRepository.findById(expiredId).orElseThrow()
        expiredSession.endsAt = Instant.now().minusSeconds(5)
        attendanceSessionRepository.saveAndFlush(expiredSession)
        val expiredCode = expiredSession.code
        postJson("/api/v1/attendance/$expiredId/check-in", mapOf("code" to expiredCode), secondStudentToken)
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("ATTENDANCE_CLOSED"))

        // Questions: choice, duplicate, fill with trim/case-insensitive, deadline
        val choiceBody = postJson(
            "/api/v1/courses/$courseId/questions",
            mapOf(
                "type" to QuestionType.SINGLE_CHOICE.name,
                "prompt" to "栈的访问原则是什么？",
                "options" to listOf("先进先出", "后进先出", "随机访问"),
                "correctAnswer" to "B",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.options.length()").value(3))
            .andReturn().response.contentAsString
        val questionId = objectMapper.readTree(choiceBody).path("data").path("id").asLong()
        postJson("/api/v1/questions/$questionId/answers", mapOf("answer" to "B"), studentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isCorrect").value(true))
        postJson("/api/v1/questions/$questionId/answers", mapOf("answer" to "B"), studentToken)
            .andExpect(status().isConflict)
        postJson("/api/v1/questions/$questionId/answers", mapOf("answer" to "B"), outsiderToken)
            .andExpect(status().isForbidden)
        getJson("/api/v1/questions/$questionId/stats", teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.answerDetails.length()").value(1))
            .andExpect(jsonPath("$.data.question.submissionCount").value(1))
            .andExpect(jsonPath("$.data.question.correctCount").value(1))
        getJson("/api/v1/questions/$questionId/stats", studentToken)
            .andExpect(status().isForbidden)

        val fillBody = postJson(
            "/api/v1/courses/$courseId/questions",
            mapOf(
                "type" to QuestionType.FILL_BLANK.name,
                "prompt" to "快速排序平均时间复杂度？",
                "correctAnswer" to "O(n log n)",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val fillId = objectMapper.readTree(fillBody).path("data").path("id").asLong()
        postJson("/api/v1/questions/$fillId/answers", mapOf("answer" to "  o(N LOG n)  "), secondStudentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isCorrect").value(true))
        val expiredQuestion = questionRepository.findById(fillId).orElseThrow()
        expiredQuestion.deadlineAt = Instant.now().minusSeconds(1)
        questionRepository.saveAndFlush(expiredQuestion)
        postJson("/api/v1/questions/$fillId/answers", mapOf("answer" to "O(n)"), outsiderToken)
            .andExpect(status().isForbidden)
        postJson("/api/v1/questions/$fillId/answers", mapOf("answer" to "O(n)"), studentToken)
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("QUESTION_CLOSED"))
        postJson(
            "/api/v1/courses/$courseId/questions",
            mapOf(
                "type" to QuestionType.FILL_BLANK.name,
                "prompt" to "无人作答的新题",
                "correctAnswer" to "42",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated)

        // Assignment upload, replacement and image authorization
        val assignmentBody = postJson(
            "/api/v1/courses/$courseId/assignments",
            mapOf(
                "title" to "拍照作业",
                "description" to "上传一张测试图片",
                "deadlineAt" to Instant.now().plus(3, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val assignmentId = objectMapper.readTree(assignmentBody).path("data").path("id").asLong()
        val jpeg = Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAH/AP/EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Bf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEABj8Cf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8hf//aAAwDAQACAAMAAAAQH//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Qf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Qf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8Qf//Z",
        )
        val upload = mockMvc.perform(
            multipart("/api/v1/assignments/$assignmentId/submissions")
                .file(MockMultipartFile("file", "first.jpg", "image/jpeg", jpeg))
                .param("note", "第一次提交")
                .header("Authorization", "Bearer $studentToken"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.imageUrl").isString)
            .andReturn().response.contentAsString
        val submissionId = objectMapper.readTree(upload).path("data").path("id").asLong()
        mockMvc.perform(
            multipart("/api/v1/assignments/$assignmentId/submissions")
                .file(MockMultipartFile("file", "second.jpg", "image/jpeg", jpeg))
                .param("note", "替换后的提交")
                .header("Authorization", "Bearer $studentToken"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(submissionId))
            .andExpect(jsonPath("$.data.note").value("替换后的提交"))
            .andExpect(jsonPath("$.data.submittedAt").isString)
        mockMvc.perform(
            multipart("/api/v1/assignments/$assignmentId/submissions")
                .file(MockMultipartFile("file", "bad.txt", "text/plain", "not an image".toByteArray()))
                .header("Authorization", "Bearer $studentToken"),
        ).andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"))
        mockMvc.perform(
            multipart("/api/v1/assignments/$assignmentId/submissions")
                .file(MockMultipartFile("file", "blocked.jpg", "image/jpeg", jpeg))
                .header("Authorization", "Bearer $outsiderToken"),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            get("/api/v1/submissions/$submissionId/image")
                .header("Authorization", "Bearer $studentToken"),
        ).andExpect(status().isOk)
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(content().contentType("image/jpeg"))
        mockMvc.perform(
            get("/api/v1/submissions/$submissionId/image")
                .header("Authorization", "Bearer $secondStudentToken"),
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("IMAGE_FORBIDDEN"))
        mockMvc.perform(
            get("/api/v1/submissions/$submissionId/image")
                .header("Authorization", "Bearer $teacherToken"),
        ).andExpect(status().isOk)

        getJson("/api/v1/assignments/$assignmentId/submissions", teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.submittedCount").value(1))
            .andExpect(jsonPath("$.data.missingCount").value(1))
        getJson("/api/v1/assignments/$assignmentId/submissions", studentToken)
            .andExpect(status().isForbidden)

        // PNG is also accepted, and a second student's upload is visible to the teacher.
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            ByteArray(40) { (it % 31).toByte() }
        mockMvc.perform(
            multipart("/api/v1/assignments/$assignmentId/submissions")
                .file(MockMultipartFile("file", "second-student.png", "image/png", png))
                .header("Authorization", "Bearer $secondStudentToken"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.contentType").value("image/png"))
        getJson("/api/v1/assignments/$assignmentId/submissions", teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.submittedCount").value(2))
            .andExpect(jsonPath("$.data.missingCount").value(0))

        // Oversized image is rejected with a stable error code.
        val oversized = ByteArray(10 * 1024 * 1024 + 1).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xD8.toByte()
            it[2] = 0xFF.toByte()
        }
        mockMvc.perform(
            multipart("/api/v1/assignments/$assignmentId/submissions")
                .file(MockMultipartFile("file", "too-large.jpg", "image/jpeg", oversized))
                .header("Authorization", "Bearer $secondStudentToken"),
        ).andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))

        // Statistics use explicit denominators
        getJson("/api/v1/courses/$courseId/stats", teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memberCount").value(2))
            .andExpect(jsonPath("$.data.attendance.items[0].expectedCount").value(2))
            .andExpect(jsonPath("$.data.questions.items.length()").value(3))
            .andExpect(jsonPath("$.data.questions.averageCorrectRate").value(1.0))
            .andExpect(jsonPath("$.data.questions.conclusion").value(org.hamcrest.Matchers.containsString("未作答题目不参与")))
            .andExpect(jsonPath("$.data.assignments.items[0].expectedCount").value(2))
            .andExpect(jsonPath("$.data.summary.highlights.length()").value(3))
        getJson("/api/v1/courses/$courseId/stats", studentToken)
            .andExpect(status().isForbidden)

        // Unauthenticated access is rejected
        mockMvc.perform(get("/api/v1/courses"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        mockMvc.perform(
            get("/api/v1/courses").header("Authorization", "Bearer invalid-token"),
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))

        // Deadline enforcement for assignments
        val closedBody = postJson(
            "/api/v1/courses/$courseId/assignments",
            mapOf(
                "title" to "即将关闭的作业",
                "description" to "测试截止后拒绝",
                "deadlineAt" to Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val closedId = objectMapper.readTree(closedBody).path("data").path("id").asLong()
        val closedAssignment = assignmentRepository.findById(closedId).orElseThrow()
        closedAssignment.deadlineAt = Instant.now().minusSeconds(1)
        assignmentRepository.saveAndFlush(closedAssignment)
        mockMvc.perform(
            multipart("/api/v1/assignments/$closedId/submissions")
                .file(MockMultipartFile("file", "late.jpg", "image/jpeg", jpeg))
                .header("Authorization", "Bearer $secondStudentToken"),
        ).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("ASSIGNMENT_CLOSED"))

        assertTrue(Files.exists(UPLOAD_DIR))
    }

    @Test
    fun multipleChoiceRequiresExactSelection() {
        val invalid = postJson(
            "/api/v1/courses/$courseId/questions",
            mapOf(
                "type" to QuestionType.MULTIPLE_CHOICE.name,
                "prompt" to "至少两个正确项",
                "options" to listOf("A", "B", "C", "D"),
                "correctAnswer" to "A",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        )
        invalid.andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_ANSWER"))

        val created = postJson(
            "/api/v1/courses/$courseId/questions",
            mapOf(
                "type" to QuestionType.MULTIPLE_CHOICE.name,
                "prompt" to "选择全部正确项",
                "options" to listOf("甲", "乙", "丙", "丁"),
                "correctAnswer" to "C,A",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.type").value("MULTIPLE_CHOICE"))
            .andExpect(jsonPath("$.data.correctAnswer").value("A,C"))
            .andReturn().response.contentAsString
        val questionId = objectMapper.readTree(created).path("data").path("id").asLong()

        postJson(
            "/api/v1/questions/$questionId/answers",
            mapOf("answer" to "C,A"),
            studentToken,
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.answer").value("A,C"))
            .andExpect(jsonPath("$.data.isCorrect").value(true))

        postJson(
            "/api/v1/questions/$questionId/answers",
            mapOf("answer" to "A"),
            secondStudentToken,
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isCorrect").value(false))

        getJson("/api/v1/questions/$questionId/stats", teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.question.submissionCount").value(2))
            .andExpect(jsonPath("$.data.question.correctCount").value(1))
            .andExpect(jsonPath("$.data.optionDistribution[0].count").value(2))
            .andExpect(jsonPath("$.data.optionDistribution[2].count").value(1))
    }

    @Test
    fun allUnansweredQuestionsShowNoAnswerData() {
        val created = postJson(
            "/api/v1/courses",
            mapOf("name" to "无人作答统计课", "description" to "验证未作答题目不参与平均"),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val localCourseId = objectMapper.readTree(created).path("data").path("id").asLong()
        postJson(
            "/api/v1/courses/$localCourseId/questions",
            mapOf(
                "type" to QuestionType.FILL_BLANK.name,
                "prompt" to "无人作答的题目",
                "correctAnswer" to "42",
                "deadlineAt" to Instant.now().plus(1, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated)

        getJson("/api/v1/courses/$localCourseId/stats", teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.questions.items.length()").value(1))
            .andExpect(jsonPath("$.data.questions.averageCorrectRate").value(0.0))
            .andExpect(jsonPath("$.data.questions.conclusion").value("暂无作答数据。"))
            .andExpect(jsonPath("$.data.summary.highlights[0]").value(containsString("问答暂无作答数据")))
    }

    @Test
    fun validationAndPasswordSafety() {
        postJson(
            "/api/v1/auth/register",
            mapOf("studentNo" to "S9001", "name" to "重复学生", "password" to "Password@123"),
            null,
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"))
        postJson(
            "/api/v1/auth/register",
            mapOf("studentNo" to "x", "name" to "A", "password" to "short"),
            null,
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        postJson(
            "/api/v1/auth/login",
            mapOf("username" to "S9001", "password" to "wrong-password"),
            null,
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value(containsString("账号或密码错误")))
        postJson(
            "/api/v1/courses/join",
            mapOf("inviteCode" to "ZZZZ999"),
            studentToken,
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"))
        val stored = userRepository.findByUsername("S9001")!!
        assertNotEquals("Student@123", stored.passwordHash)
        assertTrue(stored.passwordHash.startsWith("$2"))
    }

    @Test
    fun courseLifecycleAndAccountManagement() {
        val lifecycleToken = registerOrLogin("LC9001", "生命周期学生")
        val created = postJson(
            "/api/v1/courses",
            mapOf("name" to "生命周期课程", "description" to "测试归档和删除"),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val course = objectMapper.readTree(created).path("data")
        val lifecycleCourseId = course.path("id").asLong()
        val invite = course.path("inviteCode").asText()
        postJson("/api/v1/courses/join", mapOf("inviteCode" to invite), lifecycleToken)
            .andExpect(status().isOk)
        putJson(
            "/api/v1/courses/$lifecycleCourseId",
            mapOf("name" to "生命周期课程（改名）", "description" to "已更新"),
            teacherToken,
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name").value("生命周期课程（改名）"))
        postJson("/api/v1/courses/$lifecycleCourseId/archive", emptyMap<String, String>(), teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.archived").value(true))
        postJson(
            "/api/v1/courses/$lifecycleCourseId/questions",
            mapOf(
                "type" to QuestionType.FILL_BLANK.name,
                "prompt" to "归档课程不能出题",
                "correctAnswer" to "x",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("COURSE_ARCHIVED"))
        postJson("/api/v1/courses/$lifecycleCourseId/unarchive", emptyMap<String, String>(), teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.archived").value(false))
        val resetInvite = postJson(
            "/api/v1/courses/$lifecycleCourseId/invite-code/reset",
            emptyMap<String, String>(),
            teacherToken,
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val newInvite = objectMapper.readTree(resetInvite).path("data").path("inviteCode").asText()
        assertNotEquals(invite, newInvite)

        putJson(
            "/api/v1/auth/password",
            mapOf("currentPassword" to "Student@123", "newPassword" to "NewPass@123"),
            lifecycleToken,
        ).andExpect(status().isOk)
        getJson("/api/v1/auth/me", lifecycleToken)
            .andExpect(status().isUnauthorized)
        val afterChangeToken = login("LC9001", "NewPass@123")

        val lifecycleStudent = userRepository.findByUsername("LC9001")!!
        val resetPassword = postJson(
            "/api/v1/courses/$lifecycleCourseId/members/${lifecycleStudent.id}/reset-password",
            emptyMap<String, String>(),
            teacherToken,
        ).andExpect(status().isOk).andReturn().response.contentAsString
        getJson("/api/v1/auth/me", afterChangeToken)
            .andExpect(status().isUnauthorized)
        val tempPassword = objectMapper.readTree(resetPassword).path("data").path("temporaryPassword").asText()
        login("LC9001", tempPassword)

        deleteJson("/api/v1/courses/$lifecycleCourseId/members/${lifecycleStudent.id}", teacherToken)
            .andExpect(status().isOk)
        deleteJson("/api/v1/courses/$lifecycleCourseId", teacherToken)
            .andExpect(status().isOk)
        getJson("/api/v1/courses/$lifecycleCourseId", teacherToken)
            .andExpect(status().isNotFound)
    }

    @Test
    fun questionEditingGradingAndStudentSummary() {
        val createdQuestion = postJson(
            "/api/v1/courses/$courseId/questions",
            mapOf(
                "type" to QuestionType.FILL_BLANK.name,
                "prompt" to "可编辑题目",
                "correctAnswer" to "42",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val questionId = objectMapper.readTree(createdQuestion).path("data").path("id").asLong()
        putJson(
            "/api/v1/questions/$questionId",
            mapOf(
                "prompt" to "已编辑题干",
                "options" to null,
                "correctAnswer" to "43",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.prompt").value("已编辑题干"))
        postJson("/api/v1/questions/$questionId/answers", mapOf("answer" to "43"), studentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isCorrect").value(true))
        putJson(
            "/api/v1/questions/$questionId",
            mapOf(
                "prompt" to "已有作答后不能改答案",
                "options" to null,
                "correctAnswer" to "44",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("QUESTION_HAS_ANSWERS"))
        postJson("/api/v1/questions/$questionId/close", emptyMap<String, String>(), teacherToken)
            .andExpect(status().isOk)
        postJson("/api/v1/questions/$questionId/answers", mapOf("answer" to "43"), studentToken)
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("QUESTION_CLOSED"))
        deleteJson("/api/v1/questions/$questionId", teacherToken)
            .andExpect(status().isOk)
        getJson("/api/v1/courses/$courseId/questions", teacherToken)
            .andExpect(jsonPath("$.data.length()").value(0))

        val assignmentBody = postJson(
            "/api/v1/courses/$courseId/assignments",
            mapOf(
                "title" to "评分作业",
                "description" to "测试评分",
                "deadlineAt" to Instant.now().plus(3, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val assignmentId = objectMapper.readTree(assignmentBody).path("data").path("id").asLong()
        val jpeg = Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAH/AP/EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Bf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEABj8Cf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8hf//aAAwDAQACAAMAAAAQH//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Qf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Qf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8Qf//Z",
        )
        val upload = mockMvc.perform(
            multipart("/api/v1/assignments/$assignmentId/submissions")
                .file(MockMultipartFile("file", "grade.jpg", "image/jpeg", jpeg))
                .param("note", "待评分")
                .header("Authorization", "Bearer $studentToken"),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val submissionId = objectMapper.readTree(upload).path("data").path("id").asLong()
        val submissionVersion = objectMapper.readTree(upload).path("data").path("version").asLong()
        putJson(
            "/api/v1/submissions/$submissionId/grade",
            mapOf("score" to 88, "feedback" to "结构清晰", "expectedVersion" to (submissionVersion - 1)),
            teacherToken,
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SUBMISSION_CHANGED"))
        putJson(
            "/api/v1/submissions/$submissionId/grade",
            mapOf("score" to 88, "feedback" to "结构清晰", "expectedVersion" to submissionVersion),
            teacherToken,
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.score").value(88))
        getJson("/api/v1/courses/$courseId/assignments", studentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].mySubmission.score").value(88))
        getJson("/api/v1/courses/$courseId/my-stats", studentToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.gradedCount").value(1))
            .andExpect(jsonPath("$.data.averageScore").value(88.0))
    }

    @Test
    fun memberRemovalAlignsStatsAndImageAccess() {
        val attendanceBody = postJson(
            "/api/v1/courses/$courseId/attendance",
            mapOf("title" to "成员移除统计", "durationMinutes" to 10),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val attendance = objectMapper.readTree(attendanceBody).path("data")
        val attendanceId = attendance.path("id").asLong()
        val code = attendance.path("code").asText()
        postJson("/api/v1/attendance/$attendanceId/check-in", mapOf("code" to code), studentToken)
            .andExpect(status().isOk)
        postJson("/api/v1/attendance/$attendanceId/check-in", mapOf("code" to code), secondStudentToken)
            .andExpect(status().isOk)

        val assignmentBody = postJson(
            "/api/v1/courses/$courseId/assignments",
            mapOf(
                "title" to "成员移除作业",
                "description" to "统计口径",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val assignmentId = objectMapper.readTree(assignmentBody).path("data").path("id").asLong()
        val jpeg = Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAH/AP/EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Bf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEABj8Cf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8hf//aAAwDAQACAAMAAAAQH//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Qf//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Qf//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8Qf//Z",
        )
        mockMvc.perform(
            multipart("/api/v1/assignments/$assignmentId/submissions")
                .file(MockMultipartFile("file", "member-one.jpg", "image/jpeg", jpeg))
                .header("Authorization", "Bearer $studentToken"),
        ).andExpect(status().isOk)
        val removedUpload = mockMvc.perform(
            multipart("/api/v1/assignments/$assignmentId/submissions")
                .file(MockMultipartFile("file", "member-two.jpg", "image/jpeg", jpeg))
                .header("Authorization", "Bearer $secondStudentToken"),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val removedSubmissionId = objectMapper.readTree(removedUpload).path("data").path("id").asLong()

        val questionBody = postJson(
            "/api/v1/courses/$courseId/questions",
            mapOf(
                "type" to QuestionType.FILL_BLANK.name,
                "prompt" to "成员移除题目",
                "correctAnswer" to "x",
                "deadlineAt" to Instant.now().plus(2, ChronoUnit.DAYS).toString(),
            ),
            teacherToken,
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val questionId = objectMapper.readTree(questionBody).path("data").path("id").asLong()
        postJson("/api/v1/questions/$questionId/answers", mapOf("answer" to "x"), studentToken)
            .andExpect(status().isOk)
        postJson("/api/v1/questions/$questionId/answers", mapOf("answer" to "x"), secondStudentToken)
            .andExpect(status().isOk)

        val removed = userRepository.findByUsername("S9002")!!
        deleteJson("/api/v1/courses/$courseId/members/${removed.id}", teacherToken)
            .andExpect(status().isOk)

        getJson("/api/v1/courses/$courseId/stats", teacherToken)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.attendance.items[0].presentCount").value(1))
            .andExpect(jsonPath("$.data.attendance.items[0].expectedCount").value(1))
            .andExpect(jsonPath("$.data.questions.items[0].submissionCount").value(1))
            .andExpect(jsonPath("$.data.assignments.items[0].submissionCount").value(1))
            .andExpect(jsonPath("$.data.assignments.items[0].expectedCount").value(1))

        getJson("/api/v1/submissions/$removedSubmissionId/image", secondStudentToken)
            .andExpect(status().isForbidden)
    }

    private fun createTeacher(username: String, name: String) {
        userRepository.save(
            User(
                username = username,
                name = name,
                role = Role.TEACHER,
                passwordHash = passwordEncoder.encode("Teacher@123"),
                createdAt = Instant.now(),
            ),
        )
    }

    private fun registerOrLogin(username: String, name: String): String {
        if (userRepository.findByUsername(username) == null) {
            val body = postJson(
                "/api/v1/auth/register",
                mapOf("studentNo" to username, "name" to name, "password" to "Student@123"),
                null,
            ).andExpect(status().isCreated).andReturn().response.contentAsString
            return objectMapper.readTree(body).path("data").path("token").asText()
        }
        return login(username, "Student@123")
    }

    private fun login(username: String, password: String): String {
        val body = postJson(
            "/api/v1/auth/login",
            mapOf("username" to username, "password" to password),
            null,
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(body).path("data").path("token").asText()
    }

    private fun postJson(path: String, body: Any, token: String?) =
        mockMvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .apply { if (token != null) header("Authorization", "Bearer $token") },
        )

    private fun getJson(path: String, token: String) =
        mockMvc.perform(get(path).header("Authorization", "Bearer $token"))

    private fun putJson(path: String, body: Any, token: String?) =
        mockMvc.perform(
            put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .apply { if (token != null) header("Authorization", "Bearer $token") },
        )

    private fun deleteJson(path: String, token: String?) =
        mockMvc.perform(
            delete(path)
                .apply { if (token != null) header("Authorization", "Bearer $token") },
        )

    companion object {
        private val UPLOAD_DIR = Files.createTempDirectory("classroom-pulse-test-uploads")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.upload.dir") { UPLOAD_DIR.toString() }
        }
    }
}


