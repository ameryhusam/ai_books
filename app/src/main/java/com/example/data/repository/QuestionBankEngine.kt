package com.example.data.repository

import com.example.model.LessonRef
import com.example.model.QuestionDifficulty
import com.example.model.QuestionItem
import com.example.model.QuestionType
import com.example.ui.components.LearningProgressCalculator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
            val resolvedAnswer = when {
                rawAns.isNotBlank() -> rawAns
                answerIdx != null && answerIdx in opts.indices -> opts[answerIdx]
                opts.isNotEmpty() -> opts.first()
                else -> ""
            }
            val typeStr = firstStr(obj, "type", "questionType")
            val type = when {
                typeStr.isNotBlank() -> QuestionType.fromId(typeStr)
                opts.size == 2 && opts.any { it == "صح" || it == "True" } -> QuestionType.TRUE_FALSE
                opts.isEmpty() -> QuestionType.SHORT_ANSWER
                else -> QuestionType.MCQ
            }
            val finalOpts = if (type == QuestionType.TRUE_FALSE && opts.isEmpty()) {
                listOf("صح", "خطأ")
            } else {
                opts
            }

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
                correctAnswer = resolvedAnswer,
                rationale = firstStr(obj, "rationale", "explanation", "feedback"),
                difficulty = QuestionDifficulty.fromId(firstStr(obj, "difficulty", "level")),
                sourcePages = firstStr(obj, "sourcePages", "sourcePage", "page").ifBlank { lesson.pageRange },
                fingerprint = computeFingerprint(prompt),
                reviewed = false,
                createdAt = now
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
            val finalAnswer = resolveAnswerLetterToOption(correctAnswer, finalOpts)

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
                createdAt = now
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
        return if (mappedIdx in options.indices) options[mappedIdx] else rawAnswer.ifBlank { options.first() }
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
            picked.isNotBlank() && picked.equals(q.correctAnswer.trim(), ignoreCase = true)
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

    private fun firstStr(obj: JsonObject, vararg keys: String): String {
        for (k in keys) {
            val v = (obj[k] as? JsonPrimitive)?.contentOrNull?.trim()
            if (!v.isNullOrBlank()) return v
        }
        return ""
    }
}
