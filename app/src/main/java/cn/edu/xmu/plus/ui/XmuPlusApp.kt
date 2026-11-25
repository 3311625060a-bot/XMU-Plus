package cn.edu.xmu.plus.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.edu.xmu.plus.auth.AuthState
import cn.edu.xmu.plus.auth.LoginUiState
import cn.edu.xmu.plus.data.AccentTone
import cn.edu.xmu.plus.data.ActiveRollcall
import cn.edu.xmu.plus.data.AttachmentPreviewInfo
import cn.edu.xmu.plus.data.CourseDetailUiState
import cn.edu.xmu.plus.data.CourseItem
import cn.edu.xmu.plus.data.DarkModePreference
import cn.edu.xmu.plus.data.DockStyle
import cn.edu.xmu.plus.data.HomeworkDetailUiState
import cn.edu.xmu.plus.data.PickedLocalFile
import cn.edu.xmu.plus.data.RollcallHistoryUiState
import cn.edu.xmu.plus.data.ThemeSettings
import cn.edu.xmu.plus.data.UpdateCheckResult
import cn.edu.xmu.plus.data.XmuDashboardState
import cn.edu.xmu.plus.network.CheckInApiResult
import cn.edu.xmu.plus.ui.components.ExpressiveFloatingNavBar
import cn.edu.xmu.plus.ui.components.ExpressiveTabletNavigationRail
import cn.edu.xmu.plus.ui.components.FilmGrainOverlay
import cn.edu.xmu.plus.ui.components.LocalHapticsEnabled
import cn.edu.xmu.plus.ui.components.LocalSharedElementEnabled
import cn.edu.xmu.plus.ui.components.MainDestination
import cn.edu.xmu.plus.ui.components.PasscodeCheckInSheet
import cn.edu.xmu.plus.ui.screens.CoursesScreen
import cn.edu.xmu.plus.ui.screens.GlobalSearchScreen
import cn.edu.xmu.plus.ui.screens.HomeScreen
import cn.edu.xmu.plus.ui.screens.HomeworkDetailScreen
import cn.edu.xmu.plus.ui.screens.HomeworkFilter
import cn.edu.xmu.plus.ui.screens.HomeworkScreen
import cn.edu.xmu.plus.ui.screens.InAppViewerScreen
import cn.edu.xmu.plus.ui.screens.LoginScreen
import cn.edu.xmu.plus.ui.screens.ProfileScreen
import cn.edu.xmu.plus.ui.screens.QrScannerScreen
import cn.edu.xmu.plus.ui.screens.RollcallHistoryScreen
import cn.edu.xmu.plus.ui.screens.SettingsScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File

private const val ROUTE_HOMEWORK_DETAIL = "homework_detail/{homeworkId}/{sourceTag}"
private const val ROUTE_SETTINGS = "settings/{sourceTag}"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_QR_SCANNER = "qr_scanner"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_ROLLCALL_HISTORY = "rollcall_history"
private const val ROUTE_VIEWER = "viewer/{uploadId}/{fileName}"

private val TABLET_BREAKPOINT_DP = 600.dp

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
private val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

private fun isMainTab(route: String?): Boolean {
    return MainDestination.entries.any { it.route == route }
}

private fun mainTabIndex(route: String?): Int {
    return MainDestination.entries.indexOfFirst { it.route == route }
}

