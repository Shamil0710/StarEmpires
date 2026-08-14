# Star Empires — Ship Mathematics v0.4: Deterministic Terminal-Salvo Harness

> Статус: **executable engineering / balance seed v0.4**  
> Дата: **2026-08-15**  
> Связан с: `docs/ship_mathematics_v0_2.md`, `docs/ship_mathematics_v0_3.md`, `docs/benchmarks/weapon_interaction_reference_v0_3.json`  
> Код: `src/test/java/com/spacesim/combat/benchmark/DeterministicSalvoHarness.java`  
> Назначение: впервые проверить корабельную доктрину не только таблицами, а детерминированной 2D-симуляцией terminal missile defense.

---

## 1. Граница v0.4

v0.4 **не заменяет** production `CombatController` Stage 13.

Stage 13 остаётся минимальным игровым vertical slice с мгновенным damage resolution. Новый harness живёт в test layer и используется как engineering acceptance до Stage 17.5 / Combat Depth, когда доказанная часть модели сможет быть перенесена в authoritative runtime.

Это позволяет одновременно:

- не ломать существующую игру временной механикой;
- проверять реальные физические зависимости;
- фиксировать balance invariants автоматическими тестами;
- менять calibration seeds до их promotion в production content.

---

## 2. Канонический сценарий первой волны

Атакующая группа:

```text
24 × Torpedo Corvette
2 × M anti-ship missile в синхронном первом залпе каждого
= 48 incoming guided weapons
```

Terminal benchmark начинается не в момент старта с корветов, а когда surviving missiles уже входят в defensive battlespace:

```text
initial range            = 800 000 m
closing velocity         = 18 000 m/s
nominal time to impact   = 44.444... s
incoming threats         = 48
```

Это сознательное разделение фаз:

1. long-range launch / boost / coast позднее моделируется отдельным engagement layer;
2. v0.4 проверяет именно saturation behavior последней оборонительной фазы.

Все величины хранятся в SI.

---

## 3. Детерминированная траектория атакующей ракеты

Каждая ракета получает небольшой уникальный начальный lateral offset и детерминированный jink, который затухает к точке атаки.

Концептуально:

```text
x(t) = max(0, R0 - Vc × t)

f(t) = x(t) / R0

y(t) = f(t) × [offset_i + A_i × sin(omega_i × t + phase_i)]
```

Параметры отличаются по `threatId`, но не используют случайный runtime RNG.

Это необходимо по двум причинам:

- все одинаковые initial states дают одинаковый результат bit-for-bit на одной JVM/math implementation;
- ракеты не летят идеально по одной математической прямой, поэтому PN и point-defense должны реально работать с 2D geometry.

Текущий jink — benchmark trajectory, а не финальный missile AI.

---

## 4. Эшелон 1 — L area-defense interceptor

Используется v0.3 extended interceptor:

```text
wet mass             = 4 000 kg
dry mass             = 2 000 kg
propellant            = 2 000 kg
exhaust velocity      = 35 000 m/s
thrust                = 400 000 N
initial acceleration  = 100 m/s²
navigation constant   = 4
proximity envelope    = 150 m
```

Reference L battery:

```text
engagement range          = 700 km
launch cells              = 4
cell recycle              = 6 s
terminal support channels = 6
magazine                  = 48
```

Для синхронной первой волны один защитник способен выделить:

```text
4 interceptors immediately at area-entry
+2 after first cell recycle
=6 first-wave supported area intercepts
```

Оставшиеся ракеты проходят во внутренние эшелоны. Это не искусственный лимит `maxTargets=6`: он представляет количество одновременно поддерживаемых terminal fire-control solutions.

---

## 5. Эшелон 2 — M fleet interceptor

Используется S interceptor seed v0.3:

```text
wet mass             = 1 200 kg
dry mass             = 700 kg
propellant            = 500 kg
exhaust velocity      = 30 000 m/s
thrust                = 180 000 N
initial acceleration  = 150 m/s²
navigation constant   = 4
proximity envelope    = 100 m
```

Одна reference M battery:

```text
engagement range          = 350 km
launch cells              = 2
cell recycle              = 4 s
terminal support channels = 2
magazine                  = 24
```

Reference battleship имеет две такие батареи, поэтому синхронная terminal wave получает ещё четыре first-wave intercept solutions.

Итого до laser point defense:

```text
Battleship alone:
6 area + 4 fleet = 10 planned interceptor kills

Battleship + one Escort Destroyer:
12 area + 8 fleet = 20 planned interceptor kills
```

Каждый kill всё равно проходит реальный PN integrator; число каналов не означает гарантированный hit.

---

## 6. Реальная 2D proportional-navigation integration

Перехватчик не телепортируется в точку встречи.

На шаге `dt = 0.02 s` рассчитываются:

```text
relative position
relative velocity
closing velocity
line-of-sight rate
available acceleration = thrust / currentMass
propellant burn = thrust / exhaustVelocity
```

