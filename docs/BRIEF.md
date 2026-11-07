# Frame Glass: rebuild brief for the coding agent

Version: FINAL, 2026-11-07. Status: approved by the owner. Nothing is built yet.

How to read this brief:

- `[DECISION Dn]` marks a choice the owner approved on 2026-11-07, D1 to D9. Build the stated default. Do not reopen it unless the owner asks or R11 applies.
- `[UNVERIFIED]` marks a fact the research could not confirm. Measure it on the phone and never assume it.
- `[AUTO]` is a gate check you run yourself. `[OWNER]` is a gate check that needs the owner's eyes or ears on the phone. Give the owner the exact steps in Arabic and wait for the answer.
- Versions were read from primary sources on 2026-11-07. Pin them exactly.

---

## 1. Mission

Rebuild Frame Glass from scratch as a private video library and player for local files on one phone, the owner's Samsung Galaxy S26 Ultra. The owner watches series, movies and anime downloaded as files, often with Arabic subtitles. The app must turn those files into a poster library with no manual work, play every file he owns with correct sound and correctly rendered Arabic subtitles, and look and feel like the Nuvio mobile app (section 7.1), with real liquid glass. The final deliverable is one signed release APK installed and running on that phone. There is no PC, desktop, web, emulator or store output.

The previous version shipped 34 tests and was never run on a phone. That is the failure this brief exists to prevent: every phase ends with a gate on the real phone, and nothing counts as done without proof from that phone.

---

## 2. Non-negotiable rules

**R1. Private project.** Never upload, push, publish or sync any part of this project. No GitHub or any git remote. Do not touch the team-knowledge repo or its local clone. Do not run `knowledge_sync.py` or `skills_sync.py`. Do not add a line to `~/.claude/lessons-inbox.md`. Do not create, install or publish a skill. Do not publish a hosted page or Artifact. `knowledge_sync.py` does not block this project by name, so the guard is you. Any standing rule elsewhere that says to publish lessons does not apply here. Project notes stay in `D:\FrameGlass\app\docs\`.

**R2. Privacy.** Video files never leave the phone. Automatic lookups send only the parsed title, year, season and episode numbers, and provider IDs. See [DECISION D9] for the one exception. No analytics, crash SDK, ad SDK, Firebase or Google Play Services dependency. Every outbound host sits in one allow-list (section 4.9). A master setting "Online features" turns off every network call.

**R3. Free.** No ads, fees, paid tiers or nag screens.

**R4. No key in the APK.** TMDB, OMDb, OpenSubtitles, SubDL and SubSource keys are typed by the owner into Settings on the phone, encrypted with an AndroidKeyStore AES-GCM key, and excluded from backups. `androidx.security:security-crypto` is deprecated, so do not use it. No key in `BuildConfig`, resources, `local.properties`, test fixtures or git history.

**R5. One device.** Design, test and tune for the S26 Ultra only. Delete every "below Android X" tier from the old brief. Set `minSdk 36`, `compileSdk 37`, `targetSdk 37`, `arm64-v8a` only. Two degraded states stay because they can really happen on this phone: Battery saver or reduced motion (section 7.9), and `SurfaceView.setBlurRegions()` missing or failing (section 7.5).

**R6. The old project is read-only.** It sits outside this repository. You may read only these items:

- the package name `com.frameglass.player` in `android/AndroidManifest.xml`
- the signing files in `android/.signing/`, under section 3.5
- `legal/tmdb-terms.html` and `android/assets/tmdb-logo.svg`, which are TMDB's own material

Never write there, run anything from there or copy source from there. Every old behaviour worth keeping is written out in this brief. Migration code applies only if [DECISION D1] says old data exists.

**R7. No material derived from reverse engineering.** Write any behaviour you need from public specs. No decompiled code, strings or assets from any app. No other app's name or logo. The only look reference is the one in section 7.1.

**R8. The Nuvio images are a visual reference only.** Do not copy Nuvio source code, shaders, fonts, icons, assets, name or logo. Nuvio is GPL-3.0 and gives no rights to its branding. Film frames and posters in the reference images must never ship. See section 7.1.

**R9. Honest reporting.** `docs/STATUS.md` lists every feature ID from section 6 with one status: `NOT STARTED`, `BUILT`, `UNIT TESTED`, `VERIFIED ON PHONE`, `BLOCKED`, or `NOT BUILT (allowed)` for F-T7 only. `VERIFIED ON PHONE` requires an evidence file under `docs/evidence/phase-N/`: a log, a CSV, a screenshot, or the owner's recorded answer. JVM tests, Robolectric and emulators prove nothing about playback and can never set that status. If you did not run a check, write that you did not run it.

**R10. Gate discipline.** Work through the phases in order. No UI polish before Gate 0 passes. A failed gate means fix it or stop and report. Only the owner can waive a gate item, and each waiver is recorded in `docs/WAIVERS.md`.

**R11. Stop and ask the owner when:**

- a Core row of the test matrix still fails after one working session
- the evidence points to changing the engine
- the phone reports API below 37
- you need a permission outside section 4.10
- a dependency has an unclear licence
- anything would cost money or need a new account
- something in section 8 seems necessary

**R12. This PC.** Install only under `D:\dev` and `D:\FrameGlass`. Do not change Windows Defender settings or exclusions, the system PATH, services, scheduled tasks or the registry. Set environment variables per process in `tools/env.ps1`. All downloads run in the background with no browser, dialog or pop-up. Put each downloaded archive in its own empty folder and check its SHA-256 where the publisher gives one.

**R13. Keep it simple.** Build what this brief lists and nothing more. New capability goes behind a menu and does not crowd Home, Library or the player's main controls.

**R14. Language.** Code, comments and docs are in English. Everything addressed to the owner, including gate questions and the final report, is in plain Arabic with technical names left in English.

**R15. Multi-session work.** This job will not fit in one session. First create the agent instructions file for your tool (`CLAUDE.md` or `AGENTS.md`) in `D:\FrameGlass\app\` with R1 copied word for word, and copy this brief to `docs/BRIEF.md`. Work on one phase per session. End each session by updating `docs/STATUS.md` and `docs/NEXT.md`. At `[OWNER]` items, send the Arabic checklist, record the replies in `docs/evidence/phase-N/owner-answers.md`, and stop until the owner answers.

---

## 3. Environment bootstrap (Windows 11, AMD RX 6500 XT)

The PC has `node`, `python` and `ffmpeg`. It has no JDK, Gradle, NDK, adb or Android Studio. D: has about 124 GB free.

### 3.1 Layout

| Path | Contents |
|---|---|
| `D:\dev\jdk-21` | Eclipse Temurin 21, Windows x64 zip, no installer. Record the exact 21.0.x. |
| `D:\dev\android-sdk` | `ANDROID_HOME` |
| `D:\dev\gradle-home` | `GRADLE_USER_HOME` |
| `D:\FrameGlass\app` | The source tree. One private GitHub remote, per `WAIVERS.md`. |
| `D:\FrameGlass\secrets` | Keystore and `keystore.properties`. Outside the repo. |
| `D:\FrameGlass\testmedia` | `gen\` for generated clips, `real\` for the owner's samples. Outside the repo. |
| `D:\FrameGlass\reference` | Nuvio reference images. Outside the repo. |
| `D:\FrameGlass\storyboards` | Milestone storyboards. Outside the repo. |
| `D:\FrameGlass\releases` | Good APKs, kept for rollback. Outside the repo. |
| `D:\FrameGlass\logs` | Build and harness logs. Outside the repo. |
| `D:\FrameGlass\shots` | Current phone captures. Outside the repo. |

### 3.2 Install steps

1. Unpack Temurin 21 to `D:\dev\jdk-21`.
2. Unpack `commandlinetools-win-15859902_latest.zip` from https://developer.android.com/studio#command-line-tools-only so that `D:\dev\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat` exists.
3. Show the owner `sdkmanager --licenses` to accept. Do not accept SDK licences on the owner's behalf.
4. Run `sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" "build-tools;36.0.0"`. AGP 9.5.1 uses 36.0.0 by default, and `fg.ps1` uses 37.0.0.
5. The Gradle wrapper is in the repo, so use only `gradlew.bat`. `tools/bootstrap_downloads.py` fetches the toolchain with checksums on a new PC.
6. No NDK and no Android Studio. Every native piece comes as a prebuilt AAR. `ndk;28.2.13676358` is allowed only if you need `llvm-readelf` or stripping. Building mpv-android yourself is not an option, because its build does not work on Windows or WSL.

### 3.3 The single command

`D:\FrameGlass\app\fg.ps1`, compatible with Windows PowerShell 5.1:

| Command | What it does |
|---|---|
| `.\fg.ps1 ship` | **The one build command.** Builds the signed release APK, verifies it, installs it on the phone with `adb install -r`, runs `cmd package compile -m speed-profile -f com.frameglass.player`, launches it, and prints the installed `versionName`. |
| `.\fg.ps1 build` | `assembleRelease`, then `apksigner verify --print-certs`, `zipalign -c -P 16 -v 4`, the size budget check, and a listing of `lib/` in the APK. Fails on any problem. |
| `.\fg.ps1 phone` | Reconnects adb and prints model, One UI build, `SDK_INT`, page size and free storage. |
| `.\fg.ps1 doctor` | Prints and checks every tool version against section 4.2. |
| `.\fg.ps1 gate N` | Runs the `[AUTO]` checks of gate N and writes `docs/evidence/phase-N/`. |

`versionCode` is 100 + phase number and `versionName` is `1.0.0-pN` until the final `1.0.0`. The old build was versionCode 8, so upgrades stay possible.

### 3.4 Build rules

- All gates run on the signed, R8-minified release build or the `qa` build type derived from it. A debug build never counts, because R8 can break JNI libraries and debug Compose performance means nothing.
- `isMinifyEnabled = true`, `isShrinkResources = true`. Write keep rules for the JNI classes of NextLib, ass-kt and mpv, and add `-keepclassmembers` for `MatroskaExtractor.subtitleSample` and `extractorOutput`.
- Set `defaultConfig { ndk { abiFilters += "arm64-v8a" } }`, then confirm by listing `lib/` in the APK. The syntax is not rechecked against AGP 9.5.
- AGP 9 has built-in Kotlin. Do not apply `org.jetbrains.kotlin.android`. Add `kotlin-gradle-plugin` 2.4.21 to the buildscript classpath and keep the Compose compiler plugin.
- Use `kotlin("jvm")` 2.4.21 for the pure `:parser` module.
- `.gitignore` covers `*.jks`, `keystore.properties`, `local.properties`, `*.mkv`, `*.mp4`. A pre-commit hook rejects those files and any string that looks like an API key.

### 3.5 Keystore

[DECISION D1] Default: the app was never installed on any phone, so there is nothing to migrate. Keep package `com.frameglass.player` and reuse the old key.

- Copy `android/.signing/frame-glass.jks` and the password from `android/.signing/password.txt` to `D:\FrameGlass\secrets\`, which is outside any repo.
- Write `keystore.properties` there. `build.gradle.kts` reads the folder from the environment variable `FG_SECRETS`, and the build fails if it is missing.
- Never print the password, and never put it in a log, commit, report or command line that gets recorded.
- Ask the owner to copy the secrets folder to one removable drive. A later Android developer-verification route needs the same package name and key.

### 3.6 Wireless adb

Windows needs only Platform Tools. Wireless debugging avoids the Samsung USB driver.

1. The owner turns on Developer options and Wireless debugging, chooses "Pair device with pairing code", and reads you the address and code.
2. Run `adb pair <ip>:<pairport> <code>`, then `adb connect <ip>:<port>`.
3. `adb shell getprop ro.product.model` must identify a Galaxy S26 Ultra, and `adb shell getprop ro.build.version.sdk` must print the phone's API level.
4. Android 17 with adb 37.0.0 auto-connects on trusted networks. If the link drops, `fg.ps1 phone` reconnects it.
5. Auto Blocker must be off, or the install is refused. Its effect on wireless adb and any automatic return are `[UNVERIFIED]`. If an install fails, check it first.

### 3.7 What only the owner can do

1. Update the phone to the latest One UI 9 / Android 17 build and report it.
2. Turn Auto Blocker off. Turn on Developer options and Wireless debugging. Read out the pairing code.
3. Accept the Android SDK licences.
4. Create a TMDB account and API key, and type the key into the app. OMDb, OpenSubtitles, SubDL and SubSource keys are optional and go in the same way.
5. Put real samples in `D:\FrameGlass\testmedia\real\`: any owned files with Dolby Vision, DTS-HD MA, TrueHD, E-AC-3 Atmos, PGS, VobSub, and heavy fansub Arabic ASS. Plus a list of real file names from the library.
6. Add Frame Glass to Samsung's "Never sleeping apps" list.
7. Answer the `[OWNER]` gate questions.

---

## 4. Architecture

### 4.1 Engine decision

**Primary engine: Media3 ExoPlayer 1.11.1 with the NextLib FFmpeg extension and libass through ass-media in `OVERLAY_OPEN_GL` mode.**

Reasons:

- It has the best HDR path here: hardware MediaCodec into a SurfaceView, with HDR10, HDR10+ and HLG handled natively. The phone has no Dolby Vision display path.
- It has the lowest battery cost, and native MediaSession and picture-in-picture.
- NextLib gives DTS, DTS-HD, TrueHD, AC-3 and E-AC-3 decoding from a prebuilt AAR with no NDK, with FFmpeg 9.0.1 and dav1d 1.5.4.
- libass with HarfBuzz and FriBidi closes the biggest hole in Media3, which is real ASS rendering and Arabic shaping. The overlay mode supports animation and HDR and does not block the UI thread.

Rejected:

- **libVLC:** 92.6 MB of AARs, Android Arabic fallback problems, and 4.0 is still EAP.
- **mpv as primary:** HDR10 needs gpu-next plus Vulkan and loses hardware decode. An October 2026 report describes no HDR and choppy playback. `mediacodec_embed` drops subtitles.
- **The official Media3 FFmpeg module:** it is not on Maven and needs the NDK.
- **Jellyfin's decoder:** it lags at 1.9.0+1.

Licence: NextLib is GPL-3.0. [DECISION D2] Default: accept GPL-3.0, because this is a private build that is never distributed. This replaces the old "LGPL build" line. Keep the licence texts in About. If the owner ever shares the APK, GPL obligations apply and this needs review.

**Second engine: libmpv.** [DECISION D3] Default: a separate, gated milestone (Phase 8), built only if Gate 0 shows a real need such as Dolby Vision Profile 5, WMV/RMVB containers, or a Core file Media3 cannot play. mpv and NextLib ship identically named `libavcodec`, `libavutil`, `libswresample` and `libswscale` at different versions, so they cannot share one APK as published. The UI must never know which engine runs: mpv is wrapped in `SimpleBasePlayer`, so controls and session code see one `Player`.

**Two risks found while writing this brief. Prove both before anything else in Phase 0:**

1. **ass-media version mismatch.** The published `ass-media:0.5.1` POM declares Media3 **1.8.0** and Kotlin 2.2.0, while NextLib `1.11.1-0.17.0` requires Media3 **1.11.1**. The repo's master branch has moved to 1.11.1, but no release has. A static check found 132 Media3 references with none missing, so the method links look fine, but behaviour is unproven. The real hazard is reflection on the private fields `MatroskaExtractor.subtitleSample` and `extractorOutput`, and the library has empty consumer rules. If 0.5.1 fails on 1.11.1, compile `lib_ass_media` from tag 0.5.1 with Media3 bumped to 1.11.1, not from master, as a local module `:third_party:ass-media` (MIT), and keep the prebuilt `ass-kt:0.5.1` for native code. Whether that needs no NDK is `[UNVERIFIED]`.
2. **Delay controls.** Cue subtitles can use NextLib's `ExoPlayer.subtitleDelayMilliseconds` through `NextTextRenderer`, which must be proved beside ass-media. Overlay ASS goes through `videoTimeCallback`. Media3 itself has no usable API under this pin, because `timeOffsetUs` arrived in 1.12.0-alpha01. Audio delay remains unproven. Both are must-have features here, so Phase 0 must prove them (step 0.6). A delay that cannot be delivered on Media3 is an engine question for the owner, not a footnote.

Also keep in mind that NextLib does not recover from runtime decoder failures. The app must catch them and reopen the file (F-P18). NextLib's software video path has no PQ handling, so HDR that falls to software decode will look wrong. Treat that as a known limit and show it in the info panel. Software decode of 10-bit H.264 (Hi10P, row V2) is `[UNVERIFIED]`: NextLib lists H.264 but does not mention High 10.

### 4.2 Version pins

| Piece | Coordinates | Version |
|---|---|---|
| JDK | Eclipse Temurin | 21 |
| Gradle | wrapper | 9.8.2 (19 Oct 2026) |
| Android Gradle Plugin | `com.android.tools.build:gradle` | 9.5.1 (9.5 needs Gradle 9.6.0+) |
| Kotlin | `kotlin-gradle-plugin` | 2.4.21 (8 Oct 2026) |
| KSP | `com.google.devtools.ksp` | 2.3.12, the version Next Player ships with AGP 9.5.1, Kotlin 2.4.21, Room 2.8.5 and NextLib |
| Compose BOM | `androidx.compose:compose-bom` | 2026.09.00 |
| Material3 | `androidx.compose.material3:material3` | 1.4.0 (stable). Do not use 1.5.0-rc01. |
| Activity Compose | `androidx.activity:activity-compose` | 1.13.0 |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 |
| Core | `androidx.core:core-ktx` | 1.19.1 |
| Navigation 3 | `androidx.navigation3:navigation3-runtime`, `navigation3-ui` | 1.2.1 |
| Media3 | `androidx.media3:media3-exoplayer`, `-session`, `-ui`, `-ui-compose`, `-inspector`, `-inspector-frame`, `-effect` | 1.11.1 (10 Sep 2026) |
| FFmpeg extension | `io.github.anilbeesetti:nextlib-media3ext` | 1.11.1-0.17.0 (30 Oct 2026). Tied to Media3 1.11.1. |
| Media probe | `io.github.anilbeesetti:nextlib-mediainfo` | 1.11.1-0.17.0 |
| libass | `io.github.peerless2012:ass-media` | 0.5.1 (11 Aug 2026). See the risk in 4.1. |
| libmpv, Phase 8 only | `io.github.marlboro-advance:mpv-android` | 1.0.0 (25 Sep 2026) |
| Glass | `dev.chrisbanes.haze:haze`, `haze-glass` | 2.1.0 (2 Nov 2026) |
| Database | `androidx.room:room-runtime`, `-ktx`, `-compiler`, `-testing` | 2.8.5 |
| Background work | `androidx.work:work-runtime-ktx` | 2.12.0 |
| Preferences | `androidx.datastore:datastore-preferences` | 1.3.0 |
| Paging | `androidx.paging:paging-compose` | 3.5.3 |
| Images | `io.coil-kt.coil3:coil-compose`, `coil-network-okhttp` | 3.6.3 |
| Colour | `androidx.palette:palette` | 1.1.0. `palette-ktx` is now empty. |
| HTTP | `com.squareup.okhttp3:okhttp` | 5.5.1 |
| JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 |
| Benchmarks | `androidx.benchmark:benchmark-macro-junit4` | 1.5.0 |
| Frame stats | `androidx.metrics:metrics-performance` | 1.0.0 |
| Startup profile | `androidx.profileinstaller:profileinstaller` | 1.4.1 |
| UI tests | `androidx.test.uiautomator:uiautomator` | 2.4.1 |

Rules:

- Haze 2.1.0 was built with Kotlin 2.4.21 and Compose Multiplatform 1.12.2, while the BOM gives Compose UI 1.12.1 and Material3 1.4.0. Run `gradlew :app:dependencies` in Phase 0, record what resolves, and accept the higher patch version Gradle picks.
- Do not move a pin unless a build break forces it. Log every change in `docs/VERSIONS.md` with the reason.
- Do not use Hilt. Use one manual `AppContainer` in `Application`.

### 4.3 Modules

| Module | Holds |
|---|---|
| `:app` | `MainActivity`, navigation, Home, Library, Details, Search, Settings, onboarding |
| `:player` | `PlayerActivity`, `PlaybackService`, controls, gestures, sheets |
| `:engine` | `FramePlayer`: Media3 setup, renderers, subtitle pipeline, track logic, probing, fallback, later the mpv adapter |
| `:data` | Room, DataStore, repositories, scanner, workers, backup |
| `:net` | TMDB, AniList, anibridge, AniSkip, TheIntroDB, OMDb, subtitle providers, host allow-list, secret store |
| `:parser` | Pure Kotlin JVM: filename parser and Arabic normaliser, no Android imports |
| `:glass` | Tokens, `GlassSurface`, motion, typography |
| `:qa` | Matrix harness, included only in the `qa` build type |
| `:benchmark` | Macrobenchmarks and the Baseline Profile generator |

The `qa` build type uses `initWith(release)` and `applicationIdSuffix ".qa"`, and is signed with the same key so it installs beside the real app.

### 4.4 Player view stack

`PlayerActivity` is separate from `MainActivity`, because PiP and orientation act on the whole activity. Its root is a `FrameLayout` with four layers, bottom to top:

1. **Video:** a `SurfaceView` in your own `AndroidView`, connected with `player.setVideoSurfaceView()`. Always SurfaceView, never TextureView for video. TextureView costs up to 30% more power, has worse frame timing, and has a known HDR tone-mapping path. `PlayerSurface` appears not to expose its view, and `setBlurRegions()` needs it.
2. **Controls:** a `ComposeView`.
3. **Subtitles:** `AssSubtitleView(context, assHandler)` for ASS and SSA, plus a Media3 `SubtitleView` for SRT, VTT, PGS and VobSub. Both are non-clickable. `AssSubtitleView` holds a transparent TextureView of its own. That is fine, because the video rule above concerns the video only. Check in Phase 0 that the overlay works with your own stack rather than `PlayerView`.
4. **Sheets:** a second `ComposeView`.

Subtitles sit above the controls and move up by the control height while controls show. Sheets sit above subtitles.

### 4.5 Data layer

Room 2.8.5 with `exportSchema = true` from version 1. DataStore holds preferences. The encrypted key blob lives in its own DataStore file. Writes are per row, off the main thread, and never rewrite the whole library. Save playback position every 5 s and on pause, stop and backgrounding.

| Entity | Key fields |
|---|---|
| `Volume` | `uuid`, `label`, `isRemovable`, `lastSeenAt` |
| `RootFolder` | `id`, `volumeUuid`, `relPath`, `addedAt`, `enabled` |
| `MediaFile` | `rowId` PK, `fileId` indexed and not unique, `volumeUuid`, `relPath`, `size`, `mtime`, `durationMs`, `width`, `height`, `videoCodec`, `hdrType`, `audioSummary`, `osHash`, `available`, `firstSeenAt`, `lastSeenAt`, `titleId`, `episodeId`, `releaseTags`, `hasSidecarSub` |
| `Title` | `id`, `kind` (MOVIE, SERIES, ANIME), `groupKey`, `name`, `originalName`, `year`, `overview`, `runtime`, `tmdbId`, `anilistId`, `malId`, `imdbId`, `matchState`, `matchConfidence`, `manualMatch`, `coverPath`, `manualCover`, `backdropPath`, `logoPath`, `dominantColor`, `coverLuma`, `collectionId`, `fetchedAt` |
| `Episode` | `id`, `titleId`, `season`, `number`, `absolute`, `name`, `overview`, `stillPath`, `runtime`, `airDate`, `fetchedAt` |
| `WatchState` | `playableKey` PK, `positionMs`, `durationMs`, `watched`, `lastPlayedAt` |
| `SeriesPrefs` | `titleId` PK, `audioLang`, `subLang`, `subsOff`, `speed` |
| `FilePrefs` | `fileId` PK, `audioTrack`, `subtitleTrack`, `subtitleUri`, `subtitleEncoding`, `subtitleDelayMs` |
| `Person`, `TitlePerson` | `personId`, `name`, `photoPath`, `character`, `order`, `fetchedAt` |
| `Genre`, `TitleGenre`, `Collection` | ids and names |
| `Rating` | `titleId`, `source` (TMDB, IMDB), `value`, `votes`, `fetchedAt` |
| `IntroSegment` | `titleId`, `season`, `episodeId?`, `kind` (OP, ED, RECAP), `startMs`, `endMs`, `source`, `confidence` |
| `AnimeMap` | `anilistId`, `malId`, `tmdbShowId`, `tmdbSeason`, `srcRange`, `dstRange` |
| `LookupQueue` | `titleId`, `state`, `attempts`, `retryAt` |
| `ScanLog` | `startedAt`, `root`, `checkpoint`, `stopReason`, `counts` |
| `SearchIndex` | FTS table over normalised title, original title, people, genres |

Identity rules:

- `fileId` = SHA-256 of the file size, the first 256 KiB and the last 256 KiB. Duration is dropped from the old formula because it changes between engines. The same read computes the OpenSubtitles `osHash` (size plus first and last 64 KiB), stored locally only.
- `playableKey` = `titleId` plus the parser's own season and episode numbers, never remapped, or the title for movies. With no parsed episode it is the `fileId`. Watch state belongs to the playable, so a 720p and a 1080p copy of one episode share progress.
- Fix match rekeys the affected rows in one transaction. Titles that resolve to one provider ID merge unless one of them is a manual match.
- `groupKey` = normalised title, year and kind, persisted on the `Title` row, so a later parser change cannot split a series.
- A rename or move keeps the `fileId`, so the row updates its path. A missing file sets `available = false` and deletes nothing.
- A manual match or manual cover always wins and no background job may overwrite it.
- If [DECISION D1] finds old data, keep old `fileId` and `groupKey` values in an alias table. The old fingerprint used a duration from MediaMetadataRetriever and cannot be recomputed reliably.

### 4.6 Storage access

[DECISION D4] Default: `MANAGE_EXTERNAL_STORAGE`. Only this gives sidecar subtitles, native paths and USB drives together. SAF has no native paths, warns about large scans, and bars root and Download. MediaStore has no sidecars. This is a sideloaded private app, so no store policy applies. It is still broad, so read only what is listed here.

- Scan only folders the owner adds. The + button opens a folder browser built on direct paths.
- Walk added folders on every volume with one cached code path. MediaStore is only a change signal. A folder the owner adds is scanned even if it holds `.nomedia`.
- USB: walk on mount with an mtime and size cache. Row key = volume UUID plus relative path, so an unplugged SSD shows as Unavailable and returns intact. The phone has no microSD, so USB drives are the removable case.
- Detect changes with `MediaStore.getVersion` plus `getGeneration`, and a `ContentObserver` while the app is visible. A `StorageVolumeCallback` handles mount and unmount.
- Open files by direct path with `FileDataSource`. Measure seek speed on USB.
- Inside an added folder, skip hidden subfolders and `.nomedia` subfolders by default, following the public Android convention, with a toggle in Settings for each.
- Never write into the owner's media folders. Downloaded subtitles go to app storage. Screenshots go to `Pictures/Frame Glass` through MediaStore.

`[UNVERIFIED]`: whether restricted settings affect All Files access after adb install, exFAT and NTFS behaviour on USB. Recommend exFAT for SSDs. NTFS reports are community-only.

### 4.7 Background work

One UI may put unused apps to sleep after about 3 days and has no developer-side cure, so the design must not depend on background runs.

- **Catch-up on app open is the primary path.** Scan and queue processing start every time the app opens. They must not delay the first screen.
- WorkManager periodic scan every 12 h, as a bonus.
- Every worker is short, resumable and checkpointed in the database, because job quotas apply since Android 16 even beside a foreground service.
- Log `WorkInfo.getStopReason()` to `ScanLog`.
- The metadata worker requires network and retries after 1 h, 6 h and 24 h.
- Local intro detection is user-started and runs in a `mediaProcessing` foreground service. That type has 6 h per 24 h, and `onTimeout` must call `stopSelf()`.
- Onboarding links to Samsung's "Never sleeping apps" list, once, with a skip option.

### 4.8 Playback service and Android 17 rules

- Use a Media3 `MediaSessionService` with `foregroundServiceType="mediaPlayback"`. Android 17 silences background audio with no error unless the app is visible, in PiP, or running a foreground service, and target 37 also needs while-in-use capability.
- Test with `adb shell cmd audio set-enable-hardening throw`.
- Edge-to-edge is mandatory, with no opt-out. Predictive back is on: use `OnBackPressedCallback` and Navigation 3, never `onBackPressed` or `KEYCODE_BACK`.
- Android 17 applies RAM-based memory limits to all apps. A kill shows `MemoryLimiter:AnonSwap`. Check `am memory-limiter status`. Bound native memory: libass `cacheSize` 128 MB at most, Coil memory cache 256 MB at most, a load control target of 256 MB at most.
- Orientation lock still works on this phone because the rules apply at sw≥600dp, and the phone should be under that. A DeX or large external window may ignore it, so survive any resize without restarting playback.

### 4.9 Network and secrets

- One OkHttp client. An interceptor rejects any host outside this allow-list, and a unit test fails if a second client exists: `api.themoviedb.org`, `image.tmdb.org`, `graphql.anilist.co`, the GitHub release hosts for anibridge, `api.aniskip.com`, the TheIntroDB API host, `api.opensubtitles.com`, the SubDL and SubSource API and download hosts, `www.omdbapi.com`, plus the owner's custom TMDB base URL. Confirm the exact hostnames in Phase 3.
- `usesCleartextTraffic="false"`. Connect timeout 8 s, read timeout 15 s. TheIntroDB took 9.6 s from Libya.
- Every provider returns a typed result and fails softly. Offline playback never depends on any service. Provider errors appear only in Settings or at the point of a user action, never as a playback block.
- Do not use keyless aggregators. Wyzie's sudden API-key requirement broke subtitle search in mpvEx.
- Do not use Jikan (timed out 3 of 3 from the owner's connection) or AniDB (client registration, leeching bans).

[DECISION D5] Default: the owner's own TMDB key in Settings, plus an editable base URL so a Cloudflare Worker can be added later if phone data fails. Measured from the owner's PC in Libya today: TMDB API 0.29 s, images 0.18 s. Phone data is untested. This replaces the old proxy, whose empty URL stopped all metadata and which adds nothing for one user.

### 4.10 Manifest

Permissions, and no others without asking: `INTERNET`, `ACCESS_NETWORK_STATE`, `MANAGE_EXTERNAL_STORAGE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.

