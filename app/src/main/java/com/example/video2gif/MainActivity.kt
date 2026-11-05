package com.example.video2gif

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.video2gif.ui.AppViewModel
import com.example.video2gif.ui.Video2GifAppUi
import com.example.video2gif.ui.theme.Video2GifTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = viewModel.settings.collectAsStateWithLifecycle()
            Video2GifTheme(
                themeMode = settings.value.themeMode,
                dynamicColor = settings.value.dynamicColor,
            ) {
                Video2GifAppUi(viewModel)
            }
        }
    }
}
