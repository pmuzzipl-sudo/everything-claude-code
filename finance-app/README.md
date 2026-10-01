# Minhas Finanças (Android)

Aplicativo Android nativo para controle de finanças pessoais. Funciona 100% offline: os dados ficam salvos
no próprio celular.

## Funcionalidades

- **Receitas e despesas**: lançamentos com valor, descrição, categoria e data. Toque em um item para editar ou excluir.
- **Saldo mensal**: resumo do mês com receitas, despesas e saldo; navegue entre os meses.
- **Gráficos**: gráfico de rosca com as despesas por categoria e gráfico de barras com receitas e despesas dos últimos 6 meses.
- **Orçamento mensal**: defina um limite por categoria de despesa. O app avisa quando você atinge 80% do limite e quando ultrapassa.

## Tecnologia

Kotlin, Jetpack Compose (Material 3), Room (SQLite) e Navigation Compose. Android 8.0 (API 26) ou superior.

## Obter o APK

O workflow `.github/workflows/finance-app.yml` compila o APK a cada push que altera `finance-app/`.
Abra a aba **Actions** do repositório, entre na execução mais recente de **Finance App APK** e baixe o artefato
`MinhasFinancas-apk` (um `.zip` com o `app-release.apk`).

No celular, permita a instalação de apps de fontes desconhecidas e abra o arquivo `.apk`.

> O APK de release é assinado com a chave de debug, o que é suficiente para instalar no seu aparelho.
> Para publicar na Play Store, configure uma keystore própria em `app/build.gradle.kts`.

## Compilar localmente

Requer JDK 17 e Android SDK (API 34):

```bash
cd finance-app
./gradlew assembleRelease   # APK em app/build/outputs/apk/release/
./gradlew testDebugUnitTest # testes unitários
```
