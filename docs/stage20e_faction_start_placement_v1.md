# Stage 20E — faction-start acceptance and bounded placement v1

> Статус: **IMPLEMENTATION SLICE — DOES NOT CLOSE ALL OF STAGE 20E**  
> Родительский план: `docs/stage20_physical_world_generation_plan.md`  
> Предшествующий слой: `docs/stage20e_faction_start_dependency_diagnostics_v1.md`

## 1. Цель

Этот slice реализует следующий обязательный порядок Stage 20E:

```text
topology + resources + facilities
→ viability/dependency diagnostics
→ faction-start candidate evaluation
→ bounded deterministic placement
```

Он не создаёт новую faction simulation и не меняет уже сгенерированную экономику ради удобного старта.

## 2. Authority boundary

Candidate evaluator получает только уже вычисленный:

```text
Stage20FactionStartDependencyDiagnostics.Report
```

и versioned:

```text
Stage20FactionStartAcceptanceProfile
```

Он не имеет права:

- добавлять resource occurrence;
- увеличивать reserve;
- создавать facility;
- увеличивать throughput;
- добавлять supplier;
- добавлять topology edge;
- уменьшать route time;
- подставлять ownership;
- подставлять inventory buffer;
- подставлять delivered monetary cost.

Если physical/economic state плохой, candidate отклоняется.

Если hard acceptance требует authority, которой ещё нет, результат обязан быть:

```text
UNRESOLVED_AUTHORITY
```

а не `REJECTED` и не автоматически `ACCEPTED`.

## 3. Versioned v1 acceptance profile

`Stage20FactionStartAcceptanceProfile.current()` задаёт ordinary procedural generation policy.

Текущая v1 policy:

```text
dominant import dependency                    >= 0.50
max supplier-capacity HHI                     = 0.80
max final-gateway route HHI                   = 0.80
max largest critical-gateway share            = 0.80
max accessible recoverable-reserve HHI        = 0.80
min external suppliers for any import         = 1
min external suppliers for dominant import    = 2
min edge-disjoint path floor for dominant     = 2
min separation between faction starts         = 2 ordinary hops
bounded placement search                      = 10,000 candidate nodes
```

Это structural safety bounds, а не faction bonuses и не окончательный Stage-22 balance.

`stage22ReviewRequired = true` сохраняется явно.

## 4. Hard viability

Каждый essential commodity обязан иметь:

```text
totalReachableSupplyKgPerSecond >= requiredKgPerSecond
throughputHeadroomKgPerSecond >= 0
```

То есть normal start не принимается, если theoretical physical delivery уже на bootstrap меньше устойчивой essential потребности.

Shortage как gameplay state остаётся допустимым в специально поддерживаемом scenario design, но ordinary procedural start не должен случайно начинаться в unrecoverable throughput deficit.

## 5. Strategic dependency не запрещена

Local self-sufficiency не является requirement.

Candidate может иметь существенную import dependency и по этой причине получает ненулевой `selectionPenalty`.

Этот penalty:

- не меняет runtime production;
- не меняет price;
- не меняет resource reserve;
- не даёт faction modifier;
- используется только как deterministic ordering среди уже acceptable candidates.

Таким образом asymmetric starts сохраняются.

## 6. Import-dominant resilience gate

При import dependency от 50% и выше v1 дополнительно требует:

- минимум двух external suppliers;
- supplier-capacity HHI не выше 0.80;
- route/final-gateway HHI не выше 0.80;
- largest critical gateway share не выше 0.80;
- proven edge-disjoint path floor = 2;
- accessible finite reserve HHI не выше 0.80.

Цель — не сделать старт безопасным, а исключить accidental civilization-critical single-point dependency в обычном procedural seed.

## 7. Optional upstream authorities в текущем v1

Текущий профиль пока не делает hard blocker из:

- delivered monetary cost authority;
- generated/current inventory buffer authority;
- reserve ownership authority.

