/*
 * Lyra Music Project (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.shnwaz.lyramusic.ui.screens.library

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavController
import com.shnwaz.lyramusic.LocalPlayerConnection
import com.shnwaz.lyramusic.playback.queues.ListQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DeviceAudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long,
    val mimeType: String?,
    val uri: android.net.Uri,
) {
    fun toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(
                        if (albumId > 0L) {
                            ContentUris.withAppendedId(
                                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                                albumId,
                            )
                        } else {
                            null
                        },
                    )
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
}

@Composable
fun OfflineMusicScreen(navController: NavController) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val permission =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var tracks by remember { mutableStateOf<List<DeviceAudioTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
        }

    suspend fun loadTracks() {
        loading = true
        loadError = null
        tracks =
            withContext(Dispatchers.IO) {
                val result = mutableListOf<DeviceAudioTrack>()
                val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection =
                    arrayOf(
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
                        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?",
                        arrayOf("10000"),
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
                            val uri = ContentUris.withAppendedId(collection, id)
                            result +=
                                DeviceAudioTrack(
                                    id = id,
                                    title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                                    artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() } ?: "Unknown artist",
                                    album = cursor.getString(albumColumn)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                                    durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L),
                                    albumId = cursor.getLong(albumIdColumn),
                                    mimeType = cursor.getString(mimeColumn),
                                    uri = uri,
                                )
                        }
                    }
                } catch (error: SecurityException) {
                    loadError = "Allow audio access to scan music on this device."
                } catch (error: Exception) {
                    loadError = error.localizedMessage ?: "Unable to read audio files."
                }
                result
            }
        loading = false
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) loadTracks()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("On-device music", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (hasPermission) "${tracks.size} tracks on this device" else "Play music stored on your phone or SD card",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                enabled = hasPermission && !loading,
                onClick = { kotlinx.coroutines.MainScope().let { scope -> scope.launch { loadTracks() } } },
            ) {
                Text("Refresh")
            }
        }

        when {
            !hasPermission -> {
                Surface(
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
                        Button(onClick = { permissionLauncher.launch(permission) }) {
                            Text("Grant permission")
                        }
                    }
                }
            }
            loading -> BoxedLoading()
            loadError != null -> Text(loadError.orEmpty(), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            tracks.isEmpty() -> Text(
                "No music files found. Add audio files to your device and refresh.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(tracks, key = { it.id }) { track ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val connection = playerConnection ?: return@clickable
                                val items = tracks.map { it.toMediaItem() }
                                val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                connection.playQueue(
                                    ListQueue(
                                        title = "On-device music",
                                        items = items,
                                        startIndex = index,
                                    ),
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
                                Text("▶", modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxedLoading() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

private fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
