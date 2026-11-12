# Frame

<div dir="rtl">

**مكتبتك ومشغلك للفيديو على Android، بواجهة زجاجية ودعم كامل للعربية.**

</div>

A video library and player for Android, with a glass interface and full Arabic support.

![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![arm64-v8a](https://img.shields.io/badge/ABI-arm64--v8a-555555)

[تحميل آخر نسخة / Download the latest version](../../releases/latest)

[العربية](#العربية) · [English](#english)

---

<div dir="rtl">

## العربية

يجمع Frame كل فيديوهات الهاتف في مكتبة واحدة تلقائياً، ويعرض الأفلام والمسلسلات والأنمي بأغلفتها وتقييماتها وتفاصيلها، ويشغّلها بمحرك يتعامل مع أصعب الصيغ والترجمات. يعمل كاملاً على الجهاز، ولا يتطلب حساباً.

### المكتبة

- بحث تلقائي في الهاتف كله، بدون إضافة المجلدات يدوياً.
- تعرّف تلقائي على الأفلام والمسلسلات والأنمي، مع الأغلفة والشعارات والقصص وطاقم التمثيل والحلقات.
- تقييمات TMDB بالأزرق وIMDb بالأصفر.
- قسم «تابع المشاهدة» مع شريط تقدم لكل فيديو.
- المفضلة، وإخفاء العناوين، واستبعاد المجلدات، ومقاطع الهاتف العشوائية في قسم مستقل.
- السحب لتحت يحدّث الملفات والبيانات والتقييمات معاً.
- وضع السينما: الرئيسية والمكتبة للأفلام والمسلسلات والأنمي فقط.
- مفتاح تشغيل لكل من TMDB وIMDb، وعند إيقاف TMDB يتعرف التطبيق على العناوين من IMDb.

### المشغل

- فيديو حتى 4K و60 إطاراً، بصيغ HEVC وAV1 وHi10P، مع HDR10 وHLG وHDR10+.
- صوت AC-3 وE-AC-3 وDTS وTrueHD وALAC.
- ترجمات SRT وASS بتنسيقها الكامل، مع حروف عربية متصلة واتجاه صحيح.
- تأخير الصوت والترجمة، والسرعة، وحجم الصورة، والإيماءات، وقفل الشاشة.
- نافذة عائمة عند الخروج من التطبيق.
- تخطي المقدمة والملخص، والانتقال للحلقة التالية.
- بحث عن الترجمة داخل المشغل عبر OpenSubtitles وSubDL وSubSource.
- مزامنة تلقائية للترجمة على الجهاز نفسه، مع إصلاح فرق سرعة الإطارات وزر تراجع.

### الخصوصية والتخزين

- مجلد خاص يُفتح بكلمة مرور أو بالبصمة، مع رمز استرجاع. تختفي فيديوهاته من المكتبة والبحث، وتمنع شاشاته لقطات الشاشة.
- مدير وسائط يعرض مساحة المجلدات والفيديوهات، مع تأكيد قبل كل حذف.
- لا إعلانات ولا حسابات. المفاتيح محفوظة في Android Keystore، ومقاطع الهاتف الشخصية لا تُرسل لأي خدمة.
- مفتاح واحد في الإعدادات يوقف كل اتصال بالإنترنت.
- نسخة احتياطية للتقدم والمطابقات والإعدادات.

### المظهر واللغة

- داكن وفاتح وحسب الهاتف، مع ستة ألوان مميزة.
- العربية والإنجليزية، مع واجهة كاملة من اليمين إلى اليسار، ولغة مستقلة عن لغة الهاتف.
- إعدادات مفصلة ومرتبة بالأيقونات.

### التثبيت والتحديث

نزّل ملف APK من صفحة [الإصدارات](../../releases/latest) وافتحه على الهاتف، ثم اختر **تحديث**. لا تحذف التطبيق قبل التحديث، لأن الحذف يمسح المكتبة والمفاتيح والمجلد الخاص.

للأغلفة والتقييمات أضف مفتاح TMDB من الإعدادات. بدونه يعمل التطبيق مشغلاً محلياً كاملاً.

يحتاج التطبيق Android 13 أو أحدث، على هاتف بمعالج arm64 (ARM بنظام 64 بت). صُمّم وجُرّب على Galaxy S26 Ultra بنظام Android 17، ولم يُجرَّب بعد على أي هاتف بنظام Android 13 أو 14 أو 15.

</div>

---

## English

Frame gathers every video on the phone into one library by itself. It shows films, series and anime with their art, ratings and details, and plays them with an engine built for difficult formats and subtitles. It runs entirely on the device, with no account.

### Library

- Finds every video on the phone by itself, with no folders to add.
- Matches films, series and anime, with covers, title logos, plots, cast and episodes.
- TMDB ratings in blue and IMDb ratings in yellow.
- Continue watching, with a progress bar on each video.
- Favourites, hidden titles, excluded folders, and personal clips kept in their own section.
- Swipe down to refresh files, details and ratings together.
- Cinema mode: Home and Library show only films, series and anime.
- An on/off switch for TMDB and for IMDb. With TMDB off, titles are identified through IMDb.

### Player

- Video up to 4K60 in HEVC, AV1 and Hi10P, with HDR10, HLG and HDR10+.
- Audio in AC-3, E-AC-3, DTS, TrueHD and ALAC.
- SRT and fully styled ASS subtitles, with joined Arabic letters and correct direction.
- Audio and subtitle delay, speed, aspect and zoom, gestures and screen lock.
- Picture-in-picture when leaving the app.
- Skip intro and recap, and go on to the next episode.
- Online subtitle search through OpenSubtitles, SubDL and SubSource.
- Automatic subtitle sync on the device, with a frame-rate fix and Undo.

### Privacy and storage

- A private folder opened by password or fingerprint, with a recovery code. Its videos leave the library and search, and its screens block screenshots.
- A Media Manager that shows the size of folders and videos, with a confirmation before every delete.
- No ads and no accounts. Keys are sealed in Android Keystore, and personal clips never reach any service.
- One switch turns off every internet connection.
- Backup of progress, matches and settings.

### Look and language

- Dark, light or follow the phone, with six accent colours.
- Arabic and English, with a full right-to-left interface and a language independent of the phone.
- Detailed Settings, organised with icons.

### Install and update

Download the APK from [Releases](../../releases/latest), open it on the phone and choose **Update**. Do not uninstall first: that deletes the library, keys and private folder.

Add a TMDB key in Settings for covers and ratings. Without one, Frame is a complete local player.

## Technology

| Area | Stack |
|---|---|
| Language and UI | Kotlin, Jetpack Compose, Material 3, Navigation 3, Haze |
| Playback | Media3, FFmpeg through NextLib, libass through a local fork of ass-media |
| Data | Room, DataStore, WorkManager |
| Network and images | OkHttp, Coil |
| Metadata | TMDB, AniList, OMDb, AniSkip, TheIntroDB |

| Module | Role |
|---|---|
| `app` | Screens, navigation, Settings |
| `player` | Player screen and controls |
| `engine` | Playback engine, audio and subtitle delay |
| `data` | Library, scanning, metadata, Media Manager, private folder |
| `net` | Metadata and subtitle clients |
| `parser` | File name parsing, including Arabic |
| `glass` | Design system and shared components |
| `qa` | On-device test harness |
| `third_party/ass-media` | Local fork, see `LOCAL-CHANGES.md` |

Requires JDK 21 and Android SDK 37. The signing key is not in this repository, so a build needs your own key through `FG_SECRETS`. See `tools/env.ps1` and `fg.ps1`.

Requires Android 13 or newer on an arm64 (64-bit ARM) phone. Built and tuned for the Galaxy S26 Ultra on Android 17; it has not been tried on any phone running Android 13, 14 or 15, and other arm64 phones on Android 16 or newer are untested too.

## Credits

This product uses the TMDB API but is not endorsed or certified by TMDB. IMDb ratings come through OMDb. Each open-source licence ships inside the app, under Settings, About.

## License

Frame is free software under the GNU General Public License v3.0, see [LICENSE](LICENSE). It must be, because it links the GPL-3.0 FFmpeg build of NextLib.

فريم برنامج حر بترخيص GPL-3.0، والتفاصيل في ملف LICENSE.