private fun usesSharedElementTransition(route: String?, enabled: Boolean): Boolean {
    return enabled && (route == ROUTE_HOMEWORK_DETAIL || route == ROUTE_SETTINGS)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun XmuPlusApp(
    themeSettings: ThemeSettings,
    authState: AuthState,
    loginUiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleRememberCredentials: (Boolean) -> Unit,
    onPrewarmLogin: () -> Unit,
    onSubmitNativeLogin: () -> Unit,
    onOpenWebViewDirectly: () -> Unit,
    onDismissWebView: () -> Unit,
    onWebViewCookiesCaptured: (String) -> Unit,
    onRefreshProfile: () -> Unit,
    onLogout: () -> Unit,
    dashboardState: XmuDashboardState,
    courseDetails: Map<Long, CourseDetailUiState>,
    homeworkDetails: Map<Long, HomeworkDetailUiState>,
    rollcallHistory: RollcallHistoryUiState,
    isIndexingSearch: Boolean,
    onRefreshAll: () -> Unit,
    onRefreshRollcalls: () -> Unit,
    onRefreshRollcallHistory: (Boolean) -> Unit,
    onSelectSemester: (Long?) -> Unit,
    onLoadCourseDetail: (CourseItem) -> Unit,
    onLoadHomeworkDetail: (Long) -> Unit,
    onIndexSearchResources: () -> Unit,
    onSubmitPasscodeCheckIn: suspend (Long, String) -> CheckInApiResult,
    onSubmitQrCheckIn: suspend (String) -> CheckInApiResult,
    onResolvePickedFile: (Uri) -> PickedLocalFile,
    onSubmitHomework: suspend (Long, String, List<PickedLocalFile>, Long?, (Float, String) -> Unit) -> Result<Unit>,
    onResolveAttachmentPreview: suspend (Long, String) -> Result<AttachmentPreviewInfo>,
    onDownloadPreviewFile: suspend (String, File) -> Result<File>,
    onSyncWebViewCookies: () -> Unit,
    onSelectAccentTone: (AccentTone) -> Unit,
    onSelectDarkMode: (DarkModePreference) -> Unit,
    onSelectDockStyle: (DockStyle) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleFilmGrain: (Boolean) -> Unit,
    onToggleSharedElement: (Boolean) -> Unit,
    onToggleDdlReminder: (Boolean) -> Unit,
    onToggleAutoCheckUpdates: (Boolean) -> Unit,
    onToggleDebugMode: (Boolean) -> Unit,
    startupUpdateAvailable: UpdateCheckResult.UpdateAvailable? = null,
    onDismissStartupUpdate: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var passcodeTarget by remember { mutableStateOf<ActiveRollcall?>(null) }
    var showPasscodeSheet by remember { mutableStateOf(false) }
    var homeworkFilter by remember { mutableStateOf(HomeworkFilter.ALL) }
    var pendingCourseId by remember { mutableStateOf<Long?>(null) }

    val sharedElementEnabled by rememberUpdatedState(themeSettings.sharedElementEnabled)
    val showBottomBar = isMainTab(currentRoute)
    val hazeState = rememberHazeState()

    fun navigateToMainTab(dest: MainDestination) {
        if (currentRoute != dest.route) {
            navController.navigate(dest.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(MainDestination.HOME.route) { saveState = true }
            }
        }
    }

    fun navigateToHomework(filter: HomeworkFilter) {
        homeworkFilter = filter
        navigateToMainTab(MainDestination.HOMEWORK)
    }

    fun openAttachmentViewer(uploadId: Long, title: String) {
        val encodedName = Uri.encode(title.ifBlank { "附件预览" })
        navController.navigate("viewer/$uploadId/$encodedName")
    }

    CompositionLocalProvider(
        LocalHapticsEnabled provides themeSettings.hapticsEnabled,
        LocalSharedElementEnabled provides themeSettings.sharedElementEnabled,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTabletLayout = maxWidth >= TABLET_BREAKPOINT_DP
                val hazeModifier = if (themeSettings.dockStyle == DockStyle.LIQUID_GLASS) {
                    Modifier.hazeSource(state = hazeState)
                } else {
                    Modifier
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    if (isTabletLayout && showBottomBar) {
                        ExpressiveTabletNavigationRail(
                            currentRoute = currentRoute,
                            dockStyle = themeSettings.dockStyle,
                            hazeState = hazeState,
                            onNavigate = { dest -> navigateToMainTab(dest) },
                        )
                    }

                    SharedTransitionLayout(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        val navModifier = Modifier
                            .fillMaxSize()
                            .then(hazeModifier)
                        NavHost(
                            navController = navController,
                            startDestination = MainDestination.HOME.route,
                            modifier = navModifier,
                            enterTransition = {
                                val initial = initialState.destination.route
                                val target = targetState.destination.route
                                if (isMainTab(initial) && isMainTab(target)) {
                                    fadeIn(tween(110, easing = FastOutSlowInEasing))
                                } else if (usesSharedElementTransition(target, sharedElementEnabled)) {
                                    fadeIn(tween(130, easing = EmphasizedDecelerate))
                                } else {
                                    slideInHorizontally(tween(170, easing = EmphasizedDecelerate)) {
                                        (it * 0.14f).toInt()
                                    } + fadeIn(tween(120, easing = EmphasizedDecelerate))
                                }
                            },
                            exitTransition = {
                                val initial = initialState.destination.route
                                val target = targetState.destination.route
                                if (isMainTab(initial) && isMainTab(target)) {
                                    fadeOut(tween(80, easing = FastOutLinearInEasing))
                                } else if (usesSharedElementTransition(target, sharedElementEnabled)) {
                                    fadeOut(tween(130, easing = EmphasizedDecelerate), targetAlpha = 0.96f)
                                } else {
                                    slideOutHorizontally(tween(170, easing = EmphasizedDecelerate)) {
                                        -(it * 0.06f).toInt()
                                    } + fadeOut(tween(110, easing = FastOutLinearInEasing), targetAlpha = 0.88f)
                                }
                            },
                            popEnterTransition = {
                                val initial = initialState.destination.route
                                val target = targetState.destination.route
                                if (isMainTab(initial) && isMainTab(target)) {
                                    fadeIn(tween(110, easing = FastOutSlowInEasing))
                                } else if (usesSharedElementTransition(initial, sharedElementEnabled)) {
                                    fadeIn(tween(130, easing = EmphasizedDecelerate), initialAlpha = 0.96f)
                                } else {
                                    slideInHorizontally(tween(160, easing = EmphasizedDecelerate)) {
                                        -(it * 0.06f).toInt()
                                    } + fadeIn(tween(120, easing = EmphasizedDecelerate), initialAlpha = 0.88f)
                                }
                            },
                            popExitTransition = {
                                val initial = initialState.destination.route
                                val target = targetState.destination.route
                                if (isMainTab(initial) && isMainTab(target)) {
                                    fadeOut(tween(80, easing = FastOutLinearInEasing))
                                } else if (usesSharedElementTransition(initial, sharedElementEnabled)) {
                                    fadeOut(tween(110, easing = EmphasizedAccelerate))
                                } else {
                                    slideOutHorizontally(tween(150, easing = StandardEasing)) {
                                        (it * 0.16f).toInt()
                                    } + fadeOut(tween(100, easing = FastOutLinearInEasing))
                                }
                            },
                        ) {
                            composable(MainDestination.HOME.route) {
                                HomeScreen(
                                    authState = authState,
                                    dashboardState = dashboardState,
                                    isTabletLayout = isTabletLayout,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                    onRefreshAll = onRefreshAll,
                                    onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
                                    onToggleDarkMode = { currentlyDark ->
                                        onSelectDarkMode(
                                            if (currentlyDark) DarkModePreference.LIGHT else DarkModePreference.DARK,
                                        )
                                    },
                                    onOpenQrCheckIn = { navController.navigate(ROUTE_QR_SCANNER) },
                                    onOpenPasscodeCheckIn = { rollcall ->
                                        passcodeTarget = rollcall
                                        showPasscodeSheet = true
                                    },
                                    onOpenRollcallHistory = { navController.navigate(ROUTE_ROLLCALL_HISTORY) },
                                    onOpenHomeworkDetail = { hwId, sourceTag ->
                                        navController.navigate("homework_detail/$hwId/$sourceTag")
                                    },
                                    onOpenPendingHomework = { navigateToHomework(HomeworkFilter.PENDING) },
                                    onOpenAllHomework = { navigateToHomework(HomeworkFilter.ALL) },
                                    onOpenCourses = { navigateToMainTab(MainDestination.COURSES) },
                                )
                            }

                            composable(MainDestination.HOMEWORK.route) {
                                HomeworkScreen(
                                    dashboardState = dashboardState,
                                    selectedFilter = homeworkFilter,
                                    onSelectFilter = { homeworkFilter = it },
                                    onRefreshAll = onRefreshAll,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    onOpenHomeworkDetail = { hwId, sourceTag ->
                                        navController.navigate("homework_detail/$hwId/$sourceTag")
                                    },
                                    isTabletLayout = isTabletLayout,
                                    homeworkDetails = homeworkDetails,
                                    onLoadHomeworkDetail = onLoadHomeworkDetail,
                                    onOpenAttachment = ::openAttachmentViewer,
                                    onResolvePickedFile = onResolvePickedFile,
                                    onSubmitHomework = onSubmitHomework,
                                )
                            }

                            composable(MainDestination.COURSES.route) {
                                CoursesScreen(
                                    dashboardState = dashboardState,
                                    courseDetails = courseDetails,
                                    rollcallHistory = rollcallHistory,
                                    onSelectSemester = onSelectSemester,
                                    onRefreshAll = onRefreshAll,
                                    onLoadCourseDetail = onLoadCourseDetail,
                                    onLoadRollcallHistory = onRefreshRollcallHistory,
                                    onOpenAttachment = ::openAttachmentViewer,
                                    onOpenHomeworkDetail = { hwId, sourceTag ->
                                        navController.navigate("homework_detail/$hwId/$sourceTag")
                                    },
                                    isTabletLayout = isTabletLayout,
                                    initialCourseId = pendingCourseId,
                                    onConsumeInitialCourse = { pendingCourseId = null },
                                )
                            }

                            composable(MainDestination.PROFILE.route) {
                                ProfileScreen(
                                    themeSettings = themeSettings,
                                    authState = authState,
                                    dashboardState = dashboardState,
                                    isTabletLayout = isTabletLayout,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                    onRefreshProfile = onRefreshProfile,
                                    onLogout = onLogout,
                                    onOpenCourses = { navigateToMainTab(MainDestination.COURSES) },
                                    onOpenPendingHomework = { navigateToHomework(HomeworkFilter.PENDING) },
                                    onOpenGradedHomework = { navigateToHomework(HomeworkFilter.GRADED) },
                                    onOpenRollcallHistory = { navController.navigate(ROUTE_ROLLCALL_HISTORY) },
                                    onOpenSettings = { sourceTag ->
                                        navController.navigate("settings/$sourceTag")
                                    },
                                )
                            }

                            composable(ROUTE_SEARCH) {
                                GlobalSearchScreen(
                                    dashboardState = dashboardState,
                                    courseDetails = courseDetails,
                                    isIndexingSearch = isIndexingSearch,
                                    onIndexAllResources = onIndexSearchResources,
                                    onOpenHomeworkDetail = { hwId ->
                                        navController.navigate("homework_detail/$hwId/search")
                                    },
                                    onOpenCourse = { courseId ->
                                        pendingCourseId = courseId
                                        navController.popBackStack()
                                        navigateToMainTab(MainDestination.COURSES)
                                    },
                                    onOpenAttachment = ::openAttachmentViewer,
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable(ROUTE_ROLLCALL_HISTORY) {
                                RollcallHistoryScreen(
                                    uiState = rollcallHistory,
                                    isDemoMode = dashboardState.isDemoMode,
                                    onRefresh = onRefreshRollcallHistory,
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable(
                                route = ROUTE_VIEWER,
                                arguments = listOf(
                                    navArgument("uploadId") { type = NavType.LongType },
                                    navArgument("fileName") { type = NavType.StringType },
                                ),
                            ) { entry ->
                                val uploadId = entry.arguments?.getLong("uploadId") ?: 0L
                                val fileName = Uri.decode(entry.arguments?.getString("fileName").orEmpty())
                                    .ifBlank { "附件预览" }
                                InAppViewerScreen(
                                    uploadId = uploadId,
                                    initialFileName = fileName,
                                    isDemoMode = dashboardState.isDemoMode,
                                    onResolvePreview = onResolveAttachmentPreview,
                                    onDownloadFile = onDownloadPreviewFile,
                                    onSyncWebViewCookies = onSyncWebViewCookies,
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable(ROUTE_QR_SCANNER) {
                                QrScannerScreen(
                                    isDemoMode = dashboardState.isDemoMode,
                                    onSubmitQrCode = onSubmitQrCheckIn,
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable(ROUTE_LOGIN) {
                                LoginScreen(
                                    authState = authState,
                                    uiState = loginUiState,
                                    onUsernameChange = onUsernameChange,
                                    onPasswordChange = onPasswordChange,
                                    onToggleRememberCredentials = onToggleRememberCredentials,
                                    onPrewarmLogin = onPrewarmLogin,
                                    onSubmitNativeLogin = onSubmitNativeLogin,
                                    onOpenWebViewDirectly = onOpenWebViewDirectly,
                                    onDismissWebView = onDismissWebView,
                                    onWebViewCookiesCaptured = onWebViewCookiesCaptured,
                                    onLoginSuccess = { navController.popBackStack() },
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable(
                                route = ROUTE_SETTINGS,
                                arguments = listOf(
                                    navArgument("sourceTag") { type = NavType.StringType },
                                ),
                            ) { entry ->
                                val sourceTag = entry.arguments?.getString("sourceTag") ?: "appearance"
                                SettingsScreen(
                                    settings = themeSettings,
                                    sourceTag = sourceTag,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    onSelectAccentTone = onSelectAccentTone,
                                    onSelectDarkMode = onSelectDarkMode,
                                    onSelectDockStyle = onSelectDockStyle,
                                    onToggleHaptics = onToggleHaptics,
                                    onToggleFilmGrain = onToggleFilmGrain,
                                    onToggleSharedElement = onToggleSharedElement,
                                    onToggleDdlReminder = onToggleDdlReminder,
                                    onToggleAutoCheckUpdates = onToggleAutoCheckUpdates,
                                    onToggleDebugMode = onToggleDebugMode,
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable(
                                route = ROUTE_HOMEWORK_DETAIL,
                                arguments = listOf(
                                    navArgument("homeworkId") { type = NavType.LongType },
                                    navArgument("sourceTag") { type = NavType.StringType },
                                ),
                            ) { entry ->
                                val hwId = entry.arguments?.getLong("homeworkId") ?: 0L
                                val sourceTag = entry.arguments?.getString("sourceTag") ?: "home"
                                val fallbackHw = dashboardState.homeworks.find { it.id == hwId }
                                HomeworkDetailScreen(
                                    homeworkId = hwId,
                                    sourceTag = sourceTag,
                                    isDemoMode = dashboardState.isDemoMode,
                                    detailState = homeworkDetails[hwId],
                                    fallbackHomework = fallbackHw,
                                    onLoadHomeworkDetail = onLoadHomeworkDetail,
                                    onOpenAttachment = ::openAttachmentViewer,
                                    onResolvePickedFile = onResolvePickedFile,
                                    onSubmitHomework = onSubmitHomework,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showBottomBar && !isTabletLayout,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(200, easing = EmphasizedDecelerate),
                    ) + fadeIn(tween(160)),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 2 },
                        animationSpec = tween(110, easing = EmphasizedAccelerate),
                    ) + fadeOut(tween(90)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ExpressiveFloatingNavBar(
                        currentRoute = currentRoute,
                        dockStyle = themeSettings.dockStyle,
                        hazeState = hazeState,
                        onNavigate = { dest -> navigateToMainTab(dest) },
                    )
                }

                // Toggleable Subtle Film Grain Overlay (non-interactive Canvas)
                FilmGrainOverlay(
                    enabled = themeSettings.filmGrainEnabled,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showPasscodeSheet) {
        PasscodeCheckInSheet(
            activeRollcalls = dashboardState.activeRollcalls,
            initialRollcall = passcodeTarget,
            isDemoMode = dashboardState.isDemoMode,
            onRefreshRollcalls = onRefreshRollcalls,
            onSubmitPasscode = onSubmitPasscodeCheckIn,
            onDismiss = { showPasscodeSheet = false },
        )
    }

    if (startupUpdateAvailable != null) {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val downloadTarget = startupUpdateAvailable.apkDownloadUrl ?: startupUpdateAvailable.releasePageUrl
        AlertDialog(
            onDismissRequest = onDismissStartupUpdate,
            title = {
                Text(
                    text = buildString {
                        append("发现新版本 ${startupUpdateAvailable.latestVersion}")
                        if (startupUpdateAvailable.isPreRelease) append(" (Preview)")
                    },
                )
            },
            text = {
                Text(
                    text = startupUpdateAvailable.releaseNotes.ifBlank {
                        "GitHub Releases 已发布新版本 ${startupUpdateAvailable.releaseTitle}，点击前往下载更新。"
                    }.take(320),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissStartupUpdate) {
                    Text("暂不更新")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { uriHandler.openUri(downloadTarget) }.onFailure {
                            Toast.makeText(context, "无法打开更新链接", Toast.LENGTH_SHORT).show()
                        }
                        onDismissStartupUpdate()
                    },
                ) {
                    Text(if (startupUpdateAvailable.apkDownloadUrl != null) "下载更新" else "查看发布页")
                }
            },
        )
    }
}
