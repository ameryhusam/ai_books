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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CloudDraft
import com.example.model.GenerationRunRecord
import com.example.model.LessonProgress
import com.example.model.PromptTemplateRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.model.SyncUiState
import com.example.model.TaskType
import com.example.ui.ProviderFormState
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduHeroSub
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduSecurityBg
import com.example.ui.theme.EduSecurityText
import com.example.ui.theme.EduWhite
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    form: ProviderFormState,
    providers: List<ProviderConfig>,
    recentRuns: List<GenerationRunRecord>,
    promptTemplates: List<PromptTemplateRecord> = emptyList(),
    userEmail: String? = null,
    userDisplayName: String? = null,
    cloudDraftsState: SyncUiState<List<CloudDraft>> = SyncUiState.Loading,
    cloudProgressState: SyncUiState<List<LessonProgress>> = SyncUiState.Loading,
    onSyncWorkspaceToCloud: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onBackToLibrary: () -> Unit,
    onSelectProviderType: (ProviderType) -> Unit,
    onLabelChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onFallbackModelsChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onDailyRequestLimitChange: (String) -> Unit = {},
    onQuotaTimeZoneChange: (String) -> Unit = {},
    onToggleStrictModel: () -> Unit = {},
    onAddOrUpdateProvider: () -> Unit,
    onCancelEditProvider: () -> Unit = {},
    onEditProvider: (ProviderConfig) -> Unit = {},
    onTestFormProvider: () -> Unit = {},
    onTestSavedProvider: (ProviderConfig) -> Unit = {},
    onToggleProvider: (String) -> Unit,
    onReactivateProvider: (String) -> Unit = {},
    onMoveProvider: (String, Int) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onClearRecentRuns: () -> Unit = {},
    onSavePromptTemplate: (String, TaskType, String, String, String, String?) -> Unit = { _, _, _, _, _, _ -> },
    onDeletePromptTemplate: (String) -> Unit = {},
    onUseTemplateInStudio: (PromptTemplateRecord) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToLibrary)

    var showApiKey by remember { mutableStateOf(false) }
    var newTplTitle by remember { mutableStateOf("") }
    var newTplTask by remember { mutableStateOf(TaskType.EXPLANATION) }
    var newTplInstructions by remember { mutableStateOf("") }
    var newTplSchema by remember { mutableStateOf("") }
    var newTplMapping by remember { mutableStateOf("") }
    var editingTemplateId by remember { mutableStateOf<String?>(null) }
    var templateValidationError by remember { mutableStateOf<String?>(null) }

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
            item(key = "settings_back") {
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onBackToLibrary)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .testTag("settings_back_button"),
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
                        text = stringResource(R.string.nav_library),
                        style = MaterialTheme.typography.labelLarge,
                        color = EduGreenDark
                    )
                }
            }

            item(key = "settings_hero") {
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
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = EduWhite
                        )
                        Text(
                            text = stringResource(R.string.settings_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduHeroSub
                        )
                    }
                }
            }

            item(key = "cloud_sync_account") {
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = EduGreenDark,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.cloud_sync_heading),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EduInk
                                )
                                Text(
                                    text = listOfNotNull(userDisplayName?.takeIf { it.isNotBlank() }, userEmail?.takeIf { it.isNotBlank() })
                                        .joinToString(" • ")
                                        .ifBlank { "حساب Google موثّق" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduMuted
                                )
                            }
                        }
                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                tint = EduRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.auth_sign_out_cta),
                                color = EduRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val syncSummaryText = when {
                        cloudDraftsState is SyncUiState.Error -> "تعذر الاتصال بالسحابة: ${cloudDraftsState.message}"
                        cloudProgressState is SyncUiState.Error -> "تعذر مزامنة التقدم: ${cloudProgressState.message}"
                        cloudDraftsState is SyncUiState.Success && cloudProgressState is SyncUiState.Success ->
                            "متصل • ${cloudDraftsState.data.size} مسودة متزامنة • ${cloudProgressState.data.size} سجل تقدم"
                        else -> "جارٍ الاتصال بقاعدة بيانات Firestore..."
                    }
                    Text(
                        text = syncSummaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cloudDraftsState is SyncUiState.Error || cloudProgressState is SyncUiState.Error) EduRed else EduGreenDark
                    )

                    OutlinedButton(
                        onClick = onSyncWorkspaceToCloud,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EduGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_sync_cloud_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = EduGreenDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.cloud_sync_now_cta),
                            color = EduGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item(key = "provider_form") {
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_provider_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        ProviderType.entries.forEach { type ->
                            EduPill(
                                text = type.displayName,
                                active = form.provider == type,
                                onClick = { onSelectProviderType(type) },
                                testTag = "provider_type_${type.id}"
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.settings_profile_name_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    SettingsField(
                        value = form.label,
                        onValueChange = onLabelChange,
                        testTag = "provider_label_input"
                    )

                    Text(
                        text = stringResource(R.string.settings_model_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    SettingsField(
                        value = form.model,
                        onValueChange = onModelChange,
                        testTag = "provider_model_input"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = if (form.provider == ProviderType.GEMINI) {
                            listOf("gemini-3.5-flash", "gemini-3.6-flash", "gemini-3.7-flash", "gemini-3.8-flash")
                        } else {
                            listOf("gpt-4o-mini", "gpt-4.1-mini", "deepseek-chat")
                        }
                        presets.forEach { preset ->
                            EduPill(
                                text = preset,
                                active = form.model == preset,
                                onClick = { onModelChange(preset) }
                            )
                        }
                    }

                    Text(
                        text = "النماذج الاحتياطية (Fallback مفصولة بفاصلة)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    SettingsField(
                        value = form.fallbackModels,
                        onValueChange = onFallbackModelsChange,
                        testTag = "provider_fallback_input"
                    )

                    if (form.provider == ProviderType.OPENAI_COMPATIBLE) {
                        Text(
                            text = stringResource(R.string.settings_base_url_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = EduInk,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        SettingsField(
                            value = form.baseUrl,
                            onValueChange = onBaseUrlChange,
                            testTag = "provider_baseurl_input"
                        )
                    }

                    Text(
                        text = "الحد اليومي لهذا المفتاح (0 = غير محدود)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    SettingsField(
                        value = form.dailyRequestLimit,
                        onValueChange = onDailyRequestLimitChange,
                        testTag = "provider_daily_limit_input"
                    )
                    Text(
                        text = "المنطقة الزمنية لإعادة ضبط الحصة",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    SettingsField(
                        value = form.quotaTimeZone,
                        onValueChange = onQuotaTimeZoneChange,
                        testTag = "provider_quota_timezone_input"
                    )
                    Text(
                        text = stringResource(R.string.settings_api_key_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedTextField(
                        value = form.apiKey,
                        onValueChange = onApiKeyChange,
                        singleLine = true,
                        visualTransformation = if (showApiKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { showApiKey = !showApiKey },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (showApiKey) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (showApiKey) "إخفاء المفتاح" else "إظهار المفتاح",
                                    tint = EduMuted
                                )
                            }
                        },
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
                            .testTag("provider_apikey_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleStrictModel)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = form.strictModel,
                            onCheckedChange = { onToggleStrictModel() },
                            colors = CheckboxDefaults.colors(checkedColor = EduGreen),
                            modifier = Modifier.testTag("provider_strict_model_checkbox")
                        )
                        Text(
                            text = "التزام صارم بالنموذج المختار (تجاهل Fallback)",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduInk
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTestFormProvider,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EduGreen),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("test_form_provider_button")
                        ) {
                            Icon(Icons.Rounded.NetworkCheck, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اختبار الاتصال", color = EduGreenDark, style = MaterialTheme.typography.labelLarge)
                        }
                        if (form.editingProviderId != null) {
                            OutlinedButton(
                                onClick = onCancelEditProvider,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EduLine),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("إلغاء", color = EduMuted)
                            }
                        }
                    }

                    EduYellowButton(
                        text = if (form.editingProviderId != null) "حفظ تعديل المزود" else stringResource(R.string.settings_add_api_cta),
                        onClick = onAddOrUpdateProvider,
                        icon = Icons.Rounded.Add,
                        testTag = "add_provider_button",
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item(key = "saved_providers_heading") {
                Text(
                    text = stringResource(R.string.settings_saved_providers_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(
                items = providers,
                key = { it.id }
            ) { profile ->
                val idx = providers.indexOfFirst { it.id == profile.id }
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "#${idx + 1} • ${profile.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk
                            )
                            val fallbackSuffix = if (profile.fallbackModels.isNotEmpty() && !profile.strictModel) {
                                " • احتياطي: ${profile.fallbackModels.joinToString(", ")}"
                            } else if (profile.strictModel) {
                                " • وضع صارم"
                            } else ""
                            val statusLabel = when (profile.runtimeStatus.uppercase()) {
                                "ACTIVE" -> "نشط"
                                "DISABLED" -> "معطّل"
                                "QUOTA_EXHAUSTED" -> "الحصة اليومية مستنفدة"
                                "RATE_LIMITED" -> "انتظار من المزود"
                                "INVALID_KEY" -> "مفتاح غير صالح"
                                else -> "خطأ مؤقت"
                            }
                            Text(
                                text = "${profile.provider.id} • ${profile.model}$fallbackSuffix • ${stringResource(R.string.settings_key_hidden)}",

                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                            Text(
                                text = "$statusLabel • ${profile.dailyRequestsUsed}/${if (profile.dailyRequestLimit == 0) "∞" else profile.dailyRequestLimit} طلب يومي",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (profile.runtimeStatus == "ACTIVE") EduGreenDark else EduRed
                            )
                        }
                        IconButton(
                            onClick = { onTestSavedProvider(profile) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NetworkCheck,
                                contentDescription = "اختبار الاتصال",
                                tint = EduBlue
                            )
                        }
                        IconButton(
                            onClick = { onEditProvider(profile) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "تعديل المزود",
                                tint = EduGreenDark
                            )
                        }
                        IconButton(
                            onClick = { onMoveProvider(profile.id, -1) },
                            enabled = idx > 0,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "رفع الأولوية",
                                tint = if (idx > 0) EduGreenDark else EduLine
                            )
                        }
                        IconButton(
                            onClick = { onMoveProvider(profile.id, 1) },
                            enabled = idx in 0 until providers.lastIndex,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "خفض الأولوية",
                                tint = if (idx in 0 until providers.lastIndex) EduGreenDark else EduLine
                            )
                        }
                        if (profile.runtimeStatus != "ACTIVE" && profile.enabled) {
                            TextButton(onClick = { onReactivateProvider(profile.id) }) { Text("إعادة تنشيط", color = EduGreenDark) }
                        }
                        EduPill(
                            text = if (profile.enabled) {
                                stringResource(R.string.settings_status_enabled)
                            } else {
                                stringResource(R.string.settings_status_disabled)
                            },
                            active = profile.enabled,
                            onClick = { onToggleProvider(profile.id) },
                            testTag = "toggle_provider_${profile.id}"
                        )
                        IconButton(
                            onClick = { onDeleteProvider(profile.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_provider_${profile.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete_provider),
                                tint = EduRed
                            )
                        }
                    }
                }
            }

            item(key = "prompt_templates_section") {
                EduPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "مكتبة قوالب الأوامر (Prompt Templates)",
                        style = MaterialTheme.typography.titleMedium,
                        color = EduInk
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TaskType.entries.forEach { t ->
                            EduPill(
                                text = t.labelAr,
                                active = newTplTask == t,
                                onClick = { newTplTask = t }
                            )
                        }
                    }
                    SettingsField(
                        value = newTplTitle,
                        onValueChange = { newTplTitle = it },
                        testTag = "new_template_title_input"
                    )
                    OutlinedTextField(
                        value = newTplInstructions,
                        onValueChange = { newTplInstructions = it },
                        placeholder = { Text("اكتب نص التوجيه المخصص...") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = EduFieldBg,
                            unfocusedContainerColor = EduFieldBg,
                            focusedBorderColor = EduGreen,
                            unfocusedBorderColor = EduLine
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_template_instructions_input")
                    )
                    Text(
                        text = "مخطط الاستجابة (JSON Schema أو مثال JSON تعاقدي)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedTextField(
                        value = newTplSchema,
                        onValueChange = { newTplSchema = it },
                        placeholder = { Text("ضع هنا مخطط الاستجابة الذي يجب أن يلتزم به النموذج...") },
                        minLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = EduFieldBg,
                            unfocusedContainerColor = EduFieldBg,
                            focusedBorderColor = EduGreen,
                            unfocusedBorderColor = EduLine
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("new_template_response_schema_input")
                    )
                    Text(
                        text = "خريطة توافق الحقول (اختيارية)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedTextField(
                        value = newTplMapping,
                        onValueChange = { newTplMapping = it },
                        placeholder = { Text("""{"rootArray":"items","fields":{"prompt":"stem","options":"choices","correctAnswer":"answer"}}""") },
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = EduFieldBg,
                            unfocusedContainerColor = EduFieldBg,
                            focusedBorderColor = EduGreen,
                            unfocusedBorderColor = EduLine
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("new_template_response_mapping_input")
                    )
                    templateValidationError?.let { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = EduRed)
                    }
                    Text(
                        text = "يُحفظ المخطط مع القالب ويُرسل ضمن البرومبت. اتركه فارغاً للمخرجات النصية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                    OutlinedButton(
                        onClick = {
                            if (newTplTitle.isNotBlank() && newTplInstructions.isNotBlank()) {
                                val schemaValid = newTplSchema.isBlank() || runCatching { Json.parseToJsonElement(newTplSchema).jsonObject }.isSuccess
                                val mappingValid = newTplMapping.isBlank() || runCatching { Json.parseToJsonElement(newTplMapping).jsonObject }.isSuccess
                                if (!schemaValid || !mappingValid) {
                                    templateValidationError = "مخطط الاستجابة وخريطة الحقول يجب أن يكونا JSON object صالحين."
                                } else {
                                    templateValidationError = null
                                    onSavePromptTemplate(newTplTitle, newTplTask, newTplInstructions, newTplSchema, newTplMapping, editingTemplateId)
                                    newTplTitle = ""
                                    newTplInstructions = ""
                                    newTplSchema = ""
                                    newTplMapping = ""
                                    editingTemplateId = null
                                }
                            }
                        },
                        enabled = newTplTitle.isNotBlank() && newTplInstructions.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EduGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("save_prompt_template_button")
                    ) {
                        Text(if (editingTemplateId == null) "إضافة قالب جديد" else "حفظ تعديلات القالب", color = EduGreenDark, fontWeight = FontWeight.Bold)
                    }
                    if (editingTemplateId != null) {
                        TextButton(onClick = {
                            editingTemplateId = null
                            newTplTitle = ""
                            newTplInstructions = ""
                            newTplSchema = ""
                            newTplMapping = ""
                        }) { Text("إلغاء التعديل", color = EduMuted) }
                    }

                    promptTemplates.forEach { tpl ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${tpl.title} • ${tpl.task.labelAr}", style = MaterialTheme.typography.titleSmall, color = EduInk)
                                Text(tpl.instructions, style = MaterialTheme.typography.bodySmall, color = EduMuted, maxLines = 2)
                                if (tpl.responseSchemaJson.isNotBlank()) Text("مخطط استجابة محفوظ • ${tpl.responseFormat}", style = MaterialTheme.typography.labelSmall, color = EduGreenDark)
                            }
                            TextButton(onClick = {
                                editingTemplateId = tpl.id
                                newTplTitle = tpl.title
                                newTplTask = tpl.task
                                newTplInstructions = tpl.instructions
                                newTplSchema = tpl.responseSchemaJson
                                newTplMapping = tpl.responseMappingJson
                            }) {
                                Text("تعديل", color = EduBlue)
                            }
                            TextButton(onClick = { onUseTemplateInStudio(tpl) }) {
                                Text("تطبيق", color = EduGreenDark)
                            }
                            if (!tpl.isBuiltIn) {
                                IconButton(
                                    onClick = { onDeletePromptTemplate(tpl.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف القالب", tint = EduRed)
                                }
                            }
                        }
                    }
                }
            }

            if (recentRuns.isNotEmpty()) {
                item(key = "recent_runs_heading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_recent_runs_heading),
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk
                        )
                        TextButton(
                            onClick = onClearRecentRuns,
                            modifier = Modifier.testTag("clear_recent_runs_button")
                        ) {
                            Text("مسح السجل", color = EduRed)
                        }
                    }
                }
                items(
                    items = recentRuns.take(8),
                    key = { "run_${it.id}" }
                ) { run ->
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${run.providerLabel} (${run.model}) • ${run.status}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (run.status == "SUCCESS") EduGreenDark else EduRed
                        )
                        Text(
                            text = "${run.task.labelAr} • ${run.lessonKey}" +
                                (run.errorSummary?.let { " • $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted
                        )
                    }
                }
            }

            item(key = "security_note") {
                EduPanel(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = EduSecurityBg
                ) {
                    Text(
                        text = stringResource(R.string.settings_security_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = EduSecurityText
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
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
            .testTag(testTag)
    )
}
