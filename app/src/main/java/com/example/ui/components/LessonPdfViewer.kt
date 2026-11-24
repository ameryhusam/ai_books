package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LessonPdfViewer(
    pdfPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 380
) {
    var currentPage by remember(pdfPath) { mutableIntStateOf(0) }
    var pageCount by remember(pdfPath) { mutableIntStateOf(0) }
    var renderedBitmap by remember(pdfPath) { mutableStateOf<Bitmap?>(null) }
    var errorText by remember(pdfPath) { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfPath, currentPage) {
        if (pdfPath.isNullOrBlank()) {
            pageCount = 0
            renderedBitmap = null
            errorText = "لا يوجد ملف PDF مرتبط بهذا الدرس."
            return@LaunchedEffect
        }
        val file = File(pdfPath)
        if (!file.exists()) {
            pageCount = 0
            renderedBitmap = null
            errorText = "ملف PDF غير موجود في التخزين المحلي."
            return@LaunchedEffect
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val total = renderer.pageCount
                        if (total <= 0) {
                            Triple(0, null as Bitmap?, "ملف PDF فارغ")
                        } else {
                            val safeIndex = currentPage.coerceIn(0, total - 1)
                            renderer.openPage(safeIndex).use { page ->
                                val scale = (960f / page.width.coerceAtLeast(1)).coerceIn(0.8f, 1.8f)
                                val w = (page.width * scale).toInt().coerceAtLeast(320)
                                val h = (page.height * scale).toInt().coerceAtLeast(420)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                Triple(total, bmp, null)
                            }
                        }
                    }
                }
            }.getOrElse { err ->
                Triple(0, null as Bitmap?, err.message ?: "تعذر عرض PDF")
            }
        }
        pageCount = result.first
        renderedBitmap = result.second
        errorText = result.third
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
                    color = EduInk
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
                }
            }
        }

        val bmp = renderedBitmap
        if (bmp != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EduWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, EduLine),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeightDp.dp)
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
    }
}
