package com.example.data.workspace

/**
 * Compatibility facade for existing UI call sites.
 * All canonical labels and validation now come from BookMetadataMapper.
 * Declared display strings in index/manifest are deliberately not authoritative.
 */
@Deprecated("Use BookMetadataMapper; this facade will be removed after call-site migration.")
object CurriculumDisplayMapper {
    fun normalizeGradeKey(raw: String?): String =
        BookMetadataMapper.normalizeGradeKey(raw).orEmpty()

    fun gradeDisplayName(gradeKey: String?, declaredGradeName: String? = null): String =
        BookMetadataMapper.gradeDisplayName(gradeKey).orEmpty()

    fun gradeBadgeLabel(gradeKey: String?, declaredGradeName: String? = null): String {
        val normalized = BookMetadataMapper.normalizeGradeKey(gradeKey) ?: return ""
        val label = BookMetadataMapper.gradeDisplayName(normalized) ?: return normalized
        return "$normalized • $label"
    }

    fun subjectDisplayName(
        subjectKey: String,
        componentKey: String? = null,
        declaredSubjectName: String? = null
    ): String {
        val metadata = BookMetadataMapper.mapBookMetadata("G01", subjectKey, componentKey)
        val subject = BookMetadataMapper.subjectDisplayName(subjectKey).orEmpty()
        val component = BookMetadataMapper.componentDisplayName(componentKey)
        return if (component != null) "$subject • $component" else subject
    }

    fun componentDisplayName(componentKey: String?): String? =
        BookMetadataMapper.componentDisplayName(componentKey)

    fun partDisplayName(partKey: String?): String? =
        when (partKey?.trim()?.uppercase()) {
            "P01" -> "الجزء الأول (P01)"
            "P02" -> "الجزء الثاني (P02)"
            "BOTH" -> "الجزءان (P01 + P02)"
            else -> null
        }
}
