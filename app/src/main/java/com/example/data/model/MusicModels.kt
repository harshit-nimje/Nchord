package com.example.data.model

import androidx.compose.runtime.Immutable

enum class AudioSourceType(val shortTag: String) {
    YTM_ADFREE("YTM"),
    SPOTIFY("SPOTIFY"),
    OPEN_AAC("MASTER"),
    LOSSLESS_FLAC("LOSSLESS"),
    LOCAL_FILE("DEVICE")
}

/**
 * BitChord-inspired Audio Quality Preferences ranging from Data Saver / Standard YTM
 * up to Studio 320kbps and Hi-Res Lossless FLAC / LDAC 990kbps Best Quality.
 */
enum class AudioQualityTier(
    val id: String,
    val title: String,
    val shortBadge: String,
    val bitrateSummary: String,
    val technicalDetail: String
) {
    DATA_SAVER(
        id = "DATA_SAVER",
        title = "Data Saver",
        shortBadge = "96K ECO",
        bitrateSummary = "64–96 kbps Opus / HE-AAC",
        technicalDetail = "Minimal cellular bandwidth • Fast instant start"
    ),
    YTM_NORMAL(
        id = "YTM_NORMAL",
        title = "YouTube Music Normal",
        shortBadge = "YTM 160K",
        bitrateSummary = "128–160 kbps Opus / AAC",
        technicalDetail = "Standard YouTube Music adaptive stream"
    ),
    STUDIO_320(
        id = "STUDIO_320",
        title = "High Quality Master",
        shortBadge = "HQ 320K",
        bitrateSummary = "256–320 kbps AAC-LC",
        technicalDetail = "Unthrottled high-bitrate studio master"
    ),
    LOSSLESS_LDAC(
        id = "LOSSLESS_LDAC",
        title = "Hi-Res Lossless • LDAC Best",
        shortBadge = "LOSSLESS LDAC",
        bitrateSummary = "Up to 24-bit/96kHz FLAC • 990 kbps",
        technicalDetail = "BitChord lossless FLAC engine + Hi-Res LDAC / USB DAC path"
    );

    companion object {
        fun fromId(raw: String?): AudioQualityTier {
            return entries.firstOrNull { it.id == raw } ?: LOSSLESS_LDAC
        }
    }
}

@Immutable
data class StreamQualityTelemetry(
    val tier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC,
    val codecLabel: String = "AAC-LC / FLAC",
    val containerLabel: String = "MP4 / FLAC",
    val bitrateKbps: Int = 320,
    val sampleRateHz: Int = 48_000,
    val bitDepth: Int = 24,
    val outputRoute: String = "System Stereo"
)

@Immutable
data class TrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val audioStreamUrl: String?,
    val durationMs: Long,
    val sourceType: AudioSourceType = AudioSourceType.YTM_ADFREE,
    val youtubeVideoId: String? = null,
    val externalSpotifyUri: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val spotifyTrackId: String? = null,
    val streamQualityBadge: String? = null,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null
)

@Immutable
data class SuggestedPlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val artworkUrl: String,
    val trackCount: Int,
    val seedQuery: String = "",
    val externalPlaylistId: String? = null,
    val isSpotifyPlaylist: Boolean = false,
    val isYoutubePlaylist: Boolean = false,
    val preloadedTracks: List<TrackItem> = emptyList(),
    val accentHex: Long = 0xFFD71921
)

@Immutable
data class UserPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val coverArtworkUrl: String = "",
    val mosaicUrls: List<String> = emptyList(),
    val trackCount: Int = 0,
    val spotifyPlaylistId: String? = null,
    val youtubePlaylistId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val pinnedAt: Long = 0L
)

enum class RecentTileKind {
    LIKED_SONGS,
    ON_REPEAT,
    DOWNLOADS,
    USER_PLAYLIST,
    SPOTIFY_PLAYLIST,
    CURATED_PLAYLIST
}

