package com.classpulse

import com.classpulse.domain.Assignment
import com.classpulse.domain.Course
import com.classpulse.domain.CourseMember
import com.classpulse.domain.Role
import com.classpulse.domain.User
import com.classpulse.repository.AssignmentRepository
import com.classpulse.repository.CourseMemberRepository
import com.classpulse.repository.CourseRepository
import com.classpulse.repository.SubmissionRepository
import com.classpulse.repository.UserRepository
import com.classpulse.security.UserPrincipal
import com.classpulse.service.AssignmentService
import com.classpulse.service.StorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:classroom-files;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "app.jwt.secret=classroom-pulse-file-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz",
        "app.upload.max-bytes=10485760",
    ],
)
class AssignmentFileSafetyTest {
    @Autowired lateinit var assignmentService: AssignmentService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var courseRepository: CourseRepository
    @Autowired lateinit var memberRepository: CourseMemberRepository
    @Autowired lateinit var assignmentRepository: AssignmentRepository
    @Autowired lateinit var submissionRepository: SubmissionRepository
    @Autowired lateinit var storageService: StorageService
    @Autowired lateinit var passwordEncoder: PasswordEncoder
    @Autowired lateinit var transactionManager: PlatformTransactionManager

    private lateinit var student: User
    private lateinit var assignment: Assignment

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().take(8)
        val teacher = userRepository.save(
            User(
                username = "FT$suffix",
                name = "文件教师",
                role = Role.TEACHER,
                passwordHash = passwordEncoder.encode("Teacher@123"),
                createdAt = Instant.now(),
            ),
        )
        student = userRepository.save(
            User(
                username = "FS$suffix",
                name = "文件学生",
                role = Role.STUDENT,
                passwordHash = passwordEncoder.encode("Student@123"),
                createdAt = Instant.now(),
            ),
        )
        val course = courseRepository.save(
            Course(
                name = "文件一致性测试",
                description = "测试事务与并发文件清理",
                inviteCode = "F$suffix",
                teacher = teacher,
                createdAt = Instant.now(),
            ),
        )
        memberRepository.save(CourseMember(course = course, student = student, joinedAt = Instant.now()))
        assignment = assignmentRepository.save(
            Assignment(
                course = course,
                title = "文件替换测试",
                description = "用于验证文件与数据库事务一致性",
                deadlineAt = Instant.now().plus(1, ChronoUnit.DAYS),
                createdAt = Instant.now(),
            ),
        )
        authenticateAs(student)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun rollbackRemovesNewFileAndKeepsOldPath() {
        val initial = assignmentService.submit(
            requireNotNull(assignment.id),
            imageFile("initial.jpg", 1),
            "initial",
        )
        val oldPath = submissionRepository.findById(initial.id).orElseThrow().imagePath
        assertTrue(Files.exists(storageService.resolve(oldPath)))

        val tx = TransactionTemplate(transactionManager)
        try {
            tx.executeWithoutResult {
                assignmentService.submit(
                    requireNotNull(assignment.id),
                    imageFile("replacement.jpg", 2),
                    "should roll back",
                )
                error("force outer transaction rollback")
            }
        } catch (_: IllegalStateException) {
            // Expected: the transactional callback is rolled back.
        }

        val current = submissionRepository.findByAssignmentIdAndStudentId(
            requireNotNull(assignment.id),
            requireNotNull(student.id),
        )
        assertNotNull(current)
        assertEquals(oldPath, current!!.imagePath)
        assertTrue(Files.exists(storageService.resolve(oldPath)), "old image must remain after rollback")
        assertEquals(1, assignmentDirectoryFiles().size, "new file must be deleted after rollback")
    }

    @Test
    fun concurrentReplacementsLeaveSingleReferencedFile() {
        assignmentService.submit(
            requireNotNull(assignment.id),
            imageFile("initial.jpg", 1),
            "initial",
        )
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (2..3).map { index ->
                executor.submit<Result<*>> {
                    try {
                        authenticateAs(student)
                        start.await()
                        Result.success(
                            assignmentService.submit(
                                requireNotNull(assignment.id),
                                imageFile("replacement-$index.jpg", index),
                                "replacement $index",
                            ),
                        )
                    } catch (exception: Exception) {
                        Result.failure(exception)
                    } finally {
                        SecurityContextHolder.clearContext()
                    }
                }
            }
            start.countDown()
            val results = futures.map { it.get(20, TimeUnit.SECONDS) }
            assertTrue(results.all { it.isSuccess }, "both concurrent replacements should succeed")

            val submission = submissionRepository.findByAssignmentIdAndStudentId(
                requireNotNull(assignment.id),
                requireNotNull(student.id),
            )
            assertNotNull(submission)
            assertTrue(Files.exists(storageService.resolve(submission!!.imagePath)))
            assertEquals(1, submissionRepository.countByAssignmentId(requireNotNull(assignment.id)).toInt())
            assertEquals(
                1,
                assignmentDirectoryFiles().size,
                "only the current referenced image should remain; no orphan files",
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun authenticateAs(user: User) {
        val principal = UserPrincipal.from(user)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_STUDENT")),
        )
    }

    private fun imageFile(name: String, marker: Int): MockMultipartFile {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) +
            ByteArray(128) { ((it + marker) % 251).toByte() }
        return MockMultipartFile("file", name, "image/jpeg", bytes)
    }

    private fun assignmentDirectoryFiles(): List<java.io.File> {
        val directory = storageService.resolve("assignments/${requireNotNull(assignment.id)}")
        return directory.toFile().listFiles()?.toList().orEmpty()
    }

    companion object {
        private val UPLOAD_DIR = Files.createTempDirectory("classroom-pulse-file-test")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.upload.dir") { UPLOAD_DIR.toString() }
        }
    }
}


