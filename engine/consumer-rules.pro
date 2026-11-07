# libass-android reads these private fields by reflection.
-keepclassmembers class androidx.media3.extractor.mkv.MatroskaExtractor {
    *** subtitleSample;
    *** extractorOutput;
}
-keep class io.github.peerless2012.ass.** { *; }
-keep class io.github.anilbeesetti.nextlib.** { *; }
