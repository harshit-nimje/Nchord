package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_tracks")
data class SavedTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val audioStreamUrl: String?,
    val durationMs: Long,
    val sourceTypeName: String,
    val externalSpotifyUri: String?,
    val albumId: String? = null,
    val artistId: String? = null,
    val spotifyTrackId: String? = null,
    val youtubeVideoId: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "scrobble_history")
data class ScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val scrobbleId: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val audioStreamUrl: String?,
    val durationMs: Long,
    val sourceTypeName: String,
    val albumId: String? = null,
    val artistId: String? = null,
    val spotifyTrackId: String? = null,
    val isOfflinePlay: Boolean = false,
    val syncedToScrobbler: Boolean = false,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_playlists")
data class UserPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val coverArtworkUrl: String = "",
    val spotifyPlaylistId: String? = null,
    val youtubePlaylistId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val audioStreamUrl: String?,
    val durationMs: Long,
    val sourceTypeName: String,
    val youtubeVideoId: String? = null,
    val externalSpotifyUri: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val spotifyTrackId: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val codecLabel: String,
    val bitrateKbps: Int,
    val durationMs: Long,
    val sourceTypeName: String,
    val youtubeVideoId: String? = null,
    val externalSpotifyUri: String? = null,
    val spotifyTrackId: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pinned_library_items")
data class PinnedLibraryItemEntity(
    @PrimaryKey val itemKey: String,
    val pinnedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_collections")
data class RecentCollectionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val kindName: String,
    val referenceId: String,
    val playedAt: Long = System.currentTimeMillis()
)
