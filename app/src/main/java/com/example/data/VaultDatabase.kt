package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class VaultTypeConverters {
    @TypeConverter
    fun fromMediaType(value: VaultMediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): VaultMediaType = VaultMediaType.valueOf(value)
}

@Database(
    entities = [VaultMediaEntity::class, LockedAppEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(VaultTypeConverters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        fun create(context: Context): VaultDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                "hide_calculator_x_vault.db"
            ).build()
    }
}
