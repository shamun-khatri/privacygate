package com.privacygate.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.privacygate.app.MainTab

@Composable
fun AppTabIcon(tab: MainTab, selected: Boolean) {
    val color = if (selected) Color(0xFFD8FF78) else Color(0xFF7E8B82)
    Canvas(Modifier.size(23.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        when (tab) {
            MainTab.HOME -> {
                val shield = Path().apply {
                    moveTo(size.width / 2, size.height * .08f)
                    lineTo(size.width * .86f, size.height * .23f)
                    lineTo(size.width * .79f, size.height * .66f)
                    quadraticTo(size.width * .7f, size.height * .86f, size.width / 2, size.height * .94f)
                    quadraticTo(size.width * .3f, size.height * .86f, size.width * .21f, size.height * .66f)
                    lineTo(size.width * .14f, size.height * .23f)
                    close()
                }
                drawPath(shield, color, style = stroke)
                drawLine(color, Offset(size.width * .34f, size.height * .5f), Offset(size.width * .46f, size.height * .62f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width * .46f, size.height * .62f), Offset(size.width * .68f, size.height * .39f), 2.dp.toPx(), StrokeCap.Round)
            }
            MainTab.GALLERY -> {
                val gap = 3.dp.toPx()
                val cell = (size.width - gap) / 2
                val corner = CornerRadius(2.dp.toPx())
                drawRoundRect(color, Offset.Zero, androidx.compose.ui.geometry.Size(cell, cell), corner, style = stroke)
                drawRoundRect(color, Offset(cell + gap, 0f), androidx.compose.ui.geometry.Size(cell, cell), corner, style = stroke)
                drawRoundRect(color, Offset(0f, cell + gap), androidx.compose.ui.geometry.Size(cell, cell), corner, style = stroke)
                drawRoundRect(color, Offset(cell + gap, cell + gap), androidx.compose.ui.geometry.Size(cell, cell), corner, style = stroke)
            }
            MainTab.STUDIO -> {
                drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width * .19f, size.height * .19f), Offset(size.width * .81f, size.height * .81f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width * .81f, size.height * .19f), Offset(size.width * .19f, size.height * .81f), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
