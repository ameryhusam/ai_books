# Workspace Index Schema and Structural Diagrams

## 1. Contract overview

ai_books owns indexing, approval, slicing, and Workspace production. Room on the administrator device is the canonical store for the approved index. Student devices receive a grade-scoped content package containing lesson files and binding metadata; they do not receive the administrator's full database.

Logical book root:

`workspace/<gradeKey>/<subjectKey>[/<componentKey>]/`

The root path establishes curriculum context. Index placement and manifest metadata establish whether the package is an independent single-part source or one merged source.

## 2. Supported layouts

### 2.1 Independent PDF for P01

```text
workspace/G07/ENG/COURSE/
  P01/
    index.json
    book_manifest.json
    cover.png
    units/
      unit_01/lesson_01.pdf
```

P02 uses the corresponding `P02/` folder. The index is local to the part. The part-local manifest identifies the independent PDF and its own `sourceSha256`. There is no canonical root index for this independent-source layout.

### 2.2 One merged PDF for P01 and P02

```text
workspace/G07/ENG/COURSE/
  index.json
  book_manifest.json
  cover.png
  P01/
    index.json
    cover.png
    units/...
  P02/
    index.json
    cover.png
    units/...
```

There is one source PDF, one source identity, one root canonical index and one root manifest. Part indexes are derived filtered views. They do not create additional book identities.

### 2.3 Merged source with uncertain assignment

```text
workspace/G07/ENG/COURSE/
  index.json                 # review draft; not approved for slicing
  book_manifest.json         # sourceLayout remains MERGED_PARTS_FILE
  manual_review/
    part_assignment_review.json
```

The index records unresolved unit assignments and any suggested P01 value separately. A suggestion is not an assignment. No approved P01/P02 index view or lesson slice may be produced for unresolved units.

## 3. Source layout versus part assignment

These are independent dimensions.

| Field | Meaning | Examples |
|---|---|---|
| `sourceLayout` | Physical source PDF layout | `SINGLE_PART_FILE`, `MERGED_PARTS_FILE` |
| template `partKey` | Declared source-level part for a standalone PDF or BOTH for merged | `P01`, `P02`, `BOTH` |
| unit `partKey` | Confirmed part assignment of one unit | `P01`, `P02`, `unknown` |
| `assignmentStatus` | Whether unit assignment is confirmed | `ASSIGNED`, `NEEDS_REVIEW` |
| `proposedPartKey` | Suggested value requiring explicit confirmation | `P01` |
| `assignmentEvidence` | Why/how the assignment was made | TOC evidence, explicit extraction, reviewer decision, confirmed proposal |

A merged source remains merged even when the TOC does not identify unit parts. Do not set `SINGLE_PART_FILE` as a fallback for unclear assignment.

## 4. Authority and index roles

- An independent source's part-local `index.json` is its canonical index.
- A merged source's root `index.json` is its canonical index.
- Merged `P01/index.json` and `P02/index.json` are derived views.
- A part index must not be imported twice when the root merged index is present.
- A manifest belongs beside the canonical index it describes: part-local for an independent source; root-level for a merged source.

## 5. Book-level index fields

The current app-owned index contract declares:
- identity/display: `templateId`, `templateName`, `title`, `displayName`, optional `gradeName`, `subjectName`, `componentName`;
- canonical keys: `gradeKey`, `subjectKey`, optional `componentKey`;
- source: `partKey`, `sourceLayout`, `sourceReviewStatus`, `sourceMetadata`;
- review/approval: `approvalStatus`, `requiresReview`, `reviewReason`;
- unit collection: `units[]`.

The current `Unit` type includes:
- `partKey`: confirmed value or `unknown`;
- `assignmentStatus`: `assigned` or `need_review`;
- `proposedPartKey`: optional suggestion, not authoritative;
- `assignmentSource`: `INDEX_EVIDENCE`, `REVIEWER`, `DEFAULT_PROPOSAL`, or `LEGACY_UNVERIFIED`;
- `assignmentEvidence`: provenance/evidence description.

