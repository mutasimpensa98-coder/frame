# Waivers

Only the owner can waive a gate item (R10).

## 2026-11-07: Gate 0 no longer blocks app work

The owner asked to speed up and start on the app ("سرع العملية وابدأ في التطبيق").

- Phase 1 and the app screens start now, before Gate 0 is complete.
- UI work is allowed before Gate 0 passes, which R10 would otherwise forbid.
- Gate 0 itself is not waived. Its checks keep running beside the app work, and the owner still reviews them.
- If a Gate 0 result forces an engine change, the app work adapts. `:player` only talks to `FramePlayer`, so that change stays inside `:engine`.

## 2026-11-07: the app finds videos by itself

The owner asked that the app search the whole phone and show every video, with films and series in the same cards, rating and progress bar. This replaces "Scan only folders the owner adds" in brief section 4.6. The + button stays for extra places such as a USB drive.

## 2026-11-07: Media Manager and file deletion

The owner asked for a Media Manager section in Settings that shows the size of folders and videos, and asked to add deletion with a confirmation before every delete. This replaces "File rename or delete from inside the app" in brief section 8, for delete only. Rename stays out.

## 2026-11-07: Private folder

The owner asked for a private folder: videos can be moved into it, and only a password or the owner's fingerprint opens it. This replaces "private vault" in brief section 8, and adds the USE_BIOMETRIC permission and androidx.biometric to sections 4.10 and 4.2.

## 2026-11-07: Themes and colours

The owner asked for customisation in Settings: colours and themes, with light, dark or follow the phone. This replaces F-U12 "One look" and the "no theme choice" line in the brief. The default stays dark, and the player stays dark in every theme.

## 2026-11-07: GitHub backup

The owner asked to upload the project to their own GitHub account and leave it there, and said they might publish it one day. This replaces "Never upload, push, publish or sync" and "No GitHub or any git remote" in R1, for one repository under the owner's personal account, set to private. Making it public stays the owner's decision.

Because it may go public later, the history stays clean from the first commit: no signing material, API keys, device serials, phone screenshots, the owner's video names, Nuvio reference images or test media.

The rest of R1 stands: no team-knowledge, no `knowledge_sync.py` or `skills_sync.py`, no lessons inbox, no skill, no hosted page or Artifact.

## 2026-11-07: APK on GitHub

The owner asked that each finished version be uploaded as soon as it is ready, so they can download it from GitHub Releases and update the app themselves. `.\fg.ps1 release` publishes it. Releases are not part of git history. Before the repository ever goes public, decide which ones stay, because an APK carries GPL-3.0 duties.

## 2026-11-08: TMDB and IMDb switches

The owner asked for an on/off switch for TMDB and for IMDb on the keys page, each keeping its saved key, and for OMDb to identify new titles by name and year while TMDB is off, with the poster, plot, genres, cast names, runtime and IMDb rating, and episode titles and ratings for series. This changes the brief as follows:

- F-M10 "Never use OMDb posters" no longer holds for a title matched through OMDb. Its poster comes from OMDb's poster host, `m.media-amazon.com`, which joins the allow-list of section 4.9. The IMDb switch blocks that host with `www.omdbapi.com`.
- F-M10 "Show the TMDB rating by default": with TMDB off, no TMDB rating is shown anywhere, and cards show the IMDb rating where one exists.
- F-M1 "Search TMDB": while TMDB is off or has no key, OMDb matches instead. R2 still holds: automatic OMDb lookups send only the parsed title, year and kind, IMDb ids and season numbers. Fix match sends what the owner types, as it does to TMDB.
- F-M5 and F-M14: a title matched through OMDb has no backdrop, title logo, cast photo, character, collection, episode still or Arabic text. Hero and Details show its poster whole instead.
- F-M11 is TMDB's rule. OMDb data is refreshed on the same clock but never purged by it.

Turning TMDB back on looks up each OMDb title by its IMDb id, and keeps progress, favourites, manual covers and manual matches.

## 2026-11-08: Subtitle search with no key

After trying 1.0.104 the owner said there is no online subtitle search. They have no subtitle key, and the search row hid when no key was saved. This changes the brief as follows:

