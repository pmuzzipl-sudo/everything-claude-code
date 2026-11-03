package app.financas.data

/** Categorias fixas de lançamentos. A cor (ARGB, tons suaves para o tema escuro) é usada nos gráficos. */
enum class Category(val label: String, val type: TransactionType, val color: Long) {
    SALARY("Salário", TransactionType.INCOME, 0xFF7FB89A),
    FREELANCE("Renda extra", TransactionType.INCOME, 0xFF6FA8A0),
    INVESTMENTS("Investimentos", TransactionType.INCOME, 0xFF7C9CC4),
    OTHER_INCOME("Outras receitas", TransactionType.INCOME, 0xFF9E8FC0),

    FOOD("Alimentação", TransactionType.EXPENSE, 0xFFD4A373),
    HOUSING("Moradia", TransactionType.EXPENSE, 0xFFA68A7B),
    TRANSPORT("Transporte", TransactionType.EXPENSE, 0xFF6E9FC0),
    HEALTH("Saúde", TransactionType.EXPENSE, 0xFFC98585),
    EDUCATION("Educação", TransactionType.EXPENSE, 0xFF8C93C7),
    LEISURE("Lazer", TransactionType.EXPENSE, 0xFFC28BA8),
    SHOPPING("Compras", TransactionType.EXPENSE, 0xFFA88BC4),
    BILLS("Contas", TransactionType.EXPENSE, 0xFF8A9BA8),
    OTHER_EXPENSE("Outras despesas", TransactionType.EXPENSE, 0xFF9A9A9A);

    companion object {
        fun of(type: TransactionType) = entries.filter { it.type == type }
    }
}

enum class TransactionType { INCOME, EXPENSE }
