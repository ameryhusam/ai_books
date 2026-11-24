package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class PdfPageRenderResult(
    val pageCount: Int,
    val bitmap: Bitmap?,
    val error: String?,
    val fileBytes: Long
)

private suspend fun renderPdfPage(pdfPath: String?, pageIndex: Int, targetWidthPx: Int = 960): PdfPageRenderResult {
    if (pdfPath.isNullOrBlank()) {
        return PdfPageRenderResult(0, null, "لا يوجد ملف PDF مرتبط بهذا العنصر.", 0L)
    }
    val file = File(pdfPath)
    if (!file.exists()) {
        return PdfPageRenderResult(0, null, "ملف PDF غير موجود في التخزين المحلي.", 0L)
    }
    val fileBytes = file.length()
    return withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    if (total <= 0) {
                        PdfPageRenderResult(0, null, "ملف PDF فارغ", fileBytes)
                    } else {
                        val safeIndex = pageIndex.coerceIn(0, total - 1)
                        renderer.openPage(safeIndex).use { page ->
                            val scale = (targetWidthPx.toFloat() / page.width.coerceAtLeast(1)).coerceIn(0.8f, 2.2f)
                            val w = (page.width * scale).toInt().coerceAtLeast(320)
                            val h = (page.height * scale).toInt().coerceAtLeast(420)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PdfPageRenderResult(total, bmp, null, fileBytes)
                        }
                    }
                }
            }
        }.getOrElse { err ->
            PdfPageRenderResult(0, null, err.message ?: "تعذر عرض PDF", fileBytes)
        }
    }
}

@Composable
fun LessonPdfViewer(
    pdfPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 380,
    onOpenFullscreen: (() -> Unit)? = null
) {
    var currentPage by remember(pdfPath) { mutableIntStateOf(0) }
    var pageCount by remember(pdfPath) { mutableIntStateOf(0) }
    var fileBytes by remember(pdfPath) { mutableStateOf(0L) }
    var renderedBitmap by remember(pdfPath) { mutableStateOf<Bitmap?>(null) }
    var errorText by remember(pdfPath) { mutableStateOf<String?>(null) }
    var internalFullscreen by remember(pdfPath) { mutableStateOf(false) }

    LaunchedEffect(pdfPath, currentPage) {
        val result = renderPdfPage(pdfPath, currentPage, targetWidthPx = 960)
        pageCount = result.pageCount
        renderedBitmap = result.bitmap
        errorText = result.error
        fileBytes = result.fileBytes
    }

    EduPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = EduGreenDark,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = EduInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (pageCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_prev_page")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "الصفحة السابقة"
                        )
                    }
                    Text(
                        text = "${currentPage + 1} / $pageCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk
                    )
                    IconButton(
                        onClick = { if (currentPage < pageCount - 1) currentPage++ },
                        enabled = currentPage < pageCount - 1,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_next_page")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "الصفحة التالية"
                        )
                    }
                    IconButton(
                        onClick = {
                            if (onOpenFullscreen != null) onOpenFullscreen() else internalFullscreen = true
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "تكبير عرض PDF",
                            tint = EduGreenDark
                        )
                    }
                }
            }
        }

        val bmp = renderedBitmap
        if (bmp != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EduWhite,
                border = BorderStroke(1.dp, EduLine),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeightDp.dp)
                    .clickable {
                        if (onOpenFullscreen != null) onOpenFullscreen() else internalFullscreen = true
                    }
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "صفحة PDF للدرس",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(EduWhite)
                        .padding(4.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EduFieldBg)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorText ?: "جاري تحضير صفحات PDF...",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
        }
        if (pageCount > 0) {
            val sizeKb = (fileBytes / 1024L).coerceAtLeast(1L)
            val isLikelyScanHeavy = pageCount > 0 && (fileBytes / pageCount) > 450_000L
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isLikelyScanHeavy) EduOrange.copy(alpha = 0.12f) else EduMint.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = if (isLikelyScanHeavy) EduOrange else EduGreenDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isLikelyScanHeavy) {
                            "$pageCount صفحة (${sizeKb} KB) • ملف مصوّر عالي الدقة — يُنصح بإبقاء «إرفاق صفحات PDF» مفعّلاً أو كتابة مقتطف نصي."
                        } else {
                            "جاهزية المصدر: $pageCount صفحة (${sizeKb} KB) • اضغط على الصفحة للتكبير والتصفح الكامل."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = EduInk
                    )
                }
            }
        }
    }

    if (internalFullscreen) {
        PdfReaderDialog(
            pdfPath = pdfPath,
            title = title,
            initialPage = currentPage,
            onDismiss = { internalFullscreen = false }
        )
    }
}

