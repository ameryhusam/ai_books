package com.example.data.workspace

import com.example.data.local.BookEntity
import com.example.data.local.LessonEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceIndexEntity
import com.example.data.local.WorkspaceUnitEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import kotlin.reflect.KProperty1

/**
 * Single Room-projected schema & canonical artifact serializer for the Book Index pipeline.
 *
 * 1) Discovery AI response schema (`buildDiscoveryResponseSchemaJson`) is projected directly from
 *    compile-time KProperty references to Room entities (`WorkspaceEntity`, `BookEntity`,
 *    `WorkspaceUnitEntity`, `LessonEntity`), avoiding drift between Room storage and AI JSON contracts.
 * 2) Approved Room records (`BookEntity`, `WorkspaceUnitEntity`, `LessonEntity`) are serialized
 *    directly into canonical `index.json` (`edu.book_index.v2`) and `book_manifest.json`
 *    (`edu.book_manifest.v2`) after Room persistence.
 */
object RoomBookIndexProjection {

    private val prettyJson = Json { prettyPrint = true }

    enum class JsonFieldType(val schemaType: String) {
        STRING("string"),
        INTEGER("integer"),
        NUMBER("number")
    }

    data class RoomFieldDescriptor<T>(
        val property: KProperty1<T, *>,
        val jsonKey: String = property.name,
        val type: JsonFieldType,
        val enumValues: List<String> = emptyList(),
        val requiredInDiscovery: Boolean = true
    )

    // Compile-time bindings to Room @Entity columns for book-level metadata.
    private val workspaceFieldDescriptors: List<RoomFieldDescriptor<WorkspaceEntity>> = listOf(
        RoomFieldDescriptor(WorkspaceEntity::gradeKey, "gradeKey", JsonFieldType.STRING)
    )

    private val bookFieldDescriptors: List<RoomFieldDescriptor<BookEntity>> = listOf(
        RoomFieldDescriptor(BookEntity::title, "bookTitle", JsonFieldType.STRING),
        RoomFieldDescriptor(BookEntity::gradeName, "gradeName", JsonFieldType.STRING),
        RoomFieldDescriptor(BookEntity::subjectName, "subjectName", JsonFieldType.STRING),
        RoomFieldDescriptor(BookEntity::subjectKey, "subjectKey", JsonFieldType.STRING),
        RoomFieldDescriptor(BookEntity::componentKey, "componentKey", JsonFieldType.STRING, requiredInDiscovery = false),
        RoomFieldDescriptor(
            BookEntity::sourceLayout,
            "sourceLayout",
            JsonFieldType.STRING,
            enumValues = listOf("SINGLE_PART_FILE", "MERGED_PARTS_FILE", "UNKNOWN")
        ),
        RoomFieldDescriptor(BookEntity::sourceLayoutEvidence, "sourceLayoutEvidence", JsonFieldType.STRING),
        RoomFieldDescriptor(
            BookEntity::partKey,
            "partKey",
            JsonFieldType.STRING,
            enumValues = listOf("P01", "P02", "BOTH", "UNKNOWN")
        ),
        RoomFieldDescriptor(BookEntity::offset, "offset", JsonFieldType.INTEGER)
    )

    // Compile-time bindings to Room @Entity columns for unit-level metadata.
    private val unitFieldDescriptors: List<RoomFieldDescriptor<WorkspaceUnitEntity>> = listOf(
        RoomFieldDescriptor(WorkspaceUnitEntity::unitKey, "unitKey", JsonFieldType.STRING),
        RoomFieldDescriptor(WorkspaceUnitEntity::title, "title", JsonFieldType.STRING),
        RoomFieldDescriptor(
            WorkspaceUnitEntity::partKey,
            "partKey",
            JsonFieldType.STRING,
            enumValues = listOf("P01", "P02", "UNKNOWN")
        ),
        RoomFieldDescriptor(WorkspaceUnitEntity::proposedPartKey, "proposedPartKey", JsonFieldType.STRING),
        RoomFieldDescriptor(
            WorkspaceUnitEntity::assignmentStatus,
            "assignmentStatus",
            JsonFieldType.STRING,
            enumValues = listOf("assigned", "need_review")
        ),
        RoomFieldDescriptor(
            WorkspaceUnitEntity::assignmentSource,
            "assignmentSource",
            JsonFieldType.STRING,
            enumValues = listOf("INDEX_EVIDENCE", "DEFAULT_PROPOSAL", "LEGACY_UNVERIFIED")
        ),
        RoomFieldDescriptor(WorkspaceUnitEntity::assignmentEvidence, "assignmentEvidence", JsonFieldType.STRING)
    )

