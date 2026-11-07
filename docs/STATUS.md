# Status

Updated 2026-11-07, at the end of the improvement package. R9 applies.

## Read this first

- **Nothing in the improvement package has run on the phone.** No agent used adb. That covers themes, language, favourites, the long-press menu, file facts, Media Manager, delete, the private folder, BiometricPrompt, online subtitle search, the new player sheets, swipe to refresh and the schema 2 migration.
- **The last phone evidence on record is the first app build,** 18:19 to 18:22. Everything after it is unproven there, including whole-phone search, clip sorting, the Videos row and chip, Recently added, the new Home scan state, the sheet tint and the player fixes.
- **Delete and the private folder move real files, and neither has ever run on Android.** Their pure logic has tests. `PrivateVaultImpl`, `MediaManagerImpl` and `MediaStoreTrash` have none, because they need Room and MediaStore. Test with a throwaway clip first, as `NEXT.md` says.
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

Run on this PC on 2026-11-07 with `--rerun-tasks --no-build-cache`: 799 tasks, all executed, none from a cache.

| Check | Result |
|---|---|
| `assembleQa`, `assembleRelease` | Pass, R8 included. |
| Unit tests | 933 run, 932 passed, 0 failed, 1 skipped: parser 154, data 374, net 308, engine 28, player 31, app 20, glass 18. |
| Skipped test | `AnimeIndexTest.theRealFileParses` needs a local copy of the mappings file. |
| No tests at all | `:qa`, `:third_party:ass-media`. |
| Android lint | `lintRelease` on `:app`, `:player`, `:data`, `:glass`, `:engine`, `:net`, `:qa`: 0 errors, 21 warnings, `MissingTranslation` 0. |
| `tools/check_update.py` | Pass: 1 step, 25 tables, 21 saved rows kept. This is desktop SQLite, not Android. |
| `.\fg.ps1 build` | Pass: signature, 16 KB alignment, size, `lib/` listing. |
| Release APK | 23,013,892 bytes (21.9 MiB), budget 60 MB, only `lib/arm64-v8a`. |
| `qa` APK | 23,046,700 bytes (22.0 MiB), same certificate, 16 KB alignment passes. |
| Certificate SHA-256 | `6517fbf832436c7cb106855c03d8b8b1d94816517b2f4328279fb9a051354d67` |
| Permissions in the release APK | The list in section 4.10 plus `USE_BIOMETRIC`. `USE_FINGERPRINT` is removed. |
| Not run | adb, install, `.\fg.ps1 ship`, `MigrationTestHelper`, and every check on the phone. |

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
| F-L5 | Backup and restore | UNIT TESTED | Not seen | `BackupFormatTest` covers the format, favourites and excluded folders. Private items stay out by id. Dump, wipe and restore not run. |
| F-L6 | Migrations | UNIT TESTED | Not seen | Schema version 2. `MigrationSchemaTest` and `tools/check_update.py` replay 1 to 2 on desktop SQLite. `MigrationTestHelper` not run, and no upgrade on Android yet. |
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
| F-M3 | Fix match | BUILT | Not seen | Menu and sheet exist. No test. |
| F-M4 | Manual wins | BUILT | Not seen | Guards in `TitleOps` and `MetadataPipeline`. No test. |
| F-M5 | Saved offline | UNIT TESTED | Not seen | `TmdbDetailsTest` for fields and sizes. Saving to disk has no test. |
| F-M6 | Offline queue | BUILT | Not seen | `RetryPolicyTest` covers the delays only. |
| F-M7 | Frame fallback | UNIT TESTED | Part: frames on cards (`09`) | `FramePickerTest`. |
| F-M8 | Key handling | UNIT TESTED | Part: key step (`02`) | `BlobCipherTest`, `TmdbMatchTest`. AndroidKeyStore path not run. |
| F-M9 | Anime flow | UNIT TESTED | Not seen | `AnimeFlowTest`, `AnimeMappingsTest`. Uses `mappings.min.json`, the brief's last option, because no zstd library is pinned. |
| F-M10 | Ratings | UNIT TESTED | Not seen | `TitleListsTest`, `TmdbDetailsTest`. The owner asked for the badge, and it has never been seen. |
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
| F-S2 | Countdown | BUILT | Not seen | `NextUpTest` picks the next item. The card and countdown have no test. |
| F-S3 | Watched | UNIT TESTED | Not seen | `WatchRulesTest`. |
| F-S4 | Skip intro | UNIT TESTED | Not seen | `SkipSegmentsTest`, `SegmentRulesTest` for manual, AniSkip and TheIntroDB. The local path of Phase 7b is not started. |
| F-S5 | Missing episodes | UNIT TESTED | Not seen | `EpisodeRowsTest`. |
| F-S6 | Series memory | UNIT TESTED | Not seen | `TrackPickerTest`. |
| F-S7 | Sidecars | UNIT TESTED | Not seen | `SidecarMatcherTest`. |
| F-S8 | Night audio | UNIT TESTED | Not seen | `NightCompressorTest`. Owner listening check not done. |
| F-S9 | Online subtitle search | UNIT TESTED | Not seen | `OnlineSubtitleQueryTest`, `OnlineSubtitleSearchTest`. Now reachable from the subtitle sheet. No real provider has been called. |
| F-S10 | Resume logic | UNIT TESTED | Not seen | `ResumeLogicTest`. |

### Professional tools

| ID | Feature | Status | Phone | Notes |
|---|---|---|---|---|
| F-T1 | Thumbnail seek | BUILT | Not seen | Stutter at 4K not measured. |
| F-T2 | Chapters | NOT STARTED | Not seen | No chapter code exists. Phase 7. |
| F-T3 | Frame step | NOT STARTED | Not seen | No frame step code exists. Phase 7. |
| F-T4 | Screenshot | BUILT | Not seen | |
| F-T5 | Info panel | BUILT | Not seen | |
| F-T6 | Sleep timer | BUILT | Not seen | |
| F-T7 | Anime4K | NOT BUILT (allowed) | Not seen | Needs mpv, Phase 8. |

## Owner requests with no feature ID

From `WAIVERS.md`.

| Request | Status | Notes |
|---|---|---|
| The app finds videos by itself | BUILT | See F-L4 and F-U5. Never run. |
| Media Manager and file deletion | UNIT TESTED | `MediaManagerTest`, `DeleteFlowTest`. The system trash and its confirmation have never run. |
| Private folder | UNIT TESTED | `VaultFilesTest`, `VaultAuthTest`, `VaultRulesTest`, `VaultFlowTest`. No move, biometric prompt, FLAG_SECURE or recovery has run. |
| Themes and colours | UNIT TESTED | See F-U12. |
| Favourites, hide title, exclude folder | UNIT TESTED | `FavoritesFilterTest`, `HiddenTitlesTest`, `ExclusionsTest`. The long-press menu has no test, and `:glass` cards have no long press, so `CardMenu` watches from outside. |
| Swipe to refresh | BUILT | The new-video count is approximate. |
| Private items in the player | BUILT | Code review only: FLAG_SECURE, recents, blank notification metadata, no screenshots, background audio or online search. |

## Gates

| Gate | State |
|---|---|
| 0 | Not passed. Matrix 50 of 53 Core and the delay proof are done, see `phase-0-results.md`. Soak, glass proof, the full C2 run and the owner checks are open. |
| 1 to 7b | Not run. App work went ahead under the 2026-11-07 waiver. |
| 8 | Not needed so far. Decided at Gate 0. |
| 9 | Not started. |
