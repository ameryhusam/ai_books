# Question bank — indexer2 contract

أخرج JSON صالحاً فقط بالشكل:
{"questions":[{"type":"mcq|true_false|short_answer|fill_blank|ordering|matching","prompt":"...","options":["..."],"correctAnswer":"...","rationale":"...","difficulty":"easy|medium|hard","sourcePages":"..."}]}
- يجب أن تكون الإجابة الصحيحة صريحة ومسنودة بالمصدر.
- في MCQ وTrue/False يجب أن تطابق correctAnswer أحد الخيارات حرفياً.
- لا تترك الإجابة فارغة ولا تخمّنها ولا تستخدم الخيار الأول افتراضياً.
- إذا تعذر تحديد الإجابة، لا تنتج السؤال.
- لا تكرر السؤال بصياغة مختلفة، ووزّع مستويات الصعوبة.
- لا تضع صفحة مرجعية غير موجودة في المصدر.
