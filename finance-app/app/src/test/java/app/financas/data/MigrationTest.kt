package app.financas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cria um banco exatamente como a versão 1 do app (1.0 a 1.2) e confere que a migração para a
 * versão 2 mantém os dados e passa na validação de schema do Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    private fun createVersion1Database() {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `description` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `epochDay` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_epochDay` ON `transactions` (`epochDay`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (`category` TEXT NOT NULL, `limitCents` INTEGER NOT NULL, " +
                "PRIMARY KEY(`category`))"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'versao-1')")
        db.execSQL(
            "INSERT INTO transactions (type, amountCents, description, category, epochDay) " +
                "VALUES ('EXPENSE', 4590, 'Mercado', 'FOOD', 20000)"
        )
        db.execSQL("INSERT INTO budgets (category, limitCents) VALUES ('FOOD', 80000)")
        db.version = 1
        db.close()
    }

    @Test
    fun migratesVersion1KeepingData() = runBlocking {
        createVersion1Database()

        val room = Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            .addMigrations(FinanceDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val dao = room.dao()

        val old = dao.transactionsBetween(0, 100000).first().single()
        assertEquals("Mercado", old.description)
        assertEquals(4590L, old.amountCents)
        assertEquals(Category.FOOD, old.category)
        assertNull(old.fixedExpenseId)
        assertEquals(80000L, dao.budget(Category.FOOD)?.limitCents)

        // As novas tabelas e colunas funcionam após a migração.
        val id = dao.insert(
            FixedExpense(
                description = "Aluguel", category = Category.HOUSING, totalCents = 300000,
                installments = 2, startEpochMonth = 2026 * 12, dayOfMonth = 5,
            )
        )
        dao.insertAll(dao.fixedExpense(id)!!.toInstallments())
        assertEquals(3, dao.transactionsBetween(0, 100000).first().size)
        dao.deleteInstallments(id)
        assertEquals(1, dao.transactionsBetween(0, 100000).first().size)

        room.close()
    }
}
