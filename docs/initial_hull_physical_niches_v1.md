# Star Empires — Initial Hull Physical Niches v1

> **Статус:** CANDIDATE COMPANION BASELINE v1.0  
> **Дата:** 2026-08-19  
> **Связан с:** `docs/initial_asset_hull_manifest_v1.md`  
> **Назначение:** собрать уже существующие расчёты корпусов, отделить production authority от provisional reference data и зафиксировать физические ниши всех 15 ship asset families без изобретения скрытых class bonuses.

---

# 1. Главный вывод аудита

Расчёты для корпусов **уже существуют**, причём на нескольких уровнях авторитета.

Используемая цепочка источников:

1. `docs/ship_hull_module_and_fleet_doctrine.md` — doctrine classes, slot philosophy, роли и ограничения;
2. `docs/ship_mathematics_v0_1.md` — центральная reference grid размеров, массы, экипажа, ускорения и delta-v;
3. `docs/ship_mathematics_v0_2.md` + `docs/benchmarks/ship_reference_designs_v0_2.json` — физически собранные reference fits и fitting-budget checks;
4. `src/main/resources/data/content/ship-engineering-v1.json` — текущий production-valid engineering hull demonstrator `hull.escort_destroyer_v1`;
5. `docs/stage20a_representative_propulsion_v2.md` — accepted Stage-20 calibration propulsion references;
6. `docs/stage20a_representative_endurance_v1.md` — accepted Stage-20 sustained-thrust/endurance policy seeds;
7. `docs/stage22_content_balance_plan.md` — будущий обязательный re-author/rebalance/promote gate.

Ни один из старых benchmark/reference документов не превращается этим приложением в final production content.

---

# 2. Уровни авторитета

| Статус | Значение |
| --- | --- |
| **PRODUCTION REFERENCE** | Production schema/content уже существует и имеет приоритет над совпадающим старым benchmark. |
| **PROVISIONAL ACCEPTED REFERENCE** | Значение принято для Stage-20 calibration, но требует Stage-22 review. |
| **AUTHORING BENCHMARK** | Физически согласованный design seed; полезен как исходная точка, но не production contract. |
| **DOCTRINE REFERENCE** | Центральная шкала класса и его роли. Не жёсткая граница. |
| **UNAUTHORED GAP** | В репозитории нет достаточного расчёта; запрещено заполнять число догадкой и выдавать за канон. |

Ключевое правило из v0.1:

> Центральные military reference designs не являются жёсткими границами класса. Конкретные faction designs могут отклоняться примерно на **±20–30%**, если сохраняют доктринальную роль и проходят общие mass/volume/power/heat/crew/fitting checks.

Для concept-art planning можно использовать внешний ориентировочный corridor `~70–130%` от центрального reference по отдельной величине. Это **не означает**, что допустимо одновременно масштабировать все параметры на один коэффициент и автоматически получить валидный корабль.

---

# 3. Military hull reference grid

## 3.1. Центральные doctrine references

| Family | L × B × H | Dry reference mass | Combat departure mass | Reaction mass | Optimal crew | Max / sustained accel | Nominal Δv |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Patrol Craft | 35 × 9 × 8 m | 300 t | 430 t | 130 t | 14 | 1.50 / 0.45 m/s² | 36.0 km/s |
| Corvette | 65 × 16 × 13 m | 1,400 t | 2,000 t | 600 t | 36 | 1.10 / 0.30 m/s² | 35.7 km/s |
| Frigate | 110 × 24 × 20 m | 5,500 t | 8,000 t | 2,500 t | 90 | 0.80 / 0.20 m/s² | 37.5 km/s |
| Destroyer | 170 × 34 × 28 m | 15,000 t | 22,000 t | 7,000 t | 160 | 0.60 / 0.15 m/s² | 38.3 km/s |
| Cruiser | 280 × 55 × 45 m | 45,000 t | 70,000 t | 25,000 t | 320 | 0.40 / 0.10 m/s² | 44.2 km/s |
| Battlecruiser | 410 × 75 × 60 m | 120,000 t | 180,000 t | 60,000 t | 560 | 0.50 / 0.13 m/s² | 40.5 km/s |
| Battleship | 620 × 110 × 85 m | 350,000 t | 550,000 t | 200,000 t | 1,000 | 0.25 / 0.06 m/s² | 45.2 km/s |
| Fleet Carrier | 560 × 125 × 90 m | 300,000 t | 500,000 t | 200,000 t | 1,600 incl. air wing | 0.18 / 0.05 m/s² | 51.1 km/s |

