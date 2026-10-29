package com.marvindi.mediaoptimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marvindi.mediaoptimizer.ui.MainScreen
import com.marvindi.mediaoptimizer.ui.theme.MediaOptimizerTheme
import com.marvindi.mediaoptimizer.viewmodel.CompressViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaOptimizerTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val viewModel: CompressViewModel = viewModel()
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
