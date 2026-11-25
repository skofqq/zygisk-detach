// Added by skofqq in 2026. Part of a modified version of j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

/**
 * A page that slides over the one below it, so the state underneath (pending switches) survives.
 * Predictive back shrinks it toward the swipe and reveals the page below.
 *
 * Back handlers registered later win, so a page placed after another one in the composition (or
 * nested in it) closes first.
 */
@Composable
fun OverlayPage(visible: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    val backProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    LaunchedEffect(visible) { if (visible) backProgress.snapTo(0f) }
    PredictiveBackHandler(enabled = visible) { events ->
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                backProgress.snapTo(event.progress)
            }
            onBack()
        } catch (_: CancellationException) {
            backProgress.animateTo(0f)
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.graphicsLayer {
                val progress = backProgress.value
                val scale = 1f - 0.1f * progress
                scaleX = scale
                scaleY = scale
                // drift away from the edge the swipe started at, by up to 8% of the width
                val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
                translationX = direction * progress * size.width * 0.08f
                shape = RoundedCornerShape(32.dp * progress)
                clip = progress > 0f
            }
        ) {
            content()
        }
    }
}
