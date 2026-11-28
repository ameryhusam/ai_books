package com.example.data.repository

import com.example.model.AssessmentRecord
import com.example.model.LessonRef
import com.example.model.QuestionContentPayload
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.model.QuestionSourceType
import com.example.data.ai.QuestionResponseContract
import com.example.data.ai.TemplateOutputValidator
import com.example.ui.components.LearningProgressCalculator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class QuizResultSummary(
    val totalQuestions: Int,
    val correctCount: Int,
    val scorePercent: Int,
    val earnedXp: Int
)

object QuestionBankEngine {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Conservative Arabic normalization per PROJECT_FOUNDATION_AND_ANDROID_PLAN §12:
     * preserves hamza and letter distinctions while stripping punctuation & normalizing whitespace.
     */
    fun normalizeForFingerprint(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace(Regex("[\\p{Punct}؟،؛«»]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun computeFingerprint(prompt: String): String {
        val norm = normalizeForFingerprint(prompt)
        if (norm.isBlank()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun markDuplicates(items: List<QuestionItem>): List<QuestionItem> {
        val counts = items
            .mapNotNull { it.fingerprint.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
        return items.map { item ->
            val dup = item.fingerprint.isNotBlank() && (counts[item.fingerprint] ?: 0) > 1
            item.copy(isDuplicate = dup)
        }
    }

    fun formatGeneratedQuestionsForPreview(rawText: String): String {
        val cleaned = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val root = runCatching { json.parseToJsonElement(cleaned) as? JsonObject }.getOrNull() ?: return rawText
        val array = root["questions"] as? JsonArray ?: return rawText
        val payloads = array.map { element ->
            runCatching { json.decodeFromJsonElement<QuestionContentPayload>(element) }.getOrNull()
                ?: return rawText
        }
        if (payloads.isEmpty()) return "لم يتم إنشاء أسئلة."
        return buildString {
            payloads.forEachIndexed { index, question ->
                append("### السؤال ").append(index + 1).append("\n\n")
                append(question.prompt).append("\n\n")
                if (question.conceptTag.isNotBlank()) append("**المفهوم:** ").append(question.conceptTag).append("\n\n")
                if (question.options.isNotEmpty()) {
                    question.options.forEachIndexed { optionIndex, option ->
                        append("- ").append(('أ' + optionIndex)).append(") ").append(option).append("\n")
                    }
                    append("\n")
                }
                append("**الإجابة:** ").append(question.correctAnswer).append("\n\n")
                if (question.rationale.isNotBlank()) append("**التفسير:** ").append(question.rationale).append("\n\n")
                append("**النوع:** ").append(question.type).append(" • **الصعوبة:** ").append(question.difficulty)
                if (question.sourcePages.isNotBlank()) append(" • **الصفحة:** ").append(question.sourcePages)
                append("\n\n---\n\n")
            }
        }.trim()
    }

    fun parseQuestionsFromText(
        rawText: String,
        workspaceId: String,
        lesson: LessonRef
    ): List<QuestionItem> {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return emptyList()

        val jsonParsed = tryParseJsonQuestions(trimmed, workspaceId, lesson)
        if (jsonParsed.isNotEmpty()) return jsonParsed

        return parseStructuredTextQuestions(trimmed, workspaceId, lesson)
    }

    /**
     * Strict parser for AI-generated question JSON. Unlike the legacy/manual parser,
     * this method never guesses unknown enum values, silently drops malformed rows,
     * or falls back to free-form text. It validates the exact requested count and type mix.
     */
    fun parseGeneratedQuestions(
        rawText: String,
        workspaceId: String,
        lesson: LessonRef,
        expectedCount: Int,
        selectedTypeIds: List<String>,
        expectedDifficultyId: String? = null
    ): List<QuestionItem> {
        val cleaned = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val root = runCatching { json.parseToJsonElement(cleaned) }.getOrElse {
            throw IllegalArgumentException("استجابة الأسئلة ليست JSON صالحاً: ${it.message ?: "خطأ تحليل"}")
        } as? JsonObject ?: throw IllegalArgumentException("يجب أن يكون جذر استجابة الأسئلة كائن JSON.")
        val schemaIssues = TemplateOutputValidator.validateOutput(cleaned, QuestionResponseContract.schemaFor(selectedTypeIds, expectedDifficultyId))
        require(schemaIssues.isEmpty()) {
            "استجابة الأسئلة لا تطابق عقد بنك الأسئلة: ${schemaIssues.take(5).joinToString("; ")}"
        }
        val unexpectedRootKeys = root.keys - setOf("questions")
        require(unexpectedRootKeys.isEmpty()) {
            "حقول غير معروفة في جذر استجابة الأسئلة: ${unexpectedRootKeys.joinToString()}."
        }
        val array = root["questions"] as? JsonArray
            ?: throw IllegalArgumentException("مصفوفة questions غير موجودة في استجابة النموذج.")
        require(array.size == expectedCount) {
            "العدد الناتج غير مطابق: المطلوب $expectedCount سؤالاً، والناتج ${array.size}."
        }

        val payloads = array.mapIndexed { index, element ->
            val obj = element as? JsonObject
                ?: throw IllegalArgumentException("السؤال رقم ${index + 1} ليس كائن JSON.")
            val payloadDescriptor = QuestionContentPayload.serializer().descriptor
            val allowedFields = (0 until payloadDescriptor.elementsCount)
                .map { payloadDescriptor.getElementName(it) }.toSet()
            val unexpectedFields = obj.keys - allowedFields
            require(unexpectedFields.isEmpty()) {
                "حقول غير معروفة في السؤال رقم ${index + 1}: ${unexpectedFields.joinToString()}."
            }
            val payload = runCatching { json.decodeFromJsonElement<QuestionContentPayload>(obj) }.getOrElse {
                throw IllegalArgumentException("تعذر تحويل السؤال رقم ${index + 1} إلى عقد بيانات بنك الأسئلة: ${it.message ?: "خطأ في الحقول"}.")
            }
            val type = QuestionType.entries.firstOrNull { it.id == payload.type }
                ?: throw IllegalArgumentException("نوع السؤال رقم ${index + 1} غير مدعوم: '${payload.type}'.")
            val difficulty = QuestionDifficulty.entries.firstOrNull { it.id == payload.difficulty }
                ?: throw IllegalArgumentException("مستوى صعوبة السؤال رقم ${index + 1} غير صالح: '${payload.difficulty}'.")
            if (!expectedDifficultyId.isNullOrBlank() && difficulty.id != expectedDifficultyId) {
                throw IllegalArgumentException(
                    "مستوى صعوبة السؤال رقم ${index + 1} هو '${difficulty.id}' بينما المستوى المطلوب '$expectedDifficultyId'."
                )
            }
            require(payload.prompt.isNotBlank()) { "نص السؤال رقم ${index + 1} فارغ." }
            require(payload.correctAnswer.isNotBlank()) { "الإجابة الصحيحة للسؤال رقم ${index + 1} فارغة." }
            val options = payload.options.mapIndexed { optionIndex, option ->
                option.trim().takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("الخيار ${optionIndex + 1} في السؤال رقم ${index + 1} فارغ.")
            }
            when (type) {
                QuestionType.MCQ, QuestionType.TRUE_FALSE -> {
                    require(options.size >= 2) {
                        "السؤال رقم ${index + 1} من نوع ${type.labelAr} يحتاج خيارين على الأقل."
                    }
                    require(options.any { it.equals(payload.correctAnswer.trim(), ignoreCase = true) }) {
                        "إجابة السؤال رقم ${index + 1} لا تطابق أياً من خياراته حرفياً."
                    }
                }
                QuestionType.ORDERING, QuestionType.MATCHING -> {
                    require(options.size >= 2) {
                        "السؤال رقم ${index + 1} من نوع ${type.labelAr} يحتاج عنصرين على الأقل."
                    }
                }
                QuestionType.FILL_BLANK, QuestionType.SHORT_ANSWER -> Unit
            }
            payload.copy(
                prompt = payload.prompt.trim(),
                options = options,
                correctAnswer = payload.correctAnswer.trim(),
                conceptTag = payload.conceptTag.trim(),
                rationale = payload.rationale.trim(),
                sourcePages = payload.sourcePages.trim()
            )
        }

        val createdAt = Instant.now().toString()
        val parsed = payloads.map { payload ->
            QuestionItem(
                id = "q-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                workspaceId = workspaceId,
                gradeKey = lesson.grade,
                bookKey = lesson.bookKey,
                unitKey = lesson.unitKey,
                lessonKey = lesson.lessonKey,
                lessonTitle = lesson.title,
                type = QuestionType.fromId(payload.type),
                prompt = payload.prompt,
                options = payload.options,
                correctAnswer = payload.correctAnswer,
                rationale = payload.rationale,
                difficulty = QuestionDifficulty.fromId(payload.difficulty),
                sourcePages = payload.sourcePages.ifBlank { lesson.pageRange.orEmpty() },
                fingerprint = computeFingerprint(payload.prompt),
                reviewed = false,
                createdAt = createdAt,
                conceptTag = payload.conceptTag.ifBlank { lesson.title },
                sourceType = QuestionSourceType.AI_GENERATED
            )
        }
        val expectedDistribution = QuestionResponseContract.expectedDistribution(expectedCount, selectedTypeIds)
        val actualDistribution = parsed.groupingBy { it.type.id }.eachCount()
        require(actualDistribution == expectedDistribution) {
            "توزيع أنواع الأسئلة غير مطابق. المطلوب: $expectedDistribution، والناتج: $actualDistribution."
        }
        val duplicateCount = parsed.map { it.fingerprint }.distinct().size
        require(duplicateCount == parsed.size) {
            "تتضمن الاستجابة أسئلة مكررة؛ أعد التوليد قبل الحفظ."
        }
        return parsed
    }

    private fun tryParseJsonQuestions(
        rawText: String,
        workspaceId: String,
        lesson: LessonRef
    ): List<QuestionItem> {
        val cleaned = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val arrayStart = cleaned.indexOf('[')
        val objStart = cleaned.indexOf('{')
        val jsonSlice = when {
            objStart >= 0 && (arrayStart < 0 || objStart < arrayStart) -> {
                val end = cleaned.lastIndexOf('}')
                if (end > objStart) cleaned.substring(objStart, end + 1) else null
            }
            arrayStart >= 0 -> {
                val end = cleaned.lastIndexOf(']')
                if (end > arrayStart) cleaned.substring(arrayStart, end + 1) else null
            }
            else -> null
        } ?: return emptyList()

        val root = runCatching { json.parseToJsonElement(jsonSlice) }.getOrNull() ?: return emptyList()
        val arr = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["questions"] as? JsonArray) ?: (root["items"] as? JsonArray)
            else -> null
        } ?: return emptyList()

        val now = Instant.now().toString()
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }

        return arr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val prompt = firstStr(obj, "prompt", "question", "stem", "text")
            if (prompt.isBlank()) return@mapNotNull null

            val opts = ((obj["options"] as? JsonArray) ?: (obj["choices"] as? JsonArray))
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()

            val answerIdx = (obj["correctIndex"] as? JsonPrimitive)?.intOrNull
                ?: (obj["answerIndex"] as? JsonPrimitive)?.intOrNull
            val rawAns = firstStr(obj, "correctAnswer", "answer", "correct")
            val typeStr = firstStr(obj, "type", "questionType")
            val type = when {
                typeStr.isNotBlank() -> QuestionType.fromId(typeStr)
                opts.size == 2 && opts.any { it == "صح" || it.equals("True", ignoreCase = true) } -> QuestionType.TRUE_FALSE
                opts.isEmpty() -> QuestionType.SHORT_ANSWER
                else -> QuestionType.MCQ
            }
            val finalOpts = if (type == QuestionType.TRUE_FALSE && opts.isEmpty()) {
                listOf("صح", "خطأ")
            } else {
                opts
            }
            val resolvedAnswer = when {
                rawAns.isNotBlank() -> resolveAnswerLetterToOption(rawAns, finalOpts)
                answerIdx != null && answerIdx in finalOpts.indices -> finalOpts[answerIdx]
                else -> ""
            }.trim()
            val normalizedAnswer = when {
                type == QuestionType.TRUE_FALSE && resolvedAnswer.equals("true", ignoreCase = true) -> "صح"
                type == QuestionType.TRUE_FALSE && resolvedAnswer.equals("false", ignoreCase = true) -> "خطأ"
                else -> resolvedAnswer
            }
            val answerIsValid = when (type) {
                QuestionType.MCQ, QuestionType.TRUE_FALSE -> normalizedAnswer.isNotBlank() &&
                    finalOpts.any { it.equals(normalizedAnswer, ignoreCase = true) }
                else -> normalizedAnswer.isNotBlank()
            }
            // Never manufacture a correct answer. Invalid AI items are excluded from
            // persistence and must be regenerated or corrected by the author.
            if (!answerIsValid) return@mapNotNull null

            val conceptTag = firstStr(obj, "conceptTag", "concept", "topic", "skill")
                .ifBlank { lesson.title }

            QuestionItem(
                id = "q-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                workspaceId = canonicalWs,
                gradeKey = lesson.grade,
                bookKey = lesson.bookKey,
                unitKey = lesson.unitKey,
                lessonKey = lesson.lessonKey,
                lessonTitle = lesson.title,
                type = type,
                prompt = prompt,
                options = finalOpts,
                correctAnswer = normalizedAnswer,
                rationale = firstStr(obj, "rationale", "explanation", "feedback"),
                difficulty = QuestionDifficulty.fromId(firstStr(obj, "difficulty", "level")),
                sourcePages = firstStr(obj, "sourcePages", "sourcePage", "page").ifBlank { lesson.pageRange },
                fingerprint = computeFingerprint(prompt),
                reviewed = false,
                createdAt = now,
                conceptTag = conceptTag,
                sourceType = QuestionSourceType.AI_GENERATED
            )
        }
    }

    private fun parseStructuredTextQuestions(
        rawText: String,
        workspaceId: String,
        lesson: LessonRef
    ): List<QuestionItem> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val blocks = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null

        val questionHeaderRegex = Regex("^(?:\\*\\*)?(?:س\\s*\\d+|Q\\s*\\d+|\\d+\\s*[\\).:\\-])\\s*(.+)$")
        for (line in lines) {
            if (questionHeaderRegex.matches(line)) {
                current = mutableListOf(line)
                blocks.add(current)
            } else if (current != null) {
                current.add(line)
            }
        }
        if (blocks.isEmpty()) return emptyList()

        val now = Instant.now().toString()
        val canonicalWs = workspaceId.ifBlank { lesson.workspaceId }.ifBlank { lesson.rootPath }
        val optionRegex = Regex("^(?:[أابجدهـ]|[A-Da-d])\\s*[\\).\\-:]\\s*(.+)$|^[-•*]\\s+(.+)$")

        return blocks.mapNotNull { block ->
            val firstLine = block.first()
            val prompt = questionHeaderRegex.find(firstLine)?.groupValues?.getOrNull(1)
                ?.replace("**", "")
                ?.trim()
                .orEmpty()
            if (prompt.isBlank()) return@mapNotNull null

            val options = mutableListOf<String>()
            var correctAnswer = ""
            var rationale = ""
            var difficulty = QuestionDifficulty.MEDIUM

            for (line in block.drop(1)) {
                val cleanLine = line.replace("**", "").trim()
                when {
                    cleanLine.startsWith("الإجابة") || cleanLine.startsWith("الجواب") || cleanLine.startsWith("Answer", ignoreCase = true) -> {
                        correctAnswer = cleanLine.substringAfter(":").substringAfter("：").trim()
                    }
                    cleanLine.startsWith("التفسير") || cleanLine.startsWith("الشرح") || cleanLine.startsWith("Explanation", ignoreCase = true) -> {
                        rationale = cleanLine.substringAfter(":").substringAfter("：").trim()
                    }
                    cleanLine.startsWith("الصعوبة") || cleanLine.startsWith("المستوى") -> {
                        val diffRaw = cleanLine.substringAfter(":").trim()
                        difficulty = when {
                            diffRaw.contains("سهل") || diffRaw.contains("easy", true) -> QuestionDifficulty.EASY
                            diffRaw.contains("متقدم") || diffRaw.contains("صعب") || diffRaw.contains("hard", true) -> QuestionDifficulty.HARD
                            else -> QuestionDifficulty.MEDIUM
                        }
                    }
                    else -> {
                        val optMatch = optionRegex.find(cleanLine)
                        if (optMatch != null) {
                            val optText = optMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                            if (optText.isNotBlank()) options.add(optText)
                        }
                    }
                }
            }

            val type = when {
                options.size == 2 && options.any { it == "صح" || it == "خطأ" } -> QuestionType.TRUE_FALSE
                options.isNotEmpty() -> QuestionType.MCQ
                correctAnswer == "صح" || correctAnswer == "خطأ" -> QuestionType.TRUE_FALSE
                else -> QuestionType.SHORT_ANSWER
            }
            val finalOpts = if (type == QuestionType.TRUE_FALSE && options.isEmpty()) {
                listOf("صح", "خطأ")
            } else {
                options
            }
            val finalAnswer = resolveAnswerLetterToOption(correctAnswer, finalOpts).trim()
            val answerIsValid = when (type) {
                QuestionType.MCQ, QuestionType.TRUE_FALSE -> finalAnswer.isNotBlank() &&
                    finalOpts.any { it.equals(finalAnswer, ignoreCase = true) }
                else -> finalAnswer.isNotBlank()
            }
            if (!answerIsValid) return@mapNotNull null

            QuestionItem(
                id = "q-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                workspaceId = canonicalWs,
                gradeKey = lesson.grade,
                bookKey = lesson.bookKey,
                unitKey = lesson.unitKey,
                lessonKey = lesson.lessonKey,
                lessonTitle = lesson.title,
                type = type,
                prompt = prompt,
                options = finalOpts,
                correctAnswer = finalAnswer,
                rationale = rationale,
                difficulty = difficulty,
                sourcePages = lesson.pageRange,
                fingerprint = computeFingerprint(prompt),
                reviewed = false,
                createdAt = now,
                conceptTag = lesson.title,
                sourceType = QuestionSourceType.AI_GENERATED
            )
        }
    }

