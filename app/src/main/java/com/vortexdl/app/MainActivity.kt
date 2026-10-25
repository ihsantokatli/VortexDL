package com.vortexdl.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vortexdl.app.presentation.browser.BrowserScreen
import com.vortexdl.app.presentation.browser.BrowserViewModel
import com.vortexdl.app.presentation.browser.SharedUrlExtractor
import com.vortexdl.app.presentation.theme.VortexDLTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SharedUrlExtractor.extract(intent)?.let(viewModel::setInitialUrl)

        setContent {
            VortexDLTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SharedUrlExtractor.extract(intent)?.let(viewModel::openSharedUrl)
    }
}
