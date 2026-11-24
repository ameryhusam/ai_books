package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AchievementBadge
import com.example.model.BadgeCategory
import com.example.model.DashboardSummary
import com.example.model.LearningLessonNode
import com.example.model.LearningNodeStatus
import com.example.model.LessonRef
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.components.LearningProgress
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
import com.example.ui.theme.EduWhite
import com.example.ui.theme.EduYellow

@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    onOpenLessonInStudio: (LessonRef) -> Unit,
    onToggleLessonCompleted: (LessonRef, Boolean) -> Unit,
    onPickBookPdf: () -> Unit,
    onPickZip: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBadge by remember { mutableStateOf<AchievementBadge?>(null) }
    var selectedCategory by remember { mutableStateOf<BadgeCategory?>(null) }

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
                DashboardHeroCard(summary = summary)
            }

            if (!summary.hasWorkspace) {
                item(key = "dashboard_empty") {
                    DashboardEmptyOnboarding(
                        onPickBookPdf = onPickBookPdf,
                        onPickZip = onPickZip,
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
                            onOpenInStudio = { onOpenLessonInStudio(nextLesson) }
                        )
                    }
                }

                item(key = "kpi_grid") {
                    DashboardKpiGrid(summary = summary)
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
                        LearningProgress(bookProgress = summary.bookProgress)
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

                item(key = "learning_path_heading") {
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
                            text = "${summary.completedLessons}/${summary.totalLessons} مكتمل",
                            style = MaterialTheme.typography.labelMedium,
                            color = EduGreenDark
                        )
                    }
                }

                summary.unitPaths.forEach { unitPath ->
                    item(key = "unit_header_${unitPath.unitKey}") {
                        UnitPathHeaderCard(
                            unitTitle = unitPath.unitTitle,
                            bookTitle = unitPath.bookTitle,
                            completedCount = unitPath.completedCount,
                            totalCount = unitPath.totalCount,
                            completionPercent = unitPath.completionPercent
                        )
                    }
                    itemsIndexed(
                        items = unitPath.nodes,
                        key = { _, node -> "path_${node.lesson.bookKey}_${node.lesson.unitKey}_${node.lesson.lessonKey}" }
                    ) { idx, node ->
                        LearningPathNodeRow(
                            stepNumber = idx + 1,
                            isLast = idx == unitPath.nodes.lastIndex,
                            node = node,
                            onToggleCompleted = {
                                onToggleLessonCompleted(node.lesson, node.isCompleted)
                            },
                            onOpenInStudio = { onOpenLessonInStudio(node.lesson) }
                        )
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
private fun DashboardHeroCard(summary: DashboardSummary) {
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

@Composable
private fun NextRecommendedStepCard(
    lesson: LessonRef,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EduGreen,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = EduWhite
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "خطوتك التالية في المسار",
                    style = MaterialTheme.typography.labelMedium,
                    color = EduGreenDark
                )
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                Text(
                    text = "${lesson.bookTitle} • ${lesson.unitTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            Button(
                onClick = onOpenInStudio,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EduYellow,
                    contentColor = EduInk
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_continue_studio_button")
            ) {
                Text(
                    text = "ابدأ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
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
private fun UnitPathHeaderCard(
    unitTitle: String,
    bookTitle: String,
    completedCount: Int,
    totalCount: Int,
    completionPercent: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = EduLilac,
        border = BorderStroke(1.dp, EduPurple.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = unitTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduPurple
                )
                Text(
                    text = bookTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = EduWhite
            ) {
                Text(
                    text = "$completedCount/$totalCount • $completionPercent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = EduPurple,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
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
    onToggleCompleted: () -> Unit,
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
                        .height(86.dp)
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
                        if (node.draftsCount > 0) append(" • ${node.draftsCount} مسودة")
                        if (node.packagesCount > 0) append(" • ${node.packagesCount} حزمة")
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleCompleted,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (node.isCompleted) EduGreen else EduLine),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_complete_${node.lesson.lessonKey}")
                ) {
                    Text(
                        text = if (node.isCompleted) "مكتمل ✓" else "تحديد كمكتمل (+50 XP)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (node.isCompleted) EduGreenDark else EduInk
                    )
                }
                Button(
                    onClick = onOpenInStudio,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EduGreen,
                        contentColor = EduWhite
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("path_open_studio_${node.lesson.lessonKey}")
                ) {
                    Text(
                        text = "فتح في الاستوديو",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardEmptyOnboarding(
    onPickBookPdf: () -> Unit,
    onPickZip: () -> Unit,
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
            text = stringResource(R.string.split_book_pdf_cta),
            onClick = onPickBookPdf,
            icon = Icons.Rounded.PictureAsPdf,
            testTag = "dashboard_split_pdf_button"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPickZip,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EduGreen),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
            ) {
                Text(
                    text = stringResource(R.string.import_zip_cta),
                    color = EduGreenDark,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            OutlinedButton(
                onClick = onOpenLibrary,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EduLine),
                modifier = Modifier
                    .weight(1f)
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