Lateral command:

```text
a_lateral = N × closingVelocity × LOS_rate
```

после чего он ограничивается текущим доступным ускорением двигателя.

Оставшаяся acceleration budget используется для движения к линии визирования.

Масса уменьшается по мере расхода рабочего тела, поэтому acceleration растёт физически, а не через scripted terminal bonus.

Для предотвращения numerical tunneling при десятках km/s harness проверяет closest approach не только в discrete sample, но и по относительному segment между соседними fixed steps.

---

## 7. Safe intercept: proximity fuse около собственного корпуса не является успехом

Первый CI v0.4 обнаружил важный физический edge case.

Перехватчик, стартовавший слишком поздно, успевал формально войти в proximity envelope атакующей ракеты уже практически в точке защищаемого корабля. Математически расстояние между объектами было маленьким, но тактического перехвата не происходило.

Поэтому введён seed:

```text
minimumSafeInterceptRange = 10 000 m
```

Успешный interceptor kill требует одновременно:

```text
interceptorMissDistance <= proximityEnvelope
AND
distanceFromProtectedShip >= minimumSafeInterceptRange
```

Это временная coarse approximation будущей fragment/debris модели.

Позже вместо одной границы должны учитываться:

- blast / fragment cone;
- residual body mass;
- relative velocity;
- interception geometry;
- armor-facing exposure;
- debris dispersion;
- время до пересечения защищаемого объёма.

До появления этих данных «сбить» ракету внутри собственного корабля запрещено считать успешной обороной.

---

## 8. Эшелон 3 — S point-defense laser

Reference PD laser сохраняет v0.3 параметры:

```text
beam output power      = 5 MW
wavelength             = 1.064 µm
aperture               = 0.5 m
pointing jitter        = 50 nrad
engagement range seed  = 300 km
retarget delay         = 0.4 s
```

Spot:

```text
thetaDiffraction = 1.22 × wavelength / aperture

thetaEffective = hypot(thetaDiffraction, pointingJitter)

spotRadius = range × thetaEffective
```

Absorbed flux:

```text
incidentFlux = beamPower / (pi × spotRadius²)
absorbedFlux = incidentFlux × absorptivity
```

Поэтому laser effectiveness непрерывно зависит от range и dwell time.

---

## 9. Guidance mission kill ≠ physical hard kill

Это одно из главных решений v0.4.

Временные authoring seeds:

```text
missile surface absorptivity       = 0.50
guidance-kill absorbed fluence     = 8 MJ/m²
hard-kill absorbed fluence         = 80 MJ/m²
```

`8 MJ/m²` и `80 MJ/m²` **не являются заявлением о свойствах реального материала**. Они существуют как калибровочные величины до material/warhead catalog.

После достижения guidance-kill threshold ракета:

1. теряет активное наведение;
2. сохраняет текущую массу, позицию и velocity vector;
3. переходит в ballistic propagation;
4. больше не следует запрограммированной guided trajectory.

Затем рассчитывается её closest approach к защищаемому кораблю.

Если:

```text
ballisticClosestApproach > 60 m
```

то guidance kill достаточен: ракета промахивается.

Если:

```text
ballisticClosestApproach <= 60 m
```

то корпус остаётся опасным и PD laser продолжает dwell до hard-kill threshold либо до момента прохода closest approach.

Таким образом электронно мёртвая ракета не исчезает из симуляции.

---

## 10. Почему это особенно важно при 18 km/s

Reference M anti-ship missile имеет wet mass `12 000 kg`.

Даже без warhead её kinetic energy при terminal velocity `18 000 m/s`:

```text
E = 0.5 × m × v²
  = 0.5 × 12 000 × 18 000²
  ≈ 1.944 × 10^12 J
  ≈ 1.94 TJ
```

Поэтому поздний guidance kill не является безопасным автоматически.

Это ещё один системный аргумент в пользу layered defense и раннего перехвата.

---

## 11. Reference defenders

### Battleship alone

```text
1 × L area-defense battery
2 × M fleet-interceptor battery
6 × S PD laser
```

### Battleship + Escort Destroyer

Дополнительно:

```text
+1 × L area-defense battery
+2 × M fleet-interceptor battery
+4 × S PD laser
```

Destroyer расположен на lateral offset `25 km` от линкора, поэтому interceptor PN geometry у него отличается, а не копируется из точки линкора.

---

## 12. Calibration result для текущего v0.4 seed

Reference expected result, который должен оставаться зафиксирован acceptance tests после CI validation:

### Battleship alone

```text
incoming threats                       48
area interceptor kills                  6
fleet interceptor kills                 4
pre-laser safe interceptions           10
laser guidance mission kills           35
  of which ballistic miss neutralized   5
laser physical hard kills              30
terminal leakers                        3
aggregate laser beam time             ~87.64 s
```

Final accounting:

```text
10 interceptor kills
+5 guidance-kill ballistic misses
+30 laser hard kills
+3 leakers
=48 threats
```

