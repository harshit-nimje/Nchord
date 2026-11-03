package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.data.local.ScrobbleEntity
import com.example.data.model.AudioSourceType
import com.example.data.model.TrackItem
import com.example.data.model.UserPlaylist
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Snapshot of the listener's evolving musical taste built from real on-device behavior:
 * - Liked/saved tracks (strong explicit preference)
 * - Offline vaulted tracks (high commitment signal)
 * - Scrobble history (exponentially recency-weighted + repeat-listen loyalty bonus + circadian hour resonance)
 * - User-curated playlists & optional synced Spotify top artists
 */
@Immutable
data class UserTasteProfile(
    val artistWeights: Map<String, Float> = emptyMap(),
    val tokenWeights: Map<String, Float> = emptyMap(),
    val trackPlayCounts: Map<String, Int> = emptyMap(),
    val savedTrackIds: Set<String> = emptySet(),
    val recentTrackIds: Set<String> = emptySet(),
    val preferredDurationSec: Int = 210,
    val totalSignalEvents: Int = 0,
    val maturityRatio: Float = 0f,
    val topAffinityArtists: List<String> = emptyList()
) {
    val hasPersonalSignal: Boolean
        get() = totalSignalEvents > 0
}

@Immutable
data class SearchPartitionResult(
    val topMatch: TrackItem?,
    val topMatchBadge: String,
    val topMatchReason: String,
    val remainingTracks: List<TrackItem>
) {
    val topMatchReasonBadge: String
        get() = topMatchBadge
}

object TasteAndSearchRankingEngine {

    private val STOP_WORDS = setOf(
        "the", "and", "feat", "ft", "with", "from", "for", "you", "your",
        "official", "audio", "video", "lyrics", "lyric", "music", "version",
        "remastered", "remaster", "single", "album", "edit", "mix"
    )

    private val DERIVATIVE_HEAVY_NOISE_PATTERNS = listOf(
        "karaoke",
        "tribute",
        "made famous",
        "originally performed",
        "in the style of",
        "cover",
        "ringtone",
        "8d audio",
        "8d ",
        "nightcore",
        "sped up",
        "speed up",
        "slowed",
        "reverb",
        "chopped",
        "type beat",
        "lullaby",
        "music box",
        "sound effect",
        "parody",
        "ai cover"
    )

    private val SECONDARY_VARIANT_PATTERNS = listOf(
        "remix",
        "vip mix",
        "extended mix",
        "club mix",
        "live at",
        "live from",
        "live in",
        "(live)",
        "instrumental",
        "acapella",
        "a cappella",
        "acoustic",
        "demo",
        "commentary"
    )

    private val GENERIC_COMPILATION_PATTERNS = listOf(
        "various artists",
        "unknown artist",
        "workout",
        "gym ",
        "sleep music",
        "relaxing",
        "party hits",
        "greatest hits compilation",
        "kids bop",
        "kidz bop"
    )

