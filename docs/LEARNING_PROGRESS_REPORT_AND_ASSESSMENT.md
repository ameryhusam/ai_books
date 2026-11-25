# Learning Progress Report and Assessment Design

Date: 2026-11-25
Branch: `main`

## Product intent

The product should measure durable learning, not merely lesson completion or a single test score. The learner-facing experience is intentionally encouraging and concise. The adult-facing view (current `ADMIN` role) contains diagnostic evidence needed to decide what to teach, review, or assign next.

## Indicators currently implemented

### 1. First-attempt accuracy

For each question with at least one recorded response, take its earliest recorded response. The indicator is the percentage of those first responses that were correct.

This avoids inflating the initial understanding score by counting repeated attempts after feedback as if they were first-time knowledge.

### 2. Latest observed accuracy

For each question, take its latest recorded response and calculate the correct percentage. This is the latest observed performance, not a guarantee of long-term mastery.

### 3. Stable concept evidence

A concept is counted as showing stable evidence only when:
- at least three distinct questions have been answered for that concept;
- attempts span at least two different calendar days; and
- the latest response for at least 80% of those distinct questions is correct.

The stable-concept percentage is the share of concepts meeting this rule among concepts with enough question coverage. Concepts without sufficient evidence are excluded from the denominator rather than counted as failures.

### 4. Delayed recall and recall gap

For each question, compare its first response with its latest response at least seven days later. The report only shows delayed recall when at least three distinct questions meet that condition.

The recall gap is the non-negative difference between first-response accuracy and delayed-recall accuracy on those same questions. This is deliberately called a **recall gap**, not a scientifically established forgetting rate: repeated-question testing is only a proxy, and question difficulty or prior exposure can affect results.

### 5. Repeated errors

The report identifies:
- the same wrong answer repeated on the same question at least twice; and
- concepts where wrong responses recur across multiple questions.

These are diagnostic signals, not definitive proof of a specific misconception. A teacher should inspect the question, distractor, and explanation before assigning a corrective lesson.

### 6. Weekly assessment trend

Assessment attempts are grouped by the Monday-starting UTC week of completion. The report shows weekly average score, attempt count, and number passed for the latest eight weeks. The data is based on recorded assessment attempts; the app does not yet schedule a weekly test automatically.

### 7. Change after reinforcement practice

The current report measures the difference between pre-reinforcement and later non-reinforcement responses on the same questions, when at least two shared questions are available for a concept.

This is an observational association after practice. It does **not** isolate the effect of a generated explanation, because the app does not yet record that the learner opened, read, and completed a specific explanation. An explanation's benefit must not be claimed from practice scores alone.

## Role-based presentation

### Learner

The learner sees:
- overall lesson completion;
- points and earned badges;
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

There is currently one adult role (`ADMIN`); separate parent-only and administrator permissions have not yet been implemented.

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

## Important limitations / next design work

1. **Assigned support plans are not yet a complete workflow.** The current app generates explanation drafts and adaptive questions, and records reinforcement practice, but does not yet create a unified assignment containing explanation + questions, record that the learner read the explanation, and close the assignment after a follow-up test.
2. **Explanation effectiveness is not directly measurable yet.** Add an explicit learner exposure/completion event for each assigned explanation, then link the follow-up assessment to that assignment.
3. **Question equivalence:** delayed recall should ultimately use equivalent but not identical questions, with concept and difficulty metadata. Reusing the exact same question can introduce answer-memory effects.
4. **Misconception taxonomy:** exact repeated distractors are observable, but semantic misconception categories require teacher-authored tags or a reviewed classification step.
5. **Weekly assessment schedule:** the current report groups existing attempts; a scheduled weekly assessment and reminders remain future work.
6. **Parent-specific role and authorization:** the app currently has LEARNER and ADMIN roles; ADMIN combines parent/teacher/admin detail access and authoring. Role selection is currently a local UI choice, not a server-enforced permission boundary. Hiding diagnostics from the learner is a presentation rule, not a security guarantee. Before storing sensitive child-performance records in a shared or networked environment, add a parent-only read permission and enforce adult access with authenticated account roles (for example, Firebase custom claims) or a carefully designed local parent lock.
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
