# Nchord v1.0.0 — First Official Release

Artistic Nothing-inspired full-length music player for Android with a native **144Hz high-refresh-rate UI**, real-time **FFT Glyph Matrix audio visualizer**, **ad-free YouTube Music & 320 kbps / Hi-Res FLAC streaming**, **per-client Spotify OAuth 2.0 PKCE sync**, synced **LRCLIB lyrics**, and zero-lag **offline downloads**.

---

## Downloads & Verification

| Asset | Description |
| :--- | :--- |
| **`Nchord-v1.0.0-release.apk`** | **Recommended** — Production-optimized, signed universal Android APK (`minSdk 24`, Android 7.0 – Android 16). |
| **`Nchord-v1.0.0-debug.apk`** | Debuggable build signed with the project debug certificate for inspection & testing. |
| **`SHA256SUMS.txt`** | SHA-256 cryptographic checksums for verifying APK integrity prior to installation. |

---

## Highlights in `v1.0.0`

### 1. Native 144Hz Display & Zero-Jank Rendering Engine
- **Dedicated Display Rate Selector (`144Hz MAX` / `ADAPTIVE` / `60Hz ECO`)**: Hardware `Display.Mode` resolution matching + Window `preferredDisplayModeId` & Android 15+ (`API 35`) `View.requestedFrameRate` VSYNC voting to eliminate LTPO 60↔144Hz gear-shift micro-stutter.
- **Zero-Allocation Canvas & Backdrop Architecture**: Replaced live GPU blur passes (`RenderEffect` / `.blur()`) with pre-cached radial atmospheric luminescence brushes, primitive `FloatArray` particle pools, and `graphicsLayer` lambda reads to stay comfortably inside the **6.94 ms** 144Hz frame budget.

### 2. Audio-Synced Studio & Glyph Matrix Visualizer
- **Real-Time Multi-Band FFT Telemetry**: Live hardware `Visualizer` capture across 6 acoustic bands (`SUB`, `BASS`, `LOW-M`, `MID`, `PRES`, `AIR`) with beat-locked travelling shockwaves, harmonic crest waves, and automatic acoustic simulation fallback when `RECORD_AUDIO` is declined.
- **Minimal Studio Console**: Streamlined signal path pipeline, audio quality tier selector (`96k Saver`, `160k Standard`, `320k Master`, `24-bit/96kHz Lossless FLAC`), USB DAC bit-perfect indicator, and display refresh rate controls.

### 3. Full-Track Streaming, Original-Master Search & Taste Profile
- **Top Result Separation**: Automatically isolates the canonical/original recording or highest-authority master at the top of search results, separating remixes, sped-up edits, and duplicates below.
- **Adaptive Taste Profile Engine**: Learns artist, genre, and completion affinity locally over time (Room + DataStore) to power personalized *Picked For You* rotations without external tracking.
- **Per-Client Spotify Bridge (PKCE) & Scrobbling**: Zero bundled developer keys — bring your own Spotify Client ID (`nothingchord://callback`) to import playlists, saved tracks, and listening crates, plus optional Last.fm & ListenBrainz scrobbling.

### 4. Tactile Vinyl Player Deck & Library Management
- **Low-Friction Gesture Dismissal & Zero-Lag Scrubbing**: Instantaneous seek bar tracking (`SEEK_CLOSEST_SYNC` + 1.1s optimistic settle lock), 3-second pause-at-start marquee titles, synced lyrics, and one-tap home/library playlist pinning, hiding, or deletion.

---

## Installation Notes
1. Download **`Nchord-v1.0.0-release.apk`** onto your Android device (Android 7.0 / API 24 or newer).
2. Verify checksum if desired: `sha256sum -c SHA256SUMS.txt`.
3. Open the APK on your device and allow installation if prompted by Android Package Installer.
