package com.cashmemer.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.cashmemer.core.model.CurrencyRate
import com.cashmemer.core.model.Member
import com.cashmemer.core.model.Product
import com.cashmemer.core.model.Receipt
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Query("SELECT * FROM receipts ORDER BY pinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<Receipt>>

    @Query(
        """
        SELECT * FROM receipts
        WHERE (:query = '' OR placeName LIKE '%' || :query || '%'
               OR customerName LIKE '%' || :query || '%'
               OR locationAddress LIKE '%' || :query || '%')
          AND (:from = 0 OR createdAt >= :from)
          AND (:to = 0 OR createdAt <= :to)
        ORDER BY pinned DESC, createdAt DESC
        """
    )
    fun search(query: String, from: Long, to: Long): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<Receipt>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun byId(id: Long): Receipt?

    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    suspend fun allOnce(): List<Receipt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: Receipt): Long

    @Update
    suspend fun update(receipt: Receipt)

    @Delete
    suspend fun delete(receipt: Receipt)

    @Query("DELETE FROM receipts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE receipts SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE receipts SET annotationsJson = :json WHERE id = :id")
    suspend fun setAnnotations(id: Long, json: String)

    @Query("DELETE FROM receipts")
    suspend fun clear()
}

@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE inPriceList = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observePriceList(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): Product?

    @Query("SELECT * FROM products")
    suspend fun allOnce(): List<Product>

    @Upsert
    suspend fun upsert(product: Product): Long

    @Delete
    suspend fun delete(product: Product)

    @Query("UPDATE products SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM products")
    suspend fun clear()
}

@Dao
interface MemberDao {

    @Query("SELECT * FROM members ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Member>>

    @Query("SELECT * FROM members")
    suspend fun allOnce(): List<Member>

    @Upsert
    suspend fun upsert(member: Member): Long

    @Delete
    suspend fun delete(member: Member)

    @Query("DELETE FROM members")
    suspend fun clear()
}

@Dao
interface CurrencyRateDao {

    @Query("SELECT * FROM currency_rates ORDER BY code ASC")
    fun observeAll(): Flow<List<CurrencyRate>>

    @Query("SELECT * FROM currency_rates WHERE code = :code")
    suspend fun byCode(code: String): CurrencyRate?

    @Query("SELECT * FROM currency_rates")
    suspend fun allOnce(): List<CurrencyRate>

    @Query("SELECT code FROM currency_rates WHERE custom = 1")
    suspend fun customCodes(): List<String>

    @Upsert
    suspend fun upsertAll(rates: List<CurrencyRate>)

    @Upsert
    suspend fun upsert(rate: CurrencyRate)

    @Delete
    suspend fun delete(rate: CurrencyRate)

    @Query("SELECT MAX(updatedAt) FROM currency_rates")
    suspend fun lastUpdated(): Long?
}
