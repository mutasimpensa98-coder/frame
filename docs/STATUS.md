# Status

Updated 2026-11-12, after branch `android13` lowered `minSdk` from 36 to 33 so the app installs on Android 13 and newer (waiver of 2026-11-12). Before that, updated 2026-11-12, after the watchlist, cleanup suggestions, stats and automatic backup were built on branch `pack-library`. Before that, updated 2026-11-08, after launcher shortcuts, the Continue watching widget, the next episode date, the trailer button and tonight's pick were built on branch `pack-home`. R9 applies.

## Read this first

- **Branch `android13` (2026-11-12): `minSdk` 33 in every module, so the app installs on Android 13 and newer.** `compileSdk` and `targetSdk` stay 37, `arm64-v8a` stays the only ABI, the application ID and key are unchanged. It has not run on any phone: not on the owner's S26 Ultra, and not on any Android 13, 14 or 15 phone. No emulator was used. Every row below keeps the status it had; none of them says anything about Android 13 to 15. `NEXT.md` section 2, items 103 to 115, is the check for the other person's phone.
- **1.0.111 (2026-11-12): blurred pictures of unseen episodes. The owner checked it on the phone and said it works.**
- **1.0.108 is on the phone (2026-11-12), updated in place over 1.0.107, first install time kept, schema 6 opened with the library intact.** Seen opening with no crash: Library menu, My stats, tonight's pick, Media Manager Suggestions (one extra version listed with its kept copy named, nothing selected), the subtitle sheet and the Second subtitle sheet. Not tried: dual rendering, A-B loop, sleep end of episode, the credits card, watchlist, automatic backup.
- **Branch `pack-player` (2026-11-12) adds dual subtitles, an A-B loop, «آخر الحلقة» for the sleep timer and the next-episode card at the credits.** Built and unit tested, never installed or seen on a phone. See "This round: dual subtitles, A-B loop, sleep at the end of the episode, next at the credits".
- **1.0.107 is on the phone (2026-11-12), updated in place, first install time kept.** It fixes a crash: Android's ICU regex rejected the bare brace in `SubtitleCues.ASS_TAGS`, so 1.0.105 closed when subtitles loaded. Seen on the phone: the subtitle sheet opens, a Gestdown subtitle loads, the app stays up. `RegexBracesTest` now scans every pattern literal. Gestdown had no Arabic for a current series episode (English only), so Arabic needs a SubDL key.
- **The phone runs 1.0.104,** which holds the owner-requests package and the TMDB and IMDb switches. The owner tried it and asked for three things. No phone check of 1.0.104 is recorded here, and its schema steps 2 to 3 and 3 to 4 ran on Android only in that install, unobserved.
- **The answer to those three requests is committed** (`319d718`, version 1.0.105 in `65b53b6`), and nobody has seen it on a phone. The section "Earlier round: the owner's three requests" lists each item.
- **This round is built and unit tested on branch `pack-home`, and nobody has seen it on a phone:** launcher shortcuts, a Continue watching widget, the next episode date and a trailer on Details, and "What should I watch tonight?". It brings schema 5. The section "This round" lists each item.
- **The merged branch `pack-subs` adds the automatic Arabic subtitle download and a sites line that opens the keys page.** Built and unit tested, never installed or seen on a phone. See "This round: automatic subtitles and the keys line".
- **The library package is built and unit tested on branch `pack-library`, and nobody has seen it on a phone:** the watchlist «حابب نشوفه», cleanup suggestions in Media Manager, «إحصائياتي», and automatic backup. It brings schema 6. See "This round: watchlist, cleanup, stats and automatic backup".
- **The improvement package has run on the phone,** and the update kept the library, progress, settings and keys. `NEXT.md` section 1 lists what was seen and what was not. Screenshots of those checks stay in `D:\FrameGlass\shots`, because some show the owner's real videos. The tables below still describe filed evidence only.
- **The last filed phone evidence is the first app build,** 18:19 to 18:22. Later phone checks are recorded in `NEXT.md` section 1 and in `phase-0-results.md`.
- **Delete has not run on the phone, and the private folder ran only on the test copy.** Test delete with a clip the owner does not need.
- `VERIFIED ON PHONE` is used only when the phone showed the whole row. A row the phone showed in part stays `BUILT` or `UNIT TESTED`, and the Phone column says what was seen.
- `UNIT TESTED` means JVM tests cover the row's main logic. It proves nothing on the phone.
- `BUILT` means the code is in the APK and no test covers the row's main path.
- `NOT STARTED` is also used when a lower layer exists but the owner has no way to reach the feature. The note says so.

## What the phone has shown

First build only. Screenshots are in `evidence/phase-1/first-build/`, listed in `evidence/phase-1/README.md`.

| Flow | Evidence | Limit |
|---|---|---|
| Onboarding | `01.png` to `06.png` | The step dots and the empty Home changed after this run. |
| Add one folder and scan it | `07.png`, `09.png` | Only added folders existed then. Whole-phone search was not built. |
| Home | `08.png` | Hero only. No row has been seen on the phone. |
| Library | `09.png` | Grid of local frames. No rating badge and no progress line. |
| Details | `10.png` | Title and frame only. No key, so no plot, cast or genres. |
| Starting playback | `11.png`, `12.png` | One short clip. |

