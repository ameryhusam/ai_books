package com.example.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.InteractivePackageRecord
import com.example.model.SecuritySeverity
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

private const val VIRTUAL_ORIGIN_HOST = "edumini.local"

@Composable
fun InteractivePackagePreviewDialog(
    pkg: InteractivePackageRecord,
    studentMode: Boolean,
    onToggleStudentMode: () -> Unit,
    onApproveAndPublish: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = EduBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = EduGreen, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${pkg.title} (v${pkg.version})",
                                style = MaterialTheme.typography.titleMedium,
                                color = EduWhite
                            )
                            Text(
                                text = if (studentMode) {
                                    "معاينة الطالب (وضع معزول بدون إنترنت)"
                                } else {
                                    "معاينة المعلم + تقرير الفحص الأمني"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = EduWhite.copy(alpha = 0.9f)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "إغلاق المعاينة",
                                tint = EduWhite
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EduPill(
                        text = if (studentMode) "العودة لمعاينة المعلم" else "معاينة الطالب",
                        active = studentMode,
                        onClick = onToggleStudentMode,
                        testTag = "toggle_student_preview"
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (pkg.publishedForStudents) EduMint else EduWhite,
                        border = BorderStroke(1.dp, if (pkg.publishedForStudents) EduGreen else EduLine)
                    ) {
                        Text(
                            text = if (pkg.publishedForStudents) "منشور للطلاب" else "مسودة للمراجعة",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (pkg.publishedForStudents) EduGreenDark else EduMuted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                if (!studentMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EduPanel(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "تقرير الفحص: ${pkg.report.kind.labelAr} • ${pkg.report.totalFiles} ملف • ${(pkg.report.totalBytes / 1024).coerceAtLeast(1)} KB",
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk
                            )
                            Text(
                                text = "الامتدادات: ${pkg.report.assetExtensions.joinToString(", ")} • صفحات المصدر: ${pkg.sourcePages ?: "غير محددة"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                            pkg.report.findings.forEach { finding ->
                                val (icon, tint) = when (finding.severity) {
                                    SecuritySeverity.PASS -> Icons.Outlined.CheckCircle to EduGreenDark
                                    SecuritySeverity.WARNING -> Icons.Outlined.WarningAmber to EduOrange
                                    SecuritySeverity.BLOCKER -> Icons.Outlined.ErrorOutline to EduRed
                                }
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = finding.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduInk
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EduLine),
                    color = EduWhite
                ) {
                    if (pkg.report.hasBlockers) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = EduRed,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "تم إيقاف التشغيل لوجود مخالفات أمنية حرجة",
                                style = MaterialTheme.typography.titleSmall,
                                color = EduRed,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    } else {
                        SandboxedPackageWebView(
                            rootDir = pkg.rootDir,
                            entryFile = pkg.entryFile,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (!studentMode) {
                    Surface(
                        color = EduWhite,
                        border = BorderStroke(1.dp, EduLine),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            EduYellowButton(
                                text = if (pkg.publishedForStudents) {
                                    "منشور حالياً للطلاب (إعادة تأكيد)"
                                } else {
                                    "اعتماد المحتوى ونشره للطلاب"
                                },
                                onClick = onApproveAndPublish,
                                enabled = !pkg.report.hasBlockers,
                                icon = Icons.Rounded.Publish,
                                testTag = "approve_publish_package_button"
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxedPackageWebView(
    rootDir: String,
    entryFile: String,
    modifier: Modifier = Modifier
) {
    val root = File(rootDir)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url ?: return blockedResponse()
                        if (url.host != VIRTUAL_ORIGIN_HOST) {
                            // Strictly enforce offline sandbox: block external network calls
                            return blockedResponse()
                        }
                        val relPath = (url.path ?: "/").trimStart('/').ifBlank { entryFile }
                        val target = File(root, relPath)
                        val canonicalRoot = root.canonicalPath + File.separator
                        val canonicalTarget = runCatching { target.canonicalPath }.getOrNull()
                        if (canonicalTarget == null ||
                            (!canonicalTarget.startsWith(canonicalRoot) && canonicalTarget != root.canonicalPath) ||
                            !target.exists() ||
                            !target.isFile
                        ) {
                            return WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                404,
                                "Not Found",
                                mapOf("Access-Control-Allow-Origin" to "https://$VIRTUAL_ORIGIN_HOST"),
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                        val mime = mimeTypeFor(target.name)
                        val headers = mapOf(
                            "Access-Control-Allow-Origin" to "https://$VIRTUAL_ORIGIN_HOST",
                            "Cache-Control" to "no-store"
                        )
                        return WebResourceResponse(
                            mime,
                            if (mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json") "utf-8" else null,
                            200,
                            "OK",
                            headers,
                            FileInputStream(target)
                        )
                    }
                }
                val cleanEntry = entryFile.trimStart('/')
                loadUrl("https://$VIRTUAL_ORIGIN_HOST/$cleanEntry")
            }
        },
        update = { webView ->
            val targetUrl = "https://$VIRTUAL_ORIGIN_HOST/${entryFile.trimStart('/')}"
            if (webView.url != targetUrl) {
                webView.loadUrl(targetUrl)
            }
        }
    )
}

private fun blockedResponse(): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Blocked by Offline Sandbox",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )
}

private fun mimeTypeFor(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "application/javascript"
        "css" -> "text/css"
        "json", "gltf" -> "application/json"
        "glb" -> "model/gltf-binary"
        "bin" -> "application/octet-stream"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
    }
}

@Composable
fun InteractivePackageSummaryBadge(
    pkg: InteractivePackageRecord,
    modifier: Modifier = Modifier
) {
    val label = Uri.encode(pkg.kind.id).let {
        if (pkg.publishedForStudents) "v${pkg.version} • منشور للطلاب" else "v${pkg.version} • للمراجعة"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (pkg.publishedForStudents) EduGreenDark else EduMuted,
        modifier = modifier
    )
}
