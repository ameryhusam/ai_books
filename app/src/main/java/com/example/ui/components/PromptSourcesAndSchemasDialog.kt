package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ai.BookIndexPromptRegistry
import com.example.model.LessonRef
import com.example.model.PromptBlueprintSpec
import com.example.model.TaskType
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptSourcesAndSchemasDialog(
    selectedLesson: LessonRef?,
    activeTask: TaskType,
    questionCount: Int,
    sourceText: String,
    extraInstructions: String,
    onApplyBlueprint: (PromptBlueprintSpec) -> Unit,
    onLoadSampleIntoEditor: (TaskType, String) -> Unit,
    onShowFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0: Prompt Sources, 1: مخططات الفهرس, 2: Live Prompt
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val livePrompt = remember(selectedLesson, activeTask, questionCount, sourceText, extraInstructions) {
        val bp = BookIndexPromptRegistry.blueprintFor(activeTask)
        val templateText = runCatching {
            context.assets.open(bp.sourcePath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
        if (selectedLesson != null) {
            BookIndexPromptRegistry.assemblePrompt(
                lesson = selectedLesson,
                task = activeTask,
                source = sourceText,
                extra = extraInstructions,
                questionCount = questionCount,
                attachedPdfPagesCount = if (!selectedLesson.pdfPath.isNullOrBlank()) 1 else 0,
                promptTemplate = templateText
            )
        } else {
            "المخطط المختار: ${bp.titleAr}\nالمصدر: ${bp.sourcePath}\nالدور: ${bp.roleInstruction}\nالمخرجات المتوقعة: ${bp.outputContract}\n\n${templateText}".trim()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(22.dp),
            color = EduBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = EduGreenDark, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = EduWhite)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مصادر البرومبت ومخططات البيانات",
                                style = MaterialTheme.typography.titleMedium,
                                color = EduWhite
                            )
                            Text(
                                text = "نفس الموجهات المرجعية ومخططات JSON القياسية المستخدمة في المنظومة",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduWhite.copy(alpha = 0.82f)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("prompt_sources_close_button")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "إغلاق", tint = EduWhite)
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EduPill(
                        text = "مصادر البرومبت (${BookIndexPromptRegistry.blueprints.size})",
                        active = activeTab == 0,
                        onClick = { activeTab = 0 },
                        testTag = "prompt_sources_tab_blueprints"
                    )
                    EduPill(
                        text = "مخططات الفهرس (${BookIndexPromptRegistry.schemas.size})",
                        active = activeTab == 1,
                        onClick = { activeTab = 1 },
                        testTag = "prompt_sources_tab_schemas"
                    )
                    EduPill(
                        text = "البرومبت المركّب الحالي",
                        active = activeTab == 2,
                        onClick = { activeTab = 2 },
                        testTag = "prompt_sources_tab_live"
                    )
                }

                AnimatedContent(
                    targetState = activeTab,
                    label = "prompt_sources_tabs",
                    modifier = Modifier.weight(1f)
                ) { tab ->
                    when (tab) {
                        0 -> LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(BookIndexPromptRegistry.blueprints, key = { it.id }) { bp ->
                                EduPanel(
                                    modifier = Modifier.fillMaxWidth(),
                                    borderColor = if (bp.task == activeTask) EduGreen else EduLine,
                                    containerColor = if (bp.task == activeTask) EduMint.copy(alpha = 0.3f) else EduWhite
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = EduLilac) {
                                            Text(
                                                text = bp.sourcePath,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                color = EduPurple,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(shape = RoundedCornerShape(8.dp), color = EduMint) {
                                            Text(
                                                text = bp.task.labelAr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EduGreenDark,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text(bp.titleAr, style = MaterialTheme.typography.titleSmall, color = EduInk)
                                    Text(bp.subtitleAr, style = MaterialTheme.typography.bodySmall, color = EduMuted)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = EduFieldBg,
                                        border = BorderStroke(1.dp, EduLine),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "معيار الخرج: ${bp.outputContract}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = EduInk
                                            )
                                            Text(
                                                text = "التوجيه: ${bp.quickInstructions}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = EduGreenDark
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onApplyBlueprint(bp)
                                                onShowFeedback("تم تطبيق مخطط «${bp.titleAr}» في الاستوديو")
                                                onDismiss()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تطبيق في الاستوديو", style = MaterialTheme.typography.labelMedium)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString("${bp.roleInstruction}\n${bp.quickInstructions}\n${bp.schemaSnippet}".trim()))
                                                onShowFeedback("تم نسخ مصدر البرومبت")
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, EduLine),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = EduInk, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("نسخ", color = EduInk, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }

                        1 -> LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(BookIndexPromptRegistry.schemas, key = { it.id }) { schema ->
                                EduPanel(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Outlined.Schema, contentDescription = null, tint = EduBlue, modifier = Modifier.size(18.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(schema.titleAr, style = MaterialTheme.typography.titleSmall, color = EduInk)
                                            Text(
                                                text = schema.fileName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                color = EduBlue
                                            )
                                        }
                                    }
                                    Text(schema.descriptionAr, style = MaterialTheme.typography.bodySmall, color = EduMuted)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = EduFieldBg,
                                        border = BorderStroke(1.dp, EduLine),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = schema.schemaJson,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = EduInk,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(schema.schemaJson))
                                                onShowFeedback("تم نسخ مخطط ${schema.fileName}")
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, EduGreen),
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("نسخ JSON", color = EduGreenDark, style = MaterialTheme.typography.labelMedium)
                                        }
                                        if (schema.id in setOf("schema_educational_activities", "schema_questions", "schema_source_questions")) {
                                            Button(
                                                onClick = {
                                                    val targetTask = when (schema.id) {
                                                        "schema_educational_activities" -> TaskType.EDUCATIONAL_ACTIVITIES
                                                        "schema_source_questions" -> TaskType.SOURCE_QUESTIONS
                                                        else -> TaskType.QUESTIONS
                                                    }
                                                    onLoadSampleIntoEditor(targetTask, schema.schemaJson)
                                                    onShowFeedback("تم تحميل نموذج المخطط في محرر الاستوديو")
                                                    onDismiss()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = EduPurple, contentColor = EduWhite),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("تجربة في المحرر", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                EduPanel(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "معاينة البرومبت الكامل الذي سيُرسل للمزود",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EduInk
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = EduFieldBg,
                                        border = BorderStroke(1.dp, EduLine),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = livePrompt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EduInk,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(livePrompt))
                                            onShowFeedback("تم نسخ البرومبت الكامل")
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("نسخ البرومبت الكامل", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
