# Minhas Finanças (Android)

Aplicativo Android nativo para controle de finanças pessoais. Funciona 100% offline: os dados ficam salvos
no próprio celular.

## Funcionalidades

- **Receitas e despesas**: lançamentos com valor, descrição, categoria e data. Toque em um item para editar ou excluir.
- **Saldo mensal**: resumo do mês com receitas, despesas (fixas e variáveis) e saldo; navegue entre os meses.
  Os lançamentos do mês ficam separados nas abas **Variáveis**, **Fixas** e **Receitas**.
- **Fixas e recorrentes**: na aba **Fixas**, cadastre despesas e receitas que se repetem: valor mensal (com ou sem
  data de fim) ou valor total parcelado. O app lança cada mês automaticamente e mostra o quanto os próximos 6 meses
  já estão comprometidos.
- **Pago/recebido e contas a vencer**: marque cada lançamento como pago (ou recebido). A tela inicial mostra o saldo
  previsto para o fim do mês, o realizado até agora e as contas atrasadas ou que vencem nos próximos 7 dias, com
  lembrete diário por notificação.
- **Busca**: encontre lançamentos por descrição ou categoria, tipo, valor e período.
- **Bloqueio**: opcionalmente, o app pede a digital ou o PIN do aparelho para abrir.
- **Investimentos**: área separada (seletor Finanças | Investimentos no topo) para a carteira de ETFs/ações da B3.
  Cadastre a posição inicial (quantidade e preço médio) e registre compras e vendas: o preço médio é recalculado
  pela regra da Receita (taxas entram no custo; vendas não mudam o preço médio e geram lucro realizado). Cotações
  com atraso pelo [brapi.dev](https://brapi.dev) (chave gratuita em Configurações), divisão por classe, evolução do
  patrimônio e gráfico de 3 meses de cada ativo.
- **Metas de alocação**: defina a % que cada ativo deve ter na carteira; o app mostra a participação atual pela
  cotação, quanto falta comprar ou quanto está acima da meta, e sugere como dividir um aporte entre os ativos.
  Ativos **congelados** (sem novos aportes) continuam no patrimônio, mas ficam fora das metas.
- **Sincronização**: os dados ficam na conta Google do usuário (Firebase) e aparecem em todos os aparelhos.
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