## 3.2. Rough outer concept corridor

Только для раннего visual/scale planning, на основании уже разрешённого v0.1 отклонения до примерно ±20–30%:

| Family | Approx. length corridor | Approx. departure-mass corridor |
| --- | ---: | ---: |
| Patrol Craft | ~25–46 m | ~300–560 t |
| Corvette | ~46–85 m | ~1,400–2,600 t |
| Frigate | ~77–143 m | ~5,600–10,400 t |
| Destroyer | ~119–221 m | ~15,400–28,600 t |
| Cruiser | ~196–364 m | ~49,000–91,000 t |
| Battlecruiser | ~287–533 m | ~126,000–234,000 t |
| Battleship | ~434–806 m | ~385,000–715,000 t |
| Fleet Carrier | ~392–728 m | ~350,000–650,000 t |

Этот corridor не является `HullDefinition` validation rule.

---

# 4. Crew envelopes

| Family | Minimum combat-capable | Optimal | High-manpower / low-automation |
| --- | ---: | ---: | ---: |
| Patrol | 8 | 14 | 20 |
| Corvette | 24 | 36 | 50 |
| Frigate | 60 | 90 | 120 |
| Destroyer | 120 | 160 | 220 |
| Cruiser | 250 | 320 | 450 |
| Battlecruiser | 450 | 560 | 750 |
| Battleship | 800 | 1,000 | 1,400 |
| Fleet Carrier | 1,200 | 1,600 | 2,200 |

Crew count не даёт магический efficiency bonus. Он работает через damage control, обслуживание, ремонт, sortie rate, endurance и automation tradeoffs.

---

# 5. Military slot philosophy

Это предварительная doctrine grid, а не финальная Stage-22 production balance.

| Family | Weapon | Utility | Internal | Mission | Physical niche |
| --- | --- | --- | --- | --- | --- |
| Patrol Craft | `2S` | `2S` | `2S` | `1S` | дешёвое присутствие, полиция, таможня |
| Corvette | `3S + 1M` | `2S + 1M` | `2S + 1M` | `1M` | screen, interception, torpedo pressure |
| Frigate | `3S + 2M` | `2S + 2M` | `2S + 2M` | `1S + 1M` | escort, recon, EW, дальний patrol |
| Destroyer | `4S + 3M + 1L` | `2S + 2M` | `2S + 3M + 1L` | `2M` | первый полноценный heavy hardpoint; fleet specialist |
| Cruiser | `4S + 4M + 2L` | `2S + 3M + 1L` | `3S + 4M + 2L` | `1M + 1L` | independent long-range combatant |
| Battlecruiser | `4S + 4M + 3L + 1XL` | `3S + 3M + 1L` | `4S + 5M + 3L` | `2L` | fast capital hunter / raider |
| Battleship | `6S + 4M + 4L + 2XL` | `4S + 3M + 2L` | `6S + 6M + 4L + 1XL` | `2L + 1XL` | heavy line / breakthrough / station reduction |

`CORE` задаётся architecture-specific и не включён в эту grid.

Carrier имеет отдельную hangar-dominant architecture и не должен насильно вписываться в battleship slot ladder.

---

# 6. Concrete military design evidence

## 6.1. Corvette — `IAHM-SHIP-01`

**Reference design:** Torpedo Corvette v0.2.

```text
design dry mass       1,316 t
payload/ammo             224 t
reaction mass             600 t
departure mass          2,140 t
max accel              ~1.028 m/s²
sustained accel        ~0.280 m/s²
delta-v                ~32.9 km/s
```

Fitting utilization:

```text
mass   81.3%
volume 65.3%
power  22.7%
heat   36.7%
```

**Primary pressure:** fitting mass/volume, magazines, limited repair/endurance.  
**Required specializations:** screen/interceptor, torpedo/fast attack.  
**Stage-20 nominal stores endurance:** 7 days, provisional policy seed.

## 6.2. Frigate — `IAHM-SHIP-02`

**Reference design:** Recon/EW Frigate v0.2.

