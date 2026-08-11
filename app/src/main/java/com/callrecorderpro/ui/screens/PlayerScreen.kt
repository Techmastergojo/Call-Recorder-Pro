package com.callrecorderpro.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.platform.LocalContext
import com.callrecorderpro.data.Direction
import com.callrecorderpro.data.RecordingItem
import com.callrecorderpro.data.RecordingType
import com.callrecorderpro.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PlayerScreen(
    item: RecordingItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().also { player ->
            player.setMediaItem(MediaItem.fromUri(item.fileUri))
            player.prepare()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    val durationMs = (item.durationSeconds * 1000L).coerceAtLeast(1L)

    // Poll playback position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition
            progress = (exoPlayer.currentPosition.toFloat() / durationMs).coerceIn(0f, 1f)
            if (!exoPlayer.isPlaying) isPlaying = false
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Pulsing animation when playing
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0D2137), NavyDeep, Color(0xFF050E1A)))
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Back Button ───────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = SoftWhite)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Caller Avatar (big) ───────────────────────────────────────
            val gradient = when (item.type) {
                RecordingType.WHATSAPP -> Brush.linearGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E)))
                RecordingType.SIM      -> Brush.linearGradient(listOf(ElectricBlue, CyanGlow))
            }
            Box(
                modifier = Modifier
                    .size(if (isPlaying) (120 * pulseScale).dp else 120.dp)
                    .clip(CircleShape)
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                if (item.type == RecordingType.WHATSAPP) {
                    Icon(Icons.Default.Phone, null, tint = Color.White, modifier = Modifier.size(56.dp))
                } else {
                    Text(
                        item.displayName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 52.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Caller Name ───────────────────────────────────────────────
            Text(item.displayName, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text(item.phoneNumber, color = SubtleGray, fontSize = 15.sp)

            Spacer(Modifier.height(8.dp))

            // ── Type + Direction ──────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(item.type.label(), when(item.type) {
                    RecordingType.WHATSAPP -> Color(0xFF25D366)
                    RecordingType.SIM -> ElectricBlue
                })
                Badge(item.direction.label(), when(item.direction) {
                    Direction.INCOMING -> SuccessGreen
                    Direction.OUTGOING -> WarnAmber
                    Direction.UNKNOWN -> SubtleGray
                })
            }

            Spacer(Modifier.height(6.dp))
            Text(
                SimpleDateFormat("EEEE, MMM d · h:mm a", Locale.getDefault()).format(Date(item.timestampMs)),
                color = SubtleGray, fontSize = 13.sp
            )

            Spacer(Modifier.weight(1f))

            // ── Waveform Placeholder ──────────────────────────────────────
            WaveformVisualizer(isPlaying = isPlaying)

            Spacer(Modifier.height(16.dp))

            // ── Seekbar ───────────────────────────────────────────────────
            Slider(
                value = progress,
                onValueChange = { p ->
                    progress = p
                    exoPlayer.seekTo((p * durationMs).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = CyanGlow,
                    activeTrackColor = ElectricBlue,
                    inactiveTrackColor = NavySurface
                )
            )

            // ── Time labels ───────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(currentPositionMs), color = SubtleGray, fontSize = 12.sp)
                Text(formatMs(durationMs), color = SubtleGray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))

            // ── Controls ──────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip back 10s
                IconButton(onClick = {
                    exoPlayer.seekTo(maxOf(0L, exoPlayer.currentPosition - 10_000L))
                }) {
                    Icon(Icons.Default.Replay10, null, tint = SubtleGray, modifier = Modifier.size(32.dp))
                }

                // Play / Pause
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(ElectricBlue, CyanGlow))),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause(); isPlaying = false
                        } else {
                            exoPlayer.play(); isPlaying = true
                        }
                    }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Skip forward 10s
                IconButton(onClick = {
                    exoPlayer.seekTo(minOf(durationMs, exoPlayer.currentPosition + 10_000L))
                }) {
                    Icon(Icons.Default.Forward10, null, tint = SubtleGray, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WaveformVisualizer(isPlaying: Boolean) {
    val bars = 28
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { i ->
            val duration = remember(isPlaying) { if (isPlaying) (300..700).random() else 1000 }
            val targetVal = remember(isPlaying) { if (isPlaying) (8..40).random().toFloat() else 4f }
            val animatedHeight by rememberInfiniteTransition(label = "wave_$i").animateFloat(
                initialValue = 4f,
                targetValue = targetVal,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(listOf(CyanGlow, ElectricBlue))
                    )
            )
        }
    }
}

@Composable
private fun Badge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