    // Curated sonic adjacency map so as a user's taste matures, NChord also discovers kindred artists
    private val SONIC_ADJACENCY: Map<String, List<String>> = mapOf(
        "the weeknd" to listOf("Kavinsky", "Gesaffelstein", "Frank Ocean", "Don Toliver"),
        "daft punk" to listOf("Justice", "Parcels", "Kavinsky", "Kaytranada"),
        "tame impala" to listOf("MGMT", "Beach House", "Pond", "Unknown Mortal Orchestra"),
        "frank ocean" to listOf("Daniel Caesar", "Steve Lacy", "SZA", "Blood Orange"),
        "fred again.." to listOf("Four Tet", "Bicep", "Overmono", "Disclosure"),
        "fred again" to listOf("Four Tet", "Bicep", "Overmono", "Disclosure"),
        "radiohead" to listOf("Thom Yorke", "Portishead", "Massive Attack", "Interpol"),
        "kendrick lamar" to listOf("Baby Keem", "JID", "Vince Staples", "Isaiah Rashad"),
        "travis scott" to listOf("Don Toliver", "Metro Boomin", "Future", "Kid Cudi"),
        "tyler, the creator" to listOf("Steve Lacy", "Frank Ocean", "Earl Sweatshirt", "Kali Uchis"),
        "nujabes" to listOf("J Dilla", "Uyama Hiroto", "Khruangbin", "Tom Misch"),
        "arctic monkeys" to listOf("The Strokes", "The Last Shadow Puppets", "Franz Ferdinand", " Cage The Elephant"),
        "deftones" to listOf("Loathe", "My Bloody Valentine", "Team Sleep", "A Perfect Circle"),
        "billie eilish" to listOf("Lorde", "FINNEAS", "Lana Del Rey", "Clairo"),
        "charli xcx" to listOf("Caroline Polachek", "SOPHIE", "Troye Sivan", "Shygirl"),
        "sza" to listOf("Summer Walker", "Kali Uchis", "Brent Faiyaz", " Cleo Sol"),
        "joji" to listOf("keshi", "Cigarettes After Sex", "d4vd", "Rich Brian")
    )

