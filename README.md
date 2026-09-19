# Stash for Android

A native **Kotlin + Jetpack Compose** client for [Stash](https://github.com/logno-dev/stash), with a minimal, always-dark interface. Android 8.0 (API 26) or newer.

## Install with Obtainium

In Obtainium, choose **Add app** and enter:

```text
https://github.com/logno-dev/stash-android
```

Stable GitHub Releases contain one universal, signed APK. Obtainium checks those releases and offers updates; allow it to install unknown apps when Android asks. No Play Store or Expo account is needed. You can also install the APK directly from the Releases page.

Release application ID: `dev.logno.stash`. Debug builds use `dev.logno.stash.debug` and install alongside the release app.

## Features

| Web feature | Android implementation |
| --- | --- |
| Login, registration, logout | Native forms against the existing API; configurable HTTPS server |
| Persistent session | Bearer token encrypted with Android Keystore; expired sessions return to login |
| Bookmark/note CRUD | Optional link, notes, comma-separated tags; delete confirmation |
| Automatic page titles | Uses the same server-side title extraction |
| Domain groups and dates | Alphabetical domain sections, item counts, localized dates |
| Search | All four fields: title, URL, notes, tags; typo-tolerant matching |
| Notes-only filter | Shows records without links |
| Markdown | Write/preview, headings, emphasis, lists, code, quotes, links, images, tables, strikethrough, task lists |
| Read-only details | View on every card opens a spacious formatted note, full link, tags, and date; Back preserves list filters and scroll position |
| Long lists | Compose lazy scrolling |
| Open bookmark | Opens the link in your browser |
| Shared links/text | Android share target with a prefilled, editable draft |

Search is a native typo-tolerant implementation; rankings are not identical to the web app's Fuse.js ranking. Markdown uses native text views through Markwon rather than a WebView.

Cards, Preview, and View preserve single typed line breaks as well as paragraph breaks while rendering Markdown formatting.

The original Stash mustache appears in the app header and adaptive launcher icon, including Android themed-icon support.

### Sharing

From a browser or another app, select **Share → Stash**. Stash extracts the first HTTP(S) link, preserves the full shared text and subject as notes, and opens an editor. Review or add tags, then tap **Save**. Text without a link becomes a standalone note. Additional links remain in the notes.

Sharing works when the app is closed, already running, or signed out. Drafts survive activity/process recreation and login. If a draft is already open, subsequent shares are queued and opened after saving or discarding the current draft. Saving requires a network connection; failed saves retain the draft. Explicit sign-out clears local drafts and the session.

### Server

The default server is `https://stash.bunch.codes`; change it on the sign-in screen for another Stash deployment. The app uses the existing `/api/auth/*` and `/api/bookmarks/*` endpoints. No backend credentials are bundled in the APK. General HTTP server connections are disabled.

## Local development

Requirements: JDK 17, Android SDK platform 35/build tools 35.0.0, and `ANDROID_HOME` pointing at your SDK (or a local, ignored `local.properties` containing `sdk.dir=...`). The Gradle wrapper downloads the pinned Gradle version and verifies its checksum.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the repository in Android Studio, or use the CLI alone. A connected emulator/device can run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Tests cover API payloads and authentication errors, link extraction, native fuzzy search, cold/warm shares, draft restoration, the editor's Markdown preview, and Keystore storage. CI runs emulator tests separately from the build job.

Manually smoke-test against your deployed server before relying on a release: registration/login, create/edit/delete, notes-only filtering, search, tags, Markdown tables/images, browser links, share while signed out, share while editing, and a failed save followed by retry. Tests do not create or mutate live-server data.

## Signing and releases

### One-time setup

With `gh` authenticated and JDK `keytool` on your PATH:

```bash
python3 scripts/configure-signing.py logno-dev/stash-android
```

This generates a release keystore and random passwords in ignored `.signing/`, or reuses the existing backup, and sets these Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

**Keep a secure backup of the entire `.signing/` directory.** GitHub secrets cannot be read back, and updates to an installed app must use the original key. Never commit that directory. The APK workflow restores the key only for release signing and removes the temporary copy afterward.

### Publish

After build checks pass on `main`:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The tag-triggered workflow tests, lints, builds an optimized signed APK, verifies its signature, and publishes it with a SHA-256 checksum file. Obtainium then sees the new release. Push a new increasing version tag for each update; do not reuse published tags.

Stable tags must be `vMAJOR.MINOR.PATCH`, with major 0–999 and minor/patch 0–99. `versionCode = major * 10000 + minor * 100 + patch`, so `v0.1.0` is code 100 and `v0.1.1` is code 101. Version 0.0.0 is not supported. `versionName` comes from the tag. Local defaults are 0.1.1/code 101; override with Gradle `-PversionName=... -PversionCode=...` if needed.

## Repository layout

This is an independent Git repository nested as `android-app/` in the parent Stash repo. Android source, signing workflows, tags, and releases belong here. The parent tracks a submodule commit.

```bash
# From a fresh parent checkout
git submodule update --init --recursive

# After pushing a new Android revision, from the parent repo
git add android-app
git commit -m "Update Android app"
```

No npm, React Native, Expo, or EAS dependencies are required.