@Immutable
data class RecentGridTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val mosaicUrls: List<String> = emptyList(),
    val kind: RecentTileKind,
    val referenceId: String = "",
    val isPinned: Boolean = false
)

enum class HomeFeedFilter(val label: String) {
    ALL("All"),
    PLAYLISTS("Playlists"),
    SPOTIFY("Spotify"),
    YOUTUBE("YouTube"),
    OFFLINE("Offline")
}

@Immutable
data class ScrobblerConfigAndState(
    val activeProvider: String = "LASTFM", // LASTFM or LISTENBRAINZ
    val lastFmUsername: String = "",
    val lastFmConnected: Boolean = false,
    val hasLastFmSessionKey: Boolean = false,
    val listenBrainzUsername: String = "",
    val listenBrainzConnected: Boolean = false,
    val remotePlayCount: Int = 0,
    val pendingOfflineScrobbles: Int = 0,
    val remoteTopArtists: List<Pair<String, Int>> = emptyList(),
    val remoteRecentTracks: List<String> = emptyList(),
    val isSyncing: Boolean = false,
    val statusMessage: String? = null
)

@Immutable
data class ArtistInsightStat(
    val name: String,
    val playCount: Int,
    val minutes: Int,
    val artworkUrl: String
)

@Immutable
data class InsightsSnapshot(
    val totalMinutes: Long = 0L,
    val totalPlays: Int = 0,
    val offlinePlays: Int = 0,
    val downloadedTracksCount: Int = 0,
    val uniqueArtistsCount: Int = 0,
    val streakDays: Int = 1,
    val peakHour: Int = 22,
    val collectorArchetype: String = "NOCTURNAL VINYL",
    val hourlyMatrix: List<Float> = List(24) { 0f },
    val topArtists: List<ArtistInsightStat> = emptyList(),
    val topTrack: TrackItem? = null,
    val topTrackPlays: Int = 0
)

enum class CollectionPageType {
    USER_PLAYLIST,
    CURATED_PLAYLIST,
    SPOTIFY_PLAYLIST,
    YOUTUBE_PLAYLIST,
    ALBUM,
    ARTIST
}

@Immutable
data class CollectionPageState(
    val id: String,
    val type: CollectionPageType,
    val title: String,
    val subtitle: String,
    val badge: String,
    val artworkUrl: String,
    val mosaicUrls: List<String> = emptyList(),
    val tracks: List<TrackItem> = emptyList(),
    val relatedPlaylists: List<SuggestedPlaylist> = emptyList(),
    val isLoading: Boolean = false,
    val userPlaylistId: String? = null,
    val spotifyPlaylistId: String? = null,
    val artistNameForNav: String? = null
)

@Immutable
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val isSynced: Boolean = true
)

@Immutable
data class SpotifyProfileState(
    val isConnected: Boolean = false,
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val productTier: String = "",
    val followers: Int = 0,
    val savedTracksCount: Int = 0,
    val syncedPlaylistsCount: Int = 0,
    val topArtists: List<String> = emptyList(),
    val activeDeviceName: String? = null,
    val currentlyPlayingTitle: String? = null,
    val currentlyPlayingArtist: String? = null
)

@Immutable
data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val trackCount: Int,
    val ownerName: String
)

enum class RepeatModeState {
    OFF,
    ALL,
    ONE
}

@Immutable
data class GlyphSettings(
    val onboardingCompleted: Boolean = false,
    val ytmEngineEnabled: Boolean = true,
    val customPipedInstance: String = "https://pipedapi.kavin.rocks",
    val userSpotifyClientId: String = "",
    val ytmConnectedPlaylistUrl: String = "",
    val audioQualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC,
    val mobileAudioQualityTier: AudioQualityTier = AudioQualityTier.STUDIO_320,
    val bitPerfectUsbDac: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val oledDarkMode: Boolean = true,
    val glyphReactiveLeds: Boolean = true,
    val automixEnabled: Boolean = true,
    val tactileHaptics: Boolean = true
)
