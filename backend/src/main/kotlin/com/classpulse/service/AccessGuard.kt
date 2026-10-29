package com.classpulse.service

import com.classpulse.domain.Course
import com.classpulse.domain.Role
import com.classpulse.domain.User
import com.classpulse.repository.CourseMemberRepository
import com.classpulse.repository.CourseRepository
import com.classpulse.repository.UserRepository
import com.classpulse.security.UserPrincipal
import com.classpulse.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class AccessGuard(
    private val userRepository: UserRepository,
    private val courseRepository: CourseRepository,
    private val courseMemberRepository: CourseMemberRepository,
) {
    fun principal(): UserPrincipal {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录")
        return authentication.principal as? UserPrincipal
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录")
    }

    fun requireTeacher(): UserPrincipal {
        val principal = principal()
        if (principal.role != Role.TEACHER) {
            throw ApiException(HttpStatus.FORBIDDEN, "TEACHER_ONLY", "该操作仅限教师")
        }
        return principal
    }

    fun requireStudent(): UserPrincipal {
        val principal = principal()
        if (principal.role != Role.STUDENT) {
            throw ApiException(HttpStatus.FORBIDDEN, "STUDENT_ONLY", "该操作仅限学生")
        }
        return principal
    }

    fun currentUser(): User {
        val principal = principal()
        return userRepository.findById(principal.id).orElseThrow {
            ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "账号不存在")
        }
    }

    /**
     * Acquire a row lock on the current student for the duration of the
     * surrounding transaction. Assignment replacement uses it to serialize
     * concurrent submit/replace requests from the same student.
     */
    fun lockCurrentUser(): User {
        val principal = principal()
        return userRepository.findByIdForUpdate(principal.id) ?: throw ApiException(
            HttpStatus.UNAUTHORIZED,
            "USER_NOT_FOUND",
            "账号不存在",
        )
    }

    fun teacherCourse(courseId: Long): Course {
        val principal = requireTeacher()
        val course = courseRepository.findById(courseId).orElseThrow {
            throw ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在")
        }
        if (course.deletedAt != null) {
            throw ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在")
        }
        if (course.teacher.id != principal.id) {
            throw ApiException(HttpStatus.FORBIDDEN, "COURSE_FORBIDDEN", "只能管理自己创建的课程")
        }
        return course
    }

    fun studentCourse(courseId: Long): Course {
        val principal = requireStudent()
        val course = courseRepository.findById(courseId).orElseThrow {
            throw ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在")
        }
        if (course.deletedAt != null) {
            throw ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在")
        }
        if (!courseMemberRepository.existsByCourseIdAndStudentId(courseId, principal.id)) {
            throw ApiException(HttpStatus.FORBIDDEN, "NOT_MEMBER", "你尚未加入该课程")
        }
        return course
    }

    fun ensureWritable(course: Course) {
        if (course.archivedAt != null) {
            throw ApiException(HttpStatus.CONFLICT, "COURSE_ARCHIVED", "课程已归档，只能查看，不能继续操作")
        }
    }

    fun readableCourse(courseId: Long): Course {
        val principal = principal()
        return if (principal.role == Role.TEACHER) {
            teacherCourse(courseId)
        } else {
            studentCourse(courseId)
        }
    }

    fun ensureStudentMember(courseId: Long, studentId: Long) {
        if (!courseMemberRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw ApiException(HttpStatus.FORBIDDEN, "NOT_MEMBER", "你尚未加入该课程")
        }
    }
}