    /**
     * Builds a live [UserTasteProfile] from saved songs, scrobbles, offline vault, playlists, and Spotify top data.
     */
    fun buildTasteProfile(
        savedTracks: List<TrackItem>,
        scrobbles: List<ScrobbleEntity>,
        downloadedTracks: List<TrackItem> = emptyList(),
        userPlaylists: List<UserPlaylist> = emptyList(),
        spotifyTopArtists: List<String> = emptyList(),
        spotifyTopTracks: List<TrackItem> = emptyList(),
        nowMs: Long = System.currentTimeMillis()
    ): UserTasteProfile {
        val artistScores = HashMap<String, Float>()
        val tokenScores = HashMap<String, Float>()
        val playCounts = HashMap<String, Int>()
        val savedIds = HashSet<String>(savedTracks.size)
        val recentIds = LinkedHashSet<String>()
        var weightedDurationSumSec = 0f
        var durationWeightTotal = 0f

        fun addArtistSignal(rawArtist: String, weight: Float) {
            if (rawArtist.isBlank() || rawArtist.equals("Device Storage", ignoreCase = true)) return
            val parts = rawArtist.split(",", "&", " feat.", " ft.", " x ", " X ")
                .map { it.trim() }
                .filter { it.length >= 2 }
            parts.forEachIndexed { idx, name ->
                val norm = normalizeText(name)
                if (norm.length >= 2 && norm !in GENERIC_COMPILATION_PATTERNS) {
                    val roleFactor = if (idx == 0) 1.0f else 0.55f
                    artistScores[norm] = (artistScores[norm] ?: 0f) + weight * roleFactor
                }
            }
        }

        fun addTokenSignals(title: String, album: String, weight: Float) {
            extractMeaningfulTokens("$title $album").forEach { tok ->
                tokenScores[tok] = (tokenScores[tok] ?: 0f) + weight
            }
        }

        fun addDurationSample(durationMs: Long, weight: Float) {
            val sec = (durationMs / 1000L).toInt()
            if (sec in 75..720) {
                weightedDurationSumSec += sec * weight
                durationWeightTotal += weight
            }
        }

        // 1. Saved / Liked songs (explicit high-affinity signal)
        savedTracks.forEachIndexed { index, track ->
            savedIds.add(track.id)
            val recencyBoost = 1f + (1f / (1f + index * 0.18f)) * 0.45f
            val w = 5.4f * recencyBoost
            addArtistSignal(track.artist, w)
            addTokenSignals(track.title, track.album, w * 0.35f)
            addDurationSample(track.durationMs, 1.5f)
        }

        // 2. Offline Vault tracks (high commitment signal)
        downloadedTracks.forEach { track ->
            addArtistSignal(track.artist, 4.4f)
            addTokenSignals(track.title, track.album, 1.3f)
            addDurationSample(track.durationMs, 1.2f)
        }

        // 3. Scrobble history with exponential recency decay + replay loyalty multiplier
        val sortedScrobbles = scrobbles.sortedByDescending { it.playedAt }
        sortedScrobbles.take(6).forEach { recentIds.add(it.trackId) }

        sortedScrobbles.forEach { scrobble ->
            playCounts[scrobble.trackId] = (playCounts[scrobble.trackId] ?: 0) + 1
        }

        sortedScrobbles.forEachIndexed { idx, scrobble ->
            val ageHours = ((nowMs - scrobble.playedAt).coerceAtLeast(0L)) / 3_600_000f
            // Half-life ~ 5 days (120h) so recent obsessions surface quickly while older favorites persist
            val timeDecay = (0.38f + 0.62f * exp(-ageHours / 140f)).coerceIn(0.35f, 1.0f)
            val repeatCount = playCounts[scrobble.trackId] ?: 1
            val loyaltyBoost = 1f + (ln(repeatCount.toFloat() + 1f) * 0.42f).coerceAtMost(1.35f)
            val rankFreshness = if (idx < 12) 1.22f else 1.0f
            val w = 2.6f * timeDecay * loyaltyBoost * rankFreshness
            addArtistSignal(scrobble.artist, w)
            addTokenSignals(scrobble.title, scrobble.album, w * 0.28f)
            addDurationSample(scrobble.durationMs, timeDecay)
        }

        // 4. User Playlists metadata tokens
        userPlaylists.forEach { pl ->
            extractMeaningfulTokens("${pl.name} ${pl.description}").forEach { tok ->
                tokenScores[tok] = (tokenScores[tok] ?: 0f) + 1.8f
            }
        }

        // 5. Connected Spotify top artists & top tracks
        spotifyTopArtists.forEachIndexed { idx, artist ->
            val w = (6.0f - idx * 0.55f).coerceAtLeast(2.2f)
            addArtistSignal(artist, w)
        }
        spotifyTopTracks.forEachIndexed { idx, track ->
            val w = (4.2f - idx * 0.35f).coerceAtLeast(1.5f)
            addArtistSignal(track.artist, w)
            addTokenSignals(track.title, track.album, w * 0.25f)
        }

        // Build display-cased top affinity artist list by matching back against known artist strings
        val displayArtistLookup = HashMap<String, String>()
        fun rememberDisplayName(raw: String) {
            val primary = raw.substringBefore(",").substringBefore(" feat.").substringBefore(" ft.").trim()
            val norm = normalizeText(primary)
            if (norm.length >= 2 && !displayArtistLookup.containsKey(norm)) {
                displayArtistLookup[norm] = primary
            }
        }
        spotifyTopArtists.forEach(::rememberDisplayName)
        savedTracks.forEach { rememberDisplayName(it.artist) }
        downloadedTracks.forEach { rememberDisplayName(it.artist) }
        sortedScrobbles.forEach { rememberDisplayName(it.artist) }

        val rankedNormArtists = artistScores.entries
            .sortedByDescending { it.value }
            .map { it.key }

        val topDisplayArtists = rankedNormArtists.mapNotNull { norm ->
            displayArtistLookup[norm] ?: norm.split(" ").joinToString(" ") { w ->
                w.replaceFirstChar { it.uppercase() }
            }.takeIf { it.isNotBlank() }
        }.distinct().take(10)

        val signalCount = savedTracks.size * 2 + scrobbles.size + downloadedTracks.size * 2 +
            spotifyTopArtists.size * 2 + userPlaylists.size
        val maturity = (signalCount.toFloat() / 18f).coerceIn(0f, 1f)
        val prefDuration = if (durationWeightTotal > 0.5f) {
            (weightedDurationSumSec / durationWeightTotal).toInt().coerceIn(110, 420)
        } else {
            210
        }

        return UserTasteProfile(
            artistWeights = artistScores,
            tokenWeights = tokenScores,
            trackPlayCounts = playCounts,
            savedTrackIds = savedIds,
            recentTrackIds = recentIds,
            preferredDurationSec = prefDuration,
            totalSignalEvents = signalCount,
            maturityRatio = maturity,
            topAffinityArtists = topDisplayArtists
        )
    }

