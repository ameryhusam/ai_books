# Question bank — indexer2 contract

أخرج JSON صالحاً فقط بالشكل:
{"questions":[{"type":"mcq|true_false|short_answer|fill_blank|ordering|matching","conceptTag":"المفهوم التعليمي الدقيق الذي يقيسه السؤال","prompt":"...","options":["..."],"correctAnswer":"...","rationale":"...","difficulty":"easy|medium|hard","sourcePages":"..."}]}
- يجب أن تكون الإجابة الصحيحة صريحة ومسنودة بالمصدر.
- حدد لكل سؤال conceptTag واضحاً ومختصراً لمتابعة إتقان الطالب للمفاهيم وتشخيص نقاط الضعف.
- في MCQ وTrue/False يجب أن تطابق correctAnswer أحد الخيارات حرفياً.
- لا تترك الإجابة فارغة ولا تخمّنها ولا تستخدم الخيار الأول افتراضياً.
- إذا تعذر تحديد الإجابة، لا تنتج السؤال.
- لا تكرر السؤال بصياغة مختلفة، ووزّع مستويات الصعوبة (سهل، متوسط، متقدم).
- لا تضع صفحة مرجعية غير موجودة في المصدر.