    private fun resolveAnswerLetterToOption(rawAnswer: String, options: List<String>): String {
        if (options.isEmpty()) return rawAnswer
        if (options.any { it.equals(rawAnswer, ignoreCase = true) }) return rawAnswer
        val letter = rawAnswer.trim().firstOrNull()?.uppercaseChar()
        val mappedIdx = when (letter) {
            'أ', 'ا', 'A' -> 0
            'ب', 'B' -> 1
            'ج', 'C' -> 2
            'د', 'D' -> 3
            else -> -1
        }
        return if (mappedIdx in options.indices) options[mappedIdx] else rawAnswer
    }

    fun gradeQuiz(
        questions: List<QuestionItem>,
        selectedAnswers: Map<String, String>
    ): QuizResultSummary {
        if (questions.isEmpty()) {
            return QuizResultSummary(totalQuestions = 0, correctCount = 0, scorePercent = 0, earnedXp = 0)
        }
        val correct = questions.count { q ->
            val picked = selectedAnswers[q.id]?.trim().orEmpty()
            isAnswerCorrect(q, picked)
        }
        val percent = LearningProgressCalculator.calculateCompletionPercent(correct, questions.size)
        val earnedXp = (correct * 10) + if (percent >= 60) 15 else 5
        return QuizResultSummary(
            totalQuestions = questions.size,
            correctCount = correct,
            scorePercent = percent,
            earnedXp = earnedXp
        )
    }