```text
design dry mass       5,156 t
payload/ammo             258 t
reaction mass           2,500 t
departure mass          7,914 t
max accel              ~0.809 m/s²
sustained accel        ~0.202 m/s²
delta-v                ~38.0 km/s
```

Fitting utilization:

```text
mass   80.9%
volume 53.0%
power  48.5%
heat   95.6%
```

**Primary pressure:** thermal budget — recon/EW fit почти исчерпывает sustained heat rejection.  
**Required specializations:** escort, recon, EW, long patrol.  
**Endurance number:** UNAUTHORED for a canonical frigate; qualitative long-patrol role exists.

## 6.3. Destroyer — `IAHM-SHIP-03`

Есть **два разных уровня evidence**, которые нельзя молча смешивать.

### Doctrine/benchmark reference

```text
v0.1 central geometry  170 × 34 × 28 m
v0.2 escort departure  21,927 t
v0.2 max accel          ~0.602 m/s²
v0.2 sustained accel    ~0.150 m/s²
v0.2 delta-v            ~38.5 km/s
```

### Current production engineering demonstrator

```text
id                      hull.escort_destroyer_v1
geometry                220 × 72 × 38 m
bare hull mass          12,000 t
internal volume         210,000 m³
max operational mass    26,000 t
crew baseline           180
life-support capacity   240
```

Production schema currently exposes reactor/drive/sensor/thermal integration slots, one LARGE spinal hardpoint and three major compartments. Он существует прежде всего как Stage-17.5 engineering demonstrator.

**Important:** `bareHullMassKg`, benchmark `designDryMassKg` и `combatDepartureMass` имеют разные semantics. Их запрещено сравнивать как одно и то же поле.

**Primary pressure:** mass + heavy-hardpoint / defensive-system integration.  
**Required specializations:** escort/PD, missile, strike; EW/fire-control variant допустим doctrine.  
**Stage-20 nominal stores endurance:** 30 days, provisional policy seed.

## 6.4. Cruiser — `IAHM-SHIP-04`

**Reference design:** General-Purpose Cruiser v0.2; Stage-20 provisional accepted calibration reference.

```text
design dry mass       43,445 t
departure mass        70,279 t
reaction mass         25,000 t
max accel             ~0.398 m/s²
sustained accel       ~0.100 m/s²
delta-v               ~44.0 km/s
```

Fitting utilization:

```text
mass   90.3%
volume 42.7%
power  44.3%
heat   67.0%
```

**Primary pressure:** mass + heat while preserving broad independent capability.  
**Required specialization:** general independent cruiser; later command/strike/refit variants may use same family if fitting closes.  
**Stage-20 nominal stores endurance:** 90 days, provisional policy seed.

## 6.5. Battlecruiser — `IAHM-SHIP-07`

**Reference design:** Battlecruiser Raider v0.2.

```text
design dry mass      113,075 t
departure mass       176,599 t
reaction mass         60,000 t
max accel             ~0.510 m/s²
sustained accel       ~0.133 m/s²
delta-v               ~41.5 km/s
```

Его доктринальная цена — disproportionately large propulsion installation и меньшая устойчивость относительно battleship.

**Primary pressure:** capital firepower + acceleration + armor trade.  
**Required specialization:** raider / pursuit / heavy hunter.  
**Endurance number:** UNAUTHORED as accepted Stage-20 mission policy.

## 6.6. Battleship — `IAHM-SHIP-05`

**Reference design:** Battleship v0.2; Stage-20 capital-combatant calibration.

```text
design dry mass      338,789 t
departure mass       545,765 t
reaction mass        200,000 t
max accel             ~0.252 m/s²
sustained accel       ~0.060 m/s²
delta-v               ~45.6 km/s
```

Fitting mass use ~89.8%; главный design pressure — mass / structural integration, capital weapon support, protection, command and repair reserve.

**Required specialization:** heavy line / breakthrough / station reduction.  
**Stage-20 nominal stores endurance:** 120 days, provisional policy seed.  
**Current reference ordinary FTL:** `EXCEEDS_TRANSLATED_MASS_LIMIT` for the 100,000 t reference-drive envelope; no hidden exception is allowed.

## 6.7. Carrier — `IAHM-SHIP-08`

**Current calculated reference is specifically a Fleet Carrier, not every carrier.**

