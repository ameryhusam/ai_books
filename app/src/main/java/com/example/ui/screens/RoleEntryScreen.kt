package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.model.UserRole
import com.example.ui.components.EduPanel
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduHeroKicker
import com.example.ui.theme.EduHeroSub
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLilac
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduPurple
import com.example.ui.theme.EduWhite
import com.example.ui.theme.EduYellow

@Composable
fun RoleEntryScreen(
    workspace: ImportedWorkspace?,
    isBusy: Boolean,
    onSelectRole: (UserRole) -> Unit,
    onBindDirectFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                                text = "يفصل النظام بين تجربة المتعلّم الخالصة ومسار المدير المسؤول عن ربط الملفات وتوليد الأسئلة والمحتوى.",
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
                    containerColor = if (workspace != null) EduMint.copy(alpha = 0.55f) else EduWhite,
                    borderColor = if (workspace != null) EduGreen else EduLine
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (workspace != null) EduGreen else EduLilac,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (workspace != null) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = if (workspace != null) EduWhite else EduPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (workspace != null) {
                                    "الصف المرتبط: ${workspace.gradeKey} • ${workspace.title}"
                                } else {
                                    "لم يتم ربط مجلد الصف الدراسي بعد"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk
                            )
                            Text(
                                text = if (workspace != null) {
                                    "${workspace.books.size} مادة • ${workspace.lessons.size} درس • قراءة مباشرة بدون نسخ أو أذونات"
                                } else {
                                    "اربط مجلد مساحة العمل المحلية مباشرة من الذاكرة (SAF بدون أذونات تخزين أو نسخ للملفات)."
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
                            text = if (workspace != null) "تغيير مجلد مساحة العمل أو الصف" else "ربط مجلد مساحة العمل محلياً (بدون نسخ)",
                            color = EduGreenDark,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item(key = "role_learner_card") {
                RoleChoiceCard(
                    title = "الدخول كمتعلّم",
                    badgeText = "مسار الطالب",
                    description = "عرض المواد والوحدات ومسار الدروس المنجزة، دراسة الدرس عبر PDF مع التبديل الفوري للتفاعلي، اجتياز اختبار نهاية الدرس لاحتساب النقاط والإكمال، وتتبع المفاهيم التي تحتاج تقوية.",
                    ctaLabel = "دخول المتعلّم",
                    icon = Icons.Rounded.School,
                    accentColor = EduGreen,
                    containerTint = EduWhite,
                    buttonContainer = EduYellow,
                    buttonContent = EduInk,
                    testTag = "enter_as_learner_button",
                    onClick = { onSelectRole(UserRole.LEARNER) }
                )
            }

            item(key = "role_admin_card") {
                RoleChoiceCard(
                    title = "الدخول كمدير / معلم",
                    badgeText = "الإدارة والتوليد",
                    description = "ربط مساحة عمل الصف محلياً، استوديو توليد الشروح وبنوك الأسئلة والدروس التفاعلية، إدارة الحزم التفاعلية، ومفاتيح وإعدادات مزودي AI.",
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
