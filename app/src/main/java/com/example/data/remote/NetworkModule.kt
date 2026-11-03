package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.squareup.moshi.Moshi
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {
    const val SPOTIFY_REDIRECT_URI = "nothingchord://callback"
    const val SPOTIFY_SCOPES =
        "user-read-private user-read-email user-library-read " +
            "playlist-read-private playlist-read-collaborative " +
            "user-top-read user-read-playback-state user-modify-playback-state"

    private const val HTTP_DISK_CACHE_BYTES = 32L * 1024L * 1024L // 32 MB
    private const val ARTWORK_DISK_CACHE_BYTES = 64L * 1024L * 1024L // 64 MB

    @Volatile
    private var sharedHttpDiskCache: Cache? = null
    private val coilInitialized = AtomicBoolean(false)

    /**
     * Initializes the bounded 32 MB OkHttp HTTP cache and 64 MB Coil artwork disk/memory caches.
     * Safe and idempotent to call before first network/image access in Activity/ViewModel startup.
     */
    fun initializeRuntimeCaches(context: Context) {
        val appCtx = context.applicationContext
        if (sharedHttpDiskCache == null) {
            synchronized(this) {
                if (sharedHttpDiskCache == null) {
                    val cacheDir = File(appCtx.cacheDir, "nchord_http_cache")
                    sharedHttpDiskCache = Cache(cacheDir, HTTP_DISK_CACHE_BYTES)
                }
            }
        }
        if (coilInitialized.compareAndSet(false, true)) {
            val loader = ImageLoader.Builder(appCtx)
                .okHttpClient { fastHttpClient }
                .memoryCache {
                    MemoryCache.Builder(appCtx)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(appCtx.cacheDir, "nchord_artwork_cache"))
                        .maxSizeBytes(ARTWORK_DISK_CACHE_BYTES)
                        .build()
                }
                .crossfade(false)
                .respectCacheHeaders(false)
                .build()
            Coil.setImageLoader(loader)
        }
    }

    internal fun activeHttpCacheSizeMaxBytes(): Long? = sharedHttpDiskCache?.maxSize()

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    private val sharedPool: ConnectionPool by lazy {
        ConnectionPool(14, 5, TimeUnit.MINUTES)
    }

    private val sharedDispatcher: Dispatcher by lazy {
        Dispatcher().apply {
            maxRequests = 48
            maxRequestsPerHost = 16
        }
    }

    val fastHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedPool)
            .dispatcher(sharedDispatcher)
            .apply {
                sharedHttpDiskCache?.let { cache(it) }
            }
            .connectTimeout(4200, TimeUnit.MILLISECONDS)
            .readTimeout(6200, TimeUnit.MILLISECONDS)
            .writeTimeout(5000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val losslessProbeClient: OkHttpClient by lazy {
        fastHttpClient.newBuilder()
            .cache(null)
            .connectTimeout(2200, TimeUnit.MILLISECONDS)
            .readTimeout(2800, TimeUnit.MILLISECONDS)
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        fastHttpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    val youtubePipedApi: YouTubePipedApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://pipedapi.kavin.rocks/")
            .client(fastHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubePipedApi::class.java)
    }

    val lrcLibApi: LrcLibApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(fastHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LrcLibApi::class.java)
    }

    val spotifyAccountsApi: SpotifyAccountsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://accounts.spotify.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyAccountsApi::class.java)
    }

    val spotifyWebApi: SpotifyWebApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyWebApi::class.java)
    }
}

object SpotifyPkceHelper {
    fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun generateStateNonce(): String {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun verifyCallbackState(expectedState: String?, returnedState: String?): Boolean {
        val expected = expectedState?.trim().orEmpty()
        val returned = returnedState?.trim().orEmpty()
        if (expected.length < 16 || returned.isEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            returned.toByteArray(Charsets.UTF_8)
        )
    }

    fun buildAuthorizationUri(clientId: String, codeChallenge: String, state: String): Uri {
        return "https://accounts.spotify.com/authorize".toUri()
            .buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", NetworkModule.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", NetworkModule.SPOTIFY_SCOPES)
            .build()
    }
}
