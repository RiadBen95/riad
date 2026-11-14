package com.riad.voyage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riad.voyage.alerts.PriceAlertWorker
import com.riad.voyage.ui.VoyageApp
import com.riad.voyage.ui.theme.VoyageTheme

class MainActivity : ComponentActivity() {
    /** Billet ou confirmation partagé avec l'appli, en attente de lecture. */
    private val shared = androidx.compose.runtime.mutableStateOf<android.content.Intent?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        shared.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) shared.value = intent
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        I18n.init(this)
        PriceAlertWorker.schedule(this)
        setContent {
            VoyageTheme {
              run {
                val vm: AppViewModel = viewModel()
                shared.value?.takeIf { it.action == android.content.Intent.ACTION_SEND }?.let { i ->
                    androidx.compose.runtime.LaunchedEffect(i) {
                        @Suppress("DEPRECATION")
                        val uri = i.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                        vm.importBooking(uri, i.type?.takeIf { uri != null }, i.getStringExtra(android.content.Intent.EXTRA_TEXT))
                        shared.value = null
                    }
                }
                val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                VoyageApp(
                    vm = vm,
                    onFollowRequested = {
                        // Les notifications d'alerte demandent une autorisation depuis Android 13.
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
              }
            }
        }
    }
}
