package com.alexaplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alexaplayer.data.local.dao.PlaylistDao
import com.alexaplayer.data.local.dao.RecentSearchDao
import com.alexaplayer.data.local.dao.SongDao
import com.alexaplayer.data.local.entity.PlaylistEntity
import com.alexaplayer.data.local.entity.PlaylistSongEntity
import com.alexaplayer.data.local.entity.RecentSearchEntity
import com.alexaplayer.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        RecentSearchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AlexaDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun recentSearchDao(): RecentSearchDao

    companion object {
        private const val NAME = "alexa-player.db"

        fun build(context: Context): AlexaDatabase =
            Room.databaseBuilder(context.applicationContext, AlexaDatabase::class.java, NAME)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Playlist membership is ordered by position and always sorted on read.
                        db.execSQL("PRAGMA foreign_keys=ON")
                    }
                })
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // A stale or half-written file must not take the app down on launch; the library
                // is rebuilt from MediaStore anyway, so dropping it is cheaper than crashing.
                .fallbackToDestructiveMigration()
                .build()
    }
}
