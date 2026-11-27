package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.workspace.BookIndexIntakeDraft
import com.example.ui.BookIndexReviewUiState
import com.example.ui.theme.EduBg

/**
 * Full-screen dialog wrapper hosting the modular [BookIndexReviewScreen] component.
 */
@Composable
fun BookIndexReviewDialog(
    state: BookIndexReviewUiState,
    isBusy: Boolean,
    progressStage: String? = null,
    progressPercent: Int? = null,
    onUpdateDraft: ((BookIndexIntakeDraft) -> BookIndexIntakeDraft) -> Unit,
    onRetryAiDiscovery: () -> Unit,
    onPopulateStarterUnits: () -> Unit,
    onPreviewPdf: () -> Unit,
    onApproveAndSplit: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(22.dp),
            color = EduBg
        ) {
            BookIndexReviewScreen(
                state = state,
                isBusy = isBusy,
                progressStage = progressStage,
                progressPercent = progressPercent,
                onUpdateDraft = onUpdateDraft,
                onRetryAiDiscovery = onRetryAiDiscovery,
                onPopulateStarterUnits = onPopulateStarterUnits,
                onPreviewPdf = onPreviewPdf,
                onApproveAndSplit = onApproveAndSplit,
                onClose = onDismiss,
                applySystemBarsPadding = true
            )
        }
    }
}
