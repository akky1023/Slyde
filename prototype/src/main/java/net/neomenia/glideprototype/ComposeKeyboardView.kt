package net.neomenia.glideprototype

import android.content.Context
import android.text.Spanned
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compose owns drawing; the parent keeps receiving the original multi-pointer MotionEvents. */
class ComposeKeyboardView(context: Context, private val keyboard: GlideKeyboardView) : AbstractComposeView(context) {
    private var appearance by mutableStateOf(keyboard.visualState())

    fun refresh() { appearance = keyboard.visualState() }

    @Composable
    override fun Content() {
        SettingsTheme { KeyboardSurface(appearance) }
    }
}

@Composable
private fun KeyboardSurface(state: GlideKeyboardView.VisualState) {
    val context = LocalContext.current
    val modeMetrics = remember(context, state.typeface()) { ImeFonts.modeLabelMetrics(context, state.typeface()) }
    val colors = MaterialTheme.colorScheme
    val latin = state.page() == 0 && !state.japanese()
    val neutralInk = if (colors.surface.luminance() > .5f) Color.Black else Color.White
    val ink = if (latin) colors.primary else neutralInk
    Surface(color = colors.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints {
            val leftWidth = maxWidth * (state.leftPercent() / 100f)
            val rightWidth = maxWidth * (state.rightPercent() / 100f)
            val cellWidth = (maxWidth - leftWidth - rightWidth) / 3
            val rowHeight = maxHeight / 4
            val scale = if (state.scaleTextToFit()) minOf(1f, cellWidth / 80.dp, rowHeight / 58.dp).coerceAtLeast(.65f) else 1f
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(leftWidth).fillMaxHeight()) {
                    SideKey(Modifier.weight(1f), if (latin) {
                        when {
                            state.caps() -> R.drawable.ic_caps_lock_on
                            state.upper() -> R.drawable.ic_shift_on
                            else -> R.drawable.ic_shift
                        }
                    } else R.drawable.ic_more, neutralInk)
                    SideKey(Modifier.weight(1f), R.drawable.ic_cursor_left, neutralInk)
                    Box(Modifier.weight(2f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val kanaSize = if (state.japanese()) 26f else 14f
                        val latinSize = (if (state.japanese()) 14f else 26f) * modeMetrics.latinScale()
                        val modeWidth = kanaSize * modeMetrics.kanaWidth() + latinSize * modeMetrics.latinWidth() + 1f
                        val modeScale = minOf(1f, (leftWidth.value - 4f).coerceAtLeast(1f) / modeWidth)
                        Row(modifier = Modifier.clearAndSetSemantics {
                            contentDescription = if (state.japanese()) "日本語入力モード" else "英語入力モード"
                        }, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            KeyText("あ", kanaSize * modeScale,
                                neutralInk.copy(alpha = if (state.japanese()) 1f else .38f), state, Modifier.alignByBaseline())
                            KeyText("a", latinSize * modeScale,
                                neutralInk.copy(alpha = if (state.japanese()) .38f else 1f), state, Modifier.alignByBaseline())
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    repeat(4) { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            repeat(3) { col ->
                                val key = state.keys()[row * 3 + col]
                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (key.active()) colors.primaryContainer else colors.surfaceContainerHighest,
                                    contentColor = ink,
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        KeyText(key.label(), (if (key.compact()) 18f else 28f) * scale,
                                            ink, state, Modifier.align(Alignment.Center))
                                        val alignments = listOf(Alignment.CenterStart, Alignment.TopCenter, Alignment.CenterEnd, Alignment.BottomCenter)
                                        key.hints().forEachIndexed { direction, hint ->
                                            if (hint.isNotEmpty()) KeyText(hint, 10f * maxOf(.85f, scale),
                                                ink, state,
                                                Modifier.align(alignments[direction]).padding(horizontal = 3.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.width(rightWidth).fillMaxHeight()) {
                    listOf(R.drawable.ic_delete, R.drawable.ic_cursor_right, R.drawable.ic_space, R.drawable.ic_enter)
                        .forEachIndexed { row, icon ->
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                SideKey(Modifier.fillMaxSize(), icon, neutralInk)
                                if (row == 2 && state.page() == 0 && state.japanese()) {
                                    KeyText("全角", 9f, neutralInk, state,
                                        Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
                                }
                            }
                        }
                }
            }
            // Paths use the same normalized 3 x 4 coordinates as the decoder.
            val pathColor = colors.tertiary
            Canvas(Modifier.fillMaxSize()) {
                val left = size.width * state.leftPercent() / 100f
                val cell = size.width * (100 - state.leftPercent() - state.rightPercent()) / 300f
                val row = size.height / 4
                state.paths().forEach { points ->
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = left + point.x() * cell
                        val y = point.y() * row
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, pathColor, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun SideKey(modifier: Modifier, icon: Int, tint: Color) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = tint,
                modifier = Modifier.size(minOf(26.dp, maxWidth * .6f, maxHeight * .6f)))
        }
    }
}

@Composable
private fun KeyText(value: String, size: Float, color: Color, state: GlideKeyboardView.VisualState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val face = state.typeface()
    val text = remember(value, face) {
        val styled = ImeFonts.styled(context, face, value)
        buildAnnotatedString {
            append(value)
            if (styled is Spanned) styled.getSpans(0, styled.length, ImeFonts.FallbackSpan::class.java).forEach { span ->
                addStyle(SpanStyle(fontFamily = FontFamily(span.typeface)), styled.getSpanStart(span), styled.getSpanEnd(span))
            }
        }
    }
    // Key labels stay within the hit geometry, as in the existing Canvas renderer.
    val fontSize = (size / LocalDensity.current.fontScale).sp
    // A body-sized line height adds space around small hints and pushes them toward the center.
    Text(text, modifier, color = color, fontSize = fontSize, lineHeight = fontSize,
        style = LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        fontFamily = FontFamily(face), maxLines = 1, softWrap = false)
}
