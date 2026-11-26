package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.QuestionDifficulty
import com.example.model.QuestionType

private data class QuestionLanguageOption(val id: String, val label: String)

@Composable
fun QuestionGenerationOptions(
    questionCount: Int,
    selectedQuestionTypeIds: List<String>,
    questionLanguage: String,
    questionDifficulty: QuestionDifficulty,
    onQuestionCountChange: (Int) -> Unit,
    onQuestionTypesChange: (List<String>) -> Unit,
    onQuestionLanguageChange: (String) -> Unit,
    onQuestionDifficultyChange: (QuestionDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    var countExpanded by remember { mutableStateOf(false) }
    var typesExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var difficultyExpanded by remember { mutableStateOf(false) }

    val languages = listOf(
        QuestionLanguageOption("ar", "العربية"),
        QuestionLanguageOption("en", "English"),
        QuestionLanguageOption("ar_en", "العربية + English")
    )
    val selectedTypes = QuestionType.entries.filter { it.id in selectedQuestionTypeIds }
    val selectedLanguage = languages.firstOrNull { it.id == questionLanguage } ?: languages.first()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(0.85f)) {
                OutlinedButton(
                    onClick = { countExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("العدد: $questionCount")
                }
                DropdownMenu(expanded = countExpanded, onDismissRequest = { countExpanded = false }) {
                    listOf(3, 5, 10, 15, 20, 30).filter { it >= selectedTypes.size }.forEach { count ->
                        DropdownMenuItem(
                            text = { Text("$count سؤال") },
                            onClick = {
                                onQuestionCountChange(count)
                                countExpanded = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1.15f)) {
                OutlinedButton(
                    onClick = { typesExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("الأنواع: ${selectedTypes.size}/${QuestionType.entries.size}")
                }
                DropdownMenu(expanded = typesExpanded, onDismissRequest = { typesExpanded = false }) {
                    QuestionType.entries.forEach { type ->
                        val checked = type.id in selectedQuestionTypeIds
                        DropdownMenuItem(
                            enabled = checked || selectedQuestionTypeIds.size < questionCount,
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Text(type.labelAr)
                                }
                            },
                            onClick = {
                                val next = if (checked) {
                                    selectedQuestionTypeIds.filterNot { it == type.id }
                                } else {
                                    (selectedQuestionTypeIds + type.id).distinct()
                                }
                                // At least one type must remain selected.
                                if (next.isNotEmpty()) onQuestionTypesChange(next)
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { languageExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اللغة: ${selectedLanguage.label}")
                }
                DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                    languages.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language.label) },
                            onClick = {
                                onQuestionLanguageChange(language.id)
                                languageExpanded = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { difficultyExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("الصعوبة: ${questionDifficulty.labelAr}")
                }
                DropdownMenu(expanded = difficultyExpanded, onDismissRequest = { difficultyExpanded = false }) {
                    QuestionDifficulty.entries.forEach { difficulty ->
                        DropdownMenuItem(
                            text = { Text(difficulty.labelAr) },
                            onClick = {
                                onQuestionDifficultyChange(difficulty)
                                difficultyExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
