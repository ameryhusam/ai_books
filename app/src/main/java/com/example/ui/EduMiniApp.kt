package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.AppPage
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.EduBg
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduGreenDark
import com.example.ui.theme.EduInk
import com.example.ui.theme.EduLine
import com.example.ui.theme.EduMiniTheme
import com.example.ui.theme.EduMint
import com.example.ui.theme.EduMuted
import com.example.ui.theme.EduWhite

@Composable
fun EduMiniApp(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lessonDrafts by viewModel.lessonDrafts.collectAsStateWithLifecycle()

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importWorkspaceZip(uri)
        }
    }

    EduMiniTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 720.dp

                Scaffold(
                    containerColor = EduBg,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        EduTopBar(
                            onOpenSettings = { viewModel.navigateTo(AppPage.SETTINGS) }
                        )
                    },
                    bottomBar = {
                        if (!isWide) {
                            EduBottomNavigationBar(
                                currentPage = state.page,
                                onSelectPage = viewModel::navigateTo
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWide) {
                            EduNavigationRail(
                                currentPage = state.page,
                                onSelectPage = viewModel::navigateTo
                            )
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            AnimatedVisibility(visible = state.isBusy) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = EduGreen,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.busy_working),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EduMuted
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when (state.page) {
                                    AppPage.LIBRARY -> LibraryScreen(
                                        workspace = state.workspace,
                                        selectedBookKey = state.selectedBookKey,
                                        selectedLesson = state.selectedLesson,
                                        activeProvidersCount = state.providers.count { it.enabled },
                                        onPickZip = {
                                            zipPickerLauncher.launch(
                                                arrayOf(
                                                    "application/zip",
                                                    "application/x-zip-compressed",
                                                    "application/octet-stream"
                                                )
                                            )
                                        },
                                        onSelectBook = viewModel::selectBook,
                                        onOpenLesson = viewModel::selectLessonAndOpenStudio
                                    )

                                    AppPage.STUDIO -> StudioScreen(
                                        selectedLesson = state.selectedLesson,
                                        task = state.task,
                                        sourceText = state.sourceText,
                                        extraInstructions = state.extraInstructions,
                                        generatedResult = state.generatedResult,
                                        lessonDrafts = lessonDrafts,
                                        isBusy = state.isBusy,
                                        onBackToLibrary = { viewModel.navigateTo(AppPage.LIBRARY) },
                                        onSelectTask = viewModel::selectTask,
                                        onSourceChange = viewModel::updateSourceText,
                                        onExtraChange = viewModel::updateExtraInstructions,
                                        onGenerate = viewModel::generateContent,
                                        onLoadDraft = viewModel::loadDraftIntoEditor,
                                        onDeleteDraft = viewModel::deleteDraft
                                    )

                                    AppPage.SETTINGS -> SettingsScreen(
                                        form = state.providerForm,
                                        providers = state.providers,
                                        onBackToLibrary = { viewModel.navigateTo(AppPage.LIBRARY) },
                                        onSelectProviderType = viewModel::updateProviderType,
                                        onLabelChange = viewModel::updateFormLabel,
                                        onModelChange = viewModel::updateFormModel,
                                        onBaseUrlChange = viewModel::updateFormBaseUrl,
                                        onApiKeyChange = viewModel::updateFormApiKey,
                                        onAddProvider = viewModel::addProvider,
                                        onToggleProvider = viewModel::toggleProvider,
                                        onDeleteProvider = viewModel::deleteProvider
                                    )
                                }
                            }
                        }
                    }
                }

                state.alert?.let { alert ->
                    AlertDialog(
                        onDismissRequest = viewModel::dismissAlert,
                        title = {
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = EduInk
                            )
                        },
                        text = {
                            Text(
                                text = alert.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = EduInk
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = viewModel::dismissAlert) {
                                Text(
                                    text = stringResource(R.string.alert_ok),
                                    color = EduGreenDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        containerColor = EduWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun EduTopBar(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EduBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(13.dp),
                color = EduGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.headlineMedium,
                        color = EduWhite
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = EduInk
                )
                Text(
                    text = stringResource(R.string.brand_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = EduMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EduWhite
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.cd_settings_button),
                        tint = EduInk
                    )
                }
            }
        }
    }
}