Application: `allowBackup="false"`, `supportsRtl="true"`, `usesCleartextTraffic="false"`, `appCategory="video"`. Add `<profileable android:shell="true"/>`, because Macrobenchmark errors on non-profileable targets.

`PlayerActivity`: `supportsPictureInPicture="true"`, `resizeableActivity="true"`, `launchMode="singleTask"`, full `configChanges`, and an `ACTION_VIEW` filter for `video/*`.

---

## 5. Phases and gates

Each phase is a vertical slice that ends with a shipped APK on the phone. Each gate has `[AUTO]` checks and `[OWNER]` checks. At each gate write `docs/evidence/phase-N/README.md`, then give the owner a short Arabic report. Wait for the owner's answers to `[OWNER]` items before starting the next phase. Strings go in English and Arabic resources from the first screen. No hard-coded text at any phase.

### Phase 0. Prove the engine on the phone

No UI polish. A plain list and a plain player are enough.

**0.1 Bootstrap.** Do section 3. `fg.ps1 doctor` passes. An empty app installs by `fg.ps1 ship`.

**0.2 Device census.** Write `docs/device/census.md`:

- model, One UI build, `SDK_INT`, `SDK_INT_FULL`, security patch
- `getconf PAGE_SIZE`
- every display mode and refresh rate, including whether 24 or 48 Hz exists
- `Display.getHdrCapabilities`
- the full `MediaCodecList` dump, in particular AC-3, E-AC-3, AC-4, DTS, Dolby Vision, VVC, APV and 10-bit H.264
- `am memory-limiter status`
- USB write and read speed if an SSD is available

If `SDK_INT` is below 37, stop and ask the owner to update.

