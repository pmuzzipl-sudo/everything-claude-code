package app.financas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Transaction::class, Budget::class], version = 1, exportSchema = false)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        @Volatile
        private var instance: FinanceDatabase? = null

        fun get(context: Context): FinanceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "financas.db",
                ).build().also { instance = it }
            }
    }
}
