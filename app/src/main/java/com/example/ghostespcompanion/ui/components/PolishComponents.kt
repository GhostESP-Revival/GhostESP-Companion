package com.example.ghostespcompanion.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.ghostespcompanion.R
import com.example.ghostespcompanion.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.max

object HapticType {
    const val LIGHT = 1
    const val MEDIUM = 2
    const val HEAVY = 3
    const val SUCCESS = 4
    const val ERROR = 5
}

fun performHaptic(
    context: android.content.Context,
    type: Int = HapticType.LIGHT
) {
    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (!vibrator.hasVibrator()) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = when (type) {
            HapticType.LIGHT -> VibrationEffect.createOneShot(10, 50)
            HapticType.MEDIUM -> VibrationEffect.createOneShot(15, 100)
            HapticType.HEAVY -> VibrationEffect.createOneShot(25, 200)
            HapticType.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 10, 50, 10), intArrayOf(0, 100, 0, 50), -1)
            HapticType.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 20, 30, 20, 30, 20), intArrayOf(0, 100, 0, 100, 0, 100), -1)
            else -> VibrationEffect.createOneShot(10, 50)
        }
        vibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(when (type) {
            HapticType.LIGHT -> 10L
            HapticType.MEDIUM -> 15L
            HapticType.HEAVY -> 25L
            else -> 10L
        })
    }
}

@Composable
fun rememberHapticController(): (Int) -> Unit {
    val context = LocalContext.current
    return remember { { type -> performHaptic(context, type) } }
}

@Composable
fun Modifier.shimmer(
    shimmerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return this.drawBehind {
        val shimmerWidth = size.width * 0.6f
        val startOffset = translateAnim.value - shimmerWidth
        
        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                shimmerColor,
                Color.Transparent
            ),
            start = Offset(startOffset, 0f),
            end = Offset(startOffset + shimmerWidth, 0f)
        )
        drawRect(brush = brush)
    }
}

@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    showIcon: Boolean = true,
    showLines: Int = 2
) {
    val shape = RoundedCornerShape(8.dp)
    
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .shimmer()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showIcon) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(showLines) { index ->
                    val widthFraction = if (index == 0) 0.7f else 0.5f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(widthFraction)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
    itemHeight: Dp = 80.dp,
    showIcons: Boolean = true,
    showLines: Int = 2,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(itemCount) {
            SkeletonCard(
                modifier = Modifier.fillMaxWidth(),
                height = itemHeight,
                showIcon = showIcons,
                showLines = showLines
            )
        }
    }
}

@Composable
fun StaggeredAnimatedItem(
    index: Int,
    modifier: Modifier = Modifier,
    staggerDelayMs: Int = 50,
    content: @Composable () -> Unit
) {
    // Use rememberSaveable to persist visibility across recompositions (scroll)
    // Items only animate once, then stay visible
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    var visible by remember { mutableStateOf(hasAnimated) }
    
    LaunchedEffect(index) {
        if (!hasAnimated) {
            // Only stagger for first 8 items, instant for rest (scrolling)
            if (index < 8) {
                delay((index * staggerDelayMs).toLong())
            }
            visible = true
            hasAnimated = true
        }
    }
    
    if (visible || hasAnimated) {
        Box(modifier = modifier) {
            content()
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                animationSpec = tween(150, easing = FastOutSlowInEasing)
            ) + slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = tween(150, easing = FastOutSlowInEasing)
            ),
            modifier = modifier
        ) {
            content()
        }
    }
}

@Composable
fun ScaleOnPress(
    pressed: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 0.98f,
    content: @Composable () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    Box(modifier = modifier.scale(animatedScale)) {
        content()
    }
}

@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
    scale: Float = 0.98f,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = rememberHapticController()
    
    ScaleOnPress(
        pressed = isPressed,
        scale = scale
    ) {
        Surface(
            onClick = {
                if (hapticEnabled) haptic(HapticType.LIGHT)
                onClick()
            },
            modifier = modifier,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            content()
        }
    }
}

@Composable
fun GhostSpinner(
    modifier: Modifier = Modifier,
    color: Color = primaryColor(),
    strokeWidth: Dp = 3.dp,
    size: Dp = 32.dp
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val sweepAngle by transition.animateFloat(
        initialValue = 90f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }
    val sizePx = with(LocalDensity.current) { size.toPx() }
    
    Canvas(
        modifier = modifier.size(size)
    ) {
        rotate(rotation) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidthPx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                ),
                size = Size(sizePx, sizePx),
                topLeft = Offset(0f, 0f)
            )
        }
    }
}

@Composable
fun GhostDotsSpinner(
    modifier: Modifier = Modifier,
    color: Color = primaryColor(),
    dotSize: Dp = 8.dp,
    spacing: Dp = 6.dp
) {
    val transition = rememberInfiniteTransition(label = "dots")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 100
            val scale by transition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha$index"
            )
            
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun ConnectionPulse(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    pulseColor: Color = successColor(),
    size: Dp = 12.dp
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 0.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(size * pulseScale)
                    .clip(CircleShape)
                    .background(pulseColor.copy(alpha = pulseAlpha * 0.3f))
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.7f)
                .clip(CircleShape)
                .background(if (isConnected) pulseColor else MaterialTheme.colorScheme.outline)
        )
    }
}

@Composable
fun SkeletonWifiApCard(
    modifier: Modifier = Modifier
) {
    BrutalistCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        backgroundColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            )
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp, 20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
fun SkeletonDashboard(
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonCard(
                modifier = Modifier.fillMaxWidth(),
                height = 100.dp,
                showIcon = true,
                showLines = 2
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.header_quick_links),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmer()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.header_quick_actions),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        item {
            SkeletonCard(
                modifier = Modifier.fillMaxWidth(),
                height = 60.dp,
                showIcon = false,
                showLines = 1
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.header_channel_congestion),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        item {
            SkeletonCard(
                modifier = Modifier.fillMaxWidth(),
                height = 150.dp,
                showIcon = false,
                showLines = 0
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.header_recent_wifi),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        items(3) {
            SkeletonWifiApCard()
        }
    }
}

/**
 * Animated content for bottom sheets with spring physics
 * Wrap your bottom sheet content in this for a polished feel
 */
@Composable
fun AnimatedSheetContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ),
        modifier = modifier
    ) {
        Column {
            content()
        }
    }
}

/**
 * Hook to trigger haptic on connection state changes
 */
@Composable
fun ConnectionHapticEffect(
    isConnected: Boolean,
    wasConnected: Boolean
) {
    val haptic = rememberHapticController()
    
    LaunchedEffect(isConnected) {
        if (isConnected && !wasConnected) {
            haptic(HapticType.SUCCESS)
        } else if (!isConnected && wasConnected) {
            haptic(HapticType.LIGHT)
        }
    }
}

/**
 * Extension to remember previous value
 */
@Composable
fun <T> rememberPrevious(value: T): T? {
    var previousValue by remember { mutableStateOf<T?>(null) }
    androidx.compose.runtime.SideEffect {
        previousValue = value
    }
    return previousValue
}