Причина архитектурная: dependency diagnostics уже умеет сохранять эти данные как unresolved, но Stage 20 ещё не закрыл для них полный production-authoritative bootstrap source во всех generated worlds.

Нельзя закрывать этот пробел выдуманным cost, stock или owner.

Поэтому current v1 содержит явные flags:

```text
requireDeliveredCostAuthority = false
requireBufferAuthority        = false
requireOwnershipAuthority     = false
```

Следующий Stage-20E closeout обязан либо привязать реальные authorities и ужесточить versioned profile, либо оставить конкретную величину явно unresolved и не заявлять полный Stage-20E DoD.

## 8. Stable faction identity

Placement использует существующий canonical contract:

```text
WorldFactionIdentityState.normalizeStableId(...)
→ faction.*
```

Новый parallel `FactionId` не вводится.

Placement result хранит:

```text
stableFactionId
+ StarSystemId
+ candidateSelectionPenalty evidence
```

Materialization runtime faction state/territory/markets не выполняется этим классом.

## 9. Bounded deterministic placement

`Stage20FactionStartPlacementGenerator`:

1. сортирует stable faction IDs;
2. принимает только `ACCEPTED` candidate evaluations того же profile version;
3. сортирует candidates по selection penalty;
4. использует root seed только для deterministic tie-breaking равных/близких ordering cases;
5. запрещает один start system для двух factions;
6. проверяет hop separation только по authoritative `topology.neighbors(...)`;
7. использует bounded backtracking;
8. никогда не меняет candidate state ради успешного assignment.

## 10. Placement outcomes

Возможны только:

```text
ACCEPTED
REJECTED_SEED
UNRESOLVED_AUTHORITY
```

`REJECTED_SEED` может быть вызван:

- недостаточным количеством accepted candidates;
- невозможностью соблюсти hop separation;
- исчерпанием bounded deterministic search budget.

`UNRESOLVED_AUTHORITY` используется, когда accepted candidates недостаточно именно потому, что необходимые candidate decisions остаются unresolved.

## 11. No hidden repair

Placement generator не имеет repair path вида:

```text
bad candidate
→ give resource
→ add station
→ add edge
→ lower requirement
→ accept
```

Допустимый higher-level world-generation flow:

```text
candidate set fails
→ reject generated seed/world candidate
→ caller may deterministically try another seed under bounded generation policy
```

Любой такой outer retry должен оставаться отдельным generation layer и не менять уже оценённый seed.

## 12. Acceptance tests этого slice

Обязательные regression scenarios:

1. physically viable, strategically import-dependent candidate проходит;
2. physical throughput deficit отклоняется;
3. dominant single-supplier/single-gateway dependency отклоняется;
4. required missing authority возвращает `UNRESOLVED_AUTHORITY`;
5. одинаковый seed + world + factions даёт идентичный placement;
6. две factions не получают одну систему;
7. configured ordinary-hop separation соблюдается;
8. невозможная separation отклоняет seed без изменения topology/resources;
9. недостаток accepted candidates из-за unresolved evidence сохраняет authority-blocker semantics.

## 13. Что остаётся до закрытия Stage 20E

Этот slice **не закрывает Stage 20E целиком**.

Следующий closeout должен связать placement с representative generated-world batch и доказать минимум:

- candidate diagnostics создаются для реальных generated topology/resource/facility worlds;
- ordinary faction set получает bounded deterministic starts;
- достаточная доля calibrated seeds принимается без hidden rescue;
- rejected seeds имеют machine-readable причины;
- delivered-cost authority имеет production whole-route source там, где она требуется;
- bootstrap buffer/depletion authority имеет физический источник либо остаётся явно незакрытым DoD;
- reserve ownership/critical monopoly diagnostics получают authoritative bootstrap ownership там, где ownership уже materialized;
- final normal-seed acceptance не создаёт accidental unrecoverable start;
- final normal-seed acceptance не создаёт unintentional civilization-critical monopoly без meaningful alternative response.

Только после этого можно честно решить, закрыт ли Stage 20E и допустим ли переход к Stage 20F.
