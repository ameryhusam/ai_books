package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.UserRole
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
import com.example.ui.theme.EduYellow

/**
 * Reusable zero-permission Storage Access Framework (SAF) PDF document picker launcher.
 * Emits selected local PDF URIs straight into the book discovery intake pipeline.
 */
@Composable
fun rememberLocalPdfTextbookPickerLauncher(
    onPdfSelected: (Uri) -> Unit
): ManagedActivityResultLauncher<Array<String>, Uri?> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onPdfSelected(uri)
        }
    }

/**
 * Modern Jetpack Compose file picker card for selecting local PDF textbooks
 * by both students ([UserRole.LEARNER]) and instructors ([UserRole.ADMIN]).
 * Clicking either the interactive dashed drop-zone or CTA triggers the SAF PDF picker
 * wired into `MainViewModel.startBookPdfDiscoveryIntake(uri)`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalPdfTextbookPickerCard(
    userRole: UserRole,
    hasActiveAiProvider: Boolean,
    activeGradeKey: String? = null,
    isBusy: Boolean = false,
    compact: Boolean = false,
    onPickPdf: () -> Unit,
    cardTestTag: String = "local_pdf_picker_card",
    ctaTestTag: String = "local_pdf_picker_button",
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isLearner = userRole == UserRole.LEARNER
    val accent = if (isLearner) EduPurple else EduGreenDark
    val softSurfaceTint = if (isLearner) EduLilac else EduMint
    val triggerPicker = {
        if (!isBusy) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPickPdf()
        }
    }

    if (compact) {
        val shape = RoundedCornerShape(18.dp)
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag(cardTestTag),
            shape = shape,
            color = EduWhite,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                EduWhite,
                                softSurfaceTint.copy(alpha = 0.42f)
                            )
                        )
                    )
                    .clickable(enabled = !isBusy, onClick = triggerPicker)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PickerIconBadge(
                    isLearner = isLearner,
                    hasActiveAiProvider = hasActiveAiProvider,
                    sizeDp = 46
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (isLearner) R.string.pdf_picker_compact_title_learner
                            else R.string.pdf_picker_compact_title_admin
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = EduInk,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            if (hasActiveAiProvider) R.string.pdf_picker_badge_ai_ready
                            else R.string.pdf_picker_badge_manual_ready
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = triggerPicker,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLearner) EduPurple else EduGreen,
                        contentColor = EduWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag(ctaTestTag)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = EduWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pdf_picker_cta_compact),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }

    val outerShape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(cardTestTag),
        shape = outerShape,
        color = EduWhite,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            softSurfaceTint.copy(alpha = 0.35f),
                            EduWhite
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top capability & grade context chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (hasActiveAiProvider) EduMint else EduFieldBg,
                    border = BorderStroke(1.dp, if (hasActiveAiProvider) EduGreen.copy(alpha = 0.4f) else EduLine)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (hasActiveAiProvider) Icons.Rounded.AutoAwesome else Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = if (hasActiveAiProvider) EduGreenDark else EduMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(
                                if (hasActiveAiProvider) R.string.pdf_picker_badge_ai_ready
                                else R.string.pdf_picker_badge_manual_ready
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasActiveAiProvider) EduGreenDark else EduInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!activeGradeKey.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = softSurfaceTint,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = activeGradeKey.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Interactive dashed drop-zone surface
            val dropZoneShape = RoundedCornerShape(16.dp)
            val dashColor = accent.copy(alpha = 0.45f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dropZoneShape)
                    .background(EduFieldBg)
                    .drawBehind {
                        val strokePx = 1.5.dp.toPx()
                        val cornerPx = 16.dp.toPx()
                        drawRoundRect(
                            color = dashColor,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(
                                width = strokePx,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                            )
                        )
                    }
                    .clickable(enabled = !isBusy, onClick = triggerPicker)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PickerIconBadge(
                        isLearner = isLearner,
                        hasActiveAiProvider = hasActiveAiProvider,
                        sizeDp = 56
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pdf_picker_kicker),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(
                                if (isLearner) R.string.pdf_picker_title_learner
                                else R.string.pdf_picker_title_admin
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                if (isLearner) R.string.pdf_picker_sub_learner
                                else R.string.pdf_picker_sub_admin
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted
                        )
                    }
                }
            }

            // 3-step pipeline preview chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    stringResource(R.string.pdf_picker_step_pick),
                    stringResource(R.string.pdf_picker_step_review),
                    stringResource(R.string.pdf_picker_step_split)
                ).forEachIndexed { idx, stepLabel ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (idx == 0) softSurfaceTint.copy(alpha = 0.65f) else EduFieldBg,
                        border = BorderStroke(1.dp, if (idx == 0) accent.copy(alpha = 0.3f) else EduLine)
                    ) {
                        Text(
                            text = stepLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (idx == 0) accent else EduInk,
                            fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Primary CTA button with animated busy state
            Button(
                onClick = triggerPicker,
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EduYellow,
                    contentColor = EduInk,
                    disabledContainerColor = EduYellow.copy(alpha = 0.55f),
                    disabledContentColor = EduInk.copy(alpha = 0.65f)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag(ctaTestTag)
            ) {
                AnimatedContent(
                    targetState = isBusy,
                    transitionSpec = {
                        fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                            fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                    },
                    label = "pdf_picker_cta_state"
                ) { busy ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = EduInk,
                                strokeWidth = 2.2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.UploadFile,
                                contentDescription = stringResource(R.string.pdf_picker_cd_icon),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (busy) R.string.pdf_picker_cta_busy
                                else R.string.pdf_picker_cta_button
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = EduGreenDark,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.pdf_picker_saf_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = EduMuted
                )
            }
        }
    }
}

@Composable
private fun PickerIconBadge(
    isLearner: Boolean,
    hasActiveAiProvider: Boolean,
    sizeDp: Int
) {
    val accent = if (isLearner) EduPurple else EduGreen
    val bgTint = if (isLearner) EduLilac else EduMint
    Box(
        modifier = Modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape((sizeDp * 0.28f).dp),
            color = bgTint,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
            modifier = Modifier.size(sizeDp.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = stringResource(R.string.pdf_picker_cd_icon),
                    tint = accent,
                    modifier = Modifier.size((sizeDp * 0.52f).dp)
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = if (hasActiveAiProvider) EduGreenDark else EduYellow,
            border = BorderStroke(1.5.dp, EduWhite),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 3.dp, y = 3.dp)
                .size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (hasActiveAiProvider) Icons.Rounded.AutoAwesome else Icons.Rounded.UploadFile,
                    contentDescription = null,
                    tint = if (hasActiveAiProvider) EduWhite else EduInk,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}
