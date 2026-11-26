package com.example

import android.app.Application
import com.google.firebase.FirebaseApp

class EduMiniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            FirebaseApp.initializeApp(this)
        }
    }
}
