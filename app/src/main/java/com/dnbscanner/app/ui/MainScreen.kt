@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dnbscanner.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dnbscanner.app.data.Settings
import com.dnbscanner.app.data.TextNorm
import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Period
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.YtLink
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Date

private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    // Bewusst ohne remember: liest vm.links, damit neue YT-Treffer sofort den Filter aktualisieren.
    val queryTokens = TextNorm.tokens(state.query)
    val visible = state.releases.filter { r ->
        r.genres.any { it in state.genres } &&
            (!state.onlyMatched || vm.links[r.key]?.direct == true) &&
            (queryTokens.isEmpty() || TextNorm.tokens("${r.artist} ${r.title} ${r.label.orEmpty()}").let { hay ->
                queryTokens.all { t -> hay.any { it.startsWith(t) } }
            })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DnB / Jungle Scanner") },
                actions = {
                    IconButton(onClick = vm::scan, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Neu scannen")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = state.period == p,
                        onClick = { if (state.period != p) vm.selectPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                    ) { Text(p.label) }
                }
            }

            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Genre.entries.forEach { g ->
                    FilterChip(selected = g in state.genres, onClick = { vm.toggleGenre(g) }, label = { Text(g.label) })
                }
                FilterChip(
                    selected = state.onlyMatched,
                    onClick = vm::toggleOnlyMatched,
                    label = { Text("Nur mit YT-Music-Treffer") },
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true,
                placeholder = { Text("Artist, Titel oder Label filtern") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Leeren") }
                    }
                },
            )

            StatusBar(state, visible.size, matchedCount = visible.count { vm.links[it.key]?.direct == true })

            if (state.releases.isEmpty() && !state.loading) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        state.error ?: "Noch keine Releases. Tippe auf ↻ zum Scannen.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.key }) { r ->
                        ReleaseCard(
                            release = r,
                            link = vm.linkFor(r),
                            matching = vm.matching[r.key] == true,
                            onOpen = { url -> openUrl(context, url) },
                            onRetryMatch = { vm.match(r) },
                            onShare = { shareRelease(context, r, vm.linkFor(r)) },
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = state.settings,
            onDismiss = { showSettings = false },
            onSave = { vm.updateSettings(it); showSettings = false },
            onClearCache = { vm.clearMatchCache(); Toast.makeText(context, "YT-Music-Cache geleert", Toast.LENGTH_SHORT).show() },
        )
    }
}

@Composable
private fun StatusBar(state: UiState, visibleCount: Int, matchedCount: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(state.progress ?: "Scanne …", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
        val scanned = state.scannedAt?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
        Text(
            "$visibleCount Releases · $matchedCount mit direktem YT-Music-Link" + (scanned?.let { " · Stand $it" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.warnings.forEach {
            Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.error != null && state.releases.isNotEmpty()) {
            Text("⚠ ${state.error}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    link: YtLink,
    matching: Boolean,
    onOpen: (String) -> Unit,
    onRetryMatch: () -> Unit,
    onShare: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp)) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("♫", style = MaterialTheme.typography.headlineMedium)
                if (release.coverUrl != null) {
                    AsyncImage(
                        model = release.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(release.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(release.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(release.releaseDate.format(DATE_FMT), release.label, release.type).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (release.genres.map { it.label } + release.sources.map { it.label }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (link.direct && link.matchedText != null) {
            Text(
                "YT Music: ${link.matchedText}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FilledTonalButton(onClick = { onOpen(link.url) }) {
                if (matching) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(if (link.direct) Icons.Default.PlayArrow else Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (link.direct) "YouTube Music" else "In YT Music suchen")
            }
            if (!link.direct && !matching) {
                IconButton(onClick = onRetryMatch) { Icon(Icons.Default.Refresh, contentDescription = "Erneut abgleichen") }
            }
            release.sourceUrls.entries.sortedBy { it.key.ordinal }.forEach { (source, url) ->
                TextButton(onClick = { onOpen(url) }) { Text(source.label) }
            }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Teilen") }
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onSave: (Settings) -> Unit,
    onClearCache: () -> Unit,
) {
    var useBandcamp by remember { mutableStateOf(settings.useBandcamp) }
    var useMusicBrainz by remember { mutableStateOf(settings.useMusicBrainz) }
    var autoMatch by remember { mutableStateOf(settings.autoMatch) }
    var tags by remember { mutableStateOf(settings.bandcampTags.joinToString(", ")) }
    var pages by remember { mutableStateOf(settings.bandcampPagesPerWeek.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow("Bandcamp durchsuchen", useBandcamp) { useBandcamp = it }
                SwitchRow("MusicBrainz durchsuchen", useMusicBrainz) { useMusicBrainz = it }
                SwitchRow("YouTube Music automatisch abgleichen", autoMatch) { autoMatch = it }
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Bandcamp-Tags (kommagetrennt)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pages,
                    onValueChange = { v -> pages = v.filter(Char::isDigit).take(2) },
                    label = { Text("Bandcamp-Seiten pro Tag (Woche; ×3 Monat, ×6 3 Monate)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onClearCache) { Text("YT-Music-Treffer neu abgleichen") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    Settings(
                        useBandcamp = useBandcamp,
                        useMusicBrainz = useMusicBrainz,
                        bandcampTags = tags.split(',').map { it.trim().lowercase().replace(' ', '-') }
                            .filter { it.isNotEmpty() }.ifEmpty { Settings.DEFAULT_TAGS },
                        bandcampPagesPerWeek = (pages.toIntOrNull() ?: 3).coerceIn(1, 20),
                        autoMatch = autoMatch,
                    ),
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show()
    }
}

private fun shareRelease(context: Context, release: Release, link: YtLink) {
    val text = buildString {
        append("${release.artist} – ${release.title}")
        release.label?.let { append(" [$it]") }
        append("\n").append(link.url)
    }
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Release teilen"))
}
