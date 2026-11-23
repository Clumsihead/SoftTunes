package com.softtunes.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val format: String,
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "playlist_entries", primaryKeys = ["playlistId", "songUri"])
data class PlaylistEntry(val playlistId: Long, val songUri: String, val position: Int)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun songs(): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Query("DELETE FROM songs")
    suspend fun clearSongs()

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun playlists(): Flow<List<Playlist>>

    @Insert
    suspend fun insertPlaylist(p: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    suspend fun clearEntries(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addEntry(e: PlaylistEntry)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlistId = :id")
    suspend fun nextPosition(id: Long): Int

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id AND songUri = :uri")
    suspend fun removeEntry(id: Long, uri: String)

    @Query(
        "SELECT s.* FROM songs s INNER JOIN playlist_entries e ON e.songUri = s.uri " +
            "WHERE e.playlistId = :id ORDER BY e.position"
    )
    fun playlistSongs(id: Long): Flow<List<Song>>
}

@Database(entities = [Song::class, Playlist::class, PlaylistEntry::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LibraryDao
}

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(uri)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build()
    )
    .build()
