# Build contract for parallel work

The brief is `docs/BRIEF.md`. This file says who owns what while several agents build at once.

## Ownership

Edit only the folders you own. If you need a change elsewhere, write it in your report.

| Owner | Folder | Package |
|---|---|---|
| parser | `parser/` | `com.frameglass.parser` |
| net | `net/` | `com.frameglass.net` |
| data | `data/` | `com.frameglass.data` |
| glass | `glass/` | `com.frameglass.glass` |
| player | `player/`, `engine/` | `com.frameglass.playerui`, `com.frameglass.engine` |
| app | `app/` | `com.frameglass.player` |

`data/.../api/Api.kt`, `net/.../NetApi.kt` and `parser/.../ParsedName.kt` are fixed contracts. Implement them. Do not change their signatures. If one is truly wrong, keep it compiling, add what you need beside it, and say so in your report.

## Wiring

- `:app` has `FrameApp : Application, FrameServicesOwner`, which calls `FrameData.create(context)` and starts catch-up work. `FrameData` is `object com.frameglass.data.FrameData { fun create(context: Context): FrameServices }`, owned by data.
- `FrameData` builds the net client with `com.frameglass.net.Net.client(config: NetConfig, context: Context): MetadataClient`, owned by net.
- `:parser` exposes `object NameParser { fun parse(relativePath: String): ParsedName }` and `object ArabicNormalizer { fun key(text: String): String }`.
- `:player` exposes `object PlayerRoute { fun intent(context: Context, ref: PlayRef): Intent }`. `PlayerActivity` lives in `:player`, with its own manifest entry. `ref` travels as extras `playableKey`, `fileRowId`, `uri`.
- `:qa` keeps using `FramePlayer`. Keep every public member of `FramePlayer` that `qa/` uses.
- `:glass` exposes `FrameTheme`, tokens, `GlassSurface`, and shared components. `:app` and `:player` use only these for look and motion.

## Rules

- Build agents do not use adb or install anything. Phone work belongs to the session that ships the build.
- Build agents do not commit or push. Do not run `knowledge_sync.py`. R1 of the brief applies, as changed in `WAIVERS.md`.
- Any change to `third_party/ass-media` goes into its `LOCAL-CHANGES.md` in the same step. Its `LICENSE` stays.
- Build from PowerShell like this, one module at a time. `NoDefaultCurrentDirectoryInExePath` is set, so `.\` is required:

      . D:\FrameGlass\app\tools\env.ps1; Set-Location D:\FrameGlass\app; cmd /c ".\gradlew.bat :MODULE:compileQaKotlin --console=plain > D:\FrameGlass\logs\build-MODULE.log 2>&1"

  The `:parser` module is plain JVM: use `:parser:test`. Other agents build at the same time, so a Gradle lock wait is normal. Retry if needed.
- Versions are pinned in `gradle/libs.versions.toml`. Do not edit them. Add a dependency only if section 4.2 of the brief lists it, and report it.
- No hard-coded user text. Each module keeps `res/values/strings.xml` and `res/values-ar/strings.xml`, with string names prefixed by the module (`glass_`, `player_`, `app_`). Arabic must be natural, not literal.
- Code and comments are in English. Keep comments to the non-obvious.
- No Nuvio code, fonts, icons or assets. The look comes from `D:\FrameGlass\reference\REFERENCE.md` and its images, which stay outside the repo.
- Dark is the default, and the player stays dark in every theme (`WAIVERS.md`). `minSdk` 33 since the waiver of 2026-11-12 (36 before). Android 17 rules in brief section 4.8 apply.
