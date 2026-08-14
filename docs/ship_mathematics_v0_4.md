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

Terminal benchmark начинается, когда surviving missiles входят в defensive battlespace:

```text
initial range            = 800 000 m
closing velocity         = 18 000 m/s
nominal time to impact   = 44.444... s
incoming threats         = 48
```

Long-range boost/coast и attrition до 800 km относятся к следующему engagement layer. v0.4 проверяет насыщение последних эшелонов обороны.

Все величины хранятся в SI.

---

## 3. Детерминированная траектория атакующей ракеты

Каждая ракета получает уникальный lateral offset и детерминированный jink, затухающий к точке атаки:

```text
x(t) = max(0, R0 - Vc × t)

f(t) = x(t) / R0

y(t) = f(t) × [offset_i + A_i × sin(omega_i × t + phase_i)]
```

Параметры зависят от `threatId`, но не используют runtime RNG. Это даёт воспроизводимость и одновременно не заставляет 48 ракет лететь по одной математической прямой.

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

Для синхронной первой волны один защитник может поддерживать:

```text
4 interceptors immediately at area-entry
+2 after first cell recycle
=6 first-wave supported area intercepts
```

Это ограничение каналов fire control, а не абстрактный `maxTargets`.

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

Reference battleship имеет две M batteries. Один escort destroyer добавляет ещё две.

При **close-screen formation** оба корабля могут реализовать этот эшелон:

```text
Battleship alone:
6 area + 4 fleet = 10 safe interceptor kills

Battleship + one close-screen Escort Destroyer:
12 area + 8 fleet = 20 safe interceptor kills
```

Каждый kill всё равно проходит реальный PN integrator.

---

## 6. Реальная 2D proportional-navigation integration

Fixed step:

```text
dt = 0.02 s
```

На каждом шаге считаются:

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

Он ограничивается текущим доступным ускорением двигателя. Оставшаяся acceleration budget направляется к линии визирования.

Масса падает по мере расхода propellant, поэтому acceleration растёт физически. Для предотвращения numerical tunneling closest approach проверяется также по относительному segment между соседними fixed steps.

---

## 7. Safe intercept: proximity fuse около собственного корпуса не является успехом

Первый CI v0.4 обнаружил важный edge case: слишком поздно запущенный interceptor формально входил в proximity envelope атакующей ракеты уже практически в точке защищаемого корабля.

Введён authoring seed:

```text
minimumSafeInterceptRange = 10 000 m
```

Успешная оборона требует:

```text
interceptorMissDistance <= proximityEnvelope
AND
distanceFromProtectedShip >= minimumSafeInterceptRange
```

Это coarse precursor будущей fragment/debris модели. Позже одна граница будет заменена физикой residual body, fragment cone, relative velocity и interception geometry.

---

## 8. Formation geometry стала частью доктрины

Второй CI выявил ещё один важный эффект: оружие escort не может считаться находящимся в точке защищаемого линкора.

Изначально reference destroyer был поставлен на lateral offset `25 km`. При launch range `350 km` S interceptor с этой позиции достигал атакующей ракеты только примерно в `9.8 km` от линкора — уже внутри safe-intercept boundary.

Проверка того же перехватчика показывает примерно:

```text
escort offset 15 km -> safe intercept ~18.5 km from protected ship
escort offset 20 km -> safe intercept ~12 km
escort offset 25 km -> ~9.8 km, unsafe
```

Поэтому reference **close-screen** spacing для v0.4:

```text
Escort Destroyer lateral offset = 15 000 m
```

Это не означает, что все эсминцы обязаны лететь в 15 km. Наоборот, результат фиксирует будущий doctrinal trade-off:

- close screen лучше использует inner interceptors;
- wide screen улучшает пространственное покрытие outer defense;
- formation spacing должно стать настраиваемым параметром fleet AI, а не визуальным украшением.

v0.5 обязан отдельно sweep-ить formation spacing.

---

## 9. Эшелон 3 — распределённый S point-defense laser

Reference PD laser:

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

Важное уточнение v0.4: каждый emitter использует **собственную позицию**.

- 6 battleship lasers стреляют из `y = 0`;
- 4 destroyer lasers стреляют из `y = +15 km`.

Поэтому escort PD получает собственную геометрию range/dwell и не превращается в бесплатные дополнительные башни линкора.

---

## 10. Guidance mission kill ≠ physical hard kill

Authoring seeds:

```text
missile surface absorptivity       = 0.50
guidance-kill absorbed fluence     = 8 MJ/m²
hard-kill absorbed fluence         = 80 MJ/m²
```

Эти значения не являются утверждением о реальных материалах.

После guidance kill ракета:

1. теряет активное наведение;
2. сохраняет текущие position/velocity/mass;
3. переходит в ballistic propagation;
4. получает реальный closest approach к защищаемому кораблю.

Если:

```text
ballisticClosestApproach > 60 m
```

она безопасно промахивается.

Если:

```text
ballisticClosestApproach <= 60 m
```

PD продолжает dwell до physical hard kill либо до прохода closest approach.

---

## 11. Почему это важно при 18 km/s

Reference M anti-ship missile wet mass:

```text
12 000 kg
```