**0.3 Test media.** Write `tools/make_testmedia.py`. Clips are 30 s unless noted, with `testsrc2`, added noise so bitrate is realistic, a burned-in frame counter via `drawtext`, the same number as a bit strip (one `drawbox` per bit, for example `enable='bitand(n,1)'`) that `matrix.py` reads by block luma, and per-channel `sine` tones. The installed ffmpeg is the gyan.dev full build from 2024-09. It has libx264, libx265, libsvtav1, libaom-av1, libvpx-vp9, libvvenc, libxvid, mpeg2video, wmv2, rv20, flv, ac3, eac3, dca, truehd, flac, libopus, libvorbis, alac, libmp3lame, wmav2 and dvdsub. It has no PGS or APV encoder. `mkvmerge` and `dovi_tool` are not installed. `dovi_tool` (MIT) may go in `D:\dev` for the Dolby Vision row. Keep the set on the phone under 15 GB, in `/sdcard/Movies/FrameGlassTest/`.

`Gen` = generated with ffmpeg. `Real` = the owner's file. A Real row with no file is `NOT TESTED`, never passed.

| ID | File | Source | Class |
|---|---|---|---|
| V1 | H.264 8-bit 1080p24, MP4, AAC | Gen | Core |
| V2 | H.264 **10-bit** (Hi10P) 1080p, MKV, as used by older anime releases | Gen | Core |
| V3 | HEVC 8-bit 1080p, MKV | Gen | Core |
| V4 | HEVC 10-bit 1080p, MKV | Gen | Core |
| V5 | HEVC 10-bit 4K60 at 80 Mbps, MKV, 2 min | Gen | Core |
| V6 | AV1 10-bit 1080p, MKV | Gen | Core |
| V7 | AV1 10-bit 4K, MKV | Gen | Core |
| V8 | VP9 Profile 2, 10-bit 4K, WebM | Gen | Core |
| V9 | MPEG-4 ASP (Xvid), AVI, MP3. Generate with `-bf 2 -flags +qpel`, because default libxvid output is Simple Profile. NextLib has no `mpeg4` decoder, so this depends on Samsung's decoders. A failure is an R11 engine question. | Gen | Core |
| V10 | Rotated phone clip with rotation metadata, and anamorphic 720x576 at 16:9 | Gen | Core |
| V11 | 23.976 fps, 25, 29.97, 60 and 120 fps, plus one VFR clip | Gen | Core |
| V12 | MPEG-2 interlaced in TS | Gen | Stretch |
| V13 | WMV2 with WMA in ASF | Gen | Stretch |
| V14 | RealVideo: rv20 RM from ffmpeg, RV40 RMVB from the owner | Gen, Real | Stretch |
| V15 | VVC 1080p | Gen | Stretch: software, or a clear unsupported message |
| V16 | FLV H.264 | Gen | Stretch |
| V17 | 8K30 HEVC, and APV, both recorded on the phone | Real | Stretch |
| H1 | HDR10 HEVC 4K60 at 80 Mbps with master-display and max-cll, 2 min | Gen | Core |
| H2 | HLG HEVC | Gen | Core |
| H3 | HDR10+ dynamic metadata. Use `dhdr10-info`, which this build's libx265 accepts. | Gen or Real | Core if a file exists |
| H4 | Dolby Vision P8.1, plays as its HDR10 base | Gen with dovi_tool, or Real | Core if a file exists |
| H5 | Dolby Vision P5, deliberately unmapped in ExoPlayer | Real | Stretch: warn clearly, or Phase 8 |
| H6 | Dolby Vision P7, needs a selector for the HDR10 base layer `[UNVERIFIED]` | Real | Stretch |
| A1 | AAC-LC stereo, MP3 | Gen | Core |
| A2 | AC-3 5.1 | Gen | Core |
| A3 | E-AC-3 5.1 | Gen | Core |
| A4 | DTS 5.1 core (`dca -strict -2`) | Gen | Core |
| A5 | TrueHD 5.1 (`truehd -strict -2`) | Gen | Core |
| A6 | FLAC 5.1, Opus 5.1, Vorbis | Gen | Core |
| A7 | DTS-HD MA, E-AC-3 Atmos | Real | Core if a file exists |
| A8 | ALAC, 24-bit PCM, WMA2 | Gen | Stretch |
| S1 | SRT in UTF-8, UTF-8 BOM, UTF-16 BOM, and Windows-1256 without BOM | Gen | Core |
| S2 | WebVTT, and MP4 `mov_text` | Gen | Core |
| S3 | Styled Arabic ASS: outline, colours, `\an`, `\pos`, `\fad`, sitting at the bottom as scripted | Gen | Core |
| S4 | Heavy ASS: 5,000 events with `\move`, `\t` and karaoke over V5 | Gen | Core |
| S5 | MKV with embedded ASS and an attached font, plus embedded SRT | Gen | Core |
| S6 | ASS naming a missing font: Arabic must fall back with joined letters, no boxes | Gen | Core |
| S7 | Mixed bidi lines: `(ضحك) نعم!`, `S01 هو الأول.`, `قال "hello" ثم مشى.`, numbers and punctuation at line ends | Gen | Core |
| S8 | One MKV with 3 audio and 5 subtitle tracks and language tags | Gen | Core |
| S9 | PGS and VobSub | Real | Core if a file exists |
| S10 | Real heavy fansub Arabic ASS | Real | Core if a file exists |
| C1 | MKV chapters, and MP4 chapters | Gen | Core |
| C2 | Truncated file cut at 60%: plays, then a friendly error, no crash | Gen | Core |
| C3 | Zero-byte file, unreadable file | Gen | Core |
| C4 | Path with Arabic, spaces and emoji | Gen | Core |
| C5 | 5-hour file: seek to 10 random points | Gen | Core |
| C6 | V5 played from a USB SSD | Gen | Core if a drive exists |
| Y1 | Sync clip: one white flash and one beep each second | Gen | Core |
| Y2 | Frame-counter clip at 23.976 for step tests | Gen | Core |

**0.4 Harness.** `:qa` provides a bare `MatrixActivity`, started with `am start ... --es manifest <path>`. For each file it plays, seeks and exports JSON: decoder name, hardware or software, first-frame time, dropped frames from `AnalyticsListener`, audio format and RMS through a tap `AudioProcessor`, selected tracks, errors. `tools/matrix.py` pushes the clips, runs the harness, pulls JSON, takes `adb exec-out screencap -p`, compares SDR frames with ffmpeg-extracted reference frames by SSIM, and writes `docs/evidence/phase-0/matrix.csv`.

A row passes when all of these hold:

- first frame ≤ 1.5 s (≤ 3 s for V5 and V7)
- dropped frames ≤ 0.2% over the clip
- the frame after 5 random exact seeks shows the expected bit strip value within 1 frame
- SSIM ≥ 0.90 on SDR rows
- audio RMS above −50 dBFS where audio exists
- no uncaught exception

**0.5 Engine setup.** Use `AssRenderType.OVERLAY_OPEN_GL`, built through `buildWithAssSupport`, on top of a renderers factory from NextLib, in the section 4.4 view stack.

- Use `EXTENSION_RENDERER_MODE_ON`, so platform decoders win and FFmpeg is the fallback. Turn decoder fallback on.
- Prove the first risk in 4.1 here. Record which route worked.
- Bundle the Arabic subtitle font and pass it through `AssHandler.addFont` at every media change, because `AssHandler` sets no fallback font and fonts are cleared at that point. `ass-kt` 0.5.1 has no default-font setter. If row S6 still shows boxes after `addFont`, a fix needs a patch with the NDK and CMake, so stop and ask the owner.
- Start with `maxRenderPixels = 0`. If row S4 misses, try `3_686_400`, then `2_073_600`, and record the visual cost.
- `OVERLAY_CANVAS` is the fallback render type if `OVERLAY_OPEN_GL` fails on your stack.
- For SRT with a non-UTF-8 encoding, detect it (F-P8), transcode to a temporary UTF-8 file in app cache, and pass that as the `SubtitleConfiguration`.

**0.6 Delay proof (go or no-go).**

- Subtitle delay ±10 s, 50 ms steps, live, with no re-prepare, on SRT and ASS. Routes: NextLib `subtitleDelayMilliseconds` for cue-based tracks, and subtracting the delay in `assHandler.videoTimeCallback` for the overlay, which may need your own copy of `AssSubtitleView`.
- Audio delay ±2 s, 50 ms steps, live. Candidate route: a `ForwardingAudioSink` that offsets the reported position.
- Measure with Y1.

**0.7 Glass-over-video proof.** On `SurfaceView`, call `setBlurRegions()` with `RoundedRectBlurRegion` over a bottom bar. The API is in the android-37.0 stubs. One report says 37.2, so test on this build. Google's Jetchat sample merged this use on 10 Sep 2026. Measure and record on Samsung:

- does it work at all
- does it hold on HDR video, and does it break HDR
- does it stay in step with a 300 ms spring
- does the ASS TextureView layer stay correct above it
- what does `dumpsys SurfaceFlinger` show for GPU composition
- how many dropped frames does it add

Section 7.5 depends on the result. Do not design player glass past this until you have it.

**0.8 Storage proof.** Grant All Files access after an adb install. List a folder with sidecars. Read a USB path directly if a drive exists. Measure seek speed.

**0.9 mpv measure.** If [DECISION D3] is not "none": add the AAR on a branch only, list `lib/` in the APK and look for clashes such as `libavcodec.so` or `libc++_shared.so` against NextLib, then run rows V13, V14, H5 and H1 through mpv. The four FFmpeg libraries are known to clash, so this branch build uses `pickFirst` for mpv's copies. No integration.

**0.10 Results.** Write `docs/phase-0-results.md`. It has one row for each `[UNVERIFIED]` item in this brief, one row for each engine risk, and a plain recommendation.

**GATE 0**

- `[AUTO]` All Core rows pass on the signed `qa` build, or are recorded `NOT TESTED` for lack of a file. Every Stretch row has a recorded result.
- `[AUTO]` 30 minutes of V5, looped with `REPEAT_MODE_ONE`, with S4 subtitles: dropped frames ≤ 0.1%, no crash, no `MemoryLimiter` kill.
- `[AUTO]` `zipalign -c -P 16 -v 4` passes.
- `[AUTO]` Subtitle delay and audio delay work live. Audio delay: `AudioTimestamp` offset within 20 ms of the setting.
- `[OWNER]` Confirms Y1 stays in sync with audio delay set to +500 ms.
- `[OWNER]` Picks the subtitle font from 3 candidates on the phone.
- `[OWNER]` H1 and H2 look brighter than SDR with no washed-out colour. Note whether Privacy Display dims them.
- `[OWNER]` S3, S5, S6, S7 and S10 show joined Arabic letters, correct order, correct position, at the bottom.
- `[OWNER]` A2 to A5 have clear dialogue on the phone speaker and on Bluetooth headphones.
- `[OWNER]` The owner reads `phase-0-results.md` and approves the engine. If Media3 fails something mpv passes, show the data and options and wait for the owner's choice.

### Phase 1. Library core

Scope: F-L1 to F-L4, F-L6 to F-L12, the parser with golden tests, the Arabic normaliser, the Room schema, the scanner, background work, onboarding (permission and folders), a plain grid and folder list that open the Phase 0 player, and plain Settings.

**GATE 1**

- `[AUTO]` All parser golden cases pass.
- `[OWNER]` Labels 200 sampled real names from `name_report`. The parser must score ≥ 98% on title, season and episode against those labels, and every miss is listed.
- `[AUTO]` `adb shell mv` to rename and move a file keeps progress.
- `[AUTO]` Kill during a scan resumes from the checkpoint.
- `[AUTO]` A 2,000-file library made by `tools/make_fixture_library.py` (short valid clips) scans in ≤ 60 s the first time, not counting USB, and rescans unchanged in ≤ 3 s.
- `[AUTO]` Room `MigrationTestHelper` runs for each schema version.
- `[OWNER]` Unplug a USB drive: titles show Unavailable and keep progress. Replug: they reconnect within 10 s.
- `[OWNER]` The owner's real anime and series group into one title each.

### Phase 2. Player core

Scope: F-P1 to F-P20, F-S3, F-S7, F-S10, and a plain F-T5 panel for F-P20. Landscape-first, immersive, all gestures, tracks, subtitle controls, delays, PiP, MediaSession, background audio, restore after kill. Functional controls only. Glass comes in Phase 4.

**GATE 2**

- `[AUTO]` `adb shell am force-stop` during playback: reopening restores position within 6 s and the same tracks.
- `[AUTO]` The audio hardening test passes with no silent background audio.
- `[AUTO]` A decoder error forced through a `qa`-only codec adapter reopens the file at the same position.
- `[OWNER]` Watch one real episode with Arabic subtitles from start to end.
- `[OWNER]` Bluetooth disconnect pauses. A phone call pauses and resumes.
- `[OWNER]` PiP auto-enters on Home press, has working controls and the correct aspect ratio.
- `[OWNER]` Gestures do not fight system edge swipes.
- `[OWNER]` Lock works. Long-press speed works.
- Record: 23.976 judder result, whether a media card appears in the Now Bar (a test item, not a feature), and Privacy Display behaviour.

### Phase 3. Metadata

Scope: F-M1 to F-M15. TMDB, anime flow, Fix match, ratings, About. This phase also adds plain versions of Details, the ⋮ menu, the match-choice sheet and key entry in Settings. Phase 4 styles them.

**GATE 3**

- `[AUTO]` With a fake clock, data refreshes at day 150 and is deleted at day 175 with a fall back to the local frame.
- `[AUTO]` Request-builder tests show only allowed fields leave the phone.
- `[AUTO]` A grep of the APK and git history finds no key.
- `[OWNER]` With the phone set to Arabic and the owner's key, on Wi-Fi and again on phone data, 50 real titles match with no manual step and ≥ 95% of auto-applied matches are right.
- `[OWNER]` In airplane mode the library works and lookups queue, then finish when the network returns.
- `[OWNER]` A manual match and a manual cover survive a forced refresh.

### Phase 4. Shell and liquid glass

Scope: F-U1 to F-U15, all of section 7. Generate and include a Baseline Profile.

**GATE 4**

- `[AUTO]` Macrobenchmarks at 120 Hz with glass on and Power saving off: `frameOverrunMs` P95 ≤ 0 ms and P99 ≤ 4 ms for Library fling, Home scroll, tab change and sheet open.
- `[AUTO]` Contrast fixture: ≥ 4.5:1 on all glass surfaces over white, grey and dark posters.
- `[AUTO]` `dumpsys gfxinfo` and `SurfaceFlinger` captures are saved.
- `[OWNER]` Side-by-side with the reference images: does it reach that level? The glass must visibly react to touch and scrolling.

### Phase 5. Arabic acceptance

Scope: F-A1 to F-A7 as a full audit, in a phone set to Arabic.

**GATE 5**

- `[AUTO]` Android lint `MissingTranslation` = 0.
- `[AUTO]` UI Automator walks every screen in Arabic at font scale 1.0 and 1.3 with zero clipped strings. One text wrapper logs `hasVisualOverflow` in `qa` builds, and the walk fails on any entry.
- `[OWNER]` RTL layout is right on every screen. Copy reads naturally. Punctuation and mixed text sit correctly.

### Phase 6. Smart watching

Scope: F-S1, F-S2, F-S4 (manual and online sources), F-S5, F-S6, F-S8, F-S9.

**GATE 6**

- `[AUTO]` A full season plays through with next-episode countdown and memory.
- `[OWNER]` Skip works on 3 real anime and 2 real series.
- `[OWNER]` Online subtitles are tested on 10 real titles. Arabic coverage per provider is recorded and the default provider order is set from those results.

### Phase 7. Professional tools

Scope: F-T1 to F-T6. F-T7 belongs to Phase 8.

**GATE 7**

- `[AUTO]` With Y2: 10 steps forward advance exactly 10 frames, 10 steps back return, and each step takes ≤ 300 ms at 1080p.
- `[AUTO]` A screenshot without subtitles matches the video resolution.
- `[AUTO]` Chapters match the source, and hidden chapters do not show.
- `[OWNER]` Night audio lifts dialogue with no clipping.

### Phase 7b. Local intro detection

Scope: the local path of F-S4.

**GATE 7b**

- `[AUTO]` On a synthetic 8-episode series with a known 90 s intro at varied offsets: within ±1 s on all episodes.
- `[AUTO]` Zero false positives on silence and on a series with no shared intro.

### Phase 8. Second engine (only if [DECISION D3] requires it)

Scope: the mpv adapter behind `Player`. Probe each file first. Route to mpv only for files Media3 cannot play: Dolby Vision P5, containers outside Media3's table. On a decoder error, reopen in mpv at the same position. Keep mpv out of HDR10 by default. mpv and NextLib cannot share one APK as published. Before Phase 8 starts, offer the owner three options: `pickFirst` mpv's libraries and rerun all Core rows, rename the libraries with patchelf, or drop mpv. NextLib's `media3ext` and `mediainfo` duplicates are byte-identical, so `pickFirst` is safe between those two.

**GATE 8**

- `[AUTO]` Routed rows pass.
- `[AUTO]` All Phase 0 Core rows still pass.
- `[AUTO]` APK within budget.
- `[OWNER]` Anime4K (F-T7), if built, looks better on a 720p anime and shows no thermal drop in 20 minutes.

### Phase 9. Backup, hardening, delivery

Scope: F-L5, a final F-L6 check, every remaining `[UNVERIFIED]` item, the soak test, section 9.

**GATE 9** is the Definition of Done in section 9.

---

## 6. Feature specification

Each ID appears in `docs/STATUS.md`. Section 6.9 maps the old brief onto these IDs.

### 6.1 Library (F-L)

- **F-L1 Stable identity.** IDs per section 4.5. Progress survives rename and move. Test: `adb shell mv`.
- **F-L2 Folders.** Add folders with +, per section 4.6. Remove a folder without deleting progress.
- **F-L3 Unavailable.** If a file or USB drive is gone, mark the title "Unavailable", dimmed. Never delete its progress or match. Reconnect automatically by `fileId`. Home never shows Unavailable items. Library hides Unavailable items from internal storage behind a toggle. The ⋮ menu gains Remove from library.
- **F-L4 Automatic scan.** On app open, on mount, and periodically, per section 4.7. New videos appear with no manual step.
- **F-L5 Backup and restore.** Through the Android file picker, a zip with versioned JSON (`format: "frame-glass-backup"`, `version: 2`) and custom covers. It contains progress, matches, manual covers and preferences. It contains no API keys and no TMDB-cached images. Validate before restoring and reject bad input. Take an automatic safety copy first. Test: dump, wipe, restore, and the dump matches except timestamps.
- **F-L6 Migrations.** Every schema change ships a tested Room migration. An app update must never wipe the library. If [DECISION D1] finds old data, also import old `frame-library.db` and the old backup JSON.
- **F-L7 Instant add.** New files appear at once with a local frame, and matching happens after.
- **F-L8 Parser.** See 6.2.
- **F-L9 Versions.** Several files of one episode show as one row with a version picker. Default to the highest resolution. Progress is shared.
- **F-L10 File facts.** In Folders view and episode rows, keep small chips: NEW (never played, added in the last 7 days), SUB (subtitle present), and size.
- **F-L11 Series grouping.** All files of one series form one title across folders, by `groupKey`.
- **F-L12 Change cover.** Pick an image through the Android picker, copy it to app storage and include it in backups. It counts as a manual cover.

### 6.2 Parser and Arabic normaliser

Write your own Kotlin parser with golden tests. No Kotlin port of guessit or PTT exists. Use `kotlin-anitomy` (pure Kotlin, MPL-2.0, v1.0) for bracket-style anime names if it helps, and read PTT (MIT), Nova's matchers (Apache-2.0) and guessit 4.4.0 as rule sources. Take the rules, not the code. Run `tools/name_report.py` on the owner's real file names.

Steps: strip the extension, remove bidi marks and site tags, normalise separators, extract episode info, take the year (the last plausible year, so `Blade.Runner.2049.2017` gives 2017), cut release tags (1080p, WEB-DL, BluRay, x265, group names), and fall back to folder names skipping `Season N`.

| Input | Expected |
|---|---|
| `Sample.Journey.S01E02.1080p.WEB-DL.mkv` | Series "Sample Journey", S1 E2 |
| `Quiet.Coast.2026.BluRay.x265.mkv` | Movie "Quiet Coast", 2026 |
| `Blade.Runner.2049.2017.1080p.mkv` | Movie "Blade Runner 2049", 2017 |
| `1917.2019.1080p.mkv` | Movie "1917", 2019 |
| `[SubsPlease] Frieren - 05 (1080p) [ABCD1234].mkv` | Anime "Frieren", E5 |
| `[SubsPlease] Frieren - 07v2 (1080p) [ABCD1234].mkv` | Anime "Frieren", E7, revision 2 |
| `One Piece - 1071 [1080p].mkv` | Anime, absolute 1071 |
| `Title 2nd Season - 07 [1080p].mkv` | Anime, S2 E7 |
| `86 - 07 [1080p].mkv` | Anime "86", E7 |
| `Show.S02E07E08.mkv` | S2 E7 to E8, both marked |
| `Show 2x07.mkv` | S2 E7 |
| `Show.Ep05.mkv`, `Show E1100.mkv` | E5, E1100 |
| `Show/Season 02/07.mkv` | "Show", S2 E7 from folders |
| `Show/Specials/S00E03.mkv` | "Show", S0 E3 |
| `[Group] Title OVA - 02.mkv` | Special |
| `[Group] Title - NCOP1.mkv` | Extra, hidden from episode lists |
| `مسلسل الاختيار الحلقة 07.mp4` | Series "الاختيار", E7 |
| `الحلقة ٧.mkv` in folder `العنوان` | "العنوان", E7, Arabic-Indic digit read |
| `العنوان الموسم الثاني الحلقة 12 مترجم.mkv` | S2 E12, flag subtitled |
| `العنوان مدبلج الحلقة 3.mp4` | E3, flag dubbed |
| `فيلم العنوان 2023 مترجم.mp4` | Movie "العنوان", 2023 |
| `www.site.com - Title S01E02.mkv` | Site tag removed |
| A name containing U+200F or U+202B | Marks removed |
| `الْعُنْوَان.S01E01.mkv` | Search key equals the key of `العنوان` |

