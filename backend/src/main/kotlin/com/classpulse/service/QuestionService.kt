package com.classpulse.service

import com.classpulse.domain.Answer
import com.classpulse.domain.Question
import com.classpulse.domain.QuestionType
import com.classpulse.domain.Role
import com.classpulse.repository.AnswerRepository
import com.classpulse.repository.QuestionRepository
import com.classpulse.web.ApiException
import com.classpulse.web.dto.*
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale
import kotlin.math.round

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
    private val answerRepository: AnswerRepository,
    private val accessGuard: AccessGuard,
    private val objectMapper: ObjectMapper,
) {
    private val optionListType = object : TypeReference<List<QuestionOptionDto>>() {}

    @Transactional
    fun create(courseId: Long, request: CreateQuestionRequest): QuestionDto {
        val course = accessGuard.teacherCourse(courseId)
        accessGuard.ensureWritable(course)
        val type = request.type
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "题型不能为空")
        val deadline = request.deadlineAt
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "截止时间不能为空")

        var options = emptyList<QuestionOptionDto>()
        val standardAnswer = when (type) {
            QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE -> {
                val texts = request.options.orEmpty().map { it.trim() }
                if (texts.size !in 2..6 || texts.any { it.isBlank() }) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_OPTIONS", "选择题需要 2-6 个非空选项")
                }
                if (texts.map { it.lowercase(Locale.ROOT) }.distinct().size != texts.size) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_OPTIONS", "选项内容不能重复")
                }
                options = texts.mapIndexed { index, text ->
                    QuestionOptionDto(key = ('A'.code + index).toChar().toString(), text = text)
                }
                val answerKeys = choiceKeys(request.correctAnswer)
                if (type == QuestionType.SINGLE_CHOICE && answerKeys.size != 1) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "单选题需要且只能有一个正确答案")
                }
                if (type == QuestionType.MULTIPLE_CHOICE && answerKeys.size < 2) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "多选题至少需要两个正确答案")
                }
                if (answerKeys.distinct().size != answerKeys.size) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "参考答案不能重复")
                }
                if (answerKeys.any { key -> options.none { it.key == key } }) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "选择题参考答案应为有效选项字母")
                }
                options.filter { it.key in answerKeys }.joinToString(",") { it.key }
            }

            QuestionType.FILL_BLANK -> request.correctAnswer.trim()
        }
        val question = questionRepository.save(
            Question(
                course = course,
                type = type,
                prompt = request.prompt.trim(),
                optionsJson = if (options.isEmpty()) null else objectMapper.writeValueAsString(options),
                standardAnswer = standardAnswer,
                deadlineAt = deadline,
                createdAt = Instant.now(),
            ),
        )
        return question.toDto()
    }

    @Transactional(readOnly = true)
    fun list(courseId: Long): List<QuestionDto> {
        accessGuard.readableCourse(courseId)
        return questionRepository.findByCourseIdAndDeletedAtIsNullOrderByCreatedAtDesc(courseId).map { it.toDto() }
    }

    @Transactional
    fun submit(questionId: Long, request: AnswerRequest): AnswerResultDto {
        val principal = accessGuard.requireStudent()
        val question = findQuestion(questionId)
        val courseId = requireNotNull(question.course.id)
        accessGuard.ensureStudentMember(courseId, principal.id)
        accessGuard.ensureWritable(question.course)
        val now = Instant.now()
        if (!now.isBefore(question.deadlineAt)) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QUESTION_CLOSED", "本题已截止，无法作答")
        }
        if (answerRepository.findByQuestionIdAndStudentId(questionId, principal.id) != null) {
            throw ApiException(HttpStatus.CONFLICT, "ALREADY_ANSWERED", "本题已提交，不能重复作答")
        }
        val normalized = when (question.type) {
            QuestionType.SINGLE_CHOICE -> request.answer.trim().uppercase(Locale.ROOT)
            QuestionType.MULTIPLE_CHOICE -> {
                val selectedKeys = choiceKeys(request.answer)
                if (selectedKeys.isEmpty()) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "EMPTY_ANSWER", "答案不能为空")
                }
                if (selectedKeys.distinct().size != selectedKeys.size) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "不能重复选择同一选项")
                }
                val optionKeys = options(question).map { it.key }
                if (selectedKeys.any { it !in optionKeys }) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "请选择有效选项")
                }
                optionKeys.filter { it in selectedKeys }.joinToString(",")
            }
            QuestionType.FILL_BLANK -> request.answer.trim()
        }
        if (normalized.isBlank()) {
            throw ApiException(HttpStatus.BAD_REQUEST, "EMPTY_ANSWER", "答案不能为空")
        }
        if (question.type == QuestionType.SINGLE_CHOICE) {
            val keys = options(question).map { it.key }
            if (normalized !in keys) {
                throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "请选择有效选项")
            }
        }
        val correct = when (question.type) {
            QuestionType.FILL_BLANK -> normalized.equals(question.standardAnswer.trim(), ignoreCase = true)
            else -> normalized == question.standardAnswer
        }
        val answer = answerRepository.save(
            Answer(
                question = question,
                student = accessGuard.currentUser(),
                answerText = normalized,
                isCorrect = correct,
                submittedAt = now,
            ),
        )
        return AnswerResultDto(
            questionId = questionId,
            answer = answer.answerText,
            isCorrect = answer.isCorrect,
            correctAnswer = question.standardAnswer,
            submittedAt = answer.submittedAt,
        )
    }

    @Transactional
    fun update(questionId: Long, request: UpdateQuestionRequest): QuestionDto {
        val question = findQuestion(questionId)
        val course = accessGuard.teacherCourse(requireNotNull(question.course.id))
        accessGuard.ensureWritable(course)
        val deadline = request.deadlineAt
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "截止时间不能为空")
        val hasAnswers = answerRepository.countByQuestionId(questionId) > 0
        val currentOptions = options(question)
        val newOptions: List<QuestionOptionDto>
        val newAnswer: String
        when (question.type) {
            QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE -> {
                val texts = request.options.orEmpty().map { it.trim() }
                if (texts.size !in 2..6 || texts.any { it.isBlank() }) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_OPTIONS", "选择题需要 2-6 个非空选项")
                }
                if (texts.map { it.lowercase(Locale.ROOT) }.distinct().size != texts.size) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_OPTIONS", "选项内容不能重复")
                }
                newOptions = texts.mapIndexed { index, text ->
                    QuestionOptionDto(key = ('A'.code + index).toChar().toString(), text = text)
                }
                val answerKeys = choiceKeys(request.correctAnswer)
                if (question.type == QuestionType.SINGLE_CHOICE && answerKeys.size != 1) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "单选题需要且只能有一个正确答案")
                }
                if (question.type == QuestionType.MULTIPLE_CHOICE && answerKeys.size < 2) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "多选题至少需要两个正确答案")
                }
                if (answerKeys.distinct().size != answerKeys.size) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "参考答案不能重复")
                }
                if (answerKeys.any { key -> newOptions.none { it.key == key } }) {
                    throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "选择题参考答案应为有效选项字母")
                }
                newAnswer = newOptions.filter { it.key in answerKeys }.joinToString(",") { it.key }
            }

            QuestionType.FILL_BLANK -> {
                newOptions = emptyList()
                newAnswer = request.correctAnswer.trim()
            }
        }
        if (hasAnswers && (newOptions != currentOptions || newAnswer != question.standardAnswer)) {
            throw ApiException(HttpStatus.CONFLICT, "QUESTION_HAS_ANSWERS", "已有学生作答，只能修改题干和截止时间")
        }
        question.prompt = request.prompt.trim()
        question.deadlineAt = deadline
        if (!hasAnswers) {
            question.optionsJson = if (newOptions.isEmpty()) null else objectMapper.writeValueAsString(newOptions)
            question.standardAnswer = newAnswer
        }
        questionRepository.save(question)
        return question.toDto()
    }

    @Transactional
    fun close(questionId: Long): QuestionDto {
        val question = findQuestion(questionId)
        val course = accessGuard.teacherCourse(requireNotNull(question.course.id))
        accessGuard.ensureWritable(course)
        question.deadlineAt = Instant.now()
        questionRepository.save(question)
        return question.toDto()
    }

    @Transactional
    fun delete(questionId: Long) {
        val question = findQuestion(questionId)
        accessGuard.teacherCourse(requireNotNull(question.course.id))
        question.deletedAt = Instant.now()
        questionRepository.save(question)
    }

    @Transactional(readOnly = true)
    fun stats(questionId: Long): QuestionStatsDto {
        accessGuard.requireTeacher()
        val question = findQuestion(questionId)
        accessGuard.teacherCourse(requireNotNull(question.course.id))
        val answers = answerRepository.findByQuestionIdOrderBySubmittedAtAsc(questionId)
        val details = answers.map { answer ->
            AnswerDetailDto(
                studentId = requireNotNull(answer.student.id),
                studentNo = answer.student.username,
                studentName = answer.student.name,
                answer = answer.answerText,
                isCorrect = answer.isCorrect,
                submittedAt = answer.submittedAt,
            )
        }
        val distribution = if (question.type != QuestionType.FILL_BLANK) {
            options(question).map { option ->
                OptionDistributionDto(
                    key = option.key,
                    text = option.text,
                    count = answers.count { option.key in it.answerText.split(',') }.toLong(),
                )
            }
        } else {
            emptyList()
        }
        val correctCount = answers.count { it.isCorrect }
        val rate = if (answers.isEmpty()) 0.0 else correctCount.toDouble() / answers.size
        val conclusion = if (answers.isEmpty()) {
            "本题暂无学生提交。"
        } else {
            "本题共 ${answers.size} 人提交，$correctCount 人正确，正确率 ${percent(rate)}（按实际作答人数计算）。"
        }
        return QuestionStatsDto(
            question = question.toDto(),
            answerDetails = details,
            optionDistribution = distribution,
            conclusion = conclusion,
        )
    }

    private fun findQuestion(id: Long): Question {
        val question = questionRepository.findById(id).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "题目不存在")
        }
        if (question.deletedAt != null) {
            throw ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "题目不存在")
        }
        return question
    }

    private fun choiceKeys(raw: String): List<String> =
        raw.split(',')
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }

    private fun options(question: Question): List<QuestionOptionDto> {
        val json = question.optionsJson ?: return emptyList()
        return objectMapper.readValue(json, optionListType)
    }

    private fun Question.toDto(): QuestionDto {
        val principal = accessGuard.principal()
        val id = requireNotNull(id)
        val courseId = requireNotNull(course.id)
        val parsedOptions = options(this)
        return if (principal.role == Role.TEACHER) {
            val submissions = answerRepository.countByQuestionId(id)
            val correct = answerRepository.countByQuestionIdAndIsCorrectTrue(id)
            QuestionDto(
                id = id,
                courseId = courseId,
                type = type,
                prompt = prompt,
                options = parsedOptions,
                deadlineAt = deadlineAt,
                createdAt = createdAt,
                submissionCount = submissions,
                correctCount = correct,
                correctRate = if (submissions == 0L) 0.0 else correct.toDouble() / submissions,
                answered = false,
                myAnswer = null,
                myIsCorrect = null,
                correctAnswer = standardAnswer,
            )
        } else {
            val answer = answerRepository.findByQuestionIdAndStudentId(id, principal.id)
            QuestionDto(
                id = id,
                courseId = courseId,
                type = type,
                prompt = prompt,
                options = parsedOptions,
                deadlineAt = deadlineAt,
                createdAt = createdAt,
                submissionCount = null,
                correctCount = null,
                correctRate = null,
                answered = answer != null,
                myAnswer = answer?.answerText,
                myIsCorrect = answer?.isCorrect,
                correctAnswer = if (answer != null) standardAnswer else null,
            )
        }
    }

    private fun percent(rate: Double): String =
        String.format(Locale.CHINA, "%.1f%%", round(rate * 1000) / 10)
}