    // Compile-time bindings to Room @Entity columns for lesson-level metadata.
    private val lessonFieldDescriptors: List<RoomFieldDescriptor<LessonEntity>> = listOf(
        RoomFieldDescriptor(LessonEntity::lessonKey, "lessonKey", JsonFieldType.STRING),
        RoomFieldDescriptor(LessonEntity::title, "title", JsonFieldType.STRING),
        RoomFieldDescriptor(LessonEntity::printedPageStart, "printedPageStart", JsonFieldType.INTEGER),
        RoomFieldDescriptor(LessonEntity::printedPageEnd, "printedPageEnd", JsonFieldType.INTEGER),
        RoomFieldDescriptor(LessonEntity::pdfPageStart, "pdfPageStart", JsonFieldType.INTEGER),
        RoomFieldDescriptor(LessonEntity::pdfPageEnd, "pdfPageEnd", JsonFieldType.INTEGER)
    )

    private fun JsonFieldDescriptorSpec(type: JsonFieldType, enumValues: List<String> = emptyList()): JsonObject =
        buildJsonObject {
            put("type", type.schemaType)
            if (enumValues.isNotEmpty()) {
                put("enum", buildJsonArray { enumValues.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }
        }

    /**
     * Generates the AI discovery JSON schema directly from Room entity column descriptors.
     */
    fun buildDiscoveryResponseSchemaJson(): String {
        val lessonSchema = buildJsonObject {
            put("type", "object")
            put("required", buildJsonArray {
                lessonFieldDescriptors.filter { it.requiredInDiscovery }.forEach {
                    add(kotlinx.serialization.json.JsonPrimitive(it.jsonKey))
                }
                add(kotlinx.serialization.json.JsonPrimitive("assignmentStatus"))
                add(kotlinx.serialization.json.JsonPrimitive("assignmentEvidence"))
            })
            put("properties", buildJsonObject {
                lessonFieldDescriptors.forEach { desc ->
                    put(desc.jsonKey, JsonFieldDescriptorSpec(desc.type, desc.enumValues))
                }
                // Discovery-review provenance fields consumed by BookIndexLessonDraft
                put("assignmentStatus", JsonFieldDescriptorSpec(JsonFieldType.STRING, listOf("assigned", "need_review")))
                put("assignmentEvidence", JsonFieldDescriptorSpec(JsonFieldType.STRING))
            })
        }

        val unitSchema = buildJsonObject {
            put("type", "object")
            put("required", buildJsonArray {
                unitFieldDescriptors.filter { it.requiredInDiscovery }.forEach {
                    add(kotlinx.serialization.json.JsonPrimitive(it.jsonKey))
                }
                add(kotlinx.serialization.json.JsonPrimitive("lessons"))
            })
            put("properties", buildJsonObject {
                unitFieldDescriptors.forEach { desc ->
                    put(desc.jsonKey, JsonFieldDescriptorSpec(desc.type, desc.enumValues))
                }
                put("lessons", buildJsonObject {
                    put("type", "array")
                    put("items", lessonSchema)
                })
            })
        }

        val rootSchema = buildJsonObject {
            put("type", "object")
            put("required", buildJsonArray {
                val orderedBookKeys = listOf(
                    "bookTitle",
                    "gradeName",
                    "gradeKey",
                    "subjectName",
                    "subjectKey",
                    "sourceLayout",
                    "sourceLayoutEvidence",
                    "partKey",
                    "offset",
                    "units",
                    "discoveryNotes"
                )
                orderedBookKeys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("properties", buildJsonObject {
                bookFieldDescriptors.first { it.jsonKey == "bookTitle" }.let {
                    put(it.jsonKey, JsonFieldDescriptorSpec(it.type, it.enumValues))
                }
                bookFieldDescriptors.first { it.jsonKey == "gradeName" }.let {
                    put(it.jsonKey, JsonFieldDescriptorSpec(it.type, it.enumValues))
                }
                workspaceFieldDescriptors.forEach {
                    put(it.jsonKey, JsonFieldDescriptorSpec(it.type, it.enumValues))
                }
                bookFieldDescriptors.filter { it.jsonKey !in setOf("bookTitle", "gradeName") }.forEach {
                    put(it.jsonKey, JsonFieldDescriptorSpec(it.type, it.enumValues))
                }
                put("discoveryConfidence", JsonFieldDescriptorSpec(JsonFieldType.NUMBER))
                put("units", buildJsonObject {
                    put("type", "array")
                    put("items", unitSchema)
                })
                put("discoveryNotes", buildJsonObject {
                    put("type", "array")
                    put("items", JsonFieldDescriptorSpec(JsonFieldType.STRING))
                })
            })
        }
        return prettyJson.encodeToString(rootSchema)
    }

    /**
     * Canonical bookKey & relativePath matching DirectWorkspaceBinder / WorkspaceImporter conventions.
     */
    fun canonicalBookIdentity(
        gradeKey: String,
        subjectKey: String,
        componentKey: String?,
        sourceLayout: IndexSourceLayout,
        partKey: String?
    ): BookIdentityPath {
        val cleanGrade = (BookMetadataMapper.normalizeGradeKey(gradeKey) ?: gradeKey.trim().uppercase(Locale.ROOT))
            .ifBlank { "G04" }
        val cleanSubject = (BookMetadataMapper.normalizeSubjectKey(subjectKey)
            ?: subjectKey.trim().uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9_-]"), ""))
            .ifBlank { "BOOK" }
        val cleanComponent = componentKey?.trim()?.uppercase(Locale.ROOT)
            ?.replace(Regex("[^A-Z0-9_-]"), "")
            ?.takeIf { it.isNotBlank() && it != "NONE" }

        val bookRootRel = buildString {
            append("workspace/")
            append(cleanGrade)
            append("/")
            append(cleanSubject)
            if (cleanComponent != null) {
                append("/")
                append(cleanComponent)
            }
        }
        val cleanPart = partKey?.trim()?.uppercase(Locale.ROOT)
        val isSinglePart = sourceLayout == IndexSourceLayout.SINGLE_PART_FILE && cleanPart in setOf("P01", "P02")
        val canonicalBookRel = if (isSinglePart) "$bookRootRel/$cleanPart" else bookRootRel
        val baseBookKey = if (cleanComponent != null) "$cleanSubject/$cleanComponent" else cleanSubject
        val bookKey = if (isSinglePart) "$baseBookKey|$cleanPart" else baseBookKey

        return BookIdentityPath(
            gradeKey = cleanGrade,
            subjectKey = cleanSubject,
            componentKey = cleanComponent,
            bookKey = bookKey,
            bookRootRel = bookRootRel,
            canonicalBookRel = canonicalBookRel
        )
    }

    data class BookIdentityPath(
        val gradeKey: String,
        val subjectKey: String,
        val componentKey: String?,
        val bookKey: String,
        val bookRootRel: String,
        val canonicalBookRel: String
    )

    data class ApprovedRoomEntitiesBundle(
        val identity: BookIdentityPath,
        val book: BookEntity,
        val units: List<WorkspaceUnitEntity>,
        val lessons: List<LessonEntity>
    )

    /**
     * Converts a reviewer-confirmed [BookIndexIntakeDraft] into Room entities ready for persistence
     * BEFORE index.json / book_manifest.json generation and PDF slicing.
     */
    fun toApprovedRoomEntities(
        draft: BookIndexIntakeDraft,
        workspaceId: String,
        rootPath: String,
        indexUriOrPath: String,
        existingBookRowId: Long = 0L
    ): ApprovedRoomEntitiesBundle {
        val approved = draft.requireApproved()
        val identity = canonicalBookIdentity(
            gradeKey = approved.gradeKey,
            subjectKey = approved.subjectKey,
            componentKey = approved.componentKey,
            sourceLayout = approved.sourceLayout,
            partKey = approved.partKey
        )
        val cleanBookTitle = approved.bookTitle.trim()
        val cleanGradeName = BookMetadataMapper.gradeDisplayName(identity.gradeKey, approved.gradeName)
            ?: approved.gradeName.trim()
        val cleanSubjectName = BookMetadataMapper.subjectDisplayName(identity.subjectKey, approved.subjectName)
            ?: approved.subjectName.trim()

        val totalLessonsCount = approved.units.sumOf { it.lessons.size }
        val bookPartKey = when (approved.sourceLayout) {
            IndexSourceLayout.SINGLE_PART_FILE -> approved.partKey?.uppercase(Locale.ROOT)
            IndexSourceLayout.MERGED_PARTS_FILE -> "BOTH"
            IndexSourceLayout.UNKNOWN -> null
        }

        val bookEntity = BookEntity(
            rowId = existingBookRowId,
            workspaceId = workspaceId,
            bookKey = identity.bookKey,
            title = cleanBookTitle,
            subjectKey = identity.subjectKey,
            componentKey = identity.componentKey,
            relativePath = identity.canonicalBookRel,
            lessonsCount = totalLessonsCount,
            coverPath = null,
            sourcePdfPath = null,
            subjectName = cleanSubjectName,
            schemaVersion = "edu.book_index.v2",
            gradeName = cleanGradeName,
            sourceLayout = approved.sourceLayout.name,
            sourceLayoutEvidence = approved.sourceLayoutEvidence.trim(),
            partKey = bookPartKey,
            offset = approved.offset,
            approvalStatus = "APPROVED",
            requiresReview = false,
            totalPdfPages = approved.totalPdfPages,
            sourceFileName = approved.sourceFileName.trim(),
            sourceSha256 = approved.sourceSha256.trim().lowercase(Locale.ROOT),
            sourceReviewStatus = "VERIFIED"
        )

        val unitEntities = mutableListOf<WorkspaceUnitEntity>()
        val lessonEntities = mutableListOf<LessonEntity>()

        approved.units.forEachIndexed { unitIdx, unit ->
            val cleanUnitKey = sanitizeUnitKey(unit.unitKey, unitIdx)
            val cleanUnitPart = unit.partKey!!.trim().uppercase(Locale.ROOT)
            val cleanAssignmentSource = when (unit.assignmentSource) {
                IndexAssignmentSource.INDEX_EVIDENCE -> "INDEX_EVIDENCE"
                IndexAssignmentSource.REVIEWER -> "REVIEWER"
                IndexAssignmentSource.DEFAULT_PROPOSAL -> "REVIEWER"
                IndexAssignmentSource.LEGACY_UNVERIFIED -> "REVIEWER"
            }
            unitEntities.add(
                WorkspaceUnitEntity(
                    workspaceId = workspaceId,
                    bookKey = identity.bookKey,
                    unitKey = cleanUnitKey,
                    title = unit.title.trim(),
                    partKey = cleanUnitPart,
                    sortOrder = unitIdx,
                    sourceIndexPath = indexUriOrPath,
                    proposedPartKey = unit.proposedPartKey?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() },
                    assignmentStatus = "assigned",
                    assignmentSource = cleanAssignmentSource,
                    assignmentEvidence = unit.assignmentEvidence.trim()
                )
            )

            val folderUnitKey = unitFolderKey(cleanUnitKey, unitIdx)
            unit.lessons.forEachIndexed { lessonIdx, lesson ->
                val cleanLessonKey = sanitizeLessonKey(lesson.lessonKey, lessonIdx)
                val lessonNum = extractTrailingNumber(cleanLessonKey, lessonIdx + 1)
                val lessonFileBase = "lesson_${lessonNum.toString().padStart(2, '0')}"
                val physicalStart = approved.physicalStartPage(lesson)!!
                val physicalEnd = approved.physicalEndPage(lesson)!!
                val printedStart = lesson.printedPageStart?.takeIf { it > 0 }
                val printedEnd = lesson.printedPageEnd?.takeIf { it > 0 }
                val printedRange = if (printedStart != null && printedEnd != null) {
                    "$printedStart–$printedEnd"
                } else null
                val relUnderBookRoot = "$cleanUnitPart/units/$folderUnitKey/$lessonFileBase"
                val workspaceTextRel = "${identity.bookRootRel}/$relUnderBookRoot.txt"
                val resolvedTextPath = if (rootPath.isNotBlank()) {
                    java.io.File(rootPath, workspaceTextRel).absolutePath
                } else {
                    workspaceTextRel
                }

                lessonEntities.add(
                    LessonEntity(
                        workspaceId = workspaceId,
                        grade = identity.gradeKey,
                        rootPath = rootPath,
                        bookKey = identity.bookKey,
                        bookTitle = cleanBookTitle,
                        subjectKey = identity.subjectKey,
                        componentKey = identity.componentKey,
                        unitKey = cleanUnitKey,
                        unitTitle = unit.title.trim(),
                        lessonKey = cleanLessonKey,
                        title = lesson.title.trim(),
                        partKey = cleanUnitPart,
                        pageRange = "$physicalStart–$physicalEnd",
                        pdfPath = null,
                        sourceTextPath = resolvedTextPath,
                        interactivePath = null,
                        printedPageRange = printedRange,
                        printedPageStart = printedStart,
                        printedPageEnd = printedEnd,
                        pdfPageStart = physicalStart,
                        pdfPageEnd = physicalEnd,
                        sortOrder = lessonIdx
                    )
                )
            }
        }

        return ApprovedRoomEntitiesBundle(
            identity = identity,
            book = bookEntity,
            units = unitEntities,
            lessons = lessonEntities
        )
    }

    /**
     * Generates canonical `index.json` (`edu.book_index.v2`) directly from persisted Room entities.
     */
    fun serializeCanonicalIndexFromRoom(
        workspace: WorkspaceEntity,
        book: BookEntity,
        units: List<WorkspaceUnitEntity>,
        lessons: List<LessonEntity>,
        filterPartKey: String? = null
    ): String {
        val identity = canonicalBookIdentity(
            gradeKey = workspace.gradeKey,
            subjectKey = book.subjectKey,
            componentKey = book.componentKey,
            sourceLayout = runCatching { IndexSourceLayout.valueOf(book.sourceLayout) }
                .getOrDefault(IndexSourceLayout.SINGLE_PART_FILE),
            partKey = book.partKey
        )
        val sortedUnits = units
            .sortedBy { it.sortOrder }
            .filter { filterPartKey == null || it.partKey.equals(filterPartKey, ignoreCase = true) }
        val lessonsByUnit = lessons.groupBy { it.unitKey }
        val effectivePartKey = filterPartKey ?: book.partKey.orEmpty()

        val root = buildJsonObject {
            put("schemaVersion", book.schemaVersion.ifBlank { "edu.book_index.v2" })
            put("gradeKey", workspace.gradeKey)
            put("gradeName", book.gradeName)
            put("subjectKey", book.subjectKey)
            put("subjectName", book.subjectName ?: book.subjectKey)
            if (!book.componentKey.isNullOrBlank()) {
                put("componentKey", book.componentKey)
            }
            put("title", book.title)
            put("displayName", book.title)
            put("sourceLayout", book.sourceLayout)
            put("sourceLayoutEvidence", book.sourceLayoutEvidence)
            put("partKey", effectivePartKey)
            put("offset", book.offset)
            put("totalPdfPages", book.totalPdfPages)
            if (!book.sourceFileName.isNullOrBlank()) put("sourceFileName", book.sourceFileName)
            if (!book.sourceSha256.isNullOrBlank()) put("sourceSha256", book.sourceSha256)
            put("approvalStatus", book.approvalStatus)
            put("requiresReview", book.requiresReview)
            put("sourceReviewStatus", book.sourceReviewStatus)
            put("sourceMetadata", buildJsonObject {
                put("sourceRequiresReview", book.requiresReview)
                put("partEvidence", book.sourceLayoutEvidence)
            })
            put("units", buildJsonArray {
                sortedUnits.forEachIndexed { uIdx, unit ->
                    val unitPart = unit.partKey?.uppercase(Locale.ROOT).orEmpty()
                    val folderUnitKey = unitFolderKey(unit.unitKey, uIdx)
                    val unitLessons = lessonsByUnit[unit.unitKey].orEmpty().sortedBy { it.sortOrder }
                    add(buildJsonObject {
                        put("unitKey", unit.unitKey)
                        put("folderKey", folderUnitKey)
                        put("title", unit.title)
                        put("partKey", unitPart)
                        if (!unit.proposedPartKey.isNullOrBlank()) {
                            put("proposedPartKey", unit.proposedPartKey)
                        }
                        put("assignmentStatus", unit.assignmentStatus)
                        put("assignmentSource", unit.assignmentSource)
                        put("assignmentEvidence", unit.assignmentEvidence)
                        put("sortOrder", unit.sortOrder)
                        put("lessons", buildJsonArray {
                            unitLessons.forEachIndexed { lIdx, lesson ->
                                val lessonNum = extractTrailingNumber(lesson.lessonKey, lIdx + 1)
                                val lessonFile = "lesson_${lessonNum.toString().padStart(2, '0')}.pdf"
                                val lessonPart = (lesson.partKey ?: unitPart).uppercase(Locale.ROOT)
                                val workspacePdfUri = "${identity.bookRootRel}/$lessonPart/units/$folderUnitKey/$lessonFile"
                                val partRelativePdf = "$lessonPart/units/$folderUnitKey/$lessonFile"
                                val textUri = "${identity.bookRootRel}/$lessonPart/units/$folderUnitKey/${lessonFile.removeSuffix(".pdf")}.txt"
                                add(buildJsonObject {
                                    put("lessonKey", lesson.lessonKey)
                                    put("lessonNumber", lessonNum)
                                    put("title", lesson.title)
                                    put("partKey", lessonPart)
                                    if (lesson.printedPageStart != null) put("printedPageStart", lesson.printedPageStart)
                                    if (lesson.printedPageEnd != null) put("printedPageEnd", lesson.printedPageEnd)
                                    if (lesson.pdfPageStart != null) put("pdfPageStart", lesson.pdfPageStart)
                                    if (lesson.pdfPageEnd != null) put("pdfPageEnd", lesson.pdfPageEnd)
                                    put("sortOrder", lesson.sortOrder)
                                    put("sourceTextPath", textUri)
                                    put("sliceFile", buildJsonObject {
                                        put("workspaceUri", workspacePdfUri)
                                        put("relativePath", partRelativePdf)
                                    })
                                })
                            }
                        })
                    })
                }
            })
        }
        return prettyJson.encodeToString(root)
    }

    /**
     * Generates canonical `book_manifest.json` (`edu.book_manifest.v2`) directly from Room entities
     * and the SHA-256 hashes of the Room-serialized `index.json` file(s).
     */
    fun serializeCanonicalManifestFromRoom(
        workspace: WorkspaceEntity,
        book: BookEntity,
        canonicalIndexPath: String,
        canonicalIndexSha256: String,
        partIndexSha256ByRelativePath: Map<String, String> = emptyMap(),
        coverRelativePath: String? = null
    ): String {
        val manifest = buildJsonObject {
            put("manifestVersion", "edu.book_manifest.v2")
            put("gradeKey", workspace.gradeKey)
            put("gradeName", book.gradeName)
            put("subjectKey", book.subjectKey)
            put("subjectName", book.subjectName ?: book.subjectKey)
            if (!book.componentKey.isNullOrBlank()) {
                put("componentKey", book.componentKey)
            }
            put("title", book.title)
            put("displayName", book.title)
            put("partKey", book.partKey.orEmpty())
            put("sourceLayout", book.sourceLayout)
            put("sourceLayoutEvidence", book.sourceLayoutEvidence)
            put("sourceReviewStatus", book.sourceReviewStatus)
            if (!book.sourceFileName.isNullOrBlank()) put("sourceFileName", book.sourceFileName)
            if (!book.sourceSha256.isNullOrBlank()) put("sourceSha256", book.sourceSha256)
            put("canonicalIndexPath", canonicalIndexPath)
            put("canonicalIndexSha256", canonicalIndexSha256)
            put("indexSha256", canonicalIndexSha256)
            put("partIndexes", buildJsonArray {
                partIndexSha256ByRelativePath.entries.sortedBy { it.key }.forEach { (relPath, sha) ->
                    add(buildJsonObject {
                        put("partKey", relPath.substringBefore('/').uppercase(Locale.ROOT))
                        put("path", relPath)
                        put("sha256", sha)
                    })
                }
            })
            put("totalPdfPages", book.totalPdfPages)
            put("totalPages", book.totalPdfPages)
            put("offset", book.offset)
            if (!coverRelativePath.isNullOrBlank()) {
                put("cover", coverRelativePath)
            }
            put("sourceMetadata", buildJsonObject {
                put("sourceRequiresReview", book.requiresReview)
                put("partEvidence", book.sourceLayoutEvidence)
            })
            put("createdAt", Instant.now().toString())
        }
        return prettyJson.encodeToString(manifest)
    }

    fun unitFolderKey(unitKey: String, index: Int): String {
        val clean = unitKey.trim()
        if (clean.isBlank()) return "unit_${(index + 1).toString().padStart(2, '0')}"
        return clean.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    fun sanitizeUnitKey(rawKey: String, index: Int): String {
        val clean = rawKey.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
        return clean.ifBlank { "unit_${(index + 1).toString().padStart(2, '0')}" }
    }

    fun sanitizeLessonKey(rawKey: String, index: Int): String {
        val clean = rawKey.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
        return clean.ifBlank { "lesson_${(index + 1).toString().padStart(2, '0')}" }
    }

    fun extractTrailingNumber(key: String, fallback: Int): Int {
        val match = Regex("(\\d+)$").find(key) ?: return fallback
        return match.groupValues[1].toIntOrNull() ?: fallback
    }

    fun sha256Hex(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
