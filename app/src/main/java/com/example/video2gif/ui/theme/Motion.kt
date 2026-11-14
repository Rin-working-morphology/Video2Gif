package com.example.video2gif.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * 动效参数。所有动画都应能落到这里，不在页面里写散落的魔法数字。
 * 曲线统一为指数缓出（quart/quint），不使用回弹——工具类界面不应该让人等编排。
 */
object Motion {
    /** 按下、切换、图标旋转。 */
    const val Fast = 140
    /** 常规状态变化、面板展开。 */
    const val Base = 220
    /** 一次性入场与较大的容器变化。 */
    const val Slow = 420
    /** 极光漂移的一整个周期。 */
    const val Aurora = 26_000

    val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
    val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val EaseStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
