package com.alexaplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alexaplayer.data.local.entity.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {

    @Query("SELECT * FROM recent_searches ORDER BY searched_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clear()

    /** Keeps only the newest [limit] entries. */
    @Query(
        """
        DELETE FROM recent_searches
        WHERE query NOT IN (
            SELECT query FROM recent_searches ORDER BY searched_at DESC LIMIT :limit
        )
        """,
    )
    suspend fun trim(limit: Int)
}
