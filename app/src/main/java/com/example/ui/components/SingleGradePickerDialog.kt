package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import com.example.ui.theme.EduWhite

@Composable
fun SingleGradePickerDialog(
    inspection: DirectWorkspaceInspection,
    isBusy: Boolean,
    onConfirmGrade: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGrade by remember(inspection.rootUriOrPath) {
        mutableStateOf(inspection.grades.firstOrNull()?.gradeKey.orEmpty())
    }

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
                    text = "اختر صفاً دراسياً واحداً للتطبيق",
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

                Text(
                    text = "يعمل التطبيق لصف دراسي محدد فقط. حدد الصف الذي تريد ربطه الآن:",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )

                inspection.grades.forEach { g ->
                    val active = selectedGrade == g.gradeKey
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) EduMint.copy(alpha = 0.55f) else EduBg,
                        border = BorderStroke(1.2.dp, if (active) EduGreen else EduLine),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = !isBusy) { selectedGrade = g.gradeKey }
                            .testTag("single_grade_choice_${g.gradeKey}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (active) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (active) EduGreenDark else EduMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${g.gradeKey} • ${g.booksCount} مادة • ${g.lessonsCount} درس",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EduInk
                                )
                                Text(
                                    text = g.bookTitles.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                            }
                        }
                    }
                }

                if (inspection.warnings.isNotEmpty()) {
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
                                text = inspection.warnings.joinToString("\n"),
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
                enabled = !isBusy && selectedGrade.isNotBlank(),
                onClick = { onConfirmGrade(selectedGrade) },
                modifier = Modifier.testTag("confirm_single_grade_button")
            ) {
                Text(
                    text = "ربط الصف $selectedGrade مباشرة",
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