    fun isAnswerCorrect(q: QuestionItem, rawPicked: String): Boolean {
        val picked = rawPicked.trim()
        if (picked.isBlank()) return false
        val expected = q.correctAnswer.trim()
        return when (q.type) {
            QuestionType.ORDERING -> {
                val normPicked = picked.split("|", "->", "←").map { normalizeForFingerprint(it) }.filter { it.isNotBlank() }
                val normExpected = if (expected.isNotBlank()) {
                    expected.split("|", "->", "←").map { normalizeForFingerprint(it) }.filter { it.isNotBlank() }
                } else {
                    q.options.map { normalizeForFingerprint(it) }.filter { it.isNotBlank() }
                }
                normPicked.isNotEmpty() && normPicked == normExpected
            }
            QuestionType.MATCHING -> {
                val pickedPairs = picked.split(";").map { normalizeForFingerprint(it) }.filter { it.isNotBlank() }.toSet()
                val expectedPairs = expected.split(";").map { normalizeForFingerprint(it) }.filter { it.isNotBlank() }.toSet()
                pickedPairs.isNotEmpty() && pickedPairs == expectedPairs
            }
            QuestionType.FILL_BLANK, QuestionType.SHORT_ANSWER -> {
                val normPicked = normalizeForFingerprint(picked)
                val normExpected = normalizeForFingerprint(expected)
                normPicked == normExpected || (normExpected.length > 3 && normPicked.contains(normExpected))
            }
            else -> picked.equals(expected, ignoreCase = true) ||
                normalizeForFingerprint(picked) == normalizeForFingerprint(expected)
        }
    }

