# Learning Progress Report and Assessment Design

Date: 2026-11-27
Branch: `refactor/extract-ui-state-models`

## Product intent

The product should measure durable learning, not merely lesson completion or a single test score. The administrator device is used by the parent/teacher to prepare content, distribute grade packages, and review each child's progress. Each child uses the Android app on their own Android tablet, imports the package for their grade, studies lessons offline, and records attempts locally. Learner-facing screens remain concise; adult-facing reports provide diagnostic evidence for teaching decisions.

## Indicators currently implemented

### 1. First-attempt accuracy

For each question with at least one recorded response, take its earliest recorded response. The indicator is the percentage of those first responses that were correct.

This avoids inflating the initial understanding score by counting repeated attempts after feedback as if they were first-time knowledge. Reinforcement drills and the general question-bank practice mode are excluded from the main accuracy, stable-concept, and delayed-recall scores; their mistakes remain available for diagnostic patterns.

### 2. Latest observed accuracy

For each question, take its latest recorded response and calculate the correct percentage. This is the latest observed performance, not a guarantee of long-term mastery.

### 3. Stable concept evidence

A concept is counted as showing stable evidence only when:
- at least three distinct questions have been answered for that concept;
- attempts span at least two different calendar days; and
- the latest response for at least 80% of those distinct questions is correct.

The stable-concept percentage is the share of concepts meeting this rule among concepts with enough question coverage. Concepts without sufficient evidence are excluded from the denominator rather than counted as failures.

### 4. Delayed recall and recall gap

For each question, compare its first eligible learning response with the **first retrieval attempt at least seven days later**. The report only shows delayed recall when at least three distinct questions meet that condition. Using the first delayed retrieval avoids letting many later practice attempts inflate the retention estimate.

The recall gap is the non-negative difference between first-response accuracy and delayed-recall accuracy on those same questions. This is deliberately called a **recall gap**, not a scientifically established forgetting rate: repeated-question testing is only a proxy, and question difficulty or prior exposure can affect results.

### 5. Repeated errors

The report identifies:
- the same wrong answer repeated on the same question at least twice; and
- concepts where wrong responses recur across multiple questions.

These are diagnostic signals, not definitive proof of a specific misconception. A teacher should inspect the question, distractor, and explanation before assigning a corrective lesson.

### 6. Weekly assessment trend

Assessment attempts are grouped by the Monday-starting week in the device's local time zone. The report shows weekly average score, attempt count, and number passed for the latest eight weeks. The data is based on recorded assessment attempts; the app does not yet schedule a weekly test automatically.

### 7. Change after reinforcement practice

The current report measures the difference between pre-reinforcement and later non-reinforcement responses on the same questions, when at least two shared questions are available for a concept.

This is an observational association after practice. It does **not** isolate the effect of a generated explanation, because the app does not yet record that the learner opened, read, and completed a specific explanation. An explanation's benefit must not be claimed from practice scores alone.


## Schedule and intervention implementation status

As of this revision, the weekly report still groups recorded assessment attempts; it does not yet create or schedule a Friday test. The intended schedule is configurable study days (default Saturday–Wednesday), a separate assessment day (default Friday), local time and timezone. A WorkManager worker, idempotent per-learner weekly assessment creation, and a learner-facing assessment entry point remain outstanding.

The remedial generator still creates drafts and optional adaptive questions. It does not yet create a learner assignment or record when the learner opens, reads, and completes a specific explanation. The current post-reinforcement comparison must not be presented as direct evidence of explanation effectiveness.

## Role-based presentation

### Learner

