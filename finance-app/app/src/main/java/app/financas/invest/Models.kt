package app.financas.invest

import java.math.BigDecimal
import java.math.RoundingMode

/** Classe do ativo, para a divisão da carteira. Cores suaves no padrão do tema escuro. */
enum class AssetClass(val label: String, val color: Long) {
    FIXED_INCOME("Renda fixa", 0xFF7C9CC4),
    EQUITY("Renda variável", 0xFF7FB89A),
    CRYPTO("Cripto", 0xFFD4A373),
    OTHER("Outros", 0xFF9A9A9A),
}

/**
 * [targetBps] = meta de participação na carteira em centésimos de ponto percentual (2500 = 25%).
 * [frozen] = sem novos aportes: continua no patrimônio, mas fica fora das metas e das sugestões de aporte.
 */
data class Asset(val ticker: String, val assetClass: AssetClass, val targetBps: Int? = null, val frozen: Boolean = false)

enum class OperationKind(val label: String) {
    /** Posição que o usuário já tinha antes de usar o app (quantidade e preço médio). */
    INITIAL("Posição inicial"),
    BUY("Compra"),
    SELL("Venda"),
}

/**
 * Operação com um ativo. [amountCents] é quantidade × preço (sem taxas); [feesCents] são as taxas da
 * corretora/B3, que entram no custo das compras e saem do valor recebido nas vendas.
 */
data class AssetOperation(
    val id: Long = 0,
    val ticker: String,
    val kind: OperationKind,
    val quantity: Long,
    val amountCents: Long,
    val feesCents: Long = 0,
    val epochDay: Long,
) {
    /** Preço por cota em reais. */
    val unitPrice: Double get() = if (quantity == 0L) 0.0 else amountCents / 100.0 / quantity
}

/** Situação de um ativo depois de aplicar as operações em ordem. */
data class Position(
    val ticker: String,
    val quantity: Long = 0,
    /** Custo total das cotas em carteira (base do preço médio). */
    val costCents: Long = 0,
    /** Lucro (ou prejuízo) já realizado em vendas. */
    val realizedCents: Long = 0,
) {
    /** Preço médio em reais. */
    val averagePrice: Double get() = if (quantity == 0L) 0.0 else costCents / 100.0 / quantity
}

/** Uma operação com a posição logo depois dela (para o histórico). */
data class OperationStep(val operation: AssetOperation, val after: Position, val realizedCents: Long)

/** Ordem de aplicação: data; no mesmo dia, posição inicial, depois compras, depois vendas. */
private val operationOrder = compareBy<AssetOperation> { it.epochDay }.thenBy { it.kind.ordinal }.thenBy { it.id }

/**
 * Aplica as operações e calcula preço médio e lucro realizado, pela regra da Receita:
 * compras somam quantidade e custo (com taxas); vendas tiram custo proporcional, sem mudar o preço médio,
 * e o resultado é (valor da venda - taxas) - custo das cotas vendidas.
 */
fun computeSteps(ticker: String, operations: List<AssetOperation>): List<OperationStep> {
    var position = Position(ticker)
    return operations.filter { it.ticker == ticker }.sortedWith(operationOrder).map { op ->
        var realized = 0L
        position = when (op.kind) {
            OperationKind.INITIAL, OperationKind.BUY -> position.copy(
                quantity = position.quantity + op.quantity,
                costCents = position.costCents + op.amountCents + op.feesCents,
            )
            OperationKind.SELL -> {
                val sold = op.quantity.coerceAtMost(position.quantity)
                if (sold == 0L) {
                    position
                } else {
                    val soldCost = BigDecimal(position.costCents).multiply(BigDecimal(sold))
                        .divide(BigDecimal(position.quantity), 0, RoundingMode.HALF_UP).toLong()
                    // Venda parcial (sold < quantidade pedida) recebe proporcionalmente.
                    val proceeds = if (sold == op.quantity) op.amountCents - op.feesCents
                    else (op.amountCents - op.feesCents) * sold / op.quantity
                    realized = proceeds - soldCost
                    val remaining = position.quantity - sold
                    position.copy(
                        quantity = remaining,
                        costCents = if (remaining == 0L) 0 else position.costCents - soldCost,
                        realizedCents = position.realizedCents + realized,
                    )
                }
            }
        }
        OperationStep(op, position, realized)
    }
}

fun computePosition(ticker: String, operations: List<AssetOperation>): Position =
    computeSteps(ticker, operations).lastOrNull()?.after ?: Position(ticker)

/** Cotação vinda do brapi. [history] = (epoch day, preço de fechamento), quando pedida. */
data class Quote(
    val symbol: String,
    val name: String?,
    val price: Double,
    val changePercent: Double?,
    val fetchedAtMillis: Long,
    val history: List<Pair<Long, Double>> = emptyList(),
)

/** Ponto da evolução da carteira: valor de mercado e custo investido em um dia. */
data class PortfolioPoint(val epochDay: Long, val valueCents: Long, val investedCents: Long)

/** Ticker como a B3 usa: maiúsculas, sem espaços (ex.: "ivvb11 " -> "IVVB11"). */
fun normalizeTicker(input: String): String = input.trim().uppercase().filter { it.isLetterOrDigit() }
