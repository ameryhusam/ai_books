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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LearnerProfile
import com.example.model.WorkspaceSummary
import com.example.ui.components.EduPanel
import com.example.ui.components.EduPill
import com.example.ui.components.EduYellowButton
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
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduWhite

private enum class StudentRosterFilter {
    ALL,
    UNASSIGNED,
    BY_CLASS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    workspaces: List<WorkspaceSummary>,
    activeWorkspaceId: String?,
    availableLearners: List<LearnerProfile>,
    activeLearner: LearnerProfile,
    onCreateClassroom: (gradeKey: String, title: String) -> Unit,
    onBindDirectFolder: () -> Unit,
    onOpenClassInLibrary: (workspaceId: String) -> Unit,
    onDeleteClassroom: (workspaceId: String) -> Unit,
    onAddLearner: (displayName: String, enrolledGradeKey: String?) -> Unit,
    onUpdateLearnerGrade: (userId: String, enrolledGradeKey: String?) -> Unit,
    onDeleteLearner: (userId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var classQuery by remember { mutableStateOf("") }
    var studentFilter by remember { mutableStateOf(StudentRosterFilter.ALL) }
    var filterGradeKey by remember { mutableStateOf("") }

    var showAddClassDialog by remember { mutableStateOf(false) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var assigningClassTarget by remember { mutableStateOf<WorkspaceSummary?>(null) }
    var pendingDeleteClass by remember { mutableStateOf<WorkspaceSummary?>(null) }

    val availableGrades = remember(workspaces) {
        workspaces.map { it.gradeKey.uppercase() }.distinct().sorted()
    }
    val availableGradeSet = remember(availableGrades) { availableGrades.toSet() }

    val filteredClasses = remember(workspaces, classQuery) {
        val q = classQuery.trim()
        if (q.isEmpty()) workspaces
        else workspaces.filter {
            it.gradeKey.contains(q, ignoreCase = true) ||
                it.title.contains(q, ignoreCase = true)
        }
    }

    val unassignedLearners = remember(availableLearners, availableGradeSet) {
        availableLearners.filter { student ->
            val g = student.enrolledGradeKey?.trim()?.uppercase().orEmpty()
            g.isBlank() || g !in availableGradeSet
        }
    }

    val filteredStudents = remember(availableLearners, studentFilter, filterGradeKey, availableGradeSet) {
        when (studentFilter) {
            StudentRosterFilter.ALL -> availableLearners
            StudentRosterFilter.UNASSIGNED -> availableLearners.filter { student ->
                val g = student.enrolledGradeKey?.trim()?.uppercase().orEmpty()
                g.isBlank() || g !in availableGradeSet
            }
            StudentRosterFilter.BY_CLASS -> availableLearners.filter { student ->
                student.enrolledGradeKey.equals(filterGradeKey, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 1040.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "admin_hero") {
                AdminHeroCard(
                    onOpenAddClass = { showAddClassDialog = true },
                    onBindDirectFolder = onBindDirectFolder
                )
            }

            item(key = "admin_kpi_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminStatCard(
                        value = workspaces.size.toString(),
                        label = "الصفوف المتاحة",
                        accent = EduGreenDark,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_kpi_classes")
                    )
                    AdminStatCard(
                        value = availableLearners.size.toString(),
                        label = "إجمالي الطلاب",
                        accent = EduPurple,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_kpi_students")
                    )
                    AdminStatCard(
                        value = unassignedLearners.size.toString(),
                        label = "بلا صف مسجّل",
                        accent = if (unassignedLearners.isNotEmpty()) EduOrange else EduGreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_kpi_unassigned")
                    )
                }
            }

            item(key = "classes_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الصفوف والفصول الدراسية (${workspaces.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk
                        )
                        Text(
                            text = "استعرض الصفوف المتاحة، أضف صفّاً جديداً، أو عيّن الطلاب مباشرة لكل صف.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted
                        )
                    }
                    OutlinedButton(
                        onClick = { showAddClassDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EduGreen.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_header_add_class_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "إضافة صف جديد",
                            tint = EduGreenDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "صف جديد",
                            style = MaterialTheme.typography.labelMedium,
                            color = EduGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (workspaces.size > 2) {
                item(key = "classes_search") {
                    OutlinedTextField(
                        value = classQuery,
                        onValueChange = { classQuery = it },
                        placeholder = {
                            Text("ابحث برمز الصف أو اسمه (مثل G04)...", style = MaterialTheme.typography.bodySmall)
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = EduMuted)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = EduWhite,
                            unfocusedContainerColor = EduWhite,
                            focusedBorderColor = EduGreen,
                            unfocusedBorderColor = EduLine
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_class_search_input")
                    )
                }
            }

            if (filteredClasses.isEmpty()) {
                item(key = "classes_empty") {
                    EduPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_classes_empty_state"),
                        borderColor = EduGreen.copy(alpha = 0.25f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = null,
                                tint = EduGreenDark,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = if (workspaces.isEmpty()) "لا توجد صفوف دراسية مضافة بعد" else "لا توجد نتائج مطابقة للبحث",
                                style = MaterialTheme.typography.titleSmall,
                                color = EduInk,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "أضف صفّاً جديداً يدوياً أو اربط مجلد مساحة العمل لاكتشاف الصفوف وربطها تلقائياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EduMuted,
                                textAlign = TextAlign.Center
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showAddClassDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("admin_empty_add_class_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إضافة صف")
                                }
                                OutlinedButton(
                                    onClick = onBindDirectFolder,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ربط مجلد")
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredClasses, key = { "class_${it.workspaceId}" }) { classroom ->
                    val roster = availableLearners.filter {
                        it.enrolledGradeKey.equals(classroom.gradeKey, ignoreCase = true)
                    }
                    ClassroomCard(
                        classroom = classroom,
                        isActive = classroom.workspaceId == activeWorkspaceId,
                        enrolledStudents = roster,
                        onOpenInLibrary = { onOpenClassInLibrary(classroom.workspaceId) },
                        onAssignStudents = { assigningClassTarget = classroom },
                        onRequestDelete = { pendingDeleteClass = classroom }
                    )
                }
            }

            item(key = "students_header") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "توزيع الطلاب على الصفوف (${availableLearners.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = EduInk
                        )
                        Text(
                            text = "كل طالب يرى محتوى الصف المخصّص له فقط عند تسجيل دخوله في وضع المتعلّم.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted
                        )
                    }
                    OutlinedButton(
                        onClick = { showAddStudentDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EduPurple.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_dashboard_add_student_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "إضافة طالب جديد",
                            tint = EduPurple,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "إضافة طالب",
                            style = MaterialTheme.typography.labelMedium,
                            color = EduPurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item(key = "student_filter_chips") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EduPill(
                        text = "الكل (${availableLearners.size})",
                        active = studentFilter == StudentRosterFilter.ALL,
                        onClick = { studentFilter = StudentRosterFilter.ALL },
                        testTag = "admin_filter_students_all"
                    )
                    EduPill(
                        text = "غير معيّنين (${unassignedLearners.size})",
                        active = studentFilter == StudentRosterFilter.UNASSIGNED,
                        onClick = { studentFilter = StudentRosterFilter.UNASSIGNED },
                        testTag = "admin_filter_students_unassigned"
                    )
                    availableGrades.forEach { grade ->
                        val count = availableLearners.count {
                            it.enrolledGradeKey.equals(grade, ignoreCase = true)
                        }
                        EduPill(
                            text = "$grade ($count)",
                            active = studentFilter == StudentRosterFilter.BY_CLASS &&
                                filterGradeKey.equals(grade, ignoreCase = true),
                            onClick = {
                                studentFilter = StudentRosterFilter.BY_CLASS
                                filterGradeKey = grade
                            },
                            testTag = "admin_filter_students_$grade"
                        )
                    }
                }
            }

            if (filteredStudents.isEmpty()) {
                item(key = "students_empty") {
                    EduPanel(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = when (studentFilter) {
                                StudentRosterFilter.UNASSIGNED -> "جميع الطلاب معيّنون لصفوفهم الدراسية."
                                StudentRosterFilter.BY_CLASS -> "لا يوجد طلاب مسجّلون في الصف $filterGradeKey بعد."
                                StudentRosterFilter.ALL -> "لا يوجد طلاب مضافون بعد."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = EduMuted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(filteredStudents, key = { "student_${it.userId}" }) { student ->
                    StudentAssignmentCard(
                        student = student,
                        isActive = student.userId == activeLearner.userId,
                        availableGrades = availableGrades,
                        availableGradeSet = availableGradeSet,
                        onAssignGrade = { grade -> onUpdateLearnerGrade(student.userId, grade) },
                        onDelete = { onDeleteLearner(student.userId) }
                    )
                }
            }
        }
    }

    if (showAddClassDialog) {
        AddClassroomDialog(
            existingGrades = availableGradeSet,
            onConfirm = { gradeKey, title ->
                onCreateClassroom(gradeKey, title)
                showAddClassDialog = false
            },
            onDismiss = { showAddClassDialog = false }
        )
    }

    if (showAddStudentDialog) {
        AddStudentToClassDialog(
            availableGrades = availableGrades,
            onConfirm = { name, gradeKey ->
                onAddLearner(name, gradeKey)
                showAddStudentDialog = false
            },
            onDismiss = { showAddStudentDialog = false }
        )
    }

    assigningClassTarget?.let { targetClass ->
        AssignStudentsToClassroomDialog(
            classroom = targetClass,
            students = availableLearners,
            onToggleStudent = { student, shouldAssign ->
                onUpdateLearnerGrade(
                    student.userId,
                    if (shouldAssign) targetClass.gradeKey else null
                )
            },
            onDismiss = { assigningClassTarget = null }
        )
    }

    pendingDeleteClass?.let { targetClass ->
        AlertDialog(
            onDismissRequest = { pendingDeleteClass = null },
            containerColor = EduWhite,
            title = {
                Text("حذف الصف ${targetClass.gradeKey}؟", style = MaterialTheme.typography.titleMedium, color = EduInk)
            },
            text = {
                Text(
                    text = "سيتم حذف «${targetClass.title}» وبياناته المرتبطة من الجهاز. يمكنك إعادة إضافته أو ربط مجلده لاحقاً.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EduInk
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = targetClass.workspaceId
                        pendingDeleteClass = null
                        onDeleteClassroom(id)
                    }
                ) {
                    Text("حذف الصف", color = EduRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteClass = null }) {
                    Text("إلغاء", color = EduMuted)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminHeroCard(
    onOpenAddClass: () -> Unit,
    onBindDirectFolder: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_dashboard_hero"),
        shape = RoundedCornerShape(22.dp),
        color = EduGreenDark
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_library_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.22f,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                EduPurple.copy(alpha = 0.72f),
                                EduGreenDark.copy(alpha = 0.90f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        tint = EduHeroKicker,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "EDU MINI • ADMIN CONTROL CENTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = EduHeroKicker
                    )
                }
                Text(
                    text = "لوحة تحكم مدير النظام",
                    style = MaterialTheme.typography.headlineMedium,
                    color = EduWhite
                )
                Text(
                    text = "استعرض جميع الصفوف المتاحة، أنشئ فصولاً جديدة، وعيّن كل طالب إلى صفّه الدراسي بدقة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EduHeroSub
                )
                Spacer(modifier = Modifier.height(2.dp))
                EduYellowButton(
                    text = "إضافة صف دراسي جديد",
                    onClick = onOpenAddClass,
                    icon = Icons.Rounded.Add,
                    testTag = "admin_add_class_button"
                )
                OutlinedButton(
                    onClick = onBindDirectFolder,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EduWhite.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("admin_bind_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "ربط مجلد مساحة العمل",
                        tint = EduWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ربط أو استيراد صفوف من مجلد مساحة العمل",
                        style = MaterialTheme.typography.labelMedium,
                        color = EduWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminStatCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    EduPanel(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = accent,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = EduMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassroomCard(
    classroom: WorkspaceSummary,
    isActive: Boolean,
    enrolledStudents: List<LearnerProfile>,
    onOpenInLibrary: () -> Unit,
    onAssignStudents: () -> Unit,
    onRequestDelete: () -> Unit
) {
    EduPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_class_card_${classroom.gradeKey}"),
        borderColor = if (isActive) EduGreen else EduLine,
        containerColor = if (isActive) EduMint.copy(alpha = 0.25f) else EduWhite
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isActive) EduGreen else EduMint
                ) {
                    Text(
                        text = classroom.gradeKey,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) EduWhite else EduGreenDark,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = classroom.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = EduInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Surface(shape = RoundedCornerShape(6.dp), color = EduMint) {
                                Text(
                                    text = "محدد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EduGreenDark,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = buildString {
                            append("${enrolledStudents.size} طالب مسجّل")
                            append(" • ")
                            append(
                                if (classroom.filesCount > 0) "${classroom.filesCount} ملف مفهرس"
                                else "بدون مجلد منهج مربوط بعد"
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = EduMuted
                    )
                }
            }
            IconButton(
                onClick = onRequestDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_delete_class_${classroom.gradeKey}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "حذف الصف ${classroom.gradeKey}",
                    tint = EduRed,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        if (enrolledStudents.isNotEmpty()) {
            val previewNames = enrolledStudents.take(4).joinToString("، ") { it.displayName }
            val extra = if (enrolledStudents.size > 4) " (+${enrolledStudents.size - 4})" else ""
            Text(
                text = "الطلاب: $previewNames$extra",
                style = MaterialTheme.typography.bodySmall,
                color = EduInk
            )
        } else {
            Text(
                text = "لم يُعيَّن أي طالب لهذا الصف بعد.",
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onAssignStudents,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduPurple.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_assign_class_students_${classroom.gradeKey}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.GroupAdd,
                    contentDescription = null,
                    tint = EduPurple,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "تعيين طلاب (${enrolledStudents.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = EduPurple,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onOpenInLibrary,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EduGreen.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_open_class_library_${classroom.gradeKey}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryBooks,
                    contentDescription = null,
                    tint = EduGreenDark,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "فتح المكتبة",
                    style = MaterialTheme.typography.labelMedium,
                    color = EduGreenDark,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = EduGreenDark,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentAssignmentCard(
    student: LearnerProfile,
    isActive: Boolean,
    availableGrades: List<String>,
    availableGradeSet: Set<String>,
    onAssignGrade: (String?) -> Unit,
    onDelete: () -> Unit
) {
    val currentGrade = student.enrolledGradeKey?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
    val isMissingClass = currentGrade != null && currentGrade !in availableGradeSet

    EduPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_dashboard_student_card_${student.userId}"),
        borderColor = when {
            currentGrade == null || isMissingClass -> EduOrange.copy(alpha = 0.55f)
            else -> EduLine
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EduLilac,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = EduPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = student.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = EduInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Surface(shape = RoundedCornerShape(6.dp), color = EduMint) {
                                Text(
                                    text = "النشط",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EduGreenDark,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (currentGrade == null || isMissingClass) {
                                Icons.Rounded.WarningAmber
                            } else {
                                Icons.Rounded.CheckCircle
                            },
                            contentDescription = null,
                            tint = if (currentGrade == null || isMissingClass) EduOrange else EduGreenDark,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = when {
                                currentGrade == null -> "غير معيّن لصف دراسي"
                                isMissingClass -> "الصف $currentGrade غير مضاف حالياً"
                                else -> "الصف المسجّل: $currentGrade"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (currentGrade == null || isMissingClass) EduOrange else EduGreenDark
                        )
                    }
                }
            }
            if (student.userId != LearnerProfile.DEFAULT_USER_ID) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("admin_dashboard_delete_student_${student.userId}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "حذف الطالب ${student.displayName}",
                        tint = EduRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        val gradeChoices = (availableGrades + listOfNotNull(currentGrade)).distinct().sorted()
        if (gradeChoices.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                gradeChoices.forEach { grade ->
                    val assigned = currentGrade.equals(grade, ignoreCase = true)
                    EduPill(
                        text = "الصف $grade",
                        active = assigned,
                        onClick = { onAssignGrade(if (assigned) null else grade) },
                        testTag = "admin_dashboard_assign_${student.userId}_$grade"
                    )
                }
            }
        } else {
            Text(
                text = "أضف صفّاً دراسياً أولاً لتعيين الطالب إليه.",
                style = MaterialTheme.typography.bodySmall,
                color = EduMuted
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddClassroomDialog(
    existingGrades: Set<String>,
    onConfirm: (gradeKey: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val suggested = listOf("G01", "G02", "G03", "G04", "G05", "G06", "G07", "G08", "G09")
        .filter { it !in existingGrades }
    var gradeKey by remember { mutableStateOf(suggested.firstOrNull().orEmpty()) }
    var title by remember { mutableStateOf("") }
    val normalized = gradeKey.trim().uppercase()
    val duplicateError = normalized.isNotEmpty() && normalized in existingGrades

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text("إضافة صف دراسي جديد", style = MaterialTheme.typography.titleMedium, color = EduInk)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (suggested.isNotEmpty()) {
                    Text("اختيار سريع لرمز الصف:", style = MaterialTheme.typography.labelSmall, color = EduMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggested.take(6).forEach { code ->
                            EduPill(
                                text = code,
                                active = normalized == code,
                                onClick = {
                                    gradeKey = code
                                    if (title.isBlank()) title = "الصف $code"
                                },
                                testTag = "admin_quick_grade_$code"
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = gradeKey,
                    onValueChange = { gradeKey = it },
                    label = { Text("رمز الصف / الفصل (مثل G05 أو G04-A)") },
                    singleLine = true,
                    isError = duplicateError,
                    supportingText = if (duplicateError) {
                        { Text("هذا الرمز مستخدم مسبقاً", color = EduRed) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = EduFieldBg,
                        unfocusedContainerColor = EduFieldBg,
                        focusedBorderColor = EduGreen,
                        unfocusedBorderColor = EduLine
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_add_class_grade_input")
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم الصف المعروض (اختياري)") },
                    placeholder = { Text("مثال: الصف الخامس الأساسي") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = EduFieldBg,
                        unfocusedContainerColor = EduFieldBg,
                        focusedBorderColor = EduGreen,
                        unfocusedBorderColor = EduLine
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_add_class_title_input")
                )
            }
        },
        confirmButton = {
            Button(
                enabled = normalized.isNotEmpty() && !duplicateError,
                onClick = { onConfirm(normalized, title.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = EduGreen, contentColor = EduWhite),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_confirm_add_class_button")
            ) {
                Text("إنشاء الصف")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = EduMuted)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddStudentToClassDialog(
    availableGrades: List<String>,
    onConfirm: (displayName: String, enrolledGradeKey: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedGrade by remember { mutableStateOf(availableGrades.firstOrNull().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text("إضافة طالب جديد", style = MaterialTheme.typography.titleMedium, color = EduInk)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الطالب") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = EduFieldBg,
                        unfocusedContainerColor = EduFieldBg,
                        focusedBorderColor = EduPurple,
                        unfocusedBorderColor = EduLine
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_add_student_name_input")
                )
                if (availableGrades.isNotEmpty()) {
                    Text("تعيين الصف الدراسي:", style = MaterialTheme.typography.labelSmall, color = EduMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableGrades.forEach { grade ->
                            EduPill(
                                text = grade,
                                active = selectedGrade.equals(grade, ignoreCase = true),
                                onClick = {
                                    selectedGrade = if (selectedGrade.equals(grade, ignoreCase = true)) "" else grade
                                },
                                testTag = "admin_new_student_grade_$grade"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.trim().isNotEmpty(),
                onClick = { onConfirm(name.trim(), selectedGrade.trim().takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(containerColor = EduPurple, contentColor = EduWhite),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_confirm_new_student_button")
            ) {
                Text("حفظ الطالب")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = EduMuted)
            }
        }
    )
}

@Composable
private fun AssignStudentsToClassroomDialog(
    classroom: WorkspaceSummary,
    students: List<LearnerProfile>,
    onToggleStudent: (student: LearnerProfile, assignToClass: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EduWhite,
        title = {
            Text(
                text = "تعيين طلاب للصف ${classroom.gradeKey}",
                style = MaterialTheme.typography.titleMedium,
                color = EduInk
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = classroom.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
                if (students.isEmpty()) {
                    Text("لا يوجد طلاب مضافون بعد.", style = MaterialTheme.typography.bodyMedium, color = EduInk)
                } else {
                    students.forEach { student ->
                        val assignedHere = student.enrolledGradeKey.equals(classroom.gradeKey, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (assignedHere) EduMint.copy(alpha = 0.45f) else EduFieldBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .minimumInteractiveComponentSize()
                                .clickable { onToggleStudent(student, !assignedHere) }
                                .testTag("admin_dialog_toggle_student_${student.userId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = student.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EduInk,
                                        fontWeight = if (assignedHere) FontWeight.Bold else FontWeight.Normal
                                    )
                                    val otherGrade = student.enrolledGradeKey?.takeIf {
                                        it.isNotBlank() && !it.equals(classroom.gradeKey, ignoreCase = true)
                                    }
                                    if (otherGrade != null) {
                                        Text(
                                            text = "مسجّل حالياً في $otherGrade (سيتم نقله)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EduOrange
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = assignedHere,
                                    onCheckedChange = { checked -> onToggleStudent(student, checked) },
                                    colors = CheckboxDefaults.colors(checkedColor = EduGreen)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_dialog_assign_done_button")
            ) {
                Text("تم", color = EduGreenDark, fontWeight = FontWeight.Bold)
            }
        }
    )
}
