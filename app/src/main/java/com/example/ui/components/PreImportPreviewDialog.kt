package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.StagedWorkspacePreview
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreImportPreviewDialog(
    preview: StagedWorkspacePreview,
    isBusy: Boolean,
    existingGrades: Set<String> = emptySet(),
    onConfirmImport: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedGrades = remember(preview.stagingId) {
        mutableStateListOf<String>().apply {
            addAll(preview.grades.map { it.gradeKey })
        }
    }
    val sizeMb = String.format("%.2f MB", preview.expandedBytes / (1024.0 * 1024.0))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text(
                text = "معاينة قبل استيراد الحزمة",
                style = MaterialTheme.typography.titleMedium,
                color = EduInk
            )
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
                            text = "عدد الملفات: ${preview.fileCount} • الحجم بعد الفك: $sizeMb",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduInk
                        )
                        Text(
                            text = "الصفوف المكتشفة: ${preview.grades.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = EduGreenDark
                        )
                    }
                }

                if (preview.grades.size > 1) {
                    Text(
                        text = "اختر الصفوف المراد استيرادها كمساحات عمل مستقلة:",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        preview.grades.forEach { g ->
                            val active = selectedGrades.contains(g.gradeKey)
                            EduPill(
                                text = "${g.gradeKey} (${g.lessonsCount} درس)",
                                active = active,
                                onClick = {
                                    if (active && selectedGrades.size > 1) {
                                        selectedGrades.remove(g.gradeKey)
                                    } else if (!active) {
                                        selectedGrades.add(g.gradeKey)
                                    }
                                },
                                testTag = "preimport_grade_${g.gradeKey}"
                            )
                        }
                    }
                }

                preview.grades.forEach { g ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedGrades.contains(g.gradeKey)) EduMint.copy(alpha = 0.4f) else EduBg,
                        border = BorderStroke(1.dp, if (selectedGrades.contains(g.gradeKey)) EduGreen else EduLine),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${g.gradeKey} • ${g.booksCount} مادة • ${g.lessonsCount} درس",
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk
                            )
                            Text(
                                text = if (g.gradeKey.uppercase() in existingGrades) {
                                    "الصف موجود مسبقاً — سيتم تحديث مواده وفهارسه مع الحفاظ على سجلات التعلم المرتبطة به."
                                } else {
                                    "صف جديد — ستتم إضافته مع مواده وفهارسه."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (g.gradeKey.uppercase() in existingGrades) EduOrange else EduGreenDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = g.bookTitles.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                        }
                    }
                }

                if (preview.warnings.isNotEmpty()) {
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
                                text = preview.warnings.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = EduInk
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy && selectedGrades.isNotEmpty(),
                onClick = { onConfirmImport(selectedGrades.toList()) },
                modifier = Modifier.testTag("confirm_preimport_button")
            ) {
                Text(
                    text = "استيراد المحدد (${selectedGrades.size})",
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
