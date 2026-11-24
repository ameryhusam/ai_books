package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DraftRecord
import com.example.model.GlobalSearchHit
import com.example.model.LessonRef
import com.example.model.QuestionItem
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchDialog(
    lessons: List<LessonRef>,
    drafts: List<DraftRecord>,
    questions: List<QuestionItem>,
    onSelectLesson: (LessonRef) -> Unit,
    onSelectDraft: (DraftRecord, LessonRef?) -> Unit,
    onSelectQuestion: (QuestionItem) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var categoryIndex by remember { mutableIntStateOf(0) } // 0: all, 1: lessons, 2: drafts, 3: questions

    val hits = remember(query, categoryIndex, lessons, drafts, questions) {
        val q = query.trim()
        val out = mutableListOf<GlobalSearchHit>()
        if (categoryIndex == 0 || categoryIndex == 1) {
            lessons.filter { l ->
                q.isEmpty() ||
                    l.title.contains(q, ignoreCase = true) ||
                    l.unitTitle.contains(q, ignoreCase = true) ||
                    l.bookTitle.contains(q, ignoreCase = true)
            }.take(20).forEach { out.add(GlobalSearchHit.LessonHit(it)) }
        }
        if (categoryIndex == 0 || categoryIndex == 2) {
            drafts.filter { d ->
                q.isEmpty() ||
                    d.title.contains(q, ignoreCase = true) ||
                    d.content.contains(q, ignoreCase = true) ||
                    d.task.labelAr.contains(q, ignoreCase = true)
            }.take(20).forEach { d ->
                val matchedLesson = lessons.firstOrNull {
                    it.bookKey == d.bookKey && it.unitKey == d.unitKey && it.lessonKey == d.lessonKey
                }
                out.add(GlobalSearchHit.DraftHit(d, matchedLesson))
            }
        }
        if (categoryIndex == 0 || categoryIndex == 3) {
            questions.filter { qi ->
                q.isEmpty() ||
                    qi.prompt.contains(q, ignoreCase = true) ||
                    qi.correctAnswer.contains(q, ignoreCase = true) ||
                    qi.lessonTitle.contains(q, ignoreCase = true)
            }.take(20).forEach { out.add(GlobalSearchHit.QuestionHit(it)) }
        }
        out
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text(
                text = "البحث الشامل في مساحة العمل",
                style = MaterialTheme.typography.titleMedium,
                color = EduInk
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = EduMuted) },
                    placeholder = { Text("ابحث في الدروس والمسودات والأسئلة...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = EduFieldBg,
                        unfocusedContainerColor = EduFieldBg,
                        focusedBorderColor = EduGreen,
                        unfocusedBorderColor = EduLine
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_search_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("الكل", "الدروس", "المسودات", "الأسئلة").forEachIndexed { idx, label ->
                        EduPill(
                            text = label,
                            active = categoryIndex == idx,
                            onClick = { categoryIndex = idx }
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (hits.isEmpty()) {
                        item {
                            Text(
                                text = "لا توجد نتائج مطابقة لبحثك.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        items(items = hits, key = { it.id }) { hit ->
                            val icon = when (hit) {
                                is GlobalSearchHit.LessonHit -> Icons.Outlined.MenuBook
                                is GlobalSearchHit.DraftHit -> Icons.Outlined.Description
                                is GlobalSearchHit.QuestionHit -> Icons.Outlined.Quiz
                            }
                            val tint = when (hit) {
                                is GlobalSearchHit.LessonHit -> EduGreenDark
                                is GlobalSearchHit.DraftHit -> EduBlue
                                is GlobalSearchHit.QuestionHit -> EduPurple
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EduBg,
                                border = BorderStroke(1.dp, EduLine),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .minimumInteractiveComponentSize()
                                    .clickable {
                                        when (hit) {
                                            is GlobalSearchHit.LessonHit -> onSelectLesson(hit.lesson)
                                            is GlobalSearchHit.DraftHit -> onSelectDraft(hit.draft, hit.lesson)
                                            is GlobalSearchHit.QuestionHit -> onSelectQuestion(hit.question)
                                        }
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hit.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = EduInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = hit.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EduMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = EduGreenDark, fontWeight = FontWeight.Bold)
            }
        }
    )
}
