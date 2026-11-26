package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DirectWorkspaceInspection
import com.example.ui.theme.EduBg
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
fun SingleGradePickerDialog(
    inspection: DirectWorkspaceInspection,
    isBusy: Boolean,
    alreadyBoundGrades: Set<String> = emptySet(),
    onConfirmGrades: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGrades by remember(inspection.rootUriOrPath) {
        mutableStateOf(inspection.grades.map { it.gradeKey }.toSet())
    }
    var showAllWarnings by remember(inspection.rootUriOrPath) { mutableStateOf(false) }

    val allSelected = inspection.grades.isNotEmpty() && selectedGrades.size == inspection.grades.size

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = EduGreenDark)
                Text(
                    text = if (inspection.grades.size > 1) {
                        "اختر الصفوف الدراسية للربط"
                    } else {
                        "تأكيد ربط الصف الدراسي"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EduBg,
                    border = BorderStroke(1.dp, EduLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = inspection.displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = EduInk
                        )
                        Text(
                            text = "ربط مباشر محلياً بدون نسخ الملفات • ${inspection.totalFilesIndexed} ملف مفهرس",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduGreenDark
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (inspection.grades.size > 1) {
                            "يمكن للمدير ضبط أكثر من صف دراسي، ويرى كل طالب صفّه المسجّل فقط:"
                        } else {
                            "الصف الجاهز للربط المباشر:"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted,
                        modifier = Modifier.weight(1f)
                    )
                    if (inspection.grades.size > 1) {
                        TextButton(
                            enabled = !isBusy,
                            onClick = {
                                selectedGrades = if (allSelected) {
                                    emptySet()
                                } else {
                                    inspection.grades.map { it.gradeKey }.toSet()
                                }
                            },
                            modifier = Modifier.testTag("toggle_select_all_grades")
                        ) {
                            Text(
                                text = if (allSelected) "إلغاء الكل" else "تحديد الكل",
                                style = MaterialTheme.typography.labelMedium,
                                color = EduGreenDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                inspection.grades.forEach { g ->
                    val active = g.gradeKey in selectedGrades
                    val alreadyBound = alreadyBoundGrades.any { it.equals(g.gradeKey, ignoreCase = true) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) EduMint.copy(alpha = 0.55f) else EduBg,
                        border = BorderStroke(1.2.dp, if (active) EduGreen else EduLine),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = !isBusy) {
                                selectedGrades = if (active) {
                                    selectedGrades - g.gradeKey
                                } else {
                                    selectedGrades + g.gradeKey
                                }
                            }
                            .testTag("single_grade_choice_${g.gradeKey}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (active) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (active) EduGreenDark else EduMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${g.gradeKey} • ${g.booksCount} مادة • ${g.lessonsCount} درس",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EduInk
                                    )
                                    if (alreadyBound) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = EduMint
                                        ) {
                                            Text(
                                                text = "مربوط مسبقاً",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EduGreenDark,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = g.bookTitles.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                            }
                        }
                    }
                }

                if (inspection.errors.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EduRed.copy(alpha = 0.11f),
                        border = BorderStroke(1.dp, EduRed.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = EduRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "كتب أو صفوف مستبعدة بسبب أخطاء في الفهرس أو ملفات PDF:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EduRed,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = inspection.errors.take(6).joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduInk
                                )
                            }
                        }
                    }
                }

                if (inspection.warnings.isNotEmpty()) {
                    val displayedWarnings = if (showAllWarnings) inspection.warnings else inspection.warnings.take(3)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EduOrange.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
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
                                    text = displayedWarnings.joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduInk
                                )
                            }
                            if (inspection.warnings.size > 3) {
                                TextButton(
                                    onClick = { showAllWarnings = !showAllWarnings },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        text = if (showAllWarnings) "إخفاء التفاصيل" else "عرض كل التنبيهات (${inspection.warnings.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EduInk
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy && selectedGrades.isNotEmpty(),
                onClick = {
                    val ordered = inspection.grades.map { it.gradeKey }.filter { it in selectedGrades }
                    onConfirmGrades(ordered)
                },
                modifier = Modifier.testTag("confirm_single_grade_button")
            ) {
                val label = when (selectedGrades.size) {
                    0 -> "اختر صفاً للربط"
                    1 -> "ربط الصف ${selectedGrades.first()} مباشرة"
                    else -> "ربط ${selectedGrades.size} صفوف مباشرة"
                }
                Text(
                    text = label,
                    color = EduGreenDark,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = EduMuted)
            }
        }
    )
}