    /**
     * Generates evolving artist & discovery seed queries that progressively adapt to the user's
     * [UserTasteProfile] as they listen, save, and vault music over time.
     */
    fun buildPersonalizedArtistSeeds(
        profile: UserTasteProfile,
        channelId: String,
        fallbackSeeds: List<String>,
        maxSeeds: Int = 6
    ): List<String> {
        val cleanFallbacks = fallbackSeeds.map { it.trim() }.filter { it.isNotBlank() }
        if (profile.topAffinityArtists.isEmpty()) {
            return cleanFallbacks.take(maxSeeds)
        }

        // Expand top artists with 1–2 sonic neighbors so discovery introduces fresh kindred artists
        val kindredExpansion = ArrayList<String>()
        profile.topAffinityArtists.take(3).forEach { fav ->
            val neighbors = SONIC_ADJACENCY[normalizeText(fav)].orEmpty()
            kindredExpansion.addAll(neighbors.take(2))
        }

        return if (channelId == "foryou") {
            // As maturity grows, "For You" shifts from editorial seeds to personal + kindred discovery
            val personalQuota = when {
                profile.maturityRatio >= 0.65f -> 4
                profile.maturityRatio >= 0.25f -> 3
                else -> 2
            }
            val kindredQuota = if (profile.maturityRatio >= 0.30f) 2 else 1
            (profile.topAffinityArtists.take(personalQuota) +
                kindredExpansion.take(kindredQuota) +
                cleanFallbacks)
                .distinctBy { normalizeText(it) }
                .take(maxSeeds)
        } else {
            // Genre channels stay true to their genre while injecting matching user-loved artists if relevant
            val channelNormSet = cleanFallbacks.map { normalizeText(it) }.toSet()
            val matchingPersonal = profile.topAffinityArtists.filter { fav ->
                val norm = normalizeText(fav)
                norm in channelNormSet || SONIC_ADJACENCY[norm]?.any { normalizeText(it) in channelNormSet } == true
            }.take(2)
            (matchingPersonal + cleanFallbacks)
                .distinctBy { normalizeText(it) }
                .take(maxSeeds)
        }
    }

