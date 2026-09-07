package com.becky.bridge.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for BECKY's persistent Memory (Fase 3.2, Bloque 2).
 *
 * Holds ONLY [MemoryEntity] rows - Identity and small Internal State stay
 * on Jetpack DataStore (see [com.becky.bridge.identity.DataStoreIdentityRepository]),
 * per the approved architecture ("Room para Memory persistente, DataStore
 * unicamente para Identity/Internal State pequeno"). No external/cloud
 * database is used anywhere in this project.
 */
@Database(entities = [MemoryEntity::class], version = 1, exportSchema = false)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        private const val DATABASE_NAME = "becky_memory.db"

        @Volatile
        private var INSTANCE: MemoryDatabase? = null

        /**
         * App-wide singleton, matching the
         * [com.becky.bridge.bluetooth.BridgeRepository.getInstance] /
         * [com.becky.bridge.identity.IdentityManager.getInstance] pattern
         * already used elsewhere in the project.
         */
        fun getInstance(context: Context): MemoryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
    }
}
