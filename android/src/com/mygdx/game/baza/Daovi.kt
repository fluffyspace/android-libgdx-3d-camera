package com.mygdx.game.baza

import androidx.room.*
import com.mygdx.game.baza.Objekt

@Dao
interface ObjektDao {
    @Query("SELECT * FROM Objekt")
    fun getAll(): List<Objekt>

    @Query("SELECT * FROM Objekt WHERE id = :id")
    fun findById(id: Int): List<Objekt>

    @Query("DELETE FROM Objekt WHERE id = :id")
    fun deleteById(id: Int)

    @Insert
    fun insertAll(vararg polja: Objekt): List<Long>

    @Update
    fun update(polje: Objekt)

    @Query("SELECT * FROM Objekt WHERE rowid = :rowId")
    fun findByRowId(rowId: Long): List<Objekt>

    @Query("UPDATE Objekt SET hidden = :hidden WHERE id = :id")
    fun updateHidden(id: Int, hidden: Boolean)

    @Delete
    fun delete(polje: Objekt)
}