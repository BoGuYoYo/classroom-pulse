package com.classpulse.service

import com.classpulse.domain.Course
import com.classpulse.domain.CourseMember
import com.classpulse.domain.Role
import com.classpulse.repository.CourseMemberRepository
import com.classpulse.repository.CourseRepository
import com.classpulse.repository.UserRepository
import com.classpulse.web.ApiException
import com.classpulse.web.dto.CourseSummaryDto
import com.classpulse.web.dto.CreateCourseRequest
import com.classpulse.web.dto.JoinCourseRequest
import com.classpulse.web.dto.MemberDto
import com.classpulse.web.dto.ResetStudentPasswordDto
import com.classpulse.web.dto.UpdateCourseRequest
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant

@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val courseMemberRepository: CourseMemberRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val accessGuard: AccessGuard,
) {
    private val random = SecureRandom()
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    @Transactional
    fun create(request: CreateCourseRequest): CourseSummaryDto {
        accessGuard.requireTeacher()
        val teacher = accessGuard.currentUser()
        val course = courseRepository.save(
            Course(
                name = request.name.trim(),
                description = request.description.trim(),
                inviteCode = generateInviteCode(),
                teacher = teacher,
                createdAt = Instant.now(),
            ),
        )
        return course.toSummary(memberCount = 0, joinedAt = null, includeInviteCode = true)
    }

    @Transactional
    fun join(request: JoinCourseRequest): CourseSummaryDto {
        val principal = accessGuard.requireStudent()
        val student = accessGuard.currentUser()
        val course = courseRepository.findByInviteCode(request.inviteCode.trim().uppercase())
            ?: throw ApiException(HttpStatus.NOT_FOUND, "INVALID_INVITE_CODE", "邀请码无效，请核对后重试")
        if (course.deletedAt != null) {
            throw ApiException(HttpStatus.NOT_FOUND, "INVALID_INVITE_CODE", "邀请码无效，请核对后重试")
        }
        accessGuard.ensureWritable(course)
        if (courseMemberRepository.existsByCourseIdAndStudentId(requireNotNull(course.id), principal.id)) {
            throw ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "你已加入该课程")
        }
        val joinedAt = Instant.now()
        courseMemberRepository.save(
            CourseMember(
                course = course,
                student = student,
                joinedAt = joinedAt,
            ),
        )
        return course.toSummary(
            memberCount = courseMemberRepository.countByCourseId(requireNotNull(course.id)),
            joinedAt = joinedAt,
            includeInviteCode = false,
        )
    }

    @Transactional(readOnly = true)
    fun list(): List<CourseSummaryDto> {
        val principal = accessGuard.principal()
        return if (principal.role == Role.TEACHER) {
            courseRepository.findByTeacherIdOrderByCreatedAtDesc(principal.id)
                .filter { it.deletedAt == null }
                .map { course ->
                    course.toSummary(
                        memberCount = courseMemberRepository.countByCourseId(requireNotNull(course.id)),
                        joinedAt = null,
                        includeInviteCode = true,
                    )
                }
        } else {
            courseMemberRepository.findByStudentIdOrderByJoinedAtDesc(principal.id)
                .filter { it.course.deletedAt == null }
                .map { membership ->
                    val course = membership.course
                    course.toSummary(
                        memberCount = courseMemberRepository.countByCourseId(requireNotNull(course.id)),
                        joinedAt = membership.joinedAt,
                        includeInviteCode = false,
                    )
                }
        }
    }

    @Transactional(readOnly = true)
    fun detail(courseId: Long): CourseSummaryDto {
        val principal = accessGuard.principal()
        val course = accessGuard.readableCourse(courseId)
        val membership = if (principal.role == Role.STUDENT) {
            courseMemberRepository.findByCourseIdAndStudentId(courseId, principal.id)
        } else {
            null
        }
        return course.toSummary(
            memberCount = courseMemberRepository.countByCourseId(courseId),
            joinedAt = membership?.joinedAt,
            includeInviteCode = principal.role == Role.TEACHER,
        )
    }

    @Transactional(readOnly = true)
    fun members(courseId: Long): List<MemberDto> {
        accessGuard.teacherCourse(courseId)
        return courseMemberRepository.findByCourseIdOrderByJoinedAtAsc(courseId).map { member ->
            MemberDto(
                studentId = requireNotNull(member.student.id),
                studentNo = member.student.username,
                name = member.student.name,
                joinedAt = member.joinedAt,
            )
        }
    }

    @Transactional
    fun update(courseId: Long, request: UpdateCourseRequest): CourseSummaryDto {
        val course = accessGuard.teacherCourse(courseId)
        course.name = request.name.trim()
        course.description = request.description.trim()
        courseRepository.save(course)
        return course.toSummary(
            memberCount = courseMemberRepository.countByCourseId(courseId),
            joinedAt = null,
            includeInviteCode = true,
        )
    }

    @Transactional
    fun setArchived(courseId: Long, archived: Boolean): CourseSummaryDto {
        val course = accessGuard.teacherCourse(courseId)
        course.archivedAt = if (archived) Instant.now() else null
        courseRepository.save(course)
        return course.toSummary(
            memberCount = courseMemberRepository.countByCourseId(courseId),
            joinedAt = null,
            includeInviteCode = true,
        )
    }

    @Transactional
    fun delete(courseId: Long) {
        val course = accessGuard.teacherCourse(courseId)
        course.deletedAt = Instant.now()
        course.archivedAt = course.archivedAt ?: course.deletedAt
        courseRepository.save(course)
    }

    @Transactional
    fun resetInviteCode(courseId: Long): CourseSummaryDto {
        val course = accessGuard.teacherCourse(courseId)
        course.inviteCode = generateInviteCode()
        courseRepository.save(course)
        return course.toSummary(
            memberCount = courseMemberRepository.countByCourseId(courseId),
            joinedAt = null,
            includeInviteCode = true,
        )
    }

    @Transactional
    fun removeMember(courseId: Long, studentId: Long) {
        accessGuard.teacherCourse(courseId)
        val membership = courseMemberRepository.findByCourseIdAndStudentId(courseId, studentId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "学生不在该课程中")
        courseMemberRepository.delete(membership)
    }

    @Transactional
    fun leave(courseId: Long) {
        val principal = accessGuard.requireStudent()
        accessGuard.studentCourse(courseId)
        val membership = courseMemberRepository.findByCourseIdAndStudentId(courseId, principal.id)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "你尚未加入该课程")
        courseMemberRepository.delete(membership)
    }

    @Transactional
    fun resetStudentPassword(courseId: Long, studentId: Long): ResetStudentPasswordDto {
        accessGuard.teacherCourse(courseId)
        val membership = courseMemberRepository.findByCourseIdAndStudentId(courseId, studentId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "学生不在该课程中")
        val student = userRepository.findById(studentId).orElseThrow {
            throw ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "学生不存在")
        }
        val temporaryPassword = "Tmp-" + buildString {
            repeat(12) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
        student.passwordHash = passwordEncoder.encode(temporaryPassword)
        student.authVersion += 1
        userRepository.save(student)
        return ResetStudentPasswordDto(
            studentId = studentId,
            studentNo = membership.student.username,
            temporaryPassword = temporaryPassword,
        )
    }

    private fun generateInviteCode(): String {
        repeat(20) {
            val code = buildString {
                repeat(7) {
                    append(alphabet[random.nextInt(alphabet.length)])
                }
            }
            if (!courseRepository.existsByInviteCode(code)) return code
        }
        throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVITE_CODE_FAILED", "邀请码生成失败，请重试")
    }

    private fun Course.toSummary(
        memberCount: Long,
        joinedAt: Instant?,
        includeInviteCode: Boolean,
    ): CourseSummaryDto = CourseSummaryDto(
        id = requireNotNull(id),
        name = name,
        description = description,
        inviteCode = if (includeInviteCode) inviteCode else null,
        teacherName = teacher.name,
        memberCount = memberCount,
        createdAt = createdAt,
        joinedAt = joinedAt,
        archived = archivedAt != null,
    )
}
