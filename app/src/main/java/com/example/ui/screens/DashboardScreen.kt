package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.EduMiniRepository
import com.example.model.AchievementBadge
import com.example.model.BadgeCategory
import com.example.model.ConceptMasterySummary
import com.example.model.DashboardLessonFilter
import com.example.model.DashboardSummary
import com.example.model.LearnerProfile
import com.example.model.LearningLessonNode
import com.example.model.LearningNodeStatus
import com.example.model.LearningUnitPath
import com.example.model.LessonRef
import com.example.model.StudentDashboardState
import com.example.model.UnitCompletionStatus
import com.example.model.UserRole
import com.example.ui.components.BookCoverImage
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.components.LearningProgress
import com.example.ui.components.StudentPerformanceChartCard
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduHeroKicker
import com.example.ui.theme.EduHeroSub
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite
import com.example.ui.theme.EduYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    userRole: UserRole = UserRole.LEARNER,
    dashboardState: StudentDashboardState = StudentDashboardState(),
    conceptMasterySummaries: List<ConceptMasterySummary> = emptyList(),
    activeLearner: LearnerProfile = LearnerProfile.DEFAULT,
    availableLearners: List<LearnerProfile> = listOf(LearnerProfile.DEFAULT),
    selectedBookFilterKey: String = "",
    bookmarkedLessons: List<LessonRef> = emptyList(),
    bookCoversByKey: Map<String, String?> = emptyMap(),
    onSelectLearner: (String) -> Unit = {},
    onAddLearner: (String) -> Unit = {},
    onSelectBookFilter: (String) -> Unit = {},
    onSelectLessonFilter: (DashboardLessonFilter) -> Unit = {},
    onToggleUnitExpanded: (String) -> Unit = {},
    onToggleAllUnitsExpanded: (List<String>) -> Unit = {},
    onDismissCompletedUnitBanner: () -> Unit = {},
    onOpenLessonInStudio: (LessonRef) -> Unit = {},
    onOpenStudentStudy: (LessonRef) -> Unit = {},
    onPreviewLessonPdf: (LessonRef) -> Unit = {},
    onLaunchConceptReinforcement: (String?) -> Unit = {},
    onOpenGlobalSearch: () -> Unit = {},
    onBindDirectFolder: () -> Unit = {},
    onPickBookPdf: () -> Unit = {},
    onPickZip: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedBadge by remember { mutableStateOf<AchievementBadge?>(null) }
    var selectedCategory by remember { mutableStateOf<BadgeCategory?>(null) }
    val haptic = LocalHapticFeedback.current
    val isLearner = userRole == UserRole.LEARNER
    val activeBookKey = dashboardState.selectedBookKey.ifBlank { selectedBookFilterKey }

    val bookFilteredUnits = remember(summary.unitPaths, activeBookKey) {
        if (activeBookKey.isBlank()) {
            summary.unitPaths
        } else {
            summary.unitPaths.filter { unitPath ->
                unitPath.nodes.any { it.lesson.bookKey == activeBookKey }
            }
        }
    }
    val allBookNodes = remember(bookFilteredUnits) {
        bookFilteredUnits.flatMap { it.nodes }
    }
    val filterCounts = remember(allBookNodes) {
        mapOf(
            DashboardLessonFilter.ALL to allBookNodes.size,
            DashboardLessonFilter.PENDING to allBookNodes.count { !it.isCompleted },
            DashboardLessonFilter.IN_PROGRESS to allBookNodes.count {
                !it.isCompleted && (it.status == LearningNodeStatus.IN_PROGRESS || it.quizAttemptedCount > 0)
            },
            DashboardLessonFilter.READY_FOR_QUIZ to allBookNodes.count { !it.isCompleted && it.questionsCount > 0 },
            DashboardLessonFilter.COMPLETED to allBookNodes.count { it.isCompleted }
        )
    }
    val visibleUnitPaths = remember(bookFilteredUnits, dashboardState.lessonFilter) {
        if (dashboardState.lessonFilter == DashboardLessonFilter.ALL) {
            bookFilteredUnits
        } else {
            bookFilteredUnits.mapNotNull { unit ->
                val filteredNodes = unit.nodes.filter { node ->
                    when (dashboardState.lessonFilter) {
                        DashboardLessonFilter.ALL -> true
                        DashboardLessonFilter.PENDING -> !node.isCompleted
                        DashboardLessonFilter.IN_PROGRESS ->
                            !node.isCompleted && (node.status == LearningNodeStatus.IN_PROGRESS || node.quizAttemptedCount > 0)
                        DashboardLessonFilter.READY_FOR_QUIZ -> !node.isCompleted && node.questionsCount > 0
                        DashboardLessonFilter.COMPLETED -> node.isCompleted
                    }
                }
                if (filteredNodes.isEmpty()) null else unit.copy(nodes = filteredNodes)
            }
        }
    }
    val completedUnitsCount = remember(bookFilteredUnits) {
        bookFilteredUnits.count { it.status == UnitCompletionStatus.COMPLETED }
    }
    val inProgressUnitsCount = remember(bookFilteredUnits) {
        bookFilteredUnits.count { it.status == UnitCompletionStatus.IN_PROGRESS }
    }
    val notStartedUnitsCount = remember(bookFilteredUnits) {
        bookFilteredUnits.count { it.status == UnitCompletionStatus.NOT_STARTED }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 1040.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "dashboard_hero") {
                DashboardHeroCard(
                    summary = summary,
                    onOpenGlobalSearch = onOpenGlobalSearch
                )
            }

            if (!summary.hasWorkspace) {
                item(key = "dashboard_empty") {
                    DashboardEmptyOnboarding(
                        showAdminLibraryButton = !isLearner,
                        onBindDirectFolder = onBindDirectFolder,
                        onOpenLibrary = onOpenLibrary
                    )
                }

                if (summary.badges.isNotEmpty()) {
                    item(key = "study_streak_empty") {
                        StudyStreakCard(
                            currentStreakDays = summary.currentStreakDays,
                            longestStreakDays = summary.longestStreakDays
                        )
                    }
                    item(key = "badges_section_empty") {
                        BadgesAndMilestonesSection(
                            badges = summary.badges,
                            unlockedCount = summary.unlockedBadgesCount,
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it },
                            onBadgeClick = { selectedBadge = it }
                        )
                    }
                }
            } else {
                summary.nextRecommendedLesson?.let { nextLesson ->
                    item(key = "next_step") {
                        NextRecommendedStepCard(
                            lesson = nextLesson,
                            coverPath = bookCoversByKey[nextLesson.bookKey],
                            showStudioAction = !isLearner,
                            onOpenStudentStudy = { onOpenStudentStudy(nextLesson) },
                            onPreviewPdf = if (!nextLesson.pdfPath.isNullOrBlank()) {
                                { onPreviewLessonPdf(nextLesson) }
                            } else null,
                            onOpenInStudio = { onOpenLessonInStudio(nextLesson) }
                        )
                    }
                }

                if (summary.weakConcepts.isNotEmpty()) {
                    item(key = "weak_concepts_panel") {
                        WeakConceptsReinforcementCard(
                            weakConcepts = summary.weakConcepts,
                            onLaunchConceptReinforcement = onLaunchConceptReinforcement
                        )
                    }
                }

                if (bookmarkedLessons.isNotEmpty()) {
                    item(key = "bookmarked_lessons") {
                        BookmarkedLessonsStrip(
                            lessons = bookmarkedLessons,
                            onOpenStudentStudy = onOpenStudentStudy,
                            onPreviewLessonPdf = onPreviewLessonPdf
                        )
                    }
                }

                item(key = "kpi_grid") {
                    DashboardKpiGrid(summary = summary)
                }

                item(key = "student_performance_chart") {
                    StudentPerformanceChartCard(
                        concepts = conceptMasterySummaries,
                        unitPaths = bookFilteredUnits,
                        overallCompletionPercent = summary.overallCompletionPercent,
                        activeLearner = activeLearner,
                        availableLearners = availableLearners,
                        onSelectLearner = onSelectLearner,
                        onAddLearner = onAddLearner,
                        onSelectConceptForReinforcement = onLaunchConceptReinforcement
                    )
                }

                if (summary.bookProgress.isNotEmpty()) {
                    item(key = "books_progress_heading") {
                        Text(
                            text = stringResource(R.string.dashboard_books_progress_heading),
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    item(key = "learning_progress") {
                        LearningProgress(
                            bookProgress = summary.bookProgress,
                            selectedBookKey = activeBookKey.ifBlank { null },
                            onSelectBook = { bookKey ->
                                onSelectBookFilter(if (activeBookKey == bookKey) "" else bookKey)
                            }
                        )
                    }
                    if (summary.bookProgress.size > 1) {
                        item(key = "book_filter_strip") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                EduPill(
                                    text = "كل المواد (${summary.totalLessons})",
                                    active = activeBookKey.isBlank(),
                                    onClick = { onSelectBookFilter("") },
                                    testTag = "dashboard_filter_all_books"
                                )
                                summary.bookProgress.forEach { bp ->
                                    EduPill(
                                        text = "${bp.book.title} (${bp.completedLessons}/${bp.totalLessons})",
                                        active = activeBookKey == bp.book.bookKey,
                                        onClick = {
                                            onSelectBookFilter(
                                                if (activeBookKey == bp.book.bookKey) "" else bp.book.bookKey
                                            )
                                        },
                                        testTag = "dashboard_filter_book_${bp.book.bookKey}"
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "study_streak_card") {
                    StudyStreakCard(
                        currentStreakDays = summary.currentStreakDays,
                        longestStreakDays = summary.longestStreakDays
                    )
                }

                item(key = "badges_section") {
                    BadgesAndMilestonesSection(
                        badges = summary.badges,
                        unlockedCount = summary.unlockedBadgesCount,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        onBadgeClick = { selectedBadge = it }
                    )
                }

                if (!dashboardState.recentlyCompletedUnitTitle.isNullOrBlank()) {
                    item(key = "unit_celebration_banner") {
                        UnitCompletedCelebrationBanner(
                            unitTitle = dashboardState.recentlyCompletedUnitTitle,
                            onDismiss = onDismissCompletedUnitBanner
                        )
                    }
                }

                item(key = "learning_path_heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Route,
                                    contentDescription = null,
                                    tint = EduGreenDark,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.dashboard_learning_path_heading),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EduInk
                                )
                            }
                            Text(
                                text = "${summary.completedLessons}/${summary.totalLessons} مكتمل بالاختبار",
                                style = MaterialTheme.typography.labelMedium,
                                color = EduGreenDark
                            )
                        }

                        if (bookFilteredUnits.isNotEmpty()) {
                            UnitStatusSummaryStrip(
                                completedUnits = completedUnitsCount,
                                inProgressUnits = inProgressUnitsCount,
                                notStartedUnits = notStartedUnitsCount,
                                allCollapsed = dashboardState.collapsedUnitKeys.size >= bookFilteredUnits.size,
                                onToggleExpandAll = {
                                    onToggleAllUnitsExpanded(bookFilteredUnits.map { it.unitKey })
                                }
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DashboardLessonFilter.entries.forEach { filter ->
                                    val count = filterCounts[filter] ?: 0
                                    EduPill(
                                        text = "${filter.labelAr} ($count)",
                                        active = dashboardState.lessonFilter == filter,
                                        onClick = { onSelectLessonFilter(filter) },
                                        testTag = "dashboard_lesson_filter_${filter.name.lowercase()}"
                                    )
                                }
                            }
                        }
                    }
                }

                if (visibleUnitPaths.isEmpty()) {
                    item(key = "empty_filtered_units") {
                        EduPanel(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "لا توجد دروس مطابقة للتصفية الحالية.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduMuted
                            )
                            OutlinedButton(
                                onClick = { onSelectLessonFilter(DashboardLessonFilter.ALL) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, EduGreen),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("عرض كل الدروس", color = EduGreenDark)
                            }
                        }
                    }
                } else {
                    visibleUnitPaths.forEach { unitPath ->
                        val isExpanded = unitPath.unitKey !in dashboardState.collapsedUnitKeys
                        item(key = "unit_header_${unitPath.unitKey}") {
                            val unitLesson = unitPath.nodes.firstOrNull()?.lesson
                            val unitBookKey = unitLesson?.bookKey.orEmpty()
                            UnitPathHeaderCard(
                                unitPath = unitPath,
                                coverPath = bookCoversByKey[unitBookKey],
                                subjectKey = unitLesson?.subjectKey ?: unitBookKey,
                                isExpanded = isExpanded,
                                onToggleExpand = { onToggleUnitExpanded(unitPath.unitKey) }
                            )
                        }
                        if (isExpanded) {
                            itemsIndexed(
                                items = unitPath.nodes,
                                key = { _, node -> "path_${node.lesson.bookKey}_${node.lesson.unitKey}_${node.lesson.lessonKey}" }
                            ) { idx, node ->
                                LearningPathNodeRow(
                                    stepNumber = idx + 1,
                                    isLast = idx == unitPath.nodes.lastIndex,
                                    node = node,
                                    showStudioAction = !isLearner,
                                    onOpenStudentStudy = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenStudentStudy(node.lesson)
                                    },
                                    onPreviewPdf = if (!node.lesson.pdfPath.isNullOrBlank()) {
                                        { onPreviewLessonPdf(node.lesson) }
                                    } else null,
                                    onOpenInStudio = { onOpenLessonInStudio(node.lesson) }
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedBadge?.let { badge ->
            BadgeDetailDialog(
                badge = badge,
                onDismiss = { selectedBadge = null }
            )
        }
    }
}

@Composable
private fun DashboardHeroCard(
    summary: DashboardSummary,
    onOpenGlobalSearch: () -> Unit
) {
    val animatedCompletion by animateFloatAsState(
        targetValue = (summary.overallCompletionPercent / 100f).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "completion_ring"
    )
    val animatedLevelProgress by animateFloatAsState(
        targetValue = (summary.pointsIntoLevel.toFloat() / summary.pointsPerLevel.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "level_bar"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = EduGreen
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_dashboard_banner),
                contentDescription = stringResource(R.string.cd_dashboard_banner),
                contentScale = ContentScale.Crop,
                alpha = 0.24f,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                EduGreenDark.copy(alpha = 0.92f),
                                EduGreen.copy(alpha = 0.82f),
                                EduPurple.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CompletionRing(
                        progress = animatedCompletion,
                        percentText = "${summary.overallCompletionPercent}%"
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = EduWhite.copy(alpha = 0.18f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = EduYellow,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "المستوى ${summary.level} • ${summary.levelTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EduWhite
                                )
                            }
                        }
                        Text(
                            text = if (summary.hasWorkspace) {
                                summary.workspaceTitle
                            } else {
                                stringResource(R.string.dashboard_hero_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = EduWhite
                        )
                        Text(
                            text = "${summary.totalPoints} نقطة XP • جاهزية المحتوى ${summary.preparationPercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduHeroSub
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "التقدم نحو المستوى ${summary.level + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EduHeroKicker
                        )
                        Text(
                            text = "${summary.pointsIntoLevel} / ${summary.pointsPerLevel} XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = EduYellow
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(9.dp)
                    ) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.22f),
                            cornerRadius = CornerRadius(12f, 12f),
                            size = size
                        )
                        drawRoundRect(
                            brush = Brush.horizontalGradient(listOf(EduYellow, EduHeroKicker)),
                            cornerRadius = CornerRadius(12f, 12f),
                            size = Size(width = size.width * animatedLevelProgress, height = size.height)
                        )
                    }
                }

                if (summary.hasWorkspace) {
                    OutlinedButton(
                        onClick = onOpenGlobalSearch,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EduWhite.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_global_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = EduWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "البحث الشامل في الدروس والمسودات والأسئلة",
                            style = MaterialTheme.typography.labelLarge,
                            color = EduWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionRing(
    progress: Float,
    percentText: String
) {
    Box(
        modifier = Modifier.size(86.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 9.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(EduYellow, EduHeroKicker, EduYellow)),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percentText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = EduWhite
            )
            Text(
                text = "إنجاز",
                style = MaterialTheme.typography.labelSmall,
                color = EduHeroKicker
            )
        }
    }
}

@Composable
private fun DashboardKpiGrid(summary: DashboardSummary) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isWide = maxWidth >= 680.dp
        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    title = "نسبة الإنجاز",
                    value = "${summary.overallCompletionPercent}%",
                    subtitle = "من إجمالي الدروس",
                    icon = Icons.Rounded.Route,
                    accent = EduGreen,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "الدروس المكتملة",
                    value = "${summary.completedLessons}/${summary.totalLessons}",
                    subtitle = "مُجهّز: ${summary.preparedLessons}",
                    icon = Icons.Rounded.Check,
                    accent = EduBlue,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "المحتوى المعتمد",
                    value = summary.approvedItemsCount.toString(),
                    subtitle = "مسودات وحزم منشورة",
                    icon = Icons.Rounded.AutoAwesome,
                    accent = EduPurple,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "مجموع النقاط",
                    value = "${summary.totalPoints}",
                    subtitle = "XP مكتسبة",
                    icon = Icons.Rounded.Bolt,
                    accent = EduOrange,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiCard(
                        title = "نسبة الإنجاز",
                        value = "${summary.overallCompletionPercent}%",
                        subtitle = "من إجمالي الدروس",
                        icon = Icons.Rounded.Route,
                        accent = EduGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "الدروس المكتملة",
                        value = "${summary.completedLessons}/${summary.totalLessons}",
                        subtitle = "مُجهّز: ${summary.preparedLessons}",
                        icon = Icons.Rounded.Check,
                        accent = EduBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiCard(
                        title = "المحتوى المعتمد",
                        value = summary.approvedItemsCount.toString(),
                        subtitle = "مسودات وحزم منشورة",
                        icon = Icons.Rounded.AutoAwesome,
                        accent = EduPurple,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "مجموع النقاط",
                        value = "${summary.totalPoints}",
                        subtitle = "XP مكتسبة",
                        icon = Icons.Rounded.Bolt,
                        accent = EduOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    EduPanel(
        modifier = modifier,
        contentPadding = PaddingValues(13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = EduMuted
            )
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = EduInk
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = EduMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextRecommendedStepCard(
    lesson: LessonRef,
    coverPath: String?,
    showStudioAction: Boolean,
    onOpenStudentStudy: () -> Unit,
    onPreviewPdf: (() -> Unit)?,
    onOpenInStudio: () -> Unit
) {
    EduPanel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EduMint.copy(alpha = 0.55f),
        borderColor = EduGreen.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BookCoverImage(
                coverPath = coverPath,
                bookTitle = lesson.bookTitle,
                subjectKey = lesson.subjectKey,
                cornerRadius = 10.dp,
                targetWidthPx = 220,
                modifier = Modifier.size(width = 48.dp, height = 64.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "خطوتك التالية في مسار التعلّم",
                    style = MaterialTheme.typography.labelMedium,
                    color = EduGreenDark
                )
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                Text(
                    text = "${lesson.bookTitle} • ${lesson.unitTitle}" +
                        (lesson.pageRange?.let { " • ص $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onOpenStudentStudy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EduYellow,
                    contentColor = EduInk
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_student_study_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.School,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "ابدأ التعلّم والمذاكرة",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (onPreviewPdf != null) {
                OutlinedButton(
                    onClick = onPreviewPdf,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EduGreen),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_next_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = EduGreenDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "عرض PDF",
                        style = MaterialTheme.typography.labelLarge,
                        color = EduGreenDark
                    )
                }
            }
            if (showStudioAction) {
                OutlinedButton(
                    onClick = onOpenInStudio,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EduLine),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_continue_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = EduMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "توليد في Studio",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk
                    )
                }
            }
        }
    }
}

@Composable
private fun WeakConceptsReinforcementCard(
    weakConcepts: List<ConceptMasterySummary>,
    onLaunchConceptReinforcement: (String?) -> Unit
) {
    EduPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_weak_concepts_card"),
        containerColor = EduOrange.copy(alpha = 0.08f),
        borderColor = EduOrange.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = EduOrange.copy(alpha = 0.18f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = EduOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "مفاهيم تحتاج تقوية ومراجعة (${weakConcepts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = EduInk,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "رُصدت من إجاباتك المتكررة أو المتذبذبة في الاختبارات",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                }
            }
            OutlinedButton(
                onClick = { onLaunchConceptReinforcement(null) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduOrange),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("تقوية شاملة", style = MaterialTheme.typography.labelSmall, color = EduOrange)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            weakConcepts.take(4).forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EduWhite,
                    border = BorderStroke(1.dp, EduLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = EduRed,
                            modifier = Modifier.size(17.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.conceptTag,
                                style = MaterialTheme.typography.labelLarge,
                                color = EduInk,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${item.wrongCount} خطأ • ${item.correctCount} صحيح • دقة ${item.accuracyPercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                        }
                        Button(
                            onClick = { onLaunchConceptReinforcement(item.conceptTag) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EduPurple,
                                contentColor = EduWhite
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reinforce_concept_${item.conceptTag}")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("تدريب", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookmarkedLessonsStrip(
    lessons: List<LessonRef>,
    onOpenStudentStudy: (LessonRef) -> Unit,
    onPreviewLessonPdf: (LessonRef) -> Unit
) {
    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = null,
                tint = EduOrange,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "الدروس المعلّمة للمتابعة السريعة (${lessons.size})",
                style = MaterialTheme.typography.titleSmall,
                color = EduInk
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            lessons.take(6).forEach { l ->
                EduPill(
                    text = l.title,
                    active = false,
                    onClick = { onOpenStudentStudy(l) },
                    testTag = "bookmark_pill_${l.lessonKey}"
                )
                if (!l.pdfPath.isNullOrBlank()) {
                    EduPill(
                        text = "PDF",
                        active = true,
                        onClick = { onPreviewLessonPdf(l) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyStreakCard(
    currentStreakDays: Int,
    longestStreakDays: Int
) {
    val goalDays = 5
    val goalReached = maxOf(currentStreakDays, longestStreakDays) >= goalDays
    val filledDays = currentStreakDays.coerceIn(0, goalDays)

    EduPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_study_streak_card"),
        containerColor = if (goalReached) EduMint.copy(alpha = 0.45f) else EduWhite,
        borderColor = if (goalReached) EduGreen else EduOrange.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (currentStreakDays > 0) EduOrange.copy(alpha = 0.16f) else EduBg,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (currentStreakDays > 0) EduOrange else EduMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "سلسلة الدراسة المتواصلة",
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                Text(
                    text = if (goalReached) {
                        "أنجزت هدف الـ 5 أيام! (+75 XP مفتوحة)"
                    } else {
                        "تبقّى ${goalDays - filledDays} أيام لفتح شارة مثابرة 5 أيام"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EduOrange.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "$currentStreakDays / $goalDays أيام",
                    style = MaterialTheme.typography.labelMedium,
                    color = EduInk,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (day in 1..goalDays) {
                val active = day <= filledDays
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) EduOrange.copy(alpha = 0.18f) else EduBg,
                    border = BorderStroke(1.dp, if (active) EduOrange else EduLine)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (active) Icons.Rounded.Check else Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = if (active) EduOrange else EduMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "يوم $day",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) EduInk else EduMuted
                        )
                    }
                }
            }
        }

        Text(
            text = "السلسلة الحالية: $currentStreakDays يوم • أطول سلسلة: $longestStreakDays يوم",
            style = MaterialTheme.typography.labelSmall,
            color = EduMuted
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesAndMilestonesSection(
    badges: List<AchievementBadge>,
    unlockedCount: Int,
    selectedCategory: BadgeCategory?,
    onSelectCategory: (BadgeCategory?) -> Unit,
    onBadgeClick: (AchievementBadge) -> Unit
) {
    val filteredBadges = remember(badges, selectedCategory) {
        val list = if (selectedCategory == null) badges else badges.filter { it.category == selectedCategory }
        list.sortedByDescending { it.unlocked }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_badges_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = EduOrange,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.dashboard_badges_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EduYellow.copy(alpha = 0.28f)
            ) {
                Text(
                    text = "$unlockedCount / ${badges.size} مفتوحة",
                    style = MaterialTheme.typography.labelMedium,
                    color = EduInk,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            EduPill(
                text = "الكل (${badges.size})",
                active = selectedCategory == null,
                onClick = { onSelectCategory(null) },
                testTag = "badge_filter_all"
            )
            BadgeCategory.entries.forEach { cat ->
                val count = badges.count { it.category == cat }
                EduPill(
                    text = "${cat.labelAr} ($count)",
                    active = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    testTag = "badge_filter_${cat.name.lowercase()}"
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 700.dp) 4 else 2
            val rows = filteredBadges.chunked(columns)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowBadges ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowBadges.forEach { badge ->
                            AchievementBadgeCard(
                                badge = badge,
                                onClick = { onBadgeClick(badge) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - rowBadges.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementBadgeCard(
    badge: AchievementBadge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val progressRatio = (badge.currentProgress.toFloat() / badge.targetProgress.coerceAtLeast(1)).coerceIn(0f, 1f)
    val badgeIcon = when {
        !badge.unlocked -> Icons.Outlined.Lock
        badge.category == BadgeCategory.STREAK -> Icons.Rounded.LocalFireDepartment
        badge.category == BadgeCategory.CREATION -> Icons.Rounded.AutoAwesome
        badge.category == BadgeCategory.MILESTONE -> Icons.Rounded.Star
        else -> Icons.Outlined.EmojiEvents
    }
    val accent = when (badge.category) {
        BadgeCategory.STREAK -> EduOrange
        BadgeCategory.CREATION -> EduPurple
        BadgeCategory.MILESTONE -> EduGreen
        BadgeCategory.MASTERY -> EduBlue
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("badge_card_${badge.id}"),
        shape = shape,
        color = if (badge.unlocked) EduWhite else EduFieldSurfaceMuted(),
        border = BorderStroke(1.2.dp, if (badge.unlocked) EduYellow else EduLine)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (badge.unlocked) accent.copy(alpha = 0.18f) else EduLine,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = if (badge.unlocked) "${badge.title} مفتوحة" else "${badge.title} مقفلة",
                            tint = if (badge.unlocked) accent else EduMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (badge.unlocked) EduMint else EduBg
                ) {
                    Text(
                        text = if (badge.unlocked) "+${badge.pointsBonus} XP ✓" else "+${badge.pointsBonus} XP",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badge.unlocked) EduGreenDark else EduMuted,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = badge.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (badge.unlocked) EduInk else EduMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badge.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                ) {
                    drawRoundRect(
                        color = EduLine,
                        cornerRadius = CornerRadius(6f, 6f),
                        size = size
                    )
                    if (progressRatio > 0f) {
                        drawRoundRect(
                            color = if (badge.unlocked) EduGreen else accent.copy(alpha = 0.7f),
                            cornerRadius = CornerRadius(6f, 6f),
                            size = Size(width = size.width * progressRatio, height = size.height)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = badge.category.labelAr,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badge.unlocked) accent else EduMuted
                    )
                    Text(
                        text = "${badge.currentProgress}/${badge.targetProgress}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badge.unlocked) EduGreenDark else EduMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeDetailDialog(
    badge: AchievementBadge,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (badge.unlocked) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = if (badge.unlocked) EduOrange else EduMuted
                )
                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (badge.unlocked) EduMint else EduBg
                ) {
                    Text(
                        text = if (badge.unlocked) "شارة مفتوحة ✓ • +${badge.pointsBonus} XP" else "شارة مقفلة • المكافأة +${badge.pointsBonus} XP",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (badge.unlocked) EduGreenDark else EduMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Text(
                    text = badge.requirementLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EduInk
                )
                Text(
                    text = "التقدّم الحالي: ${badge.currentProgress} من ${badge.targetProgress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.alert_ok),
                    color = EduGreenDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun EduFieldSurfaceMuted(): Color = EduBg.copy(alpha = 0.7f)

@Composable
private fun UnitCompletedCelebrationBanner(
    unitTitle: String,
    onDismiss: () -> Unit
) {
    EduPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_unit_celebration_banner"),
        containerColor = EduMint,
        borderColor = EduGreen
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = EduGreen,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = null,
                        tint = EduWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "إنجاز رائع! اكتملت وحدة «$unitTitle»",
                    style = MaterialTheme.typography.titleSmall,
                    color = EduGreenDark,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "اجتزت جميع اختبارات دروس هذه الوحدة بنجاح.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduInk
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dismiss_unit_celebration_button")
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "إغلاق التنبيه", tint = EduGreenDark)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitStatusSummaryStrip(
    completedUnits: Int,
    inProgressUnits: Int,
    notStartedUnits: Int,
    allCollapsed: Boolean,
    onToggleExpandAll: () -> Unit
) {
    EduPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_unit_status_strip"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        containerColor = EduWhite
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                UnitStatChip(label = "مكتملة: $completedUnits", containerColor = EduMint, textColor = EduGreenDark)
                UnitStatChip(label = "قيد الدراسة: $inProgressUnits", containerColor = EduOrange.copy(alpha = 0.14f), textColor = EduInk)
                UnitStatChip(label = "لم تبدأ: $notStartedUnits", containerColor = EduBg, textColor = EduMuted)
            }
            TextButton(
                onClick = onToggleExpandAll,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_toggle_all_units")
            ) {
                Icon(
                    imageVector = if (allCollapsed) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess,
                    contentDescription = null,
                    tint = EduPurple,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (allCollapsed) "توسيع الكل" else "طي الكل",
                    style = MaterialTheme.typography.labelSmall,
                    color = EduPurple
                )
            }
        }
    }
}

@Composable
private fun UnitStatChip(
    label: String,
    containerColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun UnitPathHeaderCard(
    unitPath: LearningUnitPath,
    coverPath: String?,
    subjectKey: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val (statusContainer, statusTextColor) = when (unitPath.status) {
        UnitCompletionStatus.COMPLETED -> EduMint to EduGreenDark
        UnitCompletionStatus.IN_PROGRESS -> EduOrange.copy(alpha = 0.16f) to EduInk
        UnitCompletionStatus.NOT_STARTED -> EduWhite to EduMuted
    }
    val cardBg = when (unitPath.status) {
        UnitCompletionStatus.COMPLETED -> EduMint.copy(alpha = 0.55f)
        UnitCompletionStatus.IN_PROGRESS -> EduLilac
        UnitCompletionStatus.NOT_STARTED -> EduLilac.copy(alpha = 0.72f)
    }
    val cardBorder = when (unitPath.status) {
        UnitCompletionStatus.COMPLETED -> EduGreen.copy(alpha = 0.5f)
        UnitCompletionStatus.IN_PROGRESS -> EduPurple.copy(alpha = 0.35f)
        UnitCompletionStatus.NOT_STARTED -> EduPurple.copy(alpha = 0.2f)
    }
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(shape)
            .minimumInteractiveComponentSize()
            .clickable(onClick = onToggleExpand)
            .testTag("unit_header_${unitPath.unitKey}"),
        shape = shape,
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BookCoverImage(
                    coverPath = coverPath,
                    bookTitle = unitPath.bookTitle,
                    subjectKey = subjectKey,
                    cornerRadius = 8.dp,
                    targetWidthPx = 140,
                    modifier = Modifier.size(width = 34.dp, height = 46.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = unitPath.unitTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (unitPath.status == UnitCompletionStatus.COMPLETED) EduGreenDark else EduPurple
                    )
                    Text(
                        text = buildString {
                            append(unitPath.bookTitle)
                            if (unitPath.questionsCount > 0) append(" • ${unitPath.questionsCount} سؤال")
                            if (unitPath.generatedContentCount > 0) append(" • ${unitPath.generatedContentCount} مورد")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusContainer
                    ) {
                        Text(
                            text = unitPath.status.labelAr,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusTextColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${unitPath.completedCount}/${unitPath.totalCount} (${unitPath.completionPercent}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = EduInk
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "طي الوحدة" else "توسيع الوحدة",
                            tint = EduPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            val unitRatio = (unitPath.completionPercent / 100f).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            ) {
                drawRoundRect(
                    color = EduWhite.copy(alpha = 0.8f),
                    cornerRadius = CornerRadius(8f, 8f),
                    size = size
                )
                if (unitRatio > 0f) {
                    drawRoundRect(
                        color = if (unitPath.status == UnitCompletionStatus.COMPLETED) EduGreen else EduPurple,
                        cornerRadius = CornerRadius(8f, 8f),
                        size = Size(width = size.width * unitRatio, height = size.height)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearningPathNodeRow(
    stepNumber: Int,
    isLast: Boolean,
    node: LearningLessonNode,
    showStudioAction: Boolean,
    onOpenStudentStudy: () -> Unit,
    onPreviewPdf: (() -> Unit)?,
    onOpenInStudio: () -> Unit
) {
    val (nodeColor, borderColor, bgColor) = when (node.status) {
        LearningNodeStatus.COMPLETED -> Triple(EduGreen, EduGreen, EduMint.copy(alpha = 0.4f))
        LearningNodeStatus.READY -> Triple(EduPurple, EduPurple.copy(alpha = 0.55f), EduWhite)
        LearningNodeStatus.IN_PROGRESS -> Triple(EduOrange, EduOrange.copy(alpha = 0.6f), EduWhite)
        LearningNodeStatus.NOT_STARTED -> Triple(EduMuted, EduLine, EduWhite)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(34.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (node.isCompleted) EduGreen else EduWhite,
                border = BorderStroke(2.dp, nodeColor),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (node.isCompleted) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "مكتمل",
                            tint = EduWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = stepNumber.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = nodeColor
                        )
                    }
                }
            }
            if (!isLast) {
                Canvas(
                    modifier = Modifier
                        .width(3.dp)
                        .height(92.dp)
                ) {
                    drawLine(
                        color = if (node.isCompleted) EduGreen else EduLine,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        EduPanel(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 2.dp),
            containerColor = bgColor,
            borderColor = borderColor
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.lesson.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = EduInk
                    )
                    val details = buildString {
                        append(node.status.labelAr)
                        node.lesson.pageRange?.let { append(" • ص $it") }
                        if (node.questionsCount > 0) append(" • ${node.questionsCount} سؤال")
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EduYellow.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "${node.pointsEarned}/${node.maxPossiblePoints} XP",
                        style = MaterialTheme.typography.labelSmall,
                        color = EduInk,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (node.hasGeneratedMaterials || node.quizAttemptedCount > 0) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (node.draftsCount > 0) {
                        LessonMetaChip(
                            icon = Icons.Rounded.Description,
                            label = "ملخص/شرح (${node.draftsCount})",
                            tint = EduBlue
                        )
                    }
                    if (node.packagesCount > 0) {
                        LessonMetaChip(
                            icon = Icons.Rounded.Code,
                            label = "تفاعلي (${node.packagesCount})",
                            tint = EduPurple
                        )
                    }
                    if (node.questionsCount > 0) {
                        LessonMetaChip(
                            icon = Icons.Rounded.Quiz,
                            label = "اختبار: ${node.questionsCount} سؤال",
                            tint = EduGreenDark
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onOpenStudentStudy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EduGreen,
                        contentColor = EduWhite
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("path_study_${node.lesson.lessonKey}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (node.isCompleted) "مراجعة الدرس" else "دراسة الدرس والاختبار",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (onPreviewPdf != null) {
                    OutlinedButton(
                        onClick = onPreviewPdf,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EduGreen),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("path_pdf_${node.lesson.lessonKey}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = EduGreenDark,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PDF",
                            style = MaterialTheme.typography.labelMedium,
                            color = EduGreenDark
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        node.isCompleted -> EduMint
                        node.quizAttemptedCount > 0 -> EduOrange.copy(alpha = 0.14f)
                        else -> EduBg
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            node.isCompleted -> EduGreen
                            node.quizAttemptedCount > 0 -> EduOrange
                            else -> EduLine
                        }
                    )
                ) {
                    Text(
                        text = when {
                            node.isCompleted -> "مكتمل بالاختبار ✓"
                            node.quizAttemptedCount > 0 -> "أعد الاختبار (${EduMiniRepository.LESSON_QUIZ_PASS_PERCENT}%+)"
                            else -> "يُكمل باجتياز الاختبار"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            node.isCompleted -> EduGreenDark
                            node.quizAttemptedCount > 0 -> EduInk
                            else -> EduMuted
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                if (showStudioAction) {
                    OutlinedButton(
                        onClick = onOpenInStudio,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EduLine),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("path_open_studio_${node.lesson.lessonKey}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "تحرير في الاستوديو",
                            tint = EduMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = EduMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonMetaChip(
    icon: ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DashboardEmptyOnboarding(
    showAdminLibraryButton: Boolean,
    onBindDirectFolder: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = EduInk
        )
        Text(
            text = stringResource(R.string.dashboard_empty_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = EduMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        EduYellowButton(
            text = stringResource(R.string.bind_direct_folder_cta),
            onClick = onBindDirectFolder,
            icon = Icons.Rounded.FolderOpen,
            testTag = "dashboard_bind_folder_button"
        )
        if (showAdminLibraryButton) {
            OutlinedButton(
                onClick = onOpenLibrary,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EduLine),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Text(
                    text = stringResource(R.string.nav_library),
                    color = EduInk,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
