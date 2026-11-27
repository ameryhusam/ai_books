package com.example.data.repository

import com.example.data.local.EduMiniDao
import com.example.data.local.QuestionAttemptEntity
import com.example.data.local.QuestionEntity
import com.example.model.ConceptMasterySummary
import com.example.model.LearnerProfile
import com.example.model.LessonRef
import com.example.model.MasteryStatus
import com.example.model.QuestionAttemptRecord
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionMasteryStats
import com.example.model.QuestionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/**
 * Dedicated service managing the Concept-Tagged Question Bank repository and
 * per-user answer attempt history to track mastery levels per concept.
 */
class QuestionBankService(
    private val dao: EduMiniDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    fun observeQuestions(workspaceId: String): Flow<List<QuestionItem>> {
        if (workspaceId.isBlank()) return flowOf(emptyList())
        return dao.observeQuestionsForWorkspace(workspaceId).map { entities ->
            QuestionBankEngine.markDuplicates(entities.map { it.toModel() })
        }
    }

    fun observeUserAttempts(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ): Flow<List<QuestionAttemptRecord>> {
        if (workspaceId.isBlank()) return flowOf(emptyList())
        val safeUserId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
        return dao.observeQuestionAttemptsForWorkspaceAndUser(workspaceId, safeUserId).map { list ->
            list.map { it.toModel() }
        }
    }

    fun observeQuestionStatsForUser(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ): Flow<Map<String, QuestionMasteryStats>> {
        if (workspaceId.isBlank()) return flowOf(emptyMap())
        return combine(
            observeQuestions(workspaceId),
            observeUserAttempts(workspaceId, userId)
        ) { questions, attempts ->
            ConceptMasteryEngine.computeQuestionStats(questions, attempts)
        }
    }

    fun observeConceptMasteryForUser(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID,
        bookTitleByKey: Map<String, String> = emptyMap()
    ): Flow<List<ConceptMasterySummary>> {
        if (workspaceId.isBlank()) return flowOf(emptyList())
        return combine(
            observeQuestions(workspaceId),
            observeUserAttempts(workspaceId, userId)
        ) { questions, attempts ->
            ConceptMasteryEngine.computeConceptSummaries(questions, attempts, bookTitleByKey)
        }
    }

    suspend fun getStrugglingConceptsForUser(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID,
        bookTitleByKey: Map<String, String> = emptyMap()
    ): List<ConceptMasterySummary> {
        if (workspaceId.isBlank()) return emptyList()
        val questions = dao.observeQuestionsForWorkspace(workspaceId).first().map { it.toModel() }
        val attempts = observeUserAttempts(workspaceId, userId).first()
        return ConceptMasteryEngine.computeConceptSummaries(questions, attempts, bookTitleByKey)
            .filter { it.status == MasteryStatus.NEEDS_REINFORCEMENT }
    }

    suspend fun getAttemptHistoryForConceptAndUser(
        workspaceId: String,
        conceptTag: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID
    ): List<QuestionAttemptRecord> {
        if (workspaceId.isBlank() || conceptTag.isBlank()) return emptyList()
        val safeUserId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
        return dao.observeQuestionAttemptsForConceptAndUser(workspaceId, safeUserId, conceptTag.trim())
            .first()
            .map { it.toModel() }
    }

    /**
     * Infers a meaningful concept tag from explicit metadata or Arabic keyword/stem analysis,
     * falling back to the lesson title.
     */
    fun inferConceptTag(
        explicitTag: String?,
        prompt: String,
        rationale: String,
        lessonTitle: String
    ): String {
        val cleanExplicit = explicitTag?.trim().orEmpty()
        if (cleanExplicit.isNotBlank() && !cleanExplicit.equals(lessonTitle.trim(), ignoreCase = true)) {
            return cleanExplicit
        }
        val combined = "$prompt $rationale".trim()
        val conceptHints = listOf(
            listOf("الجمع", "جمع", "مجموع") to "مفهوم الجمع",
            listOf("الطرح", "طرح", "الفرق") to "مفهوم الطرح",
            listOf("الضرب", "ضرب", "جداء") to "مفهوم الضرب",
            listOf("القسمة", "قسمة", "مقسوم") to "مفهوم القسمة",
            listOf("الكسر", "الكسور", "بسط", "مقام") to "الكسور",
            listOf("المساحة", "المحيط", "الزاوية", "مثلث", "مربع") to "الهندسة والقياس",
            listOf("المبتدأ", "الخبر", "الفاعل", "المفعول", "الإعراب", "الجملة") to "القواعد النحوية",
            listOf("الفكرة الرئيسة", "الفكرة الرئيسية", "المعنى", "مرادف", "ضد") to "الفهم والمفردات",
            listOf("الخلية", "التمثيل الضوئي", "النبات", "الجهاز", "التنفس") to "علوم الأحياء",
            listOf("المادة", "الذرة", "التبخر", "التكثف", "التفاعل", "الحرارة") to "العلوم الفيزيائية والكيميائية",
            listOf("الخريطة", "القارة", "المناخ", "التضاريس") to "الجغرافيا",
            listOf("الصلاة", "الوضوء", "الزكاة", "القرآن", "الحديث") to "المفاهيم الإسلامية"
        )
        for ((keywords, label) in conceptHints) {
            if (keywords.any { combined.contains(it, ignoreCase = true) }) {
                return label
            }
        }
        return cleanExplicit.ifBlank { lessonTitle.trim().ifBlank { "مفهوم عام" } }
    }

    suspend fun tagQuestion(questionId: String, conceptTag: String) {
        val clean = conceptTag.trim()
        if (questionId.isBlank() || clean.isBlank()) return
        dao.updateQuestionConceptTag(questionId, clean)
    }

    suspend fun autoTagWorkspaceQuestions(workspaceId: String): Int {
        if (workspaceId.isBlank()) return 0
        val existing = dao.observeQuestionsForWorkspace(workspaceId).first()
        var updatedCount = 0
        existing.forEach { entity ->
            val current = entity.conceptTag.trim()
            if (current.isBlank() || current == entity.lessonTitle.trim()) {
                val inferred = inferConceptTag(
                    explicitTag = current,
                    prompt = entity.prompt,
                    rationale = entity.rationale,
                    lessonTitle = entity.lessonTitle
                )
                if (inferred.isNotBlank() && inferred != current) {
                    dao.updateQuestionConceptTag(entity.id, inferred)
                    updatedCount++
                }
            }
        }
        return updatedCount
    }

    suspend fun saveQuestionsWithConceptTags(
        workspaceId: String,
        lesson: LessonRef,
        parsedQuestions: List<QuestionItem>,
        draftId: String = "",
        templateId: String = "",
        promptUsed: String = "",
        responseSchemaJson: String = "",
        sourceIndexSha256: String = "",
        requireAllNew: Boolean = false
    ): List<QuestionItem> {
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        if (canonicalWs.isBlank() || parsedQuestions.isEmpty()) return emptyList()

        val existingFingerprints = dao.observeQuestionsForWorkspace(canonicalWs).first()
            .filter {
                it.bookKey == lesson.bookKey &&
                    it.unitKey == lesson.unitKey &&
                    it.lessonKey == lesson.lessonKey
            }
            .map { q -> q.fingerprint.ifBlank { QuestionBankEngine.computeFingerprint(q.prompt) } }
            .toMutableSet()

        val resolvedIndexSha = sourceIndexSha256.ifBlank {
            dao.getWorkspaceIndexForBook(canonicalWs, lesson.bookKey)?.indexSha256.orEmpty()
        }

        val newItems = parsedQuestions.mapNotNull { item ->
            val fp = item.fingerprint.ifBlank { QuestionBankEngine.computeFingerprint(item.prompt) }
            if (!existingFingerprints.add(fp)) return@mapNotNull null
            val tagged = inferConceptTag(
                explicitTag = item.conceptTag,
                prompt = item.prompt,
                rationale = item.rationale,
                lessonTitle = lesson.title
            )
            item.copy(
                workspaceId = canonicalWs,
                fingerprint = fp,
                conceptTag = tagged,
                draftId = item.draftId.ifBlank { draftId },
                templateId = item.templateId.ifBlank { templateId },
                promptUsed = item.promptUsed.ifBlank { promptUsed },
                responseSchemaJson = item.responseSchemaJson.ifBlank { responseSchemaJson },
                sourceIndexSha256 = item.sourceIndexSha256.ifBlank { resolvedIndexSha }
            )
        }
        if (requireAllNew && newItems.isEmpty()) {
            return emptyList()
        }
        if (requireAllNew && newItems.size != parsedQuestions.size) {
            val duplicateCount = parsedQuestions.size - newItems.size
            throw IllegalStateException(
                "لم تُحفظ الدفعة: يوجد $duplicateCount سؤال مكرر في بنك هذا الدرس. لم يتم إدراج أي سؤال من هذه الدفعة."
            )
        }
        if (newItems.isNotEmpty()) {
            dao.insertQuestions(newItems.map { it.toEntity() })
        }
        return newItems
    }

    suspend fun recordBatchAttemptsForUser(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID,
        questions: List<QuestionItem>,
        selectedAnswers: Map<String, String>,
        sourceMode: String
    ): List<QuestionAttemptRecord> {
        if (workspaceId.isBlank() || questions.isEmpty()) return emptyList()
        val safeUserId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID }
        val now = Instant.now().toString()
        val entities = questions.mapNotNull { q ->
            val picked = selectedAnswers[q.id]?.trim().orEmpty()
            if (picked.isBlank()) return@mapNotNull null
            QuestionAttemptEntity(
                id = "qa-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                workspaceId = workspaceId,
                userId = safeUserId,
                questionId = q.id,
                conceptTag = ConceptMasteryEngine.effectiveConceptTag(q),
                selectedAnswer = picked,
                isCorrect = QuestionBankEngine.isAnswerCorrect(q, picked),
                sourceMode = sourceMode,
                answeredAt = now
            )
        }
        if (entities.isNotEmpty()) {
            dao.insertQuestionAttempts(entities)
        }
        return entities.map { it.toModel() }
    }

    /**
     * Records a question result emitted from an interactive HTML/JS or JSON lesson checkpoint
     * for the active user, matching or creating the corresponding question row in the DB.
     */
    suspend fun recordInteractiveCheckpointResultForUser(
        workspaceId: String,
        userId: String = LearnerProfile.DEFAULT_USER_ID,
        lesson: LessonRef,
        prompt: String,
        conceptTag: String,
        selectedAnswer: String,
        correctAnswer: String,
        isCorrect: Boolean,
        options: List<String> = emptyList(),
        sourceMode: String = "interactive_html"
    ): QuestionAttemptRecord? {
        val cleanPrompt = prompt.trim()
        val cleanSelected = selectedAnswer.trim()
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        if (canonicalWs.isBlank() || cleanPrompt.isBlank() || cleanSelected.isBlank()) return null

        val fp = QuestionBankEngine.computeFingerprint(cleanPrompt)
        val existingQuestions = dao.observeQuestionsForWorkspace(canonicalWs).first()
        val matched = existingQuestions.firstOrNull { q ->
            q.bookKey == lesson.bookKey &&
                q.unitKey == lesson.unitKey &&
                q.lessonKey == lesson.lessonKey &&
                (q.fingerprint.ifBlank { QuestionBankEngine.computeFingerprint(q.prompt) }) == fp
        }

        val effectiveConcept = inferConceptTag(
            explicitTag = conceptTag.ifBlank { matched?.conceptTag },
            prompt = cleanPrompt,
            rationale = "",
            lessonTitle = lesson.title
        )

        val now = Instant.now().toString()
        val questionId = if (matched != null) {
            matched.id
        } else {
            val newId = "q-int-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
            val cleanOptions = options.map { it.trim() }.filter { it.isNotBlank() }
                .ifEmpty { listOfNotNull(correctAnswer.trim().takeIf { it.isNotBlank() }, cleanSelected).distinct() }
            val newEntity = QuestionEntity(
                id = newId,
                workspaceId = canonicalWs,
                gradeKey = lesson.grade,
                bookKey = lesson.bookKey,
                unitKey = lesson.unitKey,
                lessonKey = lesson.lessonKey,
                lessonTitle = lesson.title,
                type = if (cleanOptions.size > 1) QuestionType.MCQ.id else QuestionType.SHORT_ANSWER.id,
                prompt = cleanPrompt,
                optionsJson = json.encodeToString(cleanOptions),
                correctAnswer = correctAnswer.trim().ifBlank { if (isCorrect) cleanSelected else "" },
                rationale = "",
                difficulty = QuestionDifficulty.MEDIUM.id,
                sourcePages = lesson.pageRange,
                fingerprint = fp,
                reviewed = true,
                createdAt = now,
                conceptTag = effectiveConcept
            )
            dao.insertQuestions(listOf(newEntity))
            newId
        }

        val entity = QuestionAttemptEntity(
            id = "qa-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            workspaceId = canonicalWs,
            userId = userId.ifBlank { LearnerProfile.DEFAULT_USER_ID },
            questionId = questionId,
            conceptTag = effectiveConcept,
            selectedAnswer = cleanSelected,
            isCorrect = isCorrect,
            sourceMode = sourceMode,
            answeredAt = now
        )
        dao.insertQuestionAttempts(listOf(entity))
        return entity.toModel()
    }

    private fun QuestionEntity.toModel(): QuestionItem {
        val opts = runCatching { json.decodeFromString<List<String>>(optionsJson) }.getOrDefault(emptyList())
        return QuestionItem(
            id = id,
            workspaceId = workspaceId,
            gradeKey = gradeKey,
            bookKey = bookKey,
            unitKey = unitKey,
            lessonKey = lessonKey,
            lessonTitle = lessonTitle,
            type = QuestionType.fromId(type),
            prompt = prompt,
            options = opts,
            correctAnswer = correctAnswer,
            rationale = rationale,
            difficulty = QuestionDifficulty.fromId(difficulty),
            sourcePages = sourcePages,
            fingerprint = fingerprint,
            reviewed = reviewed,
            createdAt = createdAt,
            conceptTag = conceptTag.ifBlank { lessonTitle },
            draftId = draftId,
            templateId = templateId,
            promptUsed = promptUsed,
            responseSchemaJson = responseSchemaJson,
            sourceIndexSha256 = sourceIndexSha256
        )
    }

    private fun QuestionItem.toEntity() = QuestionEntity(
        id = id,
        workspaceId = workspaceId,
        gradeKey = gradeKey,
        bookKey = bookKey,
        unitKey = unitKey,
        lessonKey = lessonKey,
        lessonTitle = lessonTitle,
        type = type.id,
        prompt = prompt,
        optionsJson = json.encodeToString(options),
        correctAnswer = correctAnswer,
        rationale = rationale,
        difficulty = difficulty.id,
        sourcePages = sourcePages,
        fingerprint = fingerprint,
        reviewed = reviewed,
        createdAt = createdAt,
        conceptTag = conceptTag.ifBlank { lessonTitle },
        draftId = draftId,
        templateId = templateId,
        promptUsed = promptUsed,
        responseSchemaJson = responseSchemaJson,
        sourceIndexSha256 = sourceIndexSha256
    )

    private fun QuestionAttemptEntity.toModel() = QuestionAttemptRecord(
        id = id,
        workspaceId = workspaceId,
        userId = userId,
        questionId = questionId,
        conceptTag = conceptTag,
        selectedAnswer = selectedAnswer,
        isCorrect = isCorrect,
        sourceMode = sourceMode,
        answeredAt = answeredAt
    )
}
