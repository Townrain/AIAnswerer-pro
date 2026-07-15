package com.hwb.aianswerer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier {
    val scale = remember { Animatable(1f) }
    return this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    scale.snapTo(0.88f)
                    tryAwaitRelease()
                    scale.animateTo(1f, spring(dampingRatio = 0.2f, stiffness = 400f))
                },
                onTap = { onClick() }
            )
        }
}
