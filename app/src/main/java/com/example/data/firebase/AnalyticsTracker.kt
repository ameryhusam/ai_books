package com.example.data.firebase

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class AnalyticsTracker(context: Context) {
    private val firebaseAnalytics = runCatching { FirebaseAnalytics.getInstance(context) }.getOrNull()

    fun trackLessonOpened(bookKey: String, unitKey: String, lessonKey: String, lessonTitle: String) {
        val params = Bundle().apply {
            putString("book_key", bookKey)
            putString("unit_key", unitKey)
            putString("lesson_key", lessonKey)
            putString("lesson_title", lessonTitle)
        }
        firebaseAnalytics?.logEvent("lesson_opened", params)
    }

    fun trackLessonCreateCompleted(bookKey: String, unitKey: String, lessonKey: String, success: Boolean) {
        val params = Bundle().apply {
            putString("book_key", bookKey)
            putString("unit_key", unitKey)
            putString("lesson_key", lessonKey)
            putString("success", success.toString())
        }
        firebaseAnalytics?.logEvent("lesson_create_completed", params)
    }

    fun trackAiExplanationGenerated(providerId: String, taskType: String, success: Boolean) {
        val params = Bundle().apply {
            putString("provider_id", providerId)
            putString("task_type", taskType)
            putString("success", success.toString())
        }
        firebaseAnalytics?.logEvent("ai_explanation_generated", params)
    }

    fun trackQuizAttemptSubmitted(lessonKey: String, score: Int, passed: Boolean) {
        val params = Bundle().apply {
            putString("lesson_key", lessonKey)
            putInt("score", score)
            putString("passed", passed.toString())
        }
        firebaseAnalytics?.logEvent("quiz_attempt_submitted", params)
    }
}
