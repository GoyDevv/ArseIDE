package com.goydevv.arseide.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goydevv.arseide.resources.drawables
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material You (Material 3) launch screen for Arse IDE (`com.goydevv.arseide`).
 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val intro = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    val zoom = remember { Animatable(1f) }
    val exit = remember { Animatable(1f) }

    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        launch { intro.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing)) }
        coroutineScope {
            val creep =
                launch {
                    runCatching { progress.animateTo(0.9f, tween(durationMillis = 1800, easing = LinearEasing)) }
                }
            awaitAppIdle()
            creep.cancel()
        }
        progress.animateTo(1f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
        delay(140)
        coroutineScope {
            launch { zoom.animateTo(1.04f, tween(durationMillis = 320, easing = FastOutSlowInEasing)) }
            exit.animateTo(0f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
        }
        onFinish()
    }

    val status =
        when {
            progress.value >= 1f -> "Ready"
            progress.value >= 0.75f -> "Loading Horizon SDK & toolchains…"
            progress.value >= 0.4f -> "Loading Material You workspace…"
            else -> "Starting Arse IDE…"
        }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = exit.value }
                .background(scheme.background),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            modifier =
                Modifier.padding(horizontal = 32.dp)
                    .graphicsLayer {
                        val a = intro.value
                        alpha = a
                        val s = (0.94f + 0.06f * a) * zoom.value
                        scaleX = s
                        scaleY = s
                        translationY = (1f - a) * 16.dp.toPx()
                    },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 36.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    modifier = Modifier.size(68.dp),
                ) {
                    Icon(
                        painter = painterResource(drawables.folder_code),
                        contentDescription = "Arse IDE",
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Arse IDE",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                ) {
                    Text(
                        text = "Android · Fabric · Horizon .hmod",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.height(28.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.width(220.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
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
