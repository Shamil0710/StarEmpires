# Star Empires — Ship Mathematics v0.5: Saturation & Endurance Sweep

> Статус: **executable engineering / balance seed v0.5**  
> Дата: **2026-08-15**  
> Основание: `docs/ship_mathematics_v0_4.md`  
> Код: `src/test/java/com/spacesim/combat/benchmark/ShipMathematicsV05SweepHarness.java`  
> Snapshot: `docs/benchmarks/combat_saturation_sweep_v0_5.json`

---

## 1. Задача v0.5

v0.4 доказал, что один и тот же terminal-salvo можно воспроизводимо просчитать через реальную 2D-геометрию, PN-перехват, конечные fire-control channels, laser dwell и баллистическое движение ракеты после guidance kill.

v0.5 отвечает уже не на вопрос **«работает ли один эталонный бой?»**, а на четыре количественных вопроса:

1. где начинается saturation cliff при росте числа атакующих корветов;
2. сколько реальной защиты добавляет каждый следующий эсминец;
3. как геометрия строя меняет способность внутреннего M-layer успеть до `minimumSafeInterceptRange = 10 km`;
4. что происходит после последовательного расходования interceptor magazines.

Production `CombatController` по-прежнему не изменяется. v0.5 остаётся test-only engineering layer до Combat Depth promotion gate.

---

## 2. Sweep axes

### 2.1. Размер атакующей группы

```text
8 / 16 / 24 / 32 / 48 Torpedo Corvettes
2 M anti-ship missiles в первом синхронном залпе каждого
=
16 / 32 / 48 / 64 / 96 incoming threats
```

### 2.2. Эскорт

```text
0 / 1 / 2 / 3 Escort Destroyers
```

Каждый reference destroyer сохраняет v0.4 defensive package:

```text
1 × L area-defense battery
2 × M fleet-interceptor batteries
4 × S PD lasers
```

Reference battleship:

```text
1 × L area-defense battery
2 × M fleet-interceptor batteries
6 × S PD lasers
```

### 2.3. Formation slot spacing

```text
5 / 10 / 15 / 20 / 25 / 40 km
```

Это **slot spacing**, а не обязательная одинаковая radial distance каждого эсминца от линкора.

В текущей 2D-линейной formation policy позиции задаются так:

```text
Battleship = 0
Escort #1 = +1 × spacing
Escort #2 = -1 × spacing
Escort #3 = +2 × spacing
```

Например, при `spacing = 15 km` три эсминца стоят на:

```text
+15 km
-15 km
+30 km
```

Поэтому третий корабль уже не обязательно способен реализовать тот же inner-interceptor envelope, что первые два.

---

## 3. Две поверхности v0.5

Чтобы sweep оставался быстрым CI acceptance, а не тысячами почти одинаковых прогонов, v0.5 разделён на две части.

### Surface A — полный first-wave sweep

Все комбинации:

```text
5 attacker counts
× [1 zero-escort formation + 3 escort counts × 6 spacings]
= 95 first-wave points
```

### Surface B — endurance sweep

Для канонической атаки:

```text
24 corvettes = 48 missiles per wave
```

прогоняются все 19 вариантов defender formation в течение:

```text
13 consecutive waves
= 247 endurance points
```

Всего snapshot содержит:

```text
95 + 247 = 342 deterministic result points
```

Их полный canonical serialization защищён SHA-256 fingerprint:

```text
5c1ee91e262a410fffd7af46a4d328c7788c82612dd594ae375f3bd9487eac26
```

Если физический solver, target ordering, formation geometry или расход боекомплекта изменятся, acceptance fingerprint изменится.

---

## 4. Межволновая политика endurance

Между волнами предполагается достаточно времени, чтобы:

- launch cells завершили cycle;
- PD laser снова мог выбирать цель;
- transient target state был очищен.

Но **боекомплект не появляется из воздуха**.

На каждый defender:

```text
L area-defense magazine = 48 rounds
M fleet magazines       = 48 rounds total
                          (2 batteries × 24)
```

При полном 48-threat canonical wave один defender способен расходовать:

```text
6 L rounds / wave
4 M rounds / wave
```