@Composable
private fun EduBottomNavigationBar(
    currentPage: AppPage,
    onSelectPage: (AppPage) -> Unit
) {
    Column {
        HorizontalDivider(color = EduLine, thickness = 1.dp)
        NavigationBar(
            containerColor = EduWhite,
            windowInsets = WindowInsets.navigationBars,
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                selected = currentPage == AppPage.LIBRARY,
                onClick = { onSelectPage(AppPage.LIBRARY) },
                icon = {
                    Icon(
                        imageVector = if (currentPage == AppPage.LIBRARY) {
                            Icons.Rounded.LibraryBooks
                        } else {
                            Icons.Outlined.LibraryBooks
                        },
                        contentDescription = stringResource(R.string.nav_library)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_library),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EduGreenDark,
                    selectedTextColor = EduGreenDark,
                    indicatorColor = EduMint,
                    unselectedIconColor = EduMuted,
                    unselectedTextColor = EduMuted
                ),
                modifier = Modifier.testTag("nav_library")
            )
            NavigationBarItem(
                selected = currentPage == AppPage.STUDIO,
                onClick = { onSelectPage(AppPage.STUDIO) },
                icon = {
                    Icon(
                        imageVector = if (currentPage == AppPage.STUDIO) {
                            Icons.Rounded.AutoAwesome
                        } else {
                            Icons.Outlined.AutoAwesome
                        },
                        contentDescription = stringResource(R.string.nav_studio)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_studio),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EduGreenDark,
                    selectedTextColor = EduGreenDark,
                    indicatorColor = EduMint,
                    unselectedIconColor = EduMuted,
                    unselectedTextColor = EduMuted
                ),
                modifier = Modifier.testTag("nav_studio")
            )
            NavigationBarItem(
                selected = currentPage == AppPage.SETTINGS,
                onClick = { onSelectPage(AppPage.SETTINGS) },
                icon = {
                    Icon(
                        imageVector = if (currentPage == AppPage.SETTINGS) {
                            Icons.Rounded.Settings
                        } else {
                            Icons.Outlined.Settings
                        },
                        contentDescription = stringResource(R.string.nav_settings)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EduGreenDark,
                    selectedTextColor = EduGreenDark,
                    indicatorColor = EduMint,
                    unselectedIconColor = EduMuted,
                    unselectedTextColor = EduMuted
                ),
                modifier = Modifier.testTag("nav_settings")
            )
        }
    }
}

@Composable
private fun EduNavigationRail(
    currentPage: AppPage,
    onSelectPage: (AppPage) -> Unit
) {
    NavigationRail(
        containerColor = EduWhite,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.size(12.dp))
        NavigationRailItem(
            selected = currentPage == AppPage.LIBRARY,
            onClick = { onSelectPage(AppPage.LIBRARY) },
            icon = {
                Icon(
                    imageVector = if (currentPage == AppPage.LIBRARY) {
                        Icons.Rounded.LibraryBooks
                    } else {
                        Icons.Outlined.LibraryBooks
                    },
                    contentDescription = stringResource(R.string.nav_library)
                )
            },
            label = { Text(stringResource(R.string.nav_library)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = EduGreenDark,
                selectedTextColor = EduGreenDark,
                indicatorColor = EduMint,
                unselectedIconColor = EduMuted,
                unselectedTextColor = EduMuted
            )
        )
        NavigationRailItem(
            selected = currentPage == AppPage.STUDIO,
            onClick = { onSelectPage(AppPage.STUDIO) },
            icon = {
                Icon(
                    imageVector = if (currentPage == AppPage.STUDIO) {
                        Icons.Rounded.AutoAwesome
                    } else {
                        Icons.Outlined.AutoAwesome
                    },
                    contentDescription = stringResource(R.string.nav_studio)
                )
            },
            label = { Text(stringResource(R.string.nav_studio)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = EduGreenDark,
                selectedTextColor = EduGreenDark,
                indicatorColor = EduMint,
                unselectedIconColor = EduMuted,
                unselectedTextColor = EduMuted
            )
        )
        NavigationRailItem(
            selected = currentPage == AppPage.SETTINGS,
            onClick = { onSelectPage(AppPage.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (currentPage == AppPage.SETTINGS) {
                        Icons.Rounded.Settings
                    } else {
                        Icons.Outlined.Settings
                    },
                    contentDescription = stringResource(R.string.nav_settings)
                )
            },
            label = { Text(stringResource(R.string.nav_settings)) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = EduGreenDark,
                selectedTextColor = EduGreenDark,
                indicatorColor = EduMint,
                unselectedIconColor = EduMuted,
                unselectedTextColor = EduMuted
            )
        )
    }
}
