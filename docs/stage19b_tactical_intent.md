# Stage 19B — tactical intent / intercept / screen foundation

> Статус: **IMPLEMENTED SLICE / Stage 19 in progress**  
> Зависимости: Stage 19A observed threat, Stage 17.5D `TrackState`, shared flight/combat command seams

## 1. Цель

Stage 19B переводит actor-local observed threat information в командный tactical intent, не создавая второй combat или movement runtime.

```text
production TrackState
→ Stage 19A observed threat assessment
→ Stage 19B tactical posture
→ pure TacticalIntent
→ FlightCommandComponent / CombatCommandComponent
→ existing physical flight + authoritative combat runtime
```

## 2. Tactical postures

Минимальный production foundation вводит три mission-level posture:

- `HOLD` — удерживать позицию, сохраняя наблюдаемую угрозу как engagement target;
- `INTERCEPT` — сближаться с текущей наблюдаемой оценкой позиции наиболее приоритетного контакта;
- `SCREEN` — занимать screening point между защищаемой точкой и наиболее приоритетной наблюдаемой угрозой.

Это behavioral semantics, а не физические бонусы.

## 3. Information boundary

`ObservedTacticalIntentPlanner` получает только:

- actor-visible `ObservedContact`;
- собственную известную позицию actor;
- при `SCREEN` — явно известную позицию защищаемой точки;
- Stage-19A normalization scales;
- authoritative current simulation time.

Planner не читает ECS/world entities, authoritative target transform, hidden enemy fit, ammunition, hull state или faction truth.

Unknown-disposition contact может вызвать осторожное сближение/экранирование, но autonomous fire разрешается только если contact уже известен actor как `HOSTILE` и production information state достиг `TRACKED` или `FIRE_CONTROL`.

## 4. Intercept boundary

Текущий production `TrackState` содержит position estimate/covariance, но не target velocity estimate.

Поэтому Stage 19B **не выдумывает predictive intercept**. `INTERCEPT` направляет movement intent к текущей наблюдаемой оценке позиции. Когда information model получит actor-visible velocity/covariance channel, predictive lead может быть добавлен поверх той же границы без hidden truth-state lookup.

Если позиция неизвестна, target может оставаться выбранным по observed threat, но movement остаётся нулевым.

## 5. Screen geometry

Для `SCREEN` higher-level mission layer обязан передать known protected point и physical screen radius.

При известной target position desired screen point располагается:

```text
protected point
+ unit(protected → observed threat)
* min(screen radius, observed threat distance)
```

Если target position ещё неизвестна, ship возвращается/сближается с protected point вместо движения к каноническому `(0,0)` placeholder.

## 6. Shared command adapter

`TacticalIntentCommandAdapter` записывает intent только в существующие transient seams:

- `FlightCommandComponent`;
- `CombatCommandComponent`.

Он не интегрирует `TransformComponent`, не расходует reaction mass, не рассчитывает ускорение и не наносит damage.

`physicalSpeedCap` передаётся caller'ом из собственного физического ship state. Stage 19B не даёт doctrine/AI speed modifiers.

## 7. Acceptance invariants

Tests фиксируют:

1. hostile tracked contact → deterministic intercept direction + fire request;
2. unknown disposition → movement allowed, autonomous fire forbidden;
3. screen geometry размещает intent между protected point и observed threat;
4. unknown target position не превращается в ложный `(0,0)` intercept;
5. HOLD не создаёт movement, но сохраняет valid engagement target;
6. empty/friendly-only knowledge → canonical no-target intent;
7. SCREEN требует explicit protected geometry;
8. adapter записывает shared flight/combat intent и сохраняет caller-supplied physical speed cap;
9. no-target intent очищает stale combat command.

## 8. Следующий slice

**Stage 19C — retreat / pursuit / disengagement.**

Он должен принимать решения по наблюдаемой тактической картине и собственному физическому readiness/damage state. Retreat не должен получать hidden enemy truth, а pursuit не должен сохранять magical target knowledge после деградации/потери track.
