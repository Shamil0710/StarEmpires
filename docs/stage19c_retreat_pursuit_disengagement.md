# Stage 19C — retreat / pursuit / disengagement

> Статус: **IMPLEMENTED SLICE / Stage 19 in progress**  
> Зависимости: Stage 19A observed threat, Stage 19B tactical intent boundary, Stage 17.5 physical ship state

## 1. Цель

Stage 19C вводит survival behavior без omniscient enemy comparison и без бесплатного escape.

Решение строится из двух разных информационных доменов:

```text
OWN SHIP authoritative physical state
  structure / subsystem integrity
  reaction mass
  delta-v
  acceleration

ENEMY actor-visible state only
  production TrackState
  Stage 19A threat assessment
```

Скрытый enemy hull, fit, ammo, acceleration или damage state не читаются.

## 2. OwnReadiness

`TacticalSurvivalPlanner.OwnReadiness` содержит только собственные физические показатели:

- mean compartment integrity;
- minimum installed module integrity;
- reaction mass, kg;
- remaining delta-v, m/s;
- current acceleration capability, m/s².

Интеграционный слой обязан брать их из обычного Stage-17.5 ship/damage runtime (`ShipDamageRuntime.Snapshot`, derived ship state and current physical consumables), а не из doctrine/fleet-power modifiers.

## 3. Policy thresholds

`Policy` задаёт behavioral thresholds:

- minimum acceptable structural integrity;
- minimum subsystem integrity;
- reaction-mass reserve;
- delta-v reserve;
- minimum useful acceleration;
- maximum pursuit track age.

Threshold не изменяет физический state. Он только определяет момент смены поведения.

## 4. Retreat

При нарушении readiness threshold retreat имеет приоритет над pursuit.

Если известна higher-level safe point, retreat movement направляется к ней.

Если safe point неизвестна, но существует actor-visible hostile positional track, направление строится **от наблюдаемой позиции угрозы**, а не от authoritative enemy transform.

Если безопасное направление определить невозможно, planner выбирает `DISENGAGE` вместо выдуманного вектора.

## 5. No free retreat

`OwnReadiness.canManeuver()` требует одновременно:

- reaction mass > 0;
- delta-v > 0;
- acceleration > 0.

Если retreat необходим, но ship физически не может создать maneuver, result:

```text
DISENGAGE / CANNOT_MANEUVER
movement = (0, 0)
```

Stage 19 не телепортирует и не выталкивает корабль из боя.

## 6. Pursuit

Aggressive pursuit поддерживается только для actor-known `HOSTILE` contact с:

- известной production position solution;
- information state `TRACKED` или `FIRE_CONTROL`;
- track age не старше policy maximum.

`UNKNOWN` contact не становится autonomous pursuit target.

Если track потерял positional quality или протух, result становится canonical `DISENGAGE` и target ID очищается. AI не сохраняет magical target memory после потери пригодного track.

Как и в 19B, predictive intercept пока не моделируется: production TrackState не содержит velocity estimate.

## 7. Determinism / acceptance

Tests фиксируют:

1. healthy ship pursues fresh tracked hostile by observed position;
2. stale track stops pursuit and drops target;
3. structural damage retreats toward known safe point;
4. subsystem damage without safe point retreats away from observed hostile;
5. zero reaction mass/delta-v cannot create free retreat movement;
6. healthy non-pursuit mission returns `CONTINUE`;
7. CLASSIFIED-only or UNKNOWN contact cannot sustain aggressive pursuit;
8. reaction-mass/delta-v/acceleration reserve thresholds select stable explicit reasons;
9. invalid readiness/policy geometry is rejected.

## 8. Следующий slice

**Stage 19D — convoy protection / civilian rerouting.**

Он должен использовать существующие durable fleet orders and route-risk/logistics seams, Stage-19A observed threat, Stage-19B SCREEN intent и Stage-19C survival state. Civilian rerouting обязано менять реальный маршрут/ETA/cost exposure, а не просто выдавать абстрактный «convoy safety» bonus.
