package app.financas.invest

import java.time.LocalDate
import java.time.YearMonth

/** Lançamentos de um mês, com os totais do mês. */
data class LedgerMonth(val month: YearMonth, val entries: List<OperationStep>) {
    /** Total investido em compras (com taxas) e posições iniciais. */
    val boughtCents get() = entries.filter { it.operation.kind != OperationKind.SELL }.sumOf { it.operation.amountCents + it.operation.feesCents }
    /** Total recebido em vendas (já sem taxas). */
    val soldCents get() = entries.filter { it.operation.kind == OperationKind.SELL }.sumOf { it.operation.amountCents - it.operation.feesCents }
    val realizedCents get() = entries.sumOf { it.realizedCents }
}

/**
 * Histórico de todas as operações, do mês mais recente para o mais antigo. Cada lançamento traz a posição
 * do ativo logo depois dele (preço médio), calculada com todas as operações, mesmo com filtros ativos.
 */
fun ledger(
    operations: List<AssetOperation>,
    ticker: String? = null,
    kind: OperationKind? = null,
): List<LedgerMonth> {
    val steps = operations.map { it.ticker }.distinct().flatMap { computeSteps(it, operations) }
    return steps
        .filter { (ticker == null || it.operation.ticker == ticker) && (kind == null || it.operation.kind == kind) }
        .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.operation.epochDay)) }
        .map { (month, list) ->
            LedgerMonth(
                month,
                list.sortedWith(
                    compareByDescending<OperationStep> { it.operation.epochDay }
                        .thenByDescending { it.operation.kind.ordinal }
                        .thenByDescending { it.operation.id },
                ),
            )
        }
        .sortedByDescending { it.month }
}
