package com.mygdx.game.baza

import android.content.Context
import androidx.room.*
import java.lang.ref.WeakReference


@Database(entities = arrayOf(Objekt::class), version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun objektDao(): ObjektDao

    fun setInstanceToNull(){
        instance = WeakReference(null)
    }

    companion object {
        var DATABASE_NAME: String = "database-name"
        // For Singleton instantiation
        @Volatile private var instance: WeakReference<AppDatabase> = WeakReference(null)

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
                    .build()
        }
    }
}