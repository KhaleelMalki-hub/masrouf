package sa.masrouf.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import sa.masrouf.app.capture.MasroufNotificationListener
import sa.masrouf.app.ui.AddExpenseScreen
import sa.masrouf.app.ui.AddExpenseViewModel
import sa.masrouf.app.ui.MasroufTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as MasroufApp).transactions

        setContent {
            MasroufTheme {
                val viewModel: AddExpenseViewModel =
                    viewModel(factory = AddExpenseViewModel.Factory(repository))

                var captureEnabled by remember {
                    mutableStateOf(MasroufNotificationListener.isEnabled(this))
                }
                // Re-read on every resume rather than once. Granting access happens
                // in the system settings app, so the only moment this screen can
                // learn the answer changed is when the user comes back to it.
                LifecycleResumeEffect(Unit) {
                    captureEnabled = MasroufNotificationListener.isEnabled(this@MainActivity)
                    onPauseOrDispose { }
                }

                AddExpenseScreen(
                    viewModel = viewModel,
                    captureEnabled = captureEnabled,
                    onEnableCapture = { openNotificationAccessSettings() },
                )
            }
        }
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}
