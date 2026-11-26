package com.example.data.workspace

import java.util.Locale

/**
 * Canonical Indexer2 curriculum metadata catalog.
 *
 * Keys are identifiers, not display labels. Unknown keys are never guessed,
 * normalized from arbitrary text, or replaced with a default subject/grade.
 * Update this catalog only when the Indexer2 detection rules or a canonical
 * template establish a new key.
 */
object BookMetadataMapper {
    const val CATALOG_VERSION = "indexer2-curriculum-catalog.v1"

    data class BookDisplayMetadata(
        val gradeKey: String?,
        val gradeName: String?,
        val subjectKey: String?,
        val subjectName: String?,
        val componentKey: String?,
        val componentName: String?,
        val isKnown: Boolean
    )

    private val grades = linkedMapOf(
        "G01" to "الصف الأول الأساسي",
        "G02" to "الصف الثاني الأساسي",
        "G03" to "الصف الثالث الأساسي",
        "G04" to "الصف الرابع الأساسي",
        "G05" to "الصف الخامس الأساسي",
        "G06" to "الصف السادس الأساسي",
        "G07" to "الصف السابع الأساسي",
        "G08" to "الصف الثامن الأساسي",
        "G09" to "الصف التاسع الأساسي",
        "G10" to "الصف الأول الثانوي (العاشر)",
        "G11" to "الصف الثاني الثانوي (الحادي عشر)",
        "G12" to "الصف الثالث الثانوي (الثاني عشر)"
    )

    private val subjects = linkedMapOf(
        "ARAB" to "اللغة العربية",
        "ISL" to "التربية الإسلامية",
        "QUR" to "القرآن الكريم وعلومه",
        "MATH" to "الرياضيات",
        "SOC" to "الدراسات الاجتماعية",
        "ENG" to "اللغة الإنجليزية",
        "COMP" to "الحاسوب وتقنية المعلومات",
        "BIO" to "الأحياء",
        "CHEM" to "الكيمياء",
        "PHYS" to "الفيزياء",
        "SCI" to "العلوم"
    )

    // These component keys are evidenced by Indexer2 detection rules and/or
    // canonical template filenames. No display-only or legacy aliases are admitted.
    private val components = linkedMapOf(
        "GEO" to "الجغرافيا",
        "HIST" to "التاريخ",
        "TARBIA" to "التربية الوطنية",
        "COURSE" to "كتاب الطالب (Course)"
    )

    private val basicGrades = (1..9).map { "G%02d".format(Locale.ROOT, it) }.toSet()
    private val secondaryGrades = setOf("G10", "G11", "G12")

    /** Accept canonical G01..G12 and compact G1..G9 forms only. Unknown input returns null. */
    fun normalizeGradeKey(raw: String?): String? {
        val clean = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (clean.isEmpty()) return null
        val canonical = when {
            Regex("^G\\d{2}$").matches(clean) -> clean
            Regex("^G[1-9]$").matches(clean) -> "G0${clean.last()}"
            else -> return null
        }
        return canonical.takeIf { grades.containsKey(it) }
    }

    fun normalizeSubjectKey(raw: String?): String? {
        val clean = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return clean.takeIf { subjects.containsKey(it) }
    }

    fun normalizeComponentKey(raw: String?): String? {
        val clean = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (clean.isEmpty() || clean == "NONE") return null
        return clean.takeIf { components.containsKey(it) }
    }

    fun gradeDisplayName(gradeKey: String?): String? =
        normalizeGradeKey(gradeKey)?.let(grades::get)

    fun subjectDisplayName(subjectKey: String?): String? =
        normalizeSubjectKey(subjectKey)?.let(subjects::get)

    fun componentDisplayName(componentKey: String?): String? =
        normalizeComponentKey(componentKey)?.let(components::get)

    fun isValidBookIdentity(gradeKey: String?, subjectKey: String?, componentKey: String?): Boolean {
        val grade = normalizeGradeKey(gradeKey) ?: return false
        val subject = normalizeSubjectKey(subjectKey) ?: return false
        val rawComponent = componentKey?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val component = if (rawComponent.isEmpty() || rawComponent == "NONE") null
            else normalizeComponentKey(rawComponent) ?: return false

        if (subject == "SCI" && grade !in basicGrades) return false
        if (subject in setOf("BIO", "CHEM", "PHYS") && grade !in secondaryGrades) return false
        if (component in setOf("GEO", "HIST", "TARBIA") && subject != "SOC") return false
        if (component == "COURSE" && subject != "ENG") return false
        return true
    }

    fun mapBookMetadata(
        gradeKey: String?,
        subjectKey: String?,
        componentKey: String?
    ): BookDisplayMetadata {
        val grade = normalizeGradeKey(gradeKey)
        val subject = normalizeSubjectKey(subjectKey)
        val rawComponent = componentKey?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val component = if (rawComponent.isEmpty() || rawComponent == "NONE") null
            else normalizeComponentKey(rawComponent)
        val valid = isValidBookIdentity(grade, subject, componentKey)
        return BookDisplayMetadata(
            gradeKey = grade,
            gradeName = grade?.let(grades::get),
            subjectKey = subject,
            subjectName = subject?.let(subjects::get),
            componentKey = component,
            componentName = component?.let(components::get),
            isKnown = valid
        )
    }
}
