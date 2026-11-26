# Workspace Index Schema and Structural Diagrams

## 1. Contract overview

Indexer2 produces a workspace package. Edu Mini consumes it without rerunning indexing or reconstructing the curriculum structure.

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

The Indexer2 `IndexTemplate` currently declares:
- `templateId`, `templateName`, `title`, `displayName`;
- `gradeKey`, `subjectKey`, optional `componentKey`;
- `partKey`, `offset`, `coverPdfPage`, `expectedPdfPages`;
- `sourceMetadata`, `units`;
- `sourceLayout`, `sourceReviewStatus`, `approvalStatus`, `requiresReview`, `reviewReason`.

The unit assignment contract must be extended in Indexer2's shared type and serializer to include, where applicable:
- `partKey`: confirmed value or `unknown`;
- `assignmentStatus`: `ASSIGNED` or `NEEDS_REVIEW`;
- `proposedPartKey`: optional suggested part, not authoritative;
- `assignmentEvidence`: provenance for automatic evidence or reviewer confirmation.

Do not claim these fields exist in all current exports until the Indexer2 implementation and fixtures have been updated.

## 6. Lesson fields and file references

A lesson contains source numbering, title, printed page range, optional physical PDF page range, and optional descriptive metadata.

`sliceFile.workspaceUri` is the preferred explicit path to the generated lesson PDF. It is workspace-relative and must resolve from the selected workspace root. The consumer must not prepend the part or unit path again when this URI is already present.

The slicer currently calculates physical page ranges from `printedPageStart + offset` and `printedPageEnd + offset`. Optional `pdfPageStart` / `pdfPageEnd` fields must not be treated as slicer inputs unless the Indexer2 implementation is deliberately changed and tested.

## 7. Manifest contract

The existing exporter writes `manifestVersion`, `gradeKey`, `subjectKey`, `componentKey`, `partKey`, `sourceLayout`, `sourceReviewStatus`, `sourceFileName`, `sourceSha256`, `indexSha256`, lesson/unit counts, offset, cover paths and `partIndexPaths`.

The target manifest contract must identify the index files unambiguously:
- `canonicalIndexPath`: `P01/index.json`, `P02/index.json`, or `index.json`;
- `canonicalIndexSha256`: hash of that exact canonical index;
- `partIndexes`: optional map/list of derived part-index paths and each exact hash;
- retain legacy `indexSha256` for compatibility during migration, with its meaning explicitly tied to the canonical index path.

For an independent source, the canonical index path and hash refer to the part-local index. For a merged source, they refer to the root index. Never compare one file's bytes against another file's hash.

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
