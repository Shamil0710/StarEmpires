# Stage 20E — whole generated-world seed acceptance and batch observability v1

> Статус: **IMPLEMENTATION SLICE — EVIDENCE LAYER, NOT YET FULL STAGE-20E CLOSEOUT**  
> Родительский план: `docs/stage20_physical_world_generation_plan.md`  
> Предшествующие слои:
> - `docs/stage20e_faction_start_dependency_diagnostics_v1.md`
> - `docs/stage20e_faction_start_placement_v1.md`

## 1. Зачем нужен отдельный whole-seed слой

После предыдущих Stage 20D/20E работ уже существуют независимые authoritative gates:

```text
Stage20JumpTopologyGenerationResult
Stage20EconomicThroughputAcceptance.AcceptanceReport
Stage20FactionStartPlacementGenerator.PlacementResult
```

Но до этого не существовало одного machine-readable ответа на вопрос:

```text
может ли этот root seed стать обычным production world?
```

Из-за этого representative seed batch нельзя было честно измерять: отдельные тесты доказывали корректность подсистем, но не давали общего accepted/rejected/unresolved распределения.

## 2. Whole-seed composition

`Stage20GeneratedWorldSeedAcceptance.compose(...)` объединяет только уже существующие authoritative результаты.

Порядок:

```text
topology result
→ if rejected: stop, no downstream materialization
→ economic physical-throughput acceptance
→ bounded faction-start placement
→ whole-seed result
```

Whole-seed status:

```text
ACCEPTED
REJECTED_SEED
UNRESOLVED_AUTHORITY
```

## 3. Topology rejection останавливает pipeline

Если Stage 20D вернул:

```text
REJECTED_SEED
```

то economic/start reports должны отсутствовать.

Это важный invariant:

```text
rejected topology candidate
≠ ordinary world that may continue materialization
```

Попытка передать downstream reports для topology-rejected seed является integration error.

## 4. Missing downstream не считается плохим seed

Обратная ситуация также запрещена.

Если topology accepted, но harness не предоставил economic-throughput или placement result, `compose(...)` бросает ошибку.

Мы намеренно **не** превращаем сломанный test/generation harness в:

```text
REJECTED_SEED
```

иначе batch statistics искусственно ухудшали бы качество generator-а и смешивали:

```text
world-generation defect
с
pipeline-integration defect
```

## 5. Machine-readable failure reasons

Whole-seed report нормализует причины:

```text
TOPOLOGY_QUALITY_REJECTED
ECONOMIC_THROUGHPUT_REJECTED
FACTION_START_PLACEMENT_REJECTED
FACTION_START_AUTHORITY_UNRESOLVED
```

При этом исходный detail не теряется.

Economic failure сохраняет:

```text
start system
commodity
existing Stage20EconomicThroughputAcceptance.FailureReason
detail
```

Placement failure сохраняет существующий `FailureReason` bounded placement-а.

## 6. Rejection сильнее unresolved authority

Если один authoritative gate уже доказывает невозможность ordinary seed, а другой одновременно имеет unresolved authority, итог остаётся:

```text
REJECTED_SEED
```

`UNRESOLVED_AUTHORITY` используется только когда authoritative rejection отсутствует.

Это не позволяет отсутствующей информации скрыть уже доказанный physical/economic failure.

## 7. Batch observability

`Stage20GeneratedWorldBatchAcceptance.run(...)` получает:

```text
unique root seed set
+ SeedEvaluator
```

и возвращает:

```text
accepted seed count/fraction
rejected seed count/fraction
unresolved-authority seed count/fraction
failure reason histogram
ordered per-seed evidence
```

Input seed order не влияет на результат: seed set canonicalizes ascending.

Duplicate seed запрещён, потому что иначе один seed получил бы больший statistical weight.

## 8. SeedEvaluator contract

`SeedEvaluator` обязан оценивать **ровно переданный seed**.

Запрещено внутри evaluator:

```text
seed A fails
→ silently try seed B
→ return B as result for A
```

Также запрещён hidden repair уже оценённого world state.

Внешний higher-level generator позже может иметь bounded deterministic retry policy, но каждый отдельный root seed всё равно должен сохранять собственный acceptance result.

## 9. Почему v1 не вводит minimum accepted fraction

Canonical Stage-20 roadmap требует representative generated-world/seed evidence, но на данный момент не задаёт доказанный numerical target вида:

```text
>= 70%
>= 80%
>= 95%
```

Поэтому v1 **не придумывает такое число**.

Сначала требуется measured batch distribution на реальном complete generated-world pipeline.

После этого можно принять versioned calibration, если проект действительно нуждается в quantitative normal-seed acceptance rate.

Это лучше, чем подогнать generator под произвольный процент без evidence.

## 10. Что уже связано реальным кодом

Regression suite использует настоящий:

```text
Stage20JumpTopologyGenerator
→ Stage20 topology quality/repair/rejection
```

и настоящий:

```text
Stage20FactionStartPlacementGenerator
→ bounded deterministic assignment over authoritative topology
```

Whole-seed compositor принимает production `Stage20EconomicThroughputAcceptance.AcceptanceReport`, не создавая второй economic gate.

## 11. Что этот slice сознательно не подделывает

Тест whole-seed composition не изображает полную production economy вручную как доказательство Stage-20E closure.

Полная representative batch ещё должна собрать для каждого seed реальные:

```text
Stage20SystemGeometryGenerator
Stage20LocalInfrastructureLayoutGenerator
Stage20ResourceOccurrenceGenerator
Stage20BootstrapProductionCapacityCalculator
Stage20TheoreticalSupplyThroughputAnalyzer
Stage20FactionStartDependencyDiagnostics
Stage20FactionStartCandidateEvaluator
Stage20FactionStartPlacementGenerator
Stage20EconomicThroughputAcceptance
```

Этот v1 слой создаёт место, куда такой результат будет поступать, и не позволяет заменить его фиктивным batch percentage.

## 12. Следующий обязательный шаг Stage 20E

Следующий implementation slice должен создать representative production-style seed probe, который на одном root seed действительно собирает:

```text
accepted topology
+ per-system physical geometry/layout
+ Stage-18-backed finite resources/sites
+ physical export/production throughput closure
+ faction-start diagnostics/evaluations
+ bounded placement
+ essential throughput acceptance for selected starts
→ Stage20GeneratedWorldSeedAcceptance
```

После этого batch запускается на фиксированном deterministic seed corpus и сохраняет measured evidence.

## 13. Delivered cost / buffer / ownership остаются отдельным authority closeout

Даже успешный production-style batch probe не должен автоматически объявлять Stage 20E COMPLETE, если остаются unresolved requirements из roadmap:

- delivered-cost bands без production whole-route valuation authority;
- buffer depletion exposure без generated/bootstrap inventory authority;
- reserve ownership concentration без materialized bootstrap ownership authority.

Их необходимо либо физически привязать, либо явно оставить незакрытым DoD. Никакие default price/stock/owner для закрытия отчёта не допускаются.

## 14. Exit condition этого slice

Этот slice принят, когда repository-exact CI доказывает:

- topology-rejected seed останавливается до downstream materialization;
- accepted topology требует complete downstream acceptance reports;
- whole-seed rejection сохраняет machine-readable причины;
- deterministic batch не зависит от input ordering;
- duplicate seeds не искажают sample weighting;
- batch публикует измерение, а не выдуманный balance threshold.

После этого работа продолжается к production-style generated seed probe, а не к Stage 20F.
