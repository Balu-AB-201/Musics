/*
 * Lyra Music Project (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.shnwaz.lyramusic.ui.screens.library

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.shnwaz.lyramusic.LocalPlayerConnection
import com.shnwaz.lyramusic.di.LyricsHelperEntryPoint
import com.shnwaz.lyramusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.shnwaz.lyramusic.lyrics.LyricsHelper
import com.shnwaz.lyramusic.models.MediaMetadata as AppMediaMetadata
import com.shnwaz.lyramusic.ui.component.LocalLiquidGlassEnabled
import com.shnwaz.lyramusic.playback.queues.ListQueue
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DeviceAudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long,
    val mimeType: String?,
    val uri: Uri,
) {
    val artworkUri: Uri?
        get() = if (albumId > 0L) Uri.parse("content://media/external/audio/albumart/$albumId") else null

    fun toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            // The existing player reads its app-level metadata from MediaItem.tag.
            // Without this tag the player screen cannot resolve title, artist, lyrics, or artwork.
            .setTag(toLyricsMetadata())
            .setMediaMetadata(
                Media3Metadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setSubtitle(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

    fun toLyricsMetadata() = AppMediaMetadata(
        id = uri.toString(),
        title = title,
        artists = listOf(AppMediaMetadata.Artist(id = null, name = artist)),
        duration = (durationMs / 1000L).toInt(),
        thumbnailUrl = artworkUri?.toString(),
        album = AppMediaMetadata.Album(id = albumId.toString(), title = album),
    )
}

private enum class OfflineSort(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION_SHORT("Duration: shortest first"),
    DURATION_LONG("Duration: longest first"),
}

@Composable
fun OfflineMusicScreen(navController: NavController) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val permission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    val coroutineScope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var tracks by remember { mutableStateOf<List<DeviceAudioTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(OfflineSort.TITLE) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var minimumDurationSeconds by remember { mutableStateOf(0L) }
    var durationMenuExpanded by remember { mutableStateOf(false) }
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    var lyricsTrack by remember { mutableStateOf<DeviceAudioTrack?>(null) }
    var lyricsText by remember { mutableStateOf("") }
    var lyricsLoading by remember { mutableStateOf(false) }

    val lyricsHelper: LyricsHelper by remember {
        mutableStateOf(EntryPointAccessors.fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java).lyricsHelper())
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
        }

    suspend fun loadTracks() {
        loading = true
        loadError = null
        val result = withContext(Dispatchers.IO) {
            val loaded = mutableListOf<DeviceAudioTrack>()
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.MIME_TYPE,
            )
            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    // Include every audio row exposed by MediaStore, not only files
                    // tagged as "music"; the duration filter can exclude short recordings.
                    null,
                    null,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        loaded += DeviceAudioTrack(
                            id = id,
                            title = cursor.getString(titleColumn)?.takeIf(String::isNotBlank) ?: "Unknown title",
                            artist = cursor.getString(artistColumn)?.takeIf(String::isNotBlank) ?: "Unknown artist",
                            album = cursor.getString(albumColumn)?.takeIf(String::isNotBlank) ?: "Unknown album",
                            durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L),
                            albumId = cursor.getLong(albumIdColumn),
                            mimeType = cursor.getString(mimeColumn),
                            uri = ContentUris.withAppendedId(collection, id),
                        )
                    }
                }
            } catch (error: SecurityException) {
                loadError = "Allow audio access to scan music on this device."
            } catch (error: Exception) {
                loadError = error.localizedMessage ?: "Unable to read audio files."
            }
            loaded
        }
        tracks = result
        loading = false
    }

    val sortedTracks = remember(tracks, sort, minimumDurationSeconds) {
        val filtered = tracks.filter { it.durationMs >= minimumDurationSeconds * 1000L }
        when (sort) {
            OfflineSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            OfflineSort.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            OfflineSort.DURATION_SHORT -> filtered.sortedBy { it.durationMs }
            OfflineSort.DURATION_LONG -> filtered.sortedByDescending { it.durationMs }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) loadTracks()
    }

    if (lyricsTrack != null) {
        AlertDialog(
            onDismissRequest = { if (!lyricsLoading) { lyricsTrack = null; lyricsText = "" } },
            title = { Text(lyricsTrack?.title ?: "Lyrics") },
            text = {
                when {
                    lyricsLoading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item {
                            Text(
                                lyricsText.ifBlank { "Lyrics haven't been loaded yet." },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { lyricsTrack = null; lyricsText = "" }) { Text("Close") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("On-device music", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (hasPermission) "Showing ${sortedTracks.size} of ${tracks.size} tracks" else "Play music stored on your phone or SD card",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Keep controls on their own horizontally scrollable row on narrow phones.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box {
                TextButton(onClick = { sortMenuExpanded = true }, enabled = hasPermission && !loading) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text("Sort")
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    OfflineSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { sort = option; sortMenuExpanded = false },
                            leadingIcon = if (sort == option) ({ Icon(Icons.Outlined.MusicNote, contentDescription = null) }) else null,
                        )
                    }
                }
            }
            Box {
                TextButton(onClick = { durationMenuExpanded = true }, enabled = hasPermission && !loading) {
                    Text(if (minimumDurationSeconds == 0L) "Min: Off" else "Min: ${minimumDurationSeconds}s")
                }
                DropdownMenu(expanded = durationMenuExpanded, onDismissRequest = { durationMenuExpanded = false }) {
                    listOf(0L, 15L, 30L, 60L, 90L, 120L).forEach { seconds ->
                        DropdownMenuItem(
                            text = { Text(if (seconds == 0L) "Show all durations" else "At least ${seconds}s") },
                            onClick = { minimumDurationSeconds = seconds; durationMenuExpanded = false },
                        )
                    }
                }
            }
            TextButton(
                enabled = sortedTracks.isNotEmpty() && playerConnection != null,
                onClick = {
                    playerConnection?.playQueue(
                        ListQueue(title = "On-device music · Shuffle", items = sortedTracks.shuffled().map { it.toMediaItem() }),
                    )
                },
            ) {
                Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                Text("Shuffle")
            }
            TextButton(enabled = hasPermission && !loading, onClick = { coroutineScope.launch { loadTracks() } }) {
                Text("Refresh")
            }
        }
        Text(
            "Sort: ${sort.label} · Minimum duration: ${if (minimumDurationSeconds == 0L) "off" else "${minimumDurationSeconds}s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        when {
            !hasPermission -> Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Allow audio access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Lyra uses Android's media library to list audio files. It does not upload your local music.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { permissionLauncher.launch(permission) }) { Text("Grant permission") }
                }
            }
            loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            loadError != null -> Text(loadError.orEmpty(), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            sortedTracks.isEmpty() -> Text(
                "No music files found. Add audio files to your device and refresh.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sortedTracks, key = { it.id }) { track ->
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .then(
                                if (liquidGlassEnabled) Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(16.dp),
                                ) else Modifier,
                            )
                            .clickable {
                                val connection = playerConnection ?: return@clickable
                                val queueItems = sortedTracks.map { it.toMediaItem() }
                                val index = sortedTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                connection.playQueue(ListQueue(title = "On-device music", items = queueItems, startIndex = index))
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (liquidGlassEnabled) 0.82f else 1f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = track.artworkUri,
                                contentDescription = "Album artwork for ${track.title}",
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${track.album} · ${formatTrackDuration(track.durationMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                lyricsTrack = track
                                lyricsText = ""
                                lyricsLoading = true
                                coroutineScope.launch {
                                    lyricsText = try {
                                        val result = lyricsHelper.getLyrics(track.toLyricsMetadata())
                                        if (result == LYRICS_NOT_FOUND || result.isBlank()) "Couldn't find lyrics for this song." else result
                                    } catch (_: Exception) {
                                        "Couldn't load lyrics right now. Check your connection and try again."
                                    }
                                    lyricsLoading = false
                                }
                            }) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = "Find lyrics for ${track.title}", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
 
private fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
