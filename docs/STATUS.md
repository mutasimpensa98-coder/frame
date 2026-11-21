# Status

Updated 2026-11-22, after search in Library, the flat list of folders, a Folders screen and the drag order of the Library chips were built on branch `library` (cut from `main` at `e20bfe5`; waiver of 2026-11-22). Not merged, not installed and not seen on a phone. The Folders screen is not reachable on this branch: branch `tabs` puts it in the tab bar. See "This round: search in Library, flat folders, chip order".

Updated 2026-11-21, after five findings of an independent review of the merged tree were fixed on branch `themes`. Not installed and not seen on a phone. See "This round: review fixes on the merged tree".

Updated 2026-11-21, after branch `views` (with `select`) was merged into branch `themes`. Nothing of the merged tree is installed or seen on a phone. See "Build and tests" and the last rows of "This round: more themes and custom colours".

Updated 2026-11-21, after eight ready themes and a custom theme were built on branch `themes` (waiver of 2026-11-21). Not installed and not seen on a phone. Before that:

Updated 2026-11-21, after the private folder shortcut on Home was built on branch `views` (owner request of 2026-11-21). Not merged, not installed and not seen on a phone. See "This round: private folder shortcut on Home".

Updated 2026-11-21, after the delete inside the private folder was built on branch `views` (owner decision and waiver of 2026-11-21). Not merged, not installed and not seen on a phone. See "This round: delete inside the private folder".

Updated 2026-11-21, after the five view sizes were built on branch `views`, on top of `select` (owner request and waiver of 2026-11-21). Not merged, not installed and not seen on a phone. See "This round: view sizes".

Updated 2026-11-21, after multi-select was built on branch `select` (owner request of 2026-11-21). It is not merged, not installed and not seen on a phone. See "This round: multi-select". The lines below are the earlier updates.

Updated 2026-11-19, after branch `android10` lowered `minSdk` from 33 to 30 so the app installs on Android 11 and newer (waiver of 2026-11-19; Android 10 was built first and dropped by the owner the same day). Before that, updated 2026-11-12, after branch `android13` lowered `minSdk` from 36 to 33 so the app installs on Android 13 and newer (waiver of 2026-11-12). Before that, updated 2026-11-12, after the watchlist, cleanup suggestions, stats and automatic backup were built on branch `pack-library`. Before that, updated 2026-11-08, after launcher shortcuts, the Continue watching widget, the next episode date, the trailer button and tonight's pick were built on branch `pack-home`. R9 applies.
Updated 2026-11-19, after the developer name on About, the episode delete on Home and "Play in pop-up" were built on branch `features`. Before that, updated 2026-11-12, after branch `android13` lowered `minSdk` from 36 to 33 so the app installs on Android 13 and newer (waiver of 2026-11-12). Before that, updated 2026-11-12, after the watchlist, cleanup suggestions, stats and automatic backup were built on branch `pack-library`. Before that, updated 2026-11-08, after launcher shortcuts, the Continue watching widget, the next episode date, the trailer button and tonight's pick were built on branch `pack-home`. R9 applies.

## Read this first

- **Branch `library` (2026-11-22) adds a search button in Library, Folders as one flat list (the new default, with a setting that brings the tree back), a Folders screen for a tab, and filter chips the owner drags into order.** Built and unit tested (the flat list, the two settings and their backup, the chip order rules, when a dragged chip trades places, the rules of a search page, and what a delete of a flat row takes), never installed or seen on a phone: no chip has been dragged on a device, no search field has opened in Library, and the flat list has never been drawn. `FoldersScreen()` is not wired to anything on this branch. See "This round: search in Library, flat folders, chip order".
- **Review fixes on `themes` (2026-11-21), after the merge.** The private delete sheet closes when the folder locks; a private delete keeps a title the owner used in the library; a picked folder of folders really deletes and moves what is under it; the delete answers before its cleanup, which a kill no longer loses; and the theme pictures are worked out off the main thread while a slider is dragged. Unit tested where a rule could be tested; none of it has run on a phone. See "This round: review fixes on the merged tree".
- **Branch `themes` (2026-11-21) adds eight ready themes, a custom theme with three sliders, two more accents, "any colour", and a Follow the phone switch.** Built and unit tested, never installed or seen on a phone: no theme has been looked at on any screen, and the new Appearance page has never been drawn. The update keeps the old look by a tested mapping. See "This round: more themes and custom colours".
- **Branch `views` (2026-11-21) also puts a lock button at the top start of Home that opens the private folder.** One glass button, no test of its own, never seen on a phone. See "This round: private folder shortcut on Home".
- **Branch `views` (2026-11-21) also adds a permanent delete inside the private folder, always behind its own confirmation sheet.** Built and unit tested (the plan, what goes with a video, and that nothing is deleted before the confirmation), never installed: no private video has been deleted by this code, on any device, and the cleanup of a title left with no file has only been read through. See "This round: delete inside the private folder".
- **Branch `views` (2026-11-21), on top of `select`, adds five view sizes behind the view button: compact list, list, small, medium and large grid.** In Library, Folders, the row, genre and person pages, Search and the private folder, remembered per place and kept in backups. Built and unit tested (columns, what each size shows, the stored choice, the backup), never installed or seen on a phone: nobody has looked at any of the five, in any theme or language. Folders now open as the compact list. See "This round: view sizes".
- **Branch `select` (2026-11-21) adds multi-select: «تحديد» in the long-press menus, then a selection bar on top and an action bar at the bottom.** Built and unit tested (rules and state only), never installed or seen on a phone. No tap has picked a card on a device, no bulk delete, move or mark has run, and nobody has looked at the mark in either theme. Not built: a delete inside the private folder. See "This round: multi-select".
- **1.0.116 is on the phone (2026-11-19), updated in place, minSdk 30.** Seen on the phone: Play in pop-up from a Continue card enters picture-in-picture (pinned 16:9 window over Home, playing); the long-press menu shows on the hero; a lone video filed as a series shows one plain Delete after the 1.0.116 fix, a real series shows Delete the whole series; About shows the developer line. Not tried: Delete this episode (no series was in Continue watching), deleting the video the pop-up plays, anything on Android 11 or 12.
- **Branch `features` (2026-11-19) adds the developer name on About, the long-press menu on the Home hero with «حذف هذه الحلقة» on a series' Continue card, and «تشغيل في نافذة منبثقة».** Built and unit tested, never installed or seen on a phone. No device has entered picture-in-picture through this code, and no delete has run from the new rows. See "This round: developer name, delete from Home, play in pop-up".
- **1.0.113 is on the phone (2026-11-12), updated in place.** Seen on the phone: the feature tour opens from Settings, all 6 pages render with the owner's matched covers on page 2, Next walks through them, and Skip lands on Home. The repository is public since this version.
- **Branch `android10` (2026-11-19): `minSdk` 30 in every module, so the app installs on Android 11 and newer.** It was built for Android 10 first (`9a534e4`); the owner read the costs and chose Android 11, and everything that existed only for Android 10 was removed: the two storage permissions and `requestLegacyExternalStorage`, the permission dialog, the direct delete with no trash, and the API 29 fallbacks for volumes, change signals, frames and the cutout. `compileSdk` and `targetSdk` stay 37, `arm64-v8a` stays the only ABI, the application ID and key are unchanged, the manifest has the same 9 permissions as before, and no dependency changed (the highest `minSdk` among them is 24). Below Android 13 glass has no blur and the app keeps its own language choice; on Android 11 unseen episode pictures are smeared instead of blurred and picture-in-picture is entered from `onUserLeaveHint`. `WAIVERS.md` lists every difference. The branch also holds the merged `features` branch (`0ac6957`). It has not run on any phone: not on the owner's S26 Ultra, and not on any Android 11 or 12 phone. No emulator was used. Every row below keeps the status it had; none of them says anything about Android 11 or 12. `NEXT.md` section 2, items 123 to 134, is the check for such a phone.
- **Branch `android13` (2026-11-12): `minSdk` 33 in every module, so the app installs on Android 13 and newer.** `compileSdk` and `targetSdk` stay 37, `arm64-v8a` stays the only ABI, the application ID and key are unchanged. It has not run on any phone: not on the owner's S26 Ultra, and not on any Android 13, 14 or 15 phone. No emulator was used. Every row below keeps the status it had; none of them says anything about Android 13 to 15. `NEXT.md` section 2, items 103 to 115, is the check for the other person's phone.
Updated 2026-11-12, after the feature tour was built on branch `tour`. Before that, updated 2026-11-12, after the watchlist, cleanup suggestions, stats and automatic backup were built on branch `pack-library`. Before that, updated 2026-11-08, after launcher shortcuts, the Continue watching widget, the next episode date, the trailer button and tonight's pick were built on branch `pack-home`. R9 applies.

