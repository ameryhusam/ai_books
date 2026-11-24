package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.LessonSplitDraft
import com.example.ui.PdfSplitterUiState
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite

@Composable
fun PdfBookSplitterDialog(
    state: PdfSplitterUiState,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onGradeChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onBookTitleChange: (String) -> Unit,
    onPagesPerLessonChange: (Int) -> Unit,
    onUpdateSplit: (id: String, title: String, unitTitle: String, start: Int, end: Int) -> Unit,
    onAddSplit: () -> Unit,
    onRemoveSplit: (String) -> Unit,
    onCreateWorkspace: (openStudio: Boolean) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = EduBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                                text = "تجزئة كتاب PDF إلى دروس",
                                style = MaterialTheme.typography.titleMedium,
                                color = EduWhite
                            )
                            Text(
                                text = "${state.pdfInfo.fileName} • ${state.pdfInfo.totalPages} صفحة • SHA ${state.pdfInfo.sha256.take(8)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduWhite.copy(alpha = 0.9f)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "إغلاق",
                                tint = EduWhite
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(6.dp)) }

                    item(key = "pdf_preview") {
                        LessonPdfViewer(
                            pdfPath = state.pdfInfo.storedPdfPath,
                            title = "معاينة صفحات الكتاب الأصلي",
                            maxHeightDp = 240
                        )
                    }

                    item(key = "book_metadata") {
                        EduPanel(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "بيانات مساحة العمل والكتاب",
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SplitterTextField(
                                    label = "الصف (مثل G04)",
                                    value = state.gradeKey,
                                    onValueChange = onGradeChange,
                                    modifier = Modifier.weight(1f)
                                )
                                SplitterTextField(
                                    label = "رمز المادة (مثل SCI)",
                                    value = state.subjectKey,
                                    onValueChange = onSubjectChange,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SplitterTextField(
                                label = "عنوان الكتاب",
                                value = state.bookTitle,
                                onValueChange = onBookTitleChange,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "تقسيم تلقائي (${state.pagesPerLesson} صفحات/درس)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EduInk
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(3, 5, 8, 10).forEach { step ->
                                        EduPill(
                                            text = "$step",
                                            active = state.pagesPerLesson == step,
                                            onClick = { onPagesPerLessonChange(step) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "splits_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الدروس المقترحة (${state.splits.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk
                            )
                            OutlinedButton(
                                onClick = onAddSplit,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, EduGreen),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = EduGreenDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "إضافة درس",
                                    color = EduGreenDark,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = state.splits,
                        key = { _, item -> item.id }
                    ) { index, split ->
                        SplitRowCard(
                            index = index,
                            split = split,
                            canDelete = state.splits.size > 1,
                            onUpdate = { title, unitTitle, start, end ->
                                onUpdateSplit(split.id, title, unitTitle, start, end)
                            },
                            onDelete = { onRemoveSplit(split.id) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                Surface(
                    color = EduWhite,
                    border = BorderStroke(1.dp, EduLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EduYellowButton(
                            text = if (isBusy) "جاري فصل الصفحات..." else "إنشاء مساحة العمل وبدء توليد الأسئلة",
                            onClick = { onCreateWorkspace(true) },
                            enabled = !isBusy,
                            icon = Icons.Rounded.AutoAwesome,
                            testTag = "splitter_create_and_open_studio"
                        )
                        Button(
                            onClick = { onCreateWorkspace(false) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EduGreen,
                                contentColor = EduWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("splitter_create_only")
                        ) {
                            Text(
                                text = "إنشاء مساحة العمل وحفظها في المكتبة",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitRowCard(
    index: Int,
    split: LessonSplitDraft,
    canDelete: Boolean,
    onUpdate: (title: String, unitTitle: String, start: Int, end: Int) -> Unit,
    onDelete: () -> Unit
) {
    EduPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "درس ${index + 1} (${split.lessonKey})",
                style = MaterialTheme.typography.labelLarge,
                color = EduGreenDark
            )
            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "حذف الدرس",
                        tint = EduRed
                    )
                }
            }
        }

        SplitterTextField(
            label = "عنوان الدرس",
            value = split.title,
            onValueChange = { onUpdate(it, split.unitTitle, split.startPage, split.endPage) },
            modifier = Modifier.fillMaxWidth()
        )
        SplitterTextField(
            label = "الوحدة",
            value = split.unitTitle,
            onValueChange = { onUpdate(split.title, it, split.startPage, split.endPage) },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SplitterNumberField(
                label = "من صفحة",
                value = split.startPage,
                onValueChange = { newStart ->
                    onUpdate(split.title, split.unitTitle, newStart, split.endPage)
                },
                modifier = Modifier.weight(1f)
            )
            SplitterNumberField(
                label = "إلى صفحة",
                value = split.endPage,
                onValueChange = { newEnd ->
                    onUpdate(split.title, split.unitTitle, split.startPage, newEnd)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SplitterTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall, color = EduMuted) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = EduFieldBg,
            unfocusedContainerColor = EduFieldBg,
            focusedBorderColor = EduGreen,
            unfocusedBorderColor = EduLine,
            focusedTextColor = EduInk,
            unfocusedTextColor = EduInk
        ),
        modifier = modifier
    )
}

@Composable
private fun SplitterNumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw ->
            val parsed = raw.filter { it.isDigit() }.toIntOrNull()
            if (parsed != null) onValueChange(parsed)
        },
        label = { Text(label, style = MaterialTheme.typography.bodySmall, color = EduMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = EduFieldBg,
            unfocusedContainerColor = EduFieldBg,
            focusedBorderColor = EduGreen,
            unfocusedBorderColor = EduLine,
            focusedTextColor = EduInk,
            unfocusedTextColor = EduInk
        ),
        modifier = modifier
    )
}
