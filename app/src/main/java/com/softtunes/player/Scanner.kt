package com.softtunes.player

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile

object Scanner {
    private val audioExt = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "oga", "opus", "alac", "aif", "aiff", "wma", "mka"
    )

    private fun clean(s: String?, fallback: String) =
        if (s.isNullOrBlank() || s == "<unknown>") fallback else s

    /** Everything Android's media index knows about on shared storage. */
    fun mediaStore(ctx: Context): List<Song> {
        val out = mutableListOf<Song>()
        val coll = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DISPLAY_NAME,
        )
        ctx.contentResolver.query(
            coll, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val name = c.getString(iName) ?: ""
                out += Song(
                    uri = ContentUris.withAppendedId(coll, c.getLong(iId)).toString(),
                    title = clean(c.getString(iTitle), name.substringBeforeLast('.')),
                    artist = clean(c.getString(iArtist), "Unknown artist"),
                    album = clean(c.getString(iAlbum), "Unknown album"),
                    durationMs = c.getLong(iDur),
                    format = name.substringAfterLast('.', "").uppercase(),
                )
            }
        }
        return out
    }

    /** Walks a folder the user picked with the system folder picker (SAF). */
    fun tree(ctx: Context, tree: Uri): List<Song> {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return emptyList()
        val out = mutableListOf<Song>()
        val mmr = MediaMetadataRetriever()

        fun walk(dir: DocumentFile) {
            for (f in dir.listFiles()) {
                if (f.isDirectory) { walk(f); continue }
                val name = f.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in audioExt && f.type?.startsWith("audio/") != true) continue
                try {
                    mmr.setDataSource(ctx, f.uri)
                    out += Song(
                        uri = f.uri.toString(),
                        title = clean(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), name.substringBeforeLast('.')),
                        artist = clean(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST), "Unknown artist"),
                        album = clean(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM), "Unknown album"),
                        durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                        format = ext.uppercase(),
                    )
                } catch (_: Exception) { /* unreadable file: skip */ }
            }
        }
        walk(root)
        mmr.release()
        return out
    }
}
