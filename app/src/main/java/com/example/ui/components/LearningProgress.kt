package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.BookProgressSummary
import com.example.model.BookRef
import com.example.model.LessonRef
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduWhite
import kotlin.math.roundToInt

data class LearningProgressSnapshot(
    val totalBooks: Int,
    val totalLessons: Int,
    val completedLessons: Int,
    val overallCompletionPercent: Int,
    val books: List<BookProgressSummary>
)

object LearningProgressCalculator {

    fun calculateCompletionPercent(completedCount: Int, totalCount: Int): Int {
        if (totalCount <= 0 || completedCount <= 0) return 0
        return ((completedCount.coerceAtMost(totalCount) * 100f) / totalCount)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun buildSnapshot(
        books: List<BookRef>,
        lessons: List<LessonRef>,
        completedLessonKeys: Set<Triple<String, String, String>>,
        preparedLessonKeys: Set<Triple<String, String, String>> = emptySet()
    ): LearningProgressSnapshot {
        val lessonsByBook = lessons.groupBy { it.bookKey }
        val bookSummaries = books.map { book ->
            val bookLessons = lessonsByBook[book.bookKey].orEmpty()
            val total = bookLessons.size.coerceAtLeast(book.lessonsCount)
            val completed = bookLessons.count {
                Triple(it.bookKey, it.unitKey, it.lessonKey) in completedLessonKeys
            }
            val prepared = bookLessons.count {
                val key = Triple(it.bookKey, it.unitKey, it.lessonKey)
                key in completedLessonKeys || key in preparedLessonKeys
            }
            BookProgressSummary(
                book = book,
                completedLessons = completed,
                preparedLessons = prepared,
                totalLessons = total,
                completionPercent = calculateCompletionPercent(completed, total),
                preparationPercent = calculateCompletionPercent(prepared, total),
                earnedPoints = completed * 50
            )
        }
        val totalLessons = bookSummaries.sumOf { it.totalLessons }
        val completedLessons = bookSummaries.sumOf { it.completedLessons }
        return LearningProgressSnapshot(
            totalBooks = books.size,
            totalLessons = totalLessons,
            completedLessons = completedLessons,
            overallCompletionPercent = calculateCompletionPercent(completedLessons, totalLessons),
            books = bookSummaries
        )
    }
}

@Composable
fun RadialProgressIndicator(
    percentage: Int,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 7.dp,
    trackColor: Color = EduBg,
    progressColors: List<Color> = listOf(EduGreen, EduBlue),
    centerTextColor: Color = EduInk,
    label: String? = null
) {
    val clamped = percentage.coerceIn(0, 100)
    val animatedSweep by animateFloatAsState(
        targetValue = (clamped / 100f) * 360f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "radial_progress_sweep"
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "نسبة الإنجاز $clamped%"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (animatedSweep > 0.5f) {
                drawArc(
                    brush = Brush.sweepGradient(progressColors + progressColors.first()),
                    startAngle = -90f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$clamped%",
                style = if (size >= 72.dp) {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                } else {
                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                },
                color = centerTextColor
            )
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = EduMuted
                )
            }
        }
    }
}

@Composable
fun LearningProgress(
    bookProgress: List<BookProgressSummary>,
    modifier: Modifier = Modifier,
    selectedBookKey: String? = null,
    onSelectBook: ((String) -> Unit)? = null
) {
    val overall = remember(bookProgress) {
        val total = bookProgress.sumOf { it.totalLessons }
        val completed = bookProgress.sumOf { it.completedLessons }
        val prepared = bookProgress.sumOf { it.preparedLessons }
        Triple(
            LearningProgressCalculator.calculateCompletionPercent(completed, total),
            completed,
            total
        ) to prepared
    }

    val (overallPercent, completedLessons, totalLessons) = overall.first
    val preparedLessons = overall.second

    EduPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("learning_progress_component")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RadialProgressIndicator(
                percentage = overallPercent,
                size = 84.dp,
                strokeWidth = 8.dp,
                progressColors = listOf(EduGreen, EduPurple),
                label = "الإنجاز",
                modifier = Modifier.testTag("learning_progress_overall_radial")
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = EduGreenDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "تقدّم التعلّم في الكتب المستوردة",
                        style = MaterialTheme.typography.titleSmall,
                        color = EduInk
                    )
                }
                Text(
                    text = "مكتمل $completedLessons من $totalLessons درس (${bookProgress.size} كتاب)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EduInk
                )
                val subtitle = if (onSelectBook != null) {
                    "جاهز أو مُعدّ: $preparedLessons درس • اختر كتاباً لعرض دروسه"
                } else {
                    "جاهز أو مُعدّ: $preparedLessons درس • مؤشر التقدّم الدائري لكل كتاب"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
        }

        if (bookProgress.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 2.dp)
            ) {
                items(
                    items = bookProgress,
                    key = { "radial_${it.book.bookKey}" }
                ) { item ->
                    BookRadialProgressCard(
                        summary = item,
                        isSelected = selectedBookKey == item.book.bookKey,
                        onClick = if (onSelectBook != null) {
                            { onSelectBook(item.book.bookKey) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun BookRadialProgressCard(
    summary: BookProgressSummary,
    isSelected: Boolean,
    onClick: (() -> Unit)?
) {
    val shape = RoundedCornerShape(14.dp)
    val baseModifier = Modifier
        .width(152.dp)
        .testTag("book_radial_card_${summary.book.bookKey}")
        .clip(shape)
        .let { mod ->
            if (onClick != null) {
                mod.minimumInteractiveComponentSize().clickable(onClick = onClick)
            } else {
                mod
            }
        }

    Surface(
        modifier = baseModifier,
        shape = shape,
        color = if (isSelected) EduMint.copy(alpha = 0.55f) else EduBg.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, if (isSelected) EduGreen else EduLine)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BookCoverImage(
                    coverPath = summary.book.coverPath,
                    bookTitle = summary.book.title,
                    subjectKey = summary.book.subjectKey,
                    cornerRadius = 8.dp,
                    targetWidthPx = 180,
                    modifier = Modifier.size(width = 42.dp, height = 56.dp)
                )
                RadialProgressIndicator(
                    percentage = summary.completionPercent,
                    size = 62.dp,
                    strokeWidth = 6.dp,
                    trackColor = EduWhite,
                    progressColors = if (summary.completionPercent >= 100) {
                        listOf(EduGreenDark, EduGreen)
                    } else {
                        listOf(EduGreen, EduBlue)
                    }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MenuBook,
                    contentDescription = null,
                    tint = EduGreenDark,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = summary.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${summary.completedLessons} / ${summary.totalLessons} درس",
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )
        }
    }
}
