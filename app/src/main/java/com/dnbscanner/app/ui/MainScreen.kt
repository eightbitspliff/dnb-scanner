@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.dnbscanner.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dnbscanner.app.data.Settings
import com.dnbscanner.app.data.TextNorm
import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.MusicLink
import com.dnbscanner.app.model.Period
import com.dnbscanner.app.model.Release
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val STAMP_FMT = java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY)

private fun genreColor(g: Genre): Color = when (g) {
    Genre.DNB -> Neon.Acid
    Genre.JUNGLE -> Neon.Amber
}

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    // Bewusst ohne remember: liest die Link-Maps, damit neue Treffer sofort Filter und Zähler aktualisieren.
    val queryTokens = TextNorm.tokens(state.query)
    val visible = state.releases.filter { r ->
        r.genres.any { it in state.genres } &&
            (!state.onlyYouTube || vm.isOnYouTube(r)) &&
            (!state.onlySpotify || vm.isOnSpotify(r)) &&
            (queryTokens.isEmpty() || TextNorm.tokens("${r.artist} ${r.title} ${r.label.orEmpty()}").let { hay ->
                queryTokens.all { t -> hay.any { it.startsWith(t) } }
            })
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(Neon.Bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Neon.Acid.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.width * 0.9f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Neon.Magenta.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(0f, size.height * 0.4f),
                        radius = size.width,
                    ),
                )
            },
    ) {
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = vm::scan,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
            ) {
                item { Header(loading = state.loading, onRefresh = vm::scan, onSettings = { showSettings = true }) }

                stickyHeader {
                    FilterBar(
                        state = state,
                        onPeriod = vm::selectPeriod,
                        onGenre = vm::toggleGenre,
                        onOnlyYouTube = vm::toggleOnlyYouTube,
                        onOnlySpotify = vm::toggleOnlySpotify,
                    )
                }

                item {
                  Column {
                    SearchField(state.query, vm::setQuery)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatTile("RELEASES", visible.size, Neon.Acid, Modifier.weight(1f))
                        StatTile("YT MUSIC", visible.count { vm.ytLinks[it.key]?.direct == true }, Neon.YouTubeRed, Modifier.weight(1f))
                        StatTile("SPOTIFY", visible.count { vm.spotifyLinks[it.key]?.direct == true }, Neon.SpotifyGreen, Modifier.weight(1f))
                    }
                    StatusLine(state)
                  }
                }

                if (visible.isEmpty() && !state.loading) {
                    item { EmptyState(if (state.releases.isEmpty()) state.error ?: "Noch keine Releases.\nRunterziehen zum Scannen." else if ((state.onlyYouTube && vm.ytMatching.isNotEmpty()) || (state.onlySpotify && vm.spotifyMatching.isNotEmpty())) "Abgleich läuft noch …" else "Keine Treffer für diese Filter.") }
                }

                items(visible, key = { it.key }) { r ->
                    ReleaseCard(
                        release = r,
                        ytLink = vm.ytLinkFor(r),
                        spotifyLink = vm.spotifyLinkFor(r),
                        ytLoading = vm.ytMatching[r.key] == true,
                        spotifyLoading = vm.spotifyMatching[r.key] == true,
                        onOpen = { url -> openUrl(context, url) },
                        onRetry = { vm.retryMatch(r) },
                        onShare = { shareRelease(context, r, vm.ytLinkFor(r), vm.spotifyLinkFor(r)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).animateItem(),
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            onDismiss = { showSettings = false },
            onSave = { vm.updateSettings(it); showSettings = false },
            onClearCache = {
                vm.clearMatchCache()
                Toast.makeText(context, "Links werden neu abgeglichen", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun Header(loading: Boolean, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val logo = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-1).sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("DNB", style = logo.copy(brush = NeonGradient))
                Text("//", style = logo.copy(color = Neon.Magenta))
                Text("SCANNER", style = logo.copy(color = Neon.Text))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EqualizerBars(bars = 5, barWidth = 3.dp, height = 12.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "174 BPM · DRUM & BASS · JUNGLE",
                    color = Neon.TextDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            Icon(Icons.Default.Refresh, contentDescription = "Neu scannen", tint = if (loading) Neon.Outline else Neon.Text)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = Neon.Text)
        }
    }
}

@Composable
private fun FilterBar(
    state: UiState,
    onPeriod: (Period) -> Unit,
    onGenre: (Genre) -> Unit,
    onOnlyYouTube: () -> Unit,
    onOnlySpotify: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Neon.Bg.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PeriodTabs(state.period, onPeriod)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Genre.entries.forEach { g ->
                NeonChip(g.label, selected = g in state.genres, color = genreColor(g)) { onGenre(g) }
            }
            NeonChip("Nur auf YT Music", selected = state.onlyYouTube, color = Neon.YouTubeRed, onClick = onOnlyYouTube)
            NeonChip("Nur auf Spotify", selected = state.onlySpotify, color = Neon.SpotifyGreen, onClick = onOnlySpotify)
        }
    }
}

