package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.LearningProgressReport
import com.example.model.WeeklyAssessmentSummary

@Composable
fun LearningProgressReportCard(
    report: LearningProgressReport,
    isLearner: Boolean,
    onExport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isLearner) "تقدمك في التعلّم" else "تقرير متابعة مستوى المتعلّم",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (isLearner) {
                val latest = report.latestAccuracyPercent
                Text(
                    text = when {
                        latest == null -> "ابدأ بحل الاختبارات حتى نتمكن من قياس تقدمك."
                        latest >= 85 -> "أداء رائع. واصل المراجعة حتى تثبت معلوماتك."
                        latest >= 65 -> "أنت تتقدم. بعض الموضوعات تحتاج إلى تدريب إضافي."
                        else -> "كل محاولة تساعدك على التقدم. تابع خطوات التعلم والمراجعة."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    LearnerMetric(
                        title = "أحدث أداء",
                        value = latest?.let { "$it%" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    LearnerMetric(
                        title = "مفاهيم ثبتت",
                        value = report.stableConceptsCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    LearnerMetric(
                        title = "أسابيع التقييم",
                        value = report.weeklyAssessments.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "تذكّر: التعلّم يثبت بالمراجعة والتطبيق، وليس بسرعة إنهاء الدروس فقط.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ReportMetric("المحاولة الأولى", report.firstTryAccuracyPercent?.let { "$it%" } ?: "غير متاح", Modifier.weight(1f))
                    ReportMetric("الأداء الأحدث", report.latestAccuracyPercent?.let { "$it%" } ?: "غير متاح", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ReportMetric(
                        "الإتقان المستقر",
                        report.stableConceptsPercent?.let { "$it%" } ?: "عينة غير كافية",
                        Modifier.weight(1f)
                    )
                    ReportMetric(
                        "الاسترجاع المؤجل",
                        report.delayedRecallPercent?.let { "$it%" } ?: "بانتظار قياس",
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (report.recurringConceptTags.isNotEmpty()) {
                    Text(
                        text = "مفاهيم تكرر التعثر فيها: ${report.recurringConceptTags.take(5).joinToString("، ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (report.reinforcementImpactPercent != null) {
                    Text(
                        text = "التغير بعد جلسات التقوية: ${if (report.reinforcementImpactPercent >= 0) "+" else ""}${report.reinforcementImpactPercent}% عبر ${report.reinforcementImpactConceptCount} مفهوماً.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "لم تتوفر بعد قياسات كافية قبل جلسات التقوية وبعدها على الأسئلة نفسها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (report.recurringMistakes.isNotEmpty()) {
                    Text("أنماط أخطاء تحتاج متابعة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    report.recurringMistakes.take(4).forEach { mistake ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "${mistake.conceptTag} • تكررت الإجابة «${mistake.selectedAnswer}» ${mistake.occurrences} مرات",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (mistake.questionPrompt.isNotBlank()) {
                                Text(mistake.questionPrompt, style = MaterialTheme.typography.bodySmall)
                            }
                            if (mistake.expectedAnswer.isNotBlank()) {
                                Text("الإجابة المعتمدة: ${mistake.expectedAnswer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (report.weeklyAssessments.isNotEmpty()) {
                    Text("تطور نتائج الاختبارات الأسبوعية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    report.weeklyAssessments.takeLast(8).forEach { week ->
                        WeeklyScoreRow(week)
                    }
                }

                Text(
                    text = "تنبيه منهجي: هذه مؤشرات متابعة وليست تشخيصاً نهائياً. التحسن بعد التقوية لا يثبت وحده أن الشرح هو سبب التحسن، كما أن غياب البيانات لا يعني ضعف الطالب.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Text("تصدير تقرير المتابعة")
                }
            }
        }
    }
}

@Composable
private fun LearnerMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReportMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeklyScoreRow(week: WeeklyAssessmentSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("أسبوع ${week.label}", style = MaterialTheme.typography.bodySmall)
            Text("${week.averageScorePercent}% • ${week.attemptsCount} اختبار", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = week.averageScorePercent.coerceIn(0, 100) / 100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
