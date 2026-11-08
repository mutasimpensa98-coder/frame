package com.frameglass.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Volume::class, RootFolder::class, MediaFile::class, Title::class, Episode::class, WatchState::class,
        SeriesPrefs::class, FilePrefs::class, Person::class, TitlePerson::class, Genre::class, TitleGenre::class,
        CollectionEntity::class, Rating::class, IntroSegment::class, AnimeMap::class, LookupQueue::class,
        ScanLog::class, SearchIndex::class, GroupAlias::class, AudioDeviceDelay::class, PlaySession::class,
        ExcludedFolder::class, VaultItem::class, VaultSidecar::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class FrameDb : RoomDatabase() {
    abstract fun scan(): ScanDao
    abstract fun titles(): TitleDao
    abstract fun watch(): WatchDao
    abstract fun meta(): MetaDao
    abstract fun vault(): VaultDao

    companion object {
        const val NAME = "frame-library.db"

        /** Every later schema version adds a tested Migration here. Nothing may ever fall back to a wipe. */
        fun open(context: Context): FrameDb =
            Room.databaseBuilder(context, FrameDb::class.java, NAME)
                .addMigrations(
                    Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5,
                )
                .build()
    }
}
