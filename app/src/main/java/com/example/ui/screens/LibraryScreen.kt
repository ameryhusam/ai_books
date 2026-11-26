package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.workspace.UriIO
import com.example.model.BookProgressSummary
import com.example.model.BookRef
import com.example.model.CloudDraft
import com.example.model.ImportedWorkspace
import com.example.model.LessonProgress
import com.example.model.LessonRef
import com.example.model.LibraryPartFilter
import com.example.model.SyncUiState
import com.example.model.WorkspaceSummary
import com.example.ui.components.BookCoverImage
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.components.LearningProgress
import com.example.ui.theme.EduFieldBg
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    workspace: ImportedWorkspace?,
    workspaces: List<WorkspaceSummary>,
    selectedBookKey: String,
    selectedLesson: LessonRef?,
    activeProvidersCount: Int,
    diskUsageBytes: Long = 0L,
    bookProgress: List<BookProgressSummary> = emptyList(),
    cloudDraftsState: SyncUiState<List<CloudDraft>> = SyncUiState.Loading,
    cloudProgressState: SyncUiState<List<LessonProgress>> = SyncUiState.Loading,
    onSyncWorkspaceToCloud: () -> Unit = {},
    onBindDirectFolder: () -> Unit = {},
    onRefreshIndex: () -> Unit = {},
    onPickZip: () -> Unit = {},
    onPickStandalonePdf: () -> Unit = {},
    onSelectWorkspace: (String) -> Unit,
    onDeleteWorkspace: (String, String) -> Unit = { _, _ -> },
    onSelectBook: (String) -> Unit,
    onPreviewBookCover: (BookRef) -> Unit = {},
    onOpenLesson: (LessonRef) -> Unit,
    onOpenStudentStudy: (LessonRef) -> Unit = {},
    onPreviewPdf: (String, String, String?) -> Unit = { _, _, _ -> },
    onOpenGlobalSearch: () -> Unit = {},
    onExportWorkspace: () -> Unit = {},
    onExportBackupJson: () -> Unit = {},
    onRestoreBackupJson: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var partFilter by remember { mutableStateOf(LibraryPartFilter.ALL) }
    var confirmDeleteWorkspace by remember { mutableStateOf(false) }

    val filteredLessons = remember(workspace, selectedBookKey, searchQuery, partFilter) {
        val q = searchQuery.trim()
        workspace?.lessons?.filter { lesson ->
            val matchesBook = lesson.bookKey == selectedBookKey
            val matchesPart = when (partFilter) {
                LibraryPartFilter.ALL -> true
                LibraryPartFilter.P01 -> lesson.partKey.equals("P01", ignoreCase = true)
                LibraryPartFilter.P02 -> lesson.partKey.equals("P02", ignoreCase = true)
                LibraryPartFilter.WITH_PDF -> !lesson.pdfPath.isNullOrBlank()
            }
            val matchesQuery = q.isEmpty() ||
                lesson.title.contains(q, ignoreCase = true) ||
                lesson.unitTitle.contains(q, ignoreCase = true) ||
                lesson.lessonKey.contains(q, ignoreCase = true)
            matchesBook && matchesPart && matchesQuery
        }.orEmpty()
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
                LibraryHeroCard(
                    onBindDirectFolder = onBindDirectFolder,
                    onPickStandalonePdf = onPickStandalonePdf
                )
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

            item(key = "cloud_sync_bar") {
                EduPanel(modifier = Modifier.fillMaxWidth()) {
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
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = EduGreenDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.cloud_sync_heading),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EduInk
                                )
                                val statusText = when {
                                    cloudDraftsState is SyncUiState.Error -> cloudDraftsState.message
                                    cloudDraftsState is SyncUiState.Success && cloudProgressState is SyncUiState.Success ->
                                        "${cloudDraftsState.data.size} مسودة • ${cloudProgressState.data.size} تقدم محفوظ سحابياً"
                                    else -> "جارٍ المزامنة مع Firestore..."
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (cloudDraftsState is SyncUiState.Error) EduRed else EduMuted
                                )
                            }
                        }
                        if (workspace != null) {
                            TextButton(
                                onClick = onSyncWorkspaceToCloud,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("library_sync_cloud_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    tint = EduGreenDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مزامنة", color = EduGreenDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (workspace != null && bookProgress.isNotEmpty()) {
                item(key = "learning_progress") {
                    LearningProgress(
                        bookProgress = bookProgress,
                        selectedBookKey = selectedBookKey,
                        onSelectBook = onSelectBook
                    )
                }
            }

            if (workspaces.size > 1) {
                item(key = "workspace_switcher") {
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.workspaces_switcher_heading),
                            style = MaterialTheme.typography.titleSmall,
                            color = EduInk
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            workspaces.forEach { ws ->
                                EduPill(
                                    text = ws.title,
                                    active = ws.workspaceId == workspace?.workspaceId,
                                    onClick = { onSelectWorkspace(ws.workspaceId) },
                                    testTag = "ws_pill_${ws.workspaceId}"
                                )
                            }
                        }
                    }
                }
            }

            if (workspace != null) {
                item(key = "workspace_summary") {
                    val diskLabel = remember(diskUsageBytes) {
                        val mb = diskUsageBytes / (1024.0 * 1024.0)
                        if (mb >= 1.0) String.format("%.1f MB", mb) else "${(diskUsageBytes / 1024L).coerceAtLeast(1L)} KB"
                    }
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                                val isDirectWorkspace = remember(workspace.workspaceId, workspace.rootPath) {
                                    workspace.workspaceId.startsWith("direct-") || UriIO.isContentUri(workspace.rootPath)
                                }
                                Text(
                                    text = if (isDirectWorkspace) {
                                        "${workspace.gradeKey} • ${workspace.books.size} كتاب • ${workspace.lessons.size} درس • ${workspace.filesCount} ملف • ربط مباشر محلي (بدون نسخ)"
                                    } else {
                                        "${workspace.gradeKey} • ${workspace.filesCount} ملف • الحجم على القرص: $diskLabel"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = onOpenGlobalSearch,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("library_global_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = "بحث شامل",
                                        tint = EduGreenDark
                                    )
                                }
                                IconButton(
                                    onClick = { confirmDeleteWorkspace = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("delete_workspace_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "حذف مساحة العمل",
                                        tint = EduRed
                                    )
                                }
                            }
                        }
                        if (workspace.workspaceId.startsWith("direct-") || UriIO.isContentUri(workspace.rootPath)) {
                            OutlinedButton(
                                onClick = onRefreshIndex,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EduGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("refresh_workspace_index_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = EduGreenDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "مزامنة الفهرس من مساحة التخزين",
                                    color = EduGreenDark,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
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
                        OutlinedButton(
                            onClick = onExportWorkspace,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EduGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("export_workspace_zip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileDownload,
                                contentDescription = null,
                                tint = EduGreenDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تصدير حزمة متوافقة مع indexer2 (ZIP)",
                                color = EduGreenDark,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onExportBackupJson,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EduLine),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_backup_json_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Backup,
                                    contentDescription = null,
                                    tint = EduInk,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ احتياطي JSON", color = EduInk, style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = onRestoreBackupJson,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EduLine),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("restore_backup_json_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restore,
                                    contentDescription = null,
                                    tint = EduGreenDark,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("استعادة JSON", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
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
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectBook(book.bookKey)
                        },
                        onPreviewCover = { onPreviewBookCover(book) },
                        onOpenSourcePdf = book.sourcePdfPath?.let { pdf ->
                            { onPreviewPdf(pdf, book.title, "${book.subjectName ?: book.subjectKey} • الكتاب الكامل") }
                        }
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
                                text = stringResource(R.string.empty_books_hint),
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

            if (workspace != null && workspace.lessons.isNotEmpty()) {
                item(key = "lessons_search_filter") {
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = EduMuted)
                            },
                            placeholder = {
                                Text("ابحث عن درس أو وحدة...", style = MaterialTheme.typography.bodyMedium, color = EduMuted)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = EduFieldBg,
                                unfocusedContainerColor = EduFieldBg,
                                focusedBorderColor = EduGreen,
                                unfocusedBorderColor = EduLine,
                                focusedTextColor = EduInk,
                                unfocusedTextColor = EduInk
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_lesson_search")
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            LibraryPartFilter.entries.forEach { filter ->
                                EduPill(
                                    text = filter.labelAr,
                                    active = partFilter == filter,
                                    onClick = { partFilter = filter },
                                    testTag = "library_filter_${filter.name.lowercase()}"
                                )
                            }
                        }
                    }
                }
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
                        onClick = { onOpenLesson(item) },
                        onOpenStudentStudy = { onOpenStudentStudy(item) },
                        onPreviewPdf = item.pdfPath?.takeIf { it.isNotBlank() }?.let { pdf ->
                            {
                                val metaSubtitle = listOfNotNull(
                                    item.bookTitle,
                                    item.unitTitle,
                                    item.pageRange?.let { "ص $it" }
                                ).joinToString(" • ")
                                onPreviewPdf(pdf, item.title, metaSubtitle)
                            }
                        }
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

    if (confirmDeleteWorkspace && workspace != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteWorkspace = false },
            containerColor = EduWhite,
            title = {
                Text("حذف مساحة العمل؟", style = MaterialTheme.typography.titleMedium, color = EduInk)
            },
            text = {
                Text(
                    text = "سيتم حذف «${workspace.title}» وجميع المسودات والأسئلة والملفات المحلية الخاصة بها نهائياً.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EduInk
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteWorkspace = false
                        onDeleteWorkspace(workspace.workspaceId, workspace.rootPath)
                    }
                ) {
                    Text("حذف نهائي", color = EduRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteWorkspace = false }) {
                    Text("إلغاء", color = EduMuted)
                }
            }
        )
    }
}

