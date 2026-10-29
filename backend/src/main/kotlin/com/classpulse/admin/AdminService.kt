package com.classpulse.admin

import com.classpulse.domain.*
import com.classpulse.repository.*
import com.classpulse.service.StatsService
import com.classpulse.service.StorageService
import com.classpulse.web.ApiException
import com.classpulse.web.dto.*
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale

@Service
class AdminService(
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val memberRepository: CourseMemberRepository,
    private val attendanceSessionRepository: AttendanceSessionRepository,
    private val attendanceRecordRepository: AttendanceRecordRepository,
    private val questionRepository: QuestionRepository,
    private val answerRepository: AnswerRepository,
    private val assignmentRepository: AssignmentRepository,
    private val submissionRepository: SubmissionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val storageService: StorageService,
    private val statsService: StatsService,
) {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val random = SecureRandom()
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    @Transactional(readOnly = true)
    fun summary(): AdminSummaryDto = AdminSummaryDto(
        teachers = userRepository.countByRole(Role.TEACHER),
        students = userRepository.countByRole(Role.STUDENT),
        courses = courseRepository.count(),
        archivedCourses = courseRepository.findAll().count { it.archivedAt != null && it.deletedAt == null }.toLong(),
        deletedCourses = courseRepository.findAll().count { it.deletedAt != null }.toLong(),
    )

    @Transactional(readOnly = true)
    fun accounts(query: String, role: Role?): List<AdminAccountDto> =
        userRepository.adminSearch(query.trim(), role).map { it.toAdminAccount() }

    @Transactional
    fun createAccount(request: CreateAdminAccountRequest): AdminAccountDto {
        val username = request.username.trim()
        if (userRepository.existsByUsername(username)) {
            throw ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "账号已存在")
        }
        val user = userRepository.save(
            User(
                username = username,
                name = request.name.trim(),
                role = requireNotNull(request.role),
                passwordHash = passwordEncoder.encode(request.password),
                createdAt = Instant.now(),
            ),
        )
        return user.toAdminAccount()
    }

    @Transactional
    fun updateAccount(accountId: Long, request: UpdateAdminAccountRequest): AdminAccountDto {
        val user = findUser(accountId)
        assertVersion(user.version, request.expectedVersion, "账号")
        user.name = request.name.trim()
        return userRepository.save(user).toAdminAccount()
    }

    @Transactional
    fun resetAccountPassword(accountId: Long): AdminPasswordResetResponse {
        val user = findUser(accountId)
        val temporaryPassword = "Adm-" + buildString {
            repeat(14) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
        user.passwordHash = passwordEncoder.encode(temporaryPassword)
        user.authVersion += 1
        userRepository.save(user)
        return AdminPasswordResetResponse(
            accountId = accountId,
            username = user.username,
            temporaryPassword = temporaryPassword,
        )
    }

    @Transactional(readOnly = true)
    fun accountImpact(accountId: Long): AdminAccountDeleteImpactDto {
        val user = findUser(accountId)
        return AdminAccountDeleteImpactDto(
            accountId = accountId,
            username = user.username,
            role = user.role,
            ownedCourseCount = if (user.role == Role.TEACHER) courseRepository.countByTeacherId(accountId) else 0,
            membershipCount = if (user.role == Role.STUDENT) memberRepository.findByStudentIdOrderByJoinedAtDesc(accountId).size.toLong() else 0,
            answerCount = answerRepository.countByStudentId(accountId),
            attendanceCount = attendanceRecordRepository.countByStudentId(accountId),
            submissionCount = submissionRepository.countByStudentId(accountId),
        )
    }

    @Transactional
    fun deleteAccount(accountId: Long, expectedVersion: Long, confirm: Boolean) {
        if (!confirm) {
            throw ApiException(HttpStatus.BAD_REQUEST, "CONFIRM_REQUIRED", "请确认删除操作")
        }
        val user = findUser(accountId)
        assertVersion(user.version, expectedVersion, "账号")
        val files = mutableListOf<String>()
        if (user.role == Role.TEACHER) {
            courseRepository.findByTeacherIdOrderByCreatedAtDesc(accountId).forEach { course ->
                purgeCourseData(course, files)
            }
        } else {
            submissionRepository.findByStudentId(accountId).forEach { files += it.imagePath }
            submissionRepository.deleteByStudentId(accountId)
            answerRepository.deleteByStudentId(accountId)
            attendanceRecordRepository.deleteByStudentId(accountId)
            memberRepository.deleteByStudentId(accountId)
            entityManager.flush()
            entityManager.clear()
        }
        userRepository.deleteById(accountId)
        deleteFilesAfterCommit(files)
    }

    @Transactional(readOnly = true)
    fun courses(query: String, status: String?): List<AdminCourseDto> {
        val q = query.trim().lowercase(Locale.ROOT)
        return courseRepository.findAll()
            .asSequence()
            .filter { course ->
                q.isBlank() || course.name.lowercase(Locale.ROOT).contains(q) ||
                    course.teacher.name.lowercase(Locale.ROOT).contains(q)
            }
            .filter {
                when (status) {
                    "normal" -> it.deletedAt == null && it.archivedAt == null
                    "archived" -> it.deletedAt == null && it.archivedAt != null
                    "deleted" -> it.deletedAt != null
                    else -> true
                }
            }
            .sortedByDescending { it.createdAt }
            .map { it.toAdminCourse() }
            .toList()
    }

    @Transactional
    fun createCourse(request: CreateAdminCourseRequest): AdminCourseDto {
        val teacher = findTeacher(requireNotNull(request.teacherId))
        val course = courseRepository.save(
            Course(
                name = request.name.trim(),
                description = request.description.trim(),
                inviteCode = generateInviteCode(),
                teacher = teacher,
                createdAt = Instant.now(),
            ),
        )
        return course.toAdminCourse()
    }

    @Transactional
    fun updateCourse(courseId: Long, request: UpdateAdminCourseRequest): AdminCourseDto {
        val course = findCourse(courseId)
        assertVersion(course.version, request.expectedVersion, "课程")
        course.name = request.name.trim()
        course.description = request.description.trim()
        course.teacher = findTeacher(requireNotNull(request.teacherId))
        return courseRepository.save(course).toAdminCourse()
    }

    @Transactional
    fun setArchived(courseId: Long, archived: Boolean, expectedVersion: Long): AdminCourseDto {
        val course = findCourse(courseId)
        assertVersion(course.version, expectedVersion, "课程")
        if (course.deletedAt != null) {
            throw ApiException(HttpStatus.CONFLICT, "COURSE_DELETED", "已软删除课程不能归档")
        }
        course.archivedAt = if (archived) Instant.now() else null
        return courseRepository.save(course).toAdminCourse()
    }

    @Transactional
    fun resetInviteCode(courseId: Long, expectedVersion: Long): AdminCourseDto {
        val course = findCourse(courseId)
        assertVersion(course.version, expectedVersion, "课程")
        course.inviteCode = generateInviteCode()
        return courseRepository.save(course).toAdminCourse()
    }

    @Transactional
    fun softDeleteCourse(courseId: Long, expectedVersion: Long): AdminCourseDto {
        val course = findCourse(courseId)
        assertVersion(course.version, expectedVersion, "课程")
        if (course.deletedAt == null) {
            course.deletedAt = Instant.now()
            course.archivedAt = course.archivedAt ?: course.deletedAt
        }
        return courseRepository.save(course).toAdminCourse()
    }

    @Transactional(readOnly = true)
    fun courseImpact(courseId: Long): AdminCourseImpactDto {
        val course = findCourse(courseId)
        return AdminCourseImpactDto(
            courseId = courseId,
            members = memberRepository.countByCourseId(courseId),
            questions = questionRepository.countByCourseIdAndDeletedAtIsNull(courseId),
            answers = answerRepository.countByQuestionCourseId(courseId),
            assignments = assignmentRepository.countByCourseId(courseId),
            submissions = submissionRepository.countByAssignmentCourseId(courseId),
            attendanceSessions = attendanceSessionRepository.countByCourseId(courseId),
            attendanceRecords = attendanceRecordRepository.countBySessionCourseId(courseId),
        )
    }

    @Transactional
    fun permanentDeleteCourse(courseId: Long, expectedVersion: Long, confirmName: String) {
        val course = findCourse(courseId)
        if (course.deletedAt == null) {
            throw ApiException(HttpStatus.CONFLICT, "COURSE_NOT_SOFT_DELETED", "请先软删除课程，再进行永久删除")
        }
        assertVersion(course.version, expectedVersion, "课程")
        if (course.name != confirmName.trim()) {
            throw ApiException(HttpStatus.BAD_REQUEST, "CONFIRM_NAME_MISMATCH", "课程名称确认不一致")
        }
        val files = mutableListOf<String>()
        purgeCourseData(course, files)
        deleteFilesAfterCommit(files)
    }

    @Transactional(readOnly = true)
    fun members(courseId: Long): List<AdminMemberDto> {
        findCourse(courseId)
        return memberRepository.findByCourseIdOrderByJoinedAtAsc(courseId).map { it.toAdminMember() }
    }

    @Transactional
    fun addMember(courseId: Long, request: AddAdminMemberRequest): List<AdminMemberDto> {
        val course = findCourse(courseId)
        if (course.deletedAt != null) {
            throw ApiException(HttpStatus.CONFLICT, "COURSE_DELETED", "已删除课程不能添加成员")
        }
        val student = userRepository.findByUsername(request.studentNo.trim())
            ?: throw ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "学生账号不存在")
        if (student.role != Role.STUDENT) {
            throw ApiException(HttpStatus.BAD_REQUEST, "NOT_STUDENT", "只能添加学生账号")
        }
        if (memberRepository.existsByCourseIdAndStudentId(courseId, requireNotNull(student.id))) {
            throw ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "学生已在课程中")
        }
        memberRepository.save(CourseMember(course = course, student = student, joinedAt = Instant.now()))
        return members(courseId)
    }

    @Transactional
    fun removeMember(courseId: Long, studentId: Long) {
        findCourse(courseId)
        val membership = memberRepository.findByCourseIdAndStudentId(courseId, studentId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "学生不在课程中")
        memberRepository.delete(membership)
    }

    @Transactional(readOnly = true)
    fun courseDetail(courseId: Long): AdminCourseDetailDto {
        val course = findCourse(courseId)
        val questions = questionRepository.findByCourseIdAndDeletedAtIsNullOrderByCreatedAtDesc(courseId)
            .map {
                AdminQuestionDto(
                    id = requireNotNull(it.id),
                    type = it.type,
                    prompt = it.prompt,
                    deadlineAt = it.deadlineAt,
                    submissionCount = answerRepository.countByQuestionId(requireNotNull(it.id)),
                    correctCount = answerRepository.countByQuestionIdAndIsCorrectTrue(requireNotNull(it.id)),
                )
            }
        val assignments = assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId).map { assignment ->
            val submissions = submissionRepository.findByAssignmentIdOrderByUpdatedAtAsc(requireNotNull(assignment.id))
            AdminAssignmentDto(
                id = requireNotNull(assignment.id),
                title = assignment.title,
                description = assignment.description,
                deadlineAt = assignment.deadlineAt,
                submissionCount = submissions.size.toLong(),
                submissions = submissions.map { submission ->
                    AdminSubmissionDto(
                        id = requireNotNull(submission.id),
                        studentNo = submission.student.username,
                        studentName = submission.student.name,
                        imageUrl = "/api/v1/submissions/${submission.id}/image",
                        note = submission.note,
                        score = submission.score,
                        feedback = submission.feedback,
                        submittedAt = submission.submittedAt,
                        updatedAt = submission.updatedAt,
                    )
                },
            )
        }
        return AdminCourseDetailDto(
            course = course.toAdminCourse(),
            members = members(courseId),
            questions = questions,
            assignments = assignments,
        )
    }

    @Transactional(readOnly = true)
    fun studentDetail(accountId: Long): AdminStudentDetailDto {
        val user = findUser(accountId)
        if (user.role != Role.STUDENT) {
            throw ApiException(HttpStatus.BAD_REQUEST, "NOT_STUDENT", "该账号不是学生")
        }
        val courses = memberRepository.findByStudentIdOrderByJoinedAtDesc(accountId)
            .filter { it.course.deletedAt == null }
            .map { statsService.studentCourseStatsForAdmin(requireNotNull(it.course.id), accountId) }
        return AdminStudentDetailDto(user.toAdminAccount(), courses)
    }

    private fun purgeCourseData(course: Course, files: MutableList<String>) {
        val courseId = requireNotNull(course.id)
        submissionRepository.findByAssignmentCourseId(courseId).forEach { files += it.imagePath }
        answerRepository.deleteByCourseId(courseId)
        questionRepository.deleteByCourseId(courseId)
        attendanceRecordRepository.deleteByCourseId(courseId)
        attendanceSessionRepository.deleteByCourseId(courseId)
        submissionRepository.deleteByCourseId(courseId)
        assignmentRepository.deleteByCourseId(courseId)
        memberRepository.deleteByCourseId(courseId)
        entityManager.flush()
        entityManager.clear()
        courseRepository.deleteById(courseId)
    }

    private fun deleteFilesAfterCommit(files: List<String>) {
        val unique = files.distinct()
        if (unique.isEmpty()) return
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            unique.forEach(storageService::delete)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    unique.forEach(storageService::delete)
                }
            },
        )
    }

    private fun findUser(id: Long): User = userRepository.findById(id).orElseThrow {
        ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在")
    }

    private fun findCourse(id: Long): Course = courseRepository.findById(id).orElseThrow {
        ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在")
    }

    private fun findTeacher(id: Long): User {
        val user = findUser(id)
        if (user.role != Role.TEACHER) {
            throw ApiException(HttpStatus.BAD_REQUEST, "NOT_TEACHER", "任课教师必须是教师账号")
        }
        return user
    }

    private fun assertVersion(actual: Long, expected: Long?, label: String) {
        if (expected == null || expected != actual) {
            throw ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "${label}已被其他人修改，请刷新后重新核对")
        }
    }

    private fun generateInviteCode(): String {
        repeat(30) {
            val code = buildString {
                repeat(7) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            if (!courseRepository.existsByInviteCode(code)) return code
        }
        throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVITE_CODE_FAILED", "邀请码生成失败")
    }

    private fun User.toAdminAccount(): AdminAccountDto = AdminAccountDto(
        id = requireNotNull(id),
        username = username,
        name = name,
        role = role,
        courseCount = if (role == Role.TEACHER) {
            courseRepository.countByTeacherId(requireNotNull(id))
        } else {
            memberRepository.findByStudentIdOrderByJoinedAtDesc(requireNotNull(id)).size.toLong()
        },
        createdAt = createdAt,
        version = version,
    )

    private fun Course.toAdminCourse(): AdminCourseDto = AdminCourseDto(
        id = requireNotNull(id),
        name = name,
        description = description,
        inviteCode = inviteCode,
        teacherId = requireNotNull(teacher.id),
        teacherName = teacher.name,
        memberCount = memberRepository.countByCourseId(requireNotNull(id)),
        questionCount = questionRepository.countByCourseIdAndDeletedAtIsNull(requireNotNull(id)),
        assignmentCount = assignmentRepository.countByCourseId(requireNotNull(id)),
        archived = archivedAt != null,
        deleted = deletedAt != null,
        createdAt = createdAt,
        version = version,
    )

    private fun CourseMember.toAdminMember(): AdminMemberDto = AdminMemberDto(
        studentId = requireNotNull(student.id),
        studentNo = student.username,
        name = student.name,
        joinedAt = joinedAt,
    )
}
