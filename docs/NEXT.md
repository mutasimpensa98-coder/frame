# Next

Updated 2026-11-08, after the owner-requests package was built and the four faults of its review were fixed. Read `STATUS.md` first. R1 applies as changed by the GitHub waiver in `WAIVERS.md`: one private repository on the owner's account, and nothing else.

The history must stay fit to publish. `.gitignore` and `tools/hooks/pre-commit`, set through `core.hooksPath`, keep out what the waiver bans. A new clone needs `git config core.hooksPath tools/hooks`.

Testing stays small: essentials only, and long runs such as the soak only when the owner asks.

## 1. State of the phone

The phone runs 1.0.103. The owner-requests package of `STATUS.md`, section "This round", is in the working tree only: not committed, not installed and not seen on any phone. Shipping it is step 1 of section 7.

The release build is on the phone, updated in place from the first install, with the library, progress, settings and keys intact. `firstInstallTime` did not change.

Seen on the phone: the Settings pages, Media Manager sizes, themes, accents, language, long press, hide and restore, the player subtitle sheet and the blue TMDB badge.

Seen on the test copy, which is now removed: favourites and the private folder. Move in, locked after a forced stop, wrong password refused, playback with the name hidden, move out, and all 4 files came back byte-identical at their old paths.

Not seen yet:

1. Fingerprint unlock. It needs the owner's finger.
2. Delete to the phone's trash. The test clips were pushed by adb and have no MediaStore row, so the app called the delete permanent and it was cancelled. Try a real clip the owner does not need.
3. Online subtitle search. It needs a subtitle key.
4. The general swipe to refresh in 1.0.103. It scans, fetches art, text and ratings again for titles older than 10 minutes, retries titles with no match, and waits up to 8 s before the rest carries on in the background. The owner was using the phone, so it has not been seen.
5. The yellow IMDb badge. The general refresh should fetch ratings for titles matched before the OMDb key was added.

The 2 demo folders on the phone are already empty. The owner still has to delete `Movies/FrameGlassTest` from My Files, and empty `D:\FrameGlass\_to-delete`. Test clips on the PC went there, so a matrix run first needs `tools/make_testmedia.py`, `python tools/matrix.py push` and `.\fg.ps1 qa`.

## 2. Owner checklist for this round

Send this to the owner as it stands, and record the answers in `docs/evidence/phase-1/owner-answers.md`.

1. افتح التطبيق بعد التثبيت. هل بدأ يبحث وحده في التلفون بدون ما تضيف مجلد؟
2. هل ظهرت فيديوهاتك في الرئيسية؟ وهل مقاطع الكاميرا وواتساب في صف «مقاطع» لحالها؟
3. هل ظهرت الأفلام والمسلسلات ببطاقات بنفس الشكل في الرئيسية وفي المكتبة؟
4. شغّل فيديو دقيقة، ثم ارجع. هل ظهر شريط التتبع على بطاقته؟
5. أضف مفتاح TMDB من الإعدادات. هل ظهر التقييم والغلاف على الأفلام والمسلسلات؟
6. افتح «مقاطع» و«مجلدات» في المكتبة. هل كل شيء في مكانه؟
7. هل ظهر فيديو ما تبيه، مثل مقاطع تطبيقات أو ملفات مؤقتة؟ اكتب اسم المجلد.
8. كم تقريبًا أخذ البحث الأول حتى انتهى؟

جولة التحسينات. جرّب أول شيء بمقطع ما يهمك، مش بفيديو مهم:

9. من الإعدادات، غيّر المظهر بين داكن وفاتح وحسب التلفون، وجرّب الألوان الستة. هل كل النصوص واضحة في الفاتح؟ وهل المشغّل بقى داكن؟
10. غيّر لغة التطبيق بين العربي والإنجليزي. هل كل الشاشات تقلب صح؟
11. ضغطة طويلة على بطاقة: هل ظهرت القائمة كاملة؟ جرّب المفضلة، وإخفاء العنوان، واستبعاد المجلد.
12. افتح تفاصيل الملف. هل الحجم والمسار والدقة صحيحة؟
13. اسحب الشاشة لتحت في الرئيسية أو المكتبة. هل بدأ بحث جديد وظهر عدد الفيديوهات الجديدة؟
14. افتح إدارة الوسائط. هل أحجام المجلدات والفيديوهات قريبة من اللي تعرفه؟
15. احذف مقطع تجربة. هل وضّحت لك رسالة التأكيد إن كان يمشي لسلة المهملات أو ينحذف نهائيًا؟ وهل قدرت ترجّعه من السلة؟
16. افتح المجلد الخاص أول مرة. هل طلع رمز الاسترجاع؟ اكتبه في مكان آمن. وهل البصمة تفتح المجلد؟
17. انقل مقطع تجربة للمجلد الخاص، وشغّله، ثم رجّعه. هل رجع لمجلده الأصلي؟ وهل اختفى من الرئيسية والبحث وإدارة الوسائط وهو داخل المجلد؟
18. وأنت تشغّل فيديو من المجلد الخاص، حاول تاخذ لقطة شاشة، وشوف الإشعار. هل الاسم مخفي؟
19. في المشغّل، افتح الترجمة وابحث أونلاين (يحتاج مفتاح ترجمة). هل ظهرت النتائج والعربي أولها؟ وهل اشتغلت الترجمة بدون ما يعيد الفيديو؟

