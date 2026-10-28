package com.classpulse.service

import com.classpulse.config.AppProperties
import com.classpulse.domain.Assignment
import com.classpulse.domain.Role
import com.classpulse.domain.Submission
import com.classpulse.repository.AssignmentRepository
import com.classpulse.repository.CourseMemberRepository
import com.classpulse.repository.SubmissionRepository
import com.classpulse.web.ApiException
import com.classpulse.web.dto.*
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

data class StoredImage(
    val resource: Resource,
    val contentType: String,
    val filename: String,
)

@Service
class AssignmentService(
    private val assignmentRepository: AssignmentRepository,
    private val submissionRepository: SubmissionRepository,
    private val memberRepository: CourseMemberRepository,
    private val accessGuard: AccessGuard,
    private val storageService: StorageService,
    private val properties: AppProperties,
) {
    @Transactional
    fun create(courseId: Long, request: CreateAssignmentRequest): AssignmentDto {
        val course = accessGuard.teacherCourse(courseId)
        val assignment = assignmentRepository.save(
            Assignment(
                course = course,
                title = request.title.trim(),
                description = request.description.trim(),
                deadlineAt = requireNotNull(request.deadlineAt),
                createdAt = Instant.now(),
            ),
        )
        return assignment.toDto()
    }

    @Transactional(readOnly = true)
    fun list(courseId: Long): List<AssignmentDto> {
        accessGuard.readableCourse(courseId)
        return assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId).map { it.toDto() }
    }

    @Transactional
    fun submit(assignmentId: Long, file: MultipartFile, note: String?): SubmissionDto {
        val principal = accessGuard.requireStudent()
        val assignment = findAssignment(assignmentId)
        val courseId = requireNotNull(assignment.course.id)
        accessGuard.ensureStudentMember(courseId, principal.id)
        if (!Instant.now().isBefore(assignment.deadlineAt)) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ASSIGNMENT_CLOSED", "作业已截止，无法提交")
        }
        if (file.isEmpty) {
            throw ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "请选择要上传的图片")
        }
        if (file.size > properties.upload.maxBytes) {
            throw ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "图片不能超过 10 MB")
        }
        if ((note?.length ?: 0) > 500) {
            throw ApiException(HttpStatus.BAD_REQUEST, "NOTE_TOO_LONG", "提交说明不能超过 500 字")
        }
        val bytes = file.bytes
        if (bytes.size > properties.upload.maxBytes) {
            throw ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "图片不能超过 10 MB")
        }
        val detected = detectImage(bytes)
            ?: throw ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "仅支持 JPEG 或 PNG 图片")
        val cleanFilename = sanitizeFilename(file.originalFilename, detected.extension)

        // Serialize same-student replacements: the row lock is held until the
        // surrounding transaction commits or rolls back. This prevents two
        // requests from reading the same old path and creating overwritten rows.
        val student = accessGuard.lockCurrentUser()
        val existing = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, principal.id)
        val previousPath = existing?.imagePath
        val newPath = storageService.store(assignmentId, bytes, detected.extension)
        val now = Instant.now()
        var synchronizationRegistered = false
        return try {
            val submission = if (existing == null) {
                Submission(
                    assignment = assignment,
                    student = student,
                    imagePath = newPath,
                    originalFilename = cleanFilename,
                    contentType = detected.contentType,
                    fileSize = bytes.size.toLong(),
                    note = note?.trim()?.ifBlank { null },
                    submittedAt = now,
                    updatedAt = now,
                )
            } else {
                existing.apply {
                    imagePath = newPath
                    originalFilename = cleanFilename
                    contentType = detected.contentType
                    fileSize = bytes.size.toLong()
                    this.note = note?.trim()?.ifBlank { null }
                    updatedAt = now
                }
            }
            val saved = submissionRepository.save(submission)

            // Files are not transactional. Delete the previous file only after
            // the database commit; if the commit/rollback fails, remove the
            // newly written file instead, so the row never points at a missing
            // image and the old row keeps a valid file.
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        if (previousPath != null && previousPath != newPath) {
                            storageService.delete(previousPath)
                        }
                    }

                    override fun afterCompletion(status: Int) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            storageService.delete(newPath)
                        }
                    }
                },
            )
            synchronizationRegistered = true
            saved.toDto(includeStudent = true)
        } catch (ex: Exception) {
            if (!synchronizationRegistered) {
                storageService.delete(newPath)
            }
            throw ex
        }
    }

    @Transactional(readOnly = true)
    fun submissions(assignmentId: Long): AssignmentSubmissionsDto {
        accessGuard.requireTeacher()
        val assignment = findAssignment(assignmentId)
        val courseId = requireNotNull(assignment.course.id)
        accessGuard.teacherCourse(courseId)
        val members = memberRepository.findByCourseIdOrderByJoinedAtAsc(courseId)
        val submissions = submissionRepository.findByAssignmentIdOrderByUpdatedAtAsc(assignmentId)
        val byStudent = submissions.associateBy { requireNotNull(it.student.id) }
        val rows = members.map { member ->
            val submission = byStudent[member.student.id]
            StudentSubmissionDto(
                studentId = requireNotNull(member.student.id),
                studentNo = member.student.username,
                studentName = member.student.name,
                submitted = submission != null,
                submission = submission?.toDto(includeStudent = true),
            )
        }
        return AssignmentSubmissionsDto(
            assignment = assignment.toDto(),
            rows = rows,
            submittedCount = rows.count { it.submitted }.toLong(),
            missingCount = rows.count { !it.submitted }.toLong(),
        )
    }

    @Transactional(readOnly = true)
    fun image(submissionId: Long): StoredImage {
        val principal = accessGuard.principal()
        val submission = submissionRepository.findById(submissionId).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "作业提交不存在")
        }
        val assignment = submission.assignment
        val course = assignment.course
        val isOwner = submission.student.id == principal.id
        val isTeacher = principal.role == Role.TEACHER && course.teacher.id == principal.id
        if (!isOwner && !isTeacher) {
            throw ApiException(HttpStatus.FORBIDDEN, "IMAGE_FORBIDDEN", "无权查看该图片")
        }
        return StoredImage(
            resource = storageService.load(submission.imagePath),
            contentType = submission.contentType,
            filename = submission.originalFilename,
        )
    }

    private fun findAssignment(assignmentId: Long): Assignment =
        assignmentRepository.findById(assignmentId).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "作业不存在")
        }

    private fun Assignment.toDto(): AssignmentDto {
        val principal = accessGuard.principal()
        val id = requireNotNull(id)
        val courseId = requireNotNull(course.id)
        val memberCount = memberRepository.countByCourseId(courseId)
        val mySubmission = if (principal.role == Role.STUDENT) {
            submissionRepository.findByAssignmentIdAndStudentId(id, principal.id)
        } else {
            null
        }
        return AssignmentDto(
            id = id,
            courseId = courseId,
            title = title,
            description = description,
            deadlineAt = deadlineAt,
            createdAt = createdAt,
            submissionCount = submissionRepository.countByAssignmentId(id),
            memberCount = memberCount,
            active = Instant.now().isBefore(deadlineAt),
            mySubmission = mySubmission?.toDto(includeStudent = true),
        )
    }

    private fun Submission.toDto(includeStudent: Boolean): SubmissionDto {
        val id = requireNotNull(id)
        return SubmissionDto(
            id = id,
            assignmentId = requireNotNull(assignment.id),
            studentId = requireNotNull(student.id),
            studentName = if (includeStudent) student.name else null,
            imageUrl = "/api/v1/submissions/$id/image",
            originalFilename = originalFilename,
            contentType = contentType,
            fileSize = fileSize,
            note = note,
            submittedAt = submittedAt,
            updatedAt = updatedAt,
        )
    }

    private fun detectImage(bytes: ByteArray): DetectedImage? {
        val jpeg = bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        if (jpeg) return DetectedImage("image/jpeg", "jpg")
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val png = bytes.size >= 8 && pngSignature.indices.all { bytes[it] == pngSignature[it] }
        if (png) return DetectedImage("image/png", "png")
        return null
    }

    private fun sanitizeFilename(original: String?, extension: String): String {
        val clean = original
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.replace(Regex("[\\u0000-\\u001F]"), "")
            ?.trim()
            ?.take(255)
            ?.ifBlank { null }
        return clean ?: "assignment.$extension"
    }

    private data class DetectedImage(val contentType: String, val extension: String)
}
