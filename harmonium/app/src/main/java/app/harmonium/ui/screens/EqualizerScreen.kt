package app.harmonium.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.harmonium.data.model.EqProfile

@Composable
fun EqualizerScreen(
    profiles: List<EqProfile>,
    activeId: String,
    onSelect: (String) -> Unit,
    onBandChange: (String, Int, Float) -> Unit,
) {
    val active = profiles.find { it.id == activeId } ?: profiles.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Text(
                "Equalizer",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            Text(
                "Graphic EQ plus AutoEQ-inspired headphone presets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // wrap chips in a flow-like column of rows
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                profiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { profile ->
                            FilterChip(
                                selected = profile.id == activeId,
                                onClick = { onSelect(profile.id) },
                                label = {
                                    Text(
                                        buildString {
                                            append(profile.name)
                                            if (profile.isAutoEq) append(" ★")
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (active != null) {
            items(active.bands, key = { "${active.id}-${it.frequencyHz}" }) { band ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            freqLabel(band.frequencyHz),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "%+.1f dB".format(band.gainDb),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = band.gainDb,
                        onValueChange = { onBandChange(active.id, band.frequencyHz, it) },
                        valueRange = -12f..12f,
                    )
                }
            }
        }
    }
}

private fun freqLabel(hz: Int): String =
    if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
