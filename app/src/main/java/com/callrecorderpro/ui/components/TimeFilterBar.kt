package com.callrecorderpro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorderpro.ui.theme.ElectricBlue
import com.callrecorderpro.ui.theme.NavySurface
import com.callrecorderpro.viewmodel.TimeFilter

@Composable
fun TimeFilterBar(
    selected: TimeFilter,
    onSelect: (TimeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            val containerColor by animateColorAsState(
                if (isSelected) ElectricBlue else NavySurface,
                label = "chip_color"
            )
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = filter.label(),
                        fontSize = 12.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricBlue,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = NavySurface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    selectedBorderColor = ElectricBlue,
                    borderColor = NavySurface
                )
            )
        }
    }
}

fun TimeFilter.label() = when (this) {
    TimeFilter.ALL   -> "All"
    TimeFilter.TODAY -> "Today"
    TimeFilter.WEEK  -> "7 Days"
    TimeFilter.MONTH -> "30 Days"
}
