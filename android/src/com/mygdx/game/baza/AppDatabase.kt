package com.mygdx.game.baza

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.ref.WeakReference


@Database(entities = [Objekt::class, UserBuilding::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun objektDao(): ObjektDao
    abstract fun userBuildingDao(): UserBuildingDao

    fun setInstanceToNull(){
        instance = WeakReference(null)
    }

    companion object {
        var DATABASE_NAME: String = "database-name"
        // For Singleton instantiation
        @Volatile private var instance: WeakReference<AppDatabase> = WeakReference(null)

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `user_building` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `osmId` INTEGER,
                        `polygonJson` TEXT NOT NULL,
                        `heightMeters` REAL NOT NULL DEFAULT 10.0,
                        `minHeightMeters` REAL NOT NULL DEFAULT 0.0,
                        `name` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Objekt ADD COLUMN osmId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE Objekt ADD COLUMN polygonJson TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE Objekt ADD COLUMN heightMeters REAL NOT NULL DEFAULT 10.0")
                db.execSQL("ALTER TABLE Objekt ADD COLUMN minHeightMeters REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Objekt ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance.get() ?: synchronized(this) {
                instance.get() ?: buildDatabase(context).also { instance = WeakReference(it) }
            }
        }

        fun renewInstance(context: Context): AppDatabase {
            return buildDatabase(context).also { instance = WeakReference(it) }
        }

        // Create and pre-populate the database. See this article for more details:
        // https://medium.com/google-developers/7-pro-tips-for-room-fbadea4bfbd1#4785
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
        }
    }
}
