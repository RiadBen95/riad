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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        I18n.init(this)
        PriceAlertWorker.schedule(this)
        setContent {
            VoyageTheme {
              run {
                val vm: AppViewModel = viewModel()
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