```text
reference geometry       560 × 125 × 90 m
v0.2 design dry mass     275,543 t
v0.2 departure mass      508,143 t
reaction mass             200,000 t
max accel                ~0.177 m/s²
sustained accel          ~0.049 m/s²
delta-v                  ~50.0 km/s
optimal crew              1,600
reference air wing          120 craft
```

Carrier v0.2 deliberately has no XL direct-fire weapon; mass/volume/power reserve goes into hangars, aviation servicing, launch/recovery, stores and repair.

**Primary pressure:** hangar/aviation volume, flight operations, stores, sortie servicing, power/thermal reserve.  
**Stage-20 nominal stores endurance:** 120 days, provisional policy seed.  
**Current reference ordinary FTL:** overmass.

### Open carrier-family question

Doctrine explicitly allows Escort / Light / Fleet / Heavy Carrier. Current math closes **Fleet Carrier** only. Existing project light/drone-carrier concepts therefore must **not** automatically inherit the 560 m fleet-carrier physics. Stage 22 must decide whether light carriers are:

- a second carrier structural hull family;
- carrier specialization of a smaller military architecture;
- or a size tier inside one broader carrier family with distinct `HullDefinition`s.

Until then `IAHM-SHIP-08` is a functional family label, not one universal physical hull.

## 6.8. Patrol Craft — `IAHM-SHIP-06`

Only doctrine/reference-grid math is currently authored:

```text
35 × 9 × 8 m
300 t dry reference
430 t departure
130 t reaction mass
crew 8 / 14 / 20
1.50 / 0.45 m/s² max/sustained
36.0 km/s nominal delta-v
```

No concrete v0.2/Stage-20 production fit exists.

**Primary pressure:** ownership cost, low signature, response acceleration, minimal crew.  
**Required specializations:** patrol, police, customs/security.  
**Status:** DOCTRINE REFERENCE; Stage-22 production design required.

---

# 7. Civilian / auxiliary physical references

## 7.1. General Civilian Freighter — `IAHM-SHIP-09`

Stage-20 had no older physically closed reference and therefore authored a bounded calibration-only seed:

```text
design dry mass        8,000 t
cargo/mission mass    12,000 t
reaction mass          8,000 t
departure mass        28,000 t
max thrust              5.6 MN
sustained thrust        1.8 MN
exhaust velocity       80 km/s
max accel              0.200 m/s²
sustained accel       ~0.064 m/s²
delta-v               ~26.9 km/s
nominal endurance       14 days
```

**UNAUTHORED:** bounding dimensions, crew, detailed cargo geometry, production fit.  
**Required coverage:** early light trader / mixed container freighter; commodity type alone не создаёт новый hull.

## 7.2. Bulk / Material Freighter — `IAHM-SHIP-10`

v0.2 physically closes a bulk freighter:

```text
geometry               240 × 58 × 48 m
dry mass                28,000 t
cargo capacity           90,000 t
reaction mass            25,000 t
full departure mass     143,000 t
max/sustained thrust      12 / 4 MN
civilian exhaust          80 km/s
crew                      48
loaded max accel          ~0.084 m/s²
loaded sustained accel    ~0.028 m/s²
loaded delta-v            ~15.4 km/s
nominal endurance          45 days
```

Empty with full reaction tanks, the same hull is ~53,000 t, ~0.226 m/s² and ~51 km/s delta-v. Cargo state therefore changes actual performance without a hidden `loadedFreighterPenalty`.

**Primary pressure:** cargo mass, braking, delta-v, loading geometry and economic throughput.  
**Current reference ordinary FTL:** overmass while fully loaded.

## 7.3. Tanker / Reaction-Mass Hull — `IAHM-SHIP-11`

v0.2 Fleet Tanker reference:

```text
geometry                  250 × 65 × 55 m
dry mass                   40,000 t
own reaction mass           30,000 t
deliverable reaction mass   90,000 t
other fleet stores          10,000 t
full departure mass        170,000 t
max/sustained thrust         25 / 8 MN
exhaust velocity            100 km/s
crew                         72
loaded max accel             ~0.147 m/s²
loaded sustained accel       ~0.047 m/s²
loaded delta-v               ~19.4 km/s
nominal endurance             60 days
```

After offload with own tanks still full, mass falls to ~70,000 t, max acceleration rises to ~0.357 m/s² and available delta-v to ~56 km/s.

