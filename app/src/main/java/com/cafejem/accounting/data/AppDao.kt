package com.cafejem.accounting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGuest(guest: Guest): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(rate: MealRate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFinanceSetting(setting: FinanceSetting)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: MonthlyExpense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addEntry(entry: MealEntry)

    @Query("SELECT * FROM Guest ORDER BY name")
    fun guestsFlow(): Flow<List<Guest>>

    @Query("SELECT * FROM Guest ORDER BY name")
    suspend fun guests(): List<Guest>

    @Query("SELECT * FROM MealRate")
    suspend fun rates(): List<MealRate>

    @Query("SELECT * FROM FinanceSetting WHERE id = 1")
    suspend fun financeSetting(): FinanceSetting?

    @Query("SELECT * FROM MonthlyExpense WHERE month = :month ORDER BY id DESC")
    fun monthExpensesFlow(month: String): Flow<List<MonthlyExpense>>

    @Query("SELECT * FROM MonthlyExpense WHERE month = :month ORDER BY id DESC")
    suspend fun monthExpenses(month: String): List<MonthlyExpense>

    @Query("SELECT * FROM MealEntry WHERE month = :month ORDER BY day DESC, id DESC")
    fun monthEntriesFlow(month: String): Flow<List<MealEntry>>

    @Query("SELECT * FROM MealEntry WHERE month = :month ORDER BY day DESC, id DESC")
    suspend fun monthEntries(month: String): List<MealEntry>

    @Query("DELETE FROM MealEntry WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("DELETE FROM MealEntry WHERE guestId = :guestId AND month = :month AND day = :day AND mealType = :mealType")
    suspend fun deleteEntriesForGuestDayMeal(guestId: Long, month: String, day: Int, mealType: MealType)

    @Query("DELETE FROM Guest WHERE id = :guestId")
    suspend fun deleteGuest(guestId: Long)
}