Phase 0 matrix, on the round 2 `qa` build (`evidence/phase-0/matrix.csv`): 50 of 53 Core rows passed. The results and the three open rows are in `phase-0-results.md`. Earlier runs are in `D:\FrameGlass\logs\`: `matrix-run1.csv` for run 1 and `matrix-run2.log` for run 2, 29 of 53.

## Build and tests

Branch `android13`, on this PC on 2026-11-12: `testDebugUnitTest`, `:parser:test` and `assembleRelease` pass, and `lintRelease` passes on `:app`, `:player`, `:data`, `:glass`, `:engine`, `:net` and `:qa`. Logs: `D:\FrameGlass\logs\build-android13.log`, `build-android13-tests.log` (every test task rerun with `--rerun`) and `check-update-android13.log`. Unit tests: 1,318 run, 1,317 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): parser 161, data 526, net 353, engine 75, player 99, app 71, glass 33. No test was added: the change is a build setting, two guarded reads in `:qa` and one manifest flag. Lint at `minSdk` 33 before the guards: 1 `NewApi` error (`Display.getHdrSdrRatio`, API 34, in `:qa`). After: `NewApi` 0, `InlinedApi` 0 in every module; the seven modules above have 0 errors and the same 23 warnings and hints as at `minSdk` 36 (app 9, player 9, qa 5); the one `ObsoleteSdkInt` (`mipmap-anydpi-v26`) was there before. `:third_party:ass-media` still stops on its 7 upstream errors (6 `UnsafeOptInUsageError`, 1 `ByteOrderMark`), unchanged and with no `NewApi`. Lint does not flag `Build.VERSION.SDK_INT_FULL` (API 36), so the compiled classes of every module were also checked against the SDK's `api-versions.xml`: the only references above API 33 are the two guarded ones in `MatrixActivity.census()`, and `VideoBlurHook`'s `setBlurRegions` (API 37) is compiled out while `ENABLED` is false. Every native library in the APK carries an NDK API note of 21 to 24 and needs only base `LIBC` symbols. No SQL needs a SQLite newer than Android 13's. `tools/check_update.py`: pass, 5 steps, 26 tables, 21 saved rows kept. The merged manifest: `minSdkVersion` 33, the same 9 permissions, `enableOnBackInvokedCallback` true. Release APK 23,472,941 bytes. Nothing was installed, and no emulator or phone was used.

This round (`pack-library`), on this PC on 2026-11-12: `testDebugUnitTest`, `:parser:test`, `assembleRelease`, `:app:lintRelease` and `:data:lintRelease` pass. Logs: `D:\FrameGlass\logs\build-pack-library.log`, `build-pack-library-parser.log` and `build-pack-library-lint.log`. Unit tests: 1,260 run, 1,259 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): parser 161, data 519, net 353, engine 69, player 54, app 71, glass 33. This round added 29 tests in `:data` (`LibraryPackTest`, two in `BackupFormatTest`, one in `MigrationSchemaTest`) and one in `:app` (`HeroArtTest`). Lint: `:data` 0 issues, `:app` 0 errors and 9 warnings, none in a file of this round. `tools/check_update.py`: pass, 5 steps (1 to 2 up to 5 to 6), 26 tables, 21 saved rows kept. Release APK 23,391,564 bytes. No manifest changed. `fg.ps1 build`, `assembleQa` and lint of the other modules were not run. Nothing was installed.

Branch `pack-player`, on this PC on 2026-11-12, with the build cache: `testDebugUnitTest` and `assembleRelease` pass. Log: `D:\FrameGlass\logs\build-pack-player.log`. Unit tests: 1,109 run, 1,108 passed, 0 failed, 1 skipped: data 490, net 353 (1 skipped), engine 69, player 94 (40 new: `SecondSubtitleTest` 13, `AbLoopTest` 10, `EpisodeEndTest` 17), app 70, glass 33. `:parser:test`, lint, `assembleQa`, `fg.ps1 build` and `tools/check_update.py` were not run; no schema changed. Release APK 23,352,664 bytes. Nothing was installed.

The `pack-home` round, on this PC on 2026-11-08, with the build cache: `testDebugUnitTest`, `:parser:test`, `assembleRelease` and `:app:lintRelease` pass. Logs: `D:\FrameGlass\logs\build-quick-features.log` and `build-quick-features-lint.log`. Unit tests: 1,195 run, 1,194 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): parser 161, data 476, net 350, engine 67, player 41, app 67, glass 33. `:app` lint: 0 errors and the same 8 warnings as before. `tools/check_update.py`: pass, 4 steps (1 to 2 up to 4 to 5), 25 tables, 21 saved rows kept. The merged release manifest has the same permissions as before; the widget receiver needs none. Release APK 23,286,928 bytes. `fg.ps1 build`, `assembleQa` and lint of the other modules were not run in this round. Nothing was installed.

The table below is from the earlier round.

Run on this PC on 2026-11-08, after the three review faults of this round were fixed, with `--rerun-tasks --no-build-cache`: 799 tasks, all executed, none from a cache. Log: `D:\FrameGlass\logs\build-integrate3-full.log`. `fg.ps1 build` and the APK rows below it were not run again in this round.

| Check | Result |
|---|---|
| `assembleQa`, `assembleRelease` | Pass, R8 included. |
| Unit tests | 1,169 run, 1,168 passed, 0 failed, 1 skipped: parser 161, data 467, net 347 (1 skipped), engine 67, player 41, app 53, glass 33. |
| Skipped test | `AnimeIndexTest.theRealFileParses` needs a local copy of the mappings file. |
| No tests at all | `:qa`, `:third_party:ass-media`. |
| Android lint | `lintRelease` on `:app`, `:player`, `:data`, `:glass`, `:engine`, `:net`, `:qa`: 0 errors, 17 warnings (app 8, qa 5, player 4), the same 17 as before, `MissingTranslation` 0. `:third_party:ass-media` is not in this set: its own lint stops on 7 `UnsafeOptInUsageError` errors in the upstream code, which this round did not touch. |
| `tools/check_update.py` | Pass: 3 steps (1 to 2, 2 to 3, 3 to 4), 25 tables, 21 saved rows kept. This is desktop SQLite, not Android. Log: `check-update-integrate3.log`. |
| `.\fg.ps1 build` | Last run in the switches round. Pass: signature, 16 KB alignment, size, `lib/` listing. Local only: nothing was installed or published. |
| Release APK, switches round | 23,179,780 bytes (22.1 MiB), budget 60 MB, only `lib/arm64-v8a`. Not installed and not saved as a release. |
| `qa` APK | 23,212,584 bytes (22.1 MiB), same certificate. |
| Certificate SHA-256 | `6517fbf832436c7cb106855c03d8b8b1d94816517b2f4328279fb9a051354d67` |
| Permissions in the release APK | The list in section 4.10 plus `USE_BIOMETRIC`. `USE_FINGERPRINT` is removed. No manifest changed in this round, and the list was not read again. |
| Not run | `MigrationTestHelper`. Schema 1 to 2 passed on the phone through the in-place update. Schemas 2 to 3 and 3 to 4 have not run on Android. |

R8 and `androidx.biometric`: no failure and no new keep rule. `BiometricFragment`, `FingerprintDialogFragment` and `BiometricViewModel` keep their no-argument constructors through the fragment and lifecycle consumer rules, confirmed in `seeds.txt`. `BiometricPrompt` itself has not been shown.

## Data-loss read-through

Every place that deletes or renames a file was read, in `:data`, `:app`, `:player`, `:engine` and `:net`. Only `PrivateVaultImpl` and `MediaManagerImpl` touch the owner's files. Seven faults were found and fixed. Each change was compiled and its pure part has a JVM test, but no fix has run on Android.

| Severity | Fault | Fix |
|---|---|---|
| Video lost | `moveOneIn` deleted the private copies on any exception, even after the original had been removed, for example if the final row update failed. Both copies were then gone. | `PlacedCopies`: once the row commits it holds the copies, and only a finished rollback gives them back. |
| Video lost | The restart path removed the original on the row's word alone. It never checked that the private copy still existed, and the fault above could have deleted it. | `finishMoveIn` hashes both sides first. A damaged copy puts the file back in the library (`VaultRecovery`, `undoMoveIn`). |
| Wrong file removed | A file replaced at the same path while the app was down, or while a move waited for a scan to end, was removed as if it were the original. | Size and modified time are checked just before removal, and the restart path compares hashes. A different file stays. `copyVerified` also refuses a source rewritten at the same size. |
| Subtitle lost | Moving out skipped a subtitle if that name was taken, then deleted the private copy, even if the file there was different. | It keeps the owner's file and restores this one under a numbered name. It skips only if the bytes match or the subtitle was shared. |
| File replaced | `File.renameTo` replaces its target. The free-name check came just before it, so another app could slip in between. | `VaultFiles.moveNoReplace`. |
| Outside the plan | MediaStore rows were looked up by lower-case path. On a case-sensitive drive, two files differing only by case shared a key, so the wrong one could reach the trash. | `TrashIndex.byPath` gives neither a row, so both read as having no trash, and the plan says permanent. |
| Lost on power cut | A rename was not synced, so a power cut could lose it after the row said it was done. | `VaultFiles.syncFolder`, best effort, before each commit. Untested on Android's filesystems. |

Read and found sound:

- `delete()` acts only on entries that `planDelete` wrote. Each one is rechecked for path, size and plain file before it goes.
- A file promised to the trash is never deleted for good. If the trash fails, it stays and is reported.
- A subtitle goes only with its video.
- A second sidecar plan replaces the first, and Delete stays disabled until the new plan is ready.
- Backup restore does not touch private rows, and no sweep touches `files/vault`.

Limits that remain are in `NEXT.md` section 4.

`Api.kt` additions are listed in the package contract, `ApiPackage.kt`. `ApiExtras.kt` gained `privatePlayRef`, `VaultClosedException`, `SubtitleFailure`, `trashConsents` and `answerTrashConsent`, all beside the fixed contract.

## Whole-phone flow, read through the code

This was read in the code from a fresh install. It was not run. No test runs `Scanner`, `MediaIndex`, `CatchUp`, `Enricher` or `SnapshotSource`. Room checks the SQL at compile time only.

1. `FrameApp` registers `CatchUpOnOpen`, so the first `onActivityStarted` calls `startCatchUp`, then `CatchUp.onOpen`, then `scanLane.kick()`. With no All Files access `Scanner.scan` returns at once and nothing is marked missing.
2. Onboarding opens Android's All Files page. That stops `MainActivity`, so coming back is a new start and `CatchUpOnOpen` fires again, now with access. `rememberAllFilesAccess` rereads the state on resume and moves the step on.
3. `scanAll` runs the clip pass, then saves mounted volumes. `ScanRoots.plan` gives one automatic root per volume, and `MediaIndex` names the folders MediaStore knows. `RootRun` reads those first, then walks the rest with a checkpoint.
4. `ScanProgress` reaches Home through `scanState` and `UiModel.scan`. An empty Home shows the count and a thin line. A full Home shows a chip after 4 s.
5. Each new file commits in its own transaction. Room invalidates, `SnapshotSource` reloads at most every 400 ms, `HomeBuilder` and `TitleLists` rebuild, and `UiModel` hands the cards to the screens. `Enricher` then fills duration and frame on the same rows.
6. Upgrading over the first build now runs `MIGRATION_1_2`, with the same package and key. The clip pass moves old rows once.

Three gaps were found and fixed in the whole-phone search round, with tests:

- With no key, no row of Home could qualify, so films and series had no card on Home at all. `08.png` shows that empty space. A fallback row, Recently added, now appears when no other title row qualifies. This goes beyond the list in F-U5, so the owner should confirm it.
- When every file was gone or removed, Home stayed blank with no message. It now shows the empty state.
- A clip in the hero was labelled Movie. It now has no kind label.

## Feature rows

### Library

| ID | Feature | Status | Phone | Notes |
|---|---|---|---|---|
| F-L1 | Stable identity | UNIT TESTED | Not seen | `FileIdentityTest`, `KeysTest`. The `adb shell mv` test has not been run. |
| F-L2 | Folders | BUILT | Part: one folder added (`07`, `09`) | Now only for extra places, per the waiver. Remove not seen. |
| F-L3 | Unavailable | UNIT TESTED | Not seen | `TitleListsTest`, `FolderBuilderTest`. USB unplug not run. |
| F-L4 | Automatic scan | BUILT | Part: one added folder (`08`, `09`) | Whole-phone search came after that run. Path rules are unit tested. `Scanner`, `MediaIndex` and `CatchUp` have never run. |
| F-L5 | Backup and restore | UNIT TESTED | Not seen | `BackupFormatTest` covers the format, favourites, excluded folders and the service switches. A backup from before the switches keeps the phone's switches. Private items stay out by id. The watchlist is in it (`pack-library`), the watch log is not. Automatic backup to a picked folder is built, see "This round: watchlist, cleanup, stats and automatic backup". Dump, wipe and restore not run. |
| F-L6 | Migrations | UNIT TESTED | Part: 1 to 2 kept the library through the 1.0.103 update | Schema 3 adds the nullable `Title.metaLanguage`. Schema 4 adds the nullable `Episode.imdbRating` and `Title.omdbTriedAt`, two `ADD COLUMN` statements. `MigrationSchemaTest` and `tools/check_update.py` replay 1 to 2 to 3 to 4 on desktop SQLite. `MigrationTestHelper` not run. 2 to 3 and 3 to 4 have not run on Android. |
| F-L7 | Instant add | BUILT | Part: cards with local frames (`09`) | Matching after the add not seen. |
| F-L8 | Parser | UNIT TESTED | Not seen | All golden rows pass. The owner's 200 labelled names are not done, and `tools/name_report.py` does not exist. |
| F-L9 | Versions | UNIT TESTED | Not seen | `VersionsTest`, `EpisodeRowsTest`. The picker itself has no test. |
| F-L10 | File facts | UNIT TESTED | Not seen | `FolderBuilderTest` for NEW, `FileFactsTest` for the facts. Chips and the facts sheet not seen. |
| F-L11 | Series grouping | UNIT TESTED | Not seen | `KeysTest`, `ParserIntegrationTest`. No real series checked. |
| F-L12 | Change cover | BUILT | Not seen | No test. |

### Metadata

No real TMDB, AniList, AniSkip or OMDb call has been made from the app. The tests use a fake server.

| ID | Feature | Status | Phone | Notes |
|---|---|---|---|---|
| F-M1 | Search | UNIT TESTED | Not seen | `TmdbMatchTest`. |
| F-M2 | Confidence | UNIT TESTED | Not seen | `ScoringTest`. Thresholds not tuned, that is Gate 3. |
| F-M3 | Fix match | BUILT | Not seen | Menu and sheet exist. While TMDB is off it offers OMDb's choices, and the empty message names the service that answered, or Online features when that is off. Only the message choice has a test, `ServicesTest`. |
| F-M4 | Manual wins | BUILT | Not seen | Guards in `TitleOps` and `MetadataPipeline`. No test. |
| F-M5 | Saved offline | UNIT TESTED | Not seen | `TmdbDetailsTest` for fields and sizes. Saving to disk has no test. |
| F-M6 | Offline queue | BUILT | Not seen | `RetryPolicyTest` covers the delays only. |
| F-M7 | Frame fallback | UNIT TESTED | Part: frames on cards (`09`) | `FramePickerTest`. |
| F-M8 | Key handling | UNIT TESTED | Part: key step (`02`) | `BlobCipherTest`, `TmdbMatchTest`. AndroidKeyStore path not run. |
| F-M9 | Anime flow | UNIT TESTED | Not seen | `AnimeFlowTest`, `AnimeMappingsTest`. Uses `mappings.min.json`, the brief's last option, because no zstd library is pinned. |
| F-M10 | Ratings | UNIT TESTED | Not seen | `TitleListsTest`, `TmdbDetailsTest`, and `ViewsTest` for the switches: a service that is off gives no badge, so with TMDB off cards show IMDb. OMDb posters are allowed by the 2026-11-08 waiver. The owner asked for the badge, and it has never been seen. |
| F-M11 | TMDB cache rule | UNIT TESTED | Not seen | `CachePolicyTest`. `CacheSweeper` has no test, and the Gate 3 fake clock test is not written. |
| F-M12 | About | BUILT | Not seen | TMDB logo stored unmodified, per the app report. Not seen. |
| F-M13 | No AI with TMDB data | BUILT | Not seen | The app has no AI feature. |
| F-M14 | Title logos | UNIT TESTED | Not seen | `TmdbDetailsTest`. |
| F-M15 | Cover colour | BUILT | Not seen | Uses `BitmapFactory` and Palette, not a Coil decode as the brief says. Same result intended. No test. |

### Screens

| ID | Feature | Status | Phone | Notes |
|---|---|---|---|---|
| F-U1 | Tabs | BUILT | Part: bar on Home and Library (`08`, `09`) | Search and Settings tabs not in the evidence. |
| F-U2 | Library filter | UNIT TESTED | Part: chips (`09`) | `LibraryFilterTest`, `FavoritesFilterTest`. Videos and Favourites chips added after that run. Filtering not seen. |
| F-U3 | Library sort | UNIT TESTED | Part: closed dropdown and grid (`09`) | `LibraryOrderTest`, `TitleListsTest`. |
| F-U4 | Cards | BUILT | Part: 2:3 frames with titles (`09`) | Rating badge and progress line never seen. The owner asked for both. Videos now use 16:9 cards. |
| F-U5 | Home | UNIT TESTED | Part: hero, meta line, pill, dots (`08`) | `HomeBuilderTest`, `HomeClipsTest`. Rows, auto-advance and Resume not seen. Recently added goes beyond the row list here. |
| F-U6 | Continue watching card | UNIT TESTED | Not seen | Data only, in `HomeBuilderTest`. |
| F-U7 | Details | BUILT | Part: art, title, pill, ⋮ (`10`) | Everything that needs a key not seen. |
| F-U8 | Actor | BUILT | Not seen | No test. |
| F-U9 | Episodes | UNIT TESTED | Not seen | `EpisodeRowsTest`. |
| F-U10 | Search | UNIT TESTED | Not seen | `SearchTextTest`, `ParserIntegrationTest`. The FTS query and the 100 ms target not run. |
| F-U11 | + button | BUILT | Part: on Home and Library, not on Details (`08`, `09`, `10`) | |
| F-U12 | One look, now themes | UNIT TESTED | Not seen | Replaced by the waiver. Dark, light, follow phone, six accents. `ThemeContrastTest` computes contrast and measures no pixels. No light theme, blur, status bar or shader has been seen. |
| F-U13 | Copy | BUILT | Not seen | Plurals and time left are in resources. |
| F-U14 | Settings | BUILT | Not seen | Rebuilt as pages: appearance, language, library, playback, online, backup, private folder, Media Manager, about. |
| F-U15 | Onboarding | VERIFIED ON PHONE | Whole flow, first build (`01` to `06`) | Recheck on the next install: the step dots changed, and the empty Home now searches the phone. |

### Arabic

No screen has been seen in Arabic on the phone.

| ID | Feature | Status | Phone | Notes |
|---|---|---|---|---|
| F-A1 | Resources | BUILT | Not seen | Lint `MissingTranslation` is 0. Every string has Arabic, except `app_name` and `app_tmdb_notice`, which are marked not translatable. |
| F-A2 | Language | BUILT | Not seen | Language page in Settings through `LocaleManager`. `AppSettings` has no language field. |
| F-A3 | Mirroring | BUILT | Not seen | No RTL check run. |
| F-A4 | Fit | BUILT | Not seen | The font scale walk is not written. |
| F-A5 | Isolation | BUILT | Not seen | `bidiIsolate` is used on screens that show Latin fragments. |
| F-A6 | Numbers | BUILT | Not seen | Parser tests read Arabic-Indic digits. Locale formatting has no test. |
| F-A7 | Subtitles | UNIT TESTED | Not seen | `TrackPickerTest`. S rows of the matrix passed, apart from S4. The owner's eyes are still needed. |

### Player

| ID | Feature | Status | Phone | Notes |
|---|---|---|---|---|
| F-P1 | Engine | BUILT | Part: playback started (`11`, `12`), matrix 50 of 53 Core | See the matrix note above. |
| F-P2 | Last resort | BUILT | Not seen | No test. |
| F-P3 | Immersive | BUILT | Part: landscape full screen (`11`) | Rotate toggle not seen. |
| F-P4 | Controls | BUILT | Part: layout (`12`) | Auto-hide and long-press labels not seen. |
| F-P5 | Gestures | BUILT | Not seen | Gate 2 owner check. |
| F-P6 | Aspect and zoom | UNIT TESTED | Not seen | `VideoGeometryTest`. Subtitle narrowing was fixed after the last phone run. |
| F-P7 | Subtitle formats | BUILT | Part: matrix S2+0, S3, S5, S6, S7, S8 passed | Those rows prove no crash and timing, not the look. PGS and VobSub have no file. |
| F-P8 | Encoding | BUILT | Part: matrix S1 passed, and run 1 delivered 9 cues in each encoding (`logs/matrix-run1.csv`) | No unit test of the scorer. Owner check of the Arabic text not done. |
| F-P9 | Subtitle style | BUILT | Not seen | No test. |
| F-P10 | Audio delay | BUILT | Part: delay proof on the phone | Step 0.6: audio landed 4.6 to 16.7 ms from the setting, see `phase-0-results.md`. Not yet heard by the owner. |
| F-P11 | Tracks | UNIT TESTED | Part: matrix S8 passed | `TrackPickerTest`. |
| F-P12 | Speed | BUILT | Not seen | |
| F-P13 | PiP and lock | BUILT | Not seen | Gate 2 owner check. |
| F-P14 | Audio focus | BUILT | Not seen | Gate 2 owner check. |
| F-P15 | Restore | BUILT | Not seen | `WatchRulesTest` covers the resume rule. Force-stop test not run. |
| F-P16 | Session and background audio | BUILT | Not seen | Audio hardening test not run. |
| F-P17 | Frame rate | BUILT | Not seen | No code: Media3's default is kept. The census shows no 24 or 48 Hz mode. Result for 23.976 not recorded. |
| F-P18 | Fallback | BUILT | Not seen | The forced decoder failure of Gate 2 is not written. |
| F-P19 | Open with | BUILT | Not seen | |
| F-P20 | HDR honesty | BUILT | Not seen | No test. |

Glass over video is off: `VideoBlurHook.ENABLED` is false until step 0.7 is measured, so the controls use the scrim fallback of section 7.5.

### Smart watching

| ID | Feature | Status | Phone | Notes |
|---|---|---|---|---|
| F-S1 | Next up | UNIT TESTED | Not seen | `HomeBuilderTest`. |
| F-S2 | Countdown | UNIT TESTED | Not seen | `NextUpTest` picks the next item. `EpisodeEndTest` (branch `pack-player`): the card comes when the credits begin (AniSkip `ed`, TheIntroDB credits, or the owner's credits mark), «مشاهدة الشارة» sends it away until the true end, and with no credits data a light «الحلقة الجاية» button shows in the last minute with no countdown. The card itself and the countdown have no UI test. |
| F-S3 | Watched | UNIT TESTED | Not seen | `WatchRulesTest`. `EpisodeEndTest`: the start of the credits also marks the item finished, before 90 % when the credits start earlier. A watched title also leaves the watchlist (`WatchlistTest`). |
| F-S4 | Skip intro | UNIT TESTED | Not seen | `SkipSegmentsTest`, `SegmentRulesTest` for manual, AniSkip and TheIntroDB. The local path of Phase 7b is not started. |
| F-S5 | Missing episodes | UNIT TESTED | Not seen | `EpisodeRowsTest`. |
| F-S6 | Series memory | UNIT TESTED | Not seen | `TrackPickerTest`. |
| F-S7 | Sidecars | UNIT TESTED | Not seen | `SidecarMatcherTest`. |
| F-S8 | Night audio | UNIT TESTED | Not seen | `NightCompressorTest`. Owner listening check not done. |
| F-S9 | Online subtitle search | UNIT TESTED | Not seen | `OnlineSubtitleQueryTest`, `OnlineSubtitleSearchTest`, `GestdownApiTest`, `SubtitleSiteListTest`, `SitesNoteTest`, `AutoSubtitleTest`, `AutoSubtitleRuleTest`, `SubtitleClientTest`. The row always shows. Series search with no key through Gestdown (`WAIVERS.md`, 2026-11-08). Automatic download once per file when no Arabic exists, never through OpenSubtitles and never with the release name or hash (`WAIVERS.md`, 2026-11-08). The sites line opens Settings › Online features and keys. Gestdown replies are fixtures; no download, automatic or tapped, has run live. |
| F-S10 | Resume logic | UNIT TESTED | Not seen | `ResumeLogicTest`. |

### Professional tools

| ID | Feature | Status | Phone | Notes |
|---|---|---|---|---|
| F-T1 | Thumbnail seek | BUILT | Not seen | Stutter at 4K not measured. |
| F-T2 | Chapters | NOT STARTED | Not seen | No chapter code exists. Phase 7. |
| F-T3 | Frame step | NOT STARTED | Not seen | No frame step code exists. Phase 7. |
| F-T4 | Screenshot | BUILT | Not seen | |
| F-T5 | Info panel | BUILT | Not seen | |
| F-T6 | Sleep timer | UNIT TESTED | Not seen | `EpisodeEndTest` (branch `pack-player`): «آخر الحلقة» shows no next card, stays paused at the end on screen, ends playback and background audio behind the screen, and is cancelled when the owner picks another item. The minute timers are untested. |
| F-T7 | Anime4K | NOT BUILT (allowed) | Not seen | Needs mpv, Phase 8. |

## This round: dual subtitles, A-B loop, sleep at the end of the episode, next at the credits

Four owner requests of 2026-11-12, built on branch `pack-player` (waiver of 2026-11-12 for the first two). Not installed, and not seen on a phone, in either theme or in Arabic. No permission, dependency or network host was added, and no Room entity, migration or repository of `:data` changed.

| Request | Status | Notes |
|---|---|---|
| Dual subtitles | UNIT TESTED | `SecondSubtitleTest`: which tracks are offered (the main one, bitmap and unplayable tracks left out; files, embedded and downloaded alike), the series memory by language, the clock (main delay or its own) and the frame rate stretch. Subtitle sheet › «ترجمة ثانية»: Off or any other text track, «في الأعلى» or «فوق الترجمة الأولى», «نفس تأخير الترجمة الأولى» or its own delay, and «مزامنة مع الكلام» for a file. A file is read with Media3's parsers (`TextTracks.readFile`), an embedded track by a second pass of Media3's extractor over the file with text transcoding on (`TextTracks.readEmbedded`); neither path has a test, because both need Android. Drawn on a second `SubtitleView` at 80 % of the main size. The placement and the memory sit in the player's preferences, outside the backup. |
| A-B loop | UNIT TESTED | `AbLoopTest`: B needs A and 1 s after it, a new A past B drops B, a seek past B lands at A, and the check interval near B at any speed. More › «تكرار مقطع (A-B)». A chip under the top bar shows both times and an × while it runs. A B at the very end loops instead of ending. A new item clears it. While it runs no next card or light button shows. |
| Sleep timer «آخر الحلقة» | UNIT TESTED | See F-T6. |
| Next episode at the credits | UNIT TESTED | See F-S2 and F-S3. The owner's credits mark is set in More › «تحديد المقدمة», stored as time before the end for the whole series, and used only where no timed credits exist. |

Two faults from the review of this round were fixed:

| Fault | Fix |
|---|---|
| Any seek into the credits marked the item finished and stopped every position save for the rest of the play. | Only playback running across the credits start marks it, checked on the ticker from one tick to the next; a seek clears the last tick, and a jump longer than playback could make counts as a seek (`EpisodeEnd.reachedByPlayback`). The mark no longer stops saves: those inside the credits are skipped, and one more than 30 s before them saves as usual (`savesAfterCreditsMark`). A scene after "Watch credits" reached by a seek marks nothing. Tests in `EpisodeEndTest`. |
| An embedded second subtitle read the whole file at full speed on every open. | A complete read is kept in `cacheDir/second-subtitles`, one file per video version (path, size, change time) and track, named by a hash, least recently used first out past 50 MB (`CueCache`, `CueCacheTest`). Private-folder videos are never cached. The read is capped at 40 MB/s (`ReadPace`) and waits while the player buffers or has less than 5 s read ahead (`SecondRead.mustWait`). The pace and the wait are not measured on the phone. |## Earlier round: the owner-requests package

Built after 1.0.103 and committed as `37da6d3`. Shipped in 1.0.104. No phone check of it is recorded, in either theme or in Arabic.

| Request | Status | Notes |
|---|---|---|
| IMDb ratings as soon as the OMDb key is added | BUILT | Saving the key starts the fetch. A network failure waits for a swipe. The yellow badge shows only on Details; cards keep TMDB. |
| Refetch in the app language | UNIT TESTED | `MetaLanguageTest`. `Title.metaLanguage` is NULL on every existing row, so the first open after this update fetches every matched title once more: details, 3 images, all seasons and one OMDb call each. Whether TMDB answers plain `ar` in Arabic is unverified. `AppLanguage` reads `LocaleManager` first. |
| Home hero rules | UNIT TESTED | `HeroArtTest`, `ClipViewsTest`. Art is decoded before Home sees it. A hero with a broken backdrop falls back to its cover, and one with neither leaves the pager. `Snapshot.broken` also keeps a title out of the hero when only its frame grab failed. |
| Anime filter | UNIT TESTED | `AnimeNamesTest`, `PlainNumberTest`. A one-time pass moves false-anime rows to Series. Short numbered phone clips go to Videos and skip lookup unless 3 files share the name. Clips already matched as anime stay until Fix match. |
| Details top bar | UNIT TESTED | Tests in `app/src/test/.../details/`. A glass bar fades in once the art leaves it, with the title. |
| Cinema mode | UNIT TESTED | `ClipViewsTest`. Settings › Library. With no real titles Home says so and offers to turn it off. |
| Automatic subtitle sync | UNIT TESTED | `SyncAlignerTest`, `SubtitleCuesTest`, `SyncPlanTest`, `SpeechDetectorTest`, `SyncPipelineTest`, `SyncTextTest`. Synthetic films only: 1,600 wrong-subtitle runs gave no false fix. Decode speed, the speech detector on real film audio, the thresholds and playback impact are unmeasured. Manual delay now reaches ±60 s. |
| Back closes the long-press menu | BUILT | Sheets and menus register Back only while open. |
| About mark in the light theme | BUILT | Tinted. |
| TMDB and IMDb switches | UNIT TESTED | Built in the next round, below. |

Four faults from the review of this package were fixed, each with a JVM test of its pure part:

| Fault | Fix |
|---|---|
| Cues had no length limit. A fansub watermark line over the whole video swallowed every cue, and an SRT end typed as 99:59:59 sized the FFT near 8M points, about 330 MB. The `OutOfMemoryError` would have ended playback. | `SubtitleCues` drops any cue longer than 15 s. `SyncAligner.withinReach` cuts every cue at the reach of the audio. `SubtitleSync` and `PlayerSession.startSync` catch any `Throwable` except cancellation, so a failure only means no answer. |
| A hero whose art was on disk but did not decode was dropped by the app after `:data` had taken it out of Continue watching, so a title in progress left Home. | `HeroItem.continueItem` carries that card. `withDrawableHeroes` puts it back at the head of the row, and a row that appears this way replaces the last title row, so Home keeps 3 automatic rows. |
| A frame rate fix became the chosen subtitle, and a file added from the picker was listed only while it was the chosen one, so after a reopen the original was gone. | A reopen lists every subtitle copied in for the video (`CopiedSubtitles`), so the original and the retimed copy both stay selectable. Undo itself still ends at a reopen. |
| The anime pass left the old release tags, such as a round-bracket name at the start, so demoted rows never counted as plain numbers and went to TMDB on every swipe. | `ClipPass` reads each file again, and `TitleOps.settleAsSeries` writes its tags and anime number in the same transaction, before the keys are rebuilt. The Room path has no test; `StoredParse.reread` has. |

`Api.kt` gained `AppSettings.cinemaMode`, `HomeFeed.cinemaEmpty`, `HomeFeed.MAX_AUTOMATIC_ROWS` and `HeroItem.continueItem`, all with defaults beside the fixed contract.

## This round: shortcuts, widget, next episode, trailer and tonight's pick

The owner asked for a Continue watching widget and launcher shortcuts (waiver of 2026-11-08), the next episode date and a trailer on Details, and a random pick for tonight. Built and unit tested on branch `pack-home`. Not installed and not seen on a phone, in either theme or in Arabic.

| Request | Status | Notes |
|---|---|---|
| Launcher shortcuts | UNIT TESTED | `QuickResumeTest` (`:data`, which items), `ShortcutPlanTest` (`:app`, ids, ranks, limit). Dynamic shortcuts "Resume <title>" for the F-S10 resume item and up to two more titles in progress, newest first, each opening `PlayerActivity` through `PlayerRoute.intent` at the saved place. Icon: the stored poster cropped square, else the app icon. Clips and private-folder videos never appear. Updated 8 s after progress, files or titles stop changing, so nothing runs while a video plays. The `ShortcutManager` calls have no test. |
| Continue watching widget | UNIT TESTED | `WidgetSlotsTest` for the rows and progress, `QuickResumeTest` for the items. `ContinueWidget` with plain `RemoteViews`, no new dependency: up to three rows of cover, title, episode label and progress line, a tap resumes in the player, and an empty text opens the app. Translucent dark panel, 24 dp corners, thin white rim. Updated with the shortcuts and on the system period of 30 minutes. No permission added. Its look on One UI is unseen. |
| Next episode air date | UNIT TESTED | `TmdbDetailsTest` (parsing `next_episode_to_air` and `status`), `UpcomingTest` (ended, unknown and passed dates hidden; only the newest owned episode's Home card), `AirDatesTest` (tonight up to the day after the air date, tomorrow, weekday, localized date, year only when it differs). Details shows «الحلقة 6 من الموسم 2 تنزل الخميس 12 نوفمبر». The Home Continue card of the newest owned episode shows «حلقة جديدة يوم …» in place of the episode title. OMDb matches show nothing. |
| Trailer | UNIT TESTED | `TmdbDetailsTest` (`pickTrailer`: YouTube Trailer only, app language then English then any, official first, then newest), `TrailerTest` (only a plain YouTube key makes a link). Details has a «الإعلان» pill that opens `https://www.youtube.com/watch?v=<key>` with `ACTION_VIEW`. No host was added to `HttpCore`, and the pill hides while Online features is off. |
| What should I watch tonight? | UNIT TESTED | `TonightPoolTest` (`:data`: unstarted available films and series only, no clips; private-folder videos are not in the snapshot), `TonightPickTest` (`:app`: kind and genre filters, never the same title twice in a row, walks through the rest, genre chips). Behind the new ⋮ menu of the Library top bar, as R13 asks. A glass sheet with Any, Film and Series chips, genre chips, poster, title, year, rating badge, runtime, a four-line plot, and «شغّل», «تفاصيل», «غيره». The sheet has no UI test. |

Schema 5: `Title` gains `seriesStatus`, `nextAirDate`, `nextSeason`, `nextEpisode` and `trailerKey`, all nullable, through `STATEMENTS_4_5` and `MIGRATION_4_5`, registered in `FrameDb.open`. `MigrationSchemaTest` holds them against the exported `5.json`, and `tools/check_update.py` passes. The F-M11 purge clears them with the other TMDB fields. The backup format carries no TMDB text, so it is unchanged. A swipe to refresh refetches every TMDB title fetched more than 10 minutes before, which fills these fields for titles already in the library, and also every series whose announced date has passed. The TMDB details call now appends `videos` and sends `include_video_language`.

`Api.kt` gained `TitleDetail.upcoming`, `TitleDetail.trailerKey`, `ContinueItem.upcoming`, `UpcomingEpisode`, `ResumeEntry`, `PickOption`, and `LibraryRepository.quickResume()`, `resumeChanges` and `tonightPool`, all with defaults.

## Earlier round: the owner's three requests

The owner's words after trying 1.0.104, in short: the Settings icons are not coloured; make the refresh more interactive, not just a moving icon; there is no online subtitle search. They have no subtitle key. Committed in `319d718`, version 1.0.105 in `65b53b6`. Not seen on a phone, in either theme or in Arabic.

| Request | Status | Notes |
|---|---|---|
| Coloured Settings icons | UNIT TESTED | `IconTileTest`. Each row of the root Settings page has its own colour tile with a white glyph, from `glass/.../IconTiles.kt`. White reaches 4.5:1 on every tile. Sub-pages have no tiles. |
| A swipe to refresh that is more than a moving icon | UNIT TESTED | `RefreshFlowTest` and the `:data` swipe tests. On Home and Library a glass pill follows the finger, turns "Pull to refresh" into "Release to refresh" with a haptic tick, and the list stretches slightly. Then it shows the stages with counts (files checked, details done of total), a thin line, and the result: new videos, titles updated, titles left, or why details were skipped. Reduced motion drops the stretch and the moving line. The wait for details stays under 8 s, then the rest carries on. `UiModel.newVideos` and its before-and-after count are gone. |
| Subtitle search with no key | UNIT TESTED | `GestdownApiTest`, `SubtitleSiteListTest`, `SitesNoteTest`. Gestdown (`api.gestdown.info`), a public API in front of Addic7ed, needs no key and holds TV episodes only. Only the show name, season, episode and language go out. Clips return nothing with no request. The "Search online" row always shows: dimmed with a reason when Online features are off or the video is in the private folder. A film with only keyless sites gets its own message, and a quiet line under the results names the sites searched and those a free key adds. The Online page says series need no key. Podnapisi had no DNS records on 2026-11-08. |

Three faults from the review of this round were fixed:

| Fault | Fix |
|---|---|
| `MetadataPipeline.drainWithin` could keep the lock for good: if the caller was cancelled after `lock()` resumed but before `withTimeoutOrNull` returned, the exception came before the `try`, so every later drain waited forever and no details were fetched until the process restarted. | `Mutex.lockWithin` gives the lock back on that path. `LockWithinTest` cancels the caller at exactly that point; it failed with the old code and passes now. |
| For the 3.5 s the result stays up, a new pull gave no tick, and the pill kept the old result instead of following the finger, though letting go still refreshed. | A pull counts whenever no refresh runs and the box has come to rest after the last one. The pill then shows the pull row over the result, and the tick and the stretch work as when idle. No UI test. |
| Gestdown downloads sent `Accept: application/json` to an endpoint that returns plain SRT, so a server that negotiates could refuse every download. | Downloads send `Accept: */*`; search still asks for JSON. `GestdownApiTest` checks the header. No download has been tried live. |

The sites line now opens the keys page: see the next section.

## This round: automatic subtitles and the keys line

Two owner requests of 2026-11-08, built on branch `pack-subs`. Not installed, and not seen on a phone, in either theme or in Arabic.

| Request | Status | Notes |
|---|---|---|
| Automatic Arabic subtitle | UNIT TESTED | `AutoSubtitleRuleTest` for when the player asks, `AutoSubtitleTest` for the query (no release name, hash or size), the ranking, the once-per-file memory and the setting, `SubtitleClientTest` for OpenSubtitles being left out of an automatic search. On the first tracks of a video with no full Arabic subtitle, `PlayerSession` asks `OnlineSubtitles.autoFetch`, which skips private items, clips, Online off and files already tried, searches SubDL, SubSource and Gestdown only, picks Arabic full plain tracks first with the local name as a tie-break on the phone, tries up to 3 downloads, and saves like a tapped download. The player loads it as a live subtitle, runs `SubtitleSync`, and shows a glass note with «غيّرها». A subtitle the owner picks meanwhile wins. The "tried" memory is one empty file per video under `files/subs-auto`, so the Room schema is unchanged. The `PlayerSession` wiring and the toast have no test. |
| Setting «ترجمة تلقائية من النت» | UNIT TESTED | `AutoSubtitleTest`, `BackupFormatTest`. Settings › Subtitles: Off, Arabic (default), Arabic then English. In the backup; an older backup keeps the phone's choice. |
| Sites line opens the keys page | UNIT TESTED | `AppLinksTest`, `SitesNoteTest`. `FrameApp` implements `PlayerHost`, returning an explicit intent to `MainActivity` with clear-top, which closes the player (its position is saved) and opens Settings › Online features and keys on top of the restored stack. With no keyed site saved the line adds «أضف مفتاح SubDL مجاني للأفلام والترجمة العربية». The tap itself has no test. |

