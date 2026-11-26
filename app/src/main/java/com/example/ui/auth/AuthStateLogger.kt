package com.example.ui.auth

import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException

object AuthStateLogger {
    private const val TAG = "AuthStateLogger"

    fun logCredentialException(e: Throwable, contextMessage: String = "Google Sign-In") {
        when (e) {
            is GetCredentialCancellationException -> {
                Log.w(TAG, "[$contextMessage] User cancelled or dismissed the credential selection flow: ${e.message}", e)
            }
            is NoCredentialException -> {
                Log.w(TAG, "[$contextMessage] No saved credentials or Google accounts found on device: ${e.message}", e)
            }
            is GetCredentialException -> {
                Log.e(TAG, "[$contextMessage] Credential Manager exception: type=${e.type}, message=${e.message}", e)
            }
            else -> {
                Log.e(TAG, "[$contextMessage] Unexpected authentication failure: ${e.localizedMessage ?: e.message}", e)
            }
        }
    }

    fun analyzeAndLogSignInError(e: Throwable): Pair<String, String> {
        logCredentialException(e)
        val code = when (e) {
            is GetCredentialCancellationException -> "CREDENTIAL_CANCELLED"
            is NoCredentialException -> "NO_CREDENTIAL_FOUND"
            is GetCredentialCustomException -> "CREDENTIAL_CUSTOM_ERROR"
            is GetCredentialException -> e.type
            else -> e::class.java.simpleName
        }
        val message = when (e) {
            is GetCredentialCancellationException -> "تم إلغاء أو إغلاق نافذة اختيار حساب Google."
            is NoCredentialException -> "لا توجد حسابات Google محفوظة على هذا الجهاز أو لم يتم منح الأذونات."
            else -> e.localizedMessage ?: e.message ?: "خطأ غير معروف في المصادقة."
        }
        return code to message
    }
}
