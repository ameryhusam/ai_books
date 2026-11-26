package com.example.data.workspace

/**
 * Compatibility facade for existing UI call sites.
 * Canonical keys and identity validation remain controlled by BookMetadataMapper.
 * Declared display names from the canonical Indexer2 index may override labels without changing keys.
 */
@Deprecated("Use BookMetadataMapper; this facade will be removed after call-site migration.")
object CurriculumDisplayMapper {
    fun normalizeGradeKey(raw: String?): String =
        BookMetadataMapper.normalizeGradeKey(raw).orEmpty()

    fun gradeDisplayName(gradeKey: String?, declaredGradeName: String? = null): String =
        BookMetadataMapper.gradeDisplayName(gradeKey, declaredGradeName).orEmpty()

    fun gradeBadgeLabel(gradeKey: String?, declaredGradeName: String? = null): String {
        val normalized = BookMetadataMapper.normalizeGradeKey(gradeKey) ?: return ""
        val label = BookMetadataMapper.gradeDisplayName(normalized, declaredGradeName) ?: return normalized
        return "$normalized • $label"
    }

    fun subjectDisplayName(
        subjectKey: String,
        componentKey: String? = null,
        declaredSubjectName: String? = null
    ): String {
        val subject = BookMetadataMapper.subjectDisplayName(subjectKey, declaredSubjectName).orEmpty()
        val component = BookMetadataMapper.componentDisplayName(componentKey)
        return if (component != null) "$subject • $component" else subject
    }

    fun componentDisplayName(componentKey: String?, declaredComponentName: String? = null): String? =
        BookMetadataMapper.componentDisplayName(componentKey, declaredComponentName)

    fun partDisplayName(partKey: String?): String? =
        when (partKey?.trim()?.uppercase()) {
            "P01" -> "الجزء الأول (P01)"
            "P02" -> "الجزء الثاني (P02)"
            "BOTH" -> "الجزءان (P01 + P02)"
            else -> null
        }
}
