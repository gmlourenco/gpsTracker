# Android APK releases

**Do not commit APK files to this folder** — they are gitignored.

Official builds are published via **GitHub Releases** when you:

1. Run the **Android Release** workflow (Actions → Android Release → Run workflow), or
2. Push an update to `backend/app/config/app-release.json` on `main`.

## After each release

1. Copy the APK asset URL from the GitHub release page.
2. In **Vercel** → Project → Settings → Environment Variables:
   - `APP_LATEST_VERSION` = e.g. `1.5.0`
   - `APP_DOWNLOAD_URL` = full URL to `seguranca-rural-1.5.0.apk`
   - (optional) `APP_MIN_VERSION`, `APP_RELEASE_NOTES`
3. Redeploy the backend (or wait for the next deploy).

The mobile app checks `GET /api/app/version` on startup and prompts to download when a newer version is available.

## Signing

Configure GitHub Actions secrets (`ANDROID_KEYSTORE_*`) so release APKs use the same key — required for in-place updates without losing data.
