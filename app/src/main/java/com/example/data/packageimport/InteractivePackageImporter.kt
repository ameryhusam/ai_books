package com.example.data.packageimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.InteractivePackageReport
import com.example.model.LessonRef
import com.example.model.PackageKind
import com.example.model.SecurityFinding
import com.example.model.SecuritySeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class ImportedPackageStaging(
    val kind: PackageKind,
    val title: String,
    val rootDir: String,
    val entryFile: String,
    val report: InteractivePackageReport
)

class InteractivePackageImporter(private val context: Context) {

    private val packagesRoot: File
        get() = File(context.filesDir, "edumini/packages")

    suspend fun importFromUri(
        uri: Uri,
        lesson: LessonRef,
        sourcePages: String?,
        nextVersion: Int
    ): ImportedPackageStaging = withContext(Dispatchers.IO) {
        val displayName = queryFileName(uri) ?: "interactive_bundle"
        val declaredSize = queryFileSize(uri)
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isZip = displayName.endsWith(".zip", ignoreCase = true) ||
            mime.contains("zip", ignoreCase = true)

        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر فتح الملف المختار")

        stream.use { input ->
            importFromStream(
                inputStream = input,
                fileName = displayName,
                isZip = isZip,
                declaredSizeBytes = declaredSize,
                lesson = lesson,
                sourcePages = sourcePages,
                version = nextVersion
            )
        }
    }

    suspend fun importFromStream(
        inputStream: InputStream,
        fileName: String,
        isZip: Boolean,
        declaredSizeBytes: Long?,
        lesson: LessonRef,
        sourcePages: String?,
        version: Int
    ): ImportedPackageStaging = withContext(Dispatchers.IO) {
        val maxBytes = if (isZip) MAX_ZIP_PACKAGE_BYTES else MAX_HTML_FILE_BYTES
        if (declaredSizeBytes != null && declaredSizeBytes > maxBytes) {
            throw IllegalArgumentException(
                if (isZip) "حجم حزمة ZIP يتجاوز 80 MB" else "حجم ملف HTML يتجاوز 15 MB"
            )
        }

        val safeLessonDir = "${lesson.grade}_${lesson.bookKey}_${lesson.unitKey}_${lesson.lessonKey}"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val versionDir = File(packagesRoot, "$safeLessonDir/v$version")
        if (versionDir.exists()) versionDir.deleteRecursively()
        versionDir.mkdirs()

        try {
            if (!isZip) {
                importStandaloneHtml(
                    inputStream = inputStream,
                    fileName = fileName,
                    versionDir = versionDir,
                    lesson = lesson,
                    sourcePages = sourcePages
                )
            } else {
                importZipBundle(
                    inputStream = inputStream,
                    fileName = fileName,
                    versionDir = versionDir,
                    lesson = lesson,
                    sourcePages = sourcePages
                )
            }
        } catch (e: Exception) {
            versionDir.deleteRecursively()
            throw e
        }
    }

    private fun importStandaloneHtml(
        inputStream: InputStream,
        fileName: String,
        versionDir: File,
        lesson: LessonRef,
        sourcePages: String?
    ): ImportedPackageStaging {
        val rawBytes = readBounded(inputStream, MAX_HTML_FILE_BYTES)
        val html = rawBytes.toString(StandardCharsets.UTF_8)
        if (!html.contains("<html", ignoreCase = true) &&
            !html.contains("<!doctype", ignoreCase = true) &&
            !html.contains("<body", ignoreCase = true)
        ) {
            throw IllegalArgumentException("الملف لا يبدو كمستند HTML صالح")
        }

        val entryFile = File(versionDir, "index.html")
        entryFile.writeText(html, StandardCharsets.UTF_8)

        val report = inspectExtractedPackage(
            kind = PackageKind.STANDALONE_HTML,
            fileName = fileName,
            versionDir = versionDir,
            entryRelativePath = "index.html",
            totalFiles = 1,
            totalBytes = rawBytes.size.toLong(),
            lesson = lesson,
            sourcePages = sourcePages
        )

        val title = extractHtmlTitle(html) ?: fileName.removeSuffix(".html").removeSuffix(".htm")
        return ImportedPackageStaging(
            kind = PackageKind.STANDALONE_HTML,
            title = title.ifBlank { "محتوى HTML تفاعلي" },
            rootDir = versionDir.absolutePath,
            entryFile = "index.html",
            report = report
        )
    }

