/*
 * Lyra Music Project (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.shnwaz.lyramusic.ui.screens.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import androidx.navigation.NavController
import com.shnwaz.lyramusic.LocalPlayerConnection
import com.shnwaz.lyramusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.shnwaz.lyramusic.di.LyricsHelperEntryPoint
import com.shnwaz.lyramusic.lyrics.LyricsHelper
import com.shnwaz.lyramusic.models.MediaMetadata as AppMediaMetadata
import com.shnwaz.lyramusic.playback.queues.ListQueue
import com.shnwaz.lyramusic.ui.component.LocalLiquidGlassEnabled
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class DeviceAudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long,
    val mimeType: String?,
    val uriString: String,
) {
    val uri: Uri
        get() = Uri.parse(uriString)

    /** Artwork address handed to the player. */
    val artworkUri: Uri
        get() =
            if (Build.VERSION.SDK_INT >= 29) {
                Uri.withAppendedPath(uri, "albumart")
            } else {
                Uri.parse("content://media/external/audio/albumart/$albumId")
            }

    fun toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(uriString)
            .setUri(uri)
            // The existing player reads its app-level metadata from MediaItem.tag.
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
        id = uriString,
        title = title,
        artists = listOf(AppMediaMetadata.Artist(id = null, name = artist)),
        duration = (durationMs / 1000L).toInt(),
        thumbnailUrl = artworkUri.toString(),
        album = AppMediaMetadata.Album(id = albumId.toString(), title = album),
    )
}

private enum class OfflineSort(val label: String, val shortLabel: String) {
    TITLE("Title", "Title"),
    ARTIST("Artist", "Artist"),
    DURATION_SHORT("Duration: shortest first", "Shortest"),
    DURATION_LONG("Duration: longest first", "Longest"),
}

private val MIN_DURATION_OPTIONS = listOf(125L, 150L, 180L)
private const val DEFAULT_MIN_SECONDS = 125L

/** Remembers the sort and minimum-duration choices between app launches. */
private object OfflineSettings {
    private fun prefs(context: Context) =
        context.getSharedPreferences("offline_music_settings", Context.MODE_PRIVATE)

    fun sort(context: Context): OfflineSort =
        runCatching {
            OfflineSort.valueOf(prefs(context).getString("sort", OfflineSort.TITLE.name) ?: OfflineSort.TITLE.name)
        }.getOrDefault(OfflineSort.TITLE)

    fun setSort(context: Context, value: OfflineSort) {
        prefs(context).edit().putString("sort", value.name).apply()
    }

    fun minSeconds(context: Context): Long {
        val saved = prefs(context).getLong("min_seconds", DEFAULT_MIN_SECONDS)
        return if (saved in MIN_DURATION_OPTIONS) saved else DEFAULT_MIN_SECONDS
    }

    fun setMinSeconds(context: Context, value: Long) {
        prefs(context).edit().putLong("min_seconds", value).apply()
    }
}

/** Keeps the last scan on disk so the tab opens instantly and refreshes in the background. */
private object OfflineTrackCache {
    @Volatile
    private var memory: List<DeviceAudioTrack>? = null

    private fun file(context: Context) = File(context.filesDir, "offline_tracks_cache.json")

    fun load(context: Context): List<DeviceAudioTrack> {
        memory?.let { return it }
        val cacheFile = file(context)
        if (!cacheFile.exists()) return emptyList()
        return try {
            val array = JSONArray(cacheFile.readText())
            val list = ArrayList<DeviceAudioTrack>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list += DeviceAudioTrack(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    album = o.getString("album"),
                    durationMs = o.getLong("duration"),
                    albumId = o.getLong("albumId"),
                    mimeType = o.optString("mime").takeIf { it.isNotBlank() },
                    uriString = o.getString("uri"),
                )
            }
            memory = list
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, tracks: List<DeviceAudioTrack>) {
        memory = tracks
        try {
            val array = JSONArray()
            tracks.forEach { t ->
                array.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("title", t.title)
                        .put("artist", t.artist)
                        .put("album", t.album)
                        .put("duration", t.durationMs)
                        .put("albumId", t.albumId)
                        .put("mime", t.mimeType ?: "")
                        .put("uri", t.uriString),
                )
            }
            file(context).writeText(array.toString())
        } catch (_: Exception) {
        }
    }
}

private fun cleanTag(value: String?, fallback: String): String {
    val trimmed = value?.trim().orEmpty()
    return if (trimmed.isEmpty() || trimmed.equals("<unknown>", ignoreCase = true)) fallback else trimmed
}

