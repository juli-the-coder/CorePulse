package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.BenchmarkLabScreen
import com.example.ui.screens.GamesScreen
import com.example.ui.screens.HardwareSettingsScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.SessionDetailScreen
import com.example.ui.theme.CorePulseTheme
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoMetricSmall
import com.example.viewmodel.MainViewModel

object NavRoutes {
    const val ROUTE_MONITOR = "monitor"
    const val ROUTE_BENCHMARK = "benchmark"
    const val ROUTE_GAMES = "games"
    const val ROUTE_HARDWARE = "hardware"
    const val ROUTE_SESSION_DETAIL = "session_detail"
}

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelDestinations = listOf(
    NavDestinationItem(
        route = NavRoutes.ROUTE_MONITOR,
        labelRes = R.string.nav_monitor,
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        testTag = "nav_monitor"
    ),
    NavDestinationItem(
        route = NavRoutes.ROUTE_BENCHMARK,
        labelRes = R.string.nav_benchmark,
        selectedIcon = Icons.Filled.ViewInAr,
        unselectedIcon = Icons.Outlined.ViewInAr,
        testTag = "nav_benchmark"
    ),
    NavDestinationItem(
        route = NavRoutes.ROUTE_GAMES,
        labelRes = R.string.nav_games,
        selectedIcon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports,
        testTag = "nav_games"
    ),
    NavDestinationItem(
        route = NavRoutes.ROUTE_HARDWARE,
        labelRes = R.string.nav_hardware,
        selectedIcon = Icons.Filled.Memory,
        unselectedIcon = Icons.Outlined.Memory,
        testTag = "nav_hardware"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, window) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> viewModel.frameMetricsTracker.start(window)
                        Lifecycle.Event.ON_PAUSE -> viewModel.frameMetricsTracker.stop()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    viewModel.frameMetricsTracker.stop()
                }
            }

            CorePulseTheme(themeMode = settings.themeMode) {
                CorePulseApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorePulseApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isDetailRoute = currentDestination?.route?.startsWith(NavRoutes.ROUTE_SESSION_DETAIL) == true

    val specs by viewModel.deviceSpecs.collectAsStateWithLifecycle()
    val snapshot by viewModel.liveSnapshot.collectAsStateWithLifecycle()
    val history by viewModel.liveHistory.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val benchmarkState by viewModel.benchmarkState.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isScanningGames by viewModel.isScanningGames.collectAsStateWithLifecycle()
    val selectedGame by viewModel.selectedGame.collectAsStateWithLifecycle()
    val savedSessions by viewModel.savedSessions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiMessage) {
        val msg = uiMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val telemetry = LocalTelemetryColors.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 640.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isDetailRoute) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (recordingState.isRecording) telemetry.thermal else telemetry.ram)
                                )
                            }
                        },
                        actions = {
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "GPU ${snapshot.gpuUsagePercent.toInt()}%",
                                        style = MonoMetricSmall,
                                        color = telemetry.gpu,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "CPU ${snapshot.cpuUsagePercent.toInt()}%",
                                        style = MonoMetricSmall,
                                        color = telemetry.cpu,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${snapshot.frameMetrics.fps.toInt()} FPS",
                                        style = MonoMetricSmall,
                                        color = telemetry.frame,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!useNavRail && !isDetailRoute) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && !isDetailRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.ROUTE_MONITOR,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(NavRoutes.ROUTE_MONITOR) {
                        MonitorScreen(
                            specs = specs,
                            snapshot = snapshot,
                            history = history,
                            recordingState = recordingState,
                            settings = settings,
                            installedApps = installedApps,
                            selectedGame = selectedGame,
                            onSelectGameTarget = viewModel::selectGameTarget,
                            onSelectBuiltInBenchmark = viewModel::selectBuiltInBenchmarkTarget,
                            onToggleRecording = {
                                if (recordingState.isRecording) {
                                    viewModel.stopAndSaveRecording()
                                } else {
                                    viewModel.startRecordingForSelectedGame()
                                }
                            },
                            onLaunchSelectedGame = viewModel::launchSelectedGameAndMonitor,
                            onOpenBenchmarkLab = {
                                navController.navigate(NavRoutes.ROUTE_BENCHMARK)
                            }
                        )
                    }

                    composable(NavRoutes.ROUTE_BENCHMARK) {
                        BenchmarkLabScreen(
                            benchmarkState = benchmarkState,
                            snapshot = snapshot,
                            settings = settings,
                            frameMetricsTracker = viewModel.frameMetricsTracker,
                            onSelectPreset = viewModel::selectBenchmarkPreset,
                            onToggleCpuStress = viewModel::toggleBenchmarkCpuStress,
                            onToggleRamStress = viewModel::toggleBenchmarkRamStress,
                            onSelectDuration = viewModel::setBenchmarkDuration,
                            onStartBenchmark = viewModel::startBenchmark,
                            onStopBenchmark = { viewModel.stopBenchmark(autoSave = true) },
                            onOpenSessionDetail = { sessionId ->
                                navController.navigate("${NavRoutes.ROUTE_SESSION_DETAIL}/$sessionId")
                            }
                        )
                    }

                    composable(NavRoutes.ROUTE_GAMES) {
                        GamesScreen(
                            installedApps = installedApps,
                            isScanning = isScanningGames,
                            sessions = savedSessions,
                            settings = settings,
                            onRefreshGames = viewModel::refreshInstalledGames,
                            onLaunchAndMonitor = viewModel::launchGameAndMonitor,
                            onOpenSessionDetail = { sessionId ->
                                navController.navigate("${NavRoutes.ROUTE_SESSION_DETAIL}/$sessionId")
                            },
                            onDeleteSession = viewModel::deleteSession,
                            onGoToBenchmark = {
                                navController.navigate(NavRoutes.ROUTE_BENCHMARK)
                            }
                        )
                    }

                    composable(NavRoutes.ROUTE_HARDWARE) {
                        HardwareSettingsScreen(
                            specs = specs,
                            snapshot = snapshot,
                            settings = settings,
                            onUpdateSamplingMs = viewModel::updateSamplingInterval,
                            onUpdateTargetFps = viewModel::updateTargetFps,
                            onUpdateUseFahrenheit = viewModel::updateUseFahrenheit,
                            onUpdateLiveNotification = viewModel::updateLiveNotification,
                            onUpdateThemeMode = viewModel::updateThemeMode,
                            onClearHistory = viewModel::clearAllSessions
                        )
                    }

                    composable(
                        route = "${NavRoutes.ROUTE_SESSION_DETAIL}/{sessionId}",
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: -1L
                        val session by viewModel.observeSession(sessionId).collectAsStateWithLifecycle(initialValue = null)
                        val samples by viewModel.observeSessionSamples(sessionId).collectAsStateWithLifecycle(initialValue = emptyList())

                        SessionDetailScreen(
                            session = session,
                            samples = samples,
                            settings = settings,
                            onBack = { navController.popBackStack() },
                            onShareReport = viewModel::shareSessionReport,
                            onDeleteSession = viewModel::deleteSession
                        )
                    }
                }
            }
        }
    }
}
