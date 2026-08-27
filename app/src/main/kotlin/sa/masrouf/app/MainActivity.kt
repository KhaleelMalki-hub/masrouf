package sa.masrouf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
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
                AddExpenseScreen(viewModel = viewModel)
            }
        }
    }
}
