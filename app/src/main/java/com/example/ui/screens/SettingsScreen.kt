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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GenerationRunRecord
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.ui.ProviderFormState
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    form: ProviderFormState,
    providers: List<ProviderConfig>,
    recentRuns: List<GenerationRunRecord>,
    onBackToLibrary: () -> Unit,
    onSelectProviderType: (ProviderType) -> Unit,
    onLabelChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onFallbackModelsChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onAddProvider: () -> Unit,
    onToggleProvider: (String) -> Unit,
    onMoveProvider: (String, Int) -> Unit,
    onDeleteProvider: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToLibrary)

    var showApiKey by remember { mutableStateOf(false) }

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
                            listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite")
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

                    EduYellowButton(
                        text = stringResource(R.string.settings_add_api_cta),
                        onClick = onAddProvider,
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
                            val fallbackSuffix = if (profile.fallbackModels.isNotEmpty()) {
                                " • احتياطي: ${profile.fallbackModels.joinToString(", ")}"
                            } else ""
                            Text(
                                text = "${profile.provider.id} • ${profile.model}$fallbackSuffix • ${stringResource(R.string.settings_key_hidden)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
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

            if (recentRuns.isNotEmpty()) {
                item(key = "recent_runs_heading") {
                    Text(
                        text = stringResource(R.string.settings_recent_runs_heading),
                        style = MaterialTheme.typography.titleMedium,
                        color = EduInk,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
