package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.workspace.DirectWorkspaceBinder
import com.example.data.workspace.UriIO
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
import java.io.InputStream

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

private class EduMiniWebBridge(
    private val onQuestionAttempt: ((prompt: String, conceptTag: String, selected: String, correct: String, isCorrect: Boolean) -> Unit)?,
    private val onCompleted: ((scorePercent: Int, earnedXp: Int) -> Unit)?
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun recordQuestionAttempt(
        prompt: String?,
        conceptTag: String?,
        selected: String?,
        correct: String?,
        isCorrect: Boolean
    ) {
        val cb = onQuestionAttempt ?: return
        mainHandler.post {
            cb(
                prompt.orEmpty(),
                conceptTag.orEmpty(),
                selected.orEmpty(),
                correct.orEmpty(),
                isCorrect
            )
        }
    }

    @JavascriptInterface
    fun completeInteractive(scorePercent: Int, earnedXp: Int) {
        val cb = onCompleted ?: return
        mainHandler.post {
            cb(scorePercent, earnedXp)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxedPackageWebView(
    rootDir: String,
    entryFile: String,
    modifier: Modifier = Modifier,
    onWebQuestionAnswered: ((prompt: String, conceptTag: String, selected: String, correct: String, isCorrect: Boolean) -> Unit)? = null,
    onWebInteractiveCompleted: ((scorePercent: Int, earnedXp: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    var loadProgress by remember(rootDir, entryFile) { mutableIntStateOf(0) }
    var jsErrorText by remember(rootDir, entryFile) { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(rootDir, entryFile) {
        onDispose {
            webViewRef?.apply {
                removeJavascriptInterface("EduMiniBridge")
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    Column(modifier = modifier) {
        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = EduGreen,
                trackColor = EduMint
            )
        }
        val err = jsErrorText
        if (err != null) {
            Surface(
                color = EduOrange.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "تنبيه تشغيل تفاعلي: $err",
                    style = MaterialTheme.typography.labelSmall,
                    color = EduInk,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.textZoom = 100
                        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = false
                        addJavascriptInterface(
                            EduMiniWebBridge(
                                onQuestionAttempt = onWebQuestionAnswered,
                                onCompleted = onWebInteractiveCompleted
                            ),
                            "EduMiniBridge"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                    jsErrorText = consoleMessage.message()?.take(140)
                                }
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url ?: return blockedResponse()
                                if (url.host != VIRTUAL_ORIGIN_HOST) {
                                    return blockedResponse()
                                }
                                if (request.method.equals("OPTIONS", ignoreCase = true)) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        204,
                                        "No Content",
                                        corsHeaders(),
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                val relPath = (url.path ?: "/").trimStart('/').ifBlank { entryFile }
                                return serveLocalAssetResponse(
                                    context = ctx,
                                    rootDir = rootDir,
                                    relPath = relPath,
                                    rangeHeader = request.requestHeaders["Range"] ?: request.requestHeaders["range"]
                                )
                            }
                        }
                        val cleanEntry = entryFile.trimStart('/')
                        loadUrl("https://$VIRTUAL_ORIGIN_HOST/$cleanEntry")
                        webViewRef = this
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
    }
}

private fun serveLocalAssetResponse(
    context: Context,
    rootDir: String,
    relPath: String,
    rangeHeader: String?
): WebResourceResponse {
    if (relPath.split('/').any { it == ".." || it == "." }) {
        return blockedResponse()
    }

    var resolvedStream: InputStream? = null
    var totalLength = -1L
    var fileName = relPath.substringAfterLast('/')

    if (rootDir.startsWith("safroot::")) {
        val docUri = DirectWorkspaceBinder.resolveSafPackageAssetUri(context, rootDir, relPath)
        if (docUri != null) {
            resolvedStream = UriIO.openInputStream(context, docUri.toString())
            totalLength = UriIO.fileLength(context, docUri.toString())
        }
    } else if (UriIO.isContentUri(rootDir)) {
        // Standalone SAF document URI directly bound
        resolvedStream = UriIO.openInputStream(context, rootDir)
        totalLength = UriIO.fileLength(context, rootDir)
    } else {
        val root = File(rootDir)
        val target = File(root, relPath)
        val canonicalRoot = runCatching { root.canonicalPath + File.separator }.getOrNull()
        val canonicalTarget = runCatching { target.canonicalPath }.getOrNull()
        if (canonicalRoot != null && canonicalTarget != null &&
            (canonicalTarget.startsWith(canonicalRoot) || canonicalTarget == root.canonicalPath) &&
            target.exists() && target.isFile
        ) {
            resolvedStream = FileInputStream(target)
            totalLength = target.length()
            fileName = target.name
        }
    }

    if (resolvedStream == null) {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            corsHeaders(),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    val mime = mimeTypeFor(fileName)
    val encoding = if (mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json") "utf-8" else null

    // Inject mobile-responsive viewport + fluid layout stylesheet for HTML pages so desktop-authored lessons fit mobile screens cleanly
    if (mime == "text/html") {
        val rawHtml = resolvedStream.use { it.readBytes().toString(Charsets.UTF_8) }
        val responsiveHtml = injectMobileResponsiveWrapper(rawHtml)
        val outBytes = responsiveHtml.toByteArray(Charsets.UTF_8)
        return WebResourceResponse(
            mime,
            "utf-8",
            200,
            "OK",
            corsHeaders() + ("Content-Length" to outBytes.size.toString()),
            ByteArrayInputStream(outBytes)
        )
    }

    // Handle HTTP Range requests (206 Partial Content) for local HTML5 audio/video & GLB streaming
    if (!rangeHeader.isNullOrBlank() && totalLength > 0) {
        val rangeMatch = Regex("bytes=(\\d+)-(\\d*)").find(rangeHeader)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toLongOrNull() ?: 0L
            val end = rangeMatch.groupValues[2].toLongOrNull()?.coerceAtMost(totalLength - 1) ?: (totalLength - 1)
            if (start in 0..end) {
                val allBytes = resolvedStream.use { it.readBytes() }
                val safeEnd = end.toInt().coerceAtMost(allBytes.size - 1)
                val safeStart = start.toInt().coerceAtMost(safeEnd)
                val slice = allBytes.copyOfRange(safeStart, safeEnd + 1)
                val headers = corsHeaders() + mapOf(
                    "Accept-Ranges" to "bytes",
                    "Content-Range" to "bytes $safeStart-$safeEnd/$totalLength",
                    "Content-Length" to slice.size.toString()
                )
                return WebResourceResponse(
                    mime,
                    encoding,
                    206,
                    "Partial Content",
                    headers,
                    ByteArrayInputStream(slice)
                )
            }
        }
    }

    val headers = buildMap {
        putAll(corsHeaders())
        put("Accept-Ranges", "bytes")
        if (totalLength > 0L) put("Content-Length", totalLength.toString())
    }
    return WebResourceResponse(mime, encoding, 200, "OK", headers, resolvedStream)
}

private fun injectMobileResponsiveWrapper(rawHtml: String): String {
    val viewportMeta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes\">"
    val mobileCss = """
        <style id="edu-mini-mobile-adapter">
        html, body { max-width: 100% !important; overflow-x: hidden !important; box-sizing: border-box !important; touch-action: manipulation; -webkit-text-size-adjust: 100%; }
        *, *::before, *::after { box-sizing: border-box; }
        img, svg, canvas, video, model-viewer { max-width: 100% !important; height: auto !important; }
        table { display: block; max-width: 100%; overflow-x: auto; }
        pre, code { white-space: pre-wrap; word-break: break-word; }
        button, [role="button"], input, select { min-height: 40px; }
        </style>
    """.trimIndent()

    var updated = rawHtml
    if (!updated.contains("name=\"viewport\"", ignoreCase = true) && !updated.contains("name='viewport'", ignoreCase = true)) {
        val headMatch = Regex("<head>", RegexOption.IGNORE_CASE).find(updated)
        val htmlMatch = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(updated)
        updated = when {
            headMatch != null ->
                updated.replaceRange(headMatch.range, "${headMatch.value}\n$viewportMeta")
            htmlMatch != null ->
                updated.replaceRange(htmlMatch.range, "${htmlMatch.value}\n<head>$viewportMeta</head>")
            else -> "<!DOCTYPE html><html dir=\"rtl\" lang=\"ar\"><head>$viewportMeta</head><body>$updated</body></html>"
        }
    }
    if (!updated.contains("edu-mini-mobile-adapter")) {
        val closeHeadMatch = Regex("</head>", RegexOption.IGNORE_CASE).find(updated)
        updated = if (closeHeadMatch != null) {
            updated.replaceRange(closeHeadMatch.range, "$mobileCss\n${closeHeadMatch.value}")
        } else {
            "$mobileCss\n$updated"
        }
    }
    return updated
}

private fun corsHeaders(): Map<String, String> = mapOf(
    "Access-Control-Allow-Origin" to "https://$VIRTUAL_ORIGIN_HOST",
    "Access-Control-Allow-Methods" to "GET, OPTIONS",
    "Cache-Control" to "no-store"
)

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
        "obj" -> "text/plain"
        "mtl" -> "text/plain"
        "hdr" -> "image/vnd.radiance"
        "usdz" -> "model/vnd.usdz+zip"
        "wasm" -> "application/wasm"
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
        "otf" -> "font/otf"
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