Arabic normaliser, for search and matching, not display: lowercase, strip U+064B–065F, U+0670, U+06D6–06ED, U+0640 and bidi marks, map أإآ→ا, ة→ه, ى→ي, ؤ→و, ئ→ي, and Arabic-Indic digits to ASCII. Test on JVM and on the phone, because Android regex behaviour on Arabic was never checked.

### 6.3 Metadata (F-M)

- **F-M1 Search.** Search TMDB as movie or TV automatically. Search and score with `language=en-US` plus `alternative_titles` for the top 3 results, because `language=ar` may return neither English nor romaji names `[UNVERIFIED]`. After matching, fetch display fields in the device language with English as a per-field fallback, because Arabic overviews are often empty.
- **F-M2 Confidence.** Score: exact title or original title 0.90, else token Jaccard × 0.78. Year match +0.10, year mismatch −0.25. Auto-apply at ≥ 0.75 with a margin of ≥ 0.12 over the runner-up, and tune this at Gate 3. Below that, show up to 3 choices with covers.
- **F-M3 Fix match.** The action is called "Fix match", lives in the ⋮ menu, and is for corrections only.
- **F-M4 Manual wins.** Never overwrite a cover or match set by hand.
- **F-M5 Saved offline.** Cover, title, year, genre, runtime, plot, rating, cast with photos, collection, episode titles and images, backdrop, title logo. Sizes: poster `w500`, backdrop `w1280`, still `w300`, logo `w500`, profile `w185`.
- **F-M6 Offline queue.** No network means queue and retry later.
- **F-M7 Frame fallback.** With no match, use a frame from the video, never letter art. Sample at 10%, 20% and 30% and keep the frame with the highest luma variance, because the 1 s frame is often black.
- **F-M8 Key handling.** Per [DECISION D5]. With no key, the app works fully as a local player and Settings explains what the key adds.
- **F-M9 Anime flow.** AniList runs first when the parser sets `animeHint` (bracket group, hash tag, absolute number). Otherwise TMDB runs first, and a result with genre 16 and origin JP then runs AniList.
  1. AniList GraphQL identifies the title and gives `idMal`. The rate limit is degraded to 30 per minute (normally 90), so obey the rate headers.
  2. Download `mappings.json.zst` (1.7 MB, 78,000+ IDs, MIT, release tag `v3`, updated daily) from anibridge-mappings weekly on Wi-Fi. Load only `anilist` or `mal` to `tmdb_show` rows into `AnimeMap`. Mappings are one-way, so read both directions. Follow its range and `|ratio` syntax. For decoding, try the pure-Java decoder in `io.airlift:aircompressor` 2.0.3 on ART `[UNVERIFIED]`, then `com.github.luben:zstd-jni` 1.5.7-25 after a 16 KB check, then `mappings.min.json` (9.3 MB) on Wi-Fi.
  3. TMDB supplies display data.
  4. Resolve absolute numbers with anibridge ranges first, then TMDB episode groups of type 2.
- **F-M10 Ratings.** Show the TMDB rating by default, labelled TMDB. IMDb is optional through the owner's OMDb key, in a Settings section called "Optional: IMDb ratings". Never label a TMDB rating as IMDb. Write IMDb as plain text with no logo. Never use OMDb posters.
- **F-M11 TMDB cache rule.** Every TMDB row and image stores `fetchedAt`. Refresh from day 150. If a refresh is impossible, delete that data at day 175 and fall back to the local frame. TMDB's limit is 6 months. If the licence ends, purge everything.
- **F-M12 About.** The official TMDB logo unmodified and less prominent than the app logo, the exact notice "This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.", a statement that the app is not affiliated with IMDb, and licences (GPL-3.0, MIT, Apache-2.0, OFL).
- **F-M13 No AI with TMDB data.** TMDB's terms bar use with ML or AI applications, so TMDB data never enters any AI feature.
- **F-M14 Title logos.** Fetch from `/tv/{id}/images` and the movie equivalent, preferring the device language, then English. fanart.tv is not used in this round.
- **F-M15 Cover colour.** Run a small separate Coil decode with `allowHardware(false)` for Palette, off the main thread, and store `dominantColor` and `coverLuma` in the database.

### 6.4 Screens (F-U)

- **F-U1 Tabs.** [DECISION D7] Default: 4 tabs in a floating glass bar, as in reference images 01 and 02: Home, Search, Library, Settings. Settings replaces Nuvio's Profile tab, with no avatar.
- **F-U2 Library filter.** Chips: All, Movies, Series, Folders. Anime appears as a chip only when anime exists.
- **F-U3 Library sort.** Recently added, Name (with an Arabic-aware key), Rating, in a dropdown, plus a grid and list toggle. The default view is a 3-column poster grid.
- **F-U4 Cards.** 2:3 posters with title and year below, a small glass rating badge in the corner showing the source, and a thin progress line for started titles. Hide the badge when there is no rating. Remove the "IMDb —" line.
- **F-U5 Home.** Full-bleed hero fading into the canvas: title logo or strong typeset title, meta line, one white pill Resume, pager dots. Tapping the hero opens Details. There is no "View details" button. Hero candidates: last played, then up to 4 recently added. Swipe between them, with auto-advance every 8 s only while Home is visible, no touch is active and reduced motion is off. The pill reads Resume for a started title and Play otherwise. Below the hero, at most 3 automatic rows, chosen in order from Continue watching, Top rated in your library, a genre, a movie collection. Continue watching holds in-progress and next-up items, one per series, excluding the hero's item. Hide a row with fewer than 4 titles, except Continue watching. A row with more than 10 titles shows a small View all chip.
- **F-U6 Continue watching card.** 16:9, with time-left chip, episode label, title, thin progress line, and a "New episode" badge when a new file is the next episode.
- **F-U7 Details.** Full-bleed art, title logo, genres, primary pill, ⋮ button, meta row, credits, plot with Show more, round cast photos, then a collection rail if any. The primary pill reads Resume, or Play S1 E1 for an unstarted series. Fix match and Change cover live in the ⋮ menu.
- **F-U8 Actor.** Tapping an actor shows that person's titles in the library only, by a real join on person ID, not a text search.
- **F-U9 Episodes.** Season chips, then full-width rows with a 16:9 image, title, duration and progress. The whole row is tappable, with no large Play buttons. Use a cleaned file name only as a fallback. Dim Unavailable rows.
- **F-U10 Search.** Covers title, actor and genre through the FTS index and normaliser. Results appear while typing, within 100 ms at 2,000 files.
- **F-U11 + button.** Only on Home and Library.
- **F-U12 One look.** Dark Liquid Glass only. No theme choice.
- **F-U13 Copy.** Plurals in resources ("1 video", and Arabic forms for zero, one, two, few, many, other). Show time left ("15 min left"), never "Saved at 15:00". Remove leftover labels such as a bare "Details".
- **F-U14 Settings.** Grouped dark cards, small section labels, title and description rows, value at the end side. Sections: Library, Playback, Subtitles, Online features and keys, Optional: IMDb ratings, Backup and restore, About.
- **F-U15 Onboarding.** Three short steps: All Files access, add folders, optional key. Then the "Never sleeping" hint. An empty Home shows one message and one white pill.

### 6.5 Arabic (F-A)

- **F-A1 Resources.** Every string in resources with a complete Arabic translation. No runtime translation by hash or regex.
- **F-A2 Language.** Follow the phone's language.
- **F-A3 Mirroring.** Mirror all layouts, the tab pill, transitions and back gestures. [DECISION D6] Default: do not mirror the seek bar, transport controls, playback progress lines or horizontal seek gesture. Time runs left to right. This follows Material's bidirectionality guidance and reverses the old brief's line about progress bars.
- **F-A4 Fit.** Long Arabic strings fit with no clipping at font scale 1.3.
- **F-A5 Isolation.** Wrap Latin fragments such as `S01E08`, file names and English titles in bidi isolates (U+2068, U+2069), so punctuation cannot jump.
- **F-A6 Numbers.** Format through the locale, and read Arabic-Indic digits in input.
- **F-A7 Subtitles.** Arabic is the primary case. Prefer the Arabic track and Arabic sidecar when no memory exists. Rows S1 to S10 must stay green in every later phase.

### 6.6 Player (F-P)

- **F-P1 Engine.** Per section 4.1. All formats in the matrix have sound and picture.
- **F-P2 Last resort.** "Open in another player" appears only after an unrecoverable error.
- **F-P3 Immersive.** Landscape by sensor by default, portrait when the video is portrait, plus a rotate toggle. Hide both system bars with transient reveal on swipe. Draw into the cutout. Controls stay in safe insets.
- **F-P4 Controls.** Back at the top start and lock at the top end. Centre play with ±10 s. Title and episode line at bottom start. Thin progress bar. Elapsed and total time at the end. Auto-hide after 3.5 s during playback. [DECISION D8] Default bottom row: icons only, as in reference image 06 (Subtitles, Audio, Speed, Fit, Episodes, More), with labels on long-press.
- **F-P5 Gestures.** Vertical swipe on the left half for brightness and the right half for volume, horizontal swipe to seek, double-tap to skip 10 s (5, 10, 15 or 30 in Settings), long-press for 2×, pinch to zoom. Keep clear of system edge areas. Feedback is minimal, like reference image 09.
- **F-P6 Aspect and zoom.** Fit, Fill, 16:9, 4:3, Crop, plus pinch 1× to 4× with pan. Remember per title. Check that blur regions stay correct while zoomed.
- **F-P7 Subtitle formats.** External and embedded SRT, VTT, ASS, SSA, `mov_text`, PGS, VobSub, with MKV attached fonts honoured. "Add subtitle file" opens a picker at the video's folder.
- **F-P8 Encoding.** BOM check, then strict UTF-8, then a scorer between Windows-1256 and common alternatives based on Arabic letter ratio. Add a manual encoding picker in the subtitle menu, remembered per file. The old code forced 1256 for all non-UTF-8 files, so do not repeat that.
- **F-P9 Subtitle style.** Size, colour, font, outline, background, vertical position and delay. Custom fonts: the owner picks a `.ttf` or `.otf`, the app copies it in, and it is used for SRT and as the libass fallback. For ASS the script's own styling always wins, because ass-kt exposes no style override. Subtitles never sit behind the controls.
- **F-P10 Audio delay.** Live control, remembered per Bluetooth device by `AudioDeviceInfo` product name and type.
- **F-P11 Tracks.** Audio and subtitle selection showing language, codec and channels. Off is an option. Sidecars appear with embedded tracks.
- **F-P12 Speed.** 0.25× to 4× in 0.05 steps with pitch kept, in a sheet like reference image 08. Fade the controls out before the sheet appears.
- **F-P13 PiP and lock.** PiP with auto-enter, the real aspect ratio, a source rect hint, and play and ±10 s actions. A touch lock that survives until unlocked.
- **F-P14 Audio focus.** Pause when headphones disconnect. Correct focus handling through Media3.
- **F-P15 Restore.** Restore position and selected tracks after the app is killed.
- **F-P16 Session and background audio.** Media notification and controls through `MediaSessionService`. Background audio is a setting, off by default.
- **F-P17 Frame rate.** Keep Media3's default seamless strategy and record the result for 23.976 content. If the panel offers no 24 or 48 Hz mode, record it and do not force anything.
- **F-P18 Fallback.** Runtime `MediaCodecList` probing. Switch decoders through NextLib's `DecoderManager`, attached before prepare (https://github.com/anilbeesetti/nextlib/blob/main/media3ext/DECODER_SWITCHING.md). If that fails, reopen at the same position in the next path (software, then mpv if present). Show a plain message for anything unsupported, such as VVC with no software path. Never a blank screen.
- **F-P19 Open with.** Other apps can open a video through `ACTION_VIEW`, including `content://` URIs. Sidecar search works only when a path can be resolved.
- **F-P20 HDR honesty.** The info panel states what is really happening, such as "Dolby Vision P8, playing as HDR10". Warn on P5 and P7 if no route exists.

