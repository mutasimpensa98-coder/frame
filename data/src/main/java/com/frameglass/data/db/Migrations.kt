package com.frameglass.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** F-L6. An update must never wipe the library, and the owner's phone already holds version 1. */
internal object Migrations {
    /** Written out so a JVM test can hold it against the exported schema. */
    val STATEMENTS_1_2: List<String> = listOf(
        "ALTER TABLE `Title` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS `ExcludedFolder` (`volumeUuid` TEXT NOT NULL, `relPath` TEXT NOT NULL, " +
            "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`volumeUuid`, `relPath`))",
        "CREATE TABLE IF NOT EXISTS `VaultItem` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`storedName` TEXT NOT NULL, `sha256` TEXT NOT NULL, `state` INTEGER NOT NULL, `movedAt` INTEGER NOT NULL, " +
            "`watchPositionMs` INTEGER NOT NULL, `watchDurationMs` INTEGER NOT NULL, `watched` INTEGER NOT NULL, " +
            "`lastPlayedAt` INTEGER NOT NULL, `tempPath` TEXT, `f_rowId` INTEGER NOT NULL, `f_fileId` TEXT NOT NULL, " +
            "`f_volumeUuid` TEXT NOT NULL, `f_relPath` TEXT NOT NULL, `f_size` INTEGER NOT NULL, `f_mtime` INTEGER NOT NULL, " +
            "`f_durationMs` INTEGER NOT NULL, `f_width` INTEGER NOT NULL, `f_height` INTEGER NOT NULL, `f_videoCodec` TEXT, " +
            "`f_hdrType` TEXT, `f_audioSummary` TEXT, `f_osHash` TEXT NOT NULL, `f_available` INTEGER NOT NULL, " +
            "`f_firstSeenAt` INTEGER NOT NULL, `f_lastSeenAt` INTEGER NOT NULL, `f_titleId` INTEGER, `f_episodeId` INTEGER, " +
            "`f_releaseTags` TEXT, `f_hasSidecarSub` INTEGER NOT NULL, `f_playableKey` TEXT NOT NULL, `f_parsedSeason` INTEGER, " +
            "`f_parsedEpisode` INTEGER, `f_parsedEpisodeEnd` INTEGER, `f_parsedAbsolute` INTEGER, `f_nameKind` TEXT NOT NULL, " +
            "`f_hasEmbeddedSub` INTEGER NOT NULL, `f_framePath` TEXT, `f_enrichState` INTEGER NOT NULL, " +
            "`f_enrichTries` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_VaultItem_f_titleId` ON `VaultItem` (`f_titleId`)",
        "CREATE TABLE IF NOT EXISTS `VaultSidecar` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, " +
            "`storedName` TEXT NOT NULL, `originalRel` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, " +
            "`language` TEXT, `forced` INTEGER NOT NULL, `signs` INTEGER NOT NULL, `keptOriginal` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_VaultSidecar_itemId` ON `VaultSidecar` (`itemId`)",
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) = STATEMENTS_1_2.forEach(db::execSQL)
    }

    val STATEMENTS_2_3: List<String> = listOf(
        "ALTER TABLE `Title` ADD COLUMN `metaLanguage` TEXT",
    )

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) = STATEMENTS_2_3.forEach(db::execSQL)
    }

    /** IMDb episode ratings, and when OMDb last failed to settle a title, for matching while TMDB is off. */
    val STATEMENTS_3_4: List<String> = listOf(
        "ALTER TABLE `Episode` ADD COLUMN `imdbRating` REAL",
        "ALTER TABLE `Title` ADD COLUMN `omdbTriedAt` INTEGER",
    )

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) = STATEMENTS_3_4.forEach(db::execSQL)
    }

    /** The next episode a series has announced, its status, and a trailer, all from TMDB and all nullable. */
    val STATEMENTS_4_5: List<String> = listOf(
        "ALTER TABLE `Title` ADD COLUMN `seriesStatus` TEXT",
        "ALTER TABLE `Title` ADD COLUMN `nextAirDate` TEXT",
        "ALTER TABLE `Title` ADD COLUMN `nextSeason` INTEGER",
        "ALTER TABLE `Title` ADD COLUMN `nextEpisode` INTEGER",
        "ALTER TABLE `Title` ADD COLUMN `trailerKey` TEXT",
    )

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) = STATEMENTS_4_5.forEach(db::execSQL)
    }
}
