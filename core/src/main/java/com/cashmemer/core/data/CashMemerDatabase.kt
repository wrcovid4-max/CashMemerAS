package com.cashmemer.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cashmemer.core.model.CurrencyRate
import com.cashmemer.core.model.Member
import com.cashmemer.core.model.Product
import com.cashmemer.core.model.Receipt

@Database(
    entities = [Receipt::class, Product::class, Member::class, CurrencyRate::class],
    version = 2,
    exportSchema = true,
)
abstract class CashMemerDatabase : RoomDatabase() {

    abstract fun receiptDao(): ReceiptDao
    abstract fun productDao(): ProductDao
    abstract fun memberDao(): MemberDao
    abstract fun currencyRateDao(): CurrencyRateDao

    companion object {
        const val NAME = "cashmemer.db"

        /** v1 -> v2: cash tendered and the coordinates behind the address. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receipts ADD COLUMN cashGiven REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE receipts ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE receipts ADD COLUMN longitude REAL")
            }
        }

        @Volatile
        private var instance: CashMemerDatabase? = null

        fun get(context: Context): CashMemerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CashMemerDatabase::class.java,
                    NAME,
                )
                    // Real migrations, not destructive fallback — a shop's
                    // history must survive an app update.
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}
