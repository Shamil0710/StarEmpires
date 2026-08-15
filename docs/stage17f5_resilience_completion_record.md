# Stage 17F.5 — resilience policy completion record

**Статус:** aggregate acceptance candidate  
**Implementation PRs:** #115–#120  
**Следующий roadmap slice после aggregate gate:** Stage 17F.6 policy feedback / anti-oscillation

## Цель

Stage 17F.5 превращает уже измеряемую Stage-17E structural economic dependence в государственные resilience-решения без отдельной «магической» экономики.

Базовая причинная цепочка:

```text
physical inventories / market targets / production inputs
+ legal market access / tariffs
+ jump topology / alternative paths
+ faction treasury / construction capability
→ Stage-17E structural dependence diagnostics
→ Stage-17F doctrine
→ Stage-17F.5 resilience decisions
→ ordinary stock policy / supplier choice / route choice / production / construction
→ physical economic consequences
```

## Реализованные механизмы

### 1. Minimum strategic buffers — PR #115

`FactionResiliencePlanner` переводит uncovered requirement и `economicResiliencePriority` в обычный strategic stock floor.

Stock floor не создаёт cargo. Он materialize-ится через общий `FactionStockProductionPolicyState` / `FactionStrategicPolicyEngine`, после чего обычные market/logistics systems должны физически заполнить запас.

### 2. Diversified suppliers — PR #116

`FactionResilientGalacticTradePlanner` может выбрать менее концентрированного **реального** supplier для собственного inbound procurement, если фактическая потеря ожидаемой прибыли укладывается в doctrine-weighted replacement-risk budget.

Нет synthetic supplier, flat bonus или fictitious route cost.

### 3. Local production and capacity gaps — PR #117

`FactionLocalProductionPlanner` рекомендует только canonical recipe реально принадлежащей faction production capacity.

Если подходящей мощности нет, результатом является explicit capacity gap; произвольный recipe не назначается произвольной фабрике.

### 4. Physical capacity construction — PR #118

Actionable capacity gap + реальный own-market deficit в controlled system может породить ordinary construction recommendation.

Execution использует существующие Stage-9/16 boundaries:

```text
real catalog producer
→ legal construction project
→ treasury → project wallet funding
→ physical material delivery
→ build time
→ ordinary station materialization
```

Treasury reserve и construction authorization могут полностью заблокировать проект до денежной mutation.

### 5. Redundant jump routes — PR #119

`GalacticPathPlanner` умеет искать deterministic edge-disjoint alternative к primary jump path.

Resilience может выбрать более длинный физический маршрут только в пределах измеримого profit-sacrifice budget. `InterSystemTradeJob` не изменён и проходит выбранные jumps обычным FSM.

Если edge-disjoint path отсутствует, vulnerability остаётся реальной.

### 6. Critical-item import limits — PR #120

Hard autonomous procurement guard применяется только к structurally critical import, для которого partner loss оставляет реальный uncovered requirement и resilience plan уже рекомендует local production.

Policy использует current-snapshot partner supply concentration и doctrine-derived maximum partner share. Она не выдумывает historical import provenance.

Over-concentrated foreign supplier исключается до ordinary economic scoring. Domestic supply и trade к foreign consumer не блокируются. Если разрешённого supplier нет, shortage сохраняется физически.

## Цена resilience

Stage 17F.5 не использует абстрактный resilience multiplier. Цена возникает только через обычную simulation:

- больше капитала связано в strategic buffers;
- закупка у менее выгодного supplier снижает expected profit;
- edge-disjoint route может увеличить ETA, risk и route cost;
- local production использует реальные inputs и production time;
- отсутствующая capacity требует treasury funding, материалов и build time;
- hard import limit может оставить реальный shortage, если приемлемой альтернативы нет.

## Aggregate acceptance contract

Stage 17F.5 считается COMPLETE только если aggregate gate подтверждает одновременно:

1. один и тот же physical dependence snapshot приводит к согласованным buffer/diversification/local-production/route/import решениям;
2. policy thresholds выводятся из общей doctrine и Stage-17E diagnostics, а не из параллельных hidden ratings;
3. read-only analysis и policy assessment byte-for-byte не мутируют world state;
4. stock/production authoring и explicit apply не создают cargo или money;
5. dedicated physical acceptance #115–#120 остаётся зелёным для каждого отдельного executor/path;
6. full Java-17 CI, JaCoCo, strict Javadocs и packaging проходят на aggregate PR и после merge в `main`.

## Следующий этап

Stage 17F.6 должен добавить bounded policy review cadence и hysteresis поверх уже существующих 17F.1–17F.5 command boundaries.

Он не должен переизобретать fiscal, stock, supplier, route или construction mechanisms. Его задача — решить **когда** AI пересматривает policy и насколько сильным должно быть изменение сигнала, чтобы решение действительно переключилось.
