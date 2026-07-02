package com.guardian.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

@Composable
fun NeumorphismCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val shadowDark = if (isDark) Color(0xFF060B17) else Color(0xFFCBD5E1)
    val shadowLight = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.5f)
    val offset = elevation

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = offset, y = offset)
                .shadow(elevation, shape, clip = false, ambientColor = shadowDark, spotColor = shadowDark)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = -offset * 0.3f, y = -offset * 0.3f)
                .shadow(elevation * 0.4f, shape, clip = false, ambientColor = shadowLight, spotColor = shadowLight)
        )
        Box(
            modifier = Modifier
                .clip(shape)
                .drawBehind {
                    val highlightRadius = size.minDimension * 0.6f
                    drawCircle(
                        color = shadowLight.copy(alpha = shadowLight.alpha * 2f),
                        radius = highlightRadius,
                        center = Offset.Zero,
                        style = Fill
                    )
                }
                .drawBehind {
                    val cornerR = 20.dp.toPx()
                    val edgeW = 0.5.dp.toPx()
                    drawRoundRect(
                        color = if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.3f),
                        topLeft = Offset(edgeW, edgeW),
                        size = size.copy(
                            width = size.width - edgeW * 2,
                            height = size.height - edgeW * 2
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR),
                        style = Stroke(width = edgeW)
                    )
                }
                .background(surfaceColor)
                .padding(24.dp),
            content = content
        )
    }
}
