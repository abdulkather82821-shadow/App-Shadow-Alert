package com.shadowalert.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shadowalert.app.ui.theme.CyanBright
import com.shadowalert.app.ui.theme.NavyPrimary

@Composable
fun ShadowAlertLogo(
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    shieldColor: Color = NavyPrimary,
    accentColor: Color = CyanBright
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val shieldPath = Path().apply {
            moveTo(w * 0.50f, h * 0.08f)
            lineTo(w * 0.86f, h * 0.22f)
            cubicTo(
                w * 0.86f, h * 0.56f,
                w * 0.72f, h * 0.80f,
                w * 0.50f, h * 0.93f
            )
            cubicTo(
                w * 0.28f, h * 0.80f,
                w * 0.14f, h * 0.56f,
                w * 0.14f, h * 0.22f
            )
            close()
        }
        drawPath(path = shieldPath, color = shieldColor)

        val innerShield = Path().apply {
            moveTo(w * 0.50f, h * 0.15f)
            lineTo(w * 0.79f, h * 0.27f)
            cubicTo(
                w * 0.79f, h * 0.54f,
                w * 0.68f, h * 0.74f,
                w * 0.50f, h * 0.85f
            )
            cubicTo(
                w * 0.32f, h * 0.74f,
                w * 0.21f, h * 0.54f,
                w * 0.21f, h * 0.27f
            )
            close()
        }
        drawPath(
            path = innerShield,
            color = accentColor.copy(alpha = 0.35f),
            style = Stroke(width = w * 0.025f, join = StrokeJoin.Round)
        )

        val center = Offset(w * 0.50f, h * 0.49f)
        val radius = w * 0.19f
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = w * 0.048f)
        )

        drawLine(
            color = Color.White,
            start = center,
            end = Offset(center.x, center.y - radius * 0.58f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = accentColor,
            start = center,
            end = Offset(center.x + radius * 0.52f, center.y + radius * 0.26f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )

        drawCircle(
            color = accentColor,
            radius = w * 0.032f,
            center = Offset(w * 0.50f, h * 0.23f)
        )

        drawArc(
            color = accentColor.copy(alpha = 0.45f),
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 1.25f, center.y - radius * 1.25f),
            size = Size(radius * 2.5f, radius * 2.5f),
            style = Stroke(width = w * 0.02f, cap = StrokeCap.Round)
        )
    }
}
