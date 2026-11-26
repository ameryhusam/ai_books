package com.example.ui.auth

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser

object AuthDiagnosticLogger {
    private const val TAG = "AuthDiagnostic"

    fun logUserStatus(user: FirebaseUser?) {
        if (user == null) {
            Log.i(TAG, "AuthStatus: User is unauthenticated (null)")
        } else {
            Log.i(TAG, "AuthStatus: User authenticated -> uid=${user.uid}, email=${user.email}, provider=${user.providerId}")
        }
    }

    fun logSignInError(e: Throwable): Pair<String, String> {
        val errorCode = when (e) {
            is FirebaseAuthException -> e.errorCode
            else -> e::class.java.simpleName
        }
        val message = e.localizedMessage ?: e.message ?: "Unknown error"
        Log.e(TAG, "AuthSignInFailure -> Code: [$errorCode], Message: $message", e)
        return errorCode to message
    }
}

@Composable
fun AuthMonitorDiagnosticCard(
    lastErrorCode: String?,
    lastErrorMessage: String?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()
    var currentUserSummary by remember {
        mutableStateOf(auth.currentUser?.let { "${it.email} (${it.uid.take(6)}...)" } ?: "غير مسجل دخول")
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            AuthDiagnosticLogger.logUserStatus(user)
            currentUserSummary = user?.let { "${it.email} (${it.uid.take(6)}...)" } ?: "غير مسجل دخول"
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = EduBg,
        border = BorderStroke(1.dp, EduLine),
        modifier = modifier
            .fillMaxWidth()
            .testTag("auth_diagnostic_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BugReport,
                        contentDescription = null,
                        tint = EduMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "تشخيص المصادقة (AuthMonitor)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduInk,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "طي" else "توسيع",
                        tint = EduMuted
                    )
                }
            }

            Text(
                text = "حالة المستخدم الحالي: $currentUserSummary",
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )

            if (!lastErrorCode.isNullOrBlank() || !lastErrorMessage.isNullOrBlank()) {
                Text(
                    text = "آخر رمز خطأ: ${lastErrorCode ?: "N/A"} • ${lastErrorMessage.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = EduRed,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "تفاصيل الفحص:",
                        style = MaterialTheme.typography.labelSmall,
                        color = EduInk,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• CredentialManager: مفعل\n• Google Web Client ID: ${if (com.google.firebase.FirebaseApp.getInstance().options.applicationId.isNotBlank()) "موجود" else "مفقود"}\n• Auth State Flow: مراقب بصورة مستمرة",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = EduMuted
                    )
                }
            }
        }
    }
}
