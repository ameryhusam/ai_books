package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.workspace.BookIndexDraftEditor
import com.example.data.workspace.BookIndexIntakeDraft
import com.example.data.workspace.IndexAssignmentStatus
import com.example.ui.BookIndexReviewUiState
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite
import kotlin.math.roundToInt

/**
 * Reusable Review and Edit Jetpack Compose screen component for modifying an indexed PDF
 * workspace's metadata (book title, grade/subject/component, parts, offset, units, and lessons)
 * prior to canonical Room persistence and PDF slicing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookIndexReviewScreen(
    state: BookIndexReviewUiState,
    isBusy: Boolean,
    progressStage: String? = null,
    progressPercent: Int? = null,
    onUpdateDraft: ((BookIndexIntakeDraft) -> BookIndexIntakeDraft) -> Unit,
    onRetryAiDiscovery: () -> Unit,
    onPopulateStarterUnits: () -> Unit,
    onPreviewPdf: () -> Unit,
    onApproveAndSplit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    applySystemBarsPadding: Boolean = false
) {
    BackHandler(enabled = !isBusy) { onClose() }

    val draft = state.draft
    val validationErrors = remember(draft) { draft.validationErrors() }
    val coverageSummary = remember(draft) { BookIndexDraftEditor.summarize(draft) }
    val canApprove = validationErrors.isEmpty() && !isBusy && !state.isDiscovering
    val expandedUnits = remember { mutableStateMapOf<Int, Boolean>() }

    fun swapExpandedIndices(a: Int, b: Int) {
        val currentA = expandedUnits[a]
        val currentB = expandedUnits[b]
        if (currentB != null) expandedUnits[a] = currentB else expandedUnits.remove(a)
        if (currentA != null) expandedUnits[b] = currentA else expandedUnits.remove(b)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("book_index_review_screen")
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (applySystemBarsPadding) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.review_screen_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    val confidenceBadge = draft.discoveryConfidence?.let {
                        " • ثقة ${(it * 100).roundToInt()}%"
                    }.orEmpty()
                    Text(
                        text = "${state.pdfInfo.fileName} • ${state.pdfInfo.totalPages} صفحة • ${state.aiModel ?: draft.discoveryMethod.name}$confidenceBadge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                    )
                }
                IconButton(
                    onClick = { if (!isBusy) onClose() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("book_index_review_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.review_screen_close_cd),
                        tint = MaterialTheme.colorScheme.onPrimary
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
                            ?: if (state.isDiscovering) {
                                stringResource(R.string.review_discovering_stage)
                            } else {
                                stringResource(R.string.review_splitting_stage)
                            },
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

        // Responsive editor body: 2-pane on wide screens, unified scroll on compact/medium
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val useTwoPaneLayout = maxWidth >= 840.dp
            if (useTwoPaneLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.43f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReviewQuickToolsPanel(
                            state = state,
                            draft = draft,
                            isBusy = isBusy,
                            onPreviewPdf = onPreviewPdf,
                            onRetryAiDiscovery = onRetryAiDiscovery,
                            onPopulateStarterUnits = onPopulateStarterUnits
                        )
                        BookIndexCoverageSummaryCard(
                            draft = draft,
                            summary = coverageSummary,
                            onConfirmAll = {
                                onUpdateDraft { BookIndexDraftEditor.confirmAllUnitsAndLessons(it) }
                            }
                        )
                        BookMetadataReviewCard(draft = draft, onUpdateDraft = onUpdateDraft)
                        SourceLayoutAndPartsCard(draft = draft, onUpdateDraft = onUpdateDraft)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(0.57f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "units_header") {
                            UnitsSectionHeader(
                                draft = draft,
                                onAddUnit = {
                                    onUpdateDraft { current ->
                                        val nextIndex = current.units.size
                                        val (updated, _) = BookIndexDraftEditor.addUnit(current)
                                        expandedUnits[nextIndex] = true
                                        updated
                                    }
                                }
                            )
                        }
                        if (draft.units.isEmpty()) {
                            item(key = "empty_units") { EmptyUnitsNoticeCard() }
                        }
                        itemsIndexed(
                            items = draft.units,
                            key = { index, unit -> "${unit.unitKey}_$index" }
                        ) { unitIndex, unit ->
                            val isExpanded = expandedUnits[unitIndex]
                                ?: (unitIndex == 0 || unit.assignmentStatus == IndexAssignmentStatus.NEEDS_REVIEW)
                            UnitReviewCard(
                                unitIndex = unitIndex,
                                unitCount = draft.units.size,
                                unit = unit,
                                draft = draft,
                                expanded = isExpanded,
                                onToggleExpand = { expandedUnits[unitIndex] = !isExpanded },
                                onMoveUp = {
                                    if (unitIndex > 0) {
                                        swapExpandedIndices(unitIndex, unitIndex - 1)
                                        onUpdateDraft { BookIndexDraftEditor.moveUnit(it, unitIndex, unitIndex - 1) }
                                    }
                                },
                                onMoveDown = {
                                    if (unitIndex < draft.units.lastIndex) {
                                        swapExpandedIndices(unitIndex, unitIndex + 1)
                                        onUpdateDraft { BookIndexDraftEditor.moveUnit(it, unitIndex, unitIndex + 1) }
                                    }
                                },
                                onUpdateDraft = onUpdateDraft,
                                onRemoveUnit = {
                                    expandedUnits.remove(unitIndex)
                                    onUpdateDraft { BookIndexDraftEditor.removeUnit(it, unitIndex) }
                                }
                            )
                        }
                        if (validationErrors.isNotEmpty()) {
                            item(key = "validation_errors_card") {
                                ValidationErrorsSummaryCard(validationErrors)
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "tools_bar") {
                            ReviewQuickToolsPanel(
                                state = state,
                                draft = draft,
                                isBusy = isBusy,
                                onPreviewPdf = onPreviewPdf,
                                onRetryAiDiscovery = onRetryAiDiscovery,
                                onPopulateStarterUnits = onPopulateStarterUnits
                            )
                        }
                        item(key = "coverage_summary") {
                            BookIndexCoverageSummaryCard(
                                draft = draft,
                                summary = coverageSummary,
                                onConfirmAll = {
                                    onUpdateDraft { BookIndexDraftEditor.confirmAllUnitsAndLessons(it) }
                                }
                            )
                        }
                        item(key = "book_metadata_editor") {
                            BookMetadataReviewCard(draft = draft, onUpdateDraft = onUpdateDraft)
                        }
                        item(key = "layout_offset_editor") {
                            SourceLayoutAndPartsCard(draft = draft, onUpdateDraft = onUpdateDraft)
                        }
                        item(key = "units_header") {
                            UnitsSectionHeader(
                                draft = draft,
                                onAddUnit = {
                                    onUpdateDraft { current ->
                                        val nextIndex = current.units.size
                                        val (updated, _) = BookIndexDraftEditor.addUnit(current)
                                        expandedUnits[nextIndex] = true
                                        updated
                                    }
                                }
                            )
                        }
                        if (draft.units.isEmpty()) {
                            item(key = "empty_units") { EmptyUnitsNoticeCard() }
                        }
                        itemsIndexed(
                            items = draft.units,
                            key = { index, unit -> "${unit.unitKey}_$index" }
                        ) { unitIndex, unit ->
                            val isExpanded = expandedUnits[unitIndex]
                                ?: (unitIndex == 0 || unit.assignmentStatus == IndexAssignmentStatus.NEEDS_REVIEW)
                            UnitReviewCard(
                                unitIndex = unitIndex,
                                unitCount = draft.units.size,
                                unit = unit,
                                draft = draft,
                                expanded = isExpanded,
                                onToggleExpand = { expandedUnits[unitIndex] = !isExpanded },
                                onMoveUp = {
                                    if (unitIndex > 0) {
                                        swapExpandedIndices(unitIndex, unitIndex - 1)
                                        onUpdateDraft { BookIndexDraftEditor.moveUnit(it, unitIndex, unitIndex - 1) }
                                    }
                                },
                                onMoveDown = {
                                    if (unitIndex < draft.units.lastIndex) {
                                        swapExpandedIndices(unitIndex, unitIndex + 1)
                                        onUpdateDraft { BookIndexDraftEditor.moveUnit(it, unitIndex, unitIndex + 1) }
                                    }
                                },
                                onUpdateDraft = onUpdateDraft,
                                onRemoveUnit = {
                                    expandedUnits.remove(unitIndex)
                                    onUpdateDraft { BookIndexDraftEditor.removeUnit(it, unitIndex) }
                                }
                            )
                        }
                        if (validationErrors.isNotEmpty()) {
                            item(key = "validation_errors_card") {
                                ValidationErrorsSummaryCard(validationErrors)
                            }
                        }
                    }
                }
            }
        }

        // Sticky Footer: Reviewer confirmation + Approve & Split action
        Surface(
            color = EduWhite,
            border = BorderStroke(1.dp, EduLine),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applySystemBarsPadding) Modifier.navigationBarsPadding() else Modifier)
                .imePadding()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewTextField(
                    label = stringResource(R.string.review_reviewer_id_label),
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
                        text = stringResource(R.string.review_confirm_checkbox_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = EduInk,
                        fontWeight = FontWeight.Medium
                    )
                }
                val ctaText = when {
                    isBusy -> stringResource(R.string.review_approving_cta)
                    validationErrors.isNotEmpty() -> "أكمل ${validationErrors.size} متطلبات للاعتماد والتقطيع"
                    else -> stringResource(R.string.review_approve_split_cta)
                }
                EduYellowButton(
                    text = ctaText,
                    onClick = onApproveAndSplit,
                    enabled = canApprove,
                    icon = if (canApprove) Icons.Rounded.Verified else Icons.Rounded.AutoAwesome,
                    testTag = "review_approve_and_split_button"
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewQuickToolsPanel(
    state: BookIndexReviewUiState,
    draft: BookIndexIntakeDraft,
    isBusy: Boolean,
    onPreviewPdf: () -> Unit,
    onRetryAiDiscovery: () -> Unit,
    onPopulateStarterUnits: () -> Unit
) {
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
                Text(stringResource(R.string.review_preview_pdf_cta), color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
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
                Text(stringResource(R.string.review_retry_ai_cta), color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
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
                Text(stringResource(R.string.review_starter_split_cta), color = EduInk, style = MaterialTheme.typography.labelMedium)
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

@Composable
private fun UnitsSectionHeader(
    draft: BookIndexIntakeDraft,
    onAddUnit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "الوحدات والدروس (${draft.units.size} وحدة • ${draft.units.sumOf { it.lessons.size }} درس)",
                style = MaterialTheme.typography.titleSmall,
                color = EduInk,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "راجع إسناد الجزء (P01 / P02) والترتيب وأرقام الصفحات المطبوعة أو PDF لكل درس",
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onAddUnit,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, EduGreen),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("review_add_unit_button")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.review_add_unit_cta), color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyUnitsNoticeCard() {
    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "لا توجد وحدات بعد. أضف وحدة يدوياً أو استخدم «تقسيم مبدئي» أو أعد الاكتشاف بالذكاء الاصطناعي.",
            style = MaterialTheme.typography.bodyMedium,
            color = EduMuted
        )
    }
}

@Composable
private fun ValidationErrorsSummaryCard(validationErrors: List<String>) {
    Surface(
        color = EduRed.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, EduRed.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("review_validation_errors_card")
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
