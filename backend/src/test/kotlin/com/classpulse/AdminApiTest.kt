package com.classpulse

import com.classpulse.domain.Answer
import com.classpulse.domain.Assignment
import com.classpulse.domain.Course
import com.classpulse.domain.Question
import com.classpulse.domain.QuestionType
import com.classpulse.domain.Submission
import com.classpulse.domain.User
import com.classpulse.repository.AnswerRepository
import com.classpulse.repository.AssignmentRepository
import com.classpulse.repository.CourseRepository
import com.classpulse.repository.QuestionRepository
import com.classpulse.repository.SubmissionRepository
import com.classpulse.repository.UserRepository
import com.classpulse.service.StorageService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Files

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:admin-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.jwt.secret=classroom-pulse-admin-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz",
        "app.admin.initial-username=admin-test",
        "app.admin.initial-password=AdminPass#2026",
        "app.admin.initial-name=测试管理员",
    ],
)
@AutoConfigureMockMvc
class AdminApiTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var courseRepository: CourseRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var questionRepository: QuestionRepository
    @Autowired lateinit var answerRepository: AnswerRepository
    @Autowired lateinit var assignmentRepository: AssignmentRepository
    @Autowired lateinit var submissionRepository: SubmissionRepository
    @Autowired lateinit var storageService: StorageService

    private data class AdminSession(val cookie: Cookie, val csrf: String)

    private fun loginAdmin(): AdminSession {
        val result = mockMvc.perform(
            post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin-test","password":"AdminPass#2026"}"""),
        ).andExpect(status().isOk).andReturn()
        val rawCookie = requireNotNull(result.response.getHeader("Set-Cookie")).substringBefore(';')
        val name = rawCookie.substringBefore('=')
        val value = rawCookie.substringAfter('=')
        val body = objectMapper.readTree(result.response.contentAsString)
        return AdminSession(Cookie(name, value), body.path("data").path("csrfToken").asText())
    }

    private fun adminGet(path: String, session: AdminSession) =
        mockMvc.perform(get(path).cookie(session.cookie))

    private fun adminPost(path: String, body: Any, session: AdminSession) =
        mockMvc.perform(
            post(path)
                .cookie(session.cookie)
                .header("X-CSRF-Token", session.csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun adminPut(path: String, body: Any, session: AdminSession) =
        mockMvc.perform(
            put(path)
                .cookie(session.cookie)
                .header("X-CSRF-Token", session.csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )

    private fun adminDelete(path: String, session: AdminSession) =
        mockMvc.perform(delete(path).cookie(session.cookie).header("X-CSRF-Token", session.csrf))

    @Test
    fun adminPermissionCsrfAndCrudFlow() {
        mockMvc.perform(get("/api/admin/summary"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/admin/index.html"))
            .andExpect(status().isOk)

        val session = loginAdmin()
        adminGet("/api/admin/summary", session)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.teachers").value(0))
        mockMvc.perform(
            post("/api/admin/accounts")
                .cookie(session.cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"T-ADMIN","name":"管理测试教师","password":"TeacherPass#1","role":"TEACHER"}"""),
        ).andExpect(status().isForbidden)

        val teacherRaw = adminPost(
            "/api/admin/accounts",
            mapOf("username" to "T-ADMIN", "name" to "管理测试教师", "password" to "TeacherPass#1", "role" to "TEACHER"),
            session,
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val teacher: JsonNode = objectMapper.readTree(teacherRaw).path("data")
        val teacherId = teacher.path("id").asLong()
        assertEquals(0, teacher.path("courseCount").asInt())

        val studentRaw = adminPost(
            "/api/admin/accounts",
            mapOf("username" to "S-ADMIN", "name" to "管理测试学生", "password" to "StudentPass#1", "role" to "STUDENT"),
            session,
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val student = objectMapper.readTree(studentRaw).path("data")
        val studentId = student.path("id").asLong()

        adminPut(
            "/api/admin/accounts/$studentId",
            mapOf("name" to "管理测试学生改", "expectedVersion" to student.path("version").asLong()),
            session,
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name").value("管理测试学生改"))

        adminPost(
            "/api/admin/accounts/$studentId/reset-password",
            emptyMap<String, String>(),
            session,
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty)

        val courseRaw = adminPost(
            "/api/admin/courses",
            mapOf("name" to "管理测试课程", "description" to "后台创建", "teacherId" to teacherId),
            session,
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val course = objectMapper.readTree(courseRaw).path("data")
        val courseId = course.path("id").asLong()
        var version = course.path("version").asLong()
        val invite = course.path("inviteCode").asText()

        adminPost(
            "/api/admin/courses/$courseId/members",
            mapOf("studentNo" to "S-ADMIN"),
            session,
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))

        adminGet("/api/admin/courses/$courseId/detail", session)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.members.length()").value(1))
            .andExpect(jsonPath("$.data.course.name").value("管理测试课程"))

        val archive = adminPost(
            "/api/admin/courses/$courseId/archive?expectedVersion=$version",
            emptyMap<String, String>(),
            session,
        ).andExpect(status().isOk).andReturn().response.contentAsString
        version = objectMapper.readTree(archive).path("data").path("version").asLong()
        val unarchive = adminPost(
            "/api/admin/courses/$courseId/unarchive?expectedVersion=$version",
            emptyMap<String, String>(),
            session,
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.archived").value(false))
            .andReturn().response.contentAsString
        version = objectMapper.readTree(unarchive).path("data").path("version").asLong()

        val reset = adminPost(
            "/api/admin/courses/$courseId/invite-code/reset?expectedVersion=$version",
            emptyMap<String, String>(),
            session,
        ).andExpect(status().isOk).andReturn().response.contentAsString
        version = objectMapper.readTree(reset).path("data").path("version").asLong()
        assertTrue(objectMapper.readTree(reset).path("data").path("inviteCode").asText() != invite)

        val courseEntity = courseRepository.findById(courseId).orElseThrow()
        val studentEntity = userRepository.findByUsername("S-ADMIN")!!
        val question = questionRepository.save(
            Question(
                course = courseEntity,
                type = QuestionType.FILL_BLANK,
                prompt = "管理员级联测试题",
                standardAnswer = "x",
                deadlineAt = java.time.Instant.now().plusSeconds(3600),
                createdAt = java.time.Instant.now(),
            ),
        )
        answerRepository.save(
            Answer(
                question = question,
                student = studentEntity,
                answerText = "x",
                isCorrect = true,
                submittedAt = java.time.Instant.now(),
            ),
        )
        val assignment = assignmentRepository.save(
            Assignment(
                course = courseEntity,
                title = "管理员文件清理作业",
                description = "清理测试",
                deadlineAt = java.time.Instant.now().plusSeconds(3600),
                createdAt = java.time.Instant.now(),
            ),
        )
        val imagePath = storageService.store(courseId, byteArrayOf(1, 2, 3), "jpg")
        val imageFile = storageService.resolve(imagePath)
        submissionRepository.save(
            Submission(
                assignment = assignment,
                student = studentEntity,
                imagePath = imagePath,
                originalFilename = "admin.jpg",
                contentType = "image/jpeg",
                fileSize = 3,
                submittedAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
            ),
        )

        val deleted = adminDelete("/api/admin/courses/$courseId?expectedVersion=$version", session)
            .andExpect(status().isOk).andReturn().response.contentAsString
        version = objectMapper.readTree(deleted).path("data").path("version").asLong()
        adminGet("/api/admin/courses/$courseId/impact", session)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.members").value(1))
            .andExpect(jsonPath("$.data.questions").value(1))
            .andExpect(jsonPath("$.data.answers").value(1))
            .andExpect(jsonPath("$.data.submissions").value(1))
        mockMvc.perform(
            delete("/api/admin/courses/$courseId/permanent")
                .cookie(session.cookie)
                .header("X-CSRF-Token", session.csrf)
                .param("expectedVersion", version.toString())
                .param("confirmName", "管理测试课程"),
        ).andExpect(status().isOk)
        assertEquals(0, questionRepository.count())
        assertEquals(0, answerRepository.count())
        assertTrue(!Files.exists(imageFile))
        adminGet("/api/admin/courses", session)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))

        val accountsRaw = adminGet("/api/admin/accounts?query=S-ADMIN", session)
            .andExpect(status().isOk).andReturn().response.contentAsString
        val currentStudent = objectMapper.readTree(accountsRaw).path("data")[0]
        adminDelete(
            "/api/admin/accounts/${currentStudent.path("id").asLong()}?expectedVersion=${currentStudent.path("version").asLong()}&confirm=true",
            session,
        ).andExpect(status().isOk)
        adminGet("/api/admin/accounts?query=S-ADMIN", session)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    companion object {
        private val UPLOAD_DIR = Files.createTempDirectory("classroom-admin-test-uploads")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("app.upload.dir") { UPLOAD_DIR.toString() }
        }
    }
}