جولة طلباتك الأخيرة. هذي تنفع بعد ما تثبّت النسخة الجاية فوق 1.0.103:

20. أول فتحة بعد التحديث: هل مكتبتك ومكان وقوفك في كل فيديو والإعدادات والمفاتيح بقوا زي ما هم؟ التطبيق يجيب بيانات كل الأفلام والمسلسلات من جديد مرة وحدة، فكم تقريبًا أخذ لين هدأ؟ وهل سخن التلفون؟
21. من الإعدادات › المكتبة شغّل «وضع السينما». هل الرئيسية و«الكل» في المكتبة صاروا يعرضوا الأفلام والمسلسلات والأنمي بس؟ وهل بقية فيديوهاتك موجودة في «مقاطع»؟ طفّيه بعدها: هل رجع كل شيء زي قبل؟
22. اسحب بين صفحات الواجهة الكبيرة فوق في الرئيسية. هل كل صفحة فيها صورة، وما فيه ولا صفحة فاضية أو سوداء؟ وهل آخر فيديو كنت تتفرج عليه باين، في الواجهة أو في «تابع المشاهدة»؟
23. افتح تفاصيل مسلسل وانزل لتحت في الحلقات، في الداكن والفاتح وبالعربي. هل ظهر فوق شريط زجاجي فيه اسم المسلسل؟ وهل ما عاد فيه شيء يبان تحت شريط الساعة والبطارية؟
24. ضغطة طويلة على بطاقة، وبعدها زر الرجوع. هل القائمة تتقفل بس، بدون ما تطلع من الشاشة؟
25. في المظهر الفاتح افتح «حول». هل شعار التطبيق واضح؟
26. غيّر لغة التطبيق للعربي، وسكّر التطبيق وافتحه. هل أسماء الأفلام والتصنيفات والقصة صارت بالعربي، وين ما تكون متوفرة؟ وارجع للإنجليزي: هل رجعت؟
27. لو ما عندكش مفتاح OMDb، ضيفه من الإعدادات. هل ظهر تقييم IMDb الأصفر في صفحة التفاصيل بدون ما تسحب الشاشة للتحديث؟ (البطاقات تبقى على تقييم TMDB.)
28. مقاطعك الشخصية اللي في اسمها رقم بس، مثل «رحلة البحر - 3»: هل طلعت من «أنمي» وصارت في «مقاطع»؟ وهل فيه أنمي حقيقي طلع من «أنمي» بالغلط؟
29. شغّل فيلم بترجمة تعرف إن توقيتها غلط، واختارها من قائمة الترجمة. هل اتزامنت وحدها، وطلعت رسالة فيها الفرق بالثواني وزر «التراجع عن المزامنة»؟ كم أخذت تقريبًا؟ وهل الفيديو تقطّع وهي تشتغل، خصوصًا في فيلم 4K؟
30. جرّب «التراجع عن المزامنة». هل رجع التوقيت زي ما كان؟
31. حط على نفس الفيلم ترجمة فيلم ثاني. هل قال إنه مش متأكد، وما غيّر التوقيت؟
32. لو عندك ترجمة تمشي صح في الأول وتتأخر شوي شوي لين آخر الفيلم: هل صحّحها، وكتب إنه صحّح السرعة؟ سكّر الفيديو وافتحه: هل الترجمة الأصلية لسه في القائمة جنب النسخة المصحّحة؟
33. التأخير اليدوي للترجمة صار يوصل لدقيقة قدّام أو ورا. هل يشتغل زي قبل؟

## 3. Watch for on the phone

These come from reading the code. None has been seen.