## Read this first

- **Branch `tour` (2026-11-12) adds the feature tour «جولة في المميزات».** Built and unit tested, never installed or seen on a phone. See "This round: feature tour".
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

Branch `library`, on this PC on 2026-11-22, with the build cache: `testDebugUnitTest`, `assembleRelease`, `:app:lintRelease`, `:data:lintRelease` and `:glass:lintRelease` pass, and `python -I tools\check_update.py` passes (5 steps, 26 tables, 21 saved rows kept; no schema changed, and it was run because the settings and the backup did). Log: `build\library-build.log` in the worktree, not copied to `D:\FrameGlass\logs`. Unit tests: 1,474 run, 1,473 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): data 646 (49 new: `FlatFoldersTest` 21, `LibraryLayoutTest` 20, `SettingsPrefsTest` 13 to 17, `BackupFormatTest` 41 to 45), net 353, engine 75, player 122, app 215 (12 new: `SearchRulesTest` 8, `SelectionRulesTest` 41 to 44, `CardMenuRulesTest` 22 to 23), glass 63 (11 new: `ChipDragRulesTest`). `:parser:test` (161 before), `assembleQa`, `fg.ps1 build` and lint of the other modules were not run. Lint: `:data` and `:glass` no issue, `:app` 0 errors, 23 warnings and 1 hint, none in a file or a string of this round. Release APK 23,934,613 bytes. Nothing was installed, and no emulator or phone was used.

Branch `themes` after the review fixes, on this PC on 2026-11-21, with the build cache: `testDebugUnitTest`, `:parser:test`, `assembleRelease`, `:app:lintRelease` and `:data:lintRelease` pass, and `python -I tools\check_update.py` passes (5 steps, 26 tables, 21 saved rows kept; no schema changed). Log: `D:\FrameGlass\logs\build-themes-review.log`. Unit tests: 1,563 run, 1,562 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): parser 161, data 597 (9 new, all in `VaultDeleteRulesTest`), net 353, engine 75, player 122, app 203 (19 new: `VaultDeleteFlowTest` 10 to 18, `CardMenuRulesTest` +4, `SelectionRulesTest` +4, `DeleteFlowTest` +2, `VaultFlowTest` +1), glass 52 (1 new in `ThemeContrastTest`). Lint: `:data` no issue, `:app` 0 errors, 24 warnings and 1 hint, the same 25 findings as before the fixes and none in a file of this round. Release APK 23,859,857 bytes. Nothing was installed, and no emulator or phone was used.

Branch `themes` with `views` merged (tip `895084d`), on this PC on 2026-11-21, with the build cache: `testDebugUnitTest`, `:parser:test`, `assembleRelease`, and `lintRelease` on `:app`, `:glass`, `:data` and `:player` pass, and `tools\check_update.py` passes (5 steps, 26 tables). Log: `D:\FrameGlass\logs\build-themes-merged.log`. Unit tests: 1,534 run, 1,533 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): parser 161, data 588, net 353, engine 75, player 122, app 184, glass 51. Beyond the two branches' own tests, the merge added 6: `SettingsPrefsTest` 9 to 13 (view sizes in the settings file, beside the theme keys) and `ThemeContrastTest` 33 to 35 (a picked row and the check of a picked picture, on every theme). Lint: `:glass` and `:data` 0 findings, `:app` 25 warnings and `:player` 9 warnings, no error. The paragraphs below are each branch before the merge.

Branch `themes`, on this PC on 2026-11-21, with the build cache: `testDebugUnitTest`, `:parser:test`, `assembleRelease`, `:app:lintRelease` and `:glass:lintRelease` pass, and `tools\check_update.py` passes. Log: `D:\FrameGlass\logs\build-themes.log`. Unit tests: 1,426 run, 1,425 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): parser 161, data 559 (24 new: `ThemeMigrationTest` 10, `SettingsPrefsTest` 9, `BackupFormatTest` 32 to 37), net 353, engine 75, player 122, app 107 (`CommonTest` 2 to 6), glass 49 (16 new: `ThemeContrastTest` 18 to 33, `IconTileTest` 3 to 4). Lint: `:glass` 0 findings, `:app` 25, none of them in a file of this round; `:player:lintRelease` and `:data:lintRelease` pass too, run apart from the log. `:engine`, `:net` and `:qa` lint and `assembleQa` were not run, and none of the three changed.

Branch `views` after the Home shortcut, on this PC on 2026-11-21, with the build cache: `testDebugUnitTest`, `assembleRelease` and `:app:lintRelease` pass. Unit tests: 1,323 run, 0 failed, 1 skipped, the same counts as the build before it: the shortcut is one button and added no test. `:app` lint: 0 errors, 23 warnings and 1 hint. Release APK 23,800,925 bytes. Nothing was installed, and no emulator or phone was used.

Branch `views` after the delete inside the private folder, on this PC on 2026-11-21, with the build cache: `testDebugUnitTest`, `assembleRelease` and `:app:lintRelease` pass, and `python -I tools/check_update.py` passes (two queries were added to the DAOs, and no table or column changed). Unit tests: 1,323 run, 0 failed, 1 skipped (the same `AnimeIndexTest`): data 560 (12 new: `VaultDeleteRulesTest`), net 353, engine 75, player 122, app 180 (12 new: `VaultDeleteFlowTest` 10, two in `SelectionRulesTest`), glass 33. `:parser:test`, `assembleQa`, `fg.ps1 build` and lint of the other modules were not run. `:app` lint: 0 errors, 23 warnings and 1 hint, none in a file or a string of this round. Release APK 23,800,925 bytes. Nothing was installed, and no emulator or phone was used.

Branch `views`, on this PC on 2026-11-21, with the build cache: `testDebugUnitTest`, `assembleRelease` and `:app:lintRelease` pass, and `python -I tools/check_update.py` passes (no schema changed; it was run because the settings and the backup did). The output was not saved under `D:\FrameGlass\logs`. Unit tests: 1,299 run, 0 failed, 1 skipped (the same `AnimeIndexTest`): data 548 (13 new: `ViewSettingsTest` 9, four in `BackupFormatTest`), net 353, engine 75, player 122, app 168 (16 new: `ViewRulesTest`), glass 33. `:parser:test`, `assembleQa`, `fg.ps1 build` and lint of the other modules were not run; no manifest or dependency changed. `:app` lint: 0 errors, 23 warnings and 1 hint, none in a file or a string of this round, `NewApi` and `MissingTranslation` 0. Release APK 23,780,997 bytes. Nothing was installed, and no emulator or phone was used.

Branch `select`, on this PC on 2026-11-21, with the build cache: `testDebugUnitTest`, `assembleRelease` and `:app:lintRelease` pass. The output was not saved under `D:\FrameGlass\logs`. Unit tests: 1,270 run, 1,269 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): data 535, net 353, engine 75, player 122, app 152 (49 new: `SelectionRulesTest` 35, `SelectionStateTest` 10, four in `VaultFlowTest`), glass 33. `:parser:test`, `assembleQa`, `fg.ps1 build`, `tools/check_update.py` and lint of the other modules were not run; no schema, manifest or dependency changed. `:app` lint: 0 errors, 23 warnings and 1 hint, none in a file or a string of this round, `NewApi` and `MissingTranslation` 0. Release APK 23,746,017 bytes. Nothing was installed, and no emulator or phone was used.