Отсюда естественные magazine cliffs:

```text
после wave 8  → L magazine = 0
после wave 12 → M magazines = 0
wave 13       → missile-defense ammunition = 0
                остаются только PD lasers
```

В v0.5 thermal carryover лазеров между волнами ещё не моделируется. Поэтому endurance в первую очередь измеряет interceptor-magazine depletion, а не тепловой ресурс.

---

## 5. Saturation cliff одиночного линкора

Результат first-wave surface:

| Torpedo corvettes | Incoming missiles | Terminal leakers |
|---:|---:|---:|
| 8 | 16 | **0** |
| 16 | 32 | **0** |
| 24 | 48 | **3** |
| 32 | 64 | **18** |
| 48 | 96 | **50** |

Следовательно, при текущих v0.3/v0.4 seeds первый устойчивый saturation threshold одиночного линкора находится **между 16 и 24 torpedo corvettes**.

Это не hard-coded combat power. Линкор в каждом случае имеет те же:

```text
6 safe L attempts
4 safe M attempts
6 PD lasers
```

Рост leakers получается из роста входящего потока при неизменном числе fire-control channels и laser dwell capacity.

---

## 6. Максимальный benchmark: 48 корветов / 96 ракет

Terminal leakers first wave:

| Escort destroyers | 5 km | 10 km | 15 km | 20 km | 25 km | 40 km |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | **50** | — | — | — | — | — |
| 1 | **16** | 19 | 20 | 21 | 30 | 34 |
| 2 | **0** | **0** | **0** | **0** | 10 | 18 |
| 3 | **0** | **0** | **0** | **0** | 1 | 12 |

### Вывод 1 — один эсминец не решает saturation problem

Даже в наиболее выгодной tested geometry один escort оставляет:

```text
16 / 96 missiles
```

то есть 16.7% terminal leakage.

При reference v0.4 spacing `15 km`:

```text
20 / 96 missiles leak
```

### Вывод 2 — два эсминца создают качественный переход

Два destroyers при spacing `5–20 km` полностью закрывают первую 96-ракетную волну в текущей модели.

При `25 km` появляются 10 leakers, при `40 km` — 18.

То есть дополнительный корабль полезен не абстрактным `+defense`, а потому что добавляет:

- ещё 6 L first-wave solutions;
- ещё 4 M solutions, если геометрия успевает;
- 4 независимых laser emitters из собственной позиции.

### Вывод 3 — третий эсминец имеет diminishing return и geometry dependency

На `5–20 km` в первой волне он уже не нужен для достижения `0 leakers` против 48 корветов: два эсминца это уже обеспечивают.

Но при плохой геометрии он резко уменьшает leakage:

```text
25 km:
2 escorts → 10 leakers
3 escorts → 1 leaker

40 km:
2 escorts → 18 leakers
3 escorts → 12 leakers
```

Это первый количественный marginal-utility result для escort doctrine.

---

## 7. Formation geometry теперь измеряется напрямую

Для `24 corvettes + 1 escort`:

```text
spacing 15 km:
  fleet-interceptor kills = 8
  terminal leakers        = 0
  aggregate laser dwell   ≈ 140.88 s

spacing 25 km:
  fleet-interceptor kills = 4
  terminal leakers        = 0
  aggregate laser dwell   ≈ 143.86 s
```

То есть общий outcome ещё может выглядеть одинаково (`0 leakers`), но внутренний процесс уже хуже:

- M-layer эсминца не успевает безопасно перехватить цель;
- нагрузка переходит на lasers;
- aggregate dwell растёт.

Это важно: **нулевое число попаданий само по себе не означает, что formation одинаково хороша**.

Для трёх escorts появляется ещё один эффект. При `15 km` третий slot находится на `+30 km`, поэтому его M-layer уже не полностью реализуется. При `10 km` третий slot = `+20 km` и остаётся внутри validated geometry.

Следовательно, reference spacing нельзя выбирать независимо от количества escort slots.

---

## 8. Endurance: одиночный линкор

Каноническая 48-ракетная волна:

