package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiModelDefinition
import com.example.model.ProviderConfig
import com.example.model.ProviderType

@Composable
fun GenerationModelPickerDialog(
    provider: ProviderType,
    models: List<AiModelDefinition>,
    profiles: List<ProviderConfig>,
    defaultModelId: String,
    selectedModelId: String,
    selectedProfileId: String,
    onSelectModel: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onConfirm: (modelId: String, profileId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val availableModels = models.filter {
        it.provider == provider && it.isEnabled && it.supportsGenerateContent &&
            it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
    }
    val availableProfiles = profiles.filter {
        it.provider == provider && it.enabled && it.apiKey.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار نموذج وملف اتصال") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("النموذج", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    items(availableModels, key = { it.modelId }) { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModelId == model.modelId,
                                onClick = { onSelectModel(model.modelId) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    model.modelId + if (model.modelId == defaultModelId) " • افتراضي" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text("ملف اتصال API", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp)) {
                    items(availableProfiles, key = { it.id }) { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProfileId == profile.id,
                                onClick = { onSelectProfile(profile.id) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "المزود: ${profile.provider.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (availableModels.isEmpty()) {
                    Text("لا توجد نماذج مفعّلة لهذا المزود في قائمة مدير النظام.")
                }
                if (availableProfiles.isEmpty()) {
                    Text("لا يوجد ملف اتصال API فعّال لهذا المزود.")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = availableModels.any { it.modelId == selectedModelId } &&
                    availableProfiles.any { it.id == selectedProfileId },
                onClick = { onConfirm(selectedModelId, selectedProfileId) }
            ) { Text("متابعة التوليد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