@Composable
private fun LibraryHeroCard(
    onBindDirectFolder: () -> Unit,
    onPickStandalonePdf: () -> Unit
) {
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
                modifier = Modifier.matchParentSize()
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                Spacer(modifier = Modifier.height(2.dp))
                EduYellowButton(
                    text = stringResource(R.string.bind_direct_folder_cta),
                    onClick = onBindDirectFolder,
                    icon = Icons.Rounded.FolderOpen,
                    testTag = "bind_direct_folder_button"
                )
                OutlinedButton(
                    onClick = onPickStandalonePdf,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EduWhite.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("open_standalone_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = "عرض ملف PDF محلي",
                        tint = EduWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "عرض ملف PDF محلي مباشرة",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookItemCard(
    book: BookRef,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPreviewCover: () -> Unit,
    onOpenSourcePdf: (() -> Unit)?
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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BookCoverImage(
                coverPath = book.coverPath,
                bookTitle = book.title,
                subjectKey = book.subjectKey,
                cornerRadius = 10.dp,
                targetWidthPx = 220,
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clickable(onClick = onPreviewCover)
                    .testTag("book_cover_${book.bookKey}")
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk
                )
                val subtitle = buildString {
                    append(book.subjectName ?: book.subjectKey)
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (book.coverPath != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EduMint,
                            modifier = Modifier.clickable(onClick = onPreviewCover)
                        ) {
                            Text(
                                text = "cover.png",
                                style = MaterialTheme.typography.labelSmall,
                                color = EduGreenDark,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (onOpenSourcePdf != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EduLilac,
                            modifier = Modifier.clickable(onClick = onOpenSourcePdf)
                        ) {
                            Text(
                                text = "PDF الكتاب",
                                style = MaterialTheme.typography.labelSmall,
                                color = EduPurple,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onPreviewCover,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("book_preview_cover_${book.bookKey}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Preview,
                    contentDescription = "معاينة غلاف الكتاب",
                    tint = EduGreenDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "الكتاب المختار",
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
    onClick: () -> Unit,
    onOpenStudentStudy: () -> Unit,
    onPreviewPdf: (() -> Unit)?
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            if (onPreviewPdf != null) {
                IconButton(
                    onClick = onPreviewPdf,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lesson_pdf_${lesson.lessonKey}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = "عرض ملف PDF للدرس",
                        tint = EduPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = onOpenStudentStudy,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.School,
                    contentDescription = "معاينة وضع الطالب",
                    tint = EduGreenDark,
                    modifier = Modifier.size(20.dp)
                )
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
