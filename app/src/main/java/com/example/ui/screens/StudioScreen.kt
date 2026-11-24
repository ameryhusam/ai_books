package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DraftRecord
import com.example.model.LessonRef
import com.example.model.TaskType
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduHeroKicker
import com.example.ui.theme.EduHeroSub
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    selectedLesson: LessonRef?,
    task: TaskType,
    sourceText: String,
    extraInstructions: String,
    generatedResult: String,
    lessonDrafts: List<DraftRecord>,
    isBusy: Boolean,
    onBackToLibrary: () -> Unit,
    onSelectTask: (TaskType) -> Unit,
    onSourceChange: (String) -> Unit,
    onExtraChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onLoadDraft: (DraftRecord) -> Unit,
    onDeleteDraft: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToLibrary)

    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "back_link") {
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onBackToLibrary)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("studio_back_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = EduGreenDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.back_to_library),
                        style = MaterialTheme.typography.labelLarge,
                        color = EduGreenDark
                    )
                }
            }

            item(key = "studio_hero") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(19.dp),
                    color = EduGreen
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.studio_kicker),
                            style = MaterialTheme.typography.labelSmall,
                            color = EduHeroKicker
                        )
                        Text(
                            text = stringResource(R.string.studio_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = EduWhite
                        )
                        Text(
                            text = selectedLesson?.title ?: stringResource(R.string.studio_pick_lesson),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduHeroSub
                        )
                        if (selectedLesson != null) {
                            Text(
                                text = "${selectedLesson.grade} • ${selectedLesson.bookTitle} • ${selectedLesson.unitTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduHeroKicker
                            )
                        }
                    }
                }
            }

            item(key = "studio_form") {
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.studio_task_heading),
                        style = MaterialTheme.typography.titleMedium,
                        color = EduInk
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        TaskType.entries.forEach { item ->
                            EduPill(
                                text = item.labelAr,
                                active = task == item,
                                onClick = { onSelectTask(item) },
                                testTag = "task_pill_${item.id}"
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.studio_source_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedTextField(
                        value = sourceText,
                        onValueChange = onSourceChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.studio_source_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduMuted
                            )
                        },
                        minLines = 4,
                        maxLines = 9,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = EduFieldBg,
                            unfocusedContainerColor = EduFieldBg,
                            focusedBorderColor = EduGreen,
                            unfocusedBorderColor = EduLine,
                            focusedTextColor = EduInk,
                            unfocusedTextColor = EduInk
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                            .testTag("source_input")
                    )

                    Text(
                        text = stringResource(R.string.studio_extra_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedTextField(
                        value = extraInstructions,
                        onValueChange = onExtraChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.studio_extra_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduMuted
                            )
                        },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = EduFieldBg,
                            unfocusedContainerColor = EduFieldBg,
                            focusedBorderColor = EduGreen,
                            unfocusedBorderColor = EduLine,
                            focusedTextColor = EduInk,
                            unfocusedTextColor = EduInk
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp)
                            .testTag("extra_input")
                    )

                    EduYellowButton(
                        text = if (isBusy) {
                            stringResource(R.string.studio_generating_cta)
                        } else {
                            stringResource(R.string.studio_generate_cta)
                        },
                        onClick = onGenerate,
                        enabled = !isBusy,
                        icon = Icons.Rounded.AutoAwesome,
                        testTag = "generate_button",
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (generatedResult.isNotBlank()) {
                item(key = "generated_result") {
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.studio_draft_heading),
                                style = MaterialTheme.typography.titleMedium,
                                color = EduInk
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedResult))
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "نسخ المحتوى",
                                    tint = EduGreenDark
                                )
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = generatedResult,
                                style = MaterialTheme.typography.bodyLarge,
                                color = EduInk
                            )
                        }
                    }
                }
            }

            if (lessonDrafts.isNotEmpty()) {
                item(key = "saved_drafts_heading") {
                    Text(
                        text = stringResource(R.string.studio_saved_drafts_heading),
                        style = MaterialTheme.typography.titleMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(
                    items = lessonDrafts,
                    key = { it.id }
                ) { draft ->
                    EduPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onLoadDraft(draft) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EduLilac
                            ) {
                                Text(
                                    text = draft.task.labelAr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EduPurple,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${draft.provider} • ${draft.model}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EduInk
                                )
                                Text(
                                    text = draft.content.take(90).replace('\n', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted,
                                    maxLines = 2
                                )
                            }
                            IconButton(
                                onClick = { onDeleteDraft(draft.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "حذف المسودة",
                                    tint = EduRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
