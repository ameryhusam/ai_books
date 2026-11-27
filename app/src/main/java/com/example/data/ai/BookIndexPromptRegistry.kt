package com.example.data.ai

import com.example.model.BookIndexSchemaSpec
import com.example.model.LessonRef
import com.example.model.PromptBlueprintSpec
import com.example.model.TaskType

object BookIndexPromptRegistry {

    val blueprints: List<PromptBlueprintSpec> = listOf(
        PromptBlueprintSpec(
            id = "prompt_explanation",
            task = TaskType.EXPLANATION,
            titleAr = "مخطط شرح الدرس المنهجي",
            subtitleAr = "تهيئة المفاهيم، خطوات الشرح، أمثلة محلولة، وملخص ختامي مرتبط بالصفحات",
            sourcePath = "prompts/book_indexing/lesson_explanation.prompt.md",
            roleInstruction = "شرح مبسط ومنظم مع أمثلة محلولة وملخص ختامي",
            outputContract = "عناوين رئيسية واضحة + خطوات مرقمة + مثال تطبيقي + قاعدة ذهبية في الختام",
            quickInstructions = "قسّم الشرح إلى 3 خطوات متدرجة، وأضف مثالاً من الحياة اليومية مع خلاصة مركزة في النهاية."
        ),
        PromptBlueprintSpec(
            id = "prompt_summary",
            task = TaskType.SUMMARY,
            titleAr = "مخطط التلخيص المركّز",
            subtitleAr = "تقطير المفاهيم والقوانين الأساسية في نقاط منظمة وسريعة المراجعة",
            sourcePath = "prompts/book_indexing/lesson_summary.prompt.md",
            roleInstruction = "ملخص مركز ومنظم لأهم أفكار ومفاهيم الدرس في نقاط واضحة",
            outputContract = "الفكرة المحورية + 4 إلى 6 نقاط مركزة + تذكير بصري/مقارنة سريعة",
            quickInstructions = "اعرض الفكرة الرئيسية أولاً ثم لخّص المفاهيم الأساسية في نقاط قصيرة مع جدول مقارنة إن أمكن."
        ),
        PromptBlueprintSpec(
            id = "prompt_objectives",
            task = TaskType.OBJECTIVES,
            titleAr = "مخطط الأهداف السلوكية ومؤشرات الأداء",
            subtitleAr = "صياغة أهداف قابلة للقياس ومؤشرات تحقق ومفردات مفتاحية",
            sourcePath = "prompts/book_indexing/learning_objectives.prompt.md",
            roleInstruction = "أهداف تعلم سلوكية قابلة للقياس ومفردات مفتاحية مع تعريفاتها",
            outputContract = "أهداف بصيغة (أن + فعل مضارع قابل للقياس) + قائمة مفردات مفتاحية",
            quickInstructions = "صُغ 4 أهداف سلوكية تغطي الفهم والتطبيق، متبوعة بقائمة المصطلحات المفتاحية في الدرس."
        ),
        PromptBlueprintSpec(
            id = "prompt_vocabulary",
            task = TaskType.VOCABULARY,
            titleAr = "مخطط باني المفردات السياقي",
            subtitleAr = "استخراج المصطلحات والمفاهيم مع التعريف الميسر والمثال النصي",
            sourcePath = "prompts/book_indexing/vocabulary_builder.prompt.md",
            roleInstruction = "مسرد مفردات ومصطلحات الدرس الأساسية مع التعريف المبسط ومثال سياقي لكل مصطلح",
            outputContract = "المصطلح • المعنى المبسط • مثال سياقي من الدرس • تنبيه لعدم الخلط",
            quickInstructions = "استخرج 6 مصطلحات رئيسية من الدرس، وعرّف كل منها بلغة مبسطة مع مثال سياقي دقيق."
        ),
        PromptBlueprintSpec(
            id = "prompt_worked_examples",
            task = TaskType.WORKED_EXAMPLES,
            titleAr = "مخطط الأمثلة المحلولة خطوة بخطوة",
            subtitleAr = "حل نموذجي مشروح السبب لكل خطوة مع تمرين تحقّق فوري",
            sourcePath = "prompts/book_indexing/worked_examples.prompt.md",
            roleInstruction = "سلسلة أمثلة محلولة خطوة بخطوة مع توضيح سبب كل خطوة وتدريب مماثل للطالب",
            outputContract = "المعطيات -> الخطوة 1 (مع التعليل) -> النتيجة النهائية -> تدريب تطبيقي مماثل",
            quickInstructions = "قدّم مثالين محلولين بتسلسل الخطوات مع تعليل كل خطوة، واختم بتمرين موجّه للطالب."
        ),
        PromptBlueprintSpec(
            id = "prompt_review",
            task = TaskType.LESSON_REVIEW,
            titleAr = "مخطط المراجعة الشاملة وتصحيح المفاهيم",
            subtitleAr = "خريطة أفكار، الأخطاء الشائعة وتصحيحها، وقائمة استذكار نهائية",
            sourcePath = "prompts/book_indexing/lesson_review.prompt.md",
            roleInstruction = "مراجعة شاملة مركزة للدرس تشمل خريطة الأفكار وأبرز الأخطاء الشائعة ونقاط الاستذكار السريع",
            outputContract = "ملخص هرمي + 3 أخطاء شائعة وتصويبها + قائمة تحقق قبل الاختبار",
            quickInstructions = "اجمع نقاط الدرس في خريطة ذهنية نصية وحدد أبرز 3 أخطاء يقع فيها الطلاب مع تصحيحها."
        ),
        PromptBlueprintSpec(
            id = "prompt_difficulty_adapter",
            task = TaskType.DIFFICULTY_ADAPTER,
            titleAr = "مخطط مواءمة المستويات (3 مسارات)",
            subtitleAr = "تفريق المحتوى إلى: ميسر للمبتدئ، قياسي، وإثرائي للمتقدم",
            sourcePath = "prompts/book_indexing/difficulty_adapter.prompt.md",
            roleInstruction = "إعادة صياغة متدرجة لمحتوى الدرس في ثلاثة مستويات (مبسط للمبتدئ، قياسي، وإثرائي للمتقدم)",
            outputContract = "مسار تأسيسي ميسر + مسار صفي قياسي + تحدٍّ إثرائي متعمق",
            quickInstructions = "أعد صياغة المفهوم الرئيسي في ثلاثة مستويات متدرجة مع نشاط قصير لكل مستوى."
        ),
        PromptBlueprintSpec(
            id = "prompt_interactive",
            task = TaskType.INTERACTIVE,
            titleAr = "مخطط الدرس التفاعلي المترابط (JSON + JS + HTML)",
            subtitleAr = "محطات تعلم تفاعلية + conceptTag + نقاط تحقق مرتبطة ببنك الأسئلة ومشغل HTML/JS",
            sourcePath = "prompts/book_indexing/interactive_activity.prompt.md",
            roleInstruction = "نشاطاً تعليمياً تفاعلياً منظماً بصيغة JSON آمنة تتضمن خطوات تفاعلية، وconceptTag لكل محطة، ونقاط تحقق مرتبطة ببنك الأسئلة، ومهمة ترتيب أو توصيل ليتم تصديره وتشغيله عبر JSON + JS + HTML",
            outputContract = "JSON صريح يحتوي schemaVersion, title, objective, steps[](conceptTag, checkpoint), ordering[], matching[]",
            schemaSnippet = """
                {
                  "schemaVersion": 1,
                  "title": "عنوان النشاط التفاعلي",
                  "objective": "الهدف التعليمي للنشاط",
                  "steps": [
                    {
                      "title": "المحطة الأولى",
                      "conceptTag": "المفهوم الأساسي المستهدف",
                      "body": "شرح مختصر للمفهوم في هذه المحطة",
                      "checkpoint": {
                        "conceptTag": "المفهوم الأساسي المستهدف",
                        "question": "سؤال تحقق سريع؟",
                        "options": ["خيار أ", "خيار ب", "خيار ج"],
                        "answer": "خيار أ"
                      }
                    }
                  ],
                  "ordering": ["الخطوة الأولى", "الخطوة الثانية", "الخطوة الثالثة"],
                  "matching": [
                    { "left": "المصطلح", "right": "التعريف المقابل" }
                  ]
                }
            """.trimIndent(),
            quickInstructions = "أخرج النشاط بصيغة JSON مع conceptTag صريح لكل محطة ونقطة تحقق متوافقة مع بنك الأسئلة ومشغّل JS/HTML الموحد."
        ),
        PromptBlueprintSpec(
            id = "prompt_questions",
            task = TaskType.QUESTIONS,
            titleAr = "مخطط بنك الأسئلة القياسي",
            subtitleAr = "أسئلة متعددة الأنماط مع الإجابة والتفسير والصعوبة وربط الصفحة المرجعية",
            sourcePath = "prompts/book_indexing/question_bank.prompt.md",
            roleInstruction = "بنك أسئلة مع الخيارات والإجابة الصحيحة والتفسير ومستوى الصعوبة وربط كل سؤال بالصفحة المرجعية",
            outputContract = "رقم السؤال + الخيارات أو صيغة التوصيل/الترتيب + الإجابة: + التفسير: + الصعوبة: أو مصفوفة JSON",
            schemaSnippet = QuestionResponseContract.schemaJson,
            quickInstructions = "نوّع الأسئلة بين اختيار من متعدد، صح أم خطأ، إكمال الفراغ، وترتيب مع تحديد conceptTag لكل سؤال."
        ),
        PromptBlueprintSpec(
            id = "prompt_lesson_text",
            task = TaskType.LESSON_TEXT,
            titleAr = "مخطط استخراج وحفظ المحتوى النصي للدرس",
            subtitleAr = "استخراج أمين لنص الدرس من ملف PDF وحفظه في قاعدة البيانات مرتبطاً بالفهرس",
            sourcePath = "prompts/book_indexing/lesson_text.prompt.md",
            roleInstruction = "استخراج المحتوى النصي الكامل للدرس من نسخة ملف PDF المرفقة بأمانة وتنظيمه في أقسام واضحة لحفظه كمرجع للدرس",
            outputContract = "JSON يحتوي title, sourcePages, sections[](heading, body), plainText",
            schemaSnippet = """
                {
                  "title": "عنوان الدرس",
                  "sourcePages": "5–8",
                  "sections": [
                    {
                      "heading": "عنوان القسم أو الفكرة",
                      "body": "النص الكامل المستخرج من صفحات الدرس"
                    }
                  ],
                  "plainText": "النص الكامل المتصل للدرس كما ورد في المصدر"
                }
            """.trimIndent(),
            quickInstructions = "استخرج كامل نص الدرس من ملف PDF المرفق بدقة مع الحفاظ على العناوين والتعاريف والأمثلة والرموز، وأخرجه وفق المخطط المتوقع."
        )
    )

