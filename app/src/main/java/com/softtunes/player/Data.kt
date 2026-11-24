package com.softtunes.player

import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Immutable
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val format: String,
    val dateAdded: Long = 0,
)

@Immutable
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

@Database(entities = [Song::class, Playlist::class, PlaylistEntry::class], version = 2, exportSchema = false)
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

/** v1 -> v2 adds "date added" without touching playlists. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
    }
}

enum class SortOption(val label: String) {
    NAME_ASC("Name (A–Z)"), NAME_DESC("Name (Z–A)"),
    ARTIST_ASC("Artist (A–Z)"), ARTIST_DESC("Artist (Z–A)"),
    ALBUM_ASC("Album (A–Z)"), ALBUM_DESC("Album (Z–A)"),
    DATE_DESC("Date added (newest first)"), DATE_ASC("Date added (oldest first)"),
}

private val ci: Comparator<String> = String.CASE_INSENSITIVE_ORDER

fun sortSongs(list: List<Song>, o: SortOption): List<Song> {
    val byTitle = compareBy<Song, String>(ci) { it.title }
    val byArtist = compareBy<Song, String>(ci) { it.artist }
    val byAlbum = compareBy<Song, String>(ci) { it.album }
    return when (o) {
        SortOption.NAME_ASC -> list.sortedWith(byTitle)
        SortOption.NAME_DESC -> list.sortedWith(byTitle.reversed())
        SortOption.ARTIST_ASC -> list.sortedWith(byArtist.then(byTitle))
        SortOption.ARTIST_DESC -> list.sortedWith(byArtist.reversed().then(byTitle))
        SortOption.ALBUM_ASC -> list.sortedWith(byAlbum.then(byTitle))
        SortOption.ALBUM_DESC -> list.sortedWith(byAlbum.reversed().then(byTitle))
        SortOption.DATE_DESC -> list.sortedWith(compareByDescending<Song> { it.dateAdded }.then(byTitle))
        SortOption.DATE_ASC -> list.sortedWith(compareBy<Song> { it.dateAdded }.then(byTitle))
    }
}
