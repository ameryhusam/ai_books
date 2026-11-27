package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.workspace.BookIndexIntakeDraft
import com.example.data.workspace.BookIndexLessonDraft
import com.example.data.workspace.BookIndexUnitDraft
import com.example.data.workspace.BookMetadataMapper
import com.example.data.workspace.IndexAssignmentSource
import com.example.data.workspace.IndexAssignmentStatus
import com.example.data.workspace.IndexSourceLayout
import com.example.ui.BookIndexReviewUiState
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookIndexReviewDialog(
    state: BookIndexReviewUiState,
    isBusy: Boolean,
    progressStage: String? = null,
    progressPercent: Int? = null,
    onUpdateDraft: ((BookIndexIntakeDraft) -> BookIndexIntakeDraft) -> Unit,
    onRetryAiDiscovery: () -> Unit,
    onPopulateStarterUnits: () -> Unit,
    onPreviewPdf: () -> Unit,
    onApproveAndSplit: () -> Unit,
    onDismiss: () -> Unit
) {
    val draft = state.draft
    val validationErrors = remember(draft) { draft.validationErrors() }
    val canApprove = validationErrors.isEmpty() && !isBusy && !state.isDiscovering
    val expandedUnits = remember { mutableStateMapOf<String, Boolean>() }

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(22.dp),
            color = EduBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = EduGreen, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مراجعة واعتماد فهرس الكتاب",
                                style = MaterialTheme.typography.titleMedium,
                                color = EduWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.pdfInfo.fileName} • ${state.pdfInfo.totalPages} صفحة • ${state.aiModel ?: draft.discoveryMethod.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduWhite.copy(alpha = 0.9f)
                            )
                        }
                        IconButton(
                            onClick = { if (!isBusy) onDismiss() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("book_index_review_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "إغلاق مراجعة الفهرس",
                                tint = EduWhite
                            )
                        }
                    }
                }

                if (isBusy || state.isDiscovering) {
                    Surface(color = EduWhite, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = progressStage?.takeIf { it.isNotBlank() }
                                    ?: if (state.isDiscovering) "جارٍ تحليل صفحات الفهرس بالذكاء الاصطناعي..."
                                    else "جارٍ الحفظ في Room وتقطيع PDF...",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduGreenDark,
                                fontWeight = FontWeight.Medium
                            )
                            val pct = progressPercent
                            if (pct != null && pct in 0..100) {
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = EduGreen,
                                    trackColor = EduLine
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = EduGreen,
                                    trackColor = EduLine
                                )
                            }
                        }
                    }
                }

                // Scrollable editor body
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    // Quick tools bar (Preview PDF, Re-run AI Discovery, Starter Split)
                    item(key = "tools_bar") {
                        EduPanel(modifier = Modifier.fillMaxWidth()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPreviewPdf,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, EduGreen),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("review_preview_pdf_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PictureAsPdf,
                                        contentDescription = null,
                                        tint = EduGreenDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("معاينة صفحات PDF", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = onRetryAiDiscovery,
                                    enabled = !isBusy && !state.isDiscovering,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, EduGreen),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("review_retry_ai_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = EduGreenDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إعادة الاكتشاف بـ AI", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = onPopulateStarterUnits,
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, EduLine),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("review_starter_splits_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Splitscreen,
                                        contentDescription = null,
                                        tint = EduInk,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تقسيم مبدئي (5 ص/درس)", color = EduInk, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (state.discoveryError != null) {
                                Surface(
                                    color = EduOrange.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, EduOrange.copy(alpha = 0.45f)),
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
                                            text = state.discoveryError,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EduInk
                                        )
                                    }
                                }
                            }
                            if (draft.reviewNotes.isNotBlank()) {
                                Text(
                                    text = "ملاحظات الاكتشاف: ${draft.reviewNotes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                            }
                        }
                    }

                    // 1. Book Metadata Editor
                    item(key = "book_metadata_editor") {
                        BookMetadataReviewCard(
                            draft = draft,
                            onUpdateDraft = onUpdateDraft
                        )
                    }

                    // 2. Source Layout, Part Key & Offset Editor
                    item(key = "layout_offset_editor") {
                        SourceLayoutAndOffsetCard(
                            draft = draft,
                            onUpdateDraft = onUpdateDraft
                        )
                    }

                    // 3. Units Header
                    item(key = "units_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "الوحدات والدروس (${draft.units.size} وحدة • ${draft.units.sumOf { it.lessons.size }} درس)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EduInk,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "راجع إسناد الجزء (P01 / P02) وأرقام الصفحات المطبوعة أو PDF لكل درس",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onUpdateDraft { current ->
                                        val nextIndex = current.units.size + 1
                                        val newKey = "unit_${nextIndex.toString().padStart(2, '0')}"
                                        val defaultPart = when (current.sourceLayout) {
                                            IndexSourceLayout.SINGLE_PART_FILE -> current.partKey ?: "P01"
                                            else -> "P01"
                                        }
                                        val newUnit = BookIndexUnitDraft(
                                            unitKey = newKey,
                                            title = "الوحدة $nextIndex",
                                            partKey = defaultPart,
                                            proposedPartKey = defaultPart,
                                            assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                            assignmentSource = IndexAssignmentSource.REVIEWER,
                                            assignmentEvidence = "أضيفت يدوياً بواسطة المراجع",
                                            lessons = listOf(
                                                BookIndexLessonDraft(
                                                    lessonKey = "lesson_01",
                                                    title = "الدرس الأول",
                                                    printedPageStart = 1,
                                                    printedPageEnd = 1,
                                                    assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                                    assignmentSource = IndexAssignmentSource.REVIEWER,
                                                    assignmentEvidence = "إضافة يدوية"
                                                )
                                            )
                                        )
                                        expandedUnits[newKey] = true
                                        current.copy(
                                            units = current.units + newUnit,
                                            reviewerConfirmed = false
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, EduGreen),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("review_add_unit_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إضافة وحدة", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (draft.units.isEmpty()) {
                        item(key = "empty_units") {
                            EduPanel(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "لا توجد وحدات بعد. أضف وحدة يدوياً أو استخدم «تقسيم مبدئي» أو أعد الاكتشاف بالذكاء الاصطناعي.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EduMuted
                                )
                            }
                        }
                    }

                    // Units & Lessons Cards
                    itemsIndexed(
                        items = draft.units,
                        key = { index, unit -> "${unit.unitKey}_$index" }
                    ) { unitIndex, unit ->
                        val expandKey = "${unit.unitKey}_$unitIndex"
                        val isExpanded = expandedUnits[expandKey] ?: (unitIndex == 0 || unit.assignmentStatus == IndexAssignmentStatus.NEEDS_REVIEW)
                        UnitReviewCard(
                            unitIndex = unitIndex,
                            unit = unit,
                            draft = draft,
                            expanded = isExpanded,
                            onToggleExpand = { expandedUnits[expandKey] = !isExpanded },
                            onUpdateUnit = { updatedUnit ->
                                onUpdateDraft { current ->
                                    val mutable = current.units.toMutableList()
                                    if (unitIndex in mutable.indices) {
                                        mutable[unitIndex] = updatedUnit
                                    }
                                    current.copy(units = mutable, reviewerConfirmed = false)
                                }
                            },
                            onRemoveUnit = {
                                onUpdateDraft { current ->
                                    val mutable = current.units.toMutableList()
                                    if (unitIndex in mutable.indices) mutable.removeAt(unitIndex)
                                    current.copy(units = mutable, reviewerConfirmed = false)
                                }
                            }
                        )
                    }

                    // 4. Live Validation Summary
                    if (validationErrors.isNotEmpty()) {
                        item(key = "validation_errors_card") {
                            Surface(
                                color = EduRed.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, EduRed.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "متطلبات الاعتماد المتبقية (${validationErrors.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EduRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                    validationErrors.take(8).forEach { err ->
                                        Text(
                                            text = "• $err",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EduInk
                                        )
                                    }
                                    if (validationErrors.size > 8) {
                                        Text(
                                            text = "... و${validationErrors.size - 8} ملاحظات أخرى",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EduMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(6.dp)) }
                }

                // Footer: Reviewer Confirmation + Approve & Split Button
                Surface(
                    color = EduWhite,
                    border = BorderStroke(1.dp, EduLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReviewTextField(
                            label = "معرّف المراجع المعتمد",
                            value = draft.reviewerId.orEmpty(),
                            onValueChange = { value ->
                                onUpdateDraft { it.copy(reviewerId = value) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("review_reviewer_id_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    onUpdateDraft { current ->
                                        val nextConfirmed = !current.reviewerConfirmed
                                        current.copy(
                                            reviewerConfirmed = nextConfirmed,
                                            reviewerId = current.reviewerId?.takeIf { it.isNotBlank() } ?: "admin_reviewer"
                                        )
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = draft.reviewerConfirmed,
                                onCheckedChange = { checked ->
                                    onUpdateDraft { current ->
                                        current.copy(
                                            reviewerConfirmed = checked,
                                            reviewerId = current.reviewerId?.takeIf { it.isNotBlank() } ?: "admin_reviewer"
                                        )
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = EduGreen),
                                modifier = Modifier.testTag("review_confirm_checkbox")
                            )
                            Text(
                                text = "أؤكد مراجعة بيانات الكتاب والوحدات والدروس والأجزاء وOffset واعتمادها للحفظ والتقطيع",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduInk,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        EduYellowButton(
                            text = if (isBusy) "جارٍ الحفظ في Room وتقطيع PDF..." else "اعتماد الفهرس في Room وتوليد الملفات وتقطيع PDF",
                            onClick = onApproveAndSplit,
                            enabled = canApprove,
                            icon = if (canApprove) Icons.Rounded.Verified else Icons.Rounded.AutoAwesome,
                            testTag = "review_approve_and_split_button"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookMetadataReviewCard(
    draft: BookIndexIntakeDraft,
    onUpdateDraft: ((BookIndexIntakeDraft) -> BookIndexIntakeDraft) -> Unit
) {
    val grades = remember { BookMetadataMapper.allGrades() }
    val subjects = remember { BookMetadataMapper.allSubjects() }
    val components = remember { BookMetadataMapper.allComponents() }
    val identityValid = remember(draft.gradeKey, draft.subjectKey, draft.componentKey) {
        BookMetadataMapper.isValidBookIdentity(draft.gradeKey, draft.subjectKey, draft.componentKey)
    }

    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "1. بيانات الكتاب المرجعية",
            style = MaterialTheme.typography.titleSmall,
            color = EduInk,
            fontWeight = FontWeight.Bold
        )
        ReviewTextField(
            label = "عنوان الكتاب",
            value = draft.bookTitle,
            onValueChange = { title -> onUpdateDraft { it.copy(bookTitle = title, reviewerConfirmed = false) } },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("review_book_title_input")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReviewTextField(
                label = "رمز الصف (G01..G12)",
                value = draft.gradeKey,
                onValueChange = { raw ->
                    val clean = raw.trim().uppercase()
                    onUpdateDraft {
                        it.copy(
                            gradeKey = clean,
                            gradeName = BookMetadataMapper.gradeDisplayName(clean, it.gradeName.takeIf { _ -> BookMetadataMapper.normalizeGradeKey(clean) == null })
                                ?: it.gradeName,
                            reviewerConfirmed = false
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
            ReviewTextField(
                label = "اسم الصف",
                value = draft.gradeName,
                onValueChange = { name -> onUpdateDraft { it.copy(gradeName = name, reviewerConfirmed = false) } },
                modifier = Modifier.weight(1.3f)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            grades.keys.forEach { gKey ->
                EduPill(
                    text = gKey,
                    active = draft.gradeKey.equals(gKey, ignoreCase = true),
                    onClick = {
                        onUpdateDraft {
                            it.copy(
                                gradeKey = gKey,
                                gradeName = grades[gKey].orEmpty(),
                                reviewerConfirmed = false
                            )
                        }
                    }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReviewTextField(
                label = "رمز المادة (ARAB, SCI...)",
                value = draft.subjectKey,
                onValueChange = { raw ->
                    val clean = raw.trim().uppercase()
                    onUpdateDraft {
                        it.copy(
                            subjectKey = clean,
                            subjectName = BookMetadataMapper.subjectDisplayName(clean) ?: it.subjectName,
                            reviewerConfirmed = false
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
            ReviewTextField(
                label = "اسم المادة",
                value = draft.subjectName,
                onValueChange = { name -> onUpdateDraft { it.copy(subjectName = name, reviewerConfirmed = false) } },
                modifier = Modifier.weight(1.3f)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            subjects.forEach { (sKey, sName) ->
                EduPill(
                    text = "$sKey • $sName",
                    active = draft.subjectKey.equals(sKey, ignoreCase = true),
                    onClick = {
                        onUpdateDraft {
                            it.copy(
                                subjectKey = sKey,
                                subjectName = sName,
                                reviewerConfirmed = false
                            )
                        }
                    }
                )
            }
        }
        Text(
            text = "المكوّن الاختياري (للاجتماعيات أو الإنجليزية فقط):",
            style = MaterialTheme.typography.labelSmall,
            color = EduMuted
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EduPill(
                text = "بدون مكوّن",
                active = draft.componentKey.isNullOrBlank(),
                onClick = { onUpdateDraft { it.copy(componentKey = null, reviewerConfirmed = false) } }
            )
            components.forEach { (cKey, cName) ->
                EduPill(
                    text = "$cKey ($cName)",
                    active = draft.componentKey.equals(cKey, ignoreCase = true),
                    onClick = { onUpdateDraft { it.copy(componentKey = cKey, reviewerConfirmed = false) } }
                )
            }
        }
        if (!identityValid) {
            Text(
                text = "تنبيه: التركيبة (${draft.gradeKey} / ${draft.subjectKey}${draft.componentKey?.let { " / $it" } ?: ""}) غير متوافقة مع كتالوج Indexer2 القياسي؛ تحقق قبل الاعتماد.",
                style = MaterialTheme.typography.bodySmall,
                color = EduOrange
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceLayoutAndOffsetCard(
    draft: BookIndexIntakeDraft,
    onUpdateDraft: ((BookIndexIntakeDraft) -> BookIndexIntakeDraft) -> Unit
) {
    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "2. بنية ملف PDF والأجزاء وOffset",
            style = MaterialTheme.typography.titleSmall,
            color = EduInk,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EduPill(
                text = "جزء مستقل (P01 أو P02)",
                active = draft.sourceLayout == IndexSourceLayout.SINGLE_PART_FILE,
                onClick = {
                    onUpdateDraft { current ->
                        val part = current.partKey?.takeIf { it in setOf("P01", "P02") } ?: "P01"
                        current.copy(
                            sourceLayout = IndexSourceLayout.SINGLE_PART_FILE,
                            partKey = part,
                            units = current.units.map { u -> u.copy(partKey = part) },
                            reviewerConfirmed = false
                        )
                    }
                }
            )
            EduPill(
                text = "ملف مدمج يحتوي الجزأين (BOTH)",
                active = draft.sourceLayout == IndexSourceLayout.MERGED_PARTS_FILE,
                onClick = {
                    onUpdateDraft { current ->
                        current.copy(
                            sourceLayout = IndexSourceLayout.MERGED_PARTS_FILE,
                            partKey = "BOTH",
                            reviewerConfirmed = false
                        )
                    }
                }
            )
        }

        if (draft.sourceLayout == IndexSourceLayout.SINGLE_PART_FILE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("الجزء المستقل:", style = MaterialTheme.typography.bodySmall, color = EduInk)
                listOf("P01", "P02").forEach { part ->
                    EduPill(
                        text = part,
                        active = draft.partKey == part,
                        onClick = {
                            onUpdateDraft { current ->
                                current.copy(
                                    partKey = part,
                                    units = current.units.map { u -> u.copy(partKey = part) },
                                    reviewerConfirmed = false
                                )
                            }
                        }
                    )
                }
            }
        } else if (draft.sourceLayout == IndexSourceLayout.MERGED_PARTS_FILE) {
            Text(
                text = "وضع الملف المدمج (BOTH): حدد لكل وحدة أدناه هل تنتمي إلى P01 أم P02.",
                style = MaterialTheme.typography.bodySmall,
                color = EduGreenDark
            )
        }

        ReviewTextField(
            label = "دليل تحديد بنية الملف (sourceLayoutEvidence)",
            value = draft.sourceLayoutEvidence,
            onValueChange = { ev -> onUpdateDraft { it.copy(sourceLayoutEvidence = ev, reviewerConfirmed = false) } },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("review_layout_evidence_input")
        )
        if (draft.sourceLayoutEvidence.isBlank()) {
            TextButton(
                onClick = {
                    val autoEvidence = when (draft.sourceLayout) {
                        IndexSourceLayout.SINGLE_PART_FILE -> "تحقق المراجع من أن الملف يمثل الجزء المستقل ${draft.partKey ?: "P01"}"
                        IndexSourceLayout.MERGED_PARTS_FILE -> "تحقق المراجع من أن الملف مدمج ويحتوي الجزأين P01 و P02"
                        IndexSourceLayout.UNKNOWN -> ""
                    }
                    if (autoEvidence.isNotBlank()) {
                        onUpdateDraft { it.copy(sourceLayoutEvidence = autoEvidence) }
                    }
                },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("تعبئة دليل قياسي من المراجع", color = EduGreenDark)
            }
        }

        // Offset control
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("إزاحة الصفحات (Offset)", style = MaterialTheme.typography.labelLarge, color = EduInk)
                Text(
                    text = "صفحة PDF الفعلية = المطبوعة + (${draft.offset})",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            OutlinedButton(
                onClick = { onUpdateDraft { it.copy(offset = it.offset - 1, reviewerConfirmed = false) } },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) { Text("-") }
            ReviewSignedNumberField(
                label = "Offset",
                value = draft.offset,
                onValueChange = { newOffset -> onUpdateDraft { it.copy(offset = newOffset, reviewerConfirmed = false) } },
                modifier = Modifier
                    .width(96.dp)
                    .testTag("review_offset_input")
            )
            OutlinedButton(
                onClick = { onUpdateDraft { it.copy(offset = it.offset + 1, reviewerConfirmed = false) } },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) { Text("+") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitReviewCard(
    unitIndex: Int,
    unit: BookIndexUnitDraft,
    draft: BookIndexIntakeDraft,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onUpdateUnit: (BookIndexUnitDraft) -> Unit,
    onRemoveUnit: () -> Unit
) {
    val unitAssigned = unit.assignmentStatus == IndexAssignmentStatus.ASSIGNED &&
        unit.partKey in setOf("P01", "P02") &&
        unit.assignmentEvidence.isNotBlank() &&
        unit.assignmentSource in setOf(IndexAssignmentSource.INDEX_EVIDENCE, IndexAssignmentSource.REVIEWER)

    EduPanel(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "وحدة ${unitIndex + 1}: ${unit.title.ifBlank { unit.unitKey }}",
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${unit.unitKey} • الجزء: ${unit.partKey ?: "غير محدد"} • ${unit.lessons.size} درس • ${if (unitAssigned) "مؤكد" else "يحتاج مراجعة"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unitAssigned) EduGreenDark else EduOrange
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleExpand, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "طي الوحدة" else "توسيع الوحدة",
                        tint = EduInk
                    )
                }
                IconButton(onClick = onRemoveUnit, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "حذف الوحدة",
                        tint = EduRed
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReviewTextField(
                        label = "مفتاح الوحدة",
                        value = unit.unitKey,
                        onValueChange = { k -> onUpdateUnit(unit.copy(unitKey = k)) },
                        modifier = Modifier.weight(0.9f)
                    )
                    ReviewTextField(
                        label = "عنوان الوحدة",
                        value = unit.title,
                        onValueChange = { t -> onUpdateUnit(unit.copy(title = t)) },
                        modifier = Modifier.weight(1.6f)
                    )
                }

                // Part assignment for unit
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val allowedParts = if (draft.sourceLayout == IndexSourceLayout.SINGLE_PART_FILE) {
                        listOfNotNull(draft.partKey?.takeIf { it in setOf("P01", "P02") }).ifEmpty { listOf("P01", "P02") }
                    } else {
                        listOf("P01", "P02")
                    }
                    allowedParts.forEach { p ->
                        EduPill(
                            text = "جزء $p",
                            active = unit.partKey == p,
                            onClick = {
                                onUpdateUnit(
                                    unit.copy(
                                        partKey = p,
                                        assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                        assignmentSource = IndexAssignmentSource.REVIEWER,
                                        assignmentEvidence = unit.assignmentEvidence.ifBlank {
                                            "تأكيد المراجع لإسناد الوحدة إلى $p"
                                        }
                                    )
                                )
                            }
                        )
                    }
                    EduPill(
                        text = if (unit.assignmentStatus == IndexAssignmentStatus.ASSIGNED &&
                            unit.lessons.all { it.assignmentStatus == IndexAssignmentStatus.ASSIGNED }
                        ) {
                            "مؤكد (assigned)"
                        } else {
                            "تأكيد الوحدة والدروس"
                        },
                        active = unit.assignmentStatus == IndexAssignmentStatus.ASSIGNED &&
                            unit.lessons.all { it.assignmentStatus == IndexAssignmentStatus.ASSIGNED },
                        onClick = {
                            val chosenPart = unit.partKey ?: unit.proposedPartKey ?: draft.partKey?.takeIf { it in setOf("P01", "P02") } ?: "P01"
                            onUpdateUnit(
                                unit.copy(
                                    partKey = chosenPart,
                                    assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                    assignmentSource = IndexAssignmentSource.REVIEWER,
                                    assignmentEvidence = unit.assignmentEvidence.ifBlank {
                                        "تأكيد المراجع لإسناد الوحدة إلى $chosenPart"
                                    },
                                    lessons = unit.lessons.map { lesson ->
                                        lesson.copy(
                                            assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                            assignmentSource = IndexAssignmentSource.REVIEWER
                                        )
                                    }
                                )
                            )
                        }
                    )
                }

                ReviewTextField(
                    label = "دليل إسناد الوحدة (assignmentEvidence)",
                    value = unit.assignmentEvidence,
                    onValueChange = { ev -> onUpdateUnit(unit.copy(assignmentEvidence = ev)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Lessons inside unit
                unit.lessons.forEachIndexed { lessonIdx, lesson ->
                    LessonReviewCard(
                        lessonIdx = lessonIdx,
                        lesson = lesson,
                        draft = draft,
                        onUpdateLesson = { updatedLesson ->
                            val mutableLessons = unit.lessons.toMutableList()
                            if (lessonIdx in mutableLessons.indices) {
                                mutableLessons[lessonIdx] = updatedLesson
                            }
                            onUpdateUnit(unit.copy(lessons = mutableLessons))
                        },
                        onRemoveLesson = {
                            val mutableLessons = unit.lessons.toMutableList()
                            if (lessonIdx in mutableLessons.indices) mutableLessons.removeAt(lessonIdx)
                            onUpdateUnit(unit.copy(lessons = mutableLessons))
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        val nextIdx = unit.lessons.size + 1
                        val lastEnd = unit.lessons.lastOrNull()?.let { draft.physicalEndPage(it) } ?: 0
                        val nextPage = (lastEnd + 1).coerceIn(1, draft.totalPdfPages)
                        val added = BookIndexLessonDraft(
                            lessonKey = "lesson_${nextIdx.toString().padStart(2, '0')}",
                            title = "الدرس $nextIdx",
                            pdfPageStart = nextPage,
                            pdfPageEnd = nextPage,
                            assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                            assignmentSource = IndexAssignmentSource.REVIEWER,
                            assignmentEvidence = "إضافة يدوية"
                        )
                        onUpdateUnit(unit.copy(lessons = unit.lessons + added))
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, EduGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة درس لهذه الوحدة", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LessonReviewCard(
    lessonIdx: Int,
    lesson: BookIndexLessonDraft,
    draft: BookIndexIntakeDraft,
    onUpdateLesson: (BookIndexLessonDraft) -> Unit,
    onRemoveLesson: () -> Unit
) {
    val startPhysical = draft.physicalStartPage(lesson)
    val endPhysical = draft.physicalEndPage(lesson)
    val pagesValid = startPhysical != null && endPhysical != null &&
        startPhysical >= 1 && endPhysical >= startPhysical && endPhysical <= draft.totalPdfPages

    Surface(
        color = EduFieldBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (pagesValid) EduLine else EduRed.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (pagesValid && lesson.assignmentStatus == IndexAssignmentStatus.ASSIGNED) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.WarningAmber
                        },
                        contentDescription = null,
                        tint = if (pagesValid && lesson.assignmentStatus == IndexAssignmentStatus.ASSIGNED) EduGreenDark else EduOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "درس ${lessonIdx + 1}: PDF ${startPhysical ?: "?"}–${endPhysical ?: "?"} / ${draft.totalPdfPages}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pagesValid) EduGreenDark else EduRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EduPill(
                        text = if (lesson.assignmentStatus == IndexAssignmentStatus.ASSIGNED) "معتمد" else "تأكيد",
                        active = lesson.assignmentStatus == IndexAssignmentStatus.ASSIGNED,
                        onClick = {
                            val next = if (lesson.assignmentStatus == IndexAssignmentStatus.ASSIGNED) {
                                IndexAssignmentStatus.NEEDS_REVIEW
                            } else {
                                IndexAssignmentStatus.ASSIGNED
                            }
                            onUpdateLesson(
                                lesson.copy(
                                    assignmentStatus = next,
                                    assignmentSource = IndexAssignmentSource.REVIEWER
                                )
                            )
                        }
                    )
                    IconButton(onClick = onRemoveLesson, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "حذف الدرس",
                            tint = EduRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewTextField(
                    label = "مفتاح الدرس",
                    value = lesson.lessonKey,
                    onValueChange = { k -> onUpdateLesson(lesson.copy(lessonKey = k)) },
                    modifier = Modifier.weight(0.9f)
                )
                ReviewTextField(
                    label = "عنوان الدرس",
                    value = lesson.title,
                    onValueChange = { t -> onUpdateLesson(lesson.copy(title = t)) },
                    modifier = Modifier.weight(1.6f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReviewOptionalIntField(
                    label = "مطبوع من",
                    value = lesson.printedPageStart,
                    onValueChange = { v ->
                        onUpdateLesson(
                            lesson.copy(
                                printedPageStart = v,
                                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                assignmentSource = IndexAssignmentSource.REVIEWER
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                ReviewOptionalIntField(
                    label = "مطبوع إلى",
                    value = lesson.printedPageEnd,
                    onValueChange = { v ->
                        onUpdateLesson(
                            lesson.copy(
                                printedPageEnd = v,
                                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                assignmentSource = IndexAssignmentSource.REVIEWER
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                ReviewOptionalIntField(
                    label = "PDF من",
                    value = lesson.pdfPageStart,
                    placeholder = startPhysical?.toString() ?: "",
                    onValueChange = { v ->
                        onUpdateLesson(
                            lesson.copy(
                                pdfPageStart = v,
                                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                assignmentSource = IndexAssignmentSource.REVIEWER
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                ReviewOptionalIntField(
                    label = "PDF إلى",
                    value = lesson.pdfPageEnd,
                    placeholder = endPhysical?.toString() ?: "",
                    onValueChange = { v ->
                        onUpdateLesson(
                            lesson.copy(
                                pdfPageEnd = v,
                                assignmentStatus = IndexAssignmentStatus.ASSIGNED,
                                assignmentSource = IndexAssignmentSource.REVIEWER
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReviewTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall, color = EduMuted) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = EduWhite,
            unfocusedContainerColor = EduWhite,
            focusedBorderColor = EduGreen,
            unfocusedBorderColor = EduLine,
            focusedTextColor = EduInk,
            unfocusedTextColor = EduInk
        ),
        modifier = modifier
    )
}

@Composable
private fun ReviewOptionalIntField(
    label: String,
    value: Int?,
    placeholder: String = "",
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }
            onValueChange(if (digits.isEmpty()) null else digits.toIntOrNull())
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall, color = EduMuted) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, style = MaterialTheme.typography.bodySmall, color = EduMuted) }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = EduWhite,
            unfocusedContainerColor = EduWhite,
            focusedBorderColor = EduGreen,
            unfocusedBorderColor = EduLine,
            focusedTextColor = EduInk,
            unfocusedTextColor = EduInk
        ),
        modifier = modifier
    )
}

@Composable
private fun ReviewSignedNumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw ->
            val cleaned = raw.filterIndexed { i, ch -> ch.isDigit() || (i == 0 && ch == '-') }
            cleaned.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall, color = EduMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = EduWhite,
            unfocusedContainerColor = EduWhite,
            focusedBorderColor = EduGreen,
            unfocusedBorderColor = EduLine,
            focusedTextColor = EduInk,
            unfocusedTextColor = EduInk
        ),
        modifier = modifier
    )
}