    fun exportAssessmentMarkdown(
        assessment: AssessmentRecord,
        questions: List<QuestionItem>
    ): String = buildString {
        append("# ${assessment.title}\n\n")
        append("- **النوع:** ${assessment.kind.labelAr}\n")
        append("- **عدد الأسئلة:** ${questions.size}\n")
        append("- **الزمن:** ${if (assessment.timeLimitMinutes > 0) "${assessment.timeLimitMinutes} دقيقة" else "مفتوح"}\n")
        append("- **درجة النجاح:** ${assessment.passScorePercent}%\n\n---\n\n")
        questions.forEachIndexed { idx, q ->
            append("### ${idx + 1}. ${q.prompt}\n")
            append("*(${q.type.labelAr} • ${q.difficulty.labelAr})*\n\n")
            q.options.forEachIndexed { optIdx, opt ->
                append("- ${optIdx + 1}) $opt\n")
            }
            append("\n> **الإجابة النموذجية:** ${q.correctAnswer}\n")
            if (q.rationale.isNotBlank()) {
                append("> **التفسير:** ${q.rationale}\n")
            }
            append("\n")
        }
    }

    private fun firstStr(obj: JsonObject, vararg keys: String): String {
        for (k in keys) {
            val v = (obj[k] as? JsonPrimitive)?.contentOrNull?.trim()
            if (!v.isNullOrBlank()) return v
        }
        return ""
    }
}