- Section 4.9 "Do not use keyless aggregators" no longer holds for Gestdown, a public API in front of Addic7ed that needs no key. `api.gestdown.info` joins the allow-list. Podnapisi was the first choice, but its hosts had no DNS records on 2026-11-08. The Wyzie risk stands: if Gestdown starts asking for a key or goes away, keyless search for series stops, and the keyed sites still work.
- Gestdown holds TV episodes only. Films still need a key. Arabic there was thin in the probes of 2026-11-08.
- R2 still holds: only the show name, season, episode and language go out, never the release name or the hash. Clips and private-folder videos never reach any site, and Online features off still means no call.
- F-S9: the "Search online" row always shows in the subtitle sheet, dimmed with a reason when it cannot search.

## 2026-11-08: Shortcuts and widget

The owner asked for a Continue watching widget on the home screen and for shortcuts on the launcher icon. This replaces "widgets" in brief section 8, for these two only:

- Launcher shortcuts: dynamic shortcuts for the F-S10 resume item and up to two more Continue watching items, each opening the player at its saved place. No pinned or static shortcuts.
- One widget, "Continue watching": up to three items with cover, title, episode label and progress line. A tap resumes in the player, and with nothing to resume it opens the app. Plain `RemoteViews`, no Glance or other new dependency.
- Neither needs a new permission. Clips and private-folder videos never appear in either.
- In the same request the owner asked for the next episode date and a trailer button on Details, and a "What should I watch tonight?" pick behind a menu. The trailer opens `www.youtube.com` in the YouTube app or the browser with `ACTION_VIEW`. The app's own HTTP client gains no host, and the button hides while Online features is off.

## 2026-11-08: Automatic subtitle download

The owner asked that a video with no Arabic subtitle gets one by itself, without a tap. This changes F-S9 "On tap" as follows:

- When a video starts and has no full Arabic subtitle, embedded or beside it (F-S7), the app searches once for that file in the background, downloads the best Arabic result, saves it per `fileId` like a tapped download, loads it without stopping playback, and runs the on-device sync as a tapped download does. A small note in the player names the site and offers «غيّرها», which opens the online results.
- What goes out is decision D9's automatic set: the parsed title, year, season, episode and IDs. Never the release name, the file hash or the file size, whatever their two switches say. The local file name may order the results on the phone and is never sent.
- Only sites whose quota is not spent on a tap: SubDL and SubSource when their keys are saved, and Gestdown, which needs no key and holds series episodes only. OpenSubtitles stays tap-only, because its 5 free downloads a day belong to the owner's taps.
- Clips and private-folder videos are never searched, and Online features off still means no call. A video whose subtitles the owner turned off is left alone, and so is one with a subtitle file beside it in no stated language, because the player already shows that file.
- Each file is tried once, found or not. A network failure or a used-up limit leaves it to the next play. Failures are silent, as section 4.9 asks. The manual search still works any time.
- Settings › Subtitles › «ترجمة تلقائية من النت»: Off, Arabic (the default), or Arabic then English. English is fetched only when no Arabic exists and the video has no English subtitle of its own. The choice is in the backup.

## 2026-11-12: Dual subtitles and A-B loop

The owner asked for both. Brief section 8 lists them as "Later candidates", so this moves them into this round. Both sit behind menus, as R13 asks, and need no permission, dependency or network host.

- Dual subtitles: the subtitle sheet gains «ترجمة ثانية». Any other text track can show at the same time as the main one: embedded, a sidecar, or one downloaded or added during play. Media3 renders one text track, so the app reads the second one itself (`TextTracks` in `:engine`) and draws it on a cue overlay of its own, smaller, at the top of the picture or just above the main subtitle. An ASS second track shows its words only. Bitmap tracks (PGS, VobSub, DVB) are not offered. An embedded second track is read from the file on a background thread, from the start onward; nothing is written and nothing leaves the phone. Its clock follows the main subtitle delay or has its own, which its own sync to the speech can set. The choice is remembered per series by language, as F-S6 does for the main one, in the player's own preferences, not in `TrackPrefs`.
- A-B loop: More › «تكرار مقطع (A-B)» sets A and B at the moment playing, or clears them. Playback goes back to A each time it reaches B, through pause and seeks, and a small chip with the two times and an × shows over the video while it runs. A new item clears it. Not on the main row of the controls.