- **Time and heat of the first search.** Every new file is hashed, then probed with up to 3 at once. A phone with thousands of clips will run this for a while.
- **Playback during the first search.** The probing does not pause while a video plays, and playback has priority under section 7.9. If playback stutters, pause `Enricher` while the player is open.
- **All Files granted without leaving the app.** A catch-up starts only when `MainActivity` is stopped and started again. In split screen or a pop-up window it may not stop, and Home then says no videos until Search again is tapped.
- **Folder name case.** If MediaStore spells a folder with different case from the disk, its videos can get a second row, which shows as a second version.
- **Home changing during the first search.** The hero list follows the newest files, so it moves until the search ends.
- **Trash through MediaStore.** Direct `IS_TRASHED` is expected to work with All Files access. If Android refuses it, the system confirmation appears. A subtitle goes through the Files table, and `createTrashRequest` may refuse a batch that holds one. The whole batch then comes back as failed. Nothing is lost, but the delete would need a fix.
- **Time to move into the private folder.** Every file is copied, then the whole copy is read back and hashed, so a big film takes about twice its copy time, with heat.
- **Recovery after a kill during a move.** Both sides are hashed again in `beforeScan`, so one large file delays the first scan after that kill.
- **BiometricPrompt under R8.** The constructors of `BiometricFragment`, `FingerprintDialogFragment` and `BiometricViewModel` are in `seeds.txt`, kept by the fragment and lifecycle consumer rules. No extra keep rule was needed, but the prompt has only been built, never shown.
- **`MainActivity` is now a `FragmentActivity`.** Watch for anything that changed with it, such as back handling or the trash confirmation result.
- **Recents flag.** A process death during private playback leaves the flag set until the next public play.
- **Font scale 1.3.** `TopGlassBar` is fixed at 34 sp, so settings titles may clip.

From the owner-requests package, read in the code and never run:

- **The first open after the update.** Schema 2 to 3 runs, then the clip pass and the anime pass, then every matched title is fetched once more because `metaLanguage` is NULL on every row: details, 3 images, all seasons and one OMDb call each. With many titles this spends OMDb's 1,000 calls a day, and it runs while the owner looks at Home.
- **Arabic from TMDB.** Plain `ar` is asked for. Whether TMDB answers it in Arabic is unverified. `AppLanguage` reads `LocaleManager` first; whether the application context sees a change made in Settings has not been checked.
- **Automatic sync on real films.** A second decoder reads the audio while the film plays: decode speed, heat, the speech detector on real film audio, the thresholds and any stutter, mostly with EAC3 or Dolby audio under 4K, are unmeasured. The sync gives up after 25 s.
- **A frame rate fix** writes a stretched copy to `filesDir/player-subs/` and shows it live, because NextLib's subtitle speed cannot take a ratio.
- **Hero art** is decoded once for every feed Home gets. During the first search that can be every 400 ms or so, for up to 5 heroes.
- **The OMDb fetch after a new key** waits for a swipe when the network fails.

## 4. Known limits in this build

- A cut MKV plays to the cut, then shows a friendly message. It cannot seek or resume, and a refused seek shows a message.
- A cut MKV with cues at the front, cut exactly between clusters, holds the last frame with no message.
- The delayed error that reaches the player for a cut file was read from bytecode only. Matrix row C2 is the real check.
- `ScanState.currentFolder` is filled in, but no screen shows it.
- Changing subtitle encoding or adding a subtitle file still reopens the video. Online results load live.
- The new-video count after a swipe is a before-and-after difference, so it is approximate.
- HDR contrast under the 0.78 player sheet scrim is unmeasured.
- A kill between a copy reaching the private folder and its row committing leaves an unlisted file there. It costs space only, and the original stays. It is not swept on purpose: if the database were ever lost, a sweep would destroy the only copies.
- If the private copy is damaged and the original is already gone, nothing can restore it. The card stays in the folder.
- Automatic sync runs on a subtitle the owner picks, adds, downloads or taps "Sync with the speech" for. Sidecars loaded at open are not synced by themselves, and embedded tracks cannot be synced.
- Undo of a sync ends when the video reopens, for example after an encoding change or an added file. The original subtitle stays in the list, so choosing it again brings it back, and that starts a new sync with its own Undo.
- Every subtitle copied in for a video is listed when it opens, including old picks and retimed copies.
- Clips already matched as anime before this build stay anime until Fix match.
- A cue longer than 15 s counts for nothing in sync.
- The ClipPass rewrite of stored tags and the anime pass need Room, so only `StoredParse.reread` is tested.
- `PrivateVaultImpl`, `MediaManagerImpl` and `MediaStoreTrash` have no test that runs them, because they need Room and MediaStore on a device. Only their pure parts are tested on the JVM.

## 5. Decisions for the owner

