package com.cashmemer.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cashmemer.core.model.CurrencyRate
import com.cashmemer.core.model.Member
import com.cashmemer.core.model.Product
import com.cashmemer.core.model.Receipt

@Database(
    entities = [Receipt::class, Product::class, Member::class, CurrencyRate::class],
    version = 1,
    exportSchema = true,
)
abstract class CashMemerDatabase : RoomDatabase() {

    abstract fun receiptDao(): ReceiptDao
    abstract fun productDao(): ProductDao
    abstract fun memberDao(): MemberDao
    abstract fun currencyRateDao(): CurrencyRateDao

    companion object {
        const val NAME = "cashmemer.db"

        @Volatile
        private var instance: CashMemerDatabase? = null

        fun get(context: Context): CashMemerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CashMemerDatabase::class.java,
                    NAME,
                )
                    // Bump `version` and add a Migration before shipping a schema change.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}
