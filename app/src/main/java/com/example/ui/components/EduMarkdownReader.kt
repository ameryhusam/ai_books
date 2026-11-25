package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduWhite

@Composable
fun EduMarkdownReader(
    text: String,
    headerLabel: String = "قراءة منظمة للمحتوى",
    onSwitchToEdit: (() -> Unit)? = null,
    testTag: String = "formatted_draft_output",
    modifier: Modifier = Modifier
) {
    val lines = remember(text) { text.trim().lines() }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = EduFieldBg,
        border = BorderStroke(1.dp, EduLine),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = EduGreenDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = headerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = EduGreenDark
                    )
                }
                if (onSwitchToEdit != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EduWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onSwitchToEdit)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = EduInk,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "تعديل النص",
                                style = MaterialTheme.typography.labelSmall,
                                color = EduInk
                            )
                        }
                    }
                }
            }
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                when {
                    line.isBlank() -> Spacer(modifier = Modifier.size(2.dp))
                    line.startsWith("###") -> Text(
                        text = line.removePrefix("###").trim(),
                        style = MaterialTheme.typography.titleSmall,
                        color = EduInk,
                        fontWeight = FontWeight.Bold
                    )
                    line.startsWith("##") || line.startsWith("#") || (line.startsWith("**") && line.endsWith("**") && line.length > 4) -> {
                        val clean = line.trimStart('#').trim().removePrefix("**").removeSuffix("**").trim()
                        Text(
                            text = clean,
                            style = MaterialTheme.typography.titleMedium,
                            color = EduGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                        val content = line.drop(2).trim()
                        Text(
                            text = formatInlineBold("• $content"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduInk,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    line.startsWith("{") || line.startsWith("[") || line.startsWith("}") || line.startsWith("]") || line.startsWith("\"") -> {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = EduPurple
                        )
                    }
                    line.startsWith("---") -> {
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    else -> {
                        Text(
                            text = formatInlineBold(line),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduInk
                        )
                    }
                }
            }
        }
    }
}

private fun formatInlineBold(input: String) = buildAnnotatedString {
    var i = 0
    while (i < input.length) {
        val start = input.indexOf("**", i)
        if (start < 0) {
            append(input.substring(i))
            break
        }
        val end = input.indexOf("**", start + 2)
        if (end < 0) {
            append(input.substring(i))
            break
        }
        if (start > i) {
            append(input.substring(i, start))
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = EduInk)) {
            append(input.substring(start + 2, end))
        }
        i = end + 2
    }
}