    private fun importZipBundle(
        inputStream: InputStream,
        fileName: String,
        versionDir: File,
        lesson: LessonRef,
        sourcePages: String?
    ): ImportedPackageStaging {
        val extractedRelPaths = mutableListOf<String>()
        val seen = HashSet<String>()
        var expandedBytes = 0L

        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val normalized = safeZipPath(entry.name)
                if (!seen.add(normalized.lowercase())) {
                    throw IllegalArgumentException("ملف مكرر داخل الحزمة: $normalized")
                }
                val ext = normalized.substringAfterLast('.', "").lowercase()
                if (ext in BLOCKED_EXTENSIONS) {
                    throw IllegalArgumentException("امتداد غير مسموح أمنياً داخل الحزمة: .$ext ($normalized)")
                }
                if (ext.isNotBlank() && ext !in ALLOWED_EXTENSIONS) {
                    throw IllegalArgumentException("امتداد غير مدعوم داخل حزمة الدرس: .$ext ($normalized)")
                }
                extractedRelPaths.add(normalized)
                if (extractedRelPaths.size > MAX_PACKAGE_FILES) {
                    throw IllegalArgumentException("الحزمة تحتوي أكثر من $MAX_PACKAGE_FILES ملف")
                }

                val target = File(versionDir, normalized)
                val canonicalRoot = versionDir.canonicalPath + File.separator
                if (!target.canonicalPath.startsWith(canonicalRoot)) {
                    throw IllegalArgumentException("مسار غير آمن داخل ZIP: ${entry.name}")
                }
                target.parentFile?.mkdirs()

                FileOutputStream(target).use { out ->
                    while (true) {
                        val read = zis.read(buf)
                        if (read <= 0) break
                        expandedBytes += read
                        if (expandedBytes > MAX_ZIP_EXPANDED_BYTES) {
                            throw IllegalArgumentException("حجم الحزمة بعد فك الضغط يتجاوز 150 MB")
                        }
                        out.write(buf, 0, read)
                    }
                }
                zis.closeEntry()
            }
        }

        if (extractedRelPaths.isEmpty()) {
            throw IllegalArgumentException("حزمة ZIP فارغة")
        }

        val entryRelative = findEntryHtml(extractedRelPaths)
            ?: throw IllegalArgumentException("يجب أن تحتوي حزمة ZIP على index.html في الجذر أو المجلد الرئيسي")

        val entryHtml = File(versionDir, entryRelative).readText(StandardCharsets.UTF_8)
        val report = inspectExtractedPackage(
            kind = PackageKind.ZIP_BUNDLE,
            fileName = fileName,
            versionDir = versionDir,
            entryRelativePath = entryRelative,
            totalFiles = extractedRelPaths.size,
            totalBytes = expandedBytes,
            lesson = lesson,
            sourcePages = sourcePages
        )

        val title = extractHtmlTitle(entryHtml) ?: fileName.removeSuffix(".zip")
        return ImportedPackageStaging(
            kind = PackageKind.ZIP_BUNDLE,
            title = title.ifBlank { "حزمة تفاعلية ثلاثية الأبعاد" },
            rootDir = versionDir.absolutePath,
            entryFile = entryRelative,
            report = report
        )
    }

    fun inspectExtractedPackage(
        kind: PackageKind,
        fileName: String,
        versionDir: File,
        entryRelativePath: String,
        totalFiles: Int,
        totalBytes: Long,
        lesson: LessonRef,
        sourcePages: String?
    ): InteractivePackageReport {
        val findings = mutableListOf<SecurityFinding>()
        val allFiles = versionDir.walkTopDown().filter { it.isFile }.toList()
        val extensions = allFiles.mapNotNull { file ->
            file.extension.lowercase().takeIf { it.isNotBlank() }
        }.distinct().sorted()
        val has3dAssets = extensions.any { it in THREED_EXTENSIONS }

        val entryFile = File(versionDir, entryRelativePath)
        val entryDir = entryFile.parentFile ?: versionDir
        val htmlContent = entryFile.readText(StandardCharsets.UTF_8)

        val externalUrls = linkedSetOf<String>()
        val missingAssets = linkedSetOf<String>()

        // Scan HTML/CSS/JS files for external URLs, local references, and unsafe JS constructs
        val inspectableFiles = allFiles.filter {
            it.extension.lowercase() in setOf("html", "htm", "js", "mjs", "css", "svg")
        }

        for (file in inspectableFiles) {
            val content = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrDefault("")
            EXTERNAL_URL_REGEX.findAll(content).forEach { match ->
                externalUrls.add(match.value)
            }
            if (file.extension.lowercase() in setOf("html", "htm")) {
                LOCAL_REF_REGEX.findAll(content).forEach { match ->
                    val ref = match.groupValues[1].trim()
                    if (isRelativeLocalReference(ref)) {
                        val cleanRef = ref.substringBefore('?').substringBefore('#')
                        val resolved = File(entryDir, cleanRef)
                        val canonicalResolved = runCatching { resolved.canonicalPath }.getOrDefault("")
                        val canonicalRoot = versionDir.canonicalPath + File.separator
                        if (!canonicalResolved.startsWith(canonicalRoot)) {
                            findings.add(
                                SecurityFinding(
                                    severity = SecuritySeverity.BLOCKER,
                                    code = "LOCAL_REFERENCE_PATH_ESCAPE",
                                    message = "مرجع مورد محلي يخرج عن مجلد الحزمة: $cleanRef"
                                )
                            )
                        } else if (!resolved.exists()) {
                            missingAssets.add(cleanRef)
                        }
                    }
                }
            }
            checkDangerousPatterns(file.name, content, findings)
        }

        // Standalone HTML referencing local/3D files cannot resolve relative GLB/media
        if (kind == PackageKind.STANDALONE_HTML && missingAssets.isNotEmpty()) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.BLOCKER,
                    code = "STANDALONE_MISSING_ASSETS",
                    message = "ملف HTML المستقل يشير إلى ${missingAssets.size} مورد محلي مفقود (${missingAssets.take(3).joinToString(", ")}). ارفع حزمة ZIP تحتوي index.html والمجلدات المرافقة."
                )
            )
        } else if (missingAssets.isNotEmpty()) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.WARNING,
                    code = "MISSING_LOCAL_ASSET",
                    message = "يوجد ${missingAssets.size} مسار محلي غير موجود داخل الحزمة: ${missingAssets.take(3).joinToString(", ")}"
                )
            )
        }

        val insecureHttp = externalUrls.filter { it.startsWith("http://", ignoreCase = true) }
        if (insecureHttp.isNotEmpty()) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.BLOCKER,
                    code = "INSECURE_HTTP_RESOURCE",
                    message = "روابط HTTP غير مشفرة مرفوضة أمنياً: ${insecureHttp.take(2).joinToString(", ")}"
                )
            )
        }

        val httpsUrls = externalUrls.filter { it.startsWith("https://", ignoreCase = true) }
        if (httpsUrls.isNotEmpty()) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.WARNING,
                    code = "EXTERNAL_HTTPS_DEPENDENCY",
                    message = "تحتوي الصفحة على ${httpsUrls.size} رابط خارجي؛ في وضع Offline للطلاب لن تعمل الموارد الخارجية غير المضمنة."
                )
            )
        }

        val linkedLessonMatch = validateLessonLinkage(lesson, sourcePages, findings)

        if (has3dAssets) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.PASS,
                    code = "3D_ASSETS_VERIFIED",
                    message = "تم التحقق من وجود أصول ثلاثية الأبعاد (${extensions.filter { it in THREED_EXTENSIONS }.joinToString(", ")}) محلياً داخل الحزمة."
                )
            )
        }

        if (findings.none { it.severity == SecuritySeverity.BLOCKER }) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.PASS,
                    code = "POLICY_PASSED",
                    message = "اجتاز الفحص الأمني وصار جاهزاً لمعاينة المعلم والاعتماد."
                )
            )
        }

        return InteractivePackageReport(
            kind = kind,
            fileName = fileName,
            entryRelativePath = entryRelativePath,
            totalFiles = totalFiles,
            totalBytes = totalBytes,
            has3dAssets = has3dAssets,
            assetExtensions = extensions,
            externalUrls = externalUrls.toList(),
            missingReferencedAssets = missingAssets.toList(),
            findings = findings,
            linkedLessonMatch = linkedLessonMatch
        )
    }

    private fun checkDangerousPatterns(
        fileName: String,
        content: String,
        findings: MutableList<SecurityFinding>
    ) {
        val isMarkupFile = fileName.substringAfterLast('.', "").lowercase() in setOf("html", "htm", "svg")
        if (isMarkupFile && Regex("<\\s*(iframe|object|embed|base)\\b", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.BLOCKER,
                    code = "UNSANDBOXED_EMBED_OR_BASE",
                    message = "تم رصد iframe/object/embed/base داخل $fileName؛ يجب إزالة التضمين قبل الاعتماد."
                )
            )
        }
        if (isMarkupFile && Regex("<[^>]+\\bon[a-z]+\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.BLOCKER,
                    code = "INLINE_EVENT_HANDLER",
                    message = "تم رصد معالج JavaScript مضمّن داخل HTML/SVG في $fileName."
                )
            )
        }
        if (fileName.endsWith(".svg", ignoreCase = true) &&
            Regex("<\\s*(script|foreignObject)\\b|javascript\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(content)
        ) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.BLOCKER,
                    code = "ACTIVE_SVG_CONTENT",
                    message = "ملف SVG يحتوي محتوى تنفيذياً غير مسموح: $fileName."
                )
            )
        }
        if (Regex("\\beval\\s*\\(|new\\s+Function\\s*\\(").containsMatchIn(content)) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.BLOCKER,
                    code = "UNSAFE_DYNAMIC_EVAL",
                    message = "تم رصد eval() أو Function() في $fileName وهو مخالف للسياسة الأمنية."
                )
            )
        }
        if (Regex("javascript\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.BLOCKER,
                    code = "JAVASCRIPT_URI",
                    message = "تم رصد رابط javascript: غير آمن في $fileName."
                )
            )
        }
        if (Regex("window\\.top|parent\\.postMessage|top\\.location", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.BLOCKER,
                    code = "FRAME_ESCAPE_ATTEMPT",
                    message = "محاولة الوصول لإطار خارجي (window.top / top.location) في $fileName."
                )
            )
        }
        if (Regex("document\\.cookie|navigator\\.sendBeacon", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            findings.add(
                SecurityFinding(
                    severity = SecuritySeverity.WARNING,
                    code = "STORAGE_OR_BEACON_API",
                    message = "استخدام document.cookie أو sendBeacon في $fileName (سيُعزل داخل الصندوق الرملي)."
                )
            )
        }
    }

    private fun validateLessonLinkage(
        lesson: LessonRef,
        sourcePages: String?,
        findings: MutableList<SecurityFinding>
    ): Boolean {
        val requested = sourcePages?.trim().orEmpty()
        val lessonRange = lesson.pageRange?.trim().orEmpty()
        if (requested.isEmpty()) {
            if (lessonRange.isNotEmpty()) {
                findings.add(
                    SecurityFinding(
                        severity = SecuritySeverity.WARNING,
                        code = "SOURCE_PAGES_NOT_SPECIFIED",
                        message = "لم تحدد صفحات المصدر للحزمة؛ صفحات الدرس المرجعية هي ($lessonRange)."
                    )
                )
            }
            return true
        }
        val reqBounds = parsePageBounds(requested)
        val lessonBounds = parsePageBounds(lessonRange)
        if (reqBounds != null && lessonBounds != null) {
            val overlaps = reqBounds.first <= lessonBounds.second && reqBounds.second >= lessonBounds.first
            if (!overlaps) {
                findings.add(
                    SecurityFinding(
                        severity = SecuritySeverity.WARNING,
                        code = "PAGE_RANGE_MISMATCH",
                        message = "صفحات الحزمة ($requested) خارج نطاق صفحات الدرس ($lessonRange)."
                    )
                )
                return false
            }
        }
        return true
    }

    private fun parsePageBounds(rangeText: String): Pair<Int, Int>? {
        val nums = Regex("\\d+").findAll(rangeText).mapNotNull { it.value.toIntOrNull() }.toList()
        if (nums.isEmpty()) return null
        return if (nums.size == 1) nums[0] to nums[0] else nums.first() to nums.last()
    }

    private fun findEntryHtml(paths: List<String>): String? {
        val rootIndex = paths.firstOrNull { it.equals("index.html", ignoreCase = true) }
        if (rootIndex != null) return rootIndex
        val singleTopLevel = paths.firstOrNull {
            it.count { ch -> ch == '/' } == 1 && it.endsWith("/index.html", ignoreCase = true)
        }
        if (singleTopLevel != null) return singleTopLevel
        return paths.firstOrNull { it.endsWith(".html", ignoreCase = true) }
    }

    private fun isRelativeLocalReference(ref: String): Boolean {
        if (ref.isBlank()) return false
        val lower = ref.lowercase()
        if (lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("data:") ||
            lower.startsWith("blob:") ||
            lower.startsWith("#") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("tel:")
        ) {
            return false
        }
        // Ignore template strings like ${...}
        if (ref.contains("\${") || ref.contains("{{")) return false
        return true
    }

    private fun extractHtmlTitle(html: String): String? {
        val match = Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.take(80)
    }

    private fun safeZipPath(rawPath: String): String {
        val normalized = rawPath.replace('\\', '/')
        if (normalized.isBlank() ||
            normalized.startsWith('/') ||
            normalized.split('/').any { it == ".." || it == "." } ||
            Regex("^[A-Za-z]:").containsMatchIn(normalized) ||
            normalized.contains('\u0000') ||
            normalized.length > 350
        ) {
            throw IllegalArgumentException("مسار غير آمن داخل ZIP: $rawPath")
        }
        return normalized
    }

    private fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("حجم ملف HTML يتجاوز الحد المسموح (15 MB)")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun queryFileName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }.getOrNull()

    private fun queryFileSize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else null
            } else null
        }
    }.getOrNull()

    companion object {
        const val MAX_HTML_FILE_BYTES = 15L * 1024L * 1024L
        const val MAX_ZIP_PACKAGE_BYTES = 80L * 1024L * 1024L
        const val MAX_ZIP_EXPANDED_BYTES = 150L * 1024L * 1024L
        const val MAX_PACKAGE_FILES = 1500

        private val THREED_EXTENSIONS = setOf("glb", "gltf", "bin", "obj", "mtl", "hdr", "usdz")
        private val ALLOWED_EXTENSIONS = setOf(
            "html", "htm", "css", "js", "mjs", "json", "txt", "md",
            "png", "jpg", "jpeg", "gif", "webp", "svg", "ico",
            "glb", "gltf", "bin", "obj", "mtl", "hdr", "usdz",
            "mp3", "wav", "ogg", "mp4", "webm",
            "woff", "woff2", "ttf", "otf"
        )
        private val BLOCKED_EXTENSIONS = setOf(
            "exe", "apk", "dex", "jar", "so", "dll", "bat", "sh", "ps1", "php", "jsp", "py", "rb"
        )
        private val EXTERNAL_URL_REGEX = Regex("https?://[^\\s\"'<>`()]+", RegexOption.IGNORE_CASE)
        private val LOCAL_REF_REGEX = Regex(
            "(?:src|href|poster)\\s*=\\s*[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE
        )
    }
}
