package com.classpulse.repository

import com.classpulse.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean

    /**
     * Serializes submissions from the same student across concurrent requests.
     * The lock is released when the surrounding transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): User?
}

interface CourseRepository : JpaRepository<Course, Long> {
    fun findByTeacherIdOrderByCreatedAtDesc(teacherId: Long): List<Course>
    fun findByIdAndTeacherId(id: Long, teacherId: Long): Course?
    fun findByInviteCode(inviteCode: String): Course?
    fun existsByInviteCode(inviteCode: String): Boolean
}

interface CourseMemberRepository : JpaRepository<CourseMember, Long> {
    @EntityGraph(attributePaths = ["course", "course.teacher"])
    fun findByStudentIdOrderByJoinedAtDesc(studentId: Long): List<CourseMember>

    @EntityGraph(attributePaths = ["student"])
    fun findByCourseIdOrderByJoinedAtAsc(courseId: Long): List<CourseMember>

    fun findByCourseIdAndStudentId(courseId: Long, studentId: Long): CourseMember?
    fun existsByCourseIdAndStudentId(courseId: Long, studentId: Long): Boolean
    fun countByCourseId(courseId: Long): Long
}

interface AttendanceSessionRepository : JpaRepository<AttendanceSession, Long> {
    fun findByCourseIdOrderByStartsAtDesc(courseId: Long): List<AttendanceSession>
    fun findByIdAndCourseId(id: Long, courseId: Long): AttendanceSession?
}

interface AttendanceRecordRepository : JpaRepository<AttendanceRecord, Long> {
    fun countBySessionId(sessionId: Long): Long
    fun existsBySessionIdAndStudentId(sessionId: Long, studentId: Long): Boolean
    fun findBySessionIdAndStudentId(sessionId: Long, studentId: Long): AttendanceRecord?

    @EntityGraph(attributePaths = ["student"])
    fun findBySessionIdOrderByCheckedAtAsc(sessionId: Long): List<AttendanceRecord>
}

interface QuestionRepository : JpaRepository<Question, Long> {
    fun findByCourseIdOrderByCreatedAtDesc(courseId: Long): List<Question>
}

interface AnswerRepository : JpaRepository<Answer, Long> {
    fun countByQuestionId(questionId: Long): Long
    fun countByQuestionIdAndIsCorrectTrue(questionId: Long): Long
    fun findByQuestionIdAndStudentId(questionId: Long, studentId: Long): Answer?

    @EntityGraph(attributePaths = ["student"])
    fun findByQuestionIdOrderBySubmittedAtAsc(questionId: Long): List<Answer>
}

interface AssignmentRepository : JpaRepository<Assignment, Long> {
    fun findByCourseIdOrderByCreatedAtDesc(courseId: Long): List<Assignment>
}

interface SubmissionRepository : JpaRepository<Submission, Long> {
    fun countByAssignmentId(assignmentId: Long): Long
    fun findByAssignmentIdAndStudentId(assignmentId: Long, studentId: Long): Submission?

    @EntityGraph(attributePaths = ["student"])
    fun findByAssignmentIdOrderByUpdatedAtAsc(assignmentId: Long): List<Submission>
}