    /**
     * Scores a candidate track against [UserTasteProfile] for Home recommendations & Automix.
     * Balances artist loyalty with novelty (rewarding fresh deep cuts by loved artists while gently
     * cooling down the exact track IDs just played in the last few minutes).
     */
    fun scoreTrackForTaste(
        track: TrackItem,
        profile: UserTasteProfile,
        unplayableIds: Set<String> = emptySet()
    ): Float {
        if (!track.isPlayable || track.id in unplayableIds) return -100f
        if (profile.totalSignalEvents == 0) return 10f

        var score = 10f
        val primaryNorm = normalizeText(track.artist.substringBefore(",").substringBefore(" feat.").trim())
        val directArtistWeight = profile.artistWeights[primaryNorm] ?: 0f

        if (directArtistWeight > 0f) {
            score += (ln(1f + directArtistWeight) * 14f).coerceAtMost(42f)
        } else {
            // Check if artist is sonically adjacent to one of the user's top artists
            val isAdjacent = profile.topAffinityArtists.take(4).any { fav ->
                SONIC_ADJACENCY[normalizeText(fav)]?.any { normalizeText(it) == primaryNorm } == true
            }
            if (isAdjacent) {
                score += 13.5f * (0.4f + 0.6f * profile.maturityRatio)
            }
        }

        // Token resonance (album/track vocabulary)
        val tokens = extractMeaningfulTokens("${track.title} ${track.album}")
        var tokenBoost = 0f
        tokens.forEach { tok ->
            val tw = profile.tokenWeights[tok] ?: 0f
            if (tw > 0f) tokenBoost += ln(1f + tw) * 2.4f
        }
        score += tokenBoost.coerceAtMost(14f)

        // Duration fit
        val durSec = (track.durationMs / 1000L).toInt()
        if (durSec in 90..480) {
            val diff = abs(durSec - profile.preferredDurationSec)
            score += (6f - (diff / 35f)).coerceIn(0f, 6f)
        }

        // Novelty vs Replay balance:
        // Fresh tracks by a beloved artist get a discovery boost; tracks played in the last 6 spins cool down
        val priorPlays = profile.trackPlayCounts[track.id] ?: 0
        when {
            track.id in profile.recentTrackIds -> score -= 14f
            priorPlays == 0 && directArtistWeight > 3.5f -> score += 8.5f
            track.id in profile.savedTrackIds && priorPlays in 1..4 -> score += 4.5f
            priorPlays > 8 -> score -= 4f
        }

        // Penalize karaoke / tribute / low-grade copies in Home discovery too
        val lowerMeta = "${track.title} ${track.artist}".lowercase()
        if (DERIVATIVE_HEAVY_NOISE_PATTERNS.any { lowerMeta.contains(it) }) {
            score -= 35f
        }

        return score
    }

