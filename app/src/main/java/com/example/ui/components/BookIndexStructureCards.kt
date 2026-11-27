package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.workspace.BookIndexDraftEditor
import com.example.data.workspace.BookIndexIntakeDraft
import com.example.data.workspace.BookIndexLessonDraft
import com.example.data.workspace.BookIndexUnitDraft
import com.example.data.workspace.IndexAssignmentSource
import com.example.data.workspace.IndexAssignmentStatus
import com.example.data.workspace.IndexSourceLayout
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitReviewCard(
    unitIndex: Int,
    unitCount: Int,
    unit: BookIndexUnitDraft,
    draft: BookIndexIntakeDraft,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUpdateDraft: ((BookIndexIntakeDraft) -> BookIndexIntakeDraft) -> Unit,
    onRemoveUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unitAssigned = unit.assignmentStatus == IndexAssignmentStatus.ASSIGNED &&
        unit.partKey in setOf("P01", "P02") &&
        unit.assignmentEvidence.isNotBlank() &&
        unit.assignmentSource in setOf(IndexAssignmentSource.INDEX_EVIDENCE, IndexAssignmentSource.REVIEWER)

    EduPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("review_unit_card_$unitIndex"),
        borderColor = if (unitAssigned) EduLine else EduOrange.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (unit.partKey in setOf("P01", "P02")) EduMint else EduOrange.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = unit.partKey ?: "؟",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unit.partKey in setOf("P01", "P02")) EduGreenDark else EduOrange,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "وحدة ${unitIndex + 1}: ${unit.title.ifBlank { unit.unitKey }}",
                        style = MaterialTheme.typography.titleSmall,
                        color = EduInk,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${unit.unitKey} • ${unit.lessons.size} درس • ${if (unitAssigned) "مؤكد" else "يحتاج مراجعة"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unitAssigned) EduGreenDark else EduOrange
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = unitIndex > 0,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("review_unit_move_up_$unitIndex")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(R.string.review_move_up_cd),
                        tint = if (unitIndex > 0) EduInk else EduMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = unitIndex < unitCount - 1,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("review_unit_move_down_$unitIndex")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = stringResource(R.string.review_move_down_cd),
                        tint = if (unitIndex < unitCount - 1) EduInk else EduMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("review_unit_expand_$unitIndex")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.review_collapse_unit_cd else R.string.review_expand_unit_cd
                        ),
                        tint = EduInk
                    )
                }
                IconButton(
                    onClick = onRemoveUnit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("review_unit_delete_$unitIndex")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.review_delete_unit_cd),
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
                        onValueChange = { k ->
                            onUpdateDraft { BookIndexDraftEditor.updateUnit(it, unitIndex) { u -> u.copy(unitKey = k) } }
                        },
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("review_unit_key_input_$unitIndex")
                    )
                    ReviewTextField(
                        label = "عنوان الوحدة",
                        value = unit.title,
                        onValueChange = { t ->
                            onUpdateDraft { BookIndexDraftEditor.updateUnit(it, unitIndex) { u -> u.copy(title = t) } }
                        },
                        modifier = Modifier
                            .weight(1.6f)
                            .testTag("review_unit_title_input_$unitIndex")
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val allowedParts = if (draft.sourceLayout == IndexSourceLayout.SINGLE_PART_FILE) {
                        listOfNotNull(draft.partKey?.takeIf { it in setOf("P01", "P02") }).ifEmpty { listOf("P01", "P02") }
                    } else {
                        listOf("P01", "P02")
                    }
                    allowedParts.forEach { part ->
                        EduPill(
                            text = "جزء $part",
                            active = unit.partKey == part,
                            onClick = {
                                onUpdateDraft { BookIndexDraftEditor.assignUnitPart(it, unitIndex, part) }
                            },
                            testTag = "review_unit_part_${unitIndex}_$part"
                        )
                    }
                    val allConfirmed = unit.assignmentStatus == IndexAssignmentStatus.ASSIGNED &&
                        unit.lessons.all { it.assignmentStatus == IndexAssignmentStatus.ASSIGNED }
                    EduPill(
                        text = if (allConfirmed) "مؤكد (assigned)" else "تأكيد الوحدة والدروس",
                        active = allConfirmed,
                        onClick = {
                            onUpdateDraft { BookIndexDraftEditor.confirmUnitAndLessons(it, unitIndex) }
                        },
                        testTag = "review_unit_confirm_$unitIndex"
                    )
                }

                ReviewTextField(
                    label = "دليل إسناد الوحدة (assignmentEvidence)",
                    value = unit.assignmentEvidence,
                    onValueChange = { ev ->
                        onUpdateDraft {
                            BookIndexDraftEditor.updateUnit(it, unitIndex) { u -> u.copy(assignmentEvidence = ev) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("review_unit_evidence_input_$unitIndex")
                )

                unit.lessons.forEachIndexed { lessonIdx, lesson ->
                    LessonReviewCard(
                        unitIndex = unitIndex,
                        lessonIdx = lessonIdx,
                        lessonCount = unit.lessons.size,
                        lesson = lesson,
                        draft = draft,
                        onMoveUp = {
                            onUpdateDraft {
                                BookIndexDraftEditor.moveLesson(it, unitIndex, lessonIdx, lessonIdx - 1)
                            }
                        },
                        onMoveDown = {
                            onUpdateDraft {
                                BookIndexDraftEditor.moveLesson(it, unitIndex, lessonIdx, lessonIdx + 1)
                            }
                        },
                        onUpdateLesson = { updatedLesson ->
                            onUpdateDraft {
                                BookIndexDraftEditor.updateLesson(it, unitIndex, lessonIdx) { updatedLesson }
                            }
                        },
                        onRemoveLesson = {
                            onUpdateDraft {
                                BookIndexDraftEditor.removeLesson(it, unitIndex, lessonIdx)
                            }
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        onUpdateDraft { BookIndexDraftEditor.addLesson(it, unitIndex) }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, EduGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("review_unit_add_lesson_$unitIndex")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.review_add_lesson_cta),
                        color = EduGreenDark,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun LessonReviewCard(
    unitIndex: Int,
    lessonIdx: Int,
    lessonCount: Int,
    lesson: BookIndexLessonDraft,
    draft: BookIndexIntakeDraft,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUpdateLesson: (BookIndexLessonDraft) -> Unit,
    onRemoveLesson: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startPhysical = draft.physicalStartPage(lesson)
    val endPhysical = draft.physicalEndPage(lesson)
    val pagesValid = startPhysical != null && endPhysical != null &&
        startPhysical >= 1 && endPhysical >= startPhysical && endPhysical <= draft.totalPdfPages

    Surface(
        color = EduFieldBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (pagesValid) EduLine else EduRed.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("review_lesson_card_${unitIndex}_$lessonIdx")
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
                    modifier = Modifier.weight(1f),
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
                    IconButton(
                        onClick = onMoveUp,
                        enabled = lessonIdx > 0,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("review_lesson_move_up_${unitIndex}_$lessonIdx")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(R.string.review_move_up_cd),
                            tint = if (lessonIdx > 0) EduInk else EduMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = lessonIdx < lessonCount - 1,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("review_lesson_move_down_${unitIndex}_$lessonIdx")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = stringResource(R.string.review_move_down_cd),
                            tint = if (lessonIdx < lessonCount - 1) EduInk else EduMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                        },
                        testTag = "review_lesson_confirm_${unitIndex}_$lessonIdx"
                    )
                    IconButton(
                        onClick = onRemoveLesson,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("review_lesson_delete_${unitIndex}_$lessonIdx")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.review_delete_lesson_cd),
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
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("review_lesson_key_input_${unitIndex}_$lessonIdx")
                )
                ReviewTextField(
                    label = "عنوان الدرس",
                    value = lesson.title,
                    onValueChange = { t -> onUpdateLesson(lesson.copy(title = t)) },
                    modifier = Modifier
                        .weight(1.6f)
                        .testTag("review_lesson_title_input_${unitIndex}_$lessonIdx")
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val widePagesRow = maxWidth >= 420.dp
                if (widePagesRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PrintedStartField(lesson, onUpdateLesson, Modifier.weight(1f))
                        PrintedEndField(lesson, onUpdateLesson, Modifier.weight(1f))
                        PdfStartField(lesson, startPhysical, onUpdateLesson, Modifier.weight(1f))
                        PdfEndField(lesson, endPhysical, onUpdateLesson, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PrintedStartField(lesson, onUpdateLesson, Modifier.weight(1f))
                            PrintedEndField(lesson, onUpdateLesson, Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PdfStartField(lesson, startPhysical, onUpdateLesson, Modifier.weight(1f))
                            PdfEndField(lesson, endPhysical, onUpdateLesson, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintedStartField(
    lesson: BookIndexLessonDraft,
    onUpdateLesson: (BookIndexLessonDraft) -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
    )
}

@Composable
private fun PrintedEndField(
    lesson: BookIndexLessonDraft,
    onUpdateLesson: (BookIndexLessonDraft) -> Unit,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier
    )
}

@Composable
private fun PdfStartField(
    lesson: BookIndexLessonDraft,
    startPhysical: Int?,
    onUpdateLesson: (BookIndexLessonDraft) -> Unit,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier
    )
}

@Composable
private fun PdfEndField(
    lesson: BookIndexLessonDraft,
    endPhysical: Int?,
    onUpdateLesson: (BookIndexLessonDraft) -> Unit,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier
    )
}