**Primary pressure:** tankage, transfer interfaces, own-vs-deliverable reaction mass, outbound/return asymmetry.  
**Current reference ordinary FTL:** overmass while fully loaded.

## 7.4. Mining Hull — `IAHM-SHIP-12`

Stage-20 bounded calibration seed:

```text
design dry mass        24,000 t
ore/mission mass       18,000 t
reaction mass          14,000 t
departure mass         56,000 t
max thrust               7.0 MN
sustained thrust         2.1 MN
exhaust velocity        80 km/s
max accel               0.125 m/s²
sustained accel         0.0375 m/s²
delta-v                ~23.0 km/s
nominal endurance        45 days
```

**UNAUTHORED:** dimensions, crew, final mining-equipment geometry and production fit.  
**Primary pressure:** industrial plant mass/volume, anchoring/extraction/capture equipment, ore load, maintenance.  
**Current reference ordinary FTL:** compatible with the provisional 100,000 t reference drive.

## 7.5. Fleet Logistics / Replenishment — `IAHM-SHIP-13`

Stage-20 representative role `FLEET_TANKER_LOADED / logistics support` имеет расчёт через Fleet Tanker, но **отдельный mixed-cargo ammunition/spares replenishment hull не закрыт**.

Current evidence may be used as a mobility/order-of-magnitude reference only:

```text
250 m tanker reference
170,000 t fully loaded reference
60-day nominal endurance
```

**Status:** PARTIAL.  
**Required future distinction:** ammunition/stores/spares handling, replenishment interfaces and protected logistics may justify a separate production design even if it shares an auxiliary backbone with tanker.

## 7.6. Repair / Salvage / Industrial Support — `IAHM-SHIP-14`

Doctrine and Stage-18 capability exist, but no physically closed hull reference was found.

**Status:** UNAUTHORED GAP.  
Do not infer length/mass/crew from tanker or miner automatically.

Required future fits:

- repair/tender;
- salvage/recovery;
- optional tug/mobile workshop derivatives.

Primary budgets must come from workshop volume, spare parts, manipulator/recovery interfaces, power, heat, crew and industrial work capability.

## 7.7. Small Craft / Drone — `IAHM-SHIP-15`

v0.2 physically closes one carrier interceptor at Craft-scale `C`:

```text
geometry              18 × 7 × 3.5 m
dry hardware mass      48 t
ammunition               6 t
reaction mass            22 t
departure mass           76 t
crew                      2
max thrust             0.75 MN
sustained thrust       0.18 MN
max accel              9.87 m/s²
sustained accel        2.37 m/s²
delta-v               34.2 km/s
FTL                    none
```

Это **не универсальный drone hull**. Он доказывает отдельный craft integration scale и carrier dependency.

Required future breadth:

- interceptor;
- strike craft;
- recon/EW craft;
- utility/cargo/repair craft;
- unmanned combat/recon/repair drones.

---

# 8. Operational endurance policy currently available

Это Stage-20 calibration policy seeds, а не derived crew-survival physics:

| Representative | Nominal routine stores endurance |
| --- | ---: |
| Torpedo Corvette | 7 d |
| Early Civilian Freighter | 14 d |
| Escort Destroyer | 30 d |
| Bulk Freighter Loaded | 45 d |
| Mining Ship | 45 d |
| Fleet Tanker Loaded | 60 d |
| Cruiser | 90 d |
| Battleship | 120 d |
| Carrier Aviation Group | 120 d |

Repository пока не имеет accepted equation вида `crew × food/water/O2 consumption → exact mission days`. Поэтому эти дни нельзя использовать как emergency survival duration, ammunition endurance, reactor lifetime или universal class stat.

---

# 9. FTL mass consequence

Current Stage-20 reference drive has:

```text
max translated mass = 100,000,000 kg
```

Accepted current representative compatibility:

```text
COMPATIBLE
- Torpedo Corvette
- current Escort Destroyer
- Early Civilian Freighter
- Mining Ship
- Cruiser

EXCEEDS REFERENCE LIMIT
- Battleship
- Bulk Freighter Loaded
- Fleet Tanker Loaded
- Carrier Aviation Group
```

Frigate, Patrol and Battlecruiser are not silently added to the accepted Stage-20 matrix merely by comparing their old benchmark mass. Final FTL families and capital translated-mass capability belong to Stage 22 and must pay real fitting/economic cost.

