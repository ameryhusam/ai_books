package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.workspace.UriIO
import com.example.model.BookProgressSummary
import com.example.model.BookRef
import com.example.ui.theme.EduBlue
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduWhite
import com.example.ui.theme.EduYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun BookCoverImage(
    coverPath: String?,
    bookTitle: String,
    subjectKey: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    targetWidthPx: Int = 420
) {
    val context = LocalContext.current
    var bitmap by remember(coverPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(coverPath, targetWidthPx) {
        bitmap = if (coverPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeScaledBitmap(context, coverPath, targetWidthPx)
            }
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val paletteIndex = remember(subjectKey) { abs(subjectKey.hashCode()) % 4 }
    val gradientColors = when (paletteIndex) {
        0 -> listOf(EduGreenDark, EduGreen)
        1 -> listOf(Color(0xFF283593), EduBlue)
        2 -> listOf(Color(0xFF4A148C), EduPurple)
        else -> listOf(Color(0xFF9C4221), EduOrange)
    }

    Surface(
        modifier = modifier.clip(shape),
        shape = shape,
        color = gradientColors.first(),
        border = BorderStroke(1.dp, EduLine.copy(alpha = 0.6f))
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = "غلاف كتاب $bookTitle",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Subtle spine crease highlight on the start edge
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.16f))
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(gradientColors))
            ) {
                // Book spine shading
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.18f))
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EduWhite.copy(alpha = 0.22f)
                    ) {
                        Text(
                            text = subjectKey.take(6),
                            style = MaterialTheme.typography.labelSmall,
                            color = EduYellow,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = EduWhite.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = EduWhite,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookCoverPreviewDialog(
    book: BookRef,
    progress: BookProgressSummary?,
    onOpenBookPdf: (() -> Unit)?,
    onSelectBook: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = EduInk,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "إغلاق", tint = EduMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BookCoverImage(
                    coverPath = book.coverPath,
                    bookTitle = book.title,
                    subjectKey = book.subjectKey,
                    cornerRadius = 16.dp,
                    targetWidthPx = 800,
                    modifier = Modifier
                        .width(170.dp)
                        .aspectRatio(0.72f)
                        .testTag("book_cover_dialog_image")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = book.subjectKey,
                            style = MaterialTheme.typography.titleSmall,
                            color = EduGreenDark
                        )
                        Text(
                            text = "${book.lessonsCount} درس",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted
                        )
                        if (book.coverPath != null) {
                            Text(
                                text = "cover.png متاح",
                                style = MaterialTheme.typography.labelSmall,
                                color = EduGreenDark
                            )
                        }
                    }
                    if (progress != null) {
                        RadialProgressIndicator(
                            percentage = progress.completionPercent,
                            size = 64.dp,
                            strokeWidth = 6.dp,
                            label = "إنجاز"
                        )
                    }
                }
                if (onOpenBookPdf != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenBookPdf()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EduGreen,
                            contentColor = EduWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("book_cover_open_pdf_button")
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("عرض ملف الكتاب (PDF)", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = {
                        onSelectBook()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EduGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = EduGreenDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استعراض دروس الكتاب", color = EduGreenDark, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = EduMuted)
            }
        }
    )
}

private val coverCache = object : LruCache<String, Bitmap>(24) {}

private fun decodeScaledBitmap(context: Context, pathOrUri: String, reqWidth: Int): Bitmap? {
    val key = "$pathOrUri#$reqWidth"
    coverCache.get(key)?.let { if (!it.isRecycled) return it }
    if (!UriIO.exists(context, pathOrUri)) return null
    return runCatching {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        UriIO.openInputStream(context, pathOrUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        var inSampleSize = 1
        if (boundsOpts.outWidth > reqWidth && reqWidth > 0) {
            var halfWidth = boundsOpts.outWidth / 2
            while ((halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
                halfWidth /= 2
            }
        }
        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        UriIO.openInputStream(context, pathOrUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }?.also { decoded ->
            coverCache.put(key, decoded)
        }
    }.getOrNull()
}