Five faults from the review of the merge were fixed:

| Fault | Fix |
|---|---|
| The automatic search could send a file name: a library file with no title yet used its base name, an Open with item a near-raw name, and a clip-like name with no title was not a clip. | `AutoSubtitleGate`: only a library row (`fileRowId`), not `NameKinds.CLIP`, with a title whose match is AUTO or MANUAL, and never a blank title. `AutoSubtitleTest` covers each case. |
| The player decided on its copies of the setting and Online, which start at defaults. | The automatic job reads both flows with `first()` before choosing languages. No test. |
| An owner pick made while the downloaded file was being read could be undone. | `addLive` and `playLive` ask again after the read and before the screen changes. No test. |
| A player at the root of its task, opened from a shortcut or the widget, stayed open under the keys page. | The player calls `exit()` after starting the intent, which saves the position. `FrameRoot` uses `AppLinks.shouldOpen`, which skips the page when it is already on top (`AppLinksTest`). |
| TMDB's air date is the network's local date, so in Libya an episode often lands that night or the next morning. | The episode stays announced until air date + 1 day (`Upcoming.of`, `AirDates.dayOf`), and that case reads «تنزل الليلة» and «حلقة جديدة الليلة»; tomorrow stays «تنزل غدًا». `UpcomingTest`, `AirDatesTest`. |