---

# 10. Calculation coverage of the 15 manifest families

| Manifest ID | Family | Current calculation state | Next missing closure |
| --- | --- | --- | --- |
| 01 | Corvette | **STRONG** — doctrine + v0.2 + Stage20 | production faction hull |
| 02 | Frigate | **STRONG** — doctrine + recon/EW v0.2 | production hull + accepted endurance |
| 03 | Destroyer | **PRODUCTION REFERENCE** + older benchmarks | final Stage22 promotion/re-author |
| 04 | Cruiser | **STRONG PROVISIONAL** | production fit |
| 05 | Battleship | **STRONG PROVISIONAL** | production fit + capital FTL family |
| 06 | Patrol Craft | **DOCTRINE ONLY** | concrete physical fit |
| 07 | Battlecruiser | **AUTHORING BENCHMARK** | production/Stage20 review |
| 08 | Carrier | **FLEET-CARRIER STRONG PROVISIONAL** | resolve light-vs-fleet carrier family structure |
| 09 | General Civilian Freighter | **STAGE20 PROPULSION SEED** | dimensions/crew/cargo/final fit |
| 10 | Bulk Freighter | **STRONG AUTHORING + STAGE20** | production civilian hull |
| 11 | Tanker | **STRONG AUTHORING + STAGE20** | production tanker hull / FTL solution |
| 12 | Mining Hull | **STAGE20 PROPULSION SEED** | dimensions/crew/mining fit |
| 13 | Fleet Logistics | **PARTIAL via tanker reference** | dedicated mixed-supply fit |
| 14 | Repair/Salvage | **GAP** | first physical reference design |
| 15 | Small Craft/Drone | **INTERCEPTOR STRONG**, broader family gap | strike/recon/utility/drone designs |

---

# 11. Decisions for Initial Asset/Hull Manifest v1

1. **Do not invent a second ship scale.** All future hull physical definitions remain SI and use common Stage-17.5 budgets.
2. **Use v0.1 military dimensions as central concept references**, not hard class boundaries.
3. **Use v0.2 concrete fits to understand limiting budgets**, not as final faction ships.
4. **Production destroyer supersedes old destroyer benchmark where semantics overlap**, but does not magically promote its provisional content to final Stage-22 balance.
5. **Do not assign dimensions to Early Freighter or Miner yet.** Their Stage-20 mass/propulsion seeds are sufficient for world-scale calibration but not for asset geometry.
6. **Do not treat Fleet Tanker as the final Fleet Logistics hull.** It currently supplies the closest calculated logistics reference only.
7. **Do not fabricate Repair/Salvage dimensions.** That family remains an explicit gap.
8. **Split or tier Carrier only after physical review.** Fleet Carrier math cannot automatically describe Light Carrier / drone-carrier concepts.
9. **Small craft remain dependent craft-scale assets**, not miniature FTL corvettes.
10. **Stage 22 owns final production breadth, faction differentiation and balance promotion.**

---

# 12. Immediate authoring priority produced by this audit

The biggest remaining calculation gaps are not the main military ladder. They are:

```text
1. Patrol Craft concrete fit
2. General Civilian Freighter geometry + crew + cargo architecture
3. Mining Hull geometry + crew + mining integration
4. Fleet Logistics dedicated mixed-supply fit
5. Repair / Salvage / Industrial Support first physical reference
6. Light Carrier physical tier decision
7. non-interceptor Small Craft / Drone breadth
```

Это и есть правильная следующая очередь hull authoring. Повторный расчёт corvette/frigate/cruiser/battleship с нуля сейчас был бы дублированием уже проделанной работы.

---

# 13. Acceptance rule for this companion

Этот документ может стать accepted companion baseline, если:

- все приведённые числа сохраняют provenance;
- benchmark/provisional/production authority не смешиваются;
- ни один rough corridor не используется как validation rule;
- unresolved hull families остаются unresolved до физического authoring;
- Stage 22 сохраняет право re-author/rebalance/promote final content;
- visual asset work не начинает фиксировать geometry там, где engineering content ещё отсутствует.

Краткая формула:

```text
existing physical evidence
+ explicit authority level
+ doctrine role
+ limiting budgets
+ unresolved gaps
→ safe initial hull authoring plan
```
