package com.riad.guitartuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riad.guitartuner.TunerUiState
import com.riad.guitartuner.music.Tuning
import com.riad.guitartuner.ui.theme.Close
import com.riad.guitartuner.ui.theme.Far
import com.riad.guitartuner.ui.theme.InTune
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val GAUGE_RANGE = 50f

private fun colorFor(cents: Double?): Color = when {
    cents == null -> Color.Gray
    abs(cents) <= TunerUiState.IN_TUNE_CENTS -> InTune
    abs(cents) <= 15 -> Close
    else -> Far
}

@Composable
fun TunerScreen(
    state: TunerUiState,
    onSelectTuning: (Tuning) -> Unit,
    onToggleString: (Int) -> Unit,
    onChangeA4: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(state, onSelectTuning, onChangeA4)
            Spacer(Modifier.weight(1f))
            NoteDisplay(state)
            Spacer(Modifier.height(8.dp))
            Gauge(state.cents, Modifier.fillMaxWidth().aspectRatio(2f))
            StatusText(state, onRetry)
            Spacer(Modifier.weight(1f))
            if (!state.tuning.isChromatic) {
                StringSelector(state, onToggleString)
                Text(
                    if (state.lockedString == null) "Mode automatique · touchez une corde pour la verrouiller"
                    else "Corde verrouillée · touchez-la à nouveau pour le mode auto",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(state: TunerUiState, onSelectTuning: (Tuning) -> Unit, onChangeA4: (Int) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { menuOpen = true }) {
                Text(state.tuning.name, maxLines = 1)
                Text("  ▾")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Tuning.ALL.forEach { tuning ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                tuning.name,
                                fontWeight = if (tuning == state.tuning) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onSelectTuning(tuning)
                        },
                    )
                }
            }
        }
        FilledTonalIconButton(onClick = { onChangeA4(-1) }, modifier = Modifier.size(36.dp)) { Text("−") }
        Text(
            "La ${state.a4} Hz",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        FilledTonalIconButton(onClick = { onChangeA4(1) }, modifier = Modifier.size(36.dp)) { Text("+") }
    }
}

@Composable
private fun NoteDisplay(state: TunerUiState) {
    val color by animateColorAsState(colorFor(state.cents), label = "noteColor")
    val note = state.targetNote
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            note?.name ?: "–",
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = if (state.cents == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
        )
        if (note != null) {
            Text(
                "${note.octave}",
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp, start = 2.dp),
            )
        }
    }
    Text(
        note?.solfege ?: " ",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        state.frequency?.let { String.format(Locale.FRANCE, "%.1f Hz", it) } ?: " ",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun Gauge(cents: Double?, modifier: Modifier = Modifier) {
    val target = (cents?.toFloat() ?: 0f).coerceIn(-GAUGE_RANGE, GAUGE_RANGE)
    val animated by animateFloatAsState(
        target,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val needleColor by animateColorAsState(colorFor(cents), label = "needleColor")
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height * 0.95f)
        val radius = minOf(size.width / 2, size.height) * 0.9f
        val sweep = 140f
        val start = 270f - sweep / 2
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)

        drawArc(trackColor, start, sweep, false, topLeft, arcSize, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
        // Zone « accordé » (±5 cents)
        val zone = sweep * (TunerUiState.IN_TUNE_CENTS.toFloat() / GAUGE_RANGE) / 2
        drawArc(InTune.copy(alpha = 0.6f), 270f - zone, zone * 2, false, topLeft, arcSize, style = Stroke(14.dp.toPx()))

        fun angleFor(c: Float) = Math.toRadians((270f + c / GAUGE_RANGE * sweep / 2).toDouble())

        for (c in -50..50 step 5) {
            val a = angleFor(c.toFloat())
            val major = c % 25 == 0
            val inner = radius - (if (major) 34 else 24).dp.toPx()
            val outer = radius - 14.dp.toPx()
            drawLine(
                tickColor.copy(alpha = if (major) 0.9f else 0.4f),
                Offset(center.x + inner * cos(a).toFloat(), center.y + inner * sin(a).toFloat()),
                Offset(center.x + outer * cos(a).toFloat(), center.y + outer * sin(a).toFloat()),
                strokeWidth = (if (major) 3 else 2).dp.toPx(),
            )
        }

        if (cents != null) {
            val a = angleFor(animated)
            val len = radius - 20.dp.toPx()
            drawLine(
                needleColor,
                center,
                Offset(center.x + len * cos(a).toFloat(), center.y + len * sin(a).toFloat()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(if (cents != null) needleColor else tickColor, 9.dp.toPx(), center)
    }
}

@Composable
private fun StatusText(state: TunerUiState, onRetry: () -> Unit) {
    val cents = state.cents
    if (state.error != null) {
        Text("Micro indisponible : ${state.error}", color = Far, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("Réessayer") }
        return
    }
    val (text, color) = when {
        cents == null -> "Jouez une corde…" to MaterialTheme.colorScheme.onSurfaceVariant
        state.inTune -> "Accordé ✓" to InTune
        cents < 0 -> String.format(Locale.FRANCE, "Trop bas (%+.0f ¢) · tendez la corde", cents) to colorFor(cents)
        else -> String.format(Locale.FRANCE, "Trop haut (%+.0f ¢) · détendez la corde", cents) to colorFor(cents)
    }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun StringSelector(state: TunerUiState, onToggleString: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        state.tuning.strings.forEachIndexed { index, note ->
            val active = state.targetString == index && state.cents != null
            val locked = state.lockedString == index
            val bg by animateColorAsState(
                when {
                    active && state.inTune -> InTune
                    active -> colorFor(state.cents).copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                label = "stringBg",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(bg, CircleShape)
                        .border(
                            width = if (locked) 2.dp else 0.dp,
                            color = if (locked) MaterialTheme.colorScheme.secondary else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable { onToggleString(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        note.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (active && state.inTune) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    "${6 - index}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
