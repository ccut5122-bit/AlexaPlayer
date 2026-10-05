package com.alexaplayer.data.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.alexaplayer.core.model.Source
import com.alexaplayer.data.local.entity.SongEntity

/** Single place that knows which permission unlocks the local library. */
object MediaPermission {

    val required: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun isGranted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * True when the user denied permanently: the system stops showing the dialog, so the
     * only route left is app settings.
     */
    fun isBlocked(context: Context): Boolean {
        if (isGranted(context)) return false
        // Without an Activity in hand the rationale flag is not readable, so treat a missing
        // grant as "not yet granted" and let the UI offer the button again.
        return required.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }
}

/** Reads audio entries out of MediaStore. Never touches the database itself. */
class MediaStoreScanner(
    private val context: Context,
) {

    /**
     * @param excludedFolders absolute folder paths the user opted out of.
     * @return local songs currently visible to the app.
     */
    fun scan(excludedFolders: Set<String> = emptySet()): List<SongEntity> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
        } else {
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.TITLE,
            android.provider.MediaStore.Audio.Media.ARTIST,
            android.provider.MediaStore.Audio.Media.ALBUM,
            android.provider.MediaStore.Audio.Media.ALBUM_ID,
            android.provider.MediaStore.Audio.Media.ARTIST_ID,
            android.provider.MediaStore.Audio.Media.DURATION,
            android.provider.MediaStore.Audio.Media.TRACK,
            android.provider.MediaStore.Audio.Media.YEAR,
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.DATE_ADDED,
            android.provider.MediaStore.Audio.Media.DATE_MODIFIED,
            android.provider.MediaStore.Audio.Media.SIZE,
            android.provider.MediaStore.Audio.Media.MIME_TYPE,
        )

        val selection =
            "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${android.provider.MediaStore.Audio.Media.DURATION} > 0"

        val result = ArrayList<SongEntity>(512)

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)
            val artistIdCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST_ID)
            val durationCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.YEAR)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
            val addedCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameCol).orEmpty()
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)).toString()

                if (excludedFolders.any { it.isNotBlank() && uri.startsWith("$it/") }) continue

                val albumId = cursor.getLong(albumIdCol)
                val seconds = cursor.getLong(durationCol).coerceAtLeast(0L)

                result += SongEntity(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: displayName.substringBeforeLast('.'),
                    artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty(),
                    album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty(),
                    albumId = albumId,
                    artistId = cursor.getLong(artistIdCol),
                    durationMs = seconds * 1000L,
                    trackNumber = cursor.getInt(trackCol),
                    year = cursor.getInt(yearCol),
                    uri = uri,
                    artworkUri = albumArtUri(albumId),
                    dateAdded = cursor.getLong(addedCol) * 1000L,
                    dateModified = cursor.getLong(modifiedCol) * 1000L,
                    sizeBytes = cursor.getLong(sizeCol),
                    mimeType = cursor.getString(mimeCol),
                    source = Source.LOCAL.name,
                )
            }
        }

        return result
    }

    companion object {
        private const val ALBUM_ART_BASE = "content://media/external/audio/albumart"

        fun albumArtUri(albumId: Long): String? = if (albumId <= 0L) {
            null
        } else {
            runCatching { ContentUris.withAppendedId(Uri.parse(ALBUM_ART_BASE), albumId).toString() }.getOrNull()
        }
    }
}
