# Local changes to ass-media

Base: `io.github.peerless2012:ass-media` tag 0.5.1, MIT, from libass-android `lib_ass_media`.

- Published AAR SHA-1: `6e6915b2cb37fc51c059a1cf359173f86087719d`
- Sources jar kept in `upstream/`, SHA-1 `9c758d9f6e7a9144c8a4c51274cc0944674e5e83`. Its origin fits 0.5.1 but was not confirmed online.

The fork is built here for two reasons: `AssBidi` fixes the base direction of Arabic lines, and the published AAR targets Media3 1.8.0 while this project uses 1.11.1.

| File | Change |
|---|---|
| `AssBidi.kt` | Added, 61 lines |
| `AssHandler.kt` | +52 -32: overlay size, `videoTimeOwner`, `AssBidi.fixChunk` |
| `parser/AssFullSubtitleParser.kt` | +2 -1: `AssBidi.fixScript` |
| `widget/AssSubtitleCanvasView.kt` | +1 -1 |
| `widget/AssSubtitleTextureView.kt` | +1 -1 |
| `widget/AssSubtitleView.kt` | +3 -0 |

The other 19 Kotlin files match upstream. Native libraries still come from the prebuilt `ass-kt` 0.5.1 AAR.

Record any further change here in the same step. `LICENSE` stays.
