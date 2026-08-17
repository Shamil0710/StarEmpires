# Stage 19A — observed threat / intelligence foundation

> Статус: **IMPLEMENTED SLICE / Stage 19 in progress**  
> База: Stage 17.5D `TrackState` + production sensor knowledge boundary  
> Назначение: общий non-omniscient tactical threat input для последующего production tactical AI

## 1. Решение

Stage 19 не создаёт второй combat runtime и не получает скрытый доступ к authoritative enemy state.

Первый slice вводит чистый read-only слой оценки наблюдаемого контакта:

```text
actor-local SensorKnowledgeComponent
→ production TrackState
→ actor-known contact disposition
→ ObservedThreatAssessmentService
→ deterministic behavioral priority
→ Stage 19B tactical intent
```

Оценщик не имеет ссылок на ECS/world/faction runtime, fitted enemy modules, реальные enemy ammunition stores, точный hull/damage state или иные скрытые параметры.

## 2. Что является входом

`ObservedThreatAssessmentService` получает только:

- список `ObservedContact`, который уже ограничен знаниями конкретного actor;
- production `TrackState`;
- actor-known disposition: `FRIENDLY`, `UNKNOWN`, `HOSTILE`;
- собственную известную позицию наблюдателя;
- authoritative current simulation time;
- две явные normalization scales для tactical range и freshness.

Отсутствующий в actor-local track contact не может повлиять на результат.

## 3. Что является результатом

Для каждого видимого контакта формируется immutable `Assessment`:

- stable target ID;
- actor-known disposition;
- production information state;
- known/unknown position semantics;
- estimated range только при наличии position solution;
- track age;
- classification confidence;
- one-sigma position uncertainty только при наличии position covariance;
- dimensionless tactical `priorityScore`.

`priorityScore` — это **не** fleet power, DPS, вероятность победы или физическая характеристика корабля. Это детерминированный behavioral ordering heuristic, построенный только из доступной информации.

## 4. Формирование priority

Текущий минимальный baseline учитывает:

- disposition;
- progression `DETECTED → CLASSIFIED → TRACKED → FIRE_CONTROL`;
- freshness;
- classification confidence;
- position uncertainty;
- estimated range.

Неизвестная позиция остаётся неизвестной. Канонические `(0, 0)` placeholders из `TrackState` никогда не интерпретируются как реальная target position или нулевая дистанция.

При равном priority используется stable target ID, поэтому ordering deterministic.

## 5. Инварианты Stage 19A

Обязательные инварианты:

1. **No omniscience.** Оценщик видит только переданные actor-local tracks.
2. **No truth-state lookup.** Нет ECS/world scan и нет чтения скрытого enemy fit/state.
3. **No mutation.** Оценка не изменяет track, knowledge runtime или world state.
4. **No physical bonuses.** Doctrine/relation/intel не меняют физические характеристики корабля.
5. **Explicit uncertainty.** Старый/неточный track должен иметь меньшую tactical certainty, чем свежий/точный при прочих равных.
6. **Determinism.** Один и тот же visible snapshot даёт byte-for-semantics одинаковый ordering/result.
7. **Shared information model.** Следующий tactical AI обязан потреблять этот слой поверх production `TrackState`, а не authoritative target entities.

## 6. Acceptance tests текущего slice

Тесты фиксируют:

- одинаковый visible snapshot → одинаковый результат и ordering;
- input list не мутируется;
- fresh precise track outranks stale/uncertain equivalent;
- friendly contact имеет zero threat priority;
- unknown disposition остаётся ненулевым, но ниже known hostile;
- unknown position не превращается в ложный exact range;
- contact, известный только actor B, отсутствует в actor A assessment;
- normalization boundaries валидируются явно.

## 7. Что намеренно не входит в 19A

Этот slice пока не:

- выдаёт movement/fire orders;
- выполняет pursuit/intercept/screen;
- принимает retreat/disengagement решения;
- моделирует convoy escort или civilian rerouting;
- создаёт raid/blockade effects;
- изменяет Stage-18 physical economy;
- реализует strategic war goals;
- заменяет существующий `PlayerThreatIntelState` strategic route-risk journal.

`PlayerThreatIntelState` остаётся persistence-level наблюдением риска системы/маршрута. Stage 19A — более низкий tactical information seam.

## 8. Следующий slice

**Stage 19B — production tactical intent + intercept/screen foundation.**

Он должен потреблять `ObservedThreatAssessmentService` и существующие production movement/fire-control seams. Никаких direct reads скрытого enemy state и никакой отдельной simplified combat simulation вводиться не должно.
