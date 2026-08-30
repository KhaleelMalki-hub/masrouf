package sa.masrouf.app

import android.Manifest
import android.app.LocaleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import sa.masrouf.app.capture.MasroufNotificationListener
import sa.masrouf.app.capture.SmsInbox
import sa.masrouf.app.ui.AddExpenseScreen
import sa.masrouf.app.ui.AddExpenseViewModel
import sa.masrouf.core.money.Money
import sa.masrouf.app.ui.MasroufTheme
import sa.masrouf.app.ui.ThemeMode

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MasroufApp
        val repository = app.transactions
        val preferences = app.preferences

        setContent {
            // Read once from storage, then owned by the composition. The setter
            // writes through, so the choice survives a restart.
            var themeMode by remember { mutableStateOf(preferences.themeMode) }
            var salary by remember { mutableStateOf(preferences.salaryHalalas?.let(Money::ofHalalas)) }

            // The system bars follow the APP's theme, not the phone's. The default
            // enableEdgeToEdge() reads the system setting, so forcing the app dark
            // on a light phone left a white navigation bar under a dark screen.
            val dark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                )
            }

            MasroufTheme(mode = themeMode) {
                val viewModel: AddExpenseViewModel = viewModel(
                    factory = AddExpenseViewModel.Factory(
                        repository = repository,
                        // Passed as a function so the ViewModel never holds a
                        // ContentResolver, and so the import is testable without one.
                        readInbox = { SmsInbox(contentResolver).read() },
                        maintenance = app::runMaintenance,
                    ),
                )

                var captureEnabled by remember {
                    mutableStateOf(MasroufNotificationListener.isEnabled(this))
                }
                var smsEnabled by remember { mutableStateOf(hasSmsPermission()) }
                var canReadInbox by remember { mutableStateOf(hasReadSmsPermission()) }

                val requestSms = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> smsEnabled = granted }

                val requestReadSms = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> canReadInbox = granted }

                // Both are re-read on every resume rather than once. Notification
                // access is granted in another app entirely, and either permission
                // can be revoked from settings at any time - a cached yes would
                // leave the app claiming to capture while it silently did not.
                LifecycleResumeEffect(Unit) {
                    captureEnabled = MasroufNotificationListener.isEnabled(this@MainActivity)
                    smsEnabled = hasSmsPermission()
                    canReadInbox = hasReadSmsPermission()
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
                    canImportHistory = canReadInbox,
                    onRequestHistoryAccess = { requestReadSms.launch(Manifest.permission.READ_SMS) },
                    onSwitchLanguage = ::toggleLanguage,
                    themeMode = themeMode,
                    salary = salary,
                    onSalaryChange = { chosen ->
                        salary = chosen
                        preferences.salaryHalalas = chosen?.halalas
                    },
                    onThemeModeChange = { chosen ->
                        themeMode = chosen
                        preferences.themeMode = chosen
                    },
                )
            }
        }
    }

    private fun hasSmsPermission(): Boolean = granted(Manifest.permission.RECEIVE_SMS)

    private fun hasReadSmsPermission(): Boolean = granted(Manifest.permission.READ_SMS)

    /**
     * Switches between the two languages this app ships.
     *
     * Per-app, not system-wide: the whole point is being able to read this app in
     * Arabic without putting the rest of the phone into it. Android recreates the
     * activity itself, so nothing here has to.
     */
    private fun toggleLanguage() {
        val current = resources.configuration.locales[0].language
        val next = if (current.startsWith("ar")) "en" else "ar"

        // The framework API on 33+, not AppCompatDelegate. The delegate routes
        // through AppCompat's own machinery, which a bare ComponentActivity does
        // not have - it accepted the call and changed nothing, with no exception
        // to say so. AppCompatDelegate remains the path below 33, where there is
        // no framework LocaleManager to call.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(next)
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next))
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