### 6.7 Smart watching (F-S)

- **F-S1 Next up.** Home shows the next unwatched episode of each started series.
- **F-S2 Countdown.** At the end of an episode, show a next-episode countdown of 10 s that can be cancelled. Credits data from F-S4 may start it early. Unmatched files use the next file in natural folder order.
- **F-S3 Watched.** Mark watched at ≥ 90%, sticky. Completion sets watched and position 0. Watched titles restart from 0. Mark an episode or whole season watched or unwatched by hand.
- **F-S4 Skip intro.** Order: manual, stored result, AniSkip (anime, MAL ID, `GET https://api.aniskip.com/v2/skip-times/{malId}/{ep}` with `types[]=op`, `ed`, `recap` and `episodeLength`, no key, 120 per minute), TheIntroDB by TMDB ID (anonymous 30 per 10 s and 5,000 per day), local. Manual: the owner sets intro start and end once per series, with per-season override. Local: user-started, compares audio across episodes on the device, searches the first 25% or 10 minutes for shared runs of 15 to 120 s. Reimplement it from the idea. Do not copy intro-skipper (GPL-3.0) or link Chromaprint. Show the button only for confident results: online or manual sources, or local agreement across ≥ 3 episodes within ±1.5 s. The old confidence gate equalled the per-frame threshold and did nothing, so test that this one rejects.
- **F-S5 Missing episodes.** Dimmed rows from the TMDB episode list, limited to seasons the owner has, ignoring unreleased episodes.
- **F-S6 Series memory.** Remember audio language, subtitle language and speed per series, by language and not by track index. Apply to the next episode.
- **F-S7 Sidecars.** Load automatically when a subtitle shares the video's base name in the same folder or a `Subs` or `Subtitles` subfolder, including `Name.ar.srt`, `Name.ara.srt`, `Name.arabic.srt`, `Name.ar.forced.ass`. With no base-name match, use the parsed season and episode in the same folder when exactly one candidate exists. Arabic ranks first, and a full Arabic track outranks forced or Signs tracks.
- **F-S8 Night audio.** Optional, off by default. A Media3 `AudioProcessor` with a compressor and limiter that lowers loud peaks and lifts dialogue with no clipping. Unit test: no sample above 0 dBFS, peaks down by ≥ 6 dB on a test clip.
- **F-S9 Online subtitle search.** On tap, in the subtitle menu. Search OpenSubtitles, SubDL and SubSource in parallel with whichever keys exist, Arabic first, then English. Show provider, language, release name and hash match. Rank hash matches first. Spend the OpenSubtitles quota only on tap (5 downloads per 24 h without login). SubDL searches by `tmdb_id`. Handle zips and pick the right episode inside a season pack with the parser. Save under app storage per `fileId`. Provider order is a default until Gate 6 records real Arabic results. [DECISION D9] Default: automatic lookups send only title, year, season, episode and IDs. Only a subtitle search the owner taps may also send the release name and the OpenSubtitles hash with file size. Both can be turned off in Settings.
- **F-S10 Resume logic.** Resume the latest played item if it is unwatched and available, otherwise the next unwatched available item.

### 6.8 Professional tools (F-T)

All behind the More menu, except F-T1.

- **F-T1 Thumbnail seek.** Preview while seeking, on the seek bar, like reference image 07, using `androidx.media3.inspector.frame.FrameExtractor` from `media3-inspector-frame` on keyframes with a disk cache. Must not stutter playback at 4K.
- **F-T2 Chapters.** Chapter list and marks on the seek bar. Media3 1.11 exposes MKV chapters as metadata, and the access API is `[UNVERIFIED]`. If it falls short, write a small reader from the public Matroska spec and MP4 chapter layouts. Hidden chapters stay hidden.
- **F-T3 Frame step.** Forward and back while paused, with timecode and frame number. Media3 has no step API, so use exact seeks and scrubbing mode `[UNVERIFIED]`.
- **F-T4 Screenshot.** At full video resolution without subtitles (`FrameExtractor`), or at screen resolution with subtitles (`PixelCopy`). Saved to `Pictures/Frame Glass`.
- **F-T5 Info panel.** Codec, resolution, bitrate, frame rate, audio format, HDR type, decoder name, hardware or software, dropped frames.
- **F-T6 Sleep timer.** Minutes or end of episode.
- **F-T7 Anime4K.** Only with mpv in Phase 8, and it must measure well on the Adreno 840. One toggle, off automatically at 4K and on thermal pressure. It is reportedly not feasible on Media3.

### 6.9 Trace from the old brief

Nothing in the old brief is dropped silently.

| Old section | Now |
|---|---|
| 1 Library foundation | F-L1 to F-L7, F-L11, F-L12, section 4.6 |
| 2 Covers and data | F-M1 to F-M8, F-L8 |
| 3 Ratings and licence | F-M10 to F-M12 |
| 4 Simpler UI | F-U1 to F-U15 |
| 5 Liquid glass | Section 7 |
| 6 Arabic | F-A1 to F-A7 |
| 7 Playback | F-P1, F-P2 |
| 8 Player audit | F-P5 to F-P15 |
| 9 Smart watching | F-S1 to F-S8 |
| 10 Pro tools | F-T1 to F-T6 |

Replaced lines:

- The TMDB proxy is replaced by [DECISION D5].
- Persistable folder access is replaced by [DECISION D4].
- "Memory card" becomes USB drive, because the phone has no microSD.
- The Android 12 and lower tiers and mid-range 60 fps target are removed by R5.
- "Mirror progress bars" is replaced by [DECISION D6].
- The LGPL FFmpeg line is replaced by [DECISION D2].
- The "tell me first" engine clause is replaced by section 4.1.
- The upgrade-from-previous-version test applies only if [DECISION D1] finds old data.
- "Read the existing code first" is reversed by R6, and every old behaviour worth keeping is written out here.
- Three tabs became four to match the Nuvio target, per [DECISION D7].
- The blue pills became one white pill, and screen tint narrowed to a soft glow on Details and the hero, to match the Nuvio target.
- AGSL edge refraction comes from Haze. Hand-written AGSL stays for the finger highlight and tilt.
- Background intro detection became user-started.
- The low-end device fallback became thermal status SEVERE.

---

## 7. Design system

### 7.1 Visual target

The owner chose the Nuvio mobile app as the visual target for layout, hierarchy and feel. Read `D:\FrameGlass\reference\REFERENCE.md` and its images before building any screen. If you cannot read that folder, stop and ask the owner to attach the images. Follow its "take" and "do not take" table per image. Rules: R8. Do not copy those images into the repo. Main targets: image 01 Home, 02 Library, 03 Details, 06 player, 08 player sheet, 09 seek feedback, 10 series Details (not its bidi bugs).

The principles to hold:

1. Near-black canvas. The artwork supplies the colour.
2. One bright element per screen: the white pill.
3. Title logos wherever one exists, else a strong typeset title.
4. Glass only on floating things.
5. Thin progress lines.
6. Little text on Home. Details holds the information.

### 7.2 Tokens

Keep these in `:glass`. Record final tuned values in `docs/design-tokens.md`.

| Token | Value |
|---|---|
| Canvas | `#0D0D0D` |
| Elevated surface | `#1A1A1A` |
| Card surface | `#242424` |
| Primary action | White pill with near-black text, one per screen |
| Accent | White. No coloured accent in chrome. Status colours only for error and warning. |
| Cover glow | `dominantColor` as a soft radial glow on Details and the hero fade, 16% alpha at most. The canvas stays near-black. |
| Secondary text | 70% white |
| Radii | Poster 12 dp, card and sheet 24 dp, dialog 28 dp, chips full |
| Spacing | 4, 8, 12, 16, 24, 32, 48 dp |
| Landscape card | 1.77:1, about 1.64 × poster width |

### 7.3 Glass rules

- Glass only on the bottom tab bar, top bar, sheets, menus, rating badge and player controls. Never on cards.
- All glass goes through one wrapper in `:glass`: `GlassSurface(kind, shape, state)`. Only this wrapper calls Haze 2.1.0. Keep it swappable for Kyant Backdrop 2.0.1 (API 33+, Apache-2.0, one maintainer, a RenderThread SIGSEGV flagged in its FAQ) without touching screens. Do not mix the two.
- Hand-written AGSL is limited to the finger highlight and the tilt sheen. Do not hand-write refraction.
- Skip `RenderNode.setBackdropRenderEffect`. It needs 37.2, and Haze measured 23 to 46% higher CPU with it.
- Use Material3 1.4.0 for basics, not its opaque look.
- At most 3 blurred layers visible at once.

Starting recipe, tune on the phone:

| Kind | Blur | Base tint alpha over `#0D0D0D` | Rim | Shadow |
|---|---|---|---|---|
| Tab bar | 24 dp | 0.40 | 1 dp, white 18% at top to 4% at bottom | y 8 dp, blur 24 dp, black 40% |
| Top bar | 24 dp | 0.40 | same | none |
| Sheet, menu | 32 dp | 0.52 | same | y 12 dp, blur 32 dp, black 45% |
| Badge | 16 dp | 0.46 | 0.5 dp, white 16% | none |

Add Haze's glass refraction at the edges on tab bar, sheets and menus only. If a Haze cost measurement fails 8.33 ms, cut refraction first, then the blur radius. Haze's own numbers were taken on one Pixel 8a at 60 Hz and say nothing about this phone.

**Contrast rule.** Text and icons on glass keep at least 4.5:1 on any backdrop. Using sRGB blending, the background must stay at or below level `L`: 118 of 255 for white text, 87 for 70% white text. With backdrop level `B`, tint level 13 (`#0D0D0D`) and tint alpha `a`:

