package app.financas.invest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Com menos pontos que isso (com dados por ativo), o histórico de 3 meses é reconstruído. */
private const val MIN_POINTS_FOR_HISTORY = 20

/** Cotações mais novas que isso não são consultadas de novo ao abrir a tela (economiza o limite do plano). */
private const val QUOTE_MAX_AGE_MS = 15 * 60 * 1000L

data class InvestUiState(
    val assets: List<Asset> = emptyList(),
    val operations: List<AssetOperation> = emptyList(),
    val portfolio: PortfolioSummary = PortfolioSummary(emptyList(), emptyList()),
    val history: List<PortfolioPoint> = emptyList(),
    val hasToken: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Momento da cotação mais antiga entre os ativos em carteira. */
    val quotesAtMillis: Long? = null,
)

class InvestmentsViewModel(private val repo: InvestRepository, private val cache: QuoteCache) : ViewModel() {
    private val quotes = MutableStateFlow(cache.load())
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val historyCache = mutableMapOf<String, List<Pair<Long, Double>>>()

    val token: StateFlow<String?> = repo.brapiToken().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val status = combine(token, refreshing, error) { t, r, e -> Triple(t != null, r, e) }

    val state: StateFlow<InvestUiState> =
        combine(repo.assets(), repo.operations(), repo.history(), quotes, status) { assets, ops, history, q, (hasToken, busy, err) ->
            val portfolio = buildPortfolio(assets, ops, q)
            InvestUiState(
                assets = assets,
                operations = ops,
                portfolio = portfolio,
                history = history,
                hasToken = hasToken,
                refreshing = busy,
                error = err,
                quotesAtMillis = portfolio.holdings.mapNotNull { it.quote?.fetchedAtMillis }.minOrNull(),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, InvestUiState())

    /** Atualiza as cotações dos ativos em carteira (só as antigas, a menos que [force]). */
    fun refresh(force: Boolean = false) {
        if (refreshing.value) return
        val apiToken = token.value
        if (apiToken == null) {
            error.value = "Configure a chave do brapi.dev em Configurações para ver as cotações."
            return
        }
        val now = System.currentTimeMillis()
        val tickers = state.value.portfolio.holdings.map { it.ticker }
            .filter { force || (quotes.value[it]?.fetchedAtMillis ?: 0) < now - QUOTE_MAX_AGE_MS }
        if (tickers.isEmpty()) return
        refreshing.value = true
        error.value = null
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            // O plano gratuito aceita 1 ativo por consulta.
            for (ticker in tickers) {
                runCatching { Brapi.quote(ticker, apiToken) }
                    .onSuccess { q -> quotes.update { it + (ticker to q.copy(symbol = ticker)) } }
                    .onFailure { errors += it.message ?: "$ticker: erro ao buscar cotação." }
            }
            cache.save(quotes.value)
            if (errors.isEmpty()) saveSnapshot() else error.value = errors.distinct().joinToString("\n")
            refreshing.value = false
        }
    }

    /** Registra o valor da carteira hoje, para o gráfico de evolução. */
    private fun saveSnapshot() {
        val s = state.value
        val portfolio = buildPortfolio(s.assets, s.operations, quotes.value)
        if (portfolio.holdings.isEmpty()) return
        repo.saveSnapshot(
            PortfolioPoint(LocalDate.now().toEpochDay(), portfolio.valueCents, portfolio.investedCents, assetPoints(portfolio)),
        )
    }

    /** Tenta uma vez por sessão, para não gastar consultas do plano se o brapi tiver pouco histórico. */
    private var reconstructAttempted = false

    /**
     * Na primeira vez, monta os últimos 3 meses do gráfico com os preços passados do brapi (1 consulta por ativo).
     * Depois disso o histórico cresce sozinho com os registros diários.
     */
    fun reconstructHistoryIfNeeded() {
        val s = state.value
        if (reconstructAttempted || token.value == null || s.portfolio.holdings.isEmpty()) return
        // Já existe histórico suficiente com dados por ativo (talvez montado no outro aparelho).
        if (s.history.count { it.assets.isNotEmpty() } >= MIN_POINTS_FOR_HISTORY) return
        reconstructAttempted = true
        viewModelScope.launch {
            val tickers = s.portfolio.holdings.map { it.ticker }
            val closes = tickers.mapNotNull { t -> priceHistory(t).getOrNull()?.takeIf { it.isNotEmpty() }?.let { t to it } }.toMap()
            if (closes.isNotEmpty()) {
                val today = LocalDate.now().toEpochDay()
                val existing = s.history.filter { it.assets.isNotEmpty() }.map { it.epochDay }.toSet() + today
                repo.saveSnapshots(reconstructHistory(s.operations, closes, existing))
            }
        }
    }

    /** Preços de fechamento dos últimos 3 meses (consulta uma vez por sessão). */
    suspend fun priceHistory(ticker: String): Result<List<Pair<Long, Double>>> {
        historyCache[ticker]?.let { return Result.success(it) }
        val apiToken = token.value ?: return Result.failure(QuoteException("Configure a chave do brapi.dev em Configurações."))
        return runCatching { Brapi.quote(ticker, apiToken, withHistory = true).history }
            .onSuccess { historyCache[ticker] = it }
    }

    /** Salva a operação; [assetClass] cria ou altera a classe do ativo. */
    fun saveOperation(operation: AssetOperation, assetClass: AssetClass?) {
        if (assetClass != null) repo.saveAssetClass(operation.ticker, assetClass)
        repo.saveOperation(operation)
    }

    fun deleteOperation(operation: AssetOperation) = repo.deleteOperation(operation)

    fun setAssetClass(ticker: String, assetClass: AssetClass) = repo.saveAssetClass(ticker, assetClass)

    fun setFrozen(ticker: String, frozen: Boolean) = repo.setFrozen(ticker, frozen)

    /** Metas em pontos-base por ticker (null remove). */
    fun saveTargets(targets: Map<String, Int?>) = repo.saveTargets(targets)

    fun deleteAsset(ticker: String) {
        viewModelScope.launch {
            runCatching { repo.deleteAsset(ticker) }
                .onFailure { error.value = "Não foi possível excluir $ticker. Verifique a internet." }
        }
        quotes.update { it - ticker }
    }

    fun setToken(value: String?) {
        repo.setBrapiToken(value)
        error.value = null
    }
}
