package com.example.data.firebase

import android.content.Context
import android.os.Bundle
import com.example.EduMiniApplication
import com.google.firebase.analytics.FirebaseAnalytics

class AnalyticsTracker(context: Context) {
    private val appContext = context.applicationContext
    private val firebaseAnalytics: FirebaseAnalytics? = if (EduMiniApplication.ensureFirebaseInitialized(appContext)) {
        runCatching { FirebaseAnalytics.getInstance(appContext) }.getOrNull()
    } else {
        null
    }

    private fun safeParam(value: String): String = value.trim().take(MAX_PARAM_CHARS)

    fun trackLessonOpened(bookKey: String, unitKey: String, lessonKey: String, lessonTitle: String) {
        val params = Bundle().apply {
            putString("book_key", safeParam(bookKey))
            putString("unit_key", safeParam(unitKey))
            putString("lesson_key", safeParam(lessonKey))
            putString("lesson_title", safeParam(lessonTitle))
        }
        runCatching { firebaseAnalytics?.logEvent("lesson_opened", params) }
    }

    fun trackLessonCreateCompleted(bookKey: String, unitKey: String, lessonKey: String, success: Boolean) {
        val params = Bundle().apply {
            putString("book_key", safeParam(bookKey))
            putString("unit_key", safeParam(unitKey))
            putString("lesson_key", safeParam(lessonKey))
            putString("success", success.toString())
        }
        runCatching { firebaseAnalytics?.logEvent("lesson_create_completed", params) }
    }

    fun trackAiExplanationGenerated(providerId: String, taskType: String, success: Boolean) {
        val params = Bundle().apply {
            putString("provider_id", safeParam(providerId))
            putString("task_type", safeParam(taskType))
            putString("success", success.toString())
        }
        runCatching { firebaseAnalytics?.logEvent("ai_explanation_generated", params) }
    }

    fun trackQuizAttemptSubmitted(lessonKey: String, score: Int, passed: Boolean) {
        val params = Bundle().apply {
            putString("lesson_key", safeParam(lessonKey))
            putInt("score", score.coerceIn(0, 100))
            putString("passed", passed.toString())
        }
        runCatching { firebaseAnalytics?.logEvent("quiz_attempt_submitted", params) }
    }

    companion object {
        private const val MAX_PARAM_CHARS = 100
    }
}
