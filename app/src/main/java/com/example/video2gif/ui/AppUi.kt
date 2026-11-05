package com.example.video2gif.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.video2gif.ui.components.LocalReducedMotion
import com.example.video2gif.ui.components.rememberReducedMotion
import com.example.video2gif.ui.screens.BatchScreen
import com.example.video2gif.ui.screens.EditorScreen
import com.example.video2gif.ui.screens.HomeScreen
import com.example.video2gif.ui.screens.ProgressScreen
import com.example.video2gif.ui.screens.ResultScreen
import com.example.video2gif.ui.screens.SettingsScreen

@Composable
fun Video2GifAppUi(vm: AppViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val reducedMotion = rememberReducedMotion()
    val notice = vm.notice

    LaunchedEffect(notice) {
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            vm.consumeNotice()
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        BackHandler(enabled = vm.screenStack.size > 1) { vm.back() }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AnimatedContent(
                targetState = vm.screen,
                transitionSpec = {
                    fadeIn(tween(if (reducedMotion) 0 else 180)) togetherWith
                        fadeOut(tween(if (reducedMotion) 0 else 120))
                },
                label = "screenTransition",
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.Editor -> EditorScreen(vm)
                    Screen.Batch -> BatchScreen(vm)
                    Screen.Progress -> ProgressScreen(vm)
                    Screen.Result -> ResultScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}
