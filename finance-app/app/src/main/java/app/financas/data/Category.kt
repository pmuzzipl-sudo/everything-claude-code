package app.financas.data

/** Categorias fixas de lançamentos. A cor é usada nos gráficos (ARGB). */
enum class Category(val label: String, val type: TransactionType, val color: Long) {
    SALARY("Salário", TransactionType.INCOME, 0xFF2E7D32),
    FREELANCE("Renda extra", TransactionType.INCOME, 0xFF00897B),
    INVESTMENTS("Investimentos", TransactionType.INCOME, 0xFF1565C0),
    OTHER_INCOME("Outras receitas", TransactionType.INCOME, 0xFF6A1B9A),

    FOOD("Alimentação", TransactionType.EXPENSE, 0xFFE65100),
    HOUSING("Moradia", TransactionType.EXPENSE, 0xFF5D4037),
    TRANSPORT("Transporte", TransactionType.EXPENSE, 0xFF0277BD),
    HEALTH("Saúde", TransactionType.EXPENSE, 0xFFC62828),
    EDUCATION("Educação", TransactionType.EXPENSE, 0xFF283593),
    LEISURE("Lazer", TransactionType.EXPENSE, 0xFFAD1457),
    SHOPPING("Compras", TransactionType.EXPENSE, 0xFF7B1FA2),
    BILLS("Contas", TransactionType.EXPENSE, 0xFF455A64),
    OTHER_EXPENSE("Outras despesas", TransactionType.EXPENSE, 0xFF757575);

    companion object {
        fun of(type: TransactionType) = entries.filter { it.type == type }
    }
}

enum class TransactionType { INCOME, EXPENSE }
