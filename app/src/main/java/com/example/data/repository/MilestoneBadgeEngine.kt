package com.example.data.repository

import com.example.data.local.DraftEntity
import com.example.data.local.InteractivePackageEntity
import com.example.data.local.LessonProgressEntity
import com.example.model.AchievementBadge
import com.example.model.BadgeCategory
import com.example.model.BookProgressSummary
import com.example.model.LearningUnitPath
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StudyStreakSnapshot(
    val currentStreakDays: Int,
    val longestStreakDays: Int
)

object MilestoneBadgeEngine {

    fun parseLocalDate(raw: String?, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        runCatching { return LocalDate.parse(trimmed) }
        runCatching { return Instant.parse(trimmed).atZone(zoneId).toLocalDate() }
        if (trimmed.length >= 10) {
            runCatching { return LocalDate.parse(trimmed.substring(0, 10)) }
        }
        return null
    }

    fun collectActivityDates(
        studyDayStrings: List<String>,
        drafts: List<DraftEntity>,
        packages: List<InteractivePackageEntity>,
        progressRows: List<LessonProgressEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Set<LocalDate> {
        val dates = linkedSetOf<LocalDate>()
        studyDayStrings.forEach { parseLocalDate(it, zoneId)?.let(dates::add) }
        drafts.forEach { parseLocalDate(it.createdAt, zoneId)?.let(dates::add) }
        packages.forEach { parseLocalDate(it.createdAt, zoneId)?.let(dates::add) }
        progressRows
            .filter { it.completed }
            .forEach { parseLocalDate(it.completedAt, zoneId)?.let(dates::add) }
        return dates
    }

    fun computeStudyStreak(
        activityDates: Set<LocalDate>,
        today: LocalDate = LocalDate.now()
    ): StudyStreakSnapshot {
        if (activityDates.isEmpty()) {
            return StudyStreakSnapshot(
                currentStreakDays = 0,
                longestStreakDays = 0
            )
        }

        val sorted = activityDates.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            if (sorted[i - 1].plusDays(1) == sorted[i]) {
                run++
                if (run > longest) longest = run
            } else {
                run = 1
            }
        }

        var cursor: LocalDate? = when {
            today in activityDates -> today
            today.minusDays(1) in activityDates -> today.minusDays(1)
            else -> null
        }
        var current = 0
        while (cursor != null && cursor in activityDates) {
            current++
            cursor = cursor.minusDays(1)
        }

        return StudyStreakSnapshot(
            currentStreakDays = current,
            longestStreakDays = longest
        )
    }

    fun evaluateBadges(
        totalLessons: Int,
        completedLessons: Int,
        overallCompletionPercent: Int,
        draftsCount: Int,
        approvedDraftsCount: Int,
        packagesCount: Int,
        publishedPackagesCount: Int,
        bookProgress: List<BookProgressSummary>,
        unitPaths: List<LearningUnitPath>,
        streak: StudyStreakSnapshot
    ): List<AchievementBadge> {
        val completedUnitsCount = unitPaths.count { it.totalCount > 0 && it.completedCount == it.totalCount }
        val completedBooksCount = bookProgress.count { it.totalLessons > 0 && it.completedLessons == it.totalLessons }
        val bestStreak = maxOf(streak.currentStreakDays, streak.longestStreakDays)

        return listOf(
            AchievementBadge(
                id = "badge_first_lesson",
                title = "أول درس مكتمل",
                subtitle = "إتمام أول درس كامل في مسار التعلم",
                unlocked = completedLessons >= 1,
                pointsBonus = 40,
                category = BadgeCategory.MILESTONE,
                currentProgress = completedLessons.coerceAtMost(1),
                targetProgress = 1,
                requirementLabel = "أكمل درساً واحداً بالكامل داخل مسار التعلم"
            ),
            AchievementBadge(
                id = "badge_5_day_streak",
                title = "مثابرة 5 أيام",
                subtitle = "سلسلة دراسة لمدة 5 أيام متتالية",
                unlocked = bestStreak >= 5,
                pointsBonus = 75,
                category = BadgeCategory.STREAK,
                currentProgress = bestStreak.coerceAtMost(5),
                targetProgress = 5,
                requirementLabel = "حافظ على نشاط الدراسة لمدة 5 أيام متتالية"
            ),
            AchievementBadge(
                id = "badge_workspace",
                title = "انطلاقة المسار",
                subtitle = "إعداد مساحة عمل تعليمية",
                unlocked = totalLessons > 0,
                pointsBonus = 25,
                category = BadgeCategory.CREATION,
                currentProgress = if (totalLessons > 0) 1 else 0,
                targetProgress = 1,
                requirementLabel = "استورد ملف ZIP أو جزّئ كتاب PDF لإنشاء مساحة عمل"
            ),
            AchievementBadge(
                id = "badge_ai_author",
                title = "مؤلف ذكي",
                subtitle = "توليد أو اعتماد محتوى AI",
                unlocked = draftsCount > 0,
                pointsBonus = 30,
                category = BadgeCategory.CREATION,
                currentProgress = draftsCount.coerceAtMost(1),
                targetProgress = 1,
                requirementLabel = "أنشئ أول مسودة تعليمية (${approvedDraftsCount} معتمدة)"
            ),
            AchievementBadge(
                id = "badge_3d_creator",
                title = "رائد التفاعلية",
                subtitle = "رفع أو نشر حزمة HTML/3D",
                unlocked = packagesCount > 0,
                pointsBonus = 35,
                category = BadgeCategory.CREATION,
                currentProgress = packagesCount.coerceAtMost(1),
                targetProgress = 1,
                requirementLabel = "ارفع أول حزمة تفاعلية HTML أو 3D (${publishedPackagesCount} منشورة)"
            ),
            AchievementBadge(
                id = "badge_halfway",
                title = "نصف الطريق",
                subtitle = "إتمام 50% من الدروس",
                unlocked = overallCompletionPercent >= 50 && totalLessons > 0,
                pointsBonus = 50,
                category = BadgeCategory.MILESTONE,
                currentProgress = overallCompletionPercent.coerceIn(0, 50),
                targetProgress = 50,
                requirementLabel = "أتمم نصف دروس مساحة العمل الحالية (50%)"
            ),
            AchievementBadge(
                id = "badge_unit_master",
                title = "إتقان وحدة",
                subtitle = "إكمال وحدة دراسية كاملة",
                unlocked = completedUnitsCount > 0,
                pointsBonus = 60,
                category = BadgeCategory.MASTERY,
                currentProgress = completedUnitsCount.coerceAtMost(1),
                targetProgress = 1,
                requirementLabel = "أنهِ جميع الدروس داخل وحدة دراسية واحدة"
            ),
            AchievementBadge(
                id = "badge_book_champion",
                title = "ختم كتاب كامل",
                subtitle = "إتمام 100% من كتاب مستورد",
                unlocked = completedBooksCount > 0,
                pointsBonus = 100,
                category = BadgeCategory.MASTERY,
                currentProgress = completedBooksCount.coerceAtMost(1),
                targetProgress = 1,
                requirementLabel = "أكمل 100% من دروس أي كتاب مستورد"
            )
        )
    }
}