`a = max(baseAlpha, (B − L) / (B − 13))`

Over pure white that gives `a = 0.57` for white text and `a = 0.69` for 70% white text. Estimate `B` from the stored `coverLuma` of what sits under the bar, or a small sample at 4 Hz at most, and animate `a` over 200 ms. Verify with the contrast fixture. It renders white, grey and dark posters behind every glass kind, then measures pixels from `adb exec-out screencap`.

### 7.4 Scrolling under glass

Content runs edge to edge behind the bars, and visibly moves under them. The large title shrinks into the top bar as the user scrolls (34 sp to 20 sp), with offsets read in `graphicsLayer {}`. The bottom content inset keeps the last row reachable.

### 7.5 Glass over video

RenderNode and RenderEffect cannot sample a SurfaceView, because its surface sits behind the window. Haze and Backdrop therefore see nothing there. Playback smoothness comes first.

1. Primary, if step 0.7 passes: `SurfaceView.setBlurRegions()` with `RoundedRectBlurRegion` behind each control cluster, with Compose tint and rim on top. It gives blur with no refraction.
2. Fallback, if it fails on Samsung, breaks HDR, drifts during springs or costs frames: no blur. A bottom and top gradient scrim from black 0 to 60%, tinted pills, the same rim. This also holds 4.5:1 and looks intentional.
3. Sheets over paused video may use a `PixelCopy` frame as a static backdrop with full glass. It accepts a SurfaceView. Build this only if it adds no pause on open.

Never switch the video to TextureView to get blur. `blurBehindRadius` from Compose 1.13 rc is out of scope, because the pin is on 1.12 and AOSP warns it can be disabled during video.

### 7.6 Motion

Use these spring values as a start. Material's expressive numbers are copied as tokens, because stable M3 1.4.0 has no expressive scheme.

| Use | Damping ratio | Stiffness |
|---|---|---|
| Fast spatial: press, small toggles | 0.6 | 800 |
| Default spatial: tab pill, sheets | 0.8 | 380 |
| Slow spatial: large morphs | 0.8 | 200 |
| Effects: alpha, tint | 1.0 | 1600 |

- **Tab pill.** Slides between tabs and stretches like liquid, up to 18% wider mid-move, then settles. `graphicsLayer` translation is a raw offset, so flip the sign by hand in RTL.
- **Press.** Glass controls swell to 1.04, a soft highlight follows the finger, then a spring settles them.
- **Tilt.** `TYPE_GAME_ROTATION_VECTOR` at 30 Hz at most, registered only while the app is resumed, shifting the rim highlight 6 dp at most.
- **Shared elements.** `SharedTransitionLayout` from Library cover to Details cover, with Navigation 3 1.2.1 shared scenes and mid-flight predictive back. It excludes Dialog, ModalBottomSheet and AndroidView, and does not animate shape or ContentScale.
- **Sheets.** Morph out of the button that opened them. Build them as in-window `sharedBounds` overlays. Never use `ModalBottomSheet`. Morph applies outside the player, because player controls and sheets are separate ComposeViews. Player sheets follow F-P12.
- **Into the player.** The poster morphs, then the video fades in. The video surface itself never morphs.
- **Interruptible.** All motion uses `Animatable` springs read in `graphicsLayer {}`, so a tap or drag mid-animation continues from the current velocity.
- **Haptics.** Light haptics on tab changes and key toggles.
- **Skeletons.** Placeholder shapes while covers load, never spinners.

### 7.7 Typography

Bundle OFL fonts only. Default: Alexandria for titles, IBM Plex Sans Arabic for UI. Have the owner compare on the phone with Readex Pro, Noto Kufi Arabic, Cairo, Tajawal, Vazirmatn and Beiruti. The subtitle default is a highly legible Arabic font chosen in Phase 0 with the owner.

| Style | Size / line height | Weight |
|---|---|---|
| Display | 34 / 42 sp | 700 |
| Title large | 24 / 32 sp | 700 |
| Title | 20 / 28 sp | 600 |
| Body | 15 / 24 sp | 400 |
| Label | 13 / 18 sp | 500 |
| Caption | 12 / 16 sp | 400 |

### 7.8 Arabic design rules

- Never apply letter-spacing to Arabic, and no tracked caps in either language.
- Target 36 drops compact Arabic metrics, so check line heights on real strings with diacritics and descenders.
- Use `offset`, which mirrors, for layout. Use `absoluteOffset` only for time-based things such as the seek bar.
- Back arrows flip in RTL. Play, skip and seek icons do not, per [DECISION D6].
- The reference image 10 punctuation bugs ("-2022", ".community") must not appear here.

### 7.9 Degraded mode

When Battery saver is on, Remove Animations is on, or thermal status reaches SEVERE: refraction off, tilt off, heavy motion off, and Haze's reduced-transparency path on. One UI power saving can fix 60 Hz and cap CPU, so run this mode once at each gate from Phase 4 on and confirm it stays readable. Playback always has priority over glass.

---

## 8. NOT IN THIS ROUND

Do not build:

- Chromecast, Android TV, tablet or foldable layouts, widgets, Wear OS
- DeX-specific layouts (only survive resizing)
- On-device subtitle generation, Whisper, translation, Gemini Nano features (nothing until measured on the phone in a later round)
- Accounts, cloud sync, social features, streaming, trackers
- SMB, WebDAV or any network share (also avoids `ACCESS_LOCAL_NETWORK`)
- Playlists, audiobooks, music mode
- Torrents, yt-dlp, downloader features
- Ambient mode, media scopes, Shorts mode, skip silence, private vault
- Syncplay, watch parties, danmaku
- Cloud AI keys, Lua or script editors, live speech translation, theme packs
- Volume boost above 100%, audio passthrough
- S Pen features. It has no Bluetooth, so no remote or Air Actions.
- Now Bar and Live Update work (test item only)
- Privacy Display integration (no public API found)
- File rename or delete from inside the app
- fanart.tv
- Trakt, Simkl, MDBList, addons, sources, profiles, downloads (reference-image content)
- A desktop, web or iOS build of any kind

Later candidates, for the owner to revisit: SMB/WebDAV, playlists, Whisper batch subtitles, A-B loop, dual subtitles.

---

## 9. Definition of done

All checks run on the signed release APK on the S26 Ultra, with evidence files. Power saving is off unless stated.

| ID | Check | Pass |
|---|---|---|
| DoD-01 | `.\fg.ps1 ship` from a clean clone | Exit 0, installed `versionName` is `1.0.0` |
| DoD-02 | APK | `apksigner verify` passes, `zipalign -c -P 16 -v 4` passes, only `lib/arm64-v8a`. Size ≤ 60 MB, or ≤ 90 MB with mpv. |
| DoD-03 | Media matrix rerun on final build | 100% of Core rows pass or are recorded `NOT TESTED` for lack of a file. Every Stretch row has a recorded result. |
| DoD-04 | Cold start to interactive Home, 2,000 files | ≤ 1.2 s, measured by Macrobenchmark |
| DoD-05 | Time to first video frame | ≤ 700 ms internal, ≤ 1 s USB, on 1080p HEVC |
| DoD-06 | Exact seek to next frame shown | ≤ 500 ms at 1080p, ≤ 900 ms at 4K |
| DoD-07 | UI smoothness at 120 Hz, glass on | `frameOverrunMs` P95 ≤ 0 ms, P99 ≤ 4 ms in Library, Home, tabs, sheets |
| DoD-08 | Video smoothness | 4K60 HDR10 for 10 min with controls shown and hidden: dropped frames ≤ 0.1%, no increase from controls |
| DoD-09 | Contrast fixture | ≥ 4.5:1 on all glass surfaces and player controls |
| DoD-10 | Scan | 2,000 files ≤ 60 s first run, ≤ 3 s unchanged |
| DoD-11 | Parser | 100% golden, ≥ 98% on the owner's 200 labelled names |
| DoD-12 | Metadata | No manual step on 50 real titles. ≥ 95% right among auto-applied. Manual choices never overwritten. |
| DoD-13 | Rename, move, USB removal | Progress kept. "Unavailable" shown. Reconnect ≤ 10 s. |
| DoD-14 | Offline | Airplane mode: library, covers, playback and subtitles all work |
| DoD-15 | Backup | Dump, wipe, restore: same state except timestamps. No keys in the backup file. |
| DoD-16 | Migration | `MigrationTestHelper` passes every version. App update keeps the library. |
| DoD-17 | Kill recovery | After `adb shell am force-stop`: position within 6 s, same tracks |
| DoD-18 | Soak | 2 hours mixed playback with heavy ASS: no crash, no `FATAL`, no `MemoryLimiter:AnonSwap` |
| DoD-19 | Battery | 1 hour of 1080p HEVC at 50% brightness: drain ≤ 1.15 × the bare Phase 0 harness on the same file |
| DoD-20 | Privacy | Request tests prove only allowed fields leave. Allow-list test passes. No analytics dependency in `gradlew dependencies`. |
| DoD-21 | Secrets | APK and git history contain no key, keystore or password. `git remote -v` prints nothing. |
| DoD-22 | Arabic | `MissingTranslation` = 0. Zero clipped strings at font scale 1.0 and 1.3. Owner approves RTL. |
| DoD-23 | Arabic search | Finds titles across أ/إ/آ/ا, ة/ه, ى/ي, ؤ/و, ئ/ي, with diacritics ignored |
| DoD-24 | TMDB rules | Refresh at day 150, delete at day 175 (fake clock). About matches the terms. |
| DoD-25 | Samsung sleep | After the app is unused for 7 days or force-stopped, opening it catches up new files within 5 s of the first screen. Nothing else depends on background runs. |
| DoD-26 | Degraded mode | Battery saver and Remove Animations: readable, no refraction or tilt, playback unaffected |
| DoD-27 | Status | `docs/STATUS.md`: every feature `VERIFIED ON PHONE`, or `BLOCKED` with a reason, or waived in `docs/WAIVERS.md` |
| DoD-28 | Owner acceptance | The owner uses it for one real evening of an anime and a film with Arabic subtitles and approves |
| DoD-29 | Glass feel | `[OWNER]` The glass visibly reacts to touch and scrolling and the app holds up next to the reference images |

### Required final report

Write `docs/FINAL-REPORT.md` and give the owner the same text in plain Arabic:

1. What was built, by phase, with the installed version and signature fingerprint.
2. The DoD table with measured numbers, not just pass marks, and an evidence path per row.
3. Every matrix row that failed or was not tested, and why.
4. Every `[UNVERIFIED]` item from this brief and what the phone showed.
5. What differs from this brief and why, including version changes.
6. Waivers.
7. Licences of everything in the APK.
8. What the owner must do from now on, such as key renewal, the keystore backup, Auto Blocker, and the 2027 Android developer-verification change. adb stays exempt from it, and an advanced flow and a free limited-distribution route exist.
9. Open bugs, in order of how much they affect watching.
