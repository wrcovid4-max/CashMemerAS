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
    version = 5,
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

        /** v2 -> v3: the issuing account, printed on page 2. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receipts ADD COLUMN issuerName TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE receipts ADD COLUMN issuerEmail TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v3 -> v4: marks made on the memo in the in-app viewer. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receipts ADD COLUMN annotationsJson TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        /** v4 -> v5: per-product tax rate. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE products ADD COLUMN taxPercent REAL NOT NULL DEFAULT 0"
                )
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}
