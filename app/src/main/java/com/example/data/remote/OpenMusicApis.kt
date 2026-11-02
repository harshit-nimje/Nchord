package com.example.data.remote

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

data class ItunesSearchResponse(
    @Json(name = "resultCount") val resultCount: Int = 0,
    @Json(name = "results") val results: List<ItunesTrackDto> = emptyList()
)

data class ItunesTrackDto(
    @Json(name = "trackId") val trackId: Long? = null,
    @Json(name = "trackName") val trackName: String? = null,
    @Json(name = "artistName") val artistName: String? = null,
    @Json(name = "collectionName") val collectionName: String? = null,
    @Json(name = "artworkUrl100") val artworkUrl100: String? = null,
    @Json(name = "previewUrl") val previewUrl: String? = null,
    @Json(name = "trackTimeMillis") val trackTimeMillis: Long? = null
)

interface ItunesMusicApi {
    @GET("search")
    suspend fun searchSongs(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 25
    ): ItunesSearchResponse
}

// Piped / YouTube Music Ad-Free Full-Track Streaming DTOs
data class PipedSearchResponseDto(
    @Json(name = "items") val items: List<PipedSearchItemDto> = emptyList()
)

data class PipedSearchItemDto(
    @Json(name = "url") val url: String? = null, // e.g., "/watch?v=VIDEO_ID"
    @Json(name = "title") val title: String? = null,
    @Json(name = "uploaderName") val uploaderName: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "duration") val duration: Long? = null
)

data class PipedStreamResponseDto(
    @Json(name = "title") val title: String? = null,
    @Json(name = "uploader") val uploader: String? = null,
    @Json(name = "thumbnailUrl") val thumbnailUrl: String? = null,
    @Json(name = "duration") val duration: Long? = null,
    @Json(name = "audioStreams") val audioStreams: List<PipedAudioStreamDto> = emptyList()
)

data class PipedAudioStreamDto(
    @Json(name = "url") val url: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "bitrate") val bitrate: Int? = null,
    @Json(name = "codec") val codec: String? = null
)

interface YouTubePipedApi {
    @GET
    suspend fun searchMusicSongs(
        @Url fullSearchUrl: String
    ): PipedSearchResponseDto

    @GET
    suspend fun getVideoStreams(
        @Url fullStreamsUrl: String
    ): PipedStreamResponseDto
}

data class LrcLibResponseDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "trackName") val trackName: String? = null,
    @Json(name = "artistName") val artistName: String? = null,
    @Json(name = "albumName") val albumName: String? = null,
    @Json(name = "duration") val duration: Double? = null,
    @Json(name = "plainLyrics") val plainLyrics: String? = null,
    @Json(name = "syncedLyrics") val syncedLyrics: String? = null
)

interface LrcLibApi {
    @GET("api/get")
    suspend fun getExactLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("duration") durationSeconds: Int? = null
    ): LrcLibResponseDto

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String
    ): List<LrcLibResponseDto>

    @GET("api/search")
    suspend fun searchLyricsByQuery(
        @Query("q") query: String
    ): List<LrcLibResponseDto>
}
