package com.classpulse.service

import com.classpulse.domain.Course
import com.classpulse.domain.CourseMember
import com.classpulse.domain.Role
import com.classpulse.repository.CourseMemberRepository
import com.classpulse.repository.CourseRepository
import com.classpulse.web.ApiException
import com.classpulse.web.dto.CourseSummaryDto
import com.classpulse.web.dto.CreateCourseRequest
import com.classpulse.web.dto.JoinCourseRequest
import com.classpulse.web.dto.MemberDto
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant

@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val courseMemberRepository: CourseMemberRepository,
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
            courseRepository.findByTeacherIdOrderByCreatedAtDesc(principal.id).map { course ->
                course.toSummary(
                    memberCount = courseMemberRepository.countByCourseId(requireNotNull(course.id)),
                    joinedAt = null,
                    includeInviteCode = true,
                )
            }
        } else {
            courseMemberRepository.findByStudentIdOrderByJoinedAtDesc(principal.id).map { membership ->
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
    )
}
