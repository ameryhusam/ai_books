package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiModelDefinition
import com.example.model.ProviderConfig
import com.example.model.ProviderType
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerationModelPickerDialog(
    provider: ProviderType,
    models: List<AiModelDefinition>,
    apiKeys: List<ProviderConfig>,
    defaultModelId: String,
    selectedModelId: String,
    onSelectModel: (String) -> Unit,
    onConfirm: (modelId: String) -> Unit,
    onDismiss: () -> Unit,
    canManageDefault: Boolean = false,
    onSetDefaultModel: ((ProviderType, String) -> Unit)? = null
) {
    val availableModels = models.filter {
        it.provider == provider && it.isEnabled && it.supportsGenerateContent &&
            it.lifecycleStatus != AiModelDefinition.STATUS_DEPRECATED
    }.sortedWith(
        compareBy<AiModelDefinition> { it.modelId != defaultModelId && !it.isDefault }
            .thenBy { it.modelId }
            .thenBy { it.displayName }
    )
    val availableApiKeys = apiKeys.filter {
        it.provider == provider && it.enabled && it.apiKey.isNotBlank()
    }
    val fallbackModels = availableModels.filter { it.modelId != selectedModelId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EduMint,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = EduGreenDark,
                        modifier = Modifier.padding(7.dp)
                    )
                }
                Column {
                    Text(
                        text = "اختيار نموذج ${provider.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = EduInk
                    )
                    Text(
                        text = "يدعم التبديل التلقائي إلى النماذج الاحتياطية عند الضغط أو الأعطال",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableModels, key = { it.modelId }) { model ->
                        val isSelected = selectedModelId == model.modelId
                        val isDefault = model.modelId == defaultModelId || model.isDefault
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) EduMint.copy(alpha = 0.55f) else EduFieldBg,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) EduGreen else EduLine
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .minimumInteractiveComponentSize()
                                .clickable { onSelectModel(model.modelId) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectModel(model.modelId) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = EduGreenDark,
                                        unselectedColor = EduMuted
                                    )
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EduInk
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = model.modelId,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EduMuted
                                        )
                                        if (isDefault) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = EduMint
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Star,
                                                        contentDescription = null,
                                                        tint = EduGreenDark,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "الافتراضي",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = EduGreenDark
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = EduLilac
                                            ) {
                                                Text(
                                                    text = "احتياطي تلقائي",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EduPurple,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (canManageDefault && onSetDefaultModel != null && !isDefault) {
                                        TextButton(
                                            onClick = { onSetDefaultModel(provider, model.modelId) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text(
                                                text = "تعيين كنموذج افتراضي",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = EduGreenDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (fallbackModels.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EduFieldBg,
                        border = BorderStroke(1.dp, EduLine),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "ترتيب التحويل التلقائي (Fallback):",
                                style = MaterialTheme.typography.labelSmall,
                                color = EduGreenDark
                            )
                            Text(
                                text = fallbackModels.joinToString(" ← ") { it.modelId },
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                        }
                    }
                }

                if (availableApiKeys.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = EduGreenDark,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "مفاتيح API المفعّلة للمزود: ${availableApiKeys.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted
                        )
                    }
                }
                if (availableModels.isEmpty()) {
                    Text(
                        text = "لا توجد نماذج مفعّلة لهذا المزود في قائمة مدير النظام.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduRed
                    )
                }
                if (availableApiKeys.isEmpty()) {
                    Text(
                        text = "لا يوجد مفتاح API فعّال لهذا المزود. أضف مفتاحاً من الإعدادات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EduRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = availableModels.any { it.modelId == selectedModelId } && availableApiKeys.isNotEmpty(),
                onClick = { onConfirm(selectedModelId) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EduGreen,
                    contentColor = EduWhite
                ),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("متابعة التوليد", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("إلغاء", color = EduMuted)
            }
        }
    )
}
