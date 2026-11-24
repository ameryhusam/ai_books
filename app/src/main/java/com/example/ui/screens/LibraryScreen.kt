package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BookRef
import com.example.model.ImportedWorkspace
import com.example.model.LessonRef
import com.example.ui.components.EduPanel
import com.example.ui.components.EduYellowButton
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

@Composable
fun LibraryScreen(
    workspace: ImportedWorkspace?,
    selectedBookKey: String,
    selectedLesson: LessonRef?,
    activeProvidersCount: Int,
    onPickZip: () -> Unit,
    onSelectBook: (String) -> Unit,
    onOpenLesson: (LessonRef) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredLessons = remember(workspace, selectedBookKey) {
        workspace?.lessons?.filter { it.bookKey == selectedBookKey }.orEmpty()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero") {
                LibraryHeroCard(onPickZip = onPickZip)
            }

            item(key = "stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        value = workspace?.gradeKey ?: "—",
                        label = stringResource(R.string.stat_grade_label),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = (workspace?.lessons?.size ?: 0).toString(),
                        label = stringResource(R.string.stat_lessons_label),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = activeProvidersCount.toString(),
                        label = stringResource(R.string.stat_active_api_label),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (workspace != null) {
                item(key = "workspace_summary") {
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.current_workspace_heading),
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk
                        )
                        Text(
                            text = workspace.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = EduInk
                        )
                        Text(
                            text = "${workspace.gradeKey} • ${workspace.filesCount} ملف",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted
                        )
                        if (workspace.warnings.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EduOrange.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = EduOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = workspace.warnings.joinToString("\n"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduInk
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "books_heading") {
                Text(
                    text = stringResource(R.string.books_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (workspace != null && workspace.books.isNotEmpty()) {
                items(
                    items = workspace.books,
                    key = { "book_${it.bookKey}" }
                ) { book ->
                    BookItemCard(
                        book = book,
                        isSelected = selectedBookKey == book.bookKey,
                        onClick = { onSelectBook(book.bookKey) }
                    )
                }
            } else {
                item(key = "empty_books") {
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderZip,
                                contentDescription = null,
                                tint = EduGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "لم يتم استيراد كتب بعد. اضغط على استيراد Workspace ZIP للبدء.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduMuted
                            )
                        }
                    }
                }
            }

            item(key = "lessons_heading") {
                Text(
                    text = stringResource(R.string.lessons_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (filteredLessons.isNotEmpty()) {
                items(
                    items = filteredLessons,
                    key = { "lesson_${it.bookKey}_${it.unitKey}_${it.lessonKey}" }
                ) { item ->
                    LessonItemCard(
                        lesson = item,
                        isSelected = selectedLesson?.bookKey == item.bookKey &&
                            selectedLesson.unitKey == item.unitKey &&
                            selectedLesson.lessonKey == item.lessonKey,
                        onClick = { onOpenLesson(item) }
                    )
                }
            } else {
                item(key = "empty_lessons") {
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MenuBook,
                                contentDescription = null,
                                tint = EduMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_lessons_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeroCard(onPickZip: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = EduGreen
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_library_banner),
                contentDescription = stringResource(R.string.cd_hero_banner),
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
                modifier = Modifier
                    .matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                EduGreenDark.copy(alpha = 0.35f),
                                EduGreen.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = stringResource(R.string.hero_kicker),
                    style = MaterialTheme.typography.labelSmall,
                    color = EduHeroKicker
                )
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = EduWhite
                )
                Text(
                    text = stringResource(R.string.hero_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EduHeroSub
                )
                Spacer(modifier = Modifier.height(4.dp))
                EduYellowButton(
                    text = stringResource(R.string.import_zip_cta),
                    onClick = onPickZip,
                    icon = Icons.Rounded.Add,
                    testTag = "import_zip_button"
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    EduPanel(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold
            ),
            color = EduGreen,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = EduMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BookItemCard(
    book: BookRef,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("book_card_${book.bookKey}")
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (isSelected) EduMint else EduWhite,
        border = BorderStroke(1.dp, if (isSelected) EduGreen else EduLine)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                val subtitle = buildString {
                    append(book.subjectKey)
                    if (!book.componentKey.isNullOrBlank()) {
                        append(" • ")
                        append(book.componentKey)
                    }
                    append(" • ")
                    append(book.lessonsCount)
                    append(" درس")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = EduGreenDark,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = EduMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun LessonItemCard(
    lesson: LessonRef,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lesson_card_${lesson.lessonKey}")
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (isSelected) EduMint else EduWhite,
        border = BorderStroke(1.dp, if (isSelected) EduGreen else EduLine)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(11.dp),
                color = EduLilac
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = lesson.lessonKey,
                        style = MaterialTheme.typography.labelSmall,
                        color = EduPurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                val meta = buildString {
                    append(lesson.bookTitle)
                    append(" • ")
                    append(lesson.unitTitle)
                    if (!lesson.partKey.isNullOrBlank()) {
                        append(" • ")
                        append(lesson.partKey)
                    }
                    if (!lesson.pageRange.isNullOrBlank()) {
                        append(" • ")
                        append(lesson.pageRange)
                    }
                    append(if (!lesson.pdfPath.isNullOrBlank()) " • PDF" else " • PDF غير موجود")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!lesson.pdfPath.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = EduGreenDark,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = EduMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
