package com.example.data.remote

import android.net.Uri
import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {
    const val SPOTIFY_REDIRECT_URI = "nothingchord://callback"
    const val SPOTIFY_SCOPES =
        "user-read-private user-read-email user-library-read user-library-modify " +
            "playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private " +
            "user-top-read user-read-playback-state user-modify-playback-state user-read-recently-played"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
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
            .connectTimeout(4200, TimeUnit.MILLISECONDS)
            .readTimeout(6200, TimeUnit.MILLISECONDS)
            .writeTimeout(5000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val losslessProbeClient: OkHttpClient by lazy {
        fastHttpClient.newBuilder()
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

    fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun buildAuthorizationUri(clientId: String, codeChallenge: String): Uri {
        return Uri.parse("https://accounts.spotify.com/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", NetworkModule.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", NetworkModule.SPOTIFY_SCOPES)
            .build()
    }
}