In the same request: the F-T6 sleep timer «آخر الحلقة» no longer moves on at the end of the item: on screen it stays paused at the end, and behind the screen it ends playback, which stops background audio. The timer is cancelled if the owner picks another item by hand. The F-S2 card now comes when the credits begin, from F-S4 data (AniSkip `ed`, TheIntroDB credits, or a credits mark the owner sets for a series), and an episode with no credits data gets a light «الحلقة الجاية» button in its last minute with no countdown. These change no rule of the brief: F-S2 already allowed credits data to start the card early.
## 2026-11-12: Watchlist, cleanup suggestions, stats and automatic backup

The owner asked for these four in the same request as dual subtitles and the A-B loop.

- A watchlist separate from Favourites, in backups.
- Cleanup suggestions in Media Manager. They use the confirmed delete of the Media Manager waiver, never a delete without the sheet.
- Watching stats from a local watch log (schema 6). The log never leaves the phone and is not in the backup.
- Automatic backup to a folder the owner picks. It writes the same file as a manual backup, so keys stay out (R4), and it removes only its own older files.

## 2026-11-12: New name and icon

The owner renamed the app "Frame" (Arabic «فريم») and asked for the play mark alone, without the frame around it, as the icon. The launcher label and every on-screen mention changed; the application ID `com.frameglass.player`, the signing key, the repository name and the folders the app already writes to (`Pictures/Frame Glass`, `Movies/Frame Glass`) stay, so nothing already saved moves. Saved builds are now named `Frame <version>.apk`.

## 2026-11-12: Blur unseen episode pictures

The owner asked that an episode's picture not give the story away. An episode's own image (its TMDB still, or a frame of its file) is blurred in the episode list and on the Home card until the episode is started; a series backdrop or poster standing in is never blurred. A Library setting turns it off; on by default; in backups.

## 2026-11-12: Android 13 and newer

The owner will give the app to someone else, whose phone may not run Android 16. This changes R5 "One device" as follows:

- `minSdk` is 33 (Android 13) in every module, instead of 36. `compileSdk` and `targetSdk` stay 37, `arm64-v8a` stays the only ABI, and the application ID and signing key do not change.
- Every platform call above API 33 is guarded with `Build.VERSION.SDK_INT` and has a fallback on 33 to 35. Lint `NewApi` and `InlinedApi` are 0 in every module, and the compiled classes were checked against the SDK's API table.
- Glass that would need a newer API falls back to what section 7.5 and 7.9 already allow: `SurfaceView.setBlurRegions()` stays off below API 37 (and stays off everywhere until step 0.7 passes), and Haze picks its own blur path below API 36. No new effect was added for older phones.
- Brief 4.8 "Predictive back is on" now also holds on Android 13 to 15 through `android:enableOnBackInvokedCallback="true"`; Android 16 and newer already had it from target 37.
- R11 "the phone reports API below 37" still applies to the owner's phone. The other person's phone may report 33 to 37.
- The S26 Ultra stays the phone the app is designed and tuned for. Nothing has run on an Android 13, 14 or 15 phone yet, so every behaviour there is untested. `NEXT.md` lists what to check on the other person's phone.

## 2026-11-12: Feature tour

The owner asked for a tour of the app's features and approved its storyboard: six full-screen pages, each with a soft coloured light at the top, an illustration built from the app's own pieces, a title, a short body, page dots, a full-width «التالي» («يلا نبدأ» on the last page) and «تخطي». This changes the brief as follows:

- F-U15: a fresh install shows the tour first, then the setup steps. «تخطي» goes straight to the setup steps. An install that has finished onboarding, such as one updated in place, never shows it by itself. Settings › «جولة في المميزات» opens it at any time, and it then returns to Settings.
- Section 7.1 and 7.2: the tour keeps the dark look and the white primary pill in every theme and accent, as the player does. Its page lights reach 60% alpha at their centre, above the 16% cap of the cover glow, because the storyboard shows them so. They fade into the near-black canvas by the time they reach the text.
- R8: no film or series art ships in the APK. The library page shows up to 6 covers already stored on the phone from the owner's own library, rated titles first, never a clip or a private-folder item, with their own TMDB or IMDb badge. Slots left over, as on a fresh install, get quiet geometric posters drawn in code. Nothing is fetched or sent for this.
- Section 7.3: the see-through panels in the illustrations are tint and a 1 dp rim only. They add no blurred layer.
- Motion is a light entrance per page and stays off under section 7.9.

It needs no permission, dependency or network host.

