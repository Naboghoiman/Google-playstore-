package com.example.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.model.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans and provides audio tracks from device MediaStore.
 */
class MediaStoreRepository(private val context: Context) {

    suspend fun loadDeviceTracks(): List<TrackInfo> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackInfo>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val durationMs = cursor.getInt(durationColumn)

                    if (durationMs > 5000) { // filter out tiny notification sounds
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        tracks.add(
                            TrackInfo(
                                id = "media_$id",
                                title = title,
                                artist = artist,
                                durationSeconds = durationMs / 1000,
                                bpm = 124.0, // initial estimate, analyzed on decode
                                musicalKey = "8A",
                                genre = "Device Audio",
                                uriString = contentUri.toString(),
                                isDemoTrack = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext tracks
    }
}
