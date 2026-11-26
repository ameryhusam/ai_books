package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.FirebaseFirestore

class EduMiniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (ensureFirebaseInitialized(this)) {
            runCatching { FirebaseAnalytics.getInstance(this) }
                .onFailure { Log.w(TAG, "FirebaseAnalytics warm-up skipped: ${it.message}") }
            runCatching {
                val dbId = getString(R.string.firestore_database_id)
                if (dbId.isNotBlank()) {
                    FirebaseFirestore.getInstance(dbId)
                }
            }.onFailure { Log.w(TAG, "FirebaseFirestore warm-up skipped: ${it.message}") }
        } else {
            Log.i(TAG, "Firebase default app is not configured in this build; running in local-first mode.")
        }
    }

    companion object {
        private const val TAG = "EduMiniApplication"

        fun ensureFirebaseInitialized(context: Context): Boolean {
            val appContext = context.applicationContext
            if (FirebaseApp.getApps(appContext).isNotEmpty()) return true
            return runCatching { FirebaseApp.initializeApp(appContext) != null }
                .getOrDefault(false)
        }
    }
}
