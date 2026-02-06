package com.mygdx.game.baza

import androidx.room.*

@Dao
interface UserBuildingDao {
    @Query("SELECT * FROM user_building")
    fun getAll(): List<UserBuilding>

    @Query("SELECT * FROM user_building WHERE osmId = :osmId LIMIT 1")
    fun findByOsmId(osmId: Long): UserBuilding?

    @Query("SELECT * FROM user_building WHERE id = :id LIMIT 1")
    fun findById(id: Int): UserBuilding?

    @Insert
    fun insert(userBuilding: UserBuilding): Long

    @Update
    fun update(userBuilding: UserBuilding)

    @Delete
    fun delete(userBuilding: UserBuilding)

    @Query("DELETE FROM user_building WHERE id = :id")
    fun deleteById(id: Int)

    @Query("SELECT osmId FROM user_building WHERE osmId IS NOT NULL")
    fun getAllOsmIds(): List<Long>
}
