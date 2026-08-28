package sa.masrouf.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import sa.masrouf.app.capture.MasroufNotificationListener
import sa.masrouf.app.ui.AddExpenseScreen
import sa.masrouf.app.ui.AddExpenseViewModel
import sa.masrouf.app.ui.MasroufTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Both system bars drawn dark over the wool ground. The default
        // enableEdgeToEdge() picks a light scrim, which left a white slab under an
        // otherwise dark screen.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        val repository = (application as MasroufApp).transactions

        setContent {
            MasroufTheme {
                val viewModel: AddExpenseViewModel =
                    viewModel(factory = AddExpenseViewModel.Factory(repository))

                var captureEnabled by remember {
                    mutableStateOf(MasroufNotificationListener.isEnabled(this))
                }
                var smsEnabled by remember { mutableStateOf(hasSmsPermission()) }

                val requestSms = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> smsEnabled = granted }

                // Both are re-read on every resume rather than once. Notification
                // access is granted in another app entirely, and either permission
                // can be revoked from settings at any time - a cached yes would
                // leave the app claiming to capture while it silently did not.
                LifecycleResumeEffect(Unit) {
                    captureEnabled = MasroufNotificationListener.isEnabled(this@MainActivity)
                    smsEnabled = hasSmsPermission()
                    onPauseOrDispose { }
                }

                AddExpenseScreen(
                    viewModel = viewModel,
                    captureEnabled = captureEnabled,
                    onEnableCapture = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    smsEnabled = smsEnabled,
                    onEnableSms = { requestSms.launch(Manifest.permission.RECEIVE_SMS) },
                )
            }
        }
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
}
