package com.malekfinance

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class FinanceCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** INCOME, EXPENSE, or BOTH. */
    val kind: String,
    val colorHex: String = "#E3C68D",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface FinanceCategoryDao {
    @Query("SELECT * FROM categories ORDER BY isActive DESC, kind ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FinanceCategory>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: FinanceCategory): Long

    @Query("UPDATE categories SET isActive = 0 WHERE id = :categoryId")
    suspend fun archive(categoryId: Long): Int

    @Query("UPDATE categories SET isActive = 1 WHERE id = :categoryId")
    suspend fun restore(categoryId: Long): Int
}

/** Shared category rules used by the editor and transaction review flow. */
object FinanceCategoryPolicy {
    fun normalizedName(raw: String): String? =
        raw.trim().takeIf { it.isNotEmpty() && it.length <= 32 }

    fun isCompatible(categoryKind: String, transactionKind: String): Boolean =
        categoryKind == "BOTH" || categoryKind == transactionKind

    fun nameAlreadyExists(existingNames: Iterable<String>, candidate: String): Boolean =
        existingNames.any { it.trim().equals(candidate.trim(), ignoreCase = true) }
}
