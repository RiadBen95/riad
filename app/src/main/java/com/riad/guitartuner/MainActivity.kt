package com.riad.guitartuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riad.guitartuner.ui.PermissionScreen
import com.riad.guitartuner.ui.TunerScreen
import com.riad.guitartuner.ui.theme.TunerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TunerTheme {
                val context = LocalContext.current
                var granted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted = it }

                LaunchedEffect(Unit) {
                    if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO)
                }

                if (granted) {
                    val vm: TunerViewModel = viewModel()
                    val state by vm.state.collectAsStateWithLifecycle()
                    // N'écoute le micro que lorsque l'application est au premier plan.
                    LifecycleResumeEffect(Unit) {
                        vm.start()
                        onPauseOrDispose { vm.stop() }
                    }
                    TunerScreen(
                        state = state,
                        onSelectTuning = vm::selectTuning,
                        onToggleString = vm::toggleString,
                        onChangeA4 = vm::changeA4,
                        onRetry = { vm.stop(); vm.start() },
                    )
                } else {
                    PermissionScreen(onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) })
                }
            }
        }
    }
}