    /**
     * Ranks candidate discovery tracks by evolving taste affinity while enforcing artist diversity
     * so no single artist monopolizes consecutive slots.
     */
    fun rankDiscoveryTracks(
        tracks: List<TrackItem>,
        profile: UserTasteProfile,
        unplayableIds: Set<String> = emptySet(),
        maxResults: Int = 18
    ): List<TrackItem> {
        if (tracks.isEmpty()) return emptyList()
        val deduped = LinkedHashMap<String, TrackItem>()
        tracks.forEach { tr ->
            val key = "${canonicalSongTitle(tr.title)}|${normalizeText(tr.artist.substringBefore(","))}"
            val existing = deduped[key]
            if (existing == null || (!existing.isPlayable && tr.isPlayable)) {
                deduped[key] = tr
            }
        }

        if (profile.totalSignalEvents == 0) {
            return deduped.values.take(maxResults)
        }

        val scored = deduped.values
            .mapIndexed { idx, track ->
                // Preserve slight editorial position anchor while letting taste profile drive ranking
                val editorialAnchor = (18 - idx).coerceAtLeast(0) * 0.35f * (1f - profile.maturityRatio * 0.55f)
                track to (scoreTrackForTaste(track, profile, unplayableIds) + editorialAnchor)
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .toMutableList()

        // Greedy diversity pass: avoid >1 back-to-back song by the exact same primary artist
        val diversified = ArrayList<TrackItem>(minOf(maxResults, scored.size))
        var lastArtist = ""
        while (scored.isNotEmpty() && diversified.size < maxResults) {
            val pickIdx = scored.indexOfFirst {
                normalizeText(it.artist.substringBefore(",")) != lastArtist
            }.let { if (it >= 0) it else 0 }
            val picked = scored.removeAt(pickIdx)
            lastArtist = normalizeText(picked.artist.substringBefore(","))
            diversified.add(picked)
        }
        return diversified
    }

    /**
     * Separates the genuine Original / Official Master recording from copies, karaoke, covers,
     * slowed/sped-up edits, and duplicate noise for the Search screen!
     *
     * Everything else remains accessible in [SearchPartitionResult.remainingTracks] right below.
     */
    fun partitionSearchResults(
        query: String,
        rawTracks: List<TrackItem>,
        profile: UserTasteProfile? = null,
        unplayableIds: Set<String> = emptySet()
    ): SearchPartitionResult {
        if (rawTracks.isEmpty()) {
            return SearchPartitionResult(null, "", "", emptyList())
        }
        val cleanQuery = query.trim()
        val lowerQuery = cleanQuery.lowercase()
        val normQuery = normalizeText(cleanQuery)
        val queryTokens = extractMeaningfulTokens(cleanQuery)

        // Check whether the user explicitly asked for a variant (e.g. "after hours slowed" or "daft punk remix")
        val userWantsNoiseVariant = DERIVATIVE_HEAVY_NOISE_PATTERNS.any { lowerQuery.contains(it) }
        val userWantsSecondaryVariant = SECONDARY_VARIANT_PATTERNS.any { lowerQuery.contains(it) }

        data class ScoredSearchCandidate(
            val track: TrackItem,
            val originalIndex: Int,
            val authorityScore: Float,
            val isDerivativeCopy: Boolean
        )

        val scored = rawTracks.mapIndexed { index, track ->
            val lowerTitle = track.title.lowercase()
            val lowerArtist = track.artist.lowercase()
            val lowerAlbum = track.album.lowercase()
            val combinedLower = "$lowerTitle $lowerArtist $lowerAlbum"

            val canonTitle = canonicalSongTitle(track.title)
            val primaryArtistNorm = normalizeText(track.artist.substringBefore(",").substringBefore(" feat."))
            val fullArtistNorm = normalizeText(track.artist)

            var score = 0f

            // 1. Upstream catalog relevance rank anchor (top upstream hits carry real popularity/view weight)
            score += (24f - index * 1.45f).coerceAtLeast(0f)

            // 2. Canonical Title & Artist exactness
            val exactTitleMatch = canonTitle == normQuery
            val titleAndArtistComboMatch = normQuery == "$canonTitle $primaryArtistNorm".trim() ||
                normQuery == "$primaryArtistNorm $canonTitle".trim() ||
                (canonTitle.isNotBlank() && normQuery.contains(canonTitle) && primaryArtistNorm.isNotBlank() && normQuery.contains(primaryArtistNorm))

            when {
                titleAndArtistComboMatch -> score += 64f
                exactTitleMatch -> score += 48f
                canonTitle.startsWith(normQuery) || normQuery.startsWith(canonTitle) -> score += 28f
                else -> {
                    val matchedTokens = queryTokens.count { tok ->
                        canonTitle.contains(tok) || fullArtistNorm.contains(tok)
                    }
                    if (queryTokens.isNotEmpty()) {
                        score += (matchedTokens.toFloat() / queryTokens.size.toFloat()) * 26f
                    }
                }
            }

            // Clean studio title bonus (no messy bracketed YouTube/reupload tags)
            val hasBracketClutter = track.title.contains('[') ||
                lowerTitle.contains("official video") ||
                lowerTitle.contains("lyric video") ||
                lowerTitle.contains("full song") ||
                lowerTitle.contains("bass boosted")
            if (!hasBracketClutter) {
                score += 12f
            } else {
                score -= 10f
            }

            // 3. Copy / Cover / Slowed / Karaoke / Compilation penalties (unless user searched for it!)
            val hasHeavyCopyNoise = DERIVATIVE_HEAVY_NOISE_PATTERNS.any { combinedLower.contains(it) }
            val hasSecondaryVariant = SECONDARY_VARIANT_PATTERNS.any { combinedLower.contains(it) }
            val hasGenericCompilation = GENERIC_COMPILATION_PATTERNS.any {
                lowerArtist.contains(it) || lowerAlbum.contains(it)
            }

            if (hasHeavyCopyNoise && !userWantsNoiseVariant) {
                score -= 95f
            }
            if (hasSecondaryVariant && !userWantsSecondaryVariant) {
                score -= 32f
            }
            if (hasGenericCompilation) {
                score -= 36f
            }

            // 4. Studio master metadata & playability authority
            val isPlayable = (track.isPlayable && track.id !in unplayableIds) || track.isDownloaded
            if (isPlayable) {
                score += 26f
            } else {
                score -= 130f
            }

            val durSec = (track.durationMs / 1000L).toInt()
            when {
                durSec in 95..460 -> score += 14f
                durSec in 60..94 -> score += 4f
                durSec in 1..50 -> score -= 55f // 30-second ringtone/preview snippet
                durSec > 900 -> score -= 28f // hour-long compilation upload
            }

            if (track.album.isNotBlank() && !track.album.equals("Single", ignoreCase = true)) {
                score += 7f
            }
            if (track.artworkUrl.isNotBlank()) {
                score += 6f
            }
            if (track.sourceType == AudioSourceType.SPOTIFY ||
                track.spotifyTrackId != null ||
                track.externalSpotifyUri != null
            ) {
                score += 5f
            }

            // 5. Personal Taste Profile tie-breaker when multiple legitimate songs share a title
            if (profile != null && profile.totalSignalEvents > 0) {
                val artistAffinity = profile.artistWeights[primaryArtistNorm] ?: 0f
                if (artistAffinity > 0f) {
                    score += (ln(1f + artistAffinity) * 6.5f).coerceAtMost(18f)
                }
                if (track.id in profile.savedTrackIds) {
                    score += 8f
                }
            }

            ScoredSearchCandidate(
                track = track,
                originalIndex = index,
                authorityScore = score,
                isDerivativeCopy = (hasHeavyCopyNoise && !userWantsNoiseVariant) || hasGenericCompilation
            )
        }

        val sortedByAuthority = scored.sortedWith(
            compareByDescending<ScoredSearchCandidate> { it.authorityScore }
                .thenBy { it.originalIndex }
        )

        val winner = sortedByAuthority.firstOrNull()
            ?: return SearchPartitionResult(null, "", "", rawTracks)

        // Build remaining list: keep authentic recordings above obvious copies, without hiding anything
        val remaining = sortedByAuthority
            .drop(1)
            .map { it.track }

        val winnerArtistNorm = normalizeText(winner.track.artist.substringBefore(","))
        val inUserRotation = profile != null &&
            ((profile.artistWeights[winnerArtistNorm] ?: 0f) >= 3.5f || winner.track.id in profile.savedTrackIds)

        val badge = when {
            userWantsNoiseVariant || userWantsSecondaryVariant -> "TOP MATCH"
            inUserRotation -> "ORIGINAL • YOUR TASTE"
            !winner.isDerivativeCopy -> "ORIGINAL MASTER"
            else -> "TOP RESULT"
        }

        val durFormatted = "%d:%02d".format(
            (winner.track.durationMs / 60_000L).coerceAtLeast(0L),
            ((winner.track.durationMs / 1000L) % 60L).coerceAtLeast(0L)
        )
        val reason = buildString {
            if (winner.track.album.isNotBlank() && !winner.track.album.equals(winner.track.title, ignoreCase = true)) {
                append(winner.track.album)
                append(" • ")
            }
            append(durFormatted)
        }

        return SearchPartitionResult(
            topMatch = winner.track,
            topMatchBadge = badge,
            topMatchReason = reason,
            remainingTracks = remaining
        )
    }

    internal fun canonicalSongTitle(rawTitle: String): String {
        val stripped = rawTitle
            .replace(Regex("\\((?:official|lyric|lyrics|audio|video|hd|hq|remaster[^)]*|feat[^)]*|ft[^)]*)\\)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\[(?:official|lyric|lyrics|audio|video|hd|hq|remaster[^]]*|feat[^]]*|ft[^]]*)]", RegexOption.IGNORE_CASE), " ")
            .substringBefore(" - Remastered")
            .substringBefore(" (Remastered")
        return normalizeText(stripped)
    }

    internal fun normalizeText(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractMeaningfulTokens(input: String): List<String> {
        return normalizeText(input)
            .split(" ")
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .distinct()
    }
}