1. **Recently added on Home.** F-U5 lists Top rated, genre and collection only. With no key none of them can appear, so this round added Recently added as a fallback, used only when none of those rows qualifies. Keep it, or return to the brief's list?
2. **Player sheets.** They are solid now, because `SurfaceView` gives Haze nothing to sample. Image 08 shows dimmed video behind the sheet. Keep solid, go back to a see-through tint, or try the `PixelCopy` backdrop of section 7.5?
3. **Videos under 4.** Brief rule: a row with fewer than 4 titles is hidden. A phone with films and 1 to 3 clips shows those clips only in Library. Keep the rule for the Videos row?
4. **Cover tint.** `coverTint` is a Settings switch and defaults to off, which keeps the dark look as it was. On spreads the cover colour further. Keep it off by default?
5. **Lost recovery code.** The code is held in memory until the owner confirms it. If the app dies before that, the code is gone and only the password opens the folder, so a forgotten password then means the videos cannot be reached. A way to issue a new code while unlocked would close this, and it needs a new call beside `PrivateVault`. Build it?
6. **Uninstalling.** Private videos live in app storage, so uninstalling or clearing the app's data deletes them unless the owner picks Keep data. `hasFragileUserData` offers that choice, and the private folder shows a warning. This cannot be solved without storing them elsewhere. Accept it?
7. **Moving to a new phone.** `allowBackup="false"` stops cloud backup, but since Android 12 it does not stop device-to-device transfer, and lint flags this. With no `dataExtractionRules`, a transfer may copy `files/vault` and the database to the new phone. The keys are sealed to this phone, so the folder would not open there, but the files would have left it. Adding rules that exclude everything closes this, and the app's own backup zip then becomes the only way to move the library. Add them?

## 6. Gate 0, still open

Gate 0 does not block app work under the waiver, but it still has to pass. Done so far: matrix 50 of 53 Core and the delay proof, both in `phase-0-results.md`.

- Run C2 for the full clip. It stopped at 8 s, before the cut.
- S4 missed one seek by 1.1 frames. V11_vfr needs a check that fits a variable frame rate.
- `python tools/matrix.py soak 30` for the soak.
- Step 0.7, glass over video. `VideoBlurHook.ENABLED` stays false until this is measured. `D:\FrameGlass\releases\test\FG Test phase 0.apk` is the build with the blur button.
- Step 0.8, storage proof, needs a USB drive. Step 0.9, mpv, is skipped under D3.
- Real files for H4 to H6, A7, S9 and S10.
- Complete `phase-0-results.md` with what brief section 0.10 asks: one row per `[UNVERIFIED]` item and per engine risk, and a plain recommendation.
- Owner checks: font choice, HDR look, Arabic subtitle rendering, dialogue on A2 to A5.

## 7. Build next, in order

1. **Ship the owner-requests package.** Write `docs/RELEASE-NOTES.md` in Arabic, commit and push `main`, run `.\fg.ps1 release`, and send the checklist of section 2, items 20 to 33. Watch the first open after the update closely, because schema 3 and both one-time passes run on the owner's library there.
2. **TMDB and IMDb switches** from `docs/OWNER-REQUESTS.md`. Not started.
3. **F-T2 chapters and F-T3 frame step.** No code exists. Phase 7.
4. **F-S4 local intro detection.** Phase 7b.
5. **Tests that need the phone or a device runner:** `Scanner`, `CatchUp`, `ClipPass`, `MigrationTestHelper` for 1 to 2 and 2 to 3, a move in and out with the app killed at each step, the trash flow, the Gate 2 forced decoder failure, and the Gate 5 clipped text walk.
6. **`:benchmark` module and Baseline Profile.** Neither exists, and Phase 4 and DoD-04 need them.

## 8. Missing project files

- `docs/VERSIONS.md`. `gradle/libs.versions.toml` has entries that section 4.2 does not list: `material-icons-extended` 1.7.8, `junit` 4.13.2, and `biometric` 1.1.0, which the private folder waiver allows. Log them with the reason, and record the `:app:dependencies` result that section 4.2 asks for.
- `docs/design-tokens.md` does not have the light theme or the accents yet.
- `tools/name_report.py`, section 6.2, and `tools/make_fixture_library.py`, Gate 1. Until that tool exists, demo clips are `ffmpeg -t 15 -i testmedia/gen/V3_hevc_8bit_1080p.mkv -c copy -metadata title=demo-<i>-<stem> <tmp>.mkv`, pushed to `/sdcard/Movies/FG Titles/<name>`. Names: The.Matrix.1999.1080p.BluRay.x264.mkv, Inception.2010.1080p.WEB-DL.x265.mkv, Interstellar.2014.1080p.BluRay.mkv, Dune.Part.Two.2024.2160p.WEB-DL.mkv, The.Dark.Knight.2008.1080p.mkv, Breaking Bad/Season 1/Breaking.Bad.S01E01.1080p.mkv, S01E02 and S01E03 in the same form, [SubsPlease] Frieren - 05 (1080p) [ABCD1234].mkv, and the same with 06.
- `.\fg.ps1 gate N` covers gate 0 only.
- The pre-commit hook of section 3.4 is not installed.