The learner sees:
- overall lesson completion;
- points earned through learning progress and learning badges (authoring/creation badge bonuses are excluded from the learner's displayed score);
- latest performance summary;
- a positive next-step message;
- a general review action.

The learner does not see individual wrong answers, recurring distractors, or a list of weak concepts.

### Parent / teacher / administrator

The current `ADMIN` dashboard sees:
- first and latest accuracy;
- stable-concept evidence;
- delayed recall and recall gap when the sample is sufficient;
- repeated wrong-answer patterns and recurring concept names;
- weekly assessment trends;
- observed change after reinforcement practice;
- an exportable Markdown report.

There is currently one adult role (`ADMIN`); separate parent-only and administrator permissions have not yet been implemented. The intended deployment has one or more identified learner profiles/devices linked to a responsible adult. A local role switch alone is not an authorization boundary. Before multi-device cloud reporting, enforce authenticated parent/learner identity and server-side authorization.

## Lesson progression and rewards

- The learner can open the first lesson in a book.
- Every later lesson is locked until all earlier lessons in that book have a recorded passing completion.
- The lesson quiz pass threshold remains the repository's configured threshold (currently 60%).
- A failed first attempt does not complete the lesson.
- A failed review attempt does not revoke a previously passed lesson.
- Points continue to be recorded through the existing progress mechanism.
- General badges remain, and per-book progress badges are added at 25%, 50%, 75%, and 100%. The 100% badge is a subject/book mastery badge.

The current gate is based on the canonical order of lessons in the Workspace index. Changing the index order after a learner has progressed should be tested carefully.

## Question quality guard

AI-generated adaptive questions remain `reviewed = false`. They are not included in learner reinforcement selection, bank practice, flashcards, or newly created assessments until an adult reviews and approves them. Existing timed assessments are blocked if one of their questions is no longer approved.

## Device distribution and performance synchronization

The content and performance flows are deliberately separate:
- **Administrator → student devices:** distribute a grade-scoped ZIP containing published lesson PDFs and required assets plus stable binding metadata. It is not a full Room database export.
- **Student device → administrator/reporting service:** send learner progress, question attempts, assessment scores, and completion events under the authenticated learner identity. These events are collected locally first and must remain usable offline.
- **Room on each device:** stores the local curriculum projection and learner records needed for offline operation. The canonical authoring index remains on the administrator device; imported package metadata binds student-side Room records to the published content version.
- **Future/online service:** may synchronize learner records and package/version metadata. It must not be required to open downloaded lesson PDFs or continue learning.

A package update must not overwrite learner attempts or reset progress for unchanged lesson IDs. Content version and learner performance synchronization require separate conflict policies.

## Offline database behavior and update semantics

The learner experience should use Room as its local operational store:

- Catalog screens observe local `workspaces`, `books`, `lessons`, and `workspace_units` records.
- Lesson text is read from `lesson_texts`; lesson PDFs and interactive resources are opened from validated app-managed local files referenced by Room metadata.
- Practice and quizzes query the local `questions` bank, excluding questions that are not reviewed/approved.
- Assessments query local `assessments` and their referenced question IDs. An assessment must not be offered unless all required questions are installed and approved.
- Learner actions write to `lesson_progress`, `question_attempts`, `assessment_attempts`, and `study_days`, scoped by the active local learner ID.
- DAO `Flow` observations refresh the UI after local Room writes. They do not themselves fetch changed content from another device or server.

Content updates arrive through a separately imported grade package. The importer must verify the package manifest, relative paths, sizes and hashes; stage changed files; reconcile catalog/content rows by stable IDs; and activate the new package atomically. It must not overwrite learner attempt history or reset progress for unchanged lesson identities. Pending learner records remain local until the separate performance-sync channel is available.

The current schema has a publication signal for interactive packages (`published_for_students`) and a review signal for questions (`reviewed`). The assessment entity does not currently expose a matching publication/approval field. The package-export policy must therefore define how an assessment becomes publishable before assessment distribution is implemented; do not infer publication from its mere presence in Room.

## Important limitations / next design work

1. **Assigned support plans are not yet a complete workflow.** The current app generates explanation drafts and adaptive questions, and records reinforcement practice, but does not yet create a unified assignment containing explanation + questions, record that the learner read the explanation, and close the assignment after a follow-up test.
2. **Explanation effectiveness is not directly measurable yet.** Add an explicit learner exposure/completion event for each assigned explanation, then link the follow-up assessment to that assignment.
3. **Question equivalence:** delayed recall should ultimately use equivalent but not identical questions, with concept and difficulty metadata. Reusing the exact same question can introduce answer-memory effects.
4. **Misconception taxonomy:** exact repeated distractors are observable, but semantic misconception categories require teacher-authored tags or a reviewed classification step.
5. **Weekly assessment schedule:** the current report groups existing attempts; a scheduled weekly assessment and reminders remain future work.
6. **Parent/child identity and authorization:** the app currently has LEARNER and ADMIN roles, and local learner IDs are used for progress separation. The target flow requires each Android-tablet learner session to be associated with the correct child identity and parent account. A local role choice is not sufficient security. Any online performance sync must authenticate the learner/device, authorize the parent to read only linked children, and prevent cross-child data leakage.
7. **PDF report:** the current export is Markdown. A polished RTL PDF/HTML report can be added after the content and metrics stabilize.
8. **Calibration:** thresholds (3 questions, 2 days, 80%, 7 days) are initial operational rules and should be configurable and validated against real student usage.

## Verification

New tests cover:
- delayed recall and recall-gap calculation;
- repeated wrong-answer and recurring-concept detection;
- weekly assessment aggregation;
- observed change after reinforcement practice;
- sequential lesson unlocking;
- subject milestone badge thresholds;
- exclusion of unreviewed adaptive questions from learner practice;
- preservation of lesson completion after an unsuccessful review retake.

Gradle unit tests and Android assembly have not been run in the GitHub editing environment. Follow `docs/LOCAL_ANDROID_TESTING.md` before release.
