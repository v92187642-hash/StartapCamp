package ru.otmetka.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * A cache of the server's data plus marks waiting to be sent. Everything in
 * it can be loaded again, so a schema change simply rebuilds the file
 * (raise [version]); marks not yet sent at that moment would be lost.
 */
@Database(entities = [PlanEntity::class, EntryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "otmetka.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
