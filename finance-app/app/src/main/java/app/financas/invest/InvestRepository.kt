package app.financas.invest

import app.financas.data.listen
import app.financas.data.newCloudId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Carteira de investimentos em users/{uid}/... no Firestore (sincroniza entre aparelhos e funciona offline,
 * como o resto do app): assets/{TICKER}, asset_ops/{id}, portfolio_history/{epochDay} e settings/quotes.
 */
class InvestRepository(uid: String, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val user = db.collection("users").document(uid)
    private val assets = user.collection("assets")
    private val operations = user.collection("asset_ops")
    private val history = user.collection("portfolio_history")
    private val quoteSettings = user.collection("settings").document("quotes")

    fun assets(): Flow<List<Asset>> =
        assets.listen { snap -> snap.documents.mapNotNull { doc -> doc.data?.let { assetFromCloud(doc.id, it) } } }

    fun operations(): Flow<List<AssetOperation>> =
        operations.listen { snap ->
            snap.documents.mapNotNull { doc -> doc.id.toLongOrNull()?.let { id -> doc.data?.let { operationFromCloud(id, it) } } }
        }

    fun history(): Flow<List<PortfolioPoint>> =
        history.listen { snap ->
            snap.documents.mapNotNull { doc -> doc.id.toLongOrNull()?.let { day -> doc.data?.let { pointFromCloud(day, it) } } }
                .sortedBy { it.epochDay }
        }

    /** Define a classe sem apagar a meta já cadastrada (gravação parcial). */
    fun saveAssetClass(ticker: String, assetClass: AssetClass) {
        assets.document(ticker).set(mapOf("assetClass" to assetClass.name), SetOptions.merge())
    }

    /** Grava as metas de vários ativos de uma vez; null remove a meta. Não mexe na classe. */
    fun saveTargets(targets: Map<String, Int?>) {
        if (targets.isEmpty()) return
        val batch = db.batch()
        targets.forEach { (ticker, bps) ->
            batch.set(assets.document(ticker), mapOf("targetBps" to (bps ?: FieldValue.delete())), SetOptions.merge())
        }
        batch.commit()
    }

    fun saveOperation(operation: AssetOperation) {
        val withId = if (operation.id == 0L) operation.copy(id = newCloudId()) else operation
        operations.document(withId.id.toString()).set(withId.toCloud())
    }

    fun deleteOperation(operation: AssetOperation) {
        operations.document(operation.id.toString()).delete()
    }

    /** Remove o ativo e todas as suas operações. */
    suspend fun deleteAsset(ticker: String) {
        val ops = operations.whereEqualTo("ticker", ticker).get().await().documents
        val batch = db.batch()
        ops.forEach { batch.delete(it.reference) }
        batch.delete(assets.document(ticker))
        batch.commit()
    }

    fun saveSnapshot(point: PortfolioPoint) {
        history.document(point.epochDay.toString()).set(point.toCloud())
    }

    /** Chave do brapi.dev, guardada na conta (vale para todos os aparelhos). */
    fun brapiToken(): Flow<String?> = callbackFlow {
        val registration = quoteSettings.addSnapshotListener { snap, error ->
            if (error == null) trySend(snap?.getString("brapiToken")?.takeIf { it.isNotBlank() })
        }
        awaitClose { registration.remove() }
    }

    fun setBrapiToken(token: String?) {
        if (token.isNullOrBlank()) quoteSettings.delete() else quoteSettings.set(mapOf("brapiToken" to token.trim()))
    }
}
