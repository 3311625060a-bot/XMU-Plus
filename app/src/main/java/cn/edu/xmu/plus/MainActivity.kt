package cn.edu.xmu.plus

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.xmu.plus.auth.AuthViewModel
import cn.edu.xmu.plus.data.AppUpdateChecker
import cn.edu.xmu.plus.data.DarkModePreference
import cn.edu.xmu.plus.data.ThemePreferencesRepository
import cn.edu.xmu.plus.data.ThemeSettings
import cn.edu.xmu.plus.data.UpdateCheckResult
import cn.edu.xmu.plus.data.XmuMainViewModel
import cn.edu.xmu.plus.debug.DebugLogStore
import cn.edu.xmu.plus.ui.XmuPlusApp
import cn.edu.xmu.plus.ui.theme.XmuPlusTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogStore.init(applicationContext)
        enableEdgeToEdge()

        setContent {
            val repo = remember { ThemePreferencesRepository(applicationContext) }
            val settings by repo.themeSettings.collectAsStateWithLifecycle(initialValue = ThemeSettings())
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsStateWithLifecycle()
            val loginUiState by authViewModel.loginUiState.collectAsStateWithLifecycle()
            val mainViewModel: XmuMainViewModel = viewModel()
            val dashboardState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val courseDetails by mainViewModel.courseDetails.collectAsStateWithLifecycle()
            val homeworkDetails by mainViewModel.homeworkDetails.collectAsStateWithLifecycle()
            val rollcallHistory by mainViewModel.rollcallHistory.collectAsStateWithLifecycle()
            val isIndexingSearch by mainViewModel.isIndexingSearch.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var startupUpdateAvailable by remember { mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null) }

            LaunchedEffect(Unit) {
                delay(2200L)
                val persisted = repo.themeSettings.first()
                if (persisted.autoCheckUpdatesEnabled) {
                    val res = AppUpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                    if (res is UpdateCheckResult.UpdateAvailable) {
                        startupUpdateAvailable = res
                    }
                }
            }

            LaunchedEffect(settings.debugModeEnabled) {
                DebugLogStore.setEnabled(settings.debugModeEnabled)
            }

            LaunchedEffect(settings.ddlReminderEnabled) {
                mainViewModel.updateReminderPreference(settings.ddlReminderEnabled)
            }

            LaunchedEffect(authState) {
                mainViewModel.bindAuth(
                    authClient = authViewModel.authClient,
                    sessionStore = authViewModel.sessionStore,
                    authState = authState,
                )
            }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (settings.darkMode) {
                DarkModePreference.SYSTEM -> systemDark
                DarkModePreference.LIGHT -> false
                DarkModePreference.DARK -> true
            }

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }

            XmuPlusTheme(settings = settings) {
                XmuPlusApp(
                    themeSettings = settings,
                    authState = authState,
                    loginUiState = loginUiState,
                    onUsernameChange = authViewModel::updateUsername,
                    onPasswordChange = authViewModel::updatePassword,
                    onToggleRememberCredentials = authViewModel::toggleRememberCredentials,
                    onPrewarmLogin = authViewModel::prewarmLogin,
                    onSubmitNativeLogin = authViewModel::submitNativeLogin,
                    onOpenWebViewDirectly = authViewModel::openWebViewDirectly,
                    onDismissWebView = authViewModel::dismissWebView,
                    onWebViewCookiesCaptured = authViewModel::onWebViewCookiesCaptured,
                    onRefreshProfile = authViewModel::refreshProfile,
                    onLogout = authViewModel::logout,
                    dashboardState = dashboardState,
                    courseDetails = courseDetails,
                    homeworkDetails = homeworkDetails,
                    rollcallHistory = rollcallHistory,
                    isIndexingSearch = isIndexingSearch,
                    onRefreshAll = mainViewModel::refreshAll,
                    onRefreshRollcalls = mainViewModel::refreshRollcalls,
                    onRefreshRollcallHistory = mainViewModel::loadAllRollcallHistory,
                    onSelectSemester = mainViewModel::selectSemester,
                    onLoadCourseDetail = mainViewModel::loadCourseDetail,
                    onLoadHomeworkDetail = mainViewModel::loadHomeworkDetail,
                    onIndexSearchResources = mainViewModel::indexAllCoursesForSearch,
                    onSubmitPasscodeCheckIn = mainViewModel::submitPasscodeCheckIn,
                    onSubmitQrCheckIn = mainViewModel::submitQrCheckIn,
                    onResolvePickedFile = { uri -> mainViewModel.resolvePickedFile(this@MainActivity, uri) },
                    onSubmitHomework = { hwId, comment, files, subId, onProg ->
                        mainViewModel.submitHomeworkWithFiles(
                            context = this@MainActivity,
                            homeworkId = hwId,
                            comment = comment,
                            pickedFiles = files,
                            existingSubmissionId = subId,
                            onProgress = onProg,
                        )
                    },
                    onResolveAttachmentPreview = mainViewModel::resolveAttachmentPreview,
                    onDownloadPreviewFile = mainViewModel::downloadPreviewFile,
                    onSyncWebViewCookies = mainViewModel::syncCookiesToWebView,
                    onSelectAccentTone = { tone ->
                        scope.launch { repo.setAccentTone(tone) }
                    },
                    onSelectDarkMode = { mode ->
                        scope.launch { repo.setDarkMode(mode) }
                    },
                    onSelectDockStyle = { style ->
                        scope.launch { repo.setDockStyle(style) }
                    },
                    onToggleHaptics = { enabled ->
                        scope.launch { repo.setHapticsEnabled(enabled) }
                    },
                    onToggleFilmGrain = { enabled ->
                        scope.launch { repo.setFilmGrainEnabled(enabled) }
                    },
                    onToggleSharedElement = { enabled ->
                        scope.launch { repo.setSharedElementEnabled(enabled) }
                    },
                    onToggleDdlReminder = { enabled ->
                        scope.launch { repo.setDdlReminderEnabled(enabled) }
                    },
                    onToggleAutoCheckUpdates = { enabled ->
                        scope.launch { repo.setAutoCheckUpdatesEnabled(enabled) }
                    },
                    onToggleDebugMode = { enabled ->
                        scope.launch { repo.setDebugModeEnabled(enabled) }
                    },
                    startupUpdateAvailable = startupUpdateAvailable,
                    onDismissStartupUpdate = { startupUpdateAvailable = null },
                )
            }
        }
    }
}
