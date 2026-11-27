package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.workspace.BookIndexCoverageSummary
import com.example.data.workspace.BookIndexDraftEditor
import com.example.data.workspace.BookIndexIntakeDraft
import com.example.data.workspace.BookMetadataMapper
import com.example.data.workspace.IndexSourceLayout
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookIndexCoverageSummaryCard(
    draft: BookIndexIntakeDraft,
    summary: BookIndexCoverageSummary,
    onConfirmAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val needsBatchConfirm = summary.assignedUnits < summary.totalUnits ||
        summary.assignedLessons < summary.totalLessons ||
        draft.sourceLayoutEvidence.isBlank()

    EduPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("review_coverage_summary_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.review_parts_summary_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${summary.coveredPagesCount} / ${summary.totalPdfPages} ص",
                style = MaterialTheme.typography.labelLarge,
                color = EduGreenDark,
                fontWeight = FontWeight.ExtraBold
            )
        }

        LinearProgressIndicator(
            progress = { summary.coverageFraction },
            modifier = Modifier.fillMaxWidth(),
            color = if (summary.invalidPageLessonsCount > 0) EduOrange else EduGreen,
            trackColor = EduLine
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SummaryMetricBadge(
                label = "P01: ${summary.p01Units} وحدة",
                containerColor = EduMint,
                contentColor = EduGreenDark
            )
            SummaryMetricBadge(
                label = "P02: ${summary.p02Units} وحدة",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = EduPurple
            )
            SummaryMetricBadge(
                label = "مؤكد: ${summary.assignedUnits}/${summary.totalUnits} وحدة • ${summary.assignedLessons}/${summary.totalLessons} درس",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = EduInk
            )
            if (summary.unassignedUnits > 0) {
                SummaryMetricBadge(
                    label = "دون جزء: ${summary.unassignedUnits}",
                    containerColor = EduOrange.copy(alpha = 0.16f),
                    contentColor = EduInk
                )
            }
        }

        if (summary.overlappingRanges.isNotEmpty()) {
            Surface(
                color = EduOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduOrange.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    summary.overlappingRanges.forEach { warning ->
                        Text(
                            text = "• $warning",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduInk
                        )
                    }
                }
            }
        }

        if (draft.units.isNotEmpty() && needsBatchConfirm) {
            OutlinedButton(
                onClick = onConfirmAll,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("review_confirm_all_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = null,
                    tint = EduGreenDark,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.review_confirm_all_cta),
                    color = EduGreenDark,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricBadge(
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookMetadataReviewCard(
    draft: BookIndexIntakeDraft,
    onUpdateDraft: ((BookIndexIntakeDraft) -> BookIndexIntakeDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val grades = remember { BookMetadataMapper.allGrades() }
    val subjects = remember { BookMetadataMapper.allSubjects() }
    val components = remember { BookMetadataMapper.allComponents() }
    val identityValid = remember(draft.gradeKey, draft.subjectKey, draft.componentKey) {
        BookMetadataMapper.isValidBookIdentity(draft.gradeKey, draft.subjectKey, draft.componentKey)
    }

    EduPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("review_book_metadata_card")
    ) {
        Text(
            text = stringResource(R.string.review_section_book_meta),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        ReviewTextField(
            label = "عنوان الكتاب",
            value = draft.bookTitle,
            onValueChange = { title ->
                onUpdateDraft { BookIndexDraftEditor.updateBookMetadata(it, bookTitle = title) }
            },
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
                        val resolvedName = BookMetadataMapper.gradeDisplayName(
                            clean,
                            it.gradeName.takeIf { _ -> BookMetadataMapper.normalizeGradeKey(clean) == null }
                        ) ?: it.gradeName
                        BookIndexDraftEditor.updateBookMetadata(it, gradeKey = clean, gradeName = resolvedName)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_grade_key_input")
            )
            ReviewTextField(
                label = "اسم الصف",
                value = draft.gradeName,
                onValueChange = { name ->
                    onUpdateDraft { BookIndexDraftEditor.updateBookMetadata(it, gradeName = name) }
                },
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("review_grade_name_input")
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
                            BookIndexDraftEditor.updateBookMetadata(
                                it,
                                gradeKey = gKey,
                                gradeName = grades[gKey].orEmpty()
                            )
                        }
                    },
                    testTag = "review_grade_pill_$gKey"
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
                        val resolvedName = BookMetadataMapper.subjectDisplayName(clean) ?: it.subjectName
                        BookIndexDraftEditor.updateBookMetadata(it, subjectKey = clean, subjectName = resolvedName)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_subject_key_input")
            )
            ReviewTextField(
                label = "اسم المادة",
                value = draft.subjectName,
                onValueChange = { name ->
                    onUpdateDraft { BookIndexDraftEditor.updateBookMetadata(it, subjectName = name) }
                },
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("review_subject_name_input")
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
                            BookIndexDraftEditor.updateBookMetadata(it, subjectKey = sKey, subjectName = sName)
                        }
                    },
                    testTag = "review_subject_pill_$sKey"
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
                onClick = {
                    onUpdateDraft { BookIndexDraftEditor.updateBookMetadata(it, componentKey = null) }
                },
                testTag = "review_component_pill_none"
            )
            components.forEach { (cKey, cName) ->
                EduPill(
                    text = "$cKey ($cName)",
                    active = draft.componentKey.equals(cKey, ignoreCase = true),
                    onClick = {
                        onUpdateDraft { BookIndexDraftEditor.updateBookMetadata(it, componentKey = cKey) }
                    },
                    testTag = "review_component_pill_$cKey"
                )
            }
        }
        if (!identityValid) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = EduOrange,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "تنبيه: التركيبة (${draft.gradeKey} / ${draft.subjectKey}${draft.componentKey?.let { " / $it" } ?: ""}) غير متوافقة مع كتالوج Indexer2 القياسي؛ تحقق قبل الاعتماد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduOrange
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceLayoutAndPartsCard(
    draft: BookIndexIntakeDraft,
    onUpdateDraft: ((BookIndexIntakeDraft) -> BookIndexIntakeDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    EduPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("review_layout_parts_card")
    ) {
        Text(
            text = stringResource(R.string.review_section_layout_parts),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
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
                    onUpdateDraft { BookIndexDraftEditor.setSourceLayout(it, IndexSourceLayout.SINGLE_PART_FILE) }
                },
                testTag = "review_layout_single_part_pill"
            )
            EduPill(
                text = "ملف مدمج يحتوي الجزأين (BOTH)",
                active = draft.sourceLayout == IndexSourceLayout.MERGED_PARTS_FILE,
                onClick = {
                    onUpdateDraft { BookIndexDraftEditor.setSourceLayout(it, IndexSourceLayout.MERGED_PARTS_FILE) }
                },
                testTag = "review_layout_merged_parts_pill"
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
                            onUpdateDraft { BookIndexDraftEditor.setSinglePartKey(it, part) }
                        },
                        testTag = "review_single_part_pill_$part"
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
            onValueChange = { ev ->
                onUpdateDraft { it.copy(sourceLayoutEvidence = ev, reviewerConfirmed = false) }
            },
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
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("review_fill_evidence_button")
            ) {
                Text("تعبئة دليل قياسي من المراجع", color = EduGreenDark)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("إزاحة الصفحات (Offset)", style = MaterialTheme.typography.labelLarge, color = EduInk)
                Text(
                    text = "مثال: ص 1 مطبوعة → ص ${1 + draft.offset} PDF",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            OutlinedButton(
                onClick = { onUpdateDraft { BookIndexDraftEditor.adjustOffset(it, -1) } },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("review_offset_minus_button")
            ) { Text("-") }
            ReviewSignedNumberField(
                label = "Offset",
                value = draft.offset,
                onValueChange = { newOffset ->
                    onUpdateDraft { BookIndexDraftEditor.setOffset(it, newOffset) }
                },
                modifier = Modifier
                    .width(96.dp)
                    .testTag("review_offset_input")
            )
            OutlinedButton(
                onClick = { onUpdateDraft { BookIndexDraftEditor.adjustOffset(it, 1) } },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("review_offset_plus_button")
            ) { Text("+") }
        }
    }
}

@Composable
fun ReviewTextField(
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
fun ReviewOptionalIntField(
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
fun ReviewSignedNumberField(
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
