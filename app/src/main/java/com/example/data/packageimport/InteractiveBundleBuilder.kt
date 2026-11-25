package com.example.data.packageimport

import android.content.Context
import com.example.model.InteractiveActivityModel
import com.example.model.LessonRef
import com.example.model.PackageKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Compiles an [InteractiveActivityModel] into a self-contained offline HTML5 + JS + JSON bundle:
 *  - `lesson.interactive.json` (structured steps, conceptTag, checkpoints, ordering, matching)
 *  - `interactive-engine.js` (vanilla RTL interactive engine communicating with `window.EduMiniBridge`)
 *  - `index.html` (mobile-responsive RTL Arabic viewer loading the JSON & JS engine)
 */
object InteractiveBundleBuilder {

    private val prettyJson = Json { prettyPrint = true }

    data class GeneratedBundleFiles(
        val jsonContent: String,
        val jsContent: String,
        val htmlContent: String
    )

    fun buildBundleFiles(
        activity: InteractiveActivityModel,
        lesson: LessonRef
    ): GeneratedBundleFiles {
        val jsonPayload = buildJsonObject {
            put("schemaVersion", 1)
            put("workspaceId", lesson.workspaceId)
            put("gradeKey", lesson.grade)
            put("bookKey", lesson.bookKey)
            put("unitKey", lesson.unitKey)
            put("lessonKey", lesson.lessonKey)
            put("title", activity.title.ifBlank { lesson.title })
            put("objective", activity.objective)
            put("steps", buildJsonArray {
                activity.steps.forEachIndexed { idx, step ->
                    add(buildJsonObject {
                        put("id", "step_${idx + 1}")
                        put("title", step.title)
                        put("body", step.body)
                        put("conceptTag", step.conceptTag.ifBlank { step.title.ifBlank { lesson.title } })
                        if (step.checkpointQuestion.isNotBlank()) {
                            put("checkpoint", buildJsonObject {
                                put("question", step.checkpointQuestion)
                                put("options", buildJsonArray {
                                    step.checkpointOptions.forEach { opt ->
                                        add(kotlinx.serialization.json.JsonPrimitive(opt))
                                    }
                                })
                                put("answer", step.checkpointAnswer)
                                put("conceptTag", step.conceptTag.ifBlank { step.title.ifBlank { lesson.title } })
                            })
                        }
                    })
                }
            })
            put("ordering", buildJsonArray {
                activity.orderingItems.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("matching", buildJsonArray {
                activity.matchingPairs.forEach { pair ->
                    add(buildJsonObject {
                        put("left", pair.left)
                        put("right", pair.right)
                    })
                }
            })
        }

        val jsonText = prettyJson.encodeToString(jsonPayload)
        val jsText = buildInteractiveEngineJs()
        val htmlText = buildInteractiveIndexHtml(
            title = activity.title.ifBlank { lesson.title },
            bookSubtitle = "${lesson.bookTitle} • ${lesson.unitTitle}"
        )
        return GeneratedBundleFiles(
            jsonContent = jsonText,
            jsContent = jsText,
            htmlContent = htmlText
        )
    }

    fun materializePackage(
        context: Context,
        importer: InteractivePackageImporter,
        activity: InteractiveActivityModel,
        lesson: LessonRef,
        nextVersion: Int
    ): ImportedPackageStaging {
        val bundlesRoot = File(context.filesDir, "edumini/packages")
        val safeLessonDir = "${lesson.grade}_${lesson.bookKey}_${lesson.unitKey}_${lesson.lessonKey}"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val versionDir = File(bundlesRoot, "$safeLessonDir/v$nextVersion")
        if (versionDir.exists()) versionDir.deleteRecursively()
        versionDir.mkdirs()

        val files = buildBundleFiles(activity, lesson)
        val jsonFile = File(versionDir, "lesson.interactive.json")
        val jsFile = File(versionDir, "interactive-engine.js")
        val htmlFile = File(versionDir, "index.html")

        jsonFile.writeText(files.jsonContent, StandardCharsets.UTF_8)
        jsFile.writeText(files.jsContent, StandardCharsets.UTF_8)
        htmlFile.writeText(files.htmlContent, StandardCharsets.UTF_8)

        val totalBytes = jsonFile.length() + jsFile.length() + htmlFile.length()
        val report = importer.inspectExtractedPackage(
            kind = PackageKind.ZIP_BUNDLE,
            fileName = "lesson_interactive_bundle_v$nextVersion",
            versionDir = versionDir,
            entryRelativePath = "index.html",
            totalFiles = 3,
            totalBytes = totalBytes,
            lesson = lesson,
            sourcePages = lesson.pageRange
        )

        return ImportedPackageStaging(
            kind = PackageKind.ZIP_BUNDLE,
            title = activity.title.ifBlank { lesson.title },
            rootDir = versionDir.absolutePath,
            entryFile = "index.html",
            report = report
        )
    }

    private fun buildInteractiveIndexHtml(title: String, bookSubtitle: String): String {
        val safeTitle = escapeHtml(title)
        val safeSub = escapeHtml(bookSubtitle)
        return """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
  <title>$safeTitle</title>
  <style>
    :root {
      --bg: #F6F8F5;
      --card: #FFFFFF;
      --ink: #1C2B22;
      --muted: #5B6B61;
      --green: #16A34A;
      --green-dark: #14532D;
      --mint: #DCFCE7;
      --purple: #6D28D9;
      --lilac: #F3E8FF;
      --orange: #EA580C;
      --red: #DC2626;
      --line: #DCE4DD;
      --yellow: #FACC15;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 14px;
      font-family: system-ui, -apple-system, "Segoe UI", Tahoma, Arial, sans-serif;
      background: var(--bg);
      color: var(--ink);
      line-height: 1.65;
    }
    .header-card {
      background: linear-gradient(135deg, var(--green-dark), var(--green));
      color: #FFFFFF;
      border-radius: 18px;
      padding: 16px;
      margin-bottom: 12px;
    }
    .header-sub { font-size: 12px; opacity: 0.9; margin-bottom: 4px; }
    .header-title { font-size: 19px; font-weight: 800; margin: 0 0 6px 0; }
    .header-obj { font-size: 13px; opacity: 0.95; margin: 0; }
    .progress-wrap {
      background: var(--card);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 10px 14px;
      margin-bottom: 12px;
    }
    .progress-meta {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--muted);
      margin-bottom: 6px;
      font-weight: 600;
    }
    .progress-bar {
      height: 8px;
      background: var(--mint);
      border-radius: 99px;
      overflow: hidden;
    }
    .progress-fill {
      height: 100%;
      width: 0%;
      background: var(--green);
      transition: width 0.25s ease;
    }
    .station-card {
      background: var(--card);
      border: 1px solid var(--line);
      border-radius: 16px;
      padding: 16px;
      margin-bottom: 12px;
    }
    .concept-badge {
      display: inline-block;
      background: var(--lilac);
      color: var(--purple);
      font-size: 12px;
      font-weight: 700;
      padding: 3px 10px;
      border-radius: 99px;
      margin-bottom: 8px;
    }
    .station-title { font-size: 17px; font-weight: 800; margin: 0 0 8px 0; }
    .station-body { font-size: 15px; color: var(--ink); margin: 0 0 12px 0; white-space: pre-line; }
    .checkpoint-box {
      background: #FAF7FF;
      border: 1px solid #E5D6FA;
      border-radius: 14px;
      padding: 12px;
      margin-top: 8px;
    }
    .checkpoint-q { font-size: 14px; font-weight: 700; color: var(--purple); margin: 0 0 10px 0; }
    .opt-btn {
      display: block;
      width: 100%;
      text-align: right;
      background: #FFFFFF;
      border: 1.5px solid var(--line);
      border-radius: 11px;
      padding: 11px 12px;
      margin-bottom: 8px;
      font-size: 14px;
      color: var(--ink);
      cursor: pointer;
    }
    .opt-btn.selected { border-color: var(--purple); background: var(--lilac); font-weight: 700; }
    .opt-btn.correct { border-color: var(--green); background: var(--mint); color: var(--green-dark); font-weight: 700; }
    .opt-btn.wrong { border-color: var(--red); background: #FEE2E2; color: var(--red); }
    .action-row { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
    .btn {
      flex: 1;
      min-height: 44px;
      border-radius: 12px;
      border: none;
      font-size: 14px;
      font-weight: 800;
      padding: 10px 14px;
      cursor: pointer;
    }
    .btn-primary { background: var(--green); color: #FFFFFF; }
    .btn-accent { background: var(--yellow); color: var(--ink); }
    .btn-outline { background: #FFFFFF; color: var(--ink); border: 1.5px solid var(--line); }
    .btn:disabled { opacity: 0.45; cursor: not-allowed; }
    .feedback-line {
      margin-top: 8px;
      padding: 8px 10px;
      border-radius: 10px;
      font-size: 13px;
      font-weight: 700;
    }
    .feedback-ok { background: var(--mint); color: var(--green-dark); }
    .feedback-bad { background: #FFEDD5; color: var(--orange); }
    .order-item, .match-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 10px 12px;
      border: 1px solid var(--line);
      border-radius: 11px;
      margin-bottom: 7px;
      background: var(--bg);
    }
    .pill-choice {
      display: inline-block;
      margin: 4px;
      padding: 8px 12px;
      border-radius: 99px;
      border: 1px solid var(--line);
      background: #FFFFFF;
      cursor: pointer;
      font-size: 13px;
    }
    .pill-choice.active { border-color: var(--purple); background: var(--lilac); color: var(--purple); font-weight: 700; }
  </style>
</head>
<body>
  <div class="header-card">
    <div class="header-sub">$safeSub</div>
    <h1 class="header-title" id="lesson-title">$safeTitle</h1>
    <p class="header-obj" id="lesson-objective"></p>
  </div>

  <div class="progress-wrap">
    <div class="progress-meta">
      <span id="station-counter">جاري تحميل الدرس التفاعلي...</span>
      <span id="score-counter"></span>
    </div>
    <div class="progress-bar"><div class="progress-fill" id="progress-fill"></div></div>
  </div>

  <div id="interactive-stage" class="station-card">
    <p>جاري تجهيز المحطات التفاعلية...</p>
  </div>

  <script src="./interactive-engine.js"></script>
</body>
</html>
        """.trimIndent()
    }

    private fun buildInteractiveEngineJs(): String {
        return """
(function () {
  var state = {
    data: null,
    stationIndex: 0,
    stations: [],
    selectedOption: {},
    verifiedCheckpoint: {},
    orderingList: [],
    orderingChecked: false,
    matchSelections: {},
    activeLeft: 0,
    matchingChecked: false,
    correctCheckpoints: 0,
    totalCheckpoints: 0,
    completedNotified: false
  };

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (typeof text === "string") node.textContent = text;
    return node;
  }

  function normalizeText(value) {
    return String(value || "").trim().toLowerCase().replace(/\s+/g, " ");
  }

  function reportQuestionToHost(prompt, conceptTag, selected, correct, isCorrect) {
    try {
      if (window.EduMiniBridge && typeof window.EduMiniBridge.recordQuestionAttempt === "function") {
        window.EduMiniBridge.recordQuestionAttempt(
          String(prompt || ""),
          String(conceptTag || ""),
          String(selected || ""),
          String(correct || ""),
          Boolean(isCorrect)
        );
      }
    } catch (err) {}
  }

  function reportCompletionToHost(scorePercent, earnedXp) {
    if (state.completedNotified) return;
    state.completedNotified = true;
    try {
      if (window.EduMiniBridge && typeof window.EduMiniBridge.completeInteractive === "function") {
        window.EduMiniBridge.completeInteractive(Number(scorePercent || 100), Number(earnedXp || 35));
      }
    } catch (err) {}
  }

  function buildStations(data) {
    var list = [];
    var steps = Array.isArray(data.steps) ? data.steps : [];
    for (var i = 0; i < steps.length; i++) {
      list.push({ type: "step", step: steps[i], idx: i });
      if (steps[i] && steps[i].checkpoint && steps[i].checkpoint.question) {
        state.totalCheckpoints += 1;
      }
    }
    if (Array.isArray(data.ordering) && data.ordering.length > 0) {
      state.orderingList = data.ordering.slice().reverse();
      list.push({ type: "ordering" });
    }
    if (Array.isArray(data.matching) && data.matching.length > 0) {
      list.push({ type: "matching" });
    }
    list.push({ type: "done" });
    state.stations = list;
  }

  function updateHeaderProgress() {
    var counter = document.getElementById("station-counter");
    var scoreEl = document.getElementById("score-counter");
    var fill = document.getElementById("progress-fill");
    var total = Math.max(1, state.stations.length - 1);
    var current = Math.min(state.stationIndex + 1, total);
    if (state.stations[state.stationIndex] && state.stations[state.stationIndex].type === "done") {
      counter.textContent = "اكتملت جميع المحطات";
      fill.style.width = "100%";
    } else {
      counter.textContent = "المحطة " + current + " من " + total;
      fill.style.width = Math.round((current / total) * 100) + "%";
    }
    if (state.totalCheckpoints > 0) {
      scoreEl.textContent = "نقاط التحقق: " + state.correctCheckpoints + " / " + state.totalCheckpoints;
    } else {
      scoreEl.textContent = "";
    }
  }

  function renderNavButtons(container) {
    var row = el("div", "action-row");
    if (state.stationIndex > 0) {
      var prevBtn = el("button", "btn btn-outline", "السابق");
      prevBtn.addEventListener("click", function () {
        state.stationIndex -= 1;
        render();
      });
      row.appendChild(prevBtn);
    }
    var isLastContent = state.stationIndex >= state.stations.length - 2;
    var nextBtn = el("button", "btn btn-primary", isLastContent ? "إنهاء النشاط التفاعلي" : "المحطة التالية");
    nextBtn.addEventListener("click", function () {
      state.stationIndex += 1;
      render();
    });
    row.appendChild(nextBtn);
    container.appendChild(row);
  }

  function renderStepStation(stage, station) {
    var step = station.step || {};
    var concept = step.conceptTag || (step.checkpoint && step.checkpoint.conceptTag) || state.data.title || "";
    if (concept) stage.appendChild(el("div", "concept-badge", "المفهوم: " + concept));
    stage.appendChild(el("h2", "station-title", step.title || ("المحطة " + (station.idx + 1))));
    if (step.body) stage.appendChild(el("p", "station-body", step.body));

    var cp = step.checkpoint;
    if (cp && cp.question) {
      var box = el("div", "checkpoint-box");
      box.appendChild(el("div", "checkpoint-q", "نقطة تحقق: " + cp.question));
      var opts = Array.isArray(cp.options) ? cp.options : [];
      var picked = state.selectedOption[station.idx] || "";
      var verified = Boolean(state.verifiedCheckpoint[station.idx]);
      var expected = cp.answer || "";

      for (var i = 0; i < opts.length; i++) {
        (function (optText) {
          var cls = "opt-btn";
          var isRightOpt = normalizeText(optText) === normalizeText(expected);
          if (verified) {
            if (isRightOpt) cls += " correct";
            else if (optText === picked) cls += " wrong";
          } else if (optText === picked) {
            cls += " selected";
          }
          var b = el("button", cls, optText);
          b.disabled = verified;
          b.addEventListener("click", function () {
            state.selectedOption[station.idx] = optText;
            render();
          });
          box.appendChild(b);
        })(opts[i]);
      }

      if (!verified && opts.length > 0) {
        var checkBtn = el("button", "btn btn-accent", "تحقق وسجّل النتيجة");
        checkBtn.disabled = !picked;
        checkBtn.addEventListener("click", function () {
          state.verifiedCheckpoint[station.idx] = true;
          var ok = normalizeText(picked) === normalizeText(expected);
          if (ok) state.correctCheckpoints += 1;
          reportQuestionToHost(cp.question, concept, picked, expected, ok);
          render();
        });
        box.appendChild(checkBtn);
      } else if (verified) {
        var okNow = normalizeText(picked) === normalizeText(expected);
        var msg = okNow
          ? "إجابة صحيحة ✓ تم تسجيل إتقان المفهوم في سجلّك"
          : "الإجابة الصحيحة: " + expected + " (تم تسجيلها لمراجعة المفهوم)";
        box.appendChild(el("div", "feedback-line " + (okNow ? "feedback-ok" : "feedback-bad"), msg));
      }
      stage.appendChild(box);
    }
    renderNavButtons(stage);
  }

  function renderOrderingStation(stage) {
    stage.appendChild(el("div", "concept-badge", "نشاط الترتيب التسلسلي"));
    stage.appendChild(el("h2", "station-title", "رتّب العناصر بالتسلسل الصحيح"));
    var expected = state.data.ordering || [];

    for (var i = 0; i < state.orderingList.length; i++) {
      (function (idx, text) {
        var row = el("div", "order-item");
        row.appendChild(el("span", "", (idx + 1) + ". " + text));
        var controls = el("div", "");
        var up = el("button", "pill-choice", "▲");
        up.disabled = idx === 0 || state.orderingChecked;
        up.addEventListener("click", function () {
          var tmp = state.orderingList[idx - 1];
          state.orderingList[idx - 1] = state.orderingList[idx];
          state.orderingList[idx] = tmp;
          render();
        });
        var down = el("button", "pill-choice", "▼");
        down.disabled = idx === state.orderingList.length - 1 || state.orderingChecked;
        down.addEventListener("click", function () {
          var tmp = state.orderingList[idx + 1];
          state.orderingList[idx + 1] = state.orderingList[idx];
          state.orderingList[idx] = tmp;
          render();
        });
        controls.appendChild(up);
        controls.appendChild(down);
        row.appendChild(controls);
        stage.appendChild(row);
      })(i, state.orderingList[i]);
    }

    if (!state.orderingChecked) {
      var verify = el("button", "btn btn-accent", "تحقق من الترتيب");
      verify.addEventListener("click", function () {
        state.orderingChecked = true;
        var ok = JSON.stringify(state.orderingList) === JSON.stringify(expected);
        reportQuestionToHost(
          "رتّب خطوات: " + (state.data.title || ""),
          state.data.title || "الترتيب التسلسلي",
          state.orderingList.join(" | "),
          expected.join(" | "),
          ok
        );
        render();
      });
      stage.appendChild(verify);
    } else {
      var allMatch = JSON.stringify(state.orderingList) === JSON.stringify(expected);
      stage.appendChild(el("div", "feedback-line " + (allMatch ? "feedback-ok" : "feedback-bad"),
        allMatch ? "ترتيب صحيح بالكامل ✓" : ("الترتيب الصحيح: " + expected.join(" ← "))));
    }
    renderNavButtons(stage);
  }

  function renderMatchingStation(stage) {
    var pairs = state.data.matching || [];
    stage.appendChild(el("div", "concept-badge", "نشاط التوصيل"));
    stage.appendChild(el("h2", "station-title", "طابق كل مفهوم مع التعريف المقابل"));
    for (var i = 0; i < pairs.length; i++) {
      (function (idx, pair) {
        var row = el("div", "match-row");
        row.appendChild(el("strong", "", pair.left));
        var chosen = state.matchSelections[idx] || "اضغط ثم اختر بالأسفل";
        row.appendChild(el("span", "", chosen));
        if (state.activeLeft === idx) row.style.borderColor = "#6D28D9";
        row.addEventListener("click", function () {
          if (!state.matchingChecked) {
            state.activeLeft = idx;
            render();
          }
        });
        stage.appendChild(row);
      })(i, pairs[i]);
    }
    var rights = pairs.map(function (p) { return p.right; }).reverse();
    var choicesWrap = el("div", "");
    for (var r = 0; r < rights.length; r++) {
      (function (txt) {
        var pill = el("button", "pill-choice" + (state.matchSelections[state.activeLeft] === txt ? " active" : ""), txt);
        pill.disabled = state.matchingChecked;
        pill.addEventListener("click", function () {
          state.matchSelections[state.activeLeft] = txt;
          if (state.activeLeft < pairs.length - 1) state.activeLeft += 1;
          render();
        });
        choicesWrap.appendChild(pill);
      })(rights[r]);
    }
    stage.appendChild(choicesWrap);

    if (!state.matchingChecked) {
      var checkMatch = el("button", "btn btn-accent", "تحقق من التوصيل");
      checkMatch.disabled = Object.keys(state.matchSelections).length < pairs.length;
      checkMatch.addEventListener("click", function () {
        state.matchingChecked = true;
        var okCount = 0;
        for (var j = 0; j < pairs.length; j++) {
          if (state.matchSelections[j] === pairs[j].right) okCount += 1;
        }
        reportQuestionToHost(
          "توصيل مفاهيم: " + (state.data.title || ""),
          state.data.title || "التوصيل",
          pairs.map(function (p, k) { return p.left + "=" + (state.matchSelections[k] || ""); }).join(" | "),
          pairs.map(function (p) { return p.left + "=" + p.right; }).join(" | "),
          okCount === pairs.length
        );
        render();
      });
      stage.appendChild(checkMatch);
    } else {
      var matchedCount = 0;
      for (var k = 0; k < pairs.length; k++) {
        if (state.matchSelections[k] === pairs[k].right) matchedCount += 1;
      }
      stage.appendChild(el("div", "feedback-line feedback-ok", "طابقت " + matchedCount + " من " + pairs.length + " بنجاح ✓"));
    }
    renderNavButtons(stage);
  }

  function renderDoneStation(stage) {
    var scorePct = state.totalCheckpoints > 0
      ? Math.round((state.correctCheckpoints * 100) / state.totalCheckpoints)
      : 100;
    reportCompletionToHost(scorePct, 35);
    stage.appendChild(el("div", "concept-badge", "اكتمل الدرس التفاعلي ✓"));
    stage.appendChild(el("h2", "station-title", "أحسنت! أتممت جميع المحطات التفاعلية"));
    stage.appendChild(el("p", "station-body", "تم حفظ إجابات نقاط التحقق ومستوى إتقان المفاهيم في قاعدة بيانات التطبيق. يمكنك الآن الانتقال لاختبار نهاية الدرس."));
    var restartBtn = el("button", "btn btn-outline", "إعادة استعراض المحطات");
    restartBtn.addEventListener("click", function () {
      state.stationIndex = 0;
      render();
    });
    stage.appendChild(restartBtn);
  }

  function render() {
    var stage = document.getElementById("interactive-stage");
    stage.innerHTML = "";
    updateHeaderProgress();
    var station = state.stations[state.stationIndex];
    if (!station) return;
    if (station.type === "step") renderStepStation(stage, station);
    else if (station.type === "ordering") renderOrderingStation(stage);
    else if (station.type === "matching") renderMatchingStation(stage);
    else renderDoneStation(stage);
  }

  fetch("./lesson.interactive.json")
    .then(function (resp) { return resp.json(); })
    .then(function (data) {
      state.data = data;
      document.getElementById("lesson-title").textContent = data.title || "الدرس التفاعلي";
      document.getElementById("lesson-objective").textContent = data.objective || "";
      buildStations(data);
      render();
    })
    .catch(function (err) {
      var stage = document.getElementById("interactive-stage");
      stage.textContent = "تعذر تحميل ملف الدرس التفاعلي JSON: " + err;
    });
})();
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