### Battleship + Escort Destroyer

```text
incoming threats                       48
area interceptor kills                 12
fleet interceptor kills                 8
pre-laser safe interceptions           20
laser guidance mission kills           28
  of which ballistic miss neutralized   6
laser physical hard kills              22
terminal leakers                        0
aggregate laser beam time            ~143.12 s
```

Final accounting:

```text
20 interceptor kills
+6 guidance-kill ballistic misses
+22 laser hard kills
+0 leakers
=48 threats
```

Важно: `laser guidance mission kills` является overlapping diagnostic и не суммируется отдельно с hard kills.

---

## 13. Что означает результат для доктрины

В рамках **одной синхронной первой terminal wave**:

- линкор сам по себе имеет серьёзную layered defense;
- но часть плотного корветного залпа всё равно проходит;
- добавление одного dedicated escort destroyer удваивает first-wave interceptor depth;
- дополнительные четыре PD emitters дают больше parallel dwell capacity;
- escort переводит reference first wave из `3 leakers` в `0 leakers`.

Это именно требуемая роль эсминца:

> он не существует ради меньшего аналога DPS линкора; он меняет survival envelope всей группы.

При этом `0 leakers` **не означает неуязвимость**.

Для отражения этой волны группа:

- расходует реальные interceptor rounds;
- занимает terminal support channels;
- использует значительный суммарный laser dwell;
- ещё не сталкивается с fragment cloud от hard kills;
- ещё не отражает вторую/третью волну при частично пустых магазинах;
- ещё не испытывает ECM/decoys/sensor degradation.

Поэтому следующий залп может дать другой результат даже при той же численности атакующих.

---

## 14. Почему результат не является окончательным combat balance

Следующие упрощения намеренны:

1. attacking missile wave уже дошла до 800 km; long-range attrition не моделируется;
2. все 48 missiles принадлежат одному reference M subtype;
3. sensor tracks считаются достаточными для запуска defensive solutions;
4. ECM/ECCM/decoys пока не включены;
5. hard-kill threshold является calibration seed, а не material truth;
6. после `80 MJ/m²` объект считается physically neutralized без fragment cloud;
7. interceptor proximity kill также пока не создаёт residual fragments;
8. terminal support scheduling представлен first-wave channel budget, а не полноценной continuous fire-control queue;
9. defender maneuver отсутствует;
10. attacker terminal AI использует deterministic benchmark jink, а не adaptive evasion.

Из-за пунктов 6–7 текущая модель, вероятно, **оптимистична для защитника на очень коротких дистанциях**.

---

## 15. Acceptance invariants v0.4

CI должен доказывать:

1. одинаковый initial state даёт идентичный `SalvoReport`;
2. все 48 угроз accounted — ни одна не исчезает без outcome;
3. unescorted battleship получает хотя бы один leaker;
4. escort destroyer уменьшает число leakers минимум вдвое;
5. L/M interceptor expenditure ограничен реальными first-wave channels;
6. своевременный PN intercept происходит до impact;
7. слишком поздний proximity event внутри safe-intercept boundary не считается успешной защитой;
8. guidance kill не равен physical hard kill;
9. часть guidance-killed missiles требует дополнительного laser dwell;
10. Stage-13 production combat не изменяется этим benchmark.

---

## 16. Promotion gate в production combat

Прежде чем переносить harness в main simulation systems, требуется:

- data-driven `GuidedWeaponDefinition`;
- projectile / missile runtime identity;
- authoritative track/covariance input из sensor model;
- dynamic launcher/cell/channel scheduler;
- persistent or explicitly transient missile-state policy;
- deterministic destruction/fragment output;
- warhead and material response model;
- combat save/load policy;
- AI/player commands, использующие один shared weapon boundary;
- performance profiling для десятков/сотен simultaneously guided objects.

До выполнения gate test harness остаётся эталонным solver experiment, а не вторым скрытым combat engine.

---

## 17. Следующий шаг — v0.5

Следующая версия должна превратить одиночный acceptance point в deterministic parameter sweep.

Минимальная матрица:

```text
attacking corvettes: 8 / 16 / 24 / 32 / 48
escort destroyers:   0 / 1 / 2 / 3
salvo waves:          1 / 2 / 3
```

Дополнительно варьировать:

- terminal entry velocity;
- arrival dispersion;
- salvo timing;
- spent magazines между волнами;
- laser thermal duty cycle;
- sensor covariance;
- ECM / decoy pressure;
- safe interception range;
- hard-kill threshold;
- fragment/debris persistence.

Выходом должны стать уже не отдельные `3 vs 0`, а curves:

```text
P / deterministic fraction of leakers vs salvo size
interceptor expenditure per defended capital
laser dwell saturation
ammo endurance over repeated waves
marginal value of each additional escort
```

Именно по этим кривым следует балансировать число slots и fleet doctrine перед promotion в Stage 17.5 combat depth.
