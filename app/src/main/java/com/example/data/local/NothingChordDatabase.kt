package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Saved / Liked Tracks
    @Query("SELECT * FROM saved_tracks ORDER BY savedAt DESC")
    fun observeSavedTracks(): Flow<List<SavedTrackEntity>>

    @Query("SELECT id FROM saved_tracks")
    fun observeSavedTrackIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedTrack(track: SavedTrackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSavedTracksIgnore(tracks: List<SavedTrackEntity>)

    @Query("DELETE FROM saved_tracks WHERE id = :trackId")
    suspend fun deleteSavedTrack(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_tracks WHERE id = :trackId)")
    suspend fun isTrackSaved(trackId: String): Boolean

    // Scrobble & Listening Stats
    @Query("SELECT * FROM scrobble_history ORDER BY playedAt DESC LIMIT 60")
    fun observeRecentScrobbles(): Flow<List<ScrobbleEntity>>

    @Query("SELECT * FROM scrobble_history ORDER BY playedAt DESC LIMIT 500")
    fun observeAllScrobbles(): Flow<List<ScrobbleEntity>>

    @Query("SELECT COUNT(*) FROM scrobble_history")
    fun observeTotalScrobbleCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scrobble_history WHERE syncedToScrobbler = 0")
    fun observePendingScrobbleCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM scrobble_history")
    fun observeTotalStreamedMs(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScrobble(scrobble: ScrobbleEntity): Long

    @Query("SELECT * FROM scrobble_history WHERE syncedToScrobbler = 0 ORDER BY playedAt ASC LIMIT :limit")
    suspend fun getUnsyncedScrobbles(limit: Int = 40): List<ScrobbleEntity>

    @Query("UPDATE scrobble_history SET syncedToScrobbler = 1 WHERE scrobbleId IN (:ids)")
    suspend fun markScrobblesSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM scrobble_history WHERE trackId = :trackId")
    suspend fun countScrobblesForTrack(trackId: String): Int

    @Query("DELETE FROM scrobble_history")
    suspend fun clearScrobbles()

    // In-App Offline Downloads
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun observeDownloadedTracks(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT id FROM downloaded_tracks")
    fun observeDownloadedTrackIds(): Flow<List<String>>

    @Query("SELECT * FROM downloaded_tracks WHERE id = :trackId LIMIT 1")
    suspend fun getDownloadedTrack(trackId: String): DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadedTrack(track: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE id = :trackId")
    suspend fun deleteDownloadedTrack(trackId: String)

    // Library Pinning (Max 4 enforced in repository)
    @Query("SELECT * FROM pinned_library_items ORDER BY pinnedAt ASC")
    fun observePinnedItems(): Flow<List<PinnedLibraryItemEntity>>

    @Query("SELECT COUNT(*) FROM pinned_library_items")
    suspend fun countPinnedItems(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_library_items WHERE itemKey = :itemKey)")
    suspend fun isItemPinned(itemKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedItem(item: PinnedLibraryItemEntity)

    @Query("DELETE FROM pinned_library_items WHERE itemKey = :itemKey")
    suspend fun deletePinnedItem(itemKey: String)

    // Recently Played Playlists & Collections (Home Top Grid)
    @Query("SELECT * FROM recent_collections ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecentCollections(limit: Int = 8): Flow<List<RecentCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentCollection(entry: RecentCollectionEntity)

    @Query("DELETE FROM recent_collections WHERE referenceId = :referenceId")
    suspend fun deleteRecentCollectionByRef(referenceId: String)

    @Query("DELETE FROM recent_collections WHERE id = :id")
    suspend fun deleteRecentCollectionById(id: String)

    // User & Synced Playlists
    @Query("SELECT * FROM user_playlists ORDER BY updatedAt DESC")
    fun observeUserPlaylists(): Flow<List<UserPlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId, position ASC, addedAt ASC")
    fun observeAllPlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun observeTracksForPlaylist(playlistId: String): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    suspend fun getTracksForPlaylistOnce(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT * FROM user_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getUserPlaylistById(playlistId: String): UserPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserPlaylist(playlist: UserPlaylistEntity)

    @Query("UPDATE user_playlists SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updateUserPlaylistDetails(
        playlistId: String,
        name: String,
        description: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE user_playlists SET coverArtworkUrl = :artworkUrl, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updateUserPlaylistCover(
        playlistId: String,
        artworkUrl: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM user_playlists WHERE id = :playlistId")
    suspend fun deleteUserPlaylist(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteAllTracksForPlaylist(playlistId: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxTrackPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistTrack(entry: PlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistTracks(entries: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
}

@Database(
    entities = [
        SavedTrackEntity::class,
        ScrobbleEntity::class,
        UserPlaylistEntity::class,
        PlaylistTrackEntity::class,
        DownloadedTrackEntity::class,
        PinnedLibraryItemEntity::class,
        RecentCollectionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NothingChordDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: NothingChordDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_tracks_savedAt` ON `saved_tracks` (`savedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scrobble_history_playedAt` ON `scrobble_history` (`playedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scrobble_history_syncedToScrobbler_playedAt` ON `scrobble_history` (`syncedToScrobbler`, `playedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scrobble_history_trackId` ON `scrobble_history` (`trackId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_user_playlists_updatedAt` ON `user_playlists` (`updatedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_position_addedAt` ON `playlist_tracks` (`playlistId`, `position`, `addedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_downloaded_tracks_downloadedAt` ON `downloaded_tracks` (`downloadedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recent_collections_playedAt` ON `recent_collections` (`playedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recent_collections_referenceId` ON `recent_collections` (`referenceId`)"
                )
            }
        }

        fun getInstance(context: Context): NothingChordDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NothingChordDatabase::class.java,
                    "nothing_chord_vault.db"
                ).addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
