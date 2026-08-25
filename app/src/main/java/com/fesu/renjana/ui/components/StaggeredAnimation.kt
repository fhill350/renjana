package com.fesu.renjana.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Wraps content with staggered entrance animation.
 * Item slides up + fades in with a delay based on its index.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    contentDelay: Int = 50,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val contentResolver = LocalView.current.context.contentResolver
    val animationsEnabled = android.provider.Settings.Global.getFloat(
        contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) > 0f
    val alpha = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    val offsetY = remember { Animatable(if (animationsEnabled) 20f else 0f) }

    LaunchedEffect(index, animationsEnabled) {
        if (!animationsEnabled) {
            alpha.snapTo(1f)
            offsetY.snapTo(0f)
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay((index * contentDelay).toLong())
        alpha.animateTo(1f, tween(300))
        offsetY.animateTo(0f, tween(300))
    }

    Box(
        modifier = modifier
            .alpha(alpha.value)
            .offset { IntOffset(0, offsetY.value.toInt()) }
    ) {
        content()
    }
}
