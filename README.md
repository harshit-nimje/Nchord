# Nchord

Nothing-inspired full-length music streaming & local audio player for Android built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room**, **DataStore**, **OkHttp/Retrofit**, and native **144Hz VSYNC + real-time FFT Glyph Matrix telemetry**.

---

## Features
- **Ad-Free Full-Length Streaming & Offline Downloads**: Multi-tier audio engine (`96 kbps`, `160 kbps`, `320 kbps AAC`, and `24-bit/96kHz Lossless FLAC` probe) with local `127.0.0.1` HTTP `Range` stream relay and encrypted-free in-app offline storage.
- **Per-Client Spotify OAuth 2.0 PKCE Bridge**: Connect your own Spotify Developer Client ID (`nothingchord://callback`) with zero hardcoded developer secrets.
- **Native 144Hz High-Refresh Engine**: Dedicated `144Hz MAX` / `ADAPTIVE` / `60Hz ECO` display lock in **Studio**, backed by zero-allocation `Canvas` rendering and `graphicsLayer` state isolation.
- **Studio Glyph Matrix & Synced Lyrics**: 6-band real-time FFT audio reactivity (`SUB`..`AIR`), LRCLIB time-synced lyrics, taste-profile recommendations, and canonical Top Result search ranking.

---

## Building APKs & Publishing Releases on GitHub

This repository includes a production-ready GitHub Actions CI/CD workflow at [`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml).

### Automatic First Release (`v1.0.0`)
1. Export or click **Push to GitHub** from AI Studio Build to push the repository to your `main` (or `master`) branch.
2. GitHub Actions automatically runs:
   - Android Lint audit (`gradle :app:lintDebug`)
   - Unit & Robolectric test suite (`gradle :app:testDebugUnitTest`)
   - Signed Release & Debug APK builds (`gradle :app:assembleRelease :app:assembleDebug`)
3. On the initial push (when release `v1.0.0` does not exist yet), the workflow **automatically creates tag `v1.0.0` and publishes the first GitHub Release (`Nchord v1.0.0 — First Release`)** with:
   - `Nchord-v1.0.0-release.apk`
   - `Nchord-v1.0.0-debug.apk`
   - `SHA256SUMS.txt`

### Subsequent Version Releases
- **Via Git Tag**: Push any semantic version tag matching `v*` (for example `git tag v1.1.0 && git push origin v1.1.0`) to build and publish a new GitHub Release automatically.
- **Via Manual Dispatch**: Open **Actions → Build APK & Publish Release → Run workflow**, specify any `release_tag` (e.g. `v1.0.0` or `v1.0.1`), and click **Run workflow**.

### Optional Signing & Secret Configuration
Out of the box, the workflow requires **zero manual secrets** — if no custom keystore secrets are set, CI generates an installable PKCS12 release keystore (`alias = upload`) so `Nchord-v1.0.0-release.apk` builds and installs immediately.

To sign all future releases with a persistent upload key so users can upgrade in-place across versions, add these optional **GitHub Repository Secrets** (*Settings → Secrets and variables → Actions*):

| Secret Name | Purpose |
| :--- | :--- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `.jks` / PKCS12 keystore containing key alias `upload` (`base64 -w 0 my-upload-key.jks`) |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password for alias `upload` |
| `LASTFM_API_KEY` | *(Optional)* Last.fm API key if enabling direct Last.fm scrobbling |
| `LASTFM_SHARED_SECRET` | *(Optional)* Last.fm shared secret |

> **Security Note**: Never commit `.env` or `.jks` keystore files to version control. Note that any API keys injected into `BuildConfig` via `.env` at build time are compiled into the distributed APK binary and can be extracted by end users inspecting the APK; Nchord ships with zero bundled developer keys by default.
