package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AudioQualityTier
import com.example.data.model.GlyphSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.nothingDataStore by preferencesDataStore(name = "nothing_chord_prefs")

class NothingPreferencesStore(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val YTM_ENGINE_ENABLED = booleanPreferencesKey("ytm_engine_enabled")
        val CUSTOM_PIPED_INSTANCE = stringPreferencesKey("custom_piped_instance")
        val USER_SPOTIFY_CLIENT_ID = stringPreferencesKey("user_spotify_client_id")
        val YTM_CONNECTED_PLAYLIST_URL = stringPreferencesKey("ytm_connected_playlist_url")

        val AUDIO_QUALITY_TIER = stringPreferencesKey("audio_quality_tier")
        val MOBILE_AUDIO_QUALITY_TIER = stringPreferencesKey("mobile_audio_quality_tier")
        val BIT_PERFECT_USB_DAC = booleanPreferencesKey("bit_perfect_usb_dac")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")

        val OLED_DARK_MODE = booleanPreferencesKey("oled_dark_mode")
        val GLYPH_REACTIVE = booleanPreferencesKey("glyph_reactive_leds")
        val AUTOMIX_ENABLED = booleanPreferencesKey("automix_enabled")
        val TACTILE_HAPTICS = booleanPreferencesKey("tactile_haptics")

        val PKCE_VERIFIER = stringPreferencesKey("spotify_pkce_verifier")
        val SPOTIFY_ACCESS_TOKEN = stringPreferencesKey("spotify_access_token")
        val SPOTIFY_REFRESH_TOKEN = stringPreferencesKey("spotify_refresh_token")
        val SPOTIFY_EXPIRES_AT = longPreferencesKey("spotify_expires_at")

        val SCROBBLER_PROVIDER = stringPreferencesKey("scrobbler_provider")
        val LASTFM_USERNAME = stringPreferencesKey("lastfm_username")
        val LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
        val LISTENBRAINZ_USERNAME = stringPreferencesKey("listenbrainz_username")
        val LISTENBRAINZ_TOKEN = stringPreferencesKey("listenbrainz_token")
    }

    data class StoredScrobblerCreds(
        val provider: String = "LASTFM",
        val lastFmUsername: String = "",
        val lastFmSessionKey: String = "",
        val listenBrainzUsername: String = "",
        val listenBrainzToken: String = ""
    )

    val scrobblerCredsFlow: Flow<StoredScrobblerCreds> = context.nothingDataStore.data.map { prefs ->
        StoredScrobblerCreds(
            provider = prefs[Keys.SCROBBLER_PROVIDER] ?: "LASTFM",
            lastFmUsername = prefs[Keys.LASTFM_USERNAME] ?: "",
            lastFmSessionKey = prefs[Keys.LASTFM_SESSION_KEY] ?: "",
            listenBrainzUsername = prefs[Keys.LISTENBRAINZ_USERNAME] ?: "",
            listenBrainzToken = prefs[Keys.LISTENBRAINZ_TOKEN] ?: ""
        )
    }

    val glyphSettingsFlow: Flow<GlyphSettings> = context.nothingDataStore.data.map { prefs ->
        GlyphSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            ytmEngineEnabled = prefs[Keys.YTM_ENGINE_ENABLED] ?: true,
            customPipedInstance = prefs[Keys.CUSTOM_PIPED_INSTANCE] ?: "https://pipedapi.kavin.rocks",
            userSpotifyClientId = prefs[Keys.USER_SPOTIFY_CLIENT_ID] ?: "",
            ytmConnectedPlaylistUrl = prefs[Keys.YTM_CONNECTED_PLAYLIST_URL] ?: "",
            audioQualityTier = AudioQualityTier.fromId(prefs[Keys.AUDIO_QUALITY_TIER]),
            mobileAudioQualityTier = AudioQualityTier.fromId(
                prefs[Keys.MOBILE_AUDIO_QUALITY_TIER] ?: AudioQualityTier.STUDIO_320.id
            ),
            bitPerfectUsbDac = prefs[Keys.BIT_PERFECT_USB_DAC] ?: true,
            playbackSpeed = (prefs[Keys.PLAYBACK_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
            oledDarkMode = prefs[Keys.OLED_DARK_MODE] ?: true,
            glyphReactiveLeds = prefs[Keys.GLYPH_REACTIVE] ?: true,
            automixEnabled = prefs[Keys.AUTOMIX_ENABLED] ?: true,
            tactileHaptics = prefs[Keys.TACTILE_HAPTICS] ?: true
        )
    }

    val spotifyAccessTokenFlow: Flow<String?> = context.nothingDataStore.data.map { prefs ->
        prefs[Keys.SPOTIFY_ACCESS_TOKEN]
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.nothingDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setYtmEngineEnabled(enabled: Boolean) {
        context.nothingDataStore.edit { it[Keys.YTM_ENGINE_ENABLED] = enabled }
    }

    suspend fun setCustomPipedInstance(url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isNotEmpty()) {
            context.nothingDataStore.edit { it[Keys.CUSTOM_PIPED_INSTANCE] = clean }
        }
    }

    suspend fun setUserSpotifyClientId(clientId: String) {
        context.nothingDataStore.edit { it[Keys.USER_SPOTIFY_CLIENT_ID] = clientId.trim() }
    }

    suspend fun getUserSpotifyClientId(): String {
        return context.nothingDataStore.data.first()[Keys.USER_SPOTIFY_CLIENT_ID]?.trim().orEmpty()
    }

    suspend fun setYtmConnectedPlaylistUrl(url: String) {
        context.nothingDataStore.edit { it[Keys.YTM_CONNECTED_PLAYLIST_URL] = url.trim() }
    }

    suspend fun setAudioQualityTier(tier: AudioQualityTier) {
        context.nothingDataStore.edit { it[Keys.AUDIO_QUALITY_TIER] = tier.id }
    }

    suspend fun setMobileAudioQualityTier(tier: AudioQualityTier) {
        context.nothingDataStore.edit { it[Keys.MOBILE_AUDIO_QUALITY_TIER] = tier.id }
    }

    suspend fun setBitPerfectUsbDac(enabled: Boolean) {
        context.nothingDataStore.edit { it[Keys.BIT_PERFECT_USB_DAC] = enabled }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.nothingDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setOledDarkMode(enabled: Boolean) {
        context.nothingDataStore.edit { it[Keys.OLED_DARK_MODE] = enabled }
    }

    suspend fun setGlyphReactive(enabled: Boolean) {
        context.nothingDataStore.edit { it[Keys.GLYPH_REACTIVE] = enabled }
    }

    suspend fun setAutomixEnabled(enabled: Boolean) {
        context.nothingDataStore.edit { it[Keys.AUTOMIX_ENABLED] = enabled }
    }

    suspend fun setTactileHaptics(enabled: Boolean) {
        context.nothingDataStore.edit { it[Keys.TACTILE_HAPTICS] = enabled }
    }

    suspend fun savePkceVerifier(verifier: String) {
        context.nothingDataStore.edit { it[Keys.PKCE_VERIFIER] = verifier }
    }

    suspend fun getPkceVerifier(): String? {
        return context.nothingDataStore.data.first()[Keys.PKCE_VERIFIER]
    }

    suspend fun saveSpotifyTokens(accessToken: String, refreshToken: String?, expiresInSec: Long) {
        val expiresAt = System.currentTimeMillis() + (expiresInSec * 1000L) - 60_000L
        context.nothingDataStore.edit { prefs ->
            prefs[Keys.SPOTIFY_ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) {
                prefs[Keys.SPOTIFY_REFRESH_TOKEN] = refreshToken
            }
            prefs[Keys.SPOTIFY_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun getSpotifyTokenTuple(): Triple<String?, String?, Long> {
        val prefs = context.nothingDataStore.data.first()
        return Triple(
            prefs[Keys.SPOTIFY_ACCESS_TOKEN],
            prefs[Keys.SPOTIFY_REFRESH_TOKEN],
            prefs[Keys.SPOTIFY_EXPIRES_AT] ?: 0L
        )
    }

    suspend fun clearSpotifySession() {
        context.nothingDataStore.edit { prefs ->
            prefs.remove(Keys.SPOTIFY_ACCESS_TOKEN)
            prefs.remove(Keys.SPOTIFY_REFRESH_TOKEN)
            prefs.remove(Keys.SPOTIFY_EXPIRES_AT)
            prefs.remove(Keys.PKCE_VERIFIER)
        }
    }

    suspend fun saveLastFmCredentials(username: String, sessionKey: String) {
        context.nothingDataStore.edit { prefs ->
            prefs[Keys.SCROBBLER_PROVIDER] = "LASTFM"
            prefs[Keys.LASTFM_USERNAME] = username.trim()
            prefs[Keys.LASTFM_SESSION_KEY] = sessionKey.trim()
        }
    }

    suspend fun saveListenBrainzCredentials(username: String, token: String) {
        context.nothingDataStore.edit { prefs ->
            prefs[Keys.SCROBBLER_PROVIDER] = "LISTENBRAINZ"
            prefs[Keys.LISTENBRAINZ_USERNAME] = username.trim()
            prefs[Keys.LISTENBRAINZ_TOKEN] = token.trim()
        }
    }

    suspend fun getScrobblerCreds(): StoredScrobblerCreds {
        return scrobblerCredsFlow.first()
    }

    suspend fun clearScrobblerSession() {
        context.nothingDataStore.edit { prefs ->
            prefs.remove(Keys.LASTFM_USERNAME)
            prefs.remove(Keys.LASTFM_SESSION_KEY)
            prefs.remove(Keys.LISTENBRAINZ_USERNAME)
            prefs.remove(Keys.LISTENBRAINZ_TOKEN)
        }
    }
}
