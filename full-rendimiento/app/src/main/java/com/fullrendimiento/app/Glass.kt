package com.fullrendimiento.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// Paleta
val Violet = Color(0xFF7C5CFF)
val Cyan = Color(0xFF22D3EE)
val Pink = Color(0xFFF472B6)
val Ink = Color(0xFF0A0B20)
val TextMain = Color(0xFFF4F6FF)
val TextSub = Color(0xFFC0C7EA)
val TextFaint = Color(0xFF8C94BF)
val Ok = Color(0xFF34D399)
val Warn = Color(0xFFFBBF24)
val Bad = Color(0xFFF87171)
val AccentBrush = Brush.linearGradient(listOf(Violet, Color(0xFF5B6CFF), Cyan))

/** Fondo oscuro con manchas de color difuminadas que se mueven lentamente. */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "phase"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0A24), Color(0xFF07152A))))
    ) {
        Canvas(Modifier.fillMaxSize().blur(90.dp)) {
            val w = size.width
            val h = size.height
            val r = size.minDimension
            fun blob(c: Color, x: Float, y: Float, rad: Float, k: Float) = drawCircle(
                c, rad * r,
                Offset(w * x + cos(phase + k) * w * 0.06f, h * y + sin(phase + k) * h * 0.04f)
            )
            blob(Violet.copy(alpha = 0.75f), 0.10f, 0.10f, 0.55f, 0f)
            blob(Cyan.copy(alpha = 0.55f), 0.95f, 0.32f, 0.45f, 2f)
            blob(Pink.copy(alpha = 0.40f), 0.15f, 0.72f, 0.45f, 4f)
            blob(Color(0xFF3B82F6).copy(alpha = 0.45f), 0.85f, 0.95f, 0.50f, 1f)
        }
        content()
    }
}

/** Tarjeta de vidrio esmerilado. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    padding: Int = 18,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(0.14f), Color.White.copy(0.06f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.38f), Color.White.copy(0.07f))), shape)
            .padding(padding.dp),
        content = content
    )
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)

@Composable
fun Sub(text: String, modifier: Modifier = Modifier, color: Color = TextSub, size: Int = 13, center: Boolean = false) =
    Text(
        text, modifier, color = color, fontSize = size.sp, lineHeight = (size + 5).sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start
    )

/** Interruptor estilo vidrio. */
@Composable
fun GlassSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f), label = "x")
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .size(52.dp, 30.dp)
            .clip(CircleShape)
            .background(
                if (checked) AccentBrush
                else Brush.linearGradient(listOf(Color.White.copy(0.12f), Color.White.copy(0.12f)))
            )
            .border(1.dp, Color.White.copy(if (checked) 0.35f else 0.22f), CircleShape)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = (22 * x).dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** Fila de una optimizacion: icono, texto e interruptor. */
@Composable
fun OptionRow(emoji: String, title: String, sub: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBubble(emoji)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Sub(sub, size = 12)
        }
        Spacer(Modifier.width(10.dp))
        GlassSwitch(checked, enabled, onChange)
    }
}

@Composable
fun EmojiBubble(emoji: String, size: Int = 40) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.32f).dp))
            .background(Color.White.copy(0.10f))
            .border(1.dp, Color.White.copy(0.18f), RoundedCornerShape((size * 0.32f).dp)),
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = (size * 0.48f).sp) }
}

/** Selector de opciones tipo pastilla. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onPick: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(Color.White.copy(0.08f))
            .border(1.dp, Color.White.copy(0.18f), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(if (sel) AccentBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable(enabled = enabled && !sel) { onPick(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, color = if (sel) Color.White else TextSub, fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/** Boton secundario de vidrio. */
@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(Color.White.copy(0.12f))
            .border(1.dp, Color.White.copy(0.30f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

/** Boton principal grande con degradado y brillo que "late". */
@Composable
fun BoostButton(text: String, sub: String, enabled: Boolean, active: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "boost")
    val pulse by t.animateFloat(
        0.92f, 1.06f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse"
    )
    val glow by animateColorAsState(if (active) Ok else Violet, label = "glow")
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        if (enabled) {
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(62.dp)
                    .scale(pulse)
                    .blur(28.dp)
                    .clip(CircleShape)
                    .background(glow.copy(0.75f))
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(CircleShape)
                .background(
                    if (active) Brush.linearGradient(listOf(Color(0xFF10B981), Cyan)) else AccentBrush
                )
                .border(1.dp, Color.White.copy(0.45f), CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(sub, color = Color.White.copy(0.85f), fontSize = 12.sp)
        }
    }
}

/** Pastilla pequena de estado (temperatura, Shizuku...). */
@Composable
fun Chip(text: String, color: Color) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(0.16f))
            .border(1.dp, color.copy(0.45f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
