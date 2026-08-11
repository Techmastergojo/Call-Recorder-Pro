package com.callrecorderpro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorderpro.data.RecordingItem
import com.callrecorderpro.ui.components.RecordingCard
import com.callrecorderpro.ui.components.RecordingSearchBar
import com.callrecorderpro.ui.components.TimeFilterBar
import com.callrecorderpro.ui.theme.*
import com.callrecorderpro.viewmodel.RecordingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPlayer: (RecordingItem) -> Unit,
    viewModel: RecordingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Animated gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "gradient_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D2137),
                        NavyDeep,
                        Color(0xFF081523)
                    ),
                    center = Offset(offset * 400f, offset * 300f),
                    radius = 900f
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top App Bar ───────────────────────────────────────────────
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "RecordPro",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = SoftWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadRecordings() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = SubtleGray)
                    }
                    IconButton(onClick = { /* open settings */ }) {
                        Icon(Icons.Default.Settings, "Settings", tint = SubtleGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // ── Stats Row ─────────────────────────────────────────────────
            AnimatedVisibility(visible = !uiState.isLoading && uiState.totalCount > 0) {
                StatsRow(uiState.totalCount, uiState.totalDurationSeconds)
            }

            Spacer(Modifier.height(8.dp))

            // ── Search Bar ────────────────────────────────────────────────
            RecordingSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::setSearchQuery
            )

            Spacer(Modifier.height(10.dp))

            // ── Time Filter Bar ───────────────────────────────────────────
            TimeFilterBar(
                selected = uiState.timeFilter,
                onSelect = viewModel::setTimeFilter
            )

            Spacer(Modifier.height(12.dp))

            // ── Content ───────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> LoadingState()
                    uiState.error != null -> ErrorState(uiState.error!!) { viewModel.loadRecordings() }
                    uiState.recordings.isEmpty() -> EmptyState(uiState.searchQuery.isNotEmpty())
                    else -> RecordingsList(
                        recordings = uiState.recordings,
                        onPlay = onOpenPlayer,
                        onDelete = viewModel::deleteRecording,
                        onShare = { item ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, item.fileUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share recording"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsRow(totalCount: Int, totalDurationSeconds: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatChip(label = "$totalCount recordings", icon = Icons.Default.Mic)
        StatChip(
            label = formatTotalDuration(totalDurationSeconds),
            icon = Icons.Default.Timer
        )
    }
}

@Composable
private fun StatChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .background(NavySurface, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = ElectricBlue, modifier = Modifier.size(13.dp))
        Text(label, fontSize = 12.sp, color = SubtleGray)
    }
}

@Composable
private fun RecordingsList(
    recordings: List<RecordingItem>,
    onPlay: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onShare: (RecordingItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(
            items = recordings,
            key = { _, item -> item.id }
        ) { index, item ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300, delayMillis = index * 40)) +
                        slideInVertically(tween(300, delayMillis = index * 40)) { it / 2 }
            ) {
                RecordingCard(
                    item = item,
                    onPlay = onPlay,
                    onDelete = onDelete,
                    onShare = onShare
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ElectricBlue, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text("Loading recordings…", color = SubtleGray)
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = DangerRed, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(error, color = SubtleGray, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EmptyState(isFiltered: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 0.4f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                label = "mic_alpha"
            )
            Icon(
                Icons.Default.MicOff, null,
                tint = SubtleGray.copy(alpha = alpha),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isFiltered) "No recordings match your search"
                else "No recordings yet",
                color = SoftWhite, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isFiltered) "Try a different filter or search term"
                else "Enable Samsung call recorder in Phone app\nand grant RecordPro accessibility access\nfor WhatsApp calls",
                color = SubtleGray, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun formatTotalDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m total"
        m > 0 -> "${m}m total"
        else  -> "${seconds}s total"
    }
}
