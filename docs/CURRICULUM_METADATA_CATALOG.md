# Canonical Curriculum Metadata Catalog

## 1. Purpose and authority

This catalog defines the canonical grade, subject, and component keys consumed by the Android workspace binder. It is derived from the Indexer2 detection rules, the `IndexTemplate` contract, and the actual canonical template modules.

Primary sources inspected:

- `indexer2/src/shared/services/pdfService.ts` — `GRADE_RULES`, `SUBJECT_RULES`, `GENERIC_SCIENCE_PATTERNS`, `detectSocialComponent`, `detectGrade`, `detectSubject`.
- `indexer2/src/shared/data/templateRegistry.ts` — canonical template filename/path validation and dynamic discovery.
- `indexer2/src/shared/types/index.ts` — `IndexTemplate` identity fields.
- Actual modules under `indexer2/src/shared/data/templates/G04/` and `G07/`.

This is a key catalog, not a claim that every grade/subject combination has a template. Template availability is dynamic and is defined by the actual files discovered by Indexer2.

## 2. Grade keys

| Canonical key | Arabic display name |
|---|---|
| G01 | الصف الأول الأساسي |
| G02 | الصف الثاني الأساسي |
| G03 | الصف الثالث الأساسي |
| G04 | الصف الرابع الأساسي |
| G05 | الصف الخامس الأساسي |
| G06 | الصف السادس الأساسي |
| G07 | الصف السابع الأساسي |
| G08 | الصف الثامن الأساسي |
| G09 | الصف التاسع الأساسي |
| G10 | الصف الأول الثانوي (العاشر) |
| G11 | الصف الثاني الثانوي (الحادي عشر) |
| G12 | الصف الثالث الثانوي (الثاني عشر) |

Grade keys are canonical identifiers. The Android mapper accepts canonical G01–G12 keys and compact G1–G9 input forms only for normalization. Unknown grades return no display label and are not replaced by a default.

## 3. Subject keys

The following keys are defined by Indexer2's explicit subject rules:

| Canonical key | Arabic display name | Grade rule |
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

The grade ranges above reflect the constraints encoded by Indexer2's basic/secondary science classification: generic science is assigned to `SCI` in basic grades; secondary science is detected through the explicit `BIO`, `CHEM`, and `PHYS` subject wording. The other subject keys are listed because they are explicit entries in `SUBJECT_RULES`.

A subject key is mandatory for a bound book. If the PDF evidence and canonical workspace path do not establish a supported key, identity remains unresolved and binding must stop for that book.

## 4. Component keys

A component does not replace the parent subject key.

| Parent subject | Component key | Display name | Evidence |
|---|---|---|---|
| SOC | GEO | الجغرافيا | Indexer2 social-component detector and G07 GEO template |
| SOC | HIST | التاريخ | Indexer2 social-component detector |
| SOC | TARBIA | التربية الوطنية | Indexer2 social-component detector and G07 TARBIA template |
| ENG | COURSE | كتاب الطالب (Course) | G07 `ENG_COURSE_G07_BOTH` template |

For example, a geography book is identified as `subjectKey=SOC` and `componentKey=GEO`, not as a new geography subject key.

The Android mapper accepts only the component keys listed here. A component not established by the Indexer2 detector or canonical template naming contract is not part of this catalog.

## 5. Canonical template filename convention

The registry validates this pattern:

`<SUBJECT_KEY>[_<COMPONENT_KEY>]_<GRADE_KEY>_<P01|P02|BOTH>.ts`

It checks that the grade folder, filename, exported `gradeKey`, `subjectKey`, `componentKey`, `partKey`, and `templateName` agree. A file that does not satisfy those checks is not included in `DEFAULT_INDEX_TEMPLATES`.

## 6. Template inventory verified in the inspected repository snapshot

The following modules were directly inspected. This is a verified subset, not a complete curriculum-availability matrix.

| Grade | Template | Subject key | Component key | Part |
|---|---|---|---|---|
| G04 | ARAB_G04_P02 | ARAB | — | P02 |
| G04 | ISL_G04_BOTH | ISL | — | BOTH |
| G04 | MATH_G04_P02 | MATH | — | P02 |
| G04 | QUR_G04_BOTH | QUR | — | BOTH |
| G04 | SCI_G04_P02 | SCI | — | P02 |
| G04 | SOC_G04_BOTH | SOC | — | BOTH |
| G07 | ARAB_G07_P02 | ARAB | — | P02 |
| G07 | ENG_COURSE_G07_BOTH | ENG | COURSE | BOTH |
| G07 | ISL_G07_P02 | ISL | — | P02 |
| G07 | MATH_G07_BOTH | MATH | — | BOTH |
| G07 | QUR_G07_BOTH | QUR | — | BOTH |
| G07 | SCI_G07_P02 | SCI | — | P02 |
| G07 | SOC_GEO_G07_BOTH | SOC | GEO | BOTH |
| G07 | SOC_TARBIA_G07_BOTH | SOC | TARBIA | BOTH |

The registry scans all matching grade folders at runtime. This table must be refreshed from the actual template files when the repository inventory changes; absence from this inspected subset does not establish that a subject is unavailable in another grade.

## 7. Mapper contract

The Android application uses `BookMetadataMapper` as the single display/validation catalog.

- `normalizeGradeKey`, `normalizeSubjectKey`, and `normalizeComponentKey` return a canonical key only when the input is recognized.
- `gradeDisplayName`, `subjectDisplayName`, and `componentDisplayName` return null for unknown keys.
- `mapBookMetadata` returns the normalized keys, display names, and `isKnown`.
- `isValidBookIdentity` validates the grade/subject/component combination, including science grade constraints and component-parent constraints.

The mapper does not infer a subject from a title, accept unregistered aliases, or allow display text in the manifest to override the canonical catalog. Display strings are presentation data; keys remain the stable identity.

## 8. Catalog change policy

A new key may be added only after its source is established in Indexer2 detection logic or a canonical template that passes registry validation. A change must update, together:

1. Indexer2 detection/template evidence;
2. this catalog;
3. `BookMetadataMapper`;
4. mapper and workspace-binding tests.

Do not change the meaning of an existing key. Do not introduce a second key for an existing subject merely to accommodate a display-name variation.