## 2026-11-12: Repository made public

The owner asked to make the GitHub repository public so a friend can open it and download the APK. Before the switch: a GPL-3.0 `LICENSE` was added (required by the NextLib FFmpeg build), the whole history was scanned (no keys, keystores, media, screenshots or owner file names; the only 32-hex strings are Room schema hashes and test fixtures; the `eyJ` string is a fake test token), and the pre-commit hook stopped naming the PC user and the phone's serial. Older commits still contain that serial inside the hook's pattern; rewriting public history was not done.

## 2026-11-12: The name Frame everywhere

The owner asked for the name Frame everywhere. The GitHub repository is now `mutasimpensa98-coder/frame` (GitHub redirects the old `frame-glass` links). New screenshots go to `Pictures/Frame`, the private folder's fallback is `Movies/Frame`, manual backups are named `frame-backup-…`, automatic ones `frame-auto-…` (old `frame-glass-auto-…` files still count for retention, sorted by their time stamp). Kept on purpose: the package and application ID `com.frameglass.player` (changing it would make the phone treat a new build as a different app), the backup format marker `frame-glass-backup` inside the file (so old backups restore), the media session id, the local folder `D:\FrameGlass`, and the historical brief and waivers. Old screenshots stay in `Pictures/Frame Glass`; nothing of the owner's is moved.

## 2026-11-19: Android 11 and newer

The owner asked that the app install and run on older phones, and asked to be told every cost so it could be discussed. It was first built for Android 10 (API 29) on this branch (`9a534e4`). The owner read the list of costs and set the floor at Android 11 the same day, because Android 10 alone carried the heaviest ones: it has no All Files access, so the app needed `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` with `requestLegacyExternalStorage` there, the least testable path of the whole change; it has no trash, so every delete was permanent; a memory card could be read but not written; and cover frames, the HDR label and the notice of changes on storage each had a weaker fallback. All of that code was removed again. This changes R5 "One device" and the waiver "2026-11-12: Android 13 and newer" as follows:

- `minSdk` is 30 (Android 11) in every module, instead of 33. `compileSdk` and `targetSdk` stay 37, `arm64-v8a` stays the only ABI, and the application ID and signing key do not change.
- No dependency had to change. The highest `minSdk` any of them declares is 24 (WorkManager, DataStore, Navigation 3, Palette). Media3, NextLib, Compose, Haze, Coil, Room and core declare 23, and the prebuilt `ass-kt` native libraries 21. No core library desugaring was added: the two Java calls that needed API 33 were replaced with older ones.
- The manifest is unchanged: the same 9 permissions as section 4.10 with the biometric waiver, and no new one. Storage, the trash, volumes and the player's cutout use the same calls as before, all of which exist from API 30.
- Every platform call above API 30 in the project's own code is guarded with `Build.VERSION.SDK_INT` and has a fallback. Lint `NewApi` and `InlinedApi` are 0 in every module, and the compiled classes and the dex of the release APK were checked against the SDK's API table.
- The S26 Ultra stays the phone the app is designed and tuned for, and it must behave as before: every guard takes the old path from the API level where that path exists. R11 "the phone reports API below 37" still applies to the owner's phone.
- Nothing has run on an Android 11 or 12 phone, and no emulator was used. Every behaviour there is untested. `NEXT.md` lists what to check.

What a phone below Android 13 gets. Each line is a fallback, not a new feature.

Android 11 and 12 (API 30 to 32):

- **Glass has no blur.** Haze's glass and the app's own AGSL need `RuntimeShader`, API 33. Below it no surface asks Haze for anything: bars, badges and controls keep the tint and rim of section 7.9, and sheets and menus are solid. No refraction, no tilt sheen. The light under the finger is a plain radial gradient. This is the look a surface already has on any phone when no blur slot is free.
- **Language.** Android keeps a per-app language only from API 33. Below it the app keeps the choice itself (`AppLocale`, a small preferences file) and applies it to the application, each activity and the playback service; choosing a language recreates the screen. The phone's Settings has no per-app language page there. A player window that is in picture-in-picture while the language changes keeps its words until it is opened again.
- **Notifications** need no permission there, so the last setup step asks for none.
- **Back** works through the same callbacks. The manifest flag for the predictive gesture is ignored, so there is no preview of the screen behind.
- **The three picture-in-picture buttons** are sent as broadcasts. The flag that keeps other apps from sending them arrives in API 33. Below it each button carries a random value only this run of the app knows, and the player ignores a broadcast without it. The androidx fallback was not used, because it needs a permission the manifest removes on purpose.
- The themed (monochrome) icon is ignored. Below Android 14 the light tick of sliders and tabs is the text handle tick. On Android 11 a background job that Android stops is logged with no reason.

