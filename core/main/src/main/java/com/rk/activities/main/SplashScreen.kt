package com.rk.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branded Liquid-Glass IDE launch screen: the "XED PRO" wordmark and "by GoyDevv" badge fade/scale in
 * inside a subtle glassmorphic card over an ambient deep-indigo gradient backdrop, a loading progress
 * bar fills while the app loads behind us, then the whole screen gently zooms and fades away at 60fps.
 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val intro = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    val zoom = remember { Animatable(1f) }
    val exit = remember { Animatable(1f) }

    val bgTop = Color(0xFF0A0D14)
    val bgBottom = Color(0xFF101624)
    val accentCyan = Color(0xFF66E3F4)
    val accentBlue = Color(0xFFADC6FF)
    val fg = Color(0xFFEAF0FA)
    val muted = Color(0xFF94A0B8)
    val track = Color(0xFF1E2638)

    LaunchedEffect(Unit) {
        launch { intro.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing)) }
        coroutineScope {
            val creep =
                launch {
                    runCatching { progress.animateTo(0.9f, tween(durationMillis = 2400, easing = LinearEasing)) }
                }
            awaitAppIdle()
            creep.cancel()
        }
        progress.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        delay(200)
        coroutineScope {
            launch { zoom.animateTo(1.06f, tween(durationMillis = 420, easing = FastOutSlowInEasing)) }
            exit.animateTo(0f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
        onFinish()
    }

    val status =
        when {
            progress.value >= 1f -> "Ready"
            progress.value >= 0.75f -> "Almost ready…"
            progress.value >= 0.4f -> "Loading editor…"
            else -> "Preparing workspace…"
        }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = exit.value }
                .background(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF18243E), bgBottom, bgTop),
                            center = Offset(500f, 700f),
                            radius = 1200f,
                        )
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Ambient glow orb behind the glass card
        Box(
            modifier =
                Modifier.size(280.dp)
                    .clip(CircleShape)
                    .background(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(accentBlue.copy(alpha = 0.14f), Color.Transparent),
                            )
                    )
        )

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF151C2C).copy(alpha = 0.72f),
            border =
                BorderStroke(
                    width = 1.dp,
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0.25f),
                                    accentCyan.copy(alpha = 0.18f),
                                    Color.White.copy(alpha = 0.05f),
                                )
                        ),
                ),
            shadowElevation = 20.dp,
            modifier =
                Modifier.padding(horizontal = 32.dp)
                    .graphicsLayer {
                        val a = intro.value
                        alpha = a
                        val s = (0.92f + 0.08f * a) * zoom.value
                        scaleX = s
                        scaleY = s
                        translationY = (1f - a) * 20.dp.toPx()
                    },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 36.dp),
            ) {
                Text(
                    text = "XED PRO",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    letterSpacing = 6.sp,
                    color = fg,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentBlue.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, accentBlue.copy(alpha = 0.32f)),
                ) {
                    Text(
                        text = "by GoyDevv",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 2.5.sp,
                        color = accentBlue,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(34.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.width(220.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = accentCyan,
                    trackColor = track,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = muted,
                )
            }
        }
    }
}

private suspend fun awaitAppIdle(maxFrames: Int = 120, requiredSmooth: Int = 3, thresholdMs: Long = 22) {
    var lastFrame = 0L
    var smooth = 0
    var count = 0
    while (smooth < requiredSmooth && count < maxFrames) {
        val now = withFrameNanos { it }
        if (lastFrame != 0L) {
            val deltaMs = (now - lastFrame) / 1_000_000L
            if (deltaMs in 1..thresholdMs) smooth++ else smooth = 0
        }
        lastFrame = now
        count++
    }
}
