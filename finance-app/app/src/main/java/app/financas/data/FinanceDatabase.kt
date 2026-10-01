package app.financas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Transaction::class, Budget::class, FixedExpense::class], version = 2, exportSchema = false)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        /** v1 -> v2: despesas fixas parceladas. Mantém todos os lançamentos existentes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `fixedExpenseId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `installment` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `installmentCount` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fixed_expenses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`totalCents` INTEGER NOT NULL, " +
                        "`installments` INTEGER NOT NULL, " +
                        "`startEpochMonth` INTEGER NOT NULL, " +
                        "`dayOfMonth` INTEGER NOT NULL)"
                )
            }
        }

        @Volatile
        private var instance: FinanceDatabase? = null

        fun get(context: Context): FinanceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "financas.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
