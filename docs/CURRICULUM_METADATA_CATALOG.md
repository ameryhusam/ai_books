# Canonical Curriculum Metadata Catalog

## 1. Purpose and authority

This catalog defines the canonical grade, subject and component keys consumed by Edu Mini. It is derived from Indexer2 detection rules, the `IndexTemplate` contract and canonical template modules.

Indexer2 owns identity detection and exports both stable keys and display labels. Edu Mini validates keys using this catalog; it should prefer display labels declared in the prepared index.

## 2. Canonical keys

The current Android mapper recognizes these grades:

| Key | Arabic fallback label |
|---|---|
| G01–G09 | الصف الأول إلى التاسع الأساسي |
| G10 | الصف الأول الثانوي (العاشر) |
| G11 | الصف الثاني الثانوي (الحادي عشر) |
| G12 | الصف الثالث الثانوي (الثاني عشر) |

Recognized subject keys:

| Key | Arabic fallback label | Grade rule |
|---|---|---|
| ARAB | اللغة العربية | G01–G12 |
| ISL | التربية الإسلامية | G01–G12 |
| QUR | القرآن الكريم وعلومه | G01–G12 |
| MATH | الرياضيات | G01–G12 |
| SOC | الدراسات الاجتماعية | G01–G12 |
| ENG | اللغة الإنجليزية | G01–G12 |
| COMP | الحاسوب وتقنية المعلومات | G01–G12 |
| BIO | الأحياء | G10–G12 |
| CHEM | الكيمياء | G10–G12 |
| PHYS | الفيزياء | G10–G12 |
| SCI | العلوم | G01–G09 |

Component keys do not replace their parent subject:

| Parent subject | Component | Arabic fallback label |
|---|---|---|
| SOC | GEO | الجغرافيا |
| SOC | HIST | التاريخ |
| SOC | TARBIA | التربية الوطنية |
| ENG | COURSE | كتاب الطالب (Course) |

The tables above are fallback labels and key constraints, not a promise that every grade/subject combination has a registered template.

## 3. Validation and display are separate

- `gradeKey`, `subjectKey`, and `componentKey` are identifiers and must be validated against supported keys and parent/grade constraints.
- Indexer2-provided `gradeName`, `subjectName`, `componentName`, `displayName`, or equivalent explicit display fields are presentation values and should be preferred when non-empty.
- If a display field is absent, use the mapper's Arabic fallback label.
- Never infer a key from an Arabic display string.
- Never accept an unknown key merely because its display string looks familiar.

This separation allows Indexer2 to update curriculum display terminology without requiring a duplicate Android detection or mapping algorithm, while preserving stable database keys.

## 4. Workspace path and index precedence

The canonical curriculum path is `workspace/<gradeKey>/<subjectKey>[/<componentKey>]/`. The part-local index for a standalone source is under P01 or P02. A merged source uses the root index and may include derived part indexes.

The index is authoritative for its declared display labels and unit/lesson structure. The path and canonical keys are authoritative for identity validation. The lesson's explicit `sliceFile.workspaceUri` is authoritative for its PDF reference.

## 5. Mapper change policy

A new key may be added only when Indexer2 detection logic or a canonical template establishes it. A coordinated change must update:
1. Indexer2 detection/template evidence;
2. this catalog;
3. `BookMetadataMapper`;
4. mapper and workspace-binding tests.

Do not introduce a second key to accommodate a display-name variation.
