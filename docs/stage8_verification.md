# Stage 8 verification — faction economy

Статус документа: **READY FOR PR**. Stage 8 считается `COMPLETE` только после merge feature-ветки и зелёного post-merge CI на `main`.

## Проверяемый scope

Stage 8 превращает faction-layer в реального экономического актора поверх уже существующих local `SimulationSession`, рынков, production, TradeAI и `EconomicLedger`.

Реализовано:

- persistent treasury и budget policy;
- deterministic liquidity subsidy `treasury -> station wallet`;
- directed faction relations и persistent territory ownership;
- materialized market-access restrictions и authoritative trade gate;
- base stock policies по stable item content ID;
- production policy по stable station archetype / recipe IDs;
- strategic goals `MILITARY` и `EXPANSION`, создающие реальные target-stock demand floors;
- own-station taxes;
- foreign-market tariffs внутри controlled StarSystem;
- все fiscal/subsidy движения используют существующий `WalletComponent.transferTo` и записываются как `MONEY_TRANSFER`;
- world-level save schema сохраняет treasury, diplomacy, territory, policies, goals и fiscal rates;
- Stage-7 world schema v1 и Stage-8 treasury-only schema v2 мигрируются нейтрально без создания отсутствующих денег или strategic state.

## Архитектурные границы

### Один финансовый контур

Faction treasury не является декоративным счётчиком. Runtime `FactionEconomicAccount` владеет обычным authoritative wallet. Subsidy, tax и tariff физически переводят milli-credits между treasury и существующими station wallets.

Нормальные faction decisions не используют `MONEY_SOURCE`/`MONEY_SINK`; поэтому они не создают и не уничтожают деньги.

### Strategic demand использует существующую экономику

`FactionStrategicPolicyEngine` не создаёт товары и не выполняет виртуальные поставки. Он только:

1. повышает обычный `MarketComponent.targetStock` до persistent demand floor;
2. при необходимости переключает обычный data-driven production recipe;
3. помечает рынок dirty.

После этого цены, production и логистика работают существующими `MarketSystem`, `ProductionSystem` и `TradeAISystem`.

Demand policy идемпотентна: повторное применение одного strategic state не наращивает target stock бесконечно и не сбрасывает progress production, если recipe фактически не изменился.

### Market access не дублирует route planner

Stage-5 `TradeRoutePlanner` и `MarketDirectory` не переписывались ради diplomacy. Persistent strategic policy материализуется в transient `FactionMarketAccessComponent` на рынках.

Доступ защищён в двух местах:

- post-planner `FactionMarketAccessSystem` сбрасывает запрещённый persistent route до следующего movement tick;
- `TradeController` повторно проверяет access непосредственно перед authoritative buy/sell mutation.

Поэтому restored/stale route не может обойти изменившуюся diplomacy policy.

### Fiscal policy

`WorldSimulation.applyFiscalPolicy` обходит системы по `StarSystemId`, а станции по `EntityId`.

- tax применяется к собственным faction market stations;
- tariff применяется к чужим faction markets только внутри StarSystem, контролируемой collector faction;
- levy берётся только с station wallet surplus выше protected liquidity reserve;
- basis-points расчёт не использует floating money и защищён от overflow;
- каждое успешное списание — station wallet -> faction treasury + `MONEY_TRANSFER` ledger entry.

## Persistence

Текущая world schema: **v3**.

- v1 — Stage 7: topology + system states;
- v2 — treasury-only Stage-8 intermediate layout;
- v3 — treasury + diplomacy/territory + stock/production policies + strategic goals + fiscal rates.

Поскольку v3 создавалась внутри ещё не merged Stage-8 branch, новые Stage-8 поля добавлялись в текущую v3 без выпуска промежуточной v4. Public compatibility с ранее merged состоянием сохраняется через чтение v1/v2.

## Exact-head verification

Functional verification head перед этим documentation-only commit:

`cfb963b2051b81038b527b5cf8c2abccdb909313`

GitHub Actions run: `31639685129`.

`./mvnw --batch-mode --no-transfer-progress clean verify`:

- **289 tests**;
- failures: **0**;
- errors: **0**;
- skipped: **0**;
- Javadoc `failOnWarnings`: success;
- JaCoCo thresholds: success;
- shaded desktop JAR: success;
- reports/artifacts upload: success.

## Focused acceptance evidence

### Treasury / subsidy

Tests verify that:

- faction treasury survives save/load;
- Stage-7 migration does not invent treasury money;
- subsidy decreases treasury by exactly the amount credited to station wallets;
- each transfer appears in ledger as `MONEY_TRANSFER`;
- deterministic station ordering is stable.

### Diplomacy / territory / market access

Tests verify that:

- territory ownership survives save/load;
- one StarSystem cannot have two strategic owners;
- directed relations materialize station access rules;
- denied buy does not change inventory, wallets or ledger;
- allowed relation restores trade access;
- persistent route to a newly forbidden market is reset before movement/transaction.

### Production / stock / military / expansion demand

Tests verify that:

- strategic policies and goals round-trip through `WorldStateCodec`;
- military goal raises real weapons demand;
- expansion goal raises real food demand;
- base policy raises energy demand;
- data-driven production policy restores the desired recipe;
- changed target stock affects normal market pricing;
- repeated policy application is idempotent.

### Taxes / tariffs

Tests verify that:

- own-station tax is physically collected;
- foreign territory tariff is physically collected only in controlled territory;
- treasury increase exactly equals tax + tariff report totals;
- total authoritative money (`entity wallets + faction treasuries`) is unchanged;
- each affected station creates a `MONEY_TRANSFER` ledger record;
- fiscal rates survive world codec round-trip.

### End-to-end Definition of Done

`Stage8FactionEconomyEndToEndTest` executes one coherent faction decision sequence:

1. conserved redistribution creates both taxable surplus and a liquidity deficit without emission;
2. military/expansion strategic demand and production policy are applied;
3. tax and territory tariff are collected;
4. subsidy restores a deficient owned market;
5. normal world simulation continues for 200 ticks.

The test asserts:

- strategic demand is visible in normal market target stock;
- normal `TRADE` ledger activity continues;
- fiscal/subsidy `MONEY_TRANSFER` entries exist;
- every inventory remains non-negative;
- total authoritative money is unchanged before/after faction decisions and after continued simulation.

This directly satisfies the Stage-8 Definition of Done: faction decisions physically change demand, production, logistics and financial flows without creating a parallel economy.

## Remaining gate

No new production functionality is planned before merge.

Required sequence:

1. run push CI on final documentation HEAD;
2. open Stage-8 draft PR on that exact SHA;
3. require independent `pull_request` CI success on the same SHA;
4. mark PR ready and merge with `expected_head_sha`;
5. require post-merge CI success on `main`;
6. only then update `docs/development_roadmap.md`: Stage 8 `COMPLETE`, Stage 9 `ACTIVE`.