/** Reads every music file Android knows about. Runs on a background thread. */
private fun scanDeviceAudio(context: Context): List<DeviceAudioTrack> {
    val loaded = ArrayList<DeviceAudioTrack>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DISPLAY_NAME,
    )
    context.contentResolver.query(
        collection,
        projection,
        // Music only: ringtones, alarms and notification sounds are skipped.
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
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
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val fileName = cursor.getString(nameColumn)?.substringBeforeLast('.')
            loaded += DeviceAudioTrack(
                id = id,
                title = cleanTag(cursor.getString(titleColumn), cleanTag(fileName, "Unknown title")),
                artist = cleanTag(cursor.getString(artistColumn), "Unknown artist"),
                album = cleanTag(cursor.getString(albumColumn), "Unknown album"),
                durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L),
                albumId = cursor.getLong(albumIdColumn),
                mimeType = cursor.getString(mimeColumn),
                uriString = ContentUris.withAppendedId(collection, id).toString(),
            )
        }
    }
    return loaded
}

private val artworkCache = LruCache<Long, Bitmap>(160)

private fun loadArtwork(context: Context, track: DeviceAudioTrack): Bitmap? {
    artworkCache.get(track.id)?.let { return it }
    val bitmap =
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(track.uri, Size(256, 256), null)
            } else {
                context.contentResolver.openInputStream(track.artworkUri)?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) {
            null
        }
    if (bitmap != null) artworkCache.put(track.id, bitmap)
    return bitmap
}

@Composable
private fun TrackArtwork(track: DeviceAudioTrack, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = artworkCache.get(track.id), track.id) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { loadArtwork(context, track) }
        }
    }
    val art = bitmap
    if (art != null) {
        Image(
            bitmap = art.asImageBitmap(),
            contentDescription = "Album artwork for ${track.title}",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    // The last scan is read from disk straight away, so the list is there before the rescan ends.
    var tracks by remember { mutableStateOf<List<DeviceAudioTrack>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(OfflineSettings.sort(context)) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var minimumDurationSeconds by remember { mutableStateOf(OfflineSettings.minSeconds(context)) }
    var durationMenuExpanded by remember { mutableStateOf(false) }
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    var lyricsTrack by remember { mutableStateOf<DeviceAudioTrack?>(null) }
    var lyricsText by remember { mutableStateOf("") }
    var lyricsLoading by remember { mutableStateOf(false) }

    val lyricsHelper: LyricsHelper by remember {
        mutableStateOf(
            EntryPointAccessors.fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java)
                .lyricsHelper(),
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
        }

    suspend fun refreshTracks() {
        refreshing = true
        loadError = null
        val outcome = withContext<Pair<List<DeviceAudioTrack>?, String?>>(Dispatchers.IO) {
            try {
                val fresh = scanDeviceAudio(context)
                OfflineTrackCache.save(context, fresh)
                fresh to null
            } catch (error: SecurityException) {
                null to "Allow audio access to scan music on this device."
            } catch (error: Exception) {
                null to (error.localizedMessage ?: "Unable to read audio files.")
            }
        }
        outcome.first?.let { tracks = it }
        loadError = outcome.second
        refreshing = false
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
        if (hasPermission) {
            val cached = withContext(Dispatchers.IO) { OfflineTrackCache.load(context) }
            if (cached.isNotEmpty()) tracks = cached
            refreshTracks()
        }
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
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
                    if (hasPermission) "${sortedTracks.size} songs" else "Play music stored on your phone or SD card",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                enabled = hasPermission && !refreshing,
                onClick = { coroutineScope.launch { refreshTracks() } },
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        }

        // Three equal buttons on one line: Sort, Minimum length and Shuffle.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FilledTonalButton(
                    onClick = { sortMenuExpanded = true },
                    enabled = hasPermission,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(sort.shortLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    OfflineSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                sort = option
                                OfflineSettings.setSort(context, option)
                                sortMenuExpanded = false
                            },
                            leadingIcon = if (sort == option) ({ Icon(Icons.Outlined.MusicNote, contentDescription = null) }) else null,
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                FilledTonalButton(
                    onClick = { durationMenuExpanded = true },
                    enabled = hasPermission,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("Min ${minimumDurationSeconds}s", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = durationMenuExpanded, onDismissRequest = { durationMenuExpanded = false }) {
                    MIN_DURATION_OPTIONS.forEach { seconds ->
                        DropdownMenuItem(
                            text = { Text("At least ${seconds}s") },
                            onClick = {
                                minimumDurationSeconds = seconds
                                OfflineSettings.setMinSeconds(context, seconds)
                                durationMenuExpanded = false
                            },
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = {
                    playerConnection?.playQueue(
                        ListQueue(title = "On-device music · Shuffle", items = sortedTracks.shuffled().map { it.toMediaItem() }),
                    )
                },
                enabled = sortedTracks.isNotEmpty() && playerConnection != null,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Shuffle", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (refreshing && tracks.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }

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
            refreshing && tracks.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            loadError != null && tracks.isEmpty() -> Text(
                loadError.orEmpty(),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
            sortedTracks.isEmpty() -> Text(
                "No songs of at least ${minimumDurationSeconds}s found. Add music to your device or lower the minimum length.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sortedTracks, key = { it.id }) { track ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (liquidGlassEnabled) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                } else {
                                    Modifier
                                },
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
                            TrackArtwork(
                                track = track,
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
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
