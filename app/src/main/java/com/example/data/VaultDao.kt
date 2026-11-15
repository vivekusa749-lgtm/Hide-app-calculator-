package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_media WHERE mediaType = :type AND isTrashed = 0 ORDER BY createdAt DESC")
    fun observeActiveMedia(type: VaultMediaType): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE mediaType = :type AND isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrashedMedia(type: VaultMediaType): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media ORDER BY createdAt DESC")
    suspend fun getAllMediaOnce(): List<VaultMediaEntity>

    @Query("SELECT * FROM vault_media WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): VaultMediaEntity?

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM vault_media WHERE mediaType = :type")
    fun observeTotalBytesByType(type: VaultMediaType): Flow<Long>

    @Query("SELECT COUNT(*) FROM vault_media WHERE mediaType = :type AND isTrashed = 0")
    fun observeActiveCountByType(type: VaultMediaType): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: VaultMediaEntity)

    @Query("UPDATE vault_media SET isTrashed = 1, trashedAt = :timestamp WHERE id IN (:ids)")
    suspend fun markTrashed(ids: List<String>, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE vault_media SET isTrashed = 0, trashedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreFromTrash(ids: List<String>)

    @Query("DELETE FROM vault_media WHERE id IN (:ids)")
    suspend fun deleteMediaByIds(ids: List<String>)

    @Query("SELECT * FROM locked_apps WHERE isLocked = 1 ORDER BY appName ASC")
    fun observeLockedApps(): Flow<List<LockedAppEntity>>

    @Query("SELECT packageName FROM locked_apps WHERE isLocked = 1")
    suspend fun getLockedPackageNamesOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLockedApp(app: LockedAppEntity)

    @Query("DELETE FROM locked_apps WHERE packageName = :packageName")
    suspend fun removeLockedApp(packageName: String)
}
