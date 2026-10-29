package com.classpulse.service

import com.classpulse.domain.AttendanceRecord
import com.classpulse.domain.AttendanceSession
import com.classpulse.domain.AttendanceStatus
import com.classpulse.domain.Role
import com.classpulse.repository.AttendanceRecordRepository
import com.classpulse.repository.AttendanceSessionRepository
import com.classpulse.repository.CourseMemberRepository
import com.classpulse.web.ApiException
import com.classpulse.web.dto.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant

@Service
class AttendanceService(
    private val sessionRepository: AttendanceSessionRepository,
    private val recordRepository: AttendanceRecordRepository,
    private val memberRepository: CourseMemberRepository,
    private val accessGuard: AccessGuard,
) {
    private val random = SecureRandom()

    @Transactional
    fun create(courseId: Long, request: CreateAttendanceRequest): AttendanceDto {
        val course = accessGuard.teacherCourse(courseId)
        accessGuard.ensureWritable(course)
        val now = Instant.now()
        val session = sessionRepository.save(
            AttendanceSession(
                course = course,
                title = request.title.trim(),
                code = String.format("%06d", random.nextInt(1_000_000)),
                startsAt = now,
                endsAt = now.plusSeconds(request.durationMinutes.toLong() * 60),
                createdAt = now,
            ),
        )
        return session.toDto(includeCode = true)
    }

    @Transactional(readOnly = true)
    fun list(courseId: Long): List<AttendanceDto> {
        val principal = accessGuard.principal()
        accessGuard.readableCourse(courseId)
        return sessionRepository.findByCourseIdOrderByStartsAtDesc(courseId).map { session ->
            session.toDto(includeCode = principal.role == Role.TEACHER)
        }
    }

    @Transactional(readOnly = true)
    fun results(sessionId: Long): AttendanceDetailDto {
        accessGuard.requireTeacher()
        val session = findSession(sessionId)
        accessGuard.teacherCourse(requireNotNull(session.course.id))
        val members = memberRepository.findByCourseIdOrderByJoinedAtAsc(requireNotNull(session.course.id))
        val records = recordRepository.findBySessionIdOrderByCheckedAtAsc(sessionId)
        val recordsByStudent = records.associateBy { requireNotNull(it.student.id) }
        val present = members.mapNotNull { member ->
            val record = recordsByStudent[member.student.id] ?: return@mapNotNull null
            AttendancePersonDto(
                studentId = requireNotNull(member.student.id),
                studentNo = member.student.username,
                name = member.student.name,
                checkedAt = record.checkedAt,
            )
        }.sortedBy { it.checkedAt ?: Instant.EPOCH }
        val absent = members.mapNotNull { member ->
            if (recordsByStudent.containsKey(member.student.id)) return@mapNotNull null
            AttendancePersonDto(
                studentId = requireNotNull(member.student.id),
                studentNo = member.student.username,
                name = member.student.name,
                checkedAt = null,
            )
        }
        return AttendanceDetailDto(
            attendance = session.toDto(includeCode = true),
            present = present,
            absent = absent,
        )
    }

    @Transactional
    fun checkIn(sessionId: Long, request: CheckInRequest): CheckInResultDto {
        val principal = accessGuard.requireStudent()
        val session = findSession(sessionId)
        val courseId = requireNotNull(session.course.id)
        accessGuard.ensureStudentMember(courseId, principal.id)
        accessGuard.ensureWritable(session.course)
        val now = Instant.now()
        if (now.isBefore(session.startsAt) || !now.isBefore(session.endsAt)) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ATTENDANCE_CLOSED", "签到已结束或尚未开始")
        }
        if (session.code != request.code.trim()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ATTENDANCE_CODE", "签到码不正确")
        }
        if (recordRepository.existsBySessionIdAndStudentId(sessionId, principal.id)) {
            throw ApiException(HttpStatus.CONFLICT, "ALREADY_CHECKED_IN", "你已完成本次签到，无需重复提交")
        }
        val student = accessGuard.currentUser()
        val checkedAt = Instant.now()
        recordRepository.save(
            AttendanceRecord(
                session = session,
                student = student,
                checkedAt = checkedAt,
                status = AttendanceStatus.PRESENT,
            ),
        )
        return CheckInResultDto(
            attendanceId = sessionId,
            title = session.title,
            checkedAt = checkedAt,
            message = "签到成功",
        )
    }

    private fun findSession(sessionId: Long): AttendanceSession =
        sessionRepository.findById(sessionId).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "ATTENDANCE_NOT_FOUND", "签到活动不存在")
        }

    private fun AttendanceSession.toDto(includeCode: Boolean): AttendanceDto {
        val id = requireNotNull(id)
        val courseId = requireNotNull(course.id)
        val principal = accessGuard.principal()
        val record = if (principal.role == Role.STUDENT) {
            recordRepository.findBySessionIdAndStudentId(id, principal.id)
        } else {
            null
        }
        val now = Instant.now()
        return AttendanceDto(
            id = id,
            courseId = courseId,
            title = title,
            code = if (includeCode) code else null,
            startsAt = startsAt,
            endsAt = endsAt,
            createdAt = createdAt,
            memberCount = memberRepository.countByCourseId(courseId),
            presentCount = recordRepository.countBySessionId(id),
            active = !now.isBefore(startsAt) && now.isBefore(endsAt),
            myCheckedIn = record != null,
            myCheckedAt = record?.checkedAt,
        )
    }
}
