package com.callrecorderpro.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.callrecorderpro.data.Direction
import com.callrecorderpro.data.RecordingItem
import com.callrecorderpro.data.RecordingType
import com.callrecorderpro.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun RecordingCard(
    item: RecordingItem,
    onPlay: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onShare: (RecordingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recording") },
            text = { Text("Delete the recording with ${item.displayName}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete(item) }) {
                    Text("Delete", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
            containerColor = NavyMid
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            .clickable { onPlay(item) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMid),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Avatar / Icon ─────────────────────────────────────────────
            CallerAvatar(item)

            Spacer(Modifier.width(14.dp))

            // ── Call details ──────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                // Name + type badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SoftWhite,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    TypeBadge(item.type)
                }

                Spacer(Modifier.height(3.dp))

                // Phone number
                if (!item.phoneNumber.isNullOrBlank() && item.phoneNumber != item.displayName) {
                    Text(
                        text = item.phoneNumber,
                        fontSize = 12.sp,
                        color = SubtleGray
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Direction + date + duration row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DirectionChip(item.direction)
                    Text("·", color = SubtleGray, fontSize = 11.sp)
                    Text(
                        text = formatTimestamp(item.timestampMs),
                        fontSize = 11.sp,
                        color = SubtleGray
                    )
                    if (item.durationSeconds > 0) {
                        Text("·", color = SubtleGray, fontSize = 11.sp)
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = SubtleGray,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = formatDuration(item.durationSeconds),
                            fontSize = 11.sp,
                            color = SubtleGray
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // ── Action buttons ────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Play
                IconButton(
                    onClick = { onPlay(item) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.radialGradient(listOf(ElectricBlue, CyanGlow)),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Share
                IconButton(
                    onClick = { onShare(item) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share",
                        tint = SubtleGray, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(2.dp))
                // Delete
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete",
                        tint = DangerRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CallerAvatar(item: RecordingItem) {
    val gradient = when (item.type) {
        RecordingType.WHATSAPP -> Brush.linearGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E)))
        RecordingType.SIM      -> Brush.linearGradient(listOf(ElectricBlue, CyanGlow))
    }
    val initials = item.displayName.take(1).uppercase()

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        if (item.type == RecordingType.WHATSAPP) {
            Icon(Icons.Default.Phone, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun TypeBadge(type: RecordingType) {
    val (color, label) = when (type) {
        RecordingType.WHATSAPP -> Color(0xFF25D366) to "WA"
        RecordingType.SIM      -> ElectricBlue to "SIM"
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DirectionChip(direction: Direction) {
    val (icon, color, label) = when (direction) {
        Direction.INCOMING -> Triple(Icons.Default.CallReceived, SuccessGreen, "Incoming")
        Direction.OUTGOING -> Triple(Icons.Default.CallMade, WarnAmber, "Outgoing")
        Direction.UNKNOWN  -> Triple(Icons.Default.Call, SubtleGray, "Call")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(11.dp))
        Text(label, fontSize = 11.sp, color = color)
    }
}

private fun formatTimestamp(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
        diff < 7 * 86_400_000 -> SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(ms))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