@Composable
fun PdfReaderDialog(
    pdfPath: String?,
    title: String,
    subtitle: String? = null,
    initialPage: Int = 0,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var currentPage by remember(pdfPath) { mutableIntStateOf(initialPage) }
    var pageCount by remember(pdfPath) { mutableIntStateOf(0) }
    var fileBytes by remember(pdfPath) { mutableStateOf(0L) }
    var renderedBitmap by remember(pdfPath) { mutableStateOf<Bitmap?>(null) }
    var errorText by remember(pdfPath) { mutableStateOf<String?>(null) }
    var zoomScale by remember(pdfPath, currentPage) { mutableFloatStateOf(1f) }
    var offsetX by remember(pdfPath, currentPage) { mutableFloatStateOf(0f) }
    var offsetY by remember(pdfPath, currentPage) { mutableFloatStateOf(0f) }

    LaunchedEffect(pdfPath, currentPage) {
        val result = renderPdfPage(pdfPath, currentPage, targetWidthPx = 1280)
        pageCount = result.pageCount
        renderedBitmap = result.bitmap
        errorText = result.error
        fileBytes = result.fileBytes
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = EduBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = EduGreenDark, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = EduWhite
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = EduWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sizeLabel = if (fileBytes > 0L) {
                                "${(fileBytes / 1024L).coerceAtLeast(1L)} KB"
                            } else ""
                            val infoText = listOfNotNull(
                                subtitle?.takeIf { it.isNotBlank() },
                                if (pageCount > 0) "صفحة ${currentPage + 1} من $pageCount" else null,
                                sizeLabel.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            if (infoText.isNotBlank()) {
                                Text(
                                    text = infoText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EduWhite.copy(alpha = 0.85f)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                zoomScale = (zoomScale - 0.25f).coerceIn(1f, 2.5f)
                                if (zoomScale <= 1.05f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                            enabled = zoomScale > 1f,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.ZoomOut, contentDescription = "تصغير", tint = EduWhite)
                        }
                        Text(
                            text = "${(zoomScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = EduWhite
                        )
                        IconButton(
                            onClick = { zoomScale = (zoomScale + 0.25f).coerceIn(1f, 2.5f) },
                            enabled = zoomScale < 2.5f,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.ZoomIn, contentDescription = "تكبير", tint = EduWhite)
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pdf_dialog_close")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "إغلاق", tint = EduWhite)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = renderedBitmap
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(EduWhite)
                                .pointerInput(currentPage, pdfPath) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val nextScale = (zoomScale * zoom).coerceIn(1f, 2.5f)
                                        zoomScale = nextScale
                                        if (nextScale > 1f) {
                                            offsetX = (offsetX + pan.x).coerceIn(-600f, 600f)
                                            offsetY = (offsetY + pan.y).coerceIn(-600f, 600f)
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "صفحة PDF ${currentPage + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = offsetX
                                        translationY = offsetY
                                    }
                            )
                        }
                    } else {
                        Text(
                            text = errorText ?: "جاري تحميل الصفحة...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EduMuted
                        )
                    }
                }

                if (pageCount > 0) {
                    Surface(color = EduWhite, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (pageCount > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items((0 until pageCount).toList(), key = { it }) { pageIdx ->
                                        EduPill(
                                            text = "ص ${pageIdx + 1}",
                                            active = currentPage == pageIdx,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                currentPage = pageIdx
                                            }
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (currentPage > 0) currentPage--
                                    },
                                    enabled = currentPage > 0,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "السابقة")
                                }
                                Text(
                                    text = "الصفحة ${currentPage + 1} من $pageCount",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = EduInk
                                )
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (currentPage < pageCount - 1) currentPage++
                                    },
                                    enabled = currentPage < pageCount - 1,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "التالية")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