Android 11 (API 30) also:

- **Unseen episode pictures (waiver of 2026-11-12).** `Modifier.blur` does nothing below API 31, which would show the picture whole. There the still is decoded 12 px wide and stretched over the card: the colours of the picture with nothing in it to read. The full picture is never drawn while hidden. The tour's drawn stills get a flat cover that fades as the blur would. One helper (`Spoiler.kt` in `:glass`) decides this for the episode list, the Home card and the tour.
- **Picture-in-picture** has no auto-enter. Home or Recents while a video plays enters it from `onUserLeaveHint`, so the change of window is a plain cut with no smooth shrink. A screen the player opened itself, such as the subtitle file picker, does not trigger it. «تشغيل في نافذة منبثقة» asks for the window itself and does not depend on auto-enter.
- **No splash screen icon.** The launch window is the plain canvas colour.
- **Widget:** the covers have square corners, and the widget list of the phone shows the app icon instead of a preview.

`minSdk` 31 (Android 12) would remove the "Android 11 also" list. The glass and language items need Android 13.

## 2026-11-19: Developer name, delete from Home, play in pop-up

The owner asked for three small things. None needs a permission, a dependency, a network host or a schema change.

- F-M12: About gains one line under the app name and version, "Developed by Mutasim Suliman" («تطوير: Mutasim Suliman»). The TMDB logo, its notice and their order do not change.
- Delete from Home uses the Media Manager waiver of 2026-11-07 as it stands: every delete opens the confirmation sheet first. A Continue watching card of a series offers «حذف هذه الحلقة» for that episode's files, and the delete of a whole series reads «حذف المسلسل كله». The hero has the same long-press menu as the cards.
- F-P13: the long-press menus gain «تشغيل في نافذة منبثقة» as their first row, which starts playback and enters picture-in-picture by itself. It is behind a menu, as R13 asks, and adds nothing to Home, Library or the player's controls. Where picture-in-picture is missing or turned off for the app, the video plays in the full window and the player says so.
- A pop-up over Home lets the owner delete the very video that is playing. The delete is the same confirmed one. When it takes the file, playback stops, the pop-up or the player closes, no position is saved for that file, and it is no longer the last thing played.
- The player window is open to other apps for "Open with" (section 4.10). The pop-up request counts only when this app made it; the same intent from another app plays in the full window.

## 2026-11-21: View sizes

The owner asked for several more ways to show lists, videos and folders, from small to larger, behind the view button, in the private folder too («إضافة عدة أشكال أخرى لعرض القوائم والفيديوات والمجلدات (من صغير للأكبر) عند الضغط على تغيير العرض»), with a compact folder list as the reference for the smallest, and approved the storyboard of five sizes. This changes the brief as follows:

- F-U3 "a grid and list toggle": the button opens a sheet of five sizes instead: compact list, list, small grid, medium grid, large grid. Its icon is the size in use. The default of Library stays the 3-column poster grid. The same button is on the folder pages, the row, genre and person pages, beside the search field while titles are listed, and in the private folder.
- F-U3 is silent on Folders, which were one list. They now open as the compact list, the look of the owner's reference, until a size is chosen there.
- F-U4 "2:3 posters with title and year below, a small glass rating badge": holds for the medium grid. The small grid shows the poster alone, with no title, year or rating badge; its progress line and watched mark stay. A small 16:9 frame keeps its title on the picture. The large grid has a larger title and the year with the first genre under it. The two lists are rows, not posters.
- The choice is kept per place (Library, Folders, the private folder, the list pages with Search) in the settings and in the backup. A place with nothing stored shows its default, so an older install and an older backup keep the look they had.

It needs no permission, dependency, network host or schema change. Media Manager keeps its own list.

## 2026-11-21: Delete inside the private folder

The owner asked for a delete inside the private folder. This extends the waiver "2026-11-07: Private folder", which only moved videos in and out, and the waiver "2026-11-07: Media Manager and file deletion" as follows:

