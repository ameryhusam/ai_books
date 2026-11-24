package com.example.data.ai

import com.example.model.Indexer2SchemaSpec
import com.example.model.LessonRef
import com.example.model.PromptBlueprintSpec
import com.example.model.TaskType

object Indexer2PromptRegistry {

    val blueprints: List<PromptBlueprintSpec> = listOf(
        PromptBlueprintSpec(
            id = "prompt_explanation",
            task = TaskType.EXPLANATION,
            titleAr = "مخطط شرح الدرس المنهجي",
            subtitleAr = "تهيئة المفاهيم، خطوات الشرح، أمثلة محلولة، وملخص ختامي مرتبط بالصفحات",
            sourcePath = "prompts/indexer2/lesson_explanation.prompt.md",
            roleInstruction = "شرح مبسط ومنظم مع أمثلة محلولة وملخص ختامي",
            outputContract = "عناوين رئيسية واضحة + خطوات مرقمة + مثال تطبيقي + قاعدة ذهبية في الختام",
            quickInstructions = "قسّم الشرح إلى 3 خطوات متدرجة، وأضف مثالاً من الحياة اليومية مع خلاصة مركزة في النهاية."
        ),
        PromptBlueprintSpec(
            id = "prompt_summary",
            task = TaskType.SUMMARY,
            titleAr = "مخطط التلخيص المركّز",
            subtitleAr = "تقطير المفاهيم والقوانين الأساسية في نقاط منظمة وسريعة المراجعة",
            sourcePath = "prompts/indexer2/lesson_summary.prompt.md",
            roleInstruction = "ملخص مركز ومنظم لأهم أفكار ومفاهيم الدرس في نقاط واضحة",
            outputContract = "الفكرة المحورية + 4 إلى 6 نقاط مركزة + تذكير بصري/مقارنة سريعة",
            quickInstructions = "اعرض الفكرة الرئيسية أولاً ثم لخّص المفاهيم الأساسية في نقاط قصيرة مع جدول مقارنة إن أمكن."
        ),
        PromptBlueprintSpec(
            id = "prompt_objectives",
            task = TaskType.OBJECTIVES,
            titleAr = "مخطط الأهداف السلوكية ومؤشرات الأداء",
            subtitleAr = "صياغة أهداف قابلة للقياس ومؤشرات تحقق ومفردات مفتاحية",
            sourcePath = "prompts/indexer2/learning_objectives.prompt.md",
            roleInstruction = "أهداف تعلم سلوكية قابلة للقياس ومفردات مفتاحية مع تعريفاتها",
            outputContract = "أهداف بصيغة (أن + فعل مضارع قابل للقياس) + قائمة مفردات مفتاحية",
            quickInstructions = "صُغ 4 أهداف سلوكية تغطي الفهم والتطبيق، متبوعة بقائمة المصطلحات المفتاحية في الدرس."
        ),
        PromptBlueprintSpec(
            id = "prompt_vocabulary",
            task = TaskType.VOCABULARY,
            titleAr = "مخطط باني المفردات السياقي",
            subtitleAr = "استخراج المصطلحات والمفاهيم مع التعريف الميسر والمثال النصي",
            sourcePath = "prompts/indexer2/vocabulary_builder.prompt.md",
            roleInstruction = "مسرد مفردات ومصطلحات الدرس الأساسية مع التعريف المبسط ومثال سياقي لكل مصطلح",
            outputContract = "المصطلح • المعنى المبسط • مثال سياقي من الدرس • تنبيه لعدم الخلط",
            quickInstructions = "استخرج 6 مصطلحات رئيسية من الدرس، وعرّف كل منها بلغة مبسطة مع مثال سياقي دقيق."
        ),
        PromptBlueprintSpec(
            id = "prompt_worked_examples",
            task = TaskType.WORKED_EXAMPLES,
            titleAr = "مخطط الأمثلة المحلولة خطوة بخطوة",
            subtitleAr = "حل نموذجي مشروح السبب لكل خطوة مع تمرين تحقّق فوري",
            sourcePath = "prompts/indexer2/worked_examples.prompt.md",
            roleInstruction = "سلسلة أمثلة محلولة خطوة بخطوة مع توضيح سبب كل خطوة وتدريب مماثل للطالب",
            outputContract = "المعطيات -> الخطوة 1 (مع التعليل) -> النتيجة النهائية -> تدريب تطبيقي مماثل",
            quickInstructions = "قدّم مثالين محلولين بتسلسل الخطوات مع تعليل كل خطوة، واختم بتمرين موجّه للطالب."
        ),
        PromptBlueprintSpec(
            id = "prompt_review",
            task = TaskType.LESSON_REVIEW,
            titleAr = "مخطط المراجعة الشاملة وتصحيح المفاهيم",
            subtitleAr = "خريطة أفكار، الأخطاء الشائعة وتصحيحها، وقائمة استذكار نهائية",
            sourcePath = "prompts/indexer2/lesson_review.prompt.md",
            roleInstruction = "مراجعة شاملة مركزة للدرس تشمل خريطة الأفكار وأبرز الأخطاء الشائعة ونقاط الاستذكار السريع",
            outputContract = "ملخص هرمي + 3 أخطاء شائعة وتصويبها + قائمة تحقق قبل الاختبار",
            quickInstructions = "اجمع نقاط الدرس في خريطة ذهنية نصية وحدد أبرز 3 أخطاء يقع فيها الطلاب مع تصحيحها."
        ),
        PromptBlueprintSpec(
            id = "prompt_difficulty_adapter",
            task = TaskType.DIFFICULTY_ADAPTER,
            titleAr = "مخطط مواءمة المستويات (3 مسارات)",
            subtitleAr = "تفريق المحتوى إلى: ميسر للمبتدئ، قياسي، وإثرائي للمتقدم",
            sourcePath = "prompts/indexer2/difficulty_adapter.prompt.md",
            roleInstruction = "إعادة صياغة متدرجة لمحتوى الدرس في ثلاثة مستويات (مبسط للمبتدئ، قياسي، وإثرائي للمتقدم)",
            outputContract = "مسار تأسيسي ميسر + مسار صفي قياسي + تحدٍّ إثرائي متعمق",
            quickInstructions = "أعد صياغة المفهوم الرئيسي في ثلاثة مستويات متدرجة مع نشاط قصير لكل مستوى."
        ),
        PromptBlueprintSpec(
            id = "prompt_interactive",
            task = TaskType.INTERACTIVE,
            titleAr = "مخطط النشاط التفاعلي JSON (indexer2)",
            subtitleAr = "محطات تعلم تفاعلية + نقاط تحقق + ترتيب + توصيل بصيغة متوافقة مع المشغّل",
            sourcePath = "prompts/indexer2/interactive_activity.prompt.md",
            roleInstruction = "نشاطاً تعليمياً تفاعلياً منظماً بصيغة JSON آمنة تتضمن خطوات تفاعلية، ونقاط تحقق، ومهمة ترتيب أو توصيل",
            outputContract = "JSON صريح يحتوي title, objective, steps[], ordering[], matching[]",
            schemaSnippet = """
                {
                  "title": "عنوان النشاط التفاعلي",
                  "objective": "الهدف التعليمي للنشاط",
                  "steps": [
                    {
                      "title": "المحطة الأولى",
                      "body": "شرح مختصر للمفهوم في هذه المحطة",
                      "checkpoint": {
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
            quickInstructions = "أخرج النشاط بصيغة JSON مطابقة للمخطط القياسي مع محطتين تفاعليتين على الأقل ومهمة ترتيب وتوصيل."
        ),
        PromptBlueprintSpec(
            id = "prompt_questions",
            task = TaskType.QUESTIONS,
            titleAr = "مخطط بنك الأسئلة القياسي (indexer2)",
            subtitleAr = "أسئلة متعددة الأنماط مع الإجابة والتفسير والصعوبة وربط الصفحة المرجعية",
            sourcePath = "prompts/indexer2/question_bank.prompt.md",
            roleInstruction = "بنك أسئلة مع الخيارات والإجابة الصحيحة والتفسير ومستوى الصعوبة وربط كل سؤال بالصفحة المرجعية",
            outputContract = "رقم السؤال + الخيارات أو صيغة التوصيل/الترتيب + الإجابة: + التفسير: + الصعوبة: أو مصفوفة JSON",
            schemaSnippet = """
                [
                  {
                    "type": "mcq",
                    "prompt": "نص السؤال المرتبط بصفحات الدرس",
                    "options": ["أ", "ب", "ج", "د"],
                    "correctAnswer": "أ",
                    "rationale": "تعليل الإجابة استناداً إلى الدرس",
                    "difficulty": "medium",
                    "sourcePages": "5–8"
                  }
                ]
            """.trimIndent(),
            quickInstructions = "نوّع الأسئلة بين اختيار من متعدد، صح أم خطأ، إكمال الفراغ، وترتيب مع كتابة الإجابة والتفسير لكل سؤال."
        )
    )

    val schemas: List<Indexer2SchemaSpec> = listOf(
        Indexer2SchemaSpec(
            id = "schema_index",
            fileName = "workspace/Gxx/<SUBJECT>/index.json",
            titleAr = "مخطط الفهرس المرجعي للكتاب (index.json)",
            descriptionAr = "المخطط المعتمد في indexer2 لتعريف الوحدات والدروس وأرقام الصفحات في الكتاب المدرسي.",
            schemaJson = """
                {
                  "schemaVersion": 1,
                  "gradeKey": "G04",
                  "subjectKey": "ARAB",
                  "units": [
                    {
                      "unitKey": "unit_01",
                      "title": "الوحدة الأولى",
                      "partKey": "P01",
                      "lessons": [
                        {
                          "lessonKey": "lesson_01",
                          "title": "عنوان الدرس الأول",
                          "pdfPageStart": 5,
                          "pdfPageEnd": 10
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()
        ),
        Indexer2SchemaSpec(
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
        Indexer2SchemaSpec(
            id = "schema_questions",
            fileName = "questions/<unit>/<lesson>/ai_generated.json",
            titleAr = "مخطط بنك الأسئلة التفاعلي (questions.json)",
            descriptionAr = "يدعم 6 أنماط: mcq, true_false, fill_blank, ordering, matching, short_answer.",
            schemaJson = """
                [
                  {
                    "type": "mcq",
                    "prompt": "ما الفكرة الرئيسة في الفقرة الأولى؟",
                    "options": ["الخيار الأول", "الخيار الثاني", "الخيار الثالث", "الخيار الرابع"],
                    "correctAnswer": "الخيار الأول",
                    "rationale": "ورد ذلك في الصفحة 6 من الكتاب.",
                    "difficulty": "easy"
                  },
                  {
                    "type": "fill_blank",
                    "prompt": "يتكون الماء من ذرتي ___ وذرة أكسجين.",
                    "correctAnswer": "هيدروجين",
                    "difficulty": "medium"
                  },
                  {
                    "type": "ordering",
                    "prompt": "رتب مراحل التجربة:",
                    "options": ["الملاحظة", "الفرضية", "التجربة", "الاستنتاج"],
                    "correctAnswer": "الملاحظة | الفرضية | التجربة | الاستنتاج",
                    "difficulty": "hard"
                  }
                ]
            """.trimIndent()
        ),
        Indexer2SchemaSpec(
            id = "schema_interactive",
            fileName = "edu_mini_generated/<unit>/<lesson>/interactive.json",
            titleAr = "مخطط النشاط التفاعلي الأصيل (interactive.json)",
            descriptionAr = "مخطط JSON تصريحي يشغّله مشغّل الأنشطة المدمج بالخطوات ونقاط التحقق.",
            schemaJson = blueprints.first { it.task == TaskType.INTERACTIVE }.schemaSnippet
        ),
        Indexer2SchemaSpec(
            id = "schema_export_manifest",
            fileName = "manifest.json",
            titleAr = "مخطط حزمة التصدير الموثّقة (indexer2 Export)",
            descriptionAr = "يحتوي بصمات SHA-256 لكل ملف مدمج في الحزمة مع المسودات والأسئلة والتقييمات.",
            schemaJson = """
                {
                  "format": "edu-mini-indexer2-1.0",
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
        attachedPdfPagesCount: Int = 0,
        questionStyleHint: String = "",
        difficultyHint: String = "",
        promptTemplate: String = ""
    ): String {
        val safeCount = questionCount.coerceIn(1, 30)
        val blueprint = blueprintFor(task)
        val taskInstruction = when (task) {
            TaskType.QUESTIONS ->
                "بنك أسئلة مكوّن من $safeCount أسئلة مع الخيارات والإجابة الصحيحة والتفسير ومستوى الصعوبة وربط كل سؤال بالصفحة المرجعية"
            TaskType.INTERACTIVE ->
                "نشاطاً تعليمياً تفاعلياً منظماً بصيغة JSON آمنة تتضمن خطوات تفاعلية، ونقاط تحقق، ومهمة ترتيب أو توصيل، و$safeCount محطات تعلم"
            else -> blueprint.roleInstruction
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
            append("المخطط المرجعي (indexer2): ${blueprint.sourcePath}\n")
            append("معيار المخرجات: ${blueprint.outputContract}.\n")
            if (promptTemplate.isNotBlank()) {
                append("\nتعليمات المهمة المرجعية من ملف ${blueprint.sourcePath}:\n")
                append(promptTemplate.trim())
                append("\n")
            }
            if (task == TaskType.QUESTIONS) {
                append("عدد الأسئلة المطلوب بدقة: $safeCount.\n")
                if (questionStyleHint.isNotBlank()) {
                    append("نمط الأسئلة المطلوب: $questionStyleHint.\n")
                }
                if (difficultyHint.isNotBlank()) {
                    append("مستوى الصعوبة المستهدف: $difficultyHint.\n")
                }
                append("أخرج JSON صالحاً فقط دون Markdown أو نص خارج JSON. استخدم كائن الجذر {\"questions\":[...]}، ولا تنتج سؤالاً بلا إجابة صحيحة صريحة.\n")
                append(blueprint.schemaSnippet)
                append("\n")
            } else if (task == TaskType.INTERACTIVE) {
                append("أخرج JSON صالحاً فقط دون HTML أو JavaScript أو Markdown، والتزم بمخطط النشاط التفاعلي.\n")
                append(blueprint.schemaSnippet)
                append("\n")
            }
            if (attachedPdfPagesCount > 0) {
                append("ملاحظة: مرفق مع هذا الطلب $attachedPdfPagesCount صفحة مصورة من ملف PDF الخاص بالدرس لاستخراج المحتوى والاعتماد عليها.\n")
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
