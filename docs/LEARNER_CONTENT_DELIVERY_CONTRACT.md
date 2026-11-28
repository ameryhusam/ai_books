# Learner Content Delivery Contract

**Status:** Presentation policy implemented; complete interactive lesson authoring pipeline tracked separately.
**Related:** Architecture Issue #9; Learner feature Issue #10.

## 1. Source artifacts are not learner content

- The lesson text file and its extracted text are authoring/source artifacts.
- Teachers and Studio may use this text for quick review, adding content, and AI-assisted generation.
- Selecting the text source must allow AI preparation without attaching the lesson PDF.
- PDF remains an optional alternative input. The source-selection decision must be explicit.
- The raw source text must never be rendered in the learner experience.

## 2. Learner presentation decision

| Lesson resources | Learner initial view | Forbidden fallback |
|---|---|---|
| PDF exists | PDF viewer | Raw source text or combined PDF + raw text |
| No PDF; published interactive lesson exists | Open interactive lesson directly | Raw source text |
| No PDF; no published interactive lesson | Interactive tab with preparation/unavailable message | Blank PDF viewer or raw source text |
| Interactive draft exists but is not published | Do not expose draft | Any preview of draft content to learner |

Approved explanation/summary and quiz are separate learner features and do not turn the raw source file into learner content.

## 3. Interactive lesson contract

- INTERACTIVE means an interactive activity/checkpoint package.
- INTERACTIVE_LESSON means a complete interactive teaching version of the lesson.
- The complete lesson should include a coherent explanation, examples, ordered learning steps, formative interactions and a closing assessment.
- Reviewed textbook questions may be transformed into interactive question components while preserving question identity and source provenance.
- Question-bank records and interactive checkpoints remain distinct; an interactive checkpoint must not silently become an approved question-bank item.
- All AI outputs remain drafts until validation, teacher review, explicit approval and publication.

## 4. Source selection and provenance

- Studio should let the author choose lesson text or PDF as the generation source.
- Choosing lesson text must not cause PDF bytes or PDF pages to be attached to the AI request.
- Persist/retain source type and source identity in the generated artifact provenance.
- Do not silently combine text and PDF inputs; combined-source generation requires an explicit author choice.
- The learner UI must not request or load raw lesson text.

## 5. Current implementation

Implemented in learner presentation:
- Removed raw lesson text state loading and reader from StudentStudyDialog's source-content tab.
- Removed source-text state collection and callback wiring from the learner composition path.
- PDF is the only content displayed in the PDF tab.
- When no PDF exists, the PDF tab is omitted and the interactive tab is selected.
- If no published interactive package is available, the interactive tab displays a preparation message.
- Published package visibility and existing draft approval/publication gates remain in place.

Not yet complete:
- End-to-end INTERACTIVE_LESSON generation into a complete publishable package.
- Transformation of reviewed textbook questions into interactive components with provenance.
- Explicit Studio source selector and proof that text-only generation sends no PDF.
- End-to-end tests from source selection through approval, publication and learner rendering.

See Issue #10 for the implementation plan and acceptance tests.
