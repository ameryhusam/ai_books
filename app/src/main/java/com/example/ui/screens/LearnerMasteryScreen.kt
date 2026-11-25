package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ConceptMasterySummary
import com.example.model.LearnerProfile
import com.example.model.LearningProgressReport
import com.example.model.QuestionItem
import com.example.model.QuestionMasteryStats

@Composable
fun LearnerMasteryScreen(
    concepts: List<ConceptMasterySummary>,
    questions: List<QuestionItem>,
    questionStats: Map<String, QuestionMasteryStats>,
    totalPoints: Int,
    onLaunchReinforcement: (String?) -> Unit,
    activeLearner: LearnerProfile = LearnerProfile.DEFAULT,
    learningProgressReport: LearningProgressReport = LearningProgressReport(),
    modifier: Modifier = Modifier
) {
    val stableCount = learningProgressReport.stableConceptsCount
    val latestAccuracy = learningProgressReport.latestAccuracyPercent

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.AutoGraph, contentDescription = null)
                    Text(
                        "رحلة التعلّم",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            latestAccuracy == null -> "واصل التعلم وحل الاختبارات حتى نتمكن من إظهار تقدمك."
                            latestAccuracy >= 85 -> "أداؤك يسير بشكل جيد. استمر في المراجعة والتطبيق."
                            latestAccuracy >= 65 -> "أنت تتقدم خطوة بخطوة. واصل التدريب لتثبيت ما تعلمته."
                            else -> "التقدم يحتاج إلى وقت وممارسة. سنساعدك على المراجعة خطوة بخطوة."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column {
                            Text(latestAccuracy?.let { "$it%" } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("أحدث أداء", style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text(if (learningProgressReport.eligibleConceptsCount > 0) stableCount.toString() else "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("مفاهيم ثبتت", style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text("$totalPoints", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("نقاطك", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("خطوتك القادمة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "المراجعة القصيرة تساعدك على تذكر المعلومات واستخدامها في أسئلة جديدة.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { onLaunchReinforcement(null) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = questions.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Psychology, contentDescription = null)
                        Text("ابدأ جلسة مراجعة")
                    }
                }
            }
        }
        item {
            Text(
                "يظهر هنا ملخص تقدمك فقط. تفاصيل الإجابات والمفاهيم التي تحتاج متابعة تُستخدم لمساعدتك في خطة التعلم ويطلع عليها ولي الأمر أو المعلم.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
