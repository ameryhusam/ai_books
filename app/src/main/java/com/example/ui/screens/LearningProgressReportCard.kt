package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.LearningProgressReport
import com.example.model.WeeklyAssessmentSummary
import com.example.ui.components.EduPanel
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduWhite

@Composable
fun LearningProgressReportCard(
    report: LearningProgressReport,
    isLearner: Boolean,
    learnerName: String = "",
    onExport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    EduPanel(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = EduBg,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Assessment,
                    contentDescription = null,
                    tint = EduGreenDark,
                    modifier = Modifier.padding(7.dp)
                )
            }
            Text(
                text = if (isLearner) "تقرير تقدّمك في التعلّم" else "تقرير متابعة مستوى ${learnerName.ifBlank { "المتعلّم" }}",
                style = MaterialTheme.typography.titleMedium,
                color = EduInk,
                fontWeight = FontWeight.Bold
            )
        }

        if (isLearner) {
            val latest = report.latestAccuracyPercent
            Text(
                text = when {
                    latest == null -> "ابدأ بحل الاختبارات حتى نتمكن من قياس تقدّمك وتثبيت المفاهيم."
                    latest >= 85 -> "أداء رائع. واصل المراجعة المتباعدة حتى تثبت معلوماتك."
                    latest >= 65 -> "أنت تتقدم بثبات. بعض الموضوعات تحتاج إلى تدريب إضافي."
                    else -> "كل محاولة تساعدك على التقدم. تابع خطوات التعلّم والمراجعة."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = EduInk
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LearnerMetric(
                    title = "أحدث أداء",
                    value = latest?.let { "$it%" } ?: "—",
                    accent = EduGreenDark,
                    modifier = Modifier.weight(1f)
                )
                LearnerMetric(
                    title = "مفاهيم ثبتت",
                    value = if (report.eligibleConceptsCount > 0) report.stableConceptsCount.toString() else "—",
                    accent = EduPurple,
                    modifier = Modifier.weight(1f)
                )
                LearnerMetric(
                    title = "أسابيع التقييم",
                    value = report.weeklyAssessments.size.toString(),
                    accent = EduBlue,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "تذكّر: التعلّم يثبت بالمراجعة والتطبيق، وليس بسرعة إنهاء الدروس فقط.",
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ReportMetric("المحاولة الأولى", report.firstTryAccuracyPercent?.let { "$it%" } ?: "غير متاح", EduBlue, Modifier.weight(1f))
                ReportMetric("الأداء الأحدث", report.latestAccuracyPercent?.let { "$it%" } ?: "غير متاح", EduGreenDark, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ReportMetric(
                    "الإتقان المستقر",
                    report.stableConceptsPercent?.let { "$it%" } ?: "عينة غير كافية",
                    EduPurple,
                    Modifier.weight(1f)
                )
                ReportMetric(
                    "الاسترجاع المؤجل",
                    report.delayedRecallPercent?.let { "$it%" } ?: "بانتظار قياس",
                    EduOrange,
                    Modifier.weight(1f)
                )
            }
            Text(
                text = if (report.hasEnoughDataForRetention) {
                    "فارق الاسترجاع المؤجل: ${report.estimatedRecallDropPercent?.let { "$it%" } ?: "لا يوجد انخفاض مرصود"} (عينة ${report.delayedRecallSampleCount} أسئلة)."
                } else {
                    "لا نحسب تراجع الاسترجاع قبل توفر ثلاثة أسئلة على الأقل أُعيد قياسها بعد سبعة أيام."
                },
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )

            if (report.recurringConceptTags.isNotEmpty()) {
                Text(
                    text = "مفاهيم تكرر التعثر فيها: ${report.recurringConceptTags.take(5).joinToString("، ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }

            if (report.reinforcementImpactPercent != null) {
                Text(
                    text = "التغير بعد جلسات التقوية: ${if (report.reinforcementImpactPercent >= 0) "+" else ""}${report.reinforcementImpactPercent}% عبر ${report.reinforcementImpactConceptCount} مفهوماً.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EduGreenDark,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "لم تتوفر بعد قياسات كافية قبل جلسات التقوية وبعدها على الأسئلة نفسها.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }

            if (report.recurringMistakes.isNotEmpty()) {
                Text("أنماط أخطاء تحتاج متابعة", style = MaterialTheme.typography.titleSmall, color = EduInk)
                report.recurringMistakes.take(4).forEach { mistake ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EduFieldBg,
                        border = BorderStroke(1.dp, EduLine),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "${mistake.conceptTag} • تكررت الإجابة «${mistake.selectedAnswer}» ${mistake.occurrences} مرات",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduInk,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (mistake.questionPrompt.isNotBlank()) {
                                Text(mistake.questionPrompt, style = MaterialTheme.typography.bodySmall, color = EduInk)
                            }
                            if (mistake.expectedAnswer.isNotBlank()) {
                                Text("الإجابة المعتمدة: ${mistake.expectedAnswer}", style = MaterialTheme.typography.bodySmall, color = EduGreenDark)
                            }
                        }
                    }
                }
            }

            if (report.weeklyAssessments.isNotEmpty()) {
                Text("تطور نتائج الاختبارات الأسبوعية", style = MaterialTheme.typography.titleSmall, color = EduInk)
                report.weeklyAssessments.takeLast(8).forEach { week ->
                    WeeklyScoreRow(week)
                }
            }

            Text(
                text = "تنبيه منهجي: هذه مؤشرات متابعة وليست تشخيصاً نهائياً. التحسن بعد التقوية لا يثبت وحده أن الشرح هو سبب التحسن، كما أن غياب البيانات لا يعني ضعف الطالب.",
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )
            Button(
                onClick = onExport,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير تقرير المتابعة", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LearnerMetric(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = EduFieldBg,
        border = BorderStroke(1.dp, EduLine)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = EduMuted
            )
        }
    }
}

@Composable
private fun ReportMetric(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = EduFieldBg,
        border = BorderStroke(1.dp, EduLine)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = EduMuted)
        }
    }
}

@Composable
private fun WeeklyScoreRow(week: WeeklyAssessmentSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("أسبوع ${week.label}", style = MaterialTheme.typography.bodySmall, color = EduInk)
            Text("${week.averageScorePercent}% • ${week.attemptsCount} اختبار", style = MaterialTheme.typography.bodySmall, color = EduGreenDark, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { week.averageScorePercent.coerceIn(0, 100) / 100f },
            color = EduGreen,
            trackColor = EduBg,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
        )
    }
}
