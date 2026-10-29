package com.classpulse.repository

import com.classpulse.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean
    fun countByRole(role: Role): Long

    @Query(
        """
        select u from User u
        where (:q = '' or lower(u.username) like lower(concat('%', :q, '%'))
            or lower(u.name) like lower(concat('%', :q, '%')))
          and (:role is null or u.role = :role)
        order by u.createdAt desc
        """,
    )
    fun adminSearch(@Param("q") q: String, @Param("role") role: Role?): List<User>

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
    fun countByTeacherId(teacherId: Long): Long
}

interface CourseMemberRepository : JpaRepository<CourseMember, Long> {
    @EntityGraph(attributePaths = ["course", "course.teacher"])
    fun findByStudentIdOrderByJoinedAtDesc(studentId: Long): List<CourseMember>

    @EntityGraph(attributePaths = ["student"])
    fun findByCourseIdOrderByJoinedAtAsc(courseId: Long): List<CourseMember>

    fun findByCourseIdAndStudentId(courseId: Long, studentId: Long): CourseMember?
    fun existsByCourseIdAndStudentId(courseId: Long, studentId: Long): Boolean
    fun countByCourseId(courseId: Long): Long

    @Modifying
    @Query("delete from CourseMember m where m.student.id = :studentId")
    fun deleteByStudentId(@Param("studentId") studentId: Long)

    @Modifying
    @Query("delete from CourseMember m where m.course.id = :courseId")
    fun deleteByCourseId(@Param("courseId") courseId: Long)
}

interface AttendanceSessionRepository : JpaRepository<AttendanceSession, Long> {
    fun findByCourseIdOrderByStartsAtDesc(courseId: Long): List<AttendanceSession>
    fun findByIdAndCourseId(id: Long, courseId: Long): AttendanceSession?
    fun countByCourseId(courseId: Long): Long

    @Modifying
    @Query("delete from AttendanceSession s where s.course.id = :courseId")
    fun deleteByCourseId(@Param("courseId") courseId: Long)
}

interface AttendanceRecordRepository : JpaRepository<AttendanceRecord, Long> {
    fun countBySessionId(sessionId: Long): Long
    fun countBySessionIdAndStudentIdIn(sessionId: Long, studentIds: Collection<Long>): Long
    fun countBySessionCourseId(courseId: Long): Long
    fun countByStudentId(studentId: Long): Long
    fun existsBySessionIdAndStudentId(sessionId: Long, studentId: Long): Boolean
    fun findBySessionIdAndStudentId(sessionId: Long, studentId: Long): AttendanceRecord?

    @EntityGraph(attributePaths = ["student"])
    fun findBySessionIdOrderByCheckedAtAsc(sessionId: Long): List<AttendanceRecord>

    @Modifying
    @Query("delete from AttendanceRecord r where r.student.id = :studentId")
    fun deleteByStudentId(@Param("studentId") studentId: Long)

    @Modifying
    @Query("delete from AttendanceRecord r where r.session.id in (select s.id from AttendanceSession s where s.course.id = :courseId)")
    fun deleteByCourseId(@Param("courseId") courseId: Long)
}

interface QuestionRepository : JpaRepository<Question, Long> {
    fun findByCourseIdOrderByCreatedAtDesc(courseId: Long): List<Question>
    fun findByCourseIdAndDeletedAtIsNullOrderByCreatedAtDesc(courseId: Long): List<Question>
    fun countByCourseIdAndDeletedAtIsNull(courseId: Long): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Question q where q.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Question?

    @Modifying
    @Query("delete from Question q where q.course.id = :courseId")
    fun deleteByCourseId(@Param("courseId") courseId: Long)
}

interface AnswerRepository : JpaRepository<Answer, Long> {
    fun countByQuestionId(questionId: Long): Long
    fun countByQuestionIdAndIsCorrectTrue(questionId: Long): Long
    fun countByQuestionCourseId(courseId: Long): Long
    fun countByStudentId(studentId: Long): Long
    fun countByQuestionIdAndStudentIdIn(questionId: Long, studentIds: Collection<Long>): Long
    fun countByQuestionIdAndIsCorrectTrueAndStudentIdIn(questionId: Long, studentIds: Collection<Long>): Long
    fun findByQuestionIdAndStudentId(questionId: Long, studentId: Long): Answer?

    @EntityGraph(attributePaths = ["student"])
    fun findByQuestionIdOrderBySubmittedAtAsc(questionId: Long): List<Answer>

    @EntityGraph(attributePaths = ["student"])
    fun findByQuestionIdAndStudentIdInOrderBySubmittedAtAsc(questionId: Long, studentIds: Collection<Long>): List<Answer>

    @Modifying
    @Query("delete from Answer a where a.student.id = :studentId")
    fun deleteByStudentId(@Param("studentId") studentId: Long)

    @Modifying
    @Query("delete from Answer a where a.question.id in (select q.id from Question q where q.course.id = :courseId)")
    fun deleteByCourseId(@Param("courseId") courseId: Long)
}

interface AssignmentRepository : JpaRepository<Assignment, Long> {
    fun findByCourseIdOrderByCreatedAtDesc(courseId: Long): List<Assignment>
    fun countByCourseId(courseId: Long): Long

    @Modifying
    @Query("delete from Assignment a where a.course.id = :courseId")
    fun deleteByCourseId(@Param("courseId") courseId: Long)
}

interface AdminUserRepository : JpaRepository<AdminUser, Long> {
    fun findByUsername(username: String): AdminUser?
    fun existsByUsername(username: String): Boolean
}

interface AdminSessionRepository : JpaRepository<AdminSession, Long> {
    @EntityGraph(attributePaths = ["admin"])
    fun findByTokenHash(tokenHash: String): AdminSession?

    @Modifying
    @Query("delete from AdminSession s where s.expiresAt < :now")
    fun deleteExpired(@Param("now") now: java.time.Instant)
}

interface SubmissionRepository : JpaRepository<Submission, Long> {
    fun countByAssignmentId(assignmentId: Long): Long
    fun countByAssignmentIdAndStudentIdIn(assignmentId: Long, studentIds: Collection<Long>): Long
    fun countByStudentId(studentId: Long): Long
    fun countByAssignmentCourseId(courseId: Long): Long
    fun findByAssignmentIdAndStudentId(assignmentId: Long, studentId: Long): Submission?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s where s.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Submission?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s where s.assignment.id = :assignmentId and s.student.id = :studentId")
    fun findByAssignmentIdAndStudentIdForUpdate(
        @Param("assignmentId") assignmentId: Long,
        @Param("studentId") studentId: Long,
    ): Submission?

    @EntityGraph(attributePaths = ["student"])
    fun findByAssignmentIdOrderByUpdatedAtAsc(assignmentId: Long): List<Submission>

    fun findByStudentId(studentId: Long): List<Submission>
    fun findByAssignmentCourseId(courseId: Long): List<Submission>

    @Modifying
    @Query("delete from Submission s where s.student.id = :studentId")
    fun deleteByStudentId(@Param("studentId") studentId: Long)

    @Modifying
    @Query("delete from Submission s where s.assignment.course.id = :courseId")
    fun deleteByCourseId(@Param("courseId") courseId: Long)
}
