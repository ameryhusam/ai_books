# Edu Mini (Android Kotlin + Jetpack Compose)

Offline-first Android educational content preparation app rewritten natively in Kotlin, Jetpack Compose, and Material 3. It imports prepared `indexer2` ZIP workspaces and provides a lesson-aware Generation Studio with local Room persistence and Android Keystore-protected provider profiles.

## Features
- **ZIP Workspace Import**: Uses Android Storage Access Framework (`OpenDocument`) with ZIP-slip protection, size/file limits, grade discovery (`workspace/Gxx/`), canonical root `index.json` parsing, and `book_manifest.json` metadata.
- **Library & Lesson Navigation**: Browse imported grade stats, books/subjects, and lessons with part/page range and PDF presence indicators.
- **Generation Studio**: Select lesson task (`شرح الدرس`, `درس تفاعلي`, `بنك الأسئلة`), enter source text and additional instructions, generate drafts via Gemini REST or OpenAI-compatible endpoints with sequential fallback and retry, and browse/load/delete saved drafts in Room SQLite.
- **AI Provider Settings**: Add, enable/disable, and delete Gemini and OpenAI-compatible profiles encrypted with Android Keystore AES-GCM.

## Build & Test
- Build debug APK: `gradle assembleDebug`
- Run unit tests: `gradle :app:testDebugUnitTest`
