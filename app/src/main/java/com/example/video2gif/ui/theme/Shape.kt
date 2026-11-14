package com.example.video2gif.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * 形状梯度：小控件 10dp，普通面板 16dp，主面板 20dp。
 * 只有标签与主按钮使用整圆胶囊；卡片不再向上加圆角。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

object PanelShape {
    val panel = RoundedCornerShape(20.dp)
    val card = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(10.dp)
    val pill = RoundedCornerShape(percent = 50)
    val sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val thumb = RoundedCornerShape(10.dp)
}