Branch `android10` at `minSdk` 30, with `features` merged, on this PC on 2026-11-19: `testDebugUnitTest`, `:parser:test` and `assembleRelease` pass, and `lintRelease` passes on `:app`, `:player`, `:data`, `:glass`, `:engine`, `:net` and `:qa`. Logs: `D:\FrameGlass\logs\build-android11.log` (every test task rerun, no build cache), `lint-android11.log` and `check-update-android11.log`; the logs of the Android 10 build of the same day are `build-android10.log`, `lint-android10.log` and `lint-android10-before\`. Unit tests: 1,370 run, 1,369 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): parser 161, data 527, net 353, engine 75, player 119, app 102, glass 33. The two tests of the Android 10 mount point rule left with that rule; the rest of this change is platform calls that a JVM test cannot run. Lint: `NewApi` 0 and `InlinedApi` 0 in every module, 0 errors in the seven modules above, and no `ObsoleteSdkInt` from a version check (the one reported is the old `mipmap-anydpi-v26` folder). Warnings and hints: app 25, of which 9 are `UnusedAttribute` for manifest and widget attributes that Android 11 and 12 ignore and 8 are notices of newer library versions, which come from lint's version cache and not from the code; player 9 and qa 5 as before; data, net, glass and engine none. `:third_party:ass-media` still stops on its 7 upstream errors (6 `UnsafeOptInUsageError`, 1 `ByteOrderMark`), unchanged and with no `NewApi`. Lint does not see everything, so two scans were made against the SDK's `api-versions.xml`. The compiled classes of every module: 15 references above API 30 in 4 classes, each guarded: `PlayerCompat` (the two picture-in-picture calls of API 31), `MatrixActivity` in `:qa`, and the two classes that alone touch a type only newer phones have, `AppLocale.Api33` (`LocaleManager`) and `AgslBrush` (`RuntimeShader`). The dex of the release APK: Kotlin's `SwitchBootstraps.typeSwitch` (API 36), which the project's `when` over sealed types compiles to, is desugared by R8 and absent. No core library desugaring was added. Every native library in the APK carries an NDK API note of 21 to 24. No SQL needs a SQLite newer than Android 11's 3.28. After the four review fixes of the same day (`build-android11-fixes.log`, `lint-android11-fixes.log`, `check-update-android11-fixes.log`): 1,381 run, 1,380 passed, 0 failed, 1 skipped: data 535 (8 new), player 122 (3 new), the rest as above; lint and the update check unchanged; release APK 23,709,225 bytes. The merged manifest: `minSdkVersion` 30 and exactly the 9 permissions it had before this branch. `tools/check_update.py`: pass, 5 steps, 26 tables, 21 saved rows kept. Release APK 23,709,225 bytes. Nothing was installed, and no emulator or phone was used.

Branch `features`, on this PC on 2026-11-19, with the build cache: `testDebugUnitTest`, `assembleRelease`, `:app:lintRelease` and `:player:lintRelease` pass. Log: `D:\FrameGlass\logs\build-features.log`. Unit tests: 1,209 run, 1,208 passed, 0 failed, 1 skipped (the same `AnimeIndexTest`): data 527, net 353, engine 75, player 119 (20 new: `PopUpTest` 13, `PlayerRouteTest` 7), app 102 (18 new: `CardMenuRulesTest`), glass 33. `:parser:test`, `assembleQa`, `fg.ps1 build`, `tools/check_update.py` and lint of the other modules were not run; no schema and no manifest changed. Lint: `:app` 0 errors, 14 warnings and 1 hint; `:player` 0 errors, 9 warnings; none in a line of this round, `NewApi` and `MissingTranslation` 0 in both. This branch still has `minSdk` 33. The new code calls nothing above API 29 (`AppOpsManager.checkOpNoThrow` is API 19, `enterPictureInPictureMode(params)` API 26); `setAutoEnterEnabled` and `setSeamlessResizeEnabled` (API 31) in `pictureInPictureParams()` are older code, left as they were for the branch that lowers `minSdk` to guard. Release APK 23,543,921 bytes. Nothing was installed, and no emulator or phone was used.

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
| F-L10 | File facts | UNIT TESTED | Not seen | `FolderBuilderTest` for NEW, `FileFactsTest` for the facts. Chips and the facts sheet not seen. Since 2026-11-22 Folders are one flat list by default (`FlatFoldersTest`), never seen. |
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
| F-U2 | Library filter | UNIT TESTED | Part: chips (`09`) | `LibraryFilterTest`, `FavoritesFilterTest`. Videos and Favourites chips added after that run. Filtering not seen. Since 2026-11-22 the chips are in the owner's order and are dragged into it (`LibraryLayoutTest`, `ChipDragRulesTest`); the drag has never run. |
| F-U3 | Library sort | UNIT TESTED | Part: closed dropdown and grid (`09`) | `LibraryOrderTest`, `TitleListsTest`. |
| F-U4 | Cards | BUILT | Part: 2:3 frames with titles (`09`) | Rating badge and progress line never seen. The owner asked for both. Videos now use 16:9 cards. |
| F-U5 | Home | UNIT TESTED | Part: hero, meta line, pill, dots (`08`) | `HomeBuilderTest`, `HomeClipsTest`. Rows, auto-advance and Resume not seen. Recently added goes beyond the row list here. |
| F-U6 | Continue watching card | UNIT TESTED | Not seen | Data only, in `HomeBuilderTest`. |
| F-U7 | Details | BUILT | Part: art, title, pill, ⋮ (`10`) | Everything that needs a key not seen. |
| F-U8 | Actor | BUILT | Not seen | No test. |
| F-U9 | Episodes | UNIT TESTED | Not seen | `EpisodeRowsTest`. |
| F-U10 | Search | UNIT TESTED | Not seen | `SearchTextTest`, `ParserIntegrationTest`. The FTS query and the 100 ms target not run. Since 2026-11-22 the same search opens from the top bar of Library, and Folders have a search of names (`SearchRulesTest`, `FlatFoldersTest`); neither has been seen or timed. |
| F-U11 | + button | BUILT | Part: on Home and Library, not on Details (`08`, `09`, `10`) | |
| F-U12 | One look, now themes | UNIT TESTED | Not seen | Replaced by the waiver. Dark, light, follow phone, six accents. `ThemeContrastTest` computes contrast and measures no pixels. No light theme, blur, status bar or shader has been seen. |
| F-U13 | Copy | BUILT | Not seen | Plurals and time left are in resources. |
| F-U14 | Settings | BUILT | Not seen | Rebuilt as pages: appearance, language, library, playback, online, backup, private folder, Media Manager, about. |
| F-U15 | Onboarding | VERIFIED ON PHONE | Whole flow, first build (`01` to `06`) | Recheck on the next install: the step dots changed, and the empty Home now searches the phone. On branch `tour` a fresh install shows the feature tour before these steps; not seen. |

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
| F-P13 | PiP and lock | BUILT | Not seen | Gate 2 owner check. «تشغيل في نافذة منبثقة» (2026-11-19) enters with the same params; see its round section. |
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

## This round: search in Library, flat folders, chip order

Owner requests of 2026-11-22, built on branch `library` (cut from `main` at `e20bfe5`). Waiver "2026-11-22: Search in Library, flat folders, chip order". Not merged, not installed and not seen on a phone, in either language or any theme. No permission, dependency, network host, manifest or schema change. Branch `tabs`, built the same day by another session, owns the tab bar, the swipe between tabs and the Folders tab; this branch only hands it `FoldersScreen()` and holds its swipe lock while a chip is dragged.

| Part | Status | Notes |
|---|---|---|
| Search in Library | BUILT | A search button, the first of the four in the top bar of Library (`SearchAction`). It turns the bar into the search field (`SearchBar`: back arrow, the glass field, ×, and the view button while titles are listed), puts the cursor in it and raises the keyboard. The chips and the list give way to the results, which are the Search screen's own: titles, people and genres, at the view size of Library, with selection mode. Back or the arrow closes the search, before the tab's own Back. The grid of Library is not rebuilt while searching: its state stays with the screen, so the filter and the place in the list are back when the search closes. Read through, not seen: the focus and the keyboard, the swap of the two bars, and the return to the same place have never run. Four buttons leave the title about 155 dp on a screen 411 dp wide, by arithmetic and not by a look; a narrower screen or a large font may cut it. |
| One search, three places | UNIT TESTED | `search/SearchParts.kt` holds what the Search screen had: the field, the live answer (`rememberSearch`, one flow that a new letter cancels, as before; there was and is no debounce) and the results (`TitleSearchBody`, `searchHeader`). `SearchScreen()` is now about 60 lines over them and behaves as it did, with one difference: an empty answer left behind by a cleared field no longer flashes "Nothing found" under the next letter. `SearchRules` and `SearchRulesTest` (8): what the page shows for typed words and their answer, when the view button shows, and what "Select all" picks. The rules are tested; no field has been typed into. |
| Folders as one flat list | UNIT TESTED | `FlatFolders` in `data/logic`, `LibraryRepository.flatFolders(sort)`. `FlatFoldersTest` (21): only folders that hold videos directly have a row (a folder of folders has none, the top of a volume has one when a video lies there); a row counts and weighs its own videos; two folders of one name carry their parent path, shortened to its last two folders, the volume's name for a top folder, and the whole path when two still read the same; names that differ only in case count as one name; the files of a hidden title, of a lost volume and of a volume with no path have no row; a folder whose videos are all gone is dimmed; the four orders; the tree is unchanged. It reads the same file rows as the tree, so excluded folders and private videos cannot reach it. Not seen. |
| A row acts on its own videos | UNIT TESTED | `FolderEntry.direct`, `SelectItem.Folder.direct`, `Route.Folder(path, direct)`, `CardMenuRules.folderContent`. A tap opens `folderVideos(path)`, the videos directly in the folder. Delete and "Move to the private folder", from the long-press menu and from selection mode, take the Media Manager entry at that path alone, never the folders under it, which a row of the tree still does. `CardMenuRulesTest` +1, `SelectionRulesTest` +3, `RoutesTest` (a saved stack with and without the field). Excluding a folder still covers everything under it. No delete or move has run from a flat row. |
| The second line of a row | BUILT | `folderFacts` in `ViewCells.kt`: the parent path, then the count, on the dimmed line of the compact row and the list row, each part isolated for right-to-left; in the medium and large grid it leads the line of facts, and in the small grid, which has no such line, it stands alone under the name. Only a name that repeats has one. Not seen. |
| A sort for Folders | UNIT TESTED | `FolderSort`: name, size, number of videos, newest (a folder is as new as its newest video's file time). The order is tested; the button is the sort dropdown of Library, shown over the flat list when it holds more than one folder, and kept for the session in `UiModel`. Not seen. |
| Folders view setting | UNIT TESTED | `FoldersMode` FLAT or TREE, `AppSettings.foldersMode`, null until chosen and then FLAT, so every existing install opens Folders flat after the update. `LibraryLayoutTest` (20, with the chip rules), `SettingsPrefsTest` 13 to 17. Settings › Library › "Library layout" › "Folders view". `UiModel.foldersRoot` follows the setting for Library's chip and for the Folders screen. DataStore has not run with the key. |
| `FoldersScreen()` | BUILT | `library/FoldersScreen.kt`. Title «المجلدات», search, view button, selection, pull to refresh, the flat list or the tree by the setting. A folder opens inside the screen (`FolderPage`, the page `Route.Folder` shows too), with a back arrow and a `BackHandler` that steps out before the tab is left; each level keeps its scroll place. Nothing on this branch shows it: `TabsScreen` is the other branch's file. It has never been composed, on a phone or anywhere else. |
| Search of folders | UNIT TESTED | `LibraryRepository.searchFolders(query, under, deep)` over the file rows the library holds: folders that hold videos directly, then videos, whose name holds every word typed, anywhere in the name, through the normaliser of the title search; at most 200 of each. The Folders screen searches everything, a folder page what is under it, and a page of a flat row its own videos. Covered in `FlatFoldersTest` with a plain lower-case key; the Arabic normaliser's part is the title search's and is not tested again here. The field and the results are `FolderSearchBar` and `FolderSearchBody`. Not seen, and the time for 2,000 files was not measured: each letter normalises every name in scope on a background thread. |
| Chip order, the rules | UNIT TESTED | `ChipOrder` in `data/api/ApiLibraryLayout.kt`, `LibraryLayoutTest`: the default order; stored as names, unknown names and repeats dropped; chips the saved order does not name come after it in the default order; Anime and Watchlist show only while they hold something and take their saved place when they appear; the filter in view always has its chip; a move toward the start or the end, on a row with hidden chips, on a short saved order, and every move of a 7-chip row; reset. |
| Chip order, the drag | BUILT | `ReorderableFilterChips` in `:glass` (`ReorderChips.kt`), used by `LibraryChips`. A long press, watched before the chips and the row see the touch, lifts the chip: a tick, 1.08 scale, a 10 dp shadow over a canvas backing. It follows the finger, the others slide with `animateItem`, and a chip pushed past an edge scrolls the row, each frame, up to 720 dp a second. On release it glides to its place and the order is saved once. Right-to-left is handled by hand where a raw offset is used. `ChipDragRules` and `ChipDragRulesTest` (11) hold the part that can be tested with no layout: a chip trades with a neighbour when its edge passes that neighbour's middle, chips of different widths never trade back and forth, and the edge speed. Everything else, the gesture above all, has never run: no long press, no drag, no auto-scroll, no drop. A screen reader gets two custom actions instead, also never tried. |
| Library opens on the first chip | UNIT TESTED | `ChipOrder.opening`; `LibraryQuery.filter` is null until Library has opened once, and `UiModel.openLibraryOn` sets it. When the first chip is Anime or Watchlist the screen waits for their lists and shows the skeleton meanwhile, so it does not open on one filter and jump. The rule is tested; the wait has not been seen. |
| Tab swipe lock | BUILT | `nav/TabSwipeLock.kt`, the file branch `tabs` defines too, written here with the content agreed for it. `LibraryChips` sets `locked` while a chip is in the air and frees only a lock it holds. On this branch nothing reads it. |
| Settings rows | BUILT | Settings › Library › "Library layout": "Folders view" (a choice of two) and "Reset order", which reads "Default" and does nothing while no chip was moved. Not seen. |
| Backup | UNIT TESTED | Two nullable fields at the end of `BackupSettings`. `BackupFormatTest` 41 to 45: the two round-trip and nothing else moves; a backup from before them, a choice never made and a name this build does not know keep what the phone holds. No restore has run on a phone. |

Not in this round: the tab bar and the Folders tab (branch `tabs`); a search of Folders from Library's own button, which always searches titles; a sort for the tree; a path search (only names are searched); Media Manager, which keeps its own list.

The gaps a phone must close are in `NEXT.md` section 3, and the owner checks are items 203 to 228 of section 2.

## This round: review fixes on the merged tree

An independent review of the merged tree (`28c7312`) found five things. All are fixed on `themes`. Nothing here has run on a phone, and this version is published without a phone test, so each row says what a test holds and what is only read through. No permission, dependency, manifest or schema change.

| Finding | Status | What changed |
|---|---|---|
| 1. The private delete sheet stayed open, with the video names, after the folder locked | UNIT TESTED | `VaultDeleteFlow` watches `vault.state` from its first confirmation on. When the folder locks, a plan being made, a plan waiting for an answer and a finished result are all dismissed; a running delete cannot be called back and ends with its message. The sheet also asks the folder before it draws: `VaultSheetRules.face` gives nothing for a plan over a locked folder, so even the frames of the sheet sliding away over the lock screen hold no name, count or size, and a delete that ends after the lock says only that it ended. `VaultDeleteFlowTest` 10 to 18. Checked beside it: selection mode and the card sheet of the private folder live inside the open-folder page, which leaves composition when the folder locks, so their names and counts go with it. One more leak of the same kind was there before this round and is closed: the move sheet, also at the root, named the file being moved out; it now shows the progress alone while the folder is locked (`VaultSheetRules.namesMove`, tested). Not seen on a phone. |
| 2. Deleting a private video could forget a title the app promises to keep | UNIT TESTED | "No file row in the library" was read as "no library file", but removing or excluding a folder deletes its file rows on purpose and keeps the title. `VaultDeleteRules.forgettable` now forgets a title whole only when it was never anything but private and is empty: no other private video, no library file row, no saved progress on any playable of it, no favourite or watchlist mark, no match or cover chosen by hand. Otherwise the title stays as it is and only what belonged to the deleted file goes (its progress went with its row; its frame, saved subtitles and track choices go unless a copy of the file remains). The check is made again inside the transaction that deletes. `VaultDeleteRulesTest`: the removed-folder series keeps its progress, each owner choice keeps the title, a mixed delete forgets only the empty ones. The database part is read through, not run. Waiver wording updated. |
| 3. Delete and Move to private did nothing for a picked folder of folders | UNIT TESTED | Media Manager lists only folders that hold videos directly. A picked folder now stands for every such entry at its path or under it (`CardMenuRules.foldersUnder`, `isUnder`; a sibling whose name only starts the same is not inside), so the confirmation sheet counts every video under it and its true size. With nothing to delete the delete sheet opens on «لم يُحذف شيء» (`DeleteFlow.nothing`), with nothing to move the move sheet opens on its "nothing moved" line (`VaultFlow.nothingToMove`), and the picks stay; before, the tap passed in silence and a move cleared the picks first. The long-press menu of a folder had the same gap (a folder of folders offered neither row) and uses the same rule. `CardMenuRulesTest` +4, `SelectionRulesTest` +4, `DeleteFlowTest` +2, `VaultFlowTest` +1. Not run. |
| 4. A confirmed private delete waited behind a running scan, and its cleanup was never retried | UNIT TESTED | `PrivateVaultImpl.delete` answers as soon as the videos and their rows are gone. The cleanup runs after it on its own, takes the scan lock itself, and no longer holds the sheet. Each deleted video is first noted in a small journal in app storage (`vault-cleanup.txt`: a title id and a file id per line, no name), and the line goes when the cleanup is done; `recover()`, which every start's scan runs before its walk, finishes whatever a kill left. Every step looks at what is there now, so a second run changes nothing. A journal that cannot be written does not stop the delete: that run cleans from memory. `VaultDeleteRulesTest`: the journal reads back, skips a line cut short, cleans a video noted twice once, and keeps lines added while a cleanup ran. The file and database work is read through, not run. |
| 5. Dragging a custom-theme slider built palettes on the main thread | UNIT TESTED | `ThemeSwatch` takes its first palette where it is asked and every later one off the main thread, conflated: while one is built the looks in between are dropped and the picture goes to the newest (`rememberSwatchPalette`: `snapshotFlow`, `conflate`, `Dispatchers.Default`). This also covers the colour field of the "any colour" accent. `FramePalettes.of` builds outside its lock, so a palette in work never holds up the one the screen asks for, and two threads asking for one look still get one instance (`ThemeContrastTest` +1). Applying on release is unchanged. Smoothness itself was not measured. |

## This round: more themes and custom colours

Owner request, built on branch `themes` on 2026-11-21 from the approved storyboard (`D:\FrameGlass\storyboards\views-themes\board.png`, sections 2 and 3). Waiver "2026-11-21: More themes and custom colours". Not installed and not seen on a phone, in either language, on any Android version. No permission, dependency, network host, manifest or schema change.

| Part | Status | Notes |
|---|---|---|
| Eight ready themes | UNIT TESTED | Black, Pure black, Midnight, Violet, Forest, Coffee, Light, Paper (`FramePalettes`, values in `design-tokens.md`). `ThemeContrastTest`: canvas, raised surface and accent equal the storyboard's; on all three surfaces of every theme, with its own accent, each of the 8 accents and 7 "any colour" values, text, secondary text, error, warning and the accent keep 4.5:1, a selected chip keeps its text, every pill carries its text, and rims show (hairline 1.1:1, soft outline 1.25:1, outline 1.7:1). Numbers only: nobody has looked at one. |
| Black and Light are the old looks | UNIT TESTED | Every colour of the old dark and the old light palette is asserted value by value, with the six old accent tones on both, and every dark and light glass surface ends on the tint it drew before. One known difference: on Light the stored limits sit 3 levels higher, which changes no tint because each base tint was already above them. |
| Custom theme, derivation | UNIT TESTED | `FramePalettes.customSpec`. The same rules as above over a sweep in the suite: hue every 30 degrees, 12 shades with both sides of the dark to light step, 5 surface tones, 5 accents each (3,600 palettes). The canvas runs from pure black to light and never rounds across the step; text is light exactly where the canvas is dark; a derived colour is a whole 8-bit ARGB and the same every time. A wider sweep was run once on the final code and is not in the suite: hue every 10 degrees, every shade step of 0.005, 6 surface tones, the theme's accent and one picked colour, 86,832 palettes, no failure. |
| Custom theme, the mid tones | BUILT | The shade slider steps from CIE lightness 36 to 58. Near the step the rule leaves little: accents almost white or almost black, secondary text at 80 to 90%, bars 0.8 to 0.95 opaque, sheets solid. It passes the tests and may look poor. `NEXT.md` asks the owner. |
| Accents | UNIT TESTED | Pink and cyan, the theme's own colour, and any colour. A tone moves toward the text colour until it reads; a pill keeps the picked colour where it stands 3:1 apart from the surfaces. Amber stays amber as a fill on Light and Paper. |
| Glass takes the theme's colour | UNIT TESTED | The tint was already the canvas. The limits and the tint level of the contrast rule now come from each palette (`glassLimit`, `glassLevel`), and the sampler takes the theme's tint level out of what it reads. Tested over grey backdrops 0, 64, 127, 191 and 255 for all six kinds, rows on sheets, the tab pill, and the flat sheet of Android 11 and 12. Not seen: whether tinted glass looks right over art, and the rims, which are still white on every dark theme. |
| Follow the phone | UNIT TESTED | `FrameLook.shownWhen`: the matching side shows the chosen theme, the other Black or Light, a custom theme sits on the side of its canvas, the accent goes with both. Not seen switching while the app is open. |
| Theme change fades | BUILT | `FrameTheme` fades every colour over 200 ms by handing a new palette to the whole tree each frame, with no activity recreation; the first load of the saved look and degraded mode cut. One test covers the two ends of the blend. Not seen, and the cost of those frames is not measured. |
| Appearance page | BUILT | Preview, eight cards four to a row, ten accent dots in two rows, the custom theme (picture, three sliders, hint, reset), then Follow the phone and Cover tint. Never drawn: card and dot sizes on a narrow phone, long Arabic labels, the sliders under a finger, RTL. A slider changes the small picture while dragged and the app when released. |
| Any colour sheet | BUILT | Hue and saturation field in a glass sheet, a picture of the result, one button. Never drawn or touched. |
| Settings file | UNIT TESTED | `SettingsPrefsTest` (9): every setting round-trips, every theme with every accent, an old file migrates on read and the first save makes it lasting, a damaged value falls to the default. Runs on the JVM against the same `Preferences` type, not on a phone's file. |
| Migration | UNIT TESTED | `ThemeMigrationTest` (10): DARK to Black, LIGHT to Light, SYSTEM to Follow the phone over Black and Light, old WHITE to the theme's own colour, a chosen colour kept, unknown values to the default. |
| Backup | UNIT TESTED | `BackupFormatTest` (+5): the theme, switch, accent and four custom values round-trip; a backup with none of the new fields restores the look it had and leaves the phone's custom values; out-of-range values are brought into range; a bad theme name is rejected before any write. |
| Player, tour and widget stay dark | BUILT | By construction and not by a test: the player calls `FrameTheme()` with no look, the tour uses `DarkLook`, the widget is XML. Not seen with a light or custom theme behind them. |
| Settings icon tiles | UNIT TESTED | `IconTileTest`: the white glyph reads on every fill; on Black and Light no tile changes; on a theme whose card comes within 2.5:1 of a fill the tile takes a 1 dp rim. Not seen. |
| Merged with `views`: the settings file and the backup | UNIT TESTED | The four view sizes are read and written by `SettingsPrefs` (`readViews`, `writeViews`) beside the theme keys, and `BackupSettings` restores both (`withViews` over the theme fields). `SettingsPrefsTest` +4: every size of every place round-trips with one key of its own, a cleared size leaves no key, an unknown name is no choice, and an old file migrates its look and chooses no size. `ViewSettingsTest` and the view tests of `BackupFormatTest` pass unchanged. |
| Merged with `views`: selection, view sizes and the private folder on every theme | UNIT TESTED | Their screens compiled against the new theme API with no change: they read `FrameTheme.colors` only. One change was made for the contrast rule: the bar of actions of selection mode and Media Manager is `GlassKind.ActionBar`, the tab bar's recipe held to the limit of the error colour, because its delete labels are drawn in it. `ThemeContrastTest` +2 and one extended: on every ready theme and the custom sweep, text and secondary text keep 4.5:1 on a row washed with 12% of the accent (secondary text now counts that row when it picks its strength), the row rim shows, the check reads on its disc and the disc or the check stands 3:1 from its ring, and plain and error labels keep 4.5:1 on the bar over backdrops 0 to 255. The size chip and the folder tile are text on the raised surface, and the lock on Home is the glass button "Add a place" already was: both were covered. Not seen on any theme. |
| Hard-coded colours | BUILT | A search by reading, with no test behind it. Searched `:app`, `:glass` and `:player` for `Color(0x`, `Color.White`, `Color.Black`, colour ints and XML colours. What is left is on art, in the player, in the tour, brand colours, shadows and the widget, which keep their look. Moved to the theme: the contrast limits and tint level, which were constants of the dark and light looks. |

Not seen, and worth the first look on a phone:

- Any theme on Home, Library, Details, Search, Settings, a menu and a sheet. Screens were not opened one by one in the code either: they read tokens, and a screen that draws its own colour over a surface would be missed by the tests.
- Details and the hero fade art into the canvas with stops tuned for near-black and for off-white. On Midnight, Coffee or a mid-tone custom canvas the fade, the title over it and the cover glow may sit wrong.
- The glass rims and shadows are the dark or the light set. On a mid-tone custom theme either set may look heavy or vanish.
- Pure black: the raised surface is `#121214`, 1.1:1 from the canvas. Chips and fields may be hard to see on a dim screen.
- A light or custom theme starts from the dark launch window, as Light always has.
- Android 11 and 12, where every one of these themes is flat tint and rim.
- Selection mode, the five view sizes, the delete sheet of the private folder and the lock on Home on any theme but the dark one their branch was built against.

The owner checks are items 186 to 202 of `NEXT.md` section 2.

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

## This round: private folder shortcut on Home

Owner request of 2026-11-21 («فوق على اليسار»), built on branch `views` as a commit of its own. No permission, dependency, manifest or schema change, and no new string: the button says `app_settings_private`, «المجلد الخاص» / "Private folder", which both languages already had.

| Part | Status | Notes |
|---|---|---|
| The lock button | BUILT | `PrivateFolderButton` in `HomeScreen.kt`: the same `GlassIconButton` as "Add a place" (`Icons.Rounded.Lock`, same size, same glass over the hero, same status bar inset and 12 dp margin), at the top start: left in English, where the owner asked for it, and right in Arabic, so the two buttons keep opposite corners. It shows wherever "Add a place" shows, the empty Home included. A tap is `navigator.push(Route.PrivateFolder)`, the call the Settings row makes, so it asks for the fingerprint or the password, or opens the setup when no password exists. No badge and no count. Not seen. |
| The scan count | BUILT | The chip that counts a long scan sat alone at the top start. It now sits beside the lock in one row, which stops short of "Add a place", so a long count is cut before it reaches that button. Not seen: the three together on a narrow screen or at a large font. |

Brief R13 asks that new capability not crowd Home. This is one icon the owner asked for by place, mirrored against the one already there; nothing else was added.

## This round: delete inside the private folder

Changed the same day by "This round: review fixes on the merged tree", which is the current word where the two differ: a title is forgotten whole only when it was never anything but private, the delete answers before its cleanup, and the sheet closes when the folder locks.

Owner decision of 2026-11-21, built on branch `views` as a commit of its own. Waiver "2026-11-21: Delete inside the private folder". Not installed and not seen on a phone. No permission, dependency, network host, manifest or schema change; `TitleDao.deleteAliases` and `MetaDao.deleteSegments` are new queries on existing tables.

| Part | Status | Notes |
|---|---|---|
| The plan | UNIT TESTED | `PrivateVault.planDelete` (`VaultDeleteSource` in `data/api/ApiVaultDelete.kt`) and `VaultDeleteRules`. `VaultDeleteRulesTest` (12): a plan counts every video of the asked cards and the subtitles with them, in the order asked, with their size; a card asked twice counts once; nothing outside the asked cards is ever a target; a video moved in after the plan stays, and one that left meanwhile is not a target. Refused while the folder is locked. |
| Nothing without the confirmation | UNIT TESTED | `VaultDeleteFlow` in `:app` has one way through: `ask` only plans, and only `confirm` on a plan that is on screen deletes. `VaultDeleteFlowTest` (10): asking deletes nothing; confirm with no plan on screen is a dead button; cancel leaves every video and a late tap after it deletes nothing; a confirmed delete runs once; a running delete cannot be dismissed or asked again; a folder that is locked, or that locks before the confirmation, deletes nothing; picks stay when nothing could be deleted. |
| The sheet | BUILT | `VaultDeleteSheet` at the root, beside the other two. Title «حذف فيديو واحد نهائيًا؟» with Arabic plurals; the name in full for one video, the first three and «و N أخرى» for several; the count, the subtitles with them, the size; «حذف نهائي — ما يرجعش.» in the error colour over one line that says why; Cancel as the strong pill and «حذف نهائي» as the red one. Back, the scrim and a drag cancel. Not seen, in either language or theme. |
| Where it is offered | UNIT TESTED | The card sheet of a private video (last row, «حذف نهائي» with «ما يرجعش» under it), and the selection bar of the folder, at the end in the error colour (`SelectionAction.DELETE_PRIVATE`, a different action from the library delete; `SelectionRulesTest` +2 and one changed). Picks stay until that delete has run. The rows and the bar themselves are not seen. |
| The delete | BUILT | `PrivateVaultImpl.delete`: under the lock of the moves, not cancellable once started. Per video: the stored file first, then its subtitles, then its two rows in one transaction, so a stop in between leaves a card that says its file is missing and never a hidden file. A file that will not go is counted as failed and keeps its row. Read through only: no file has been deleted by it. |
| What is cleaned after | BUILT | Progress lives in the vault row and goes with it; private playback never writes the last-session row, and Home never lists a private video, so nothing can dangle there. For a file with no copy left in the folder or the library: its frame, the subtitles saved for it and its track choices (`VaultDeleteRules.freeFileIds`, tested). For a title left with no file anywhere (`orphanTitles`, tested): progress, series preferences, watch log, skip times, cast and genre links, ratings, the lookup queue, episodes, aliases, the search index, the title row, its picture folder and a cover the owner chose (`forgetTitle`, one transaction, with no scan running). A title that still has a file keeps everything. The database part has not run. |
| Backup | BUILT | A private video never had rows in a backup. The title left behind by a deleted one would have: it is no longer private-only, so the filter of `BackupManager` would let it through. Deleting the orphan title is what keeps its name out. Read through, not run. |
| A private video that is playing | BUILT | The delete reports the stored paths through the same `DeletedLog` Media Manager fills, and `PlayerSession` already closes on a path it is playing (`DeletedFiles.holds`, by path). A late position save finds no row and does nothing. Not seen. |

## This round: view sizes

Owner request of 2026-11-21, built on branch `views` (cut from `select` at `279b8e3`) after the storyboard `D:\FrameGlass\storyboards\views-themes\board.png`, section 1. Waiver "2026-11-21: View sizes". Not installed and not seen on a phone, in either language or any theme. No permission, dependency, network host, manifest or schema change.

| Part | Status | Notes |
|---|---|---|
| Five sizes | UNIT TESTED | `ViewSize` in `data/api/ApiViews.kt`, smallest first. `ViewRules` and `ViewRulesTest` (16): posters go 1, 1, 4, 3, 2 to a line and 16:9 frames 1, 1, 3, 2, 1; a larger grid never holds more; what each size says (`CellFace`); one content type per look, and a title cell is never reused for a folder. Only the numbers are tested: no size has been drawn on a phone. |
| What a cell shows | BUILT | `views/ViewCells.kt`, one `TitleCell` and one `FolderCell` for all five. Compact list: a 46 x 34 dp tile, the name, and one line (year, genre, state and the rating badge for a title; the count and a size chip for a folder). List: the row as it was, now with the genre, and a 16:9 thumbnail for videos. Small grid: the poster alone, or a frame with a small title on it; no rating badge. Medium grid: the card as it was. Large grid: a larger title and the year with the first genre. Progress line, watched mark and the mark of a picked item at every size. `PosterCard` and `LandscapeCard` in `:glass` take a `CardDensity`, medium by default, so Home and Details are untouched. Colours are `FrameTheme.colors` only. Not seen. |
| The view button and its sheet | BUILT | `ViewButton(place)`: its icon is the size in use (`ViewAgenda`, `ViewList`, `Apps`, `GridView`, `ViewModule`), and it opens the glass sheet «شكل العرض» with the five rows and a check on the current one. In Library (every filter; it replaces the grid/list toggle and now shows on Folders too), the folder pages, the row, genre and person pages, beside the search field while titles are listed, and in the private folder beside the lock. Media Manager has none. Not seen. |
| Remembered per place | UNIT TESTED | `ViewPlace`: Library, Folders (the chip and every folder page), the private folder, and the list pages with Search. Four nullable fields at the end of `AppSettings`, stored by `SettingsStore` under `viewLibrary`, `viewFolders`, `viewPrivate`, `viewLists`. Null is "never chosen" and shows the default of the place: medium grid, except Folders, which were a list and now open as the compact one. `ViewSettingsTest` (9). The old list toggle of Library lived in memory for one session and was never stored, so there was nothing to migrate: an old install opens Library on the medium grid, as every launch did. DataStore itself has not run with these keys. |
| Backup | UNIT TESTED | Four nullable names at the end of `BackupSettings`. `BackupFormatTest` +4: the sizes round-trip place by place and nothing else moves; a backup from before them, a place with no size and a name this build does not know all keep what the phone holds. No restore has run on a phone. An older build cannot read a backup that holds the new fields, as with every field added before. |
| Genre on a card | BUILT | `TitleCard.genre`, the first genre, null by default, filled in `TitleInfo.card()`. A clip or an unmatched title has none and shows the year alone. |
| Selection at every size | BUILT | Every cell carries `selectableItem` and `selectionMark`; the check shrinks to fit the compact tile. `SelectionScaffold` gained `selectingActions`, and each screen passes its view button, so the size can change while selecting; the picks live in `SelectionState` and do not depend on the layout. Not seen. |
| Performance | BUILT | Read through, not measured: stable keys (`id`, folder path), one content type per look, one grid (`ViewGrid`) whose state belongs to the screen, so a change of size keeps the first visible item. Pictures were already decoded at the size shown: `CoverImage` gives Coil no size, so it takes the one of the layout, and a 4-column grid asks for a 4-column bitmap. No frame time was measured at any size. |

Not in this round: Media Manager, Home rows and the episode list keep their one look; the genre is not searched or sorted by.

The gaps a phone must close are in `NEXT.md` section 3, and the owner checks are items 168 to 179 of section 2.

## This round: multi-select

Owner request of 2026-11-21, built on branch `select` (cut from `main` at `4df4b9c`): «إضافة select عند الضغط المطول للفيديوات لكي يتم تحديدهم». Not installed and not seen on a phone, in either language or theme. No permission, dependency, network host, manifest or schema change, and no rule of the brief changes: the row sits behind the long-press menu (R13), and every delete still opens the confirmation sheet first (waiver of 2026-11-07), so `WAIVERS.md` has no new entry.

| Part | Status | Notes |
|---|---|---|
| «تحديد» in the long-press menus | BUILT | `SelectRow` in `CardMenu.kt`, the second row, right under «تشغيل في نافذة منبثقة» (the first where nothing can play): the title menu, the menu of a row in Folders, the episode sheet on Details and the card sheet of the private folder (there under «تشغيل»). It closes the sheet and starts selection mode on the screen behind it with that card picked. Not seen. |
| Home | BUILT | Decision of this round: «تحديد» is not offered on Home (hero, Continue watching and the rows), nor on the collection rail of Details. Those are horizontal previews: they sit outside any `SelectionScaffold`, so their cards keep the menu they had. A row's "View all" page and Library do select. `NEXT.md` section 5 asks the owner whether that is enough. |
| Rules | UNIT TESTED | `SelectionRules` and `SelectionRulesTest` (35): picking, select all, dropping picks that left the list; what a title card, a Folders row and an episode are as a pick, and which cannot be picked (a video row the library lost, an episode with no file); which actions each place offers and in what order; a mixed selection of films, series, clips, folders, files, episodes and items with nothing on storage; the watched button turning to «غير مُشاهَد» only when everything picked is watched; the bar's five places with Delete kept at the end and the rest behind «المزيد»; the entries a delete asks the sheet about and what a move into the private folder takes, both skipping what is not on storage and never reaching a private-folder item. |
| State holder | UNIT TESTED | `SelectionState<T>`, one per screen, knows nothing about grids or cards. `SelectionStateTest` (10): the mode starts with the picked card, the last pick let go ends it, «تحديد الكل», «إلغاء الكل» (the mode stays), Back and × (the mode ends), and a list update that takes the last pick away ends it. |
| Pickable cards | BUILT | `Modifier.selectableItem(item)` and `Modifier.selectionMark(item)` in `select/SelectionUi.kt`, on `TitlePoster`, `TitleFrame`, `TitleListRow`, `FolderRow`, `EpisodeListRow` and the private folder's posters. While the screen selects, a tap toggles instead of opening, a long press that ends on the card toggles too, and no menu opens. A picked picture dims toward the canvas and takes a 2.5 dp rim in the accent and a check on the accent fill in its middle; a picked list row also takes the 12% wash and rim of a selected filter chip. Colours are `FramePalette` tokens only. Not seen: the tap is taken by consuming the press before the card sees it, the way the long-press watcher works from outside; that has never run on a phone, and neither has the look in light, in dark or with any accent. What a screen reader activates on a picked card is not checked. |
| Screens | BUILT | `SelectionScaffold` wraps Library (All, Movies, Series, Anime, Videos, Favourites, Watchlist as grid, 16:9 frames and list; Folders rows), the folder pages, the row, genre and person pages, Search, the episode list of Details and the private folder. It swaps the screen's top bar for ×, the count (the existing `app_media_selected` plurals) and «تحديد الكل» / «إلغاء الكل», raises the action bar, takes Back first, and drops picks that leave the list. On the two tabs the tab bar steps aside while selecting (`SelectionChrome` in `TabsScreen`). A Library filter change clears the picks; another season on Details does too, because the season in view is the list. On Search the bar stands where the field was and the typed words are kept. Not seen. |
| Actions | BUILT | Icon over a short label, in the glass pill Media Manager uses. Titles: «حابب نشوفه», «المفضلة», «مُشاهَد» / «غير مُشاهَد», then «إخفاء» and «نقل إلى المجلد الخاص» behind «المزيد», and «حذف» at the end in the error colour; the Favourites and Watchlist filters (and the watchlist row's page) lead with «إزالة». Folders: private folder and delete. Episodes: watched, private folder, delete. Private folder: «نقل خارج المجلد» only. A button that does not apply is dimmed. Each runs through the calls the card menu already uses (`setWatchlist`, `setFavorite`, `setWatched`, `removeFromLibrary`, `VaultFlow.moveIn`, `DeleteFlow.ask`), one item after the other; delete only opens the sheet with every picked entry, and the mode ends when that delete has run, so a cancelled sheet keeps the picks. Never run. |
| Several videos out of the private folder | UNIT TESTED | `VaultFlow.moveOut(List)`: one move after the other as one tracked move, counted together in the final message, failed only when nothing left. `VaultFlowTest` +4. No move has run. |
| Media Manager | BUILT | Its own `Selection` class is now `SelectionState<MediaEntry>` and its bar the shared `SelectionBarSurface`. Read through: the same calls with the same results. Not seen. |

Not built in this round:

- **Delete inside the private folder** was left out of that round and built the same day on `views`. See "This round: delete inside the private folder".
- **Exclude folder, fix match, change cover and "Play in pop-up"** stay single-card actions in the long-press menu.

The gaps a phone must close are in `NEXT.md` section 3, and the owner checks are items 152 to 167 of section 2.

## This round: developer name, delete from Home, play in pop-up

A read-only review of the merged branch on 2026-11-19 found nothing serious and four things to fix, all fixed the same day on `android10` and none seen on a phone. The last four rows of the table are those fixes.

Owner requests, built on branch `features` on 2026-11-19. Waiver "2026-11-19: Developer name, delete from Home, play in pop-up". Not installed and not seen on a phone, in either language. No permission, dependency, network host, manifest or schema change.

| Part | Status | Notes |
|---|---|---|
| Developer name on About | BUILT | One line under the app name and version: "Developed by Mutasim Suliman", «تطوير: Mutasim Suliman». The name is a constant in Latin letters inside bidi isolates. `README.md` has the same line in both languages under Credits. Not seen. |
| Long-press menu on every Home card | BUILT | Read through the code: Continue watching, the Watchlist row, the automatic rows and the Videos row already had it (`cardMenu` in `TitlePoster`, `TitleFrame` and the Continue card). The hero had none and now has the same menu (`HeroPager`), which watches from outside as on the cards, so the Play pill, the tap to Details and the swipe stay as they were. Not seen: whether a long press on the hero competes with the pager swipe or the pull to refresh. |
| «حذف هذه الحلقة» and «حذف المسلسل كله» | UNIT TESTED | `CardMenuRulesTest` (18): a film or a clip keeps one «حذف»; a series or anime reads «حذف المسلسل كله» on every card; a series' Continue card, and the hero when it stands for that card, also has «حذف هذه الحلقة» with the S/E label under it (the episode title when it has no number); a title with no file on storage has no delete, no private-folder row and no pop-up row. The episode's entries are the available files of its playable key from `LibraryRepository.versions`, each as a `file|rowId` entry, never the title. Both rows only call `DeleteFlow.ask`, so nothing goes without the confirmation sheet (`DeleteFlowTest`). No delete has run from these rows on a phone. |
| Home updates after a delete | BUILT | Read through the code, not run: `MediaManagerImpl.delete` marks the rows unavailable in `MediaFile`, Room invalidates `SnapshotSource.flow`, `LibraryRepository.home` rebuilds, and `HomeBuilder` shows only titles with an available file; `ResumeLogic.pick` skips an unavailable episode, so the Continue card moves to the next one. The reload waits at most the 400 ms throttle. |
| «تشغيل في نافذة منبثقة», the row | UNIT TESTED | First row of the long-press menu of every title card (Home, Library, Search, the row, genre and person pages), the hero, a Continue card, an episode row on Details (`EpisodeSheet`) and a file row in Folders. `CardMenuRulesTest`: it plays what a tap would, the card's own item on a Continue card and otherwise `TitleDetail.play` (the resume item, or the first thing to play), and it hides when nothing is on storage. Not offered inside the private folder, whose cards have no card menu. |
| «تشغيل في نافذة منبثقة», the player | UNIT TESTED | Pure parts only. `PlayerRouteTest` (7): the `popUp` extra round-trips with every kind of `PlayRef` through the plain values behind the intent, is absent from a normal play, and never counts with nothing to play. `PopUpTest` (13): the window shape is the video's own, held inside 0.42 to 2.38 as before (`PopUpShape`), and `PopUpGate` waits for the picture of the video asked for, not the one before it, enters at once for the video already playing, treats sound only as nothing to wait for, and holds the landscape request until the window is a pop-up or stays full. In `PlayerActivity`: the first size or frame of the new item calls `enterPictureInPictureMode` with the usual params; controls stay hidden and the orientation stays unspecified until then; a missing `FEATURE_PICTURE_IN_PICTURE`, the app-op turned off, a `false` or an exception, a failure of the video, 8 s with no picture or 2 s with no window all leave a normal full-window player, with the player's short message where the pop-up was refused. A pop-up already on screen takes a second request in place through `PlaybackHost.popUp`, with no new window. A restore after the app was killed opens the normal player. Position saves, Continue watching and watched rules are `PlayerSession`'s and were not touched. **Not seen: none of this has entered picture-in-picture on any device.** |
| Deleting the video that is playing | UNIT TESTED | Reachable since the pop-up floats over Home. `MediaManagerImpl.delete` reports the rows and paths it removed (`MediaManager.deletedFiles`, through `DeletedLog`) and clears the last-session row when it points at one of them. `PlayerSession` listens: if its item is among them (`DeletedFiles.holds`, by file row or by path) it drops the pending position, closes, and the window finishes as at the end of a video; with background audio and no window it stops the service. `PlaybackRepository.savePosition` refuses a save for a deleted row that was already on its way, until that file opens again. `DeletedFilesTest` (4) and `DeletedLogTest` (4) cover the rule and the log; the closing itself is not run. The app module is not involved. |
| Pop-up waits end with the window | UNIT TESTED | The 8 s wait for a picture and the 2 s wait for the window are two fields of `PlayerActivity` now, removed in `onDestroy`, by a new request and by a normal open; `dropPopUp` and `giveUpPopUp` do nothing on a finishing or destroyed activity. Rules in `PopUpGate` (`timedOut`, `replaced`), `PopUpTest` +3. |
| The 2 s give-up cannot misfire | UNIT TESTED | It acts only while the request still waits for the window and the window is not a pop-up: a pop-up whose report is late gets no message and no turn of the screen, and a normal open that replaced the request (`onNewIntent`) cancels the wait. |
| The pop-up extra counts only from this app | UNIT TESTED | `PlayerActivity` is exported for "Open with". The value of the `popUp` extra is a random string made once per run of the app (`PlayerRoute`); any other value, a Boolean included, plays as a normal intent. `PlayerRouteTest`: `aPopUpExtraFromAnotherAppPlaysNormally` replaces the old wrong-type test. |

The gaps a phone must close are in `NEXT.md` section 3, and the owner checks are items 140 to 151 of section 2.

## This round: feature tour

Owner request with an approved storyboard, built on branch `tour` on 2026-11-12. Waiver "2026-11-12: Feature tour". Not installed and not seen on a phone, in either language, theme or font scale. No permission, dependency, network host or schema change. Code in `app/.../tour/`; it uses no API above 33 (the blur of page 4 is `RenderEffect`, API 31), for the branch that lowers minSdk to 33.

| Part | Status | Notes |
|---|---|---|
| Which flow shows it | UNIT TESTED | `TourFlowTest`: a fresh install (`onboardingDone` false) opens on `Route.Tour(setup = true)`, an install that finished onboarding opens on Tabs, the first-run tour hands over to `Route.Onboarding` on «يلا نبدأ» or «تخطي», the one from Settings goes back. `RoutesTest` round-trips both routes; `AppLinksTest`: an intent link never opens over the first-run tour. Back goes to the previous page; on page 1 it leaves the tour as on any page, which on the first run closes the app with onboarding still to do. |
| Posters from the owner's library | UNIT TESTED | `TourPostersTest`: at most 6, rated first in the order given, clips (ids of the Videos list) and private-folder items (by id and by cover path) never shown, only covers that are files on the phone (checked on IO, only until the slots are full), one cover shown once, drawn placeholders fill the slots left with their own designs. A cover may be a video frame for a title with no poster. Loading is capped at 3 s; placeholders stay if it is slower. Page 5 swaps from the second cover to the first, or to drawn ones. |
| Pages, motion, Settings entry | BUILT | Six pages in a `HorizontalPager` (right to left in Arabic), the storyboard's lights, app-icon layers, filter chips, `RatingBadge`, `ProgressLine`, `ColorIconTile` rows, player icons and the player's own «تم تحميل ترجمة عربية من …» and «غيّرها» strings. Always dark (`DarkLook` in `:glass`, which changes only `FrameTheme.colors`). Entrances: cards and rows fade and rise in turn, page 4's next episode picture clears once while the other stays blurred, page 5's pick swaps once; none under section 7.9. Settings › App › «جولة في المميزات» (sky tile). No UI test; nothing seen on screen, so the fit of the illustrations on the S26 Ultra and at font scale 1.3 is unchecked. |

The first-run path cannot be seen on the owner's phone: it has finished onboarding, and the app is never uninstalled or cleared. The `qa` build starts fresh and would show it.

Build on this PC on 2026-11-12, branch `tour`, with the build cache: `testDebugUnitTest`, `assembleRelease` and `:app:lintRelease` pass. Log: `D:\FrameGlass\logs\build-tour.log`. Unit tests: 1,170 run, 1,169 passed, 0 failed, 1 skipped (`AnimeIndexTest`): data 526, net 353, engine 75, player 99, app 84 (13 new: `TourPostersTest` 8, `TourFlowTest` 4, one in `AppLinksTest`), glass 33. `:parser:test`, `assembleQa`, `fg.ps1 build` and `tools/check_update.py` were not run; no schema changed. `:app` lint: 0 errors, 10 warnings, none in a file of this round. Release APK 23,508,053 bytes. Nothing was installed.

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
| Delete inside the private folder | UNIT TESTED | Waiver of 2026-11-21. `VaultDeleteRulesTest`, `VaultDeleteFlowTest`. No private video has been deleted by it. See "This round: delete inside the private folder". |
| Private folder | UNIT TESTED | `VaultFilesTest`, `VaultAuthTest`, `VaultRulesTest`, `VaultFlowTest`. No move, biometric prompt, FLAG_SECURE or recovery has run. |
| Themes and colours | UNIT TESTED | See F-U12. |
| Favourites, hide title, exclude folder | UNIT TESTED | `FavoritesFilterTest`, `HiddenTitlesTest`, `ExclusionsTest`. The long-press menu has no test, and `:glass` cards have no long press, so `CardMenu` watches from outside. |
| Swipe to refresh | UNIT TESTED | `RefreshFlowTest`, the swipe tests in `data/src/test/.../scan/`, `LockWithinTest`. Live stages and an exact summary replace the before-and-after count. The pill has no UI test. |
| Private items in the player | BUILT | Code review only: FLAG_SECURE, recents, blank notification metadata, no screenshots, background audio or online search. |
| Launcher shortcuts and the Continue watching widget | UNIT TESTED | Waiver of 2026-11-08. See "This round". Never run. |
| Next episode date, trailer, tonight's pick | UNIT TESTED | See "This round". Never run. |
| Watchlist, cleanup suggestions, stats, automatic backup | UNIT TESTED | Built on `pack-library`. See "This round: watchlist, cleanup, stats and automatic backup". Never run. |
| Feature tour | UNIT TESTED | Built on `tour`. See "This round: feature tour". Never run. |
| Developer name, delete from Home, play in pop-up | UNIT TESTED | Built on `features`. See "This round: developer name, delete from Home, play in pop-up". Never run; picture-in-picture entry is untested on any device. |
| Five view sizes behind the view button | UNIT TESTED | Built on `views` (2026-11-21), waiver of the same day. Columns, what a size shows, the stored choice and the backup are tested; no size has been drawn on a phone. See "This round: view sizes". |
| Multi-select from the long-press menu | UNIT TESTED | Built on `select` (2026-11-21). The rules and the state are tested; the cards, the bars and every bulk action have never run. See "This round: multi-select". |

## Gates

| Gate | State |
|---|---|
| 0 | Not passed. Matrix 50 of 53 Core and the delay proof are done, see `phase-0-results.md`. Soak, glass proof, the full C2 run and the owner checks are open. |
| 1 to 7b | Not run. App work went ahead under the 2026-11-07 waiver. |
| 8 | Not needed so far. Decided at Gate 0. |
| 9 | Not started. |
