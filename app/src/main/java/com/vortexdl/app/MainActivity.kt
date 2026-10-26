package com.vortexdl.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortexdl.app.presentation.browser.BrowserScreen
import com.vortexdl.app.presentation.browser.BrowserViewModel
import com.vortexdl.app.presentation.browser.SharedUrlExtractor
import com.vortexdl.app.presentation.downloads.DownloadsScreen
import com.vortexdl.app.presentation.downloads.DownloadsViewModel
import com.vortexdl.app.presentation.theme.VortexDLTheme
import dagger.hilt.android.AndroidEntryPoint

enum class NavDestination {
    BROWSER,
    DOWNLOADS,
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()
    private val downloadsViewModel: DownloadsViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Permission result handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        requestNotificationPermissionIfNeeded()
        SharedUrlExtractor.extract(intent)?.let(browserViewModel::setInitialUrl)

        setContent {
            VortexDLTheme {
                MainContent(
                    browserViewModel = browserViewModel,
                    downloadsViewModel = downloadsViewModel,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SharedUrlExtractor.extract(intent)?.let { url ->
            browserViewModel.openSharedUrl(url)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun MainContent(
    browserViewModel: BrowserViewModel,
    downloadsViewModel: DownloadsViewModel,
) {
    var currentDestination by rememberSaveable { mutableStateOf(NavDestination.BROWSER) }
    val downloadsUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val activeDownloadsCount = downloadsUiState.activeCount

    BackHandler(enabled = currentDestination == NavDestination.DOWNLOADS) {
        currentDestination = NavDestination.BROWSER
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination == NavDestination.BROWSER,
                    onClick = { currentDestination = NavDestination.BROWSER },
                    icon = {
                        Icon(
                            imageVector = if (currentDestination == NavDestination.BROWSER) {
                                Icons.Filled.Explore
                            } else {
                                Icons.Outlined.Explore
                            },
                            contentDescription = stringResource(R.string.nav_browser),
                        )
                    },
                    label = { Text(text = stringResource(R.string.nav_browser)) },
                )
                NavigationBarItem(
                    selected = currentDestination == NavDestination.DOWNLOADS,
                    onClick = { currentDestination = NavDestination.DOWNLOADS },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeDownloadsCount > 0) {
                                    Badge {
                                        Text(text = activeDownloadsCount.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (currentDestination == NavDestination.DOWNLOADS) {
                                    Icons.Filled.Download
                                } else {
                                    Icons.Outlined.Download
                                },
                                contentDescription = stringResource(R.string.nav_downloads),
                            )
                        }
                    },
                    label = { Text(text = stringResource(R.string.nav_downloads)) },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            BrowserScreen(
                viewModel = browserViewModel,
                isTabActive = currentDestination == NavDestination.BROWSER,
                onNavigateToDownloads = { currentDestination = NavDestination.DOWNLOADS },
                modifier = Modifier.fillMaxSize(),
            )

            if (currentDestination == NavDestination.DOWNLOADS) {
                DownloadsScreen(
                    viewModel = downloadsViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
