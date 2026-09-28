package app.sinshield

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sinshield.ui.theme.SwitzerHeavyFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val LoadingBackground = Color(0xFFE5EFFF)
private val LoadingInk = Color(0xFF082D48)
private val LoadingBlue = Color(0xFF087CF0)

@Composable
fun SinShieldLoadingScreen(
    readyToFinish: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    val letterAlpha = remember { Animatable(0f) }
    val assemblyProgress = remember { Animatable(0f) }
    val dotProgress = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }
    var animationFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        letterAlpha.animateTo(1f, tween(durationMillis = 280))
        delay(120)
        assemblyProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
        delay(90)
        dotProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        )
        delay(320)
        animationFinished = true
    }

    LaunchedEffect(animationFinished, readyToFinish) {
        if (!animationFinished || !readyToFinish) return@LaunchedEffect
        screenAlpha.animateTo(0f, tween(durationMillis = 220))
        latestOnFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LoadingBackground)
            .alpha(screenAlpha.value)
            .clearAndSetSemantics { contentDescription = "SinShield" },
        contentAlignment = Alignment.Center
    ) {
        AnimatedWordmark(
            letterAlpha = letterAlpha.value,
            assemblyProgress = assemblyProgress.value,
            dotProgress = dotProgress.value
        )
    }
}

@Composable
private fun AnimatedWordmark(
    letterAlpha: Float,
    assemblyProgress: Float,
    dotProgress: Float
) {
    val supportingLetterAlpha = ((assemblyProgress - 0.12f) / 0.88f).coerceIn(0f, 1f)
    val splitProgress = (assemblyProgress / 0.55f).coerceIn(0f, 1f)
    val wordCenteringProgress =
        ((assemblyProgress - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val dotScale = 0.55f + (0.45f * dotProgress)

    Layout(
        content = {
            WordmarkPart("S", LoadingInk, Modifier.alpha(letterAlpha))
            WordmarkPart("in", LoadingInk, Modifier.alpha(supportingLetterAlpha))
            WordmarkPart("S", LoadingBlue, Modifier.alpha(letterAlpha))
            WordmarkPart("hield", LoadingBlue, Modifier.alpha(supportingLetterAlpha))
            WordmarkPart(
                ".",
                LoadingBlue,
                Modifier.alpha(dotProgress).scale(dotScale)
            )
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val wordmarkWidthWithoutDot = placeables.take(4).sumOf { it.width }
        val completeWordmarkWidth = placeables.sumOf { it.width }
        val wordmarkHeight = placeables.maxOf { it.height }
        val layoutWidth = constraints.maxWidth
        val layoutHeight = wordmarkHeight
        val screenCenter = layoutWidth / 2
        val firstSCenterInWordmark = placeables[0].width / 2
        val secondSCenterInWordmark =
            placeables[0].width + placeables[1].width + placeables[2].width / 2
        val midpointBetweenFinalSs =
            (firstSCenterInWordmark + secondSCenterInWordmark) / 2
        val splitWordmarkStart = screenCenter - midpointBetweenFinalSs
        val centeredWordmarkStart = (layoutWidth - wordmarkWidthWithoutDot) / 2
        val centeredCompleteWordmarkStart = (layoutWidth - completeWordmarkWidth) / 2

        val finalPositions = IntArray(placeables.size)
        var nextX = splitWordmarkStart
        placeables.forEachIndexed { index, placeable ->
            finalPositions[index] = nextX
            nextX += placeable.width
        }

        val openingGap = 4.dp.roundToPx()
        // The pair starts exactly in the middle of the display. Their final positions
        // straddle that same center, so each S only travels outward and never crosses.
        val firstSStart = screenCenter - openingGap / 2 - placeables[0].width
        val secondSStart = screenCenter + openingGap / 2
        val centeringOffset =
            (centeredWordmarkStart - splitWordmarkStart) * wordCenteringProgress
        val dotCenteringOffset =
            (centeredCompleteWordmarkStart - centeredWordmarkStart) * dotProgress

        layout(layoutWidth, layoutHeight) {
            placeables.forEachIndexed { index, placeable ->
                val splitX = when (index) {
                    0 -> lerp(firstSStart, finalPositions[index], splitProgress)
                    2 -> lerp(secondSStart, finalPositions[index], splitProgress)
                    else -> finalPositions[index]
                }
                val x = (splitX + centeringOffset + dotCenteringOffset).roundToInt()
                placeable.placeRelative(x, (layoutHeight - placeable.height) / 2)
            }
        }
    }
}

@Composable
private fun WordmarkPart(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontFamily = SwitzerHeavyFontFamily,
        fontSize = 48.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.9).sp,
        maxLines = 1,
        modifier = modifier
    )
}

private fun lerp(start: Int, end: Int, progress: Float): Int =
    (start + (end - start) * progress).roundToInt()
