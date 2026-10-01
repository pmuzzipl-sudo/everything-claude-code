# Minhas Finanças (Android)

Aplicativo Android nativo para controle de finanças pessoais. Funciona 100% offline: os dados ficam salvos
no próprio celular.

## Funcionalidades

- **Receitas e despesas**: lançamentos com valor, descrição, categoria e data. Toque em um item para editar ou excluir.
- **Saldo mensal**: resumo do mês com receitas, despesas (fixas e variáveis) e saldo; navegue entre os meses.
  Os lançamentos do mês ficam separados nas abas **Variáveis**, **Fixas** e **Receitas**.
- **Despesas fixas**: na aba **Fixas**, cadastre uma despesa com valor total, número de parcelas, mês de início e dia
  do vencimento. O app divide o valor e lança cada parcela automaticamente no mês correspondente.
- **Gráficos**: gráfico de rosca com despesas ou receitas por categoria e gráfico de barras dos últimos 6 meses.
  Toque em uma categoria para ver os lançamentos dela, agrupados por descrições parecidas.
- **Comparativo**: na aba Comparativo dos gráficos, compare as despesas de cada categoria mês a mês, com filtros de
  período (3, 6 ou 12 meses), tipo (fixas/variáveis) e categorias, e a variação em relação ao mês anterior.
- **Orçamento mensal**: defina um limite por categoria de despesa. O app avisa quando você atinge 80% do limite e quando ultrapassa.

## Tecnologia

Kotlin, Jetpack Compose (Material 3), Room (SQLite) e Navigation Compose. Android 8.0 (API 26) ou superior.

## Obter o APK

A cada push que altera `finance-app/`, o workflow `.github/workflows/finance-app.yml` roda os testes e compila o APK
(disponível como artefato da execução). Para publicar uma **Release**, execute o workflow manualmente em
*Actions → Finance App APK → Run workflow*. A versão publicada mais recente fica sempre neste link (abra no celular):

https://github.com/pmuzzipl-sudo/everything-claude-code/releases/latest/download/MinhasFinancas.apk

No celular, permita a instalação de apps de fontes desconhecidas e abra o arquivo `.apk`.

## Atualizar o app

1. Aumente `versionCode` (e `versionName`) em `app/build.gradle.kts`.
2. Se mudar as tabelas do banco (`data/Entities.kt`), aumente a `version` do `@Database` e adicione uma migração do Room.
3. Faça o push, publique a release (execução manual do workflow) e instale o novo APK por cima do antigo: os dados são mantidos.

O APK é assinado com a chave fixa `app/release.keystore` (senhas em `keystore.properties`). **Não apague nem troque
esses arquivos**: com outra chave, o Android recusa a atualização e seria preciso desinstalar o app, perdendo os dados.
Como este repositório é público, a chave também é pública; é aceitável apenas para uso pessoal.

## Compilar localmente

Requer JDK 17 e Android SDK (API 34):

```bash
cd finance-app
./gradlew assembleRelease   # APK em app/build/outputs/apk/release/
./gradlew testDebugUnitTest # testes unitários
```
