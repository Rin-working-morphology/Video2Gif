package com.example.video2gif

import android.os.Bundle
import android.os.Build
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.video2gif.ui.AppViewModel
import com.example.video2gif.ui.Video2GifAppUi
import com.example.video2gif.ui.theme.Video2GifTheme
import com.example.video2gif.ui.theme.Motion

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 仅在首帧准备好时淡出系统启动页，不设置最短展示时间。
            splashScreen.setOnExitAnimationListener { splash ->
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    splash.remove()
                } else {
                    splash.animate()
                        .alpha(0f)
                        .setDuration(Motion.SplashExit.toLong())
                        .setInterpolator(PathInterpolator(0.25f, 1f, 0.5f, 1f))
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) { splash.remove() }
                            override fun onAnimationCancel(animation: Animator) { splash.remove() }
                        })
                        .start()
                }
            }
        }
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
