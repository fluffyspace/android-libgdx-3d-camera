package com.ingokodba.dragnav.baza

import androidx.room.*
import com.mygdx.game.baza.Objekt

@Dao
interface ObjektDao {
    @Query("SELECT * FROM Objekt")
    fun getAll(): List<Objekt>

    @Query("SELECT * FROM Objekt WHERE id = :id")
    fun findById(id: Int): List<Objekt>

    @Insert
    fun insertAll(vararg polja: Objekt): List<Long>

    @Update
    fun update(polje: Objekt)

    @Query("SELECT * FROM Objekt WHERE rowid = :rowId")
    fun findByRowId(rowId: Long): List<Objekt>

    @Delete
    fun delete(polje: Objekt)
}