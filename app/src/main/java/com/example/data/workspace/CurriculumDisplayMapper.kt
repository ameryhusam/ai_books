package com.example.data.workspace

import java.util.Locale

/**
 * Dynamic, extensible display mapper for indexer2 grade/subject/component codes.
 * Always prefers explicit localized metadata declared inside `index.json` or `book_manifest.json`
 * and falls back to normalized code mappings when metadata omits display labels.
 */
object CurriculumDisplayMapper {

    fun normalizeGradeKey(raw: String?): String {
        val clean = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (clean.isBlank()) return ""
        val digits = Regex("(\\d{1,2})").find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (digits != null && (clean.startsWith("G") || clean.all { it.isDigit() })) {
            "G${digits.toString().padStart(2, '0')}"
        } else {
            clean
        }
    }

    fun gradeDisplayName(gradeKey: String?, declaredGradeName: String? = null): String {
        val declared = declaredGradeName?.trim()?.takeIf { it.isNotBlank() }
        if (declared != null) return declared
        val normalized = normalizeGradeKey(gradeKey)
        return when (normalized) {
            "G01" -> "الصف الأول"
            "G02" -> "الصف الثاني"
            "G03" -> "الصف الثالث"
            "G04" -> "الصف الرابع"
            "G05" -> "الصف الخامس"
            "G06" -> "الصف السادس"
            "G07" -> "الصف السابع"
            "G08" -> "الصف الثامن"
            "G09" -> "الصف التاسع"
            "G10" -> "الصف العاشر"
            "G11" -> "الصف الحادي عشر"
            "G12" -> "الصف الثاني عشر"
            else -> gradeKey?.trim().orEmpty()
        }
    }

    fun gradeBadgeLabel(gradeKey: String?, declaredGradeName: String? = null): String {
        val normalized = normalizeGradeKey(gradeKey).ifBlank { gradeKey?.trim().orEmpty() }
        if (normalized.isBlank()) return ""
        val arabicName = gradeDisplayName(normalized, declaredGradeName)
        return if (arabicName.isNotBlank() && !arabicName.equals(normalized, ignoreCase = true)) {
            "$normalized • $arabicName"
        } else {
            normalized
        }
    }

    fun subjectDisplayName(
        subjectKey: String,
        componentKey: String? = null,
        declaredSubjectName: String? = null
    ): String {
        val declared = declaredSubjectName?.trim()?.takeIf { it.isNotBlank() }
        if (declared != null) return declared
        val subject = subjectKey.trim().uppercase(Locale.ROOT)
        val component = componentKey?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        return when (subject) {
            "ARAB" -> "لغتي العربية"
            "SCI" -> "العلوم والحياة"
            "MATH" -> "الرياضيات"
            "ISL" -> "التربية الإسلامية"
            "QUR" -> "القرآن الكريم وتلاوته"
            "SOC" -> when (component) {
                "GEO" -> "الجغرافيا"
                "HIST" -> "التاريخ"
                "TARBIA", "CIV", "NAT" -> "التربية الوطنية"
                else -> "الدراسات الاجتماعية"
            }
            "ENG" -> "اللغة الإنجليزية"
            "PHY" -> "الفيزياء"
            "CHEM" -> "الكيمياء"
            "BIO" -> "الأحياء"
            "COMP", "IT", "CS" -> "الحاسوب وتقنية المعلومات"
            else -> subjectKey.trim().ifBlank { "كتاب دراسي" }
        }
    }

    fun componentDisplayName(componentKey: String?): String? {
        val clean = componentKey?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        return when (clean) {
            "COURSE", "STUDENT", "PB" -> "كتاب الطالب (Course)"
            "WORKBOOK", "WB", "ACTIVITY" -> "كتاب التمارين (Workbook)"
            "GEO" -> "الجغرافيا"
            "HIST" -> "التاريخ"
            "TARBIA", "CIV", "NAT" -> "التربية الوطنية"
            "GUIDE", "TEACHER" -> "دليل المعلم"
            else -> componentKey.trim()
        }
    }

    fun partDisplayName(partKey: String?): String? {
        return when (partKey?.trim()?.uppercase(Locale.ROOT)) {
            "P01" -> "الجزء الأول (P01)"
            "P02" -> "الجزء الثاني (P02)"
            "BOTH" -> "الجزءان (P01 + P02)"
            else -> partKey?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