- A private video can be deleted from its long-press sheet, and several from the selection bar. Only while the folder is unlocked, as for moving out.
- The delete is permanent. Private videos live in app storage, which has no trash, so nothing can be brought back.
- It never runs without a confirmation, and it has one of its own, because the confirmation of Media Manager never sees private videos. That sheet says in the error colour that the delete is for good («حذف نهائي — ما يرجعش»), names the video when it is one, gives the count and the total size, and its strong button is Cancel. Back, the scrim and a drag cancel too.
- What goes: the video, the subtitles that moved in with it, its rows and its saved progress; then what the app kept beside them for that file (its frame, subtitles saved for it, its track choices). A title left with no file anywhere, private or in the library, is forgotten whole, with its match, cover and episode list, so its name cannot show in the hidden list or enter a backup. A title that still has a file keeps everything.
- A private video that is playing closes when it is deleted, as a library video does.

It needs no permission, dependency, network host or schema change.

## 2026-11-21: More themes and custom colours

The owner asked for more themes and for colours of their own, and approved a storyboard for it (`D:\FrameGlass\storyboards\views-themes\board.png`, sections 2 and 3). This extends "2026-11-07: Themes and colours". What that waiver kept still holds: the default is the dark look, and the player stays dark in every theme. So do the feature tour and the widget, as their own waivers say.

- **Eight ready themes.** Black (the dark look as it was, and the default), Pure black for AMOLED, Midnight, Violet, Forest, Coffee, Light (the light look as it was) and Paper. Each is a whole palette: canvas, raised surface, cards, text, rims and its own accent. Section 7.2 gives one canvas, `#0D0D0D`, and section 7.1 says "near-black canvas": both now describe Black only. The values are in `docs/design-tokens.md`.
- **A custom theme.** Three sliders: background hue, darkness from pure black to light, and how far cards and bars sit from the background. Everything else is derived, and text turns light or dark by itself. The darkness slider steps over the mid tones between CIE lightness 36 and 58, because there neither white nor black text keeps 4.5:1 on a selected chip. Near that step the rule costs colour: accents turn pale or very dark, secondary text grows as strong as main text, and glass turns nearly opaque.
- **Accents.** The six of 2026-11-07, pink and cyan, "any colour" from a hue and saturation field drawn in the app, and the theme's own colour, such as blue on Midnight. A colour holds on every theme. The theme moves a colour toward its text colour until it reads as text, so "any colour" is the owner's hue, not always the exact shade.
- **Follow the phone** is a switch now, not a third theme. The side that matches the chosen theme shows it, and the other side shows Black or Light.
- **Section 7.3, a seventh glass kind.** The bar of actions of selection mode draws its delete labels in the error colour, which needs a calmer backdrop than plain text. It is the tab bar's recipe held to that colour's limit (`GlassKind.ActionBar`), so over the plain canvas it is the tab bar, and over bright art it turns denser than the tab bar would. On a light theme it is denser always, because light glass holds its worst case.
- **Section 7.3, the contrast rule.** The rule is unchanged: `a = max(baseAlpha, (B - L) / (B - T))`. Its numbers, 118 and 87 with tint level 13, are those of Black. Every theme now takes `T` from its own canvas and `L` from its own text, measured the same way, and a coloured tint keeps its limit 3 levels to the safe side. The tint of every glass surface is the theme's canvas, so a navy theme has navy glass.
- **The update keeps the look.** The old dark becomes Black, the old light becomes Light, and "follow the phone" stays on between those two. The old white accent becomes "the theme's own", which is white on Black and near-black on Light, and a chosen colour stays. A backup written before this restores the same way.
- **Android 11 and 12** get these themes as flat tint and rim, as the waiver of 2026-11-19 says, and a sheet there is the plain canvas of the theme.
- R13: all of it is on Settings › Appearance. No permission, dependency, network host or schema change. The settings file gains six keys and drops the old theme key on its first save, and the backup gains six fields that an older backup simply does not have.

Four points are not on the storyboard and are the builder's reading, waiting for the owner: the step over the mid tones, what "Reset to default" resets, the "Theme colour" dot at the start of the accent row, and how pale the raised surface of Pure black is. They are items 8 to 11 of `NEXT.md` section 5.

Nothing of this has been seen on a phone. `STATUS.md` says what was tested and what was only reasoned.
