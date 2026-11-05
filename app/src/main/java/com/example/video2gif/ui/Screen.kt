package com.example.video2gif.ui

sealed interface Screen {
    data object Home : Screen
    data object Editor : Screen
    data object Batch : Screen
    data object Progress : Screen
    data object Result : Screen
    data object Settings : Screen
}