@Composable
private fun PeriodTabs(current: Period, onSelect: (Period) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(Neon.SurfaceHi)
            .border(1.dp, Neon.Outline, RoundedCornerShape(50))
            .padding(4.dp),
    ) {
        Period.entries.forEach { p ->
            val selected by animateFloatAsState(if (p == current) 1f else 0f, label = "tab")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(NeonGradient, alpha = selected)
                    .clickable { onSelect(p) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    p.label.uppercase(),
                    color = lerp(Neon.TextDim, Neon.Bg, selected),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = { Text("Artist, Titel oder Label …", color = Neon.TextDim) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Neon.TextDim) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Leeren") }
            }
        },
    )
}

@Composable
private fun StatusLine(state: UiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (state.loading) {
            ScanningBar()
            Text(
                (state.progress ?: "Scanne …").uppercase(),
                color = Neon.Acid,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else if (state.scannedAt != null) {
            Text(
                "STAND ${STAMP_FMT.format(java.util.Date(state.scannedAt))} · RUNTERZIEHEN ZUM AKTUALISIEREN",
                color = Neon.TextDim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
            )
        }
        if (state.rejected > 0 && !state.loading) {
            Text(
                "${state.rejected} KANDIDATEN AUSSORTIERT (KEIN DNB/JUNGLE)",
                color = Neon.TextDim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        (state.warnings + listOfNotNull(state.error?.takeIf { state.releases.isNotEmpty() })).forEach {
            Text("⚠ $it", color = Neon.Amber, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EqualizerBars(bars = 9, barWidth = 6.dp, height = 56.dp)
        Spacer(Modifier.height(20.dp))
        Text(text, color = Neon.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    ytLink: MusicLink,
    spotifyLink: MusicLink,
    ytLoading: Boolean,
    spotifyLoading: Boolean,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val accent = genreColor(release.genres.firstOrNull() ?: Genre.DNB)
    val isNew = !release.releaseDate.isBefore(LocalDate.now().minusDays(2))
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Neon.SurfaceHi, Neon.Surface)))
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), Neon.Outline.copy(alpha = 0.4f), Color.Transparent)), shape)
            .padding(12.dp),
    ) {
        Row {
            Box(
                Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), Neon.Magenta.copy(alpha = 0.25f), Neon.Bg))),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerBars(bars = 4, barWidth = 5.dp, height = 30.dp, brush = Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.3f))))
                if (release.coverUrl != null) {
                    AsyncImage(
                        model = release.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isNew) TagPill("Neu", Neon.Magenta)
                    release.genres.sortedBy { it.ordinal }.forEach { TagPill(if (it == Genre.DNB) "DnB" else it.label, genreColor(it)) }
                }
                Spacer(Modifier.height(6.dp))
                Text(release.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(release.artist, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(release.releaseDate.format(DATE_FMT), release.label, release.type).joinToString("  ·  "),
                    color = Neon.TextDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (release.tags.isNotEmpty()) {
                    Text(
                        release.tags.take(8).joinToString("  ") { "#$it" },
                        color = accent.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServiceButton(
                label = "YT Music",
                color = Neon.YouTubeRed,
                contentOnColor = Color.White,
                link = ytLink,
                loading = ytLoading,
                icon = { tint -> YouTubeMusicIcon(tint) },
                onClick = { onOpen(ytLink.url) },
                modifier = Modifier.weight(1f),
            )
            ServiceButton(
                label = "Spotify",
                color = Neon.SpotifyGreen,
                contentOnColor = Color.Black,
                link = spotifyLink,
                loading = spotifyLoading,
                icon = { tint -> SpotifyIcon(tint) },
                onClick = { onOpen(spotifyLink.url) },
                modifier = Modifier.weight(1f),
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            release.sourceUrls.entries.sortedBy { it.key.ordinal }.forEach { (source, url) ->
                Text(
                    "${source.label.uppercase()} ↗",
                    color = Neon.TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen(url) }.padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if ((!ytLink.direct || !spotifyLink.direct) && !ytLoading && !spotifyLoading) {
                IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Erneut abgleichen", tint = Neon.TextDim, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Teilen", tint = Neon.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SettingsSheet(
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
    var spotifyId by remember { mutableStateOf(settings.spotifyClientId) }
    var spotifySecret by remember { mutableStateOf(settings.spotifyClientSecret) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Neon.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("EINSTELLUNGEN", style = TextStyle(brush = NeonGradient, fontSize = 22.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic))

            SectionTitle("Quellen")
            SwitchRow("Bandcamp", useBandcamp) { useBandcamp = it }
            SwitchRow("MusicBrainz", useMusicBrainz) { useMusicBrainz = it }
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Bandcamp-Tags (kommagetrennt)") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pages,
                onValueChange = { v -> pages = v.filter(Char::isDigit).take(2) },
                label = { Text("Bandcamp-Seiten pro Tag (×3 Monat, ×6 3 Monate)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Streaming-Links")
            SwitchRow("Automatisch abgleichen", autoMatch) { autoMatch = it }
            Text(
                "Spotify (optional): Mit eigener Client-ID/Secret von developer.spotify.com geht der Abgleich schnell. " +
                    "Ohne Zugangsdaten wird der YouTube-Music-Treffer per song.link umgewandelt (ca. 10 Releases pro Minute).",
                color = Neon.TextDim,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = spotifyId,
                onValueChange = { spotifyId = it.trim() },
                label = { Text("Spotify Client-ID") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = spotifySecret,
                onValueChange = { spotifySecret = it.trim() },
                label = { Text("Spotify Client-Secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                Text("Alle Links neu abgleichen")
            }
            Button(
                onClick = {
                    onSave(
                        Settings(
                            useBandcamp = useBandcamp,
                            useMusicBrainz = useMusicBrainz,
                            bandcampTags = tags.split(',').map { it.trim().lowercase().replace(' ', '-') }
                                .filter { it.isNotEmpty() }.ifEmpty { Settings.DEFAULT_TAGS },
                            bandcampPagesPerWeek = (pages.toIntOrNull() ?: 3).coerceIn(1, 20),
                            autoMatch = autoMatch,
                            spotifyClientId = spotifyId,
                            spotifyClientSecret = spotifySecret,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("SPEICHERN", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Neon.Cyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.6.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Neon.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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

private fun shareRelease(context: Context, release: Release, yt: MusicLink, spotify: MusicLink) {
    val text = buildString {
        append("${release.artist} – ${release.title}")
        release.label?.let { append(" [$it]") }
        append("\nYouTube Music: ").append(yt.url)
        append("\nSpotify: ").append(spotify.url)
    }
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Release teilen"))
}