    val schemas: List<BookIndexSchemaSpec> = listOf(
        BookIndexSchemaSpec(
            id = "schema_index",
            fileName = "workspace/Gxx/<SUBJECT>/index.json",
            titleAr = "مخطط الفهرس المرجعي للكتاب (index.json)",
            descriptionAr = "مخطط الفهرس المعتمد داخل التطبيق لتعريف الوحدات والدروس وأرقام الصفحات في الكتاب المدرسي.",
            schemaJson = """
                {
                  "schemaVersion": "edu.book_index.v2",
                  "gradeKey": "G04",
                  "gradeName": "الصف الرابع",
                  "subjectKey": "ARAB",
                  "subjectName": "اللغة العربية",
                  "title": "كتاب اللغة العربية",
                  "sourceLayout": "SINGLE_PART_FILE",
                  "partKey": "P01",
                  "offset": 4,
                  "approvalStatus": "APPROVED",
                  "requiresReview": false,
                  "units": [
                    {
                      "unitKey": "unit_01",
                      "title": "الوحدة الأولى",
                      "partKey": "P01",
                      "assignmentStatus": "assigned",
                      "assignmentSource": "INDEX_EVIDENCE",
                      "assignmentEvidence": "دليل واضح من فهرس المحتويات",
                      "lessons": [
                        {
                          "lessonKey": "lesson_01",
                          "title": "عنوان الدرس الأول",
                          "printedPageStart": 1,
                          "printedPageEnd": 6,
                          "pdfPageStart": 5,
                          "pdfPageEnd": 10
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()
        ),
        BookIndexSchemaSpec(
            id = "schema_manifest",
            fileName = "workspace/Gxx/<SUBJECT>/book_manifest.json",
            titleAr = "مخطط بطاقة الكتاب والغلاف (book_manifest.json)",
            descriptionAr = "بيانات الكتاب المرجعية ومسار الغلاف cover.png ومصدر PDF الأصلي.",
            schemaJson = """
                {
                  "title": "اسم الكتاب المدرسي",
                  "gradeKey": "G04",
                  "subjectKey": "ARAB",
                  "cover": "cover.png",
                  "totalPages": 120,
                  "createdAt": "2026-11-24T00:00:00Z"
                }
            """.trimIndent()
        ),
        BookIndexSchemaSpec(
            id = "schema_questions",
            fileName = "questions/<unit>/<lesson>/ai_generated.json",
            titleAr = "مخطط بنك الأسئلة التفاعلي (questions.json)",
            descriptionAr = "يدعم 6 أنماط: mcq, true_false, fill_blank, ordering, matching, short_answer مع تصنيف المفهوم conceptTag.",
            schemaJson = QuestionResponseContract.schemaJson
        ),
        BookIndexSchemaSpec(
            id = "schema_interactive",
            fileName = "edu_mini_generated/<unit>/<lesson>/lesson.interactive.json",
            titleAr = "مخطط النشاط التفاعلي الأصيل (lesson.interactive.json)",
            descriptionAr = "مخطط JSON تصريحي مزوّد بـ conceptTag يشغّله مشغّل الأنشطة المدمج وباني حزمة JS+HTML.",
            schemaJson = blueprints.first { it.task == TaskType.INTERACTIVE }.schemaSnippet
        ),
        BookIndexSchemaSpec(
            id = "schema_interactive_bundle",
            fileName = "interactive/<book>/<unit>/<lesson>/{index.html, interactive-engine.js, lesson.interactive.json}",
            titleAr = "مخطط تخزين حزمة الدرس التفاعلي المتكاملة (HTML + JS + JSON + Room DB)",
            descriptionAr = "يخزّن الحزمة التفاعلية في 3 ملفات مترابطة (HTML عارض + محرك JavaScript + بيانات JSON) ويربط نقاط التحقق بجداول questions و question_attempts لكل متعلّم.",
            schemaJson = """
                {
                  "storageLayout": {
                    "packageRoot": "edumini/packages/<grade>_<book>_<unit>_<lesson>/v<N>/",
                    "files": [
                      "index.html",
                      "interactive-engine.js",
                      "lesson.interactive.json"
                    ]
                  },
                  "databaseSync": {
                    "questionsTable": "id, workspace_id, lesson_key, concept_tag, prompt, options_json, correct_answer",
                    "questionAttemptsTable": "id, workspace_id, user_id, question_id, concept_tag, selected_answer, is_correct, source_mode"
                  },
                  "jsBridgeContract": {
                    "recordAttempt": "window.EduMiniBridge.recordQuestionAttempt(prompt, conceptTag, selected, correct, isCorrect)",
                    "complete": "window.EduMiniBridge.completeInteractive(scorePercent, earnedXp)"
                  }
                }
            """.trimIndent()
        ),
        BookIndexSchemaSpec(
            id = "schema_export_manifest",
            fileName = "manifest.json",
            titleAr = "مخطط حزمة التصدير القديمة",
            descriptionAr = "صيغة توافق قديمة لحزمة مساحة العمل؛ تحتوي بصمات SHA-256 للملفات المصدّرة.",
            schemaJson = """
                {
                  "format": "edu-mini-مخطط الفهرس-1.0",
                  "workspaceId": "import-...",
                  "gradeKey": "G04",
                  "exportedAt": "2026-11-24T00:00:00Z",
                  "draftsCount": 4,
                  "questionsCount": 18,
                  "assessmentsCount": 2,
                  "files": [
                    { "path": "questions/unit_01/lesson_01/ai_generated.json", "sha256": "...", "bytes": 1420 }
                  ]
                }
            """.trimIndent()
        )
    )

    fun blueprintFor(task: TaskType): PromptBlueprintSpec {
        return blueprints.firstOrNull { it.task == task } ?: blueprints.first()
    }

    fun assemblePrompt(
        lesson: LessonRef,
        task: TaskType,
        source: String,
        extra: String,
        questionCount: Int = 5,
        questionTypeIds: List<String> = emptyList(),
        questionLanguage: String = "ar",
        attachedPdfPagesCount: Int = 0,
        questionStyleHint: String = "",
        difficultyHint: String = "",
        promptTemplate: String = "",
        responseSchemaJson: String = "",
        responseFormat: String = "text"
    ): String {
        val safeCount = questionCount.coerceIn(1, 30)
        val blueprint = blueprintFor(task)
        val taskInstruction = task.labelAr
        val requestedQuestionTypes = QuestionResponseContract.effectiveTypes(questionTypeIds)
        val typeDistribution = if (task == TaskType.QUESTIONS) {
            QuestionResponseContract.expectedDistribution(safeCount, questionTypeIds)
        } else {
            emptyMap()
        }
        val languageInstruction = when (questionLanguage.trim().lowercase()) {
            "en" -> "English"
            "ar_en" -> "Arabic and English (bilingual question and answer text)"
            else -> "Arabic"
        }
        return buildString {
            append("أنت مساعد إعداد محتوى تعليمي. أنشئ ")
            append(taskInstruction)
            append(" للصف ")
            append(lesson.grade)
            append("، الكتاب ")
            append(lesson.bookTitle)
            append("، الوحدة ")
            append(lesson.unitTitle)
            append("، الدرس ")
            append(lesson.title)
            if (!lesson.pageRange.isNullOrBlank()) {
                append(" (الصفحات المرجعية: ")
                append(lesson.pageRange)
                append(")")
            }
            append(".\n")
            append("التزم بالمصدر ولا تخترع معلومات أو صفحات. اجعل المحتوى منظمًا وقابلاً للتحرير.\n")
            append("مخطط المهمة المرجعي: ${blueprint.sourcePath}\n")
            if (blueprint.outputContract.isNotBlank()) {
                append("مخطط الخرج المتوقع: ${blueprint.outputContract}\n")
            }
            if (task == TaskType.QUESTIONS) {
                append("العدد الإجمالي المطلوب من الأسئلة: $safeCount، وليس العدد لكل نوع.\n")
            } else if (task == TaskType.INTERACTIVE) {
                append("العدد المطلوب من العناصر التفاعلية: $safeCount.\n")
            }
            if (task == TaskType.QUESTIONS) {
                append("لغة نص السؤال والخيارات والإجابة والتفسير: $languageInstruction.\n")
                append("أنواع الأسئلة المسموح بها حصراً: ${requestedQuestionTypes.joinToString(", ") { "${it.id} (${it.labelAr})" }}. لا تنتج أي نوع خارج هذه القائمة.\n")
                append("التوزيع المطلوب للعدد الإجمالي حسب النوع: ${typeDistribution.entries.joinToString(", ") { "${it.key}=${it.value}" }}. التزم بالأعداد المحددة لكل نوع.\n")
            }
            if (questionStyleHint.isNotBlank()) append("تنسيق/تنويع العناصر المطلوب: $questionStyleHint.\n")
            if (difficultyHint.isNotBlank()) append("مستوى الصعوبة المطلوب (قيمة difficulty في JSON): $difficultyHint. استخدم هذه القيمة المعيارية كما هي.\n")
            if (promptTemplate.isNotBlank()) {
                append("\nتعليمات المهمة المرجعية من ملف ${blueprint.sourcePath}:\n")
                append(promptTemplate.trim())
                append("\n")
            }
            if (responseFormat.equals("json", ignoreCase = true) || responseSchemaJson.isNotBlank()) {
                append("أخرج استجابة منظمة وفق مخطط الاستجابة المرفق، دون Markdown أو نص خارج البنية المطلوبة.\n")
            }
            if (responseSchemaJson.isNotBlank()) {
                append("\nمخطط الاستجابة المعتمد من القالب (لا تغيّر أسماء الحقول أو أنواعها):\n")
                append(responseSchemaJson.trim())
                append("\n")
            }
            if (attachedPdfPagesCount > 0) {
                append("ملاحظة: ملف PDF الأصلي للدرس مرفق مع البرومبت كمدخل متعدد الوسائط؛ استخدم محتواه والصفحات المرجعية المحددة.\n")
            }
            if (source.isNotBlank()) {
                append("المصدر النصي:\n")
                append(source)
            } else {
                append("المصدر النصي: اعتمد على صفحات PDF المرفقة وبيانات الدرس.\n")
            }
            if (extra.isNotBlank()) {
                append("\nتوجيه إضافي:\n")
                append(extra)
            }
        }
    }
}
