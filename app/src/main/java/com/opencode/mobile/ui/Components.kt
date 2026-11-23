package com.opencode.mobile.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.mobile.data.ConnectionState
import com.opencode.mobile.ui.theme.Oc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

@Composable
fun OcTopBar(
    title: String,
    subtitle: String,
    onMenu: () -> Unit,
    connectionState: ConnectionState = ConnectionState.CONNECTED,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Oc.c
    val pulse = rememberInfiniteTransition(label = "dot")
    val a by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")

    val dotColor = when (connectionState) {
        ConnectionState.CONNECTED -> c.success
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Color(0xFFFF9500)
        ConnectionState.OFFLINE -> c.danger
    }

    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Menu, null, tint = c.text, modifier = Modifier.size(28.dp))
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-1).dp)
                    .size(11.dp)
                    .alpha(if (connectionState == ConnectionState.CONNECTED) 1f else a)
                    .background(dotColor, CircleShape)
            )
        }
        Spacer(Modifier.width(14.dp))
        AnimatedContent(
            targetState = title to subtitle,
            modifier = Modifier.weight(1f),
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(120)) },
            label = "title"
        ) { (t, s) ->
            Column {
                Text(t, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s, color = c.textDim, fontSize = 16.sp, maxLines = 1)
            }
        }
        trailing()
    }
}

/** ⋮ button wrapped in a ring that spins while the agent is running. */
@Composable
fun ProgressMenuButton(running: Boolean, onClick: () -> Unit) {
    val c = Oc.c
    val rot by rememberInfiniteTransition(label = "ring").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rot"
    )
    Box(Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 3.dp.toPx()
            val st = Stroke(w, cap = StrokeCap.Round)
            val inset = w / 2
            drawCircle(c.surfaceHigh, radius = size.minDimension / 2 - inset, style = st)
            if (running) drawArc(
                c.text, rot, 75f, false, style = st,
                topLeft = Offset(inset, inset), size = Size(size.width - w, size.height - w)
            )
        }
        Icon(Icons.Rounded.MoreVert, null, tint = c.text, modifier = Modifier.size(24.dp))
    }
}

/** Dotted audio waveform used in composer (recording) and voice settings. */
@Composable
fun DottedWave(modifier: Modifier, active: Boolean) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "p")
    val amp by animateFloatAsState(if (active) 1f else 0.04f, tween(450), label = "amp")
    Canvas(modifier) {
        val cols = 56
        val rows = 11
        val dx = size.width / cols
        val dy = size.height / rows
        val r = min(dx, dy) * 0.32f
        for (i in 0 until cols) {
            val x = (i + 0.5f) * dx
            val nx = (i.toFloat() / cols) * 2f - 1f
            val envelope = exp(-nx * nx * 3.5f)
            val s = sin(nx * 7f + phase) * amp * envelope
            for (j in 0 until rows) {
                val y = (j + 0.5f) * dy
                val ny = (j.toFloat() / rows) * 2f - 1f
                val dist = abs(ny - s * 0.85f)
                val alpha = (1f - (dist * 1.5f)).coerceIn(0.12f, 1f)
                val color = if (dist < 0.28f && active) c.text else c.textDim.copy(alpha = alpha * 0.7f)
                drawCircle(color, r, Offset(x, y))
            }
        }
    }
}

/** Bouncing 3-dot spinner for "Working" state. */
@Composable
fun DotSpinner(modifier: Modifier = Modifier) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "spin")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0, 160, 320).forEach { delay ->
            val a by t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(700, delayMillis = delay, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "d"
            )
            Box(Modifier.size(8.dp).alpha(a).background(c.text, CircleShape))
        }
    }
}

/** Custom iOS-style toggle using our theme tokens. */
@Composable
fun OcSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Oc.c
    val thumbOffset by animateFloatAsState(if (checked) 24f else 0f, tween(200), label = "sw")
    val bg by animateColorAsState(if (checked) c.text else c.surfaceSel, tween(200), label = "swb")
    Box(
        modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset.dp)
                .size(24.dp)
                .background(if (checked) c.bg else c.textDim, CircleShape)
        )
    }
}
