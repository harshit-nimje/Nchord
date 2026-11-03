package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class SpotifyTokenResponseDto(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Long,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "scope") val scope: String? = null
)

interface SpotifyAccountsApi {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun exchangeAuthorizationCode(
        @Field("client_id") clientId: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String
    ): SpotifyTokenResponseDto

    @FormUrlEncoded
    @POST("api/token")
    suspend fun refreshAccessToken(
        @Field("client_id") clientId: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): SpotifyTokenResponseDto
}

@JsonClass(generateAdapter = true)
data class SpotifyImageDto(
    @Json(name = "url") val url: String? = null,
    @Json(name = "height") val height: Int? = null,
    @Json(name = "width") val width: Int? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyFollowersDto(
    @Json(name = "total") val total: Int = 0
)

@JsonClass(generateAdapter = true)
data class SpotifyUserDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "product") val product: String? = null,
    @Json(name = "images") val images: List<SpotifyImageDto>? = null,
    @Json(name = "followers") val followers: SpotifyFollowersDto? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyArtistSimpleDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String = "Unknown Artist"
)

@JsonClass(generateAdapter = true)
data class SpotifyAlbumSimpleDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String = "",
    @Json(name = "images") val images: List<SpotifyImageDto>? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyTrackDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "uri") val uri: String? = null,
    @Json(name = "preview_url") val previewUrl: String? = null,
    @Json(name = "duration_ms") val durationMs: Long? = null,
    @Json(name = "artists") val artists: List<SpotifyArtistSimpleDto>? = null,
    @Json(name = "album") val album: SpotifyAlbumSimpleDto? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistTracksMetaDto(
    @Json(name = "total") val total: Int = 0
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistOwnerDto(
    @Json(name = "display_name") val displayName: String? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaylistDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "images") val images: List<SpotifyImageDto>? = null,
    @Json(name = "tracks") val tracks: SpotifyPlaylistTracksMetaDto? = null,
    @Json(name = "owner") val owner: SpotifyPlaylistOwnerDto? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyPagingPlaylistsDto(
    @Json(name = "items") val items: List<SpotifyPlaylistDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SpotifySavedTrackWrapperDto(
    @Json(name = "track") val track: SpotifyTrackDto? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyPagingSavedTracksDto(
    @Json(name = "items") val items: List<SpotifySavedTrackWrapperDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SpotifyPagingTracksDto(
    @Json(name = "items") val items: List<SpotifyTrackDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SpotifySearchTracksResponseDto(
    @Json(name = "tracks") val tracks: SpotifyPagingTracksDto? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyDeviceDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "is_active") val isActive: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class SpotifyPlaybackStateDto(
    @Json(name = "is_playing") val isPlaying: Boolean? = null,
    @Json(name = "device") val device: SpotifyDeviceDto? = null,
    @Json(name = "item") val item: SpotifyTrackDto? = null
)

interface SpotifyWebApi {
    @GET("v1/me")
    suspend fun getCurrentProfile(
        @Header("Authorization") bearer: String
    ): SpotifyUserDto

    @GET("v1/me/playlists")
    suspend fun getUserPlaylists(
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 20
    ): SpotifyPagingPlaylistsDto

    @GET("v1/playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") bearer: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 35
    ): SpotifyPagingSavedTracksDto

    @GET("v1/me/tracks")
    suspend fun getSavedTracks(
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 30
    ): SpotifyPagingSavedTracksDto

    @GET("v1/me/top/tracks")
    suspend fun getTopTracks(
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 20,
        @Query("time_range") timeRange: String = "short_term"
    ): SpotifyPagingTracksDto

    @GET("v1/search")
    suspend fun searchTracks(
        @Header("Authorization") bearer: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 25
    ): SpotifySearchTracksResponseDto

    @GET("v1/me/player")
    suspend fun getPlaybackState(
        @Header("Authorization") bearer: String
    ): Response<SpotifyPlaybackStateDto>

    @POST("v1/me/player/next")
    suspend fun skipNextRemote(
        @Header("Authorization") bearer: String
    ): Response<Unit>

    @POST("v1/me/player/previous")
    suspend fun skipPreviousRemote(
        @Header("Authorization") bearer: String
    ): Response<Unit>

    @PUT("v1/me/player/pause")
    suspend fun pauseRemote(
        @Header("Authorization") bearer: String
    ): Response<Unit>

    @PUT("v1/me/player/play")
    suspend fun resumeRemote(
        @Header("Authorization") bearer: String
    ): Response<Unit>
}