The extraction schema accepts these fields. The intake UI creates a P01 proposal for unresolved units in a merged source but keeps `partKey=unknown` and `assignmentStatus=need_review` until a reviewer confirms P01 or selects P02.

## 6. Lesson fields and file references

A lesson contains source numbering, title, printed page range, optional physical PDF page range, and optional descriptive metadata.

`sliceFile.workspaceUri` is the preferred explicit path to the generated lesson PDF. It is workspace-relative and must resolve from the selected workspace root. The consumer must not prepend the part or unit path again when this URI is already present.

The slicer currently calculates physical page ranges from `printedPageStart + offset` and `printedPageEnd + offset`. Optional `pdfPageStart` / `pdfPageEnd` fields must not be conflated with printed page numbers or user-entered expected TOC pages. Apply page offset exactly once in the ai_books slicing pipeline and cover it with tests.

## 7. Manifest contract

The TypeScript exporter now writes `edu.book_manifest.v2` with:
- `manifestVersion`, `gradeKey`, `subjectKey`, `componentKey`, `partKey`, `sourceLayout`, `sourceReviewStatus`;
- `sourceFileName` and `sourceSha256`;
- `canonicalIndexPath` and `canonicalIndexSha256`;
- `indexSha256` as a compatibility alias for the canonical index hash;
- `partIndexes`, containing each derived part-index path and its own SHA-256;
- lesson/unit counts, offset, cover paths and legacy `partIndexPaths`.

For an independent source, the canonical index path is `P01/index.json` or `P02/index.json`, and the manifest is beside that index. For a merged source, the canonical index path is root `index.json`; derived part indexes are optional filtered views.

Hashes are calculated from the exact serialized bytes written to each file. Never compare one index file against another file's hash.
## 8. Identity and key rules

- Canonical identity keys are grade, subject and optional component.
- A display name from the index may be shown to the user; it never replaces a canonical key.
- Duplicate unit numbers in P01 and P02 are allowed when the part context distinguishes them.
- Array position is not identity.
- A derived part view is not a separate source identity.

## 9. Validation invariants

1. Source layout and index placement must be compatible.
2. Manifest and canonical index identity must agree.
3. Each declared index hash must match that file's exact bytes.
4. A standalone source must contain one valid canonical part index and its companion manifest.
5. A merged source must contain one root canonical index and manifest; derived indexes are optional but must match the declared filtered units and hashes.
6. Unresolved merged unit assignments block approval and slicing.
7. Every lesson path must resolve safely and identify a readable PDF.
8. The consumer must bind a merged root index once and ignore its derived indexes as independent books.

## 10. Grade package distribution contract

The package is a **content delivery artifact per grade**, not a database backup. It is generated from approved Room records and contains only the grade's published curriculum metadata, stable book/unit/lesson keys, lesson PDFs, required HTML/interactive resources, and a manifest with relative paths, sizes, hashes, package ID, and schema version. The canonical index remains in Room; the package metadata is a transport/binding projection and must not become a second editable index source.

The student device imports the ZIP into app-managed local storage, validates it before activation, and creates/updates the local Room catalog using stable identities. Its lesson viewer resolves each lesson file from the imported package by lesson identity and validated relative path. No absolute path or content URI from the administrator device is portable.

Do not include the administrator's full database, private AI drafts, provider credentials, unrelated grades, or learner performance records. Student attempts, scores, progress, and assessment history are learner data and travel in the opposite direction through a separately authenticated/synchronized data flow. Offline learning must continue when no network is available; pending performance events can be synchronized later.

A package update must preserve learner progress for unchanged stable lesson identities. Removed or changed lessons must be handled by an explicit versioning/retention policy; never silently reset or orphan progress. The package importer must stage, validate hashes and required assets, and activate atomically.