При `18 000 m/s` её kinetic energy без учёта warhead:

```text
E = 0.5 × 12 000 × 18 000²
  ≈ 1.944 × 10^12 J
  ≈ 1.94 TJ
```

Поэтому поздний electronic kill не делает тело безопасным автоматически.

---

## 12. Reference defenders

### Battleship alone

```text
1 × L area-defense battery
2 × M fleet-interceptor battery
6 × S PD laser
```

### Battleship + close-screen Escort Destroyer

```text
Destroyer offset = +15 km lateral

+1 × L area-defense battery
+2 × M fleet-interceptor battery
+4 × S PD laser
```

Все defensive systems используют позицию собственного корабля.

---

## 13. Calibration result v0.4

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

Accounting:

```text
10 interceptor kills
+5 guidance-kill ballistic misses
+30 laser hard kills
+3 leakers
=48
```

### Battleship + one close-screen Escort Destroyer

```text
incoming threats                       48
area interceptor kills                 12
fleet interceptor kills                 8
pre-laser safe interceptions           20
laser guidance mission kills           28
  of which ballistic miss neutralized   9
laser physical hard kills              19
terminal leakers                        0
aggregate laser beam time            ~140.88 s
```

Accounting:

```text
20 interceptor kills
+9 guidance-kill ballistic misses
+19 laser hard kills
+0 leakers
=48
```

`laser guidance mission kills` — overlapping diagnostic: hard-killed ракета сначала могла получить guidance kill, поэтому mission-kill count не складывается отдельно с final accounting.

---

## 14. Что результат говорит о доктрине

В рамках одной синхронной terminal wave:

- battleship сам имеет серьёзную layered defense;
- но плотный корветный залп всё равно создаёт leakers;
- close-screen destroyer удваивает first-wave interceptor depth;
- дополнительные четыре PD emitters дают parallel dwell из своей позиции;
- reference first wave меняется с `3 leakers` до `0`.

Эсминец полезен не как уменьшенный источник capital DPS, а как корабль, который изменяет survival envelope всей группы.

`0 leakers` не означает неуязвимость: расходуются interceptor magazines и laser duty cycle; fragments, ECM, decoys, repeated waves и sensor degradation пока отсутствуют.

---

## 15. Что пока намеренно упрощено

1. long-range attrition до 800 km не моделируется;
2. все incoming missiles одного reference subtype;
3. tracks достаточны для launch authorization;
4. ECM/ECCM/decoys отсутствуют;
5. fluence thresholds — calibration seeds;
6. hard kill пока не создаёт fragment cloud;
7. interceptor kill тоже не создаёт debris;
8. terminal support scheduler пока first-wave budget, а не continuous queue;
9. battleship не маневрирует;
10. missile jink deterministic, не adaptive;
11. destroyer не является отдельной целью атакующей стороны.

Из-за отсутствия fragments текущая terminal defense, вероятно, оптимистична на малых дистанциях.

---

## 16. Acceptance invariants v0.4

CI должен доказывать:

1. одинаковый initial state даёт идентичный `SalvoReport`;
2. все 48 threats accounted;
3. battleship alone получает `3` leakers в calibration seed;
4. battleship + close-screen destroyer получает `0` leakers в той же первой волне;
5. L/M expenditure ограничен launch/support channels;
6. timely PN intercept происходит до impact;
7. proximity внутри 10 km safe boundary не считается успешной защитой;
8. 15 km escort spacing позволяет M-layer safely defend central battleship;
9. 25 km spacing уже слишком велико для того же 350 km inner-layer launch seed;
10. destroyer lasers используют его собственную позицию;
11. guidance kill не равен physical hard kill;
12. Stage-13 production combat не изменяется benchmark-кодом.

---

## 17. Promotion gate в production combat

До переноса solver в authoritative simulation нужны:

- data-driven `GuidedWeaponDefinition`;
- projectile/missile runtime identity;
- track/covariance input из sensor model;
- dynamic launcher/cell/channel scheduler;
- missile persistence policy;
- deterministic fragment/debris output;
- warhead/material response model;
- combat save/load policy;
- единый player/AI weapon boundary;
- profiling сотен simultaneously guided objects.

До этого harness остаётся эталонным solver experiment, а не вторым combat engine.

---

## 18. Следующий шаг — v0.5 parameter sweep

Минимальная матрица:

```text
attacking corvettes: 8 / 16 / 24 / 32 / 48
escort destroyers:   0 / 1 / 2 / 3
salvo waves:          1 / 2 / 3
formation offset:     5 / 10 / 15 / 20 / 25 / 40 km
```

Дополнительно варьировать:

- terminal entry velocity;
- arrival dispersion;
- salvo timing;
- magazines between waves;
- laser thermal duty cycle;
- sensor covariance;
- ECM / decoy pressure;
- safe interception range;
- hard-kill threshold;
- fragments/debris persistence.

Выходом должны стать curves:

```text
leakers vs salvo size
interceptor expenditure per defended capital
laser dwell saturation
ammo endurance over repeated waves
marginal value of each additional escort
marginal value of formation spacing
```

Именно по этим кривым следует балансировать slots и fleet doctrine перед promotion в Stage 17.5 Combat Depth.
