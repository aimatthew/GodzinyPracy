package pl.godzinypracy.workly

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import pl.godzinypracy.workly.ui.WorkViewModel
import pl.godzinypracy.workly.ui.WorklyApp
import pl.godzinypracy.workly.ui.theme.WorklyTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WorkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            WorklyTheme {
                WorklyApp(viewModel)
            }
        }
    }
}
