package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ImportedWorkspace
import com.example.model.LearnerProfile
import com.example.model.UserRole
import com.example.model.WorkspaceSummary
import com.example.ui.components.EduPanel
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduFieldBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduHeroKicker
import com.example.ui.theme.EduHeroSub
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduOrange
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduWhite
import com.example.ui.theme.EduYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleEntryScreen(
    workspace: ImportedWorkspace?,
    workspaces: List<WorkspaceSummary> = emptyList(),
    availableLearners: List<LearnerProfile> = listOf(LearnerProfile.DEFAULT),
    activeLearner: LearnerProfile = LearnerProfile.DEFAULT,
    isBusy: Boolean,
    onSelectLearner: (String) -> Unit = {},
    onSelectRole: (UserRole) -> Unit,
    onBindDirectFolder: () -> Unit,
    onOpenGoogleAuth: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val distinctGrades = workspaces.map { it.gradeKey.uppercase() }.distinct().sorted()
    val hasAnyWorkspace = workspaces.isNotEmpty() || workspace != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EduBg)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "entry_hero") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = EduGreenDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_dashboard_banner),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.22f,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            EduGreenDark.copy(alpha = 0.94f),
                                            EduGreen.copy(alpha = 0.85f),
                                            EduPurple.copy(alpha = 0.78f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "EDU MINI • بوابة الدخول",
                                style = MaterialTheme.typography.labelSmall,
                                color = EduHeroKicker
                            )
                            Text(
                                text = "اختر مسار الدخول إلى النظام",
                                style = MaterialTheme.typography.headlineMedium,
                                color = EduWhite
                            )
                            Text(
                                text = "يضبط المدير صفوفاً متعددة ويعيّن لكل طالب صفّه الدراسي، فيظهر للطالب صفّه المسجّل فقط.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduHeroSub
                            )
                        }
                    }
                }
            }

            item(key = "workspace_status_card") {
                EduPanel(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (hasAnyWorkspace) EduMint.copy(alpha = 0.55f) else EduWhite,
                    borderColor = if (hasAnyWorkspace) EduGreen else EduLine
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (hasAnyWorkspace) EduGreen else EduLilac,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (hasAnyWorkspace) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = if (hasAnyWorkspace) EduWhite else EduPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    distinctGrades.size > 1 -> "الصفوف المهيأة (${distinctGrades.size}): ${distinctGrades.joinToString(" • ")}"
                                    workspace != null -> "الصف المرتبط: ${workspace.gradeKey} • ${workspace.title}"
                                    workspaces.isNotEmpty() -> "الصف المرتبط: ${workspaces.first().gradeKey}"
                                    else -> "لم يتم ربط مجلد الصف الدراسي بعد"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk
                            )
                            Text(
                                text = when {
                                    distinctGrades.size > 1 -> "يمكن لكل طالب الدخول إلى صفّه المسجّل فقط، بينما يدير النظام كل الصفوف."
                                    workspace != null -> "${workspace.books.size} مادة • ${workspace.lessons.size} درس • قراءة مباشرة بدون نسخ أو أذونات"
                                    workspaces.isNotEmpty() -> "تم ضبط مساحة العمل محلياً بدون نسخ للملفات."
                                    else -> "اربط مجلد مساحة العمل المحلية مباشرة من الذاكرة (SAF بدون أذونات تخزين أو نسخ للملفات)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onBindDirectFolder,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EduGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("entry_bind_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = EduGreenDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasAnyWorkspace) "إضافة أو تحديث مجلد الصفوف الدراسية" else "ربط مجلد مساحة العمل محلياً (بدون نسخ)",
                            color = EduGreenDark,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item(key = "role_learner_card") {
                val enrolledGrade = activeLearner.enrolledGradeKey?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                val effectiveGradeNotice = when {
                    enrolledGrade != null && distinctGrades.any { it.equals(enrolledGrade, ignoreCase = true) } ->
                        "الصف المسجّل للطالب «${activeLearner.displayName}»: $enrolledGrade (سيظهر له هذا الصف فقط)"
                    enrolledGrade != null ->
                        "الطالب «${activeLearner.displayName}» مسجّل في الصف $enrolledGrade (لم يُربط مجلده بعد من حساب المدير)"
                    distinctGrades.size == 1 ->
                        "الطالب «${activeLearner.displayName}» سيدخل إلى الصف المتاح: ${distinctGrades.first()}"
                    distinctGrades.size > 1 ->
                        "توجد عدة صفوف مهيأة؛ لم يُعيَّن صف للطالب «${activeLearner.displayName}» بعد — اضبطه من حساب المدير."
                    else ->
                        "اربط صفّاً دراسياً أولاً ثم ادخل كمتعلّم."
                }
                RoleChoiceCard(
                    title = "الدخول كمتعلّم",
                    badgeText = "مسار الطالب",
                    description = "عرض المواد والوحدات ومسار الدروس المنجزة الخاصة بصف الطالب المسجّل فقط، مع PDF والتفاعلي واختبارات الإتقان.",
                    ctaLabel = if (enrolledGrade != null) "دخول الطالب (${activeLearner.displayName} • $enrolledGrade)" else "دخول المتعلّم (${activeLearner.displayName})",
                    icon = Icons.Rounded.School,
                    accentColor = EduGreen,
                    containerTint = EduWhite,
                    buttonContainer = EduYellow,
                    buttonContent = EduInk,
                    testTag = "enter_as_learner_button",
                    extraContent = {
                        if (availableLearners.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "اختر حساب الطالب:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EduInk,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    availableLearners.forEach { profile ->
                                        val selected = profile.userId == activeLearner.userId
                                        val gradeLabel = profile.enrolledGradeKey?.takeIf { it.isNotBlank() } ?: "غير مسجّل"
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (selected) EduGreen else EduFieldBg,
                                            border = BorderStroke(1.dp, if (selected) EduGreenDark else EduLine),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .minimumInteractiveComponentSize()
                                                .clickable { onSelectLearner(profile.userId) }
                                                .testTag("entry_learner_chip_${profile.userId}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Person,
                                                    contentDescription = null,
                                                    tint = if (selected) EduWhite else EduMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "${profile.displayName} • $gradeLabel",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (selected) EduWhite else EduInk,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (enrolledGrade == null && distinctGrades.size > 1) EduOrange.copy(alpha = 0.14f) else EduMint.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = effectiveGradeNotice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduInk,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    },
                    onClick = { onSelectRole(UserRole.LEARNER) }
                )
            }

            item(key = "role_admin_card") {
                RoleChoiceCard(
                    title = "الدخول كمدير / معلم",
                    badgeText = "الإدارة والتوليد",
                    description = "ربط وضبط أكثر من صف دراسي، تسجيل كل طالب في صفّه، استوديو توليد الشروح وبنوك الأسئلة والدروس التفاعلية، وإدارة مزودي AI.",
                    ctaLabel = "دخول المدير",
                    icon = Icons.Rounded.AdminPanelSettings,
                    accentColor = EduPurple,
                    containerTint = EduWhite,
                    buttonContainer = EduPurple,
                    buttonContent = EduWhite,
                    testTag = "enter_as_admin_button",
                    onClick = { onSelectRole(UserRole.ADMIN) }
                )
            }

            if (onOpenGoogleAuth != null) {
                item(key = "return_to_google_auth") {
                    OutlinedButton(
                        onClick = onOpenGoogleAuth,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, EduGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("entry_google_sign_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Login,
                            contentDescription = null,
                            tint = EduGreenDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تسجيل الدخول بحساب Google للتحقق من الدور",
                            color = EduGreenDark,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleChoiceCard(
    title: String,
    badgeText: String,
    description: String,
    ctaLabel: String,
    icon: ImageVector,
    accentColor: Color,
    containerTint: Color,
    buttonContainer: Color,
    buttonContent: Color,
    testTag: String,
    extraContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerTint,
        border = BorderStroke(1.3.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = EduInk,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = EduMuted
            )

            extraContent?.invoke()

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainer,
                    contentColor = buttonContent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag(testTag)
            ) {
                Text(
                    text = ctaLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
