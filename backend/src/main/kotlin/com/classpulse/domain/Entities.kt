package com.classpulse.domain

import jakarta.persistence.*
import java.time.Instant

enum class Role { STUDENT, TEACHER }

enum class QuestionType { SINGLE_CHOICE, MULTIPLE_CHOICE, FILL_BLANK }

enum class AttendanceStatus { PRESENT }

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 40)
    var username: String = "",

    @Column(nullable = false, length = 60)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var role: Role = Role.STUDENT,

    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "courses")
class Course(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 120)
    var name: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Column(name = "invite_code", nullable = false, unique = true, length = 12)
    var inviteCode: String = "",

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    var teacher: User,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "archived_at")
    var archivedAt: Instant? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

@Entity
@Table(
    name = "course_members",
    uniqueConstraints = [UniqueConstraint(name = "uk_course_member", columnNames = ["course_id", "student_id"])],
)
class CourseMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    var course: Course,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    var student: User,

    @Column(name = "joined_at", nullable = false)
    var joinedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "attendance_sessions")
class AttendanceSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    var course: Course,

    @Column(nullable = false, length = 160)
    var title: String = "",

    @Column(nullable = false, length = 12)
    var code: String = "",

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant = Instant.now(),

    @Column(name = "ends_at", nullable = false)
    var endsAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = [UniqueConstraint(name = "uk_attendance_record", columnNames = ["session_id", "student_id"])],
)
class AttendanceRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    var session: AttendanceSession,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    var student: User,

    @Column(name = "checked_at", nullable = false)
    var checkedAt: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: AttendanceStatus = AttendanceStatus.PRESENT,
)

@Entity
@Table(name = "questions")
class Question(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    var course: Course,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var type: QuestionType = QuestionType.SINGLE_CHOICE,

    @Column(nullable = false, columnDefinition = "TEXT")
    var prompt: String = "",

    @Column(name = "options_json", columnDefinition = "TEXT")
    var optionsJson: String? = null,

    @Column(name = "standard_answer", nullable = false, columnDefinition = "TEXT")
    var standardAnswer: String = "",

    @Column(name = "deadline_at", nullable = false)
    var deadlineAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

@Entity
@Table(
    name = "answers",
    uniqueConstraints = [UniqueConstraint(name = "uk_answer_student", columnNames = ["question_id", "student_id"])],
)
class Answer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    var question: Question,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    var student: User,

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    var answerText: String = "",

    @Column(name = "is_correct", nullable = false)
    var isCorrect: Boolean = false,

    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "assignments")
class Assignment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    var course: Course,

    @Column(nullable = false, length = 160)
    var title: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Column(name = "deadline_at", nullable = false)
    var deadlineAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(
    name = "submissions",
    uniqueConstraints = [UniqueConstraint(name = "uk_submission_student", columnNames = ["assignment_id", "student_id"])],
)
class Submission(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    var assignment: Assignment,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    var student: User,

    @Column(name = "image_path", nullable = false, length = 255)
    var imagePath: String = "",

    @Column(name = "original_filename", nullable = false, length = 255)
    var originalFilename: String = "",

    @Column(name = "content_type", nullable = false, length = 64)
    var contentType: String = "",

    @Column(name = "file_size", nullable = false)
    var fileSize: Long = 0,

    @Column(length = 500)
    var note: String? = null,

    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column
    var score: Int? = null,

    @Column(columnDefinition = "TEXT")
    var feedback: String? = null,

    @Column(name = "graded_at")
    var gradedAt: Instant? = null,
)
