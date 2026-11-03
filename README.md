# Nchord

Nothing-inspired full-length music streaming & local audio player for Android built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room**, **DataStore**, **OkHttp/Retrofit**, and native **144Hz VSYNC + real-time FFT Glyph Matrix telemetry**.

---

## Features
- **Ad-Free Full-Length Streaming & Offline Downloads**: Multi-tier audio engine (`96 kbps`, `160 kbps`, `320 kbps AAC`, and `24-bit/96kHz Lossless FLAC` probe) with local `127.0.0.1` HTTP `Range` stream relay and in-app offline storage.
- **Per-Client Spotify OAuth 2.0 PKCE Bridge**: Connect your own Spotify Developer Client ID (`nothingchord://callback`) with zero hardcoded developer secrets.
- **Native 144Hz High-Refresh Engine**: Dedicated `144Hz MAX` / `ADAPTIVE` / `60Hz ECO` display lock in **Studio**, backed by zero-allocation `Canvas` rendering and `graphicsLayer` state isolation.
- **Studio Glyph Matrix & Synced Lyrics**: 6-band real-time FFT audio reactivity (`SUB`..`AIR`), LRCLIB time-synced lyrics, taste-profile recommendations, and canonical Top Result search ranking.

---

## Release Setup Checklist (Permanent Signing Key + GitHub Releases)

Android requires every version of an app (`v1.0.0` → `v1.1.0` → `v2.0.0`) to be signed with the **same permanent signing key**. If the signing certificate changes between versions, Android blocks in-place updates (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), forcing users to uninstall and lose local playlists/downloads.

### Step 1 — Push Repository to GitHub
1. Click **Push to GitHub** in AI Studio Build (or export ZIP and push to a new GitHub repository).
2. In your GitHub repo, ensure **Settings → Actions → General → Workflow permissions** is set to **Read and write permissions** (default for most new repos; allows `GITHUB_TOKEN` to publish GitHub Releases).

### Step 2 — Generate & Save Your Permanent Signing Key (Recommended Before `v1.0.0`!)
Run the included one-command generator locally on your computer (never commit the `.jks` file; `*.jks` is already in `.gitignore`):

**macOS / Linux / WSL / Git Bash:**
```bash
bash scripts/generate-release-keystore.sh
# Or upload directly into GitHub Secrets in one step if you have GitHub CLI (`gh`) installed:
bash scripts/generate-release-keystore.sh --push-secrets <your-username>/<your-repo>
```

**Windows (PowerShell):**
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-release-keystore.ps1
```

This creates **`nchord-upload-key.jks`** (4096-bit RSA PKCS12, `alias = upload`, matching `app/build.gradle.kts`) — **back up `nchord-upload-key.jks` and the printed password in your password manager**, then paste the 3 printed values into **GitHub → Settings → Secrets and variables → Actions → New repository secret**:

| Secret Name | Required? | Purpose |
| :--- | :--- | :--- |
| `RELEASE_KEYSTORE_BASE64` | **Yes (for permanent updates)** | Base64-encoded `nchord-upload-key.jks` containing key alias `upload` |
| `STORE_PASSWORD` | **Yes (for permanent updates)** | Keystore password |
| `KEY_PASSWORD` | **Yes (for permanent updates)** | Key password for alias `upload` |
| `LASTFM_API_KEY` | Optional | Last.fm API key (only if using Last.fm scrobbling) |
| `LASTFM_SHARED_SECRET` | Optional | Last.fm shared secret |

> **Note if you pushed to GitHub before adding the 3 secrets:** The first push still succeeds using a temporary fallback key so CI never breaks out of the box, and once you add the 3 permanent secrets above you can open **Actions → Build APK & Publish Release → Run workflow** (`release_tag = v1.0.0`, `publish_release = true`) to re-sign and overwrite `v1.0.0` with your permanent certificate! All future upgrades (`v1.0.1`, `v1.1.0`, etc.) strictly enforce `RELEASE_KEYSTORE_BASE64` so you can never accidentally publish a key-mismatched update.

### Step 3 — Publishing Future Releases (`v1.1.0`, etc.)
1. Increment `versionCode` (e.g., `1` → `2`) and `versionName` (e.g., `"1.0"` → `"1.1"`) in `app/build.gradle.kts` (Android requires `versionCode` to increase on every update).
2. Commit and push a semantic tag:
   ```bash
   git tag v1.1.0
   git push origin main --tags
   ```
3. GitHub Actions runs Lint + unit/Robolectric tests, verifies the v2/v3 APK certificate via `apksigner`, prints the certificate SHA-256 in the Actions Summary, and publishes the new GitHub Release with `Nchord-v1.1.0-release.apk`, `Nchord-v1.1.0-debug.apk`, and `SHA256SUMS.txt`.

> **Security Note**: Never commit `.env` or `.jks` keystore files to version control. Any API keys injected into `BuildConfig` via `.env` at build time are compiled into the distributed APK binary and can be inspected by end users; Nchord ships with zero bundled developer keys by default.
