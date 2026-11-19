# Lumora — YouTube & YouTube Music, refined

A personal-use native Android wrapper around **m.youtube.com** and **music.youtube.com**:
built-in ad-blocking, background playback with lock-screen controls, Picture-in-Picture,
SponsorBlock auto-skip and a deep-AMOLED Material 3 UI.

Kotlin · Jetpack Compose · Material 3 · Media3 · AndroidX WebKit · Coil 3
minSdk 26 · targetSdk 35 · compileSdk 37.2 (required by Compose BOM 2026.11) · AGP 9.5.1 · Gradle 9.9.2

## Build & install

Requirements: JDK 17+ (Android Studio's bundled JBR works) and the Android SDK.

```bash
cd android
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Install it on your phone (USB debugging on):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and open it (allow "Install unknown apps" for your file manager).
The debug build installs as **Lumora** with id `dev.lumora.app.debug`, so it can sit next to a release build.
`./gradlew assembleRelease` gives a minified build signed with the debug key; it's meant for personal sideloading.

Run the unit tests with `./gradlew testDebugUnitTest`.

## Architecture

```
dev.lumora.app
├── MainActivity            Fullscreen video, PiP, permissions, incoming links, lifecycle
├── LumoraApplication       Coil image loader, singletons
├── data/                   BrowseMode (Video/Music) + SettingsRepository (StateFlow over prefs)
├── adblock/AdBlockManager  Host/path filter for shouldInterceptRequest + cosmetic CSS
├── web/
│   ├── WebController       Owns the WebView: settings, clients, scripts, pull-to-refresh
│   ├── LumoraWebView       Keeps the window "visible" when backgrounded
│   ├── WebBridge           JS → Kotlin messages (WebMessageListener, scoped to YouTube origins)
│   ├── UserAgents          Sign-in–safe UA strings (no "; wv")
│   └── UrlRouter           Internal vs external links, youtu.be / share-intent handling
├── media/
│   ├── WebMediaPlayer      Media3 SimpleBasePlayer mirroring the page's <video>
│   ├── PlaybackBridge      Glue: page state → player → service, wake/Wi-Fi locks
│   └── PlaybackService     MediaSessionService: notification + lock-screen controls
├── sponsor/                SponsorBlock client (hash-prefix API, nothing identifying leaves the device)
└── ui/                     MainViewModel, LumoraScreen, theme, components
assets/js/core.js           Visibility spoofing, media bridge, share polyfill, SponsorBlock skipping
assets/js/adblock.js        Ad-payload pruning, cosmetic CSS, ad skipper, anti-adblock popup removal
```

### How the main features work

- **Ad-blocking** has three layers:
  1. `shouldInterceptRequest` returns an empty response for ad, tracker and telemetry hosts and paths.
  2. A document-start script strips `adPlacements`, `playerAds` and `adSlots` from player responses before YouTube's player reads them.
  3. A `MutationObserver` clicks "Skip", fast-forwards any ad that still gets through (muted, at 16×), and removes enforcement popups and Premium upsells.
- **Background play**:
  - `document.hidden` and `visibilityState` are spoofed, and `visibilitychange`, `blur` and `pagehide` are swallowed.
  - The WebView's window visibility is pinned to VISIBLE, and `onPause` is skipped.
  - A partial wake lock and a Wi-Fi lock are held while media plays.
- **Media controls**:
  - `WebMediaPlayer` exposes the page's playback to a Media3 `MediaSession`. Media3 then shows the media notification, lock-screen controls and headset/Bluetooth buttons, and keeps the service in the foreground.
  - Next and Previous call YouTube's own `navigator.mediaSession` handlers.
- **PiP**: auto-enters when you leave the app while a full-screen video is playing (`setAutoEnterEnabled` on Android 12+, `onUserLeaveHint` before that). Closing the PiP window pauses playback.
- **Sign-in**: the user agent drops the WebView markers, and third-party cookies are enabled, so Google accounts sign in normally.
- **Back**: predictive back moves through WebView history with an animated page-card preview. On the root page, back goes to the home screen.

## Notes

- YouTube changes its markup often. If an ad layout slips through, add its selector to
  `AdBlockManager.cosmeticCss` or a path to `blockedPathFragments`.
- Swiping Lumora away from Recents stops playback.