| Wave | L rounds remaining | M rounds remaining | Terminal leakers |
|---:|---:|---:|---:|
| 1 | 42 | 44 | **3** |
| 8 | 0 | 16 | **3** |
| 9 | 0 | 12 | **9** |
| 12 | 0 | 0 | **9** |
| 13 | 0 | 0 | **13** |

Получаются два естественных ступенчатых провала:

```text
L magazine exhaustion:
3 → 9 leakers

M magazine exhaustion:
9 → 13 leakers
```

Таким образом, missile defense снижает terminal leakage одиночного линкора с PD-only уровня `13` до first-wave уровня `3`.

---

## 9. Endurance: один эсминец на 15 km

Результат оказался важнее ожидаемого:

```text
wave 1  → 0 leakers
wave 8  → 0 leakers
wave 9  → 0 leakers   (L magazines empty)
wave 12 → 0 leakers   (M magazines just exhausted)
wave 13 → 0 leakers   (PD-only)
```

На wave 13:

```text
10 PD lasers
0 L rounds
0 M rounds
48 incoming missiles
0 terminal leakers
aggregate laser dwell ≈ 146.60 s
```

Это **не означает**, что десять лазеров канонически должны быть способны бесконечно держать 48 ракет.

Наоборот, это новый balance warning:

> При текущих fluence / heatless-reset assumptions S PD laser слишком доминирует в endurance-сценарии канонической 48-threat wave.

Для более тяжёлой 96-threat волны локальная диагностическая проверка показывает даже при полностью пустых interceptor magazines:

```text
Battleship only          → 60 leakers
+1 escort at 15 km       → 40 leakers
+2 escorts at 15 km      → 20 leakers
+3 escorts at 15 km      → 5 leakers
```

Эти четыре числа пока не входят в обязательный v0.5 snapshot surface, но подтверждают, что laser layer всё же насыщается при достаточно большой плотности целей.

---

## 10. Что v0.5 доказывает

Теперь автоматически доказаны следующие invariants:

1. v0.5 на canonical `24 corvettes / 0 escort` и `24 corvettes / 1 escort / 15 km` **точно воспроизводит v0.4**;
2. каждый из 342 result points сохраняет threat accounting;
3. first-wave surface имеет фиксированный snapshot fingerprint;
4. saturation cliff одиночного линкора появляется естественно;
5. число эсминцев имеет измеримую diminishing marginal utility;
6. spacing влияет на safe M-interceptor kills даже когда terminal leakers ещё равны нулю;
7. interceptor magazines физически расходуются между волнами;
8. wave 9 и wave 13 показывают отдельные L- и M-magazine cliffs.

---

## 11. Что пока нельзя считать финальной боевой моделью

v0.5 всё ещё оптимистичен для защиты по нескольким направлениям:

- нет thermal carryover и radiator limits между волнами;
- нет повреждений самих защитников;
- launchers, sensors и emitters не выводятся из строя;
- hard kill не создаёт fragment cloud;
- proximity interceptor kill не создаёт residual debris threat;
- нет ECM, decoys и track uncertainty;
- все атакующие ракеты по-прежнему нацелены на battleship;
- эсминцы не являются альтернативными целями;
- close formation не платит за collision avoidance, mutual obscuration, fragment hazard и maneuver separation;
- target allocation использует deterministic greedy policy, а не полноценный fleet fire-control optimizer.

Особенно важно первое ограничение: текущий PD-only endurance result с одним эсминцем почти наверняка изменится после введения heat / radiator budget.

---

## 12. Следующий инженерный шаг

После v0.5 самым полезным продолжением является не дальнейшее увеличение количества точек sweep, а **v0.6 defensive resource model**:

1. thermal energy accumulation для PD lasers;
2. radiator rejection rate и допустимая температура;
3. duty-cycle / cooldown вместо бесплатного reset между волнами;
4. progressive loss of emitters / launchers после попаданий;
5. fragment/debris continuation после hard kill;
6. затем повторить тот же v0.5 sweep без изменения его axes.

Тогда можно будет сравнить v0.5 и v0.6 одной и той же сеткой и точно увидеть, какая часть нынешней живучести была следствием отсутствия тепловых и damage constraints.
