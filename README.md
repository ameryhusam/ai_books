# Edu Mini (Android Kotlin + Jetpack Compose)

Offline-first Android educational content preparation app built natively with Kotlin, Jetpack Compose, and Material 3. It manages curriculum workspaces, lesson resources, question banks, AI-assisted learning content, and learner progress on Android devices.

## Features

- **Workspace Import & Export**: Imports prepared grade ZIP workspaces through Android Storage Access Framework, validates archive paths and size limits, discovers grade folders, and reads canonical `index.json` and `book_manifest.json` metadata.
- **Library & Lesson Navigation**: Browses imported grades, books, subjects, parts, units, lessons, page ranges, and available lesson PDFs.
- **PDF Source Handling**: Imports a selected PDF into the app's managed workspace, validates that it is readable and has pages, records source metadata and SHA-256, previews the source, and provides the PDF splitting engine for producing lesson-level PDFs from supplied page ranges.
- **Book Index Intake Contracts**: Defines book/unit/lesson draft data, printed-page and physical-PDF-page coordinates, offset conversion, validation of page ranges, and an explicit approval gate before a reviewed index can be treated as approved. The AI index-discovery prompt asset and intake contracts are present; the complete upload → AI discovery → review UI → approved-index persistence → PDF-splitting workflow is not yet connected end to end.
- **Generation Studio**: Selects lesson tasks (lesson explanation, interactive lesson, question bank), prepares source material and prompts, and uses one shared AI execution service with an explicitly selected Gemini or OpenAI-compatible provider/profile/model. Provider-specific transport is internal to the service; task-specific parsing, validation, persistence, and presentation remain with the calling feature.
- **Question Bank**: Generates structured question content using an application-owned response contract derived from Kotlin serialization, validates the complete response before persistence, and maps accepted content to the existing Room entities.
- **AI Provider Settings & API Test**: Stores Gemini and OpenAI-compatible API credentials encrypted with Android Keystore AES-GCM. Credentials are provider-scoped; model selection is managed independently through the model catalogue and generation-time model picker. The connection test uses the provider's enabled default model and displays the complete text response.
- **Learning Progress & Cloud Sync**: Stores application learning data locally with Room and supports Firebase Authentication and per-user Firestore synchronization under the configured security rules.

## Book PDF indexing status

The PDF intake and splitting building blocks are not the same as a completed index-to-split user flow. The current implementation includes the intake draft/validation contract and PDF ingestion/splitting components, but the following integration work remains before claiming full automatic indexing:

1. Connect PDF upload to the book-index discovery request in the shared AI service.
2. Parse and validate the AI response into the book-index intake draft.
3. Present the discovered book metadata, parts, units, lessons, and page ranges for user review.
4. Apply the configured offset to printed page numbers exactly once when physical PDF page numbers are not already supplied.
5. Persist the user-approved canonical index and only then invoke the PDF splitter.
6. Verify the generated lesson PDFs and their links to the approved curriculum records.

The source PDF must remain intact; temporary working copies may be cleaned up only after successful processing. The printed page number in the book, the physical page number in the original PDF, and the local page number inside a split lesson PDF are distinct coordinate systems.

## Build & Test

- Build debug APK: `gradle assembleDebug`
- Run unit tests: `gradle :app:testDebugUnitTest`
- Run Firestore Security Rules unit tests (with Firebase Emulator): `node --test firestore.test.js`