## This round: watchlist, cleanup, stats and automatic backup

Four owner requests, built on branch `pack-library` on 2026-11-12. Not installed, and not seen on a phone, in either theme or in Arabic. Nothing in this round goes online (R2). No permission and no dependency were added.

| Request | Status | Notes |
|---|---|---|
| Watchlist «حابب نشوفه» | UNIT TESTED | `WatchlistTest` (`:data`: the chip holds only watchlist titles and is separate from Favourites; the Home row comes first in `HomeFeed.rows`, newest first, with one title enough, started titles left to Continue watching; `HomeRowKind.automatic` keeps it out of the F-U5 count; `WatchlistRules.done`), `BackupFormatTest` (round trip, and a backup written before it still restores with no watchlist), `RoutesTest`. Stored as `Title.watchlistAt`. Add and remove from the long-press card menu and from the Details ⋮ menu (R13: not a new button). The Library chip shows only while the list holds a title. A title leaves the list once all of it is watched (F-S3), checked after a save reaches 90%, after the end of a video, and after Mark watched; marking unwatched does not bring it back. A merge of two titles keeps it. `WatchlistKeeper` runs on Room and has no test of its own. |
| Cleanup suggestions in Media Manager | UNIT TESTED | `CleanupRulesTest`: extra versions of one episode or film (F-L9) with the best one kept and named, watched files biggest first (capped at 30), each file listed once, total of all, clips never listed, gone files never listed, a hidden title still listed because it holds space, and each row a plain `file|rowId` entry that `planDelete` expands to that one file. A fifth chip «اقتراحات» shows the total, «تحديد الكل», and the two lists. Selecting goes to the existing selection bar and the existing confirmation sheet; nothing deletes without it, and the trash path is the one Media Manager already uses. Private-folder videos have no `MediaFile` row, so they cannot reach the list. The screen has no UI test. |
| «إحصائياتي» | UNIT TESTED | `WatchTallyTest` (only forward playback between two saves counts, up to about twice the wall time, with the wall time capped at 60 s, so a seek, a jump after a long pause or a clock change counts nothing), `WatchStatsBuilderTest` (week from the region's first day, month, all time, seven days oldest first, top series without films or untitled time, a private title never named, films finished). Library ⋮ › «إحصائياتي»: three totals, films finished, a seven-bar chart in the accent colour with today full and the other days dimmer, and the five most watched series. `WatchLogger` adds time from `PlaybackRepositoryImpl.savePosition` and `markFinished`, so `:player` is untouched. Private-folder time is logged under no title, so it counts in the hours and is never named. The log is not in the backup and never leaves the phone. Time watched before this build is not in it; films finished counts every film marked watched. The week starts on the day `Locale.getDefault()` gives, Saturday for `ar-LY`; a phone set to plain Arabic with no region may start it on another day. The chart and the page have no UI test. |
| Automatic backup | UNIT TESTED | `AutoBackupRulesTest`: the name `frame-glass-auto-yyyy-MM-dd-HHmmss.zip` with Latin digits under an Arabic locale, only that exact pattern is ever deleted (a manual backup, a renamed copy such as "(1)", or any other file stays), the newest 5 stay, and when a daily or weekly backup is due (2 h early at most). Settings › Backup › «نسخ احتياطي تلقائي»: Off, Weekly, Daily; picking a schedule with no folder opens `ACTION_OPEN_DOCUMENT_TREE` first and the persistable read and write permission is taken. A periodic WorkManager job with battery-not-low writes the same zip as a manual backup, so keys stay out (R4). Every app open also enqueues a one-time job when one is due, because One UI may never run the periodic one (section 4.7). The page shows the last backup time, a failure line (no folder, folder gone or no longer allowed, write failed) and «انسخ الآن». A half-written file is deleted. Its state lives in its own DataStore file, so a restore never changes the folder or the schedule. `AutoBackupManager` (SAF, WorkManager) has no test that runs it. |

Five faults from the review after the merge with `pack-player` (`fdf9a33`) were fixed:

| Fault | Fix |
|---|---|
| Cleanup «تحديد الكل» could delete every copy: a watched title with two copies listed the extra under Versions and the kept copy under Watched. | A playable with an extra version listed keeps its best copy out of every list. `CleanupRulesTest.selectAllCanNeverTakeEveryCopyOfAPlayable` checks films and episodes with one, two and three copies, equal heights included. Other copies can still be deleted one by one from the other Media Manager tabs. |
| One rewatched episode removed a series from the watchlist, because watched flags never reset. | A title leaves only when every playable was finished after `watchlistAt` (`lastPlayedAt` at or after it), or was marked watched by hand just now. `WatchlistTest` covers the rewatch and the hand mark. |
| Two automatic backup jobs starting together could both write. | `due` is checked again inside the lock; «انسخ الآن» skips the check. No test runs it. |
| The watch log treated 2x as the limit at any speed, dropped a whole step for a short skip, and counted the credits or the rest of a file when the item was marked finished or the countdown moved on. | `savePosition` takes the playing speed (default 1), passed from `PlayerSession.savePosition` (one line). The allowed rate is 1.5 times the speed. A step that held a skip counts only its wall time at that speed, so a 10 s skip drops just the skip; a jump in a step longer than 15 s (a pause) counts nothing. `markFinished` adds nothing and only drops the mark. `WatchTallyTest`. |
| `AutoBackupRules` used `\d`, which ICU matches against Arabic-Indic digits on the phone. | `[0-9]`. `AutoBackupRulesTest` checks a name with an Arabic-Indic digit. |

Build after these fixes, on 2026-11-12: `testDebugUnitTest`, `:parser:test` and `assembleRelease` pass (log `D:\FrameGlass\logsuild-pack-library-review.log`). Unit tests on the merged branch: 1,304 run, 1,303 passed, 0 failed, 1 skipped (`AnimeIndexTest`): parser 161, data 523, net 353, engine 69, player 94, app 71, glass 33. `tools/check_update.py`: pass, 5 steps, 26 tables, 21 saved rows kept. Release APK 23,435,276 bytes. Nothing was installed.

Schema 6: `Title` gains `watchlistAt INTEGER` (nullable), and a new table `WatchLog` (`day` TEXT, `titleId` INTEGER, `watchedMs` INTEGER, primary key `day` and `titleId`, `titleId` 0 for no title and for every private video), through `STATEMENTS_5_6` and `MIGRATION_5_6`, registered in `FrameDb.open`. Both statements only add, so no saved row is touched. `MigrationSchemaTest` holds them against the exported `6.json`, and `tools/check_update.py` passes: 5 steps, 26 tables, 21 saved rows kept. Not run on Android.

`Api.kt` gained `LibraryFilter.WATCHLIST`, `HomeRowKind.WATCHLIST` and `HomeRowKind.automatic`, `TitleDetail.watchlist` and `LibraryRepository.setWatchlist`, all with defaults. `ApiLibraryPack.kt` holds the stats, cleanup and automatic backup types, reached through extensions (`watchStats`, `cleanupSuggestions`, `autoBackups`), so `FrameServices` and the test fakes did not change. The backup format stays version 2: `BackupTitle.watchlistAt` has a default.

## Earlier round: TMDB and IMDb switches

From `OWNER-REQUESTS.md`, now removed there. Shipped in 1.0.104. No phone check of it is recorded, in either theme or in Arabic. No real TMDB or OMDb call has been made: the tests use a fake server.

| Item | Status | Notes |
|---|---|---|
| A switch for TMDB and for IMDb via OMDb | UNIT TESTED | `ServicesTest`. Settings › Online features and keys, next to "Key added / No key". A switch never touches its key. With no key it is disabled and says to add the key. Each row says in English and Arabic what off means, and a line says when IMDb identifies new videos. |
| Off means no calls | UNIT TESTED | `ServiceSwitchTest`. The one HTTP client blocks TMDB, its images, the custom TMDB address, AniList and anibridge, or OMDb and its poster host, Coil's requests included. Saved keys are never touched. |
| Off means no badge | UNIT TESTED | `ViewsTest`. Cards, Details, Top rated and episode rows leave out a service that is off, so with TMDB off a card shows the IMDb rating. |
| OMDb matches new titles while TMDB is off | UNIT TESTED | `OmdbMatchTest` for the calls and scoring, `MetaRoutesTest` for which service answers each queued row. Clips and private titles are checked before any lookup. The queue itself runs on Room and has no test. |
| What OMDb brings | UNIT TESTED | `OmdbMatchTest`. Poster, plot, genres, runtime, cast names, directors and writers, the IMDb rating, and per season the episode titles, air dates and IMDb ratings. No backdrop, logo, original title, cast photo, character, still, collection or Arabic text. Its cast have negative person ids. |
| The free daily limit | UNIT TESTED | `ImdbRulesTest`. Once OMDb says the day is spent, the rows that need it park until 00:10 UTC with no call. OMDb's reset time is `[UNVERIFIED]`. A title OMDb could not settle is asked again at most once a week, through `Title.omdbTriedAt`. |
| TMDB turned back on | BUILT | Each OMDb title is looked up on TMDB by its IMDb id, then fetched in full. Progress, favourites, manual covers and manual matches stay. No test runs this path. |
| Whole poster in Hero and Details | UNIT TESTED | `PosterArtTest` for the shape and the wash, `HeroChoiceTest` for `WideArt`. Any matched title with a poster and no backdrop gets no wide art from `:data`, so the screens show its poster whole on a blurred wash. Episode rows and Continue cards pillarbox a tall picture, and a cast with no photos shows as name chips. |
| Backups | UNIT TESTED | `BackupFormatTest`. Both switches are saved. A backup from before them keeps the phone's switches. |

Six faults from the review of this round were fixed, each with a JVM test of its pure part:

| Fault | Fix |
|---|---|
| Every swipe queued every NONE_FOUND title again. With TMDB off, each spent 1 to 4 OMDb calls for the answer OMDb had already given, so a few swipes over 150 unfound titles could spend the day. | `Title.omdbTriedAt`, new in schema 4, stamps an answer that settles nothing. While TMDB is not usable a swipe skips titles OMDb tried this week, and `MetaRoutes` drops such a row whoever queued it (`ImdbRules.omdbGaveUp`). TMDB still retries them. |
| Saving a TMDB key queued the TMDB return even with its switch off, and with IMDb on those rows went to OMDb, which had already failed on them. The cached settings could also lag a switch just turned on, so TMDB's rows could go to OMDb and be dropped. | The flags follow `ServiceUse`: Online and the switch, for a key as for a switch. `queueTmdbReturn` and `queueMissingImdb` check the saved settings and the key before they queue. `SettingsStore.onSaved` hands every saved value to `NetConfigImpl` before any callback runs, as `KeyVault` does for keys. Online turned on now counts as both services coming on. |
| An OMDb match merged a new anime title into another cour, because every cour shares one IMDb id. Episode numbers overlapped and progress mixed, and TMDB could not undo it. | `ImdbRules.mergesOnImdb`: no merge for anime, a title with an AniList id, a manual match, or an IMDb id with two holders already. The IMDb id is attached, and the TMDB upgrade decides with its AniList check. |
| Restoring a backup from before the switches turned both on, so a service the owner had turned off started calling again. | The switches are nullable in `BackupSettings`, and a missing one keeps the phone's value. |
| IMDb episode ratings stayed after TMDB took a title, frozen. | Cleared when a title gains a TMDB id (`attachMatch`, `attachTmdbForImdb`, `applyManual`), by every TMDB season save, and by the purge. |
| Fix match said to turn on TMDB or IMDb when the block was Online features. | `Identifier.OFFLINE` with its own message, in English and Arabic. |

Beyond the review: `:data` gave Hero and Details the video frame as backdrop for an OMDb title, so the poster the owner asked for showed only with no frame. `WideArt` fixed that, as the row above says. TMDB titles with no backdrop now show their poster too. A manual cover on an unmatched title keeps the frame.

`Api.kt` gained `AppSettings.tmdbEnabled`, `AppSettings.imdbEnabled` and `EpisodeRow.rating`, all with defaults beside the fixed contract. The new net calls are in `net/.../NetExtras.kt`, beside `NetApi.kt`.

## Owner requests with no feature ID

From `WAIVERS.md`.

| Request | Status | Notes |
|---|---|---|
| The app finds videos by itself | BUILT | See F-L4 and F-U5. Never run. |
| Media Manager and file deletion | UNIT TESTED | `MediaManagerTest`, `DeleteFlowTest`. The system trash and its confirmation have never run. |
| Private folder | UNIT TESTED | `VaultFilesTest`, `VaultAuthTest`, `VaultRulesTest`, `VaultFlowTest`. No move, biometric prompt, FLAG_SECURE or recovery has run. |
| Themes and colours | UNIT TESTED | See F-U12. |
| Favourites, hide title, exclude folder | UNIT TESTED | `FavoritesFilterTest`, `HiddenTitlesTest`, `ExclusionsTest`. The long-press menu has no test, and `:glass` cards have no long press, so `CardMenu` watches from outside. |
| Swipe to refresh | UNIT TESTED | `RefreshFlowTest`, the swipe tests in `data/src/test/.../scan/`, `LockWithinTest`. Live stages and an exact summary replace the before-and-after count. The pill has no UI test. |
| Private items in the player | BUILT | Code review only: FLAG_SECURE, recents, blank notification metadata, no screenshots, background audio or online search. |
| Launcher shortcuts and the Continue watching widget | UNIT TESTED | Waiver of 2026-11-08. See "This round". Never run. |
| Next episode date, trailer, tonight's pick | UNIT TESTED | See "This round". Never run. |
| Watchlist, cleanup suggestions, stats, automatic backup | UNIT TESTED | Built on `pack-library`. See "This round: watchlist, cleanup, stats and automatic backup". Never run. |

## Gates

| Gate | State |
|---|---|
| 0 | Not passed. Matrix 50 of 53 Core and the delay proof are done, see `phase-0-results.md`. Soak, glass proof, the full C2 run and the owner checks are open. |
| 1 to 7b | Not run. App work went ahead under the 2026-11-07 waiver. |
| 8 | Not needed so far. Decided at Gate 0. |
| 9 | Not started. |
