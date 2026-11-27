# Edu Mini (Android Kotlin + Jetpack Compose)

Offline-first Android educational content preparation app rewritten natively in Kotlin, Jetpack Compose, and Material 3. It imports prepared `indexer2` ZIP workspaces and provides a lesson-aware Generation Studio with local Room persistence and Android Keystore-protected provider profiles.

## Features
- **ZIP Workspace Import**: Uses Android Storage Access Framework (`OpenDocument`) with ZIP-slip protection, size/file limits, grade discovery (`workspace/Gxx/`), canonical root `index.json` parsing, and `book_manifest.json` metadata.
- **Library & Lesson Navigation**: Browse imported grade stats, books/subjects, and lessons with part/page range and PDF presence indicators.
- **Generation Studio**: Select a lesson task (`شرح الدرس`, `درس تفاعلي`, `بنك الأسئلة`), prepare the source and prompt, and generate drafts through one shared AI execution service using the explicitly selected Gemini or OpenAI-compatible provider/profile/model. Provider-specific transport remains internal to the service; task-specific parsing, validation, persistence and presentation remain with the calling feature.
- **AI Provider Settings**: Add, enable/disable, and delete Gemini and OpenAI-compatible profiles encrypted with Android Keystore AES-GCM.
- **Cloud Sync (Firebase Auth & Firestore)**: Google Sign-In via Jetpack Credential Manager (`GetSignInWithGoogleOption`) and per-user Firebase Firestore synchronization (`users/{userId}/drafts` and `users/{userId}/progress`) protected by zero-trust `firestore.rules`.

## Build & Test
- Build debug APK: `gradle assembleDebug`
- Run unit tests: `gradle :app:testDebugUnitTest`
- Run Firestore Security Rules unit tests (with Firebase Emulator): `node --test firestore.test.js`
