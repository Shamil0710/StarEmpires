# Star Empires — Ship Mathematics v0.2

> Статус: **engineering / balance seed v0.2**  
> Дата: **2026-08-15**  
> Связан с: `docs/ship_hull_module_and_fleet_doctrine.md`, `docs/ship_mathematics_v0_1.md`, `docs/flight_dynamics_and_combat_depth_roadmap.md`  
> Назначение: собрать физически согласованные reference designs, проверить реальные fitting budgets и получить первые fleet-balance acceptance cases без скрытых class bonuses.

---

## 1. Что изменилось относительно v0.1

v0.1 задала hull envelopes, SI units, реактивную физику, энергетические и тепловые бюджеты. v0.2 впервые собирает из них **конкретные корабли**.

Проверяются одновременно:

```text
slots
mass
volume
power
heat
crew
reaction mass
ammunition
mission payload
acceleration
delta-v
fleet role
```

Главный принцип остаётся прежним:

> если роль класса не возникает из реальных характеристик и компоновки, класс считается плохо спроектированным и должен быть переделан, а не спасён процентным бонусом.

---

## 2. Уточнение семантики массы

В v0.1 `referenceDryMass` мог читаться двусмысленно. Для дальнейшей реализации принимается точная семантика:

```text
bareCoreMass = referenceDryMass - fittingMassBudget

designDryMass = bareCoreMass + installedNonCoreHardwareMass

combatDepartureMass = designDryMass
                    + ammunitionMass
                    + storesAndMissionPayloadMass
                    + reactionMass
```

То есть `fittingMassBudget` **входит** в reference dry envelope, а не добавляется поверх него второй раз.

`bareCoreMass` включает:

- primary structure;
- минимальную штатную защиту;
- основные реакторные и двигательные системы;
- FTL integration;
- command/computing core;
- базовое жизнеобеспечение;
- штатные магистрали;
- базовые резервные системы.

Selectable armor reinforcement, weapons, sensors, ECM, magazines, mission systems и дополнительные repair systems расходуют fitting budget.

Боеприпасы и consumables являются operational mass и не расходуют hardware fitting mass, но расходуют volume и увеличивают фактическую массу корабля.

---

## 3. Физическая база

Для боевых кораблей сохраняется:

```text
referenceExhaustVelocity = 100 000 m/s
```

Это соответствует `Isp ≈ 10 200 s`.

NASA Direct Fusion Drive studies дают порядок `Isp ~ 10 000 s` и порядка нескольких ньютонов тяги на MW fusion power для исследуемого концепта. Это используется только как опора для порядка величин; двигатели Star Empires предполагают значительно более зрелую технологию и значительно большую specific power.

Теплоотвод по-прежнему основан на радиационном теплообмене. Для горячего radiator loop:

```text
emissivity = 0.90
radiatorTemperature = 1100 K
referenceHeatFlux ≈ 74.7 kW/m²
```

Эта температура относится к специально спроектированному hot loop; habitation/electronics loops должны работать существенно холоднее.

### 3.1. Civilian propulsion

Для дешёвых bulk civilian hull допускается менее эффективная propulsion architecture:

```text
referenceCivilianExhaustVelocity = 80 000 m/s
```

Это создаёт естественную экономическую разницу между дорогим военным drive package и массовой гражданской установкой без искусственного speed modifier.

---

## 4. Малые аппараты: отдельный integration scale

Истребители/перехватчики меньше S ship-module scale. Для них вводится внутренний authoring-size:

```text
C = Craft-scale
```

`C` используется только внутри small-craft designs и не является заменой корабельным `S/M/L/XL`.

### 4.1. Reference carrier interceptor

```text
length = 18 m
beam = 7 m
height = 3.5 m
dry hardware mass = 48 t
ammunition = 6 t
reaction mass = 22 t
combat departure mass = 76 t
crew = 2
max thrust = 0.75 MN
sustained thrust = 0.18 MN
max acceleration = 9.87 m/s²
sustained acceleration = 2.37 m/s²
nominal delta-v = 34.2 km/s
rated electrical bus = 20 MW
sustained waste heat = 5 MW
hot radiator effective area ≈ 67 m²
```

Роль:

- interception;
- carrier screen;
- reconnaissance relay;
- attack on missiles/drones/small craft.

Аппарат **не имеет FTL** и не заменяет корвет: он зависит от носителя по ремонту, длительной автономности, deep maintenance, запасам и стратегической мобильности.

---

## 5. Weapon and support module seeds v0.2

Числа ниже нужны для reference designs и остаются tuning seeds.

| Module | Size | Hardware mass | Volume | Continuous power | Waste heat | Operational payload |
|---|---|---:|---:|---:|---:|---|
| Point-defense laser | S | 12 t | 20 m³ | 8 MW | 3 MW | none |
| Short-range interceptor rack | S | 25 t | 35 m³ | 1 MW | 0.2 MW | 12 × 1 t interceptors |
| Passive sensor | S | 15 t | 25 m³ | 2 MW | 0.8 MW | none |
| Fire-control node | S | 12 t | 20 m³ | 5 MW | 1.5 MW | none |
| Medium laser battery | M | 80 t | 120 m³ | 50 MW | 20 MW | none |
| Medium coilgun | M | 180 t | 250 m³ | 60 MW recharge | 15 MW | 80 × 25 kg projectiles |
| Anti-ship missile battery | M | 140 t | 300 m³ | 5 MW | 1 MW | 8 × 12 t missiles |
| Fleet interceptor battery | M | 120 t | 260 m³ | 8 MW | 2 MW | 24 × 1.5 t interceptors |
| Recon sensor suite | M | 150 t | 500 m³ | 30 MW | 10 MW | none |
| ECM suite | M | 160 t | 400 m³ | 40 MW | 20 MW | none |
| ECCM/fire-control support | M | 100 t | 300 m³ | 30 MW | 10 MW | none |
| Recon drone bay | M | 180 t | 700 m³ | 15 MW | 5 MW | ~30 t drones |
| Large coilgun | L | 1 000 t | 1 500 m³ | 300 MW recharge | 75 MW | 40 × 150 kg projectiles |
| Heavy torpedo battery | L | 900 t | 2 200 m³ | 20 MW | 5 MW | 6 × 100 t torpedoes |
| Area-defense interceptor battery | L | 700 t | 1 800 m³ | 40 MW | 10 MW | 48 × 2 t interceptors |
| Long-range sensor suite | L | 900 t | 3 000 m³ | 180 MW | 60 MW | none |
| Heavy ECM suite | L | 900 t | 2 500 m³ | 220 MW | 100 MW | none |
| Command center | L | 1 000 t | 6 000 m³ | 100 MW | 40 MW | none |
| Repair complex | L | 1 200 t | 7 000 m³ | 80 MW | 30 MW | spare parts separate |
| Capital kinetic weapon | XL | 8 000 t | ~12 000 m³ | 2 GW recharge | 400 MW | 24 × 1 t projectiles |
| Carrier hangar complex | XL | 14 000 t | ~80 000 m³ | 500 MW | 150 MW | ~30 small craft capacity |
| Aviation support complex | XL | 10 000 t | ~80 000 m³ | 100 MW | 20 MW | aviation stores separate |

### 5.1. Guided weapon reference payloads

```text
S interceptor:
wet mass ≈ 1–1.5 t
delta-v target ≈ 10–15 km/s
role = terminal / local interception

M anti-ship missile:
wet mass = 12 t
delta-v target ≈ 25 km/s
role = guided strike against corvette–capital targets

L area-defense interceptor:
wet mass = 2 t
delta-v target ≈ 20 km/s
role = fleet defense before terminal PD envelope

L heavy torpedo:
wet mass = 100 t
delta-v target ≈ 50 km/s
role = capital / station attack
```

Final seeker, acceleration, warhead and terminal guidance models remain separate future work.

---

## 6. Reference-design summary

| Design | Dry mass | Payload / ammo | Reaction mass | Departure mass | Max accel | Sustained accel | Δv |
|---|---:|---:|---:|---:|---:|---:|---:|
| Carrier interceptor | 48 t | 6 t | 22 t | 76 t | 9.87 m/s² | 2.37 m/s² | 34.2 km/s |
| Torpedo corvette | 1 316 t | 224 t | 600 t | 2 140 t | 1.028 m/s² | 0.280 m/s² | 32.9 km/s |
| Recon/EW frigate | 5 156 t | 258 t | 2 500 t | 7 914 t | 0.809 m/s² | 0.202 m/s² | 38.0 km/s |
| Escort / PD destroyer | 14 305 t | 622 t | 7 000 t | 21 927 t | 0.602 m/s² | 0.150 m/s² | 38.5 km/s |
| General-purpose cruiser | 43 445 t | 1 834 t | 25 000 t | 70 279 t | 0.398 m/s² | 0.100 m/s² | 44.0 km/s |
| Battlecruiser raider | 113 075 t | 3 524 t | 60 000 t | 176 599 t | 0.510 m/s² | 0.133 m/s² | 41.5 km/s |
| Battleship | 338 789 t | 6 976 t | 200 000 t | 545 765 t | 0.252 m/s² | 0.060 m/s² | 45.6 km/s |
| Fleet carrier | 275 543 t | 32 600 t | 200 000 t | 508 143 t | 0.177 m/s² | 0.049 m/s² | 50.0 km/s |
| Bulk freighter, full | 28 000 t | 90 000 t cargo | 25 000 t | 143 000 t | 0.084 m/s² | 0.028 m/s² | 15.4 km/s |
| Fleet tanker, full | 40 000 t | 100 000 t delivery cargo | 30 000 t | 170 000 t | 0.147 m/s² | 0.047 m/s² | 19.4 km/s |

---

## 7. Fitting-budget validation

| Design | Fitting mass used | Volume used | Electrical bus used | Continuous heat used |
|---|---:|---:|---:|---:|
| Torpedo corvette | 81.3% | 65.3% | 22.7% | 36.7% |
| Recon/EW frigate | 80.9% | 53.0% | 48.5% | **95.6%** |
| Escort destroyer | 86.1% | 30.5% | 19.1% | 30.4% |
| General cruiser | 90.3% | 42.7% | 44.3% | 67.0% |
| Battlecruiser raider | 82.7% | 26.6% | 42.9% | 56.1% |
| Battleship | 89.8% | 18.6% | 29.5% | 34.7% |
| Fleet carrier | 79.6% | 40.3% | 21.1% ship baseline | 34.0% ship baseline |

Это показывает важную особенность системы: разные роли упираются в **разные** ограничения.

- Recon frigate почти полностью упирается в тепло.
- Corvette — в fitting mass/volume.
- Cruiser — в массу и heat simultaneously.
- Battleship — прежде всего в mass/structural integration, а не в обычную электрическую сеть.
- Carrier хранит большой power/heat reserve для peak flight operations, servicing и simultaneous sortie cycles.

---

## 8. Torpedo Corvette reference design

### 8.1. Loadout

```text
WEAPON
2 × S point-defense laser
1 × S short-range interceptor rack
1 × M anti-ship missile battery

UTILITY
1 × S passive sensor
1 × S fire-control node
M utility intentionally unused

INTERNAL / MISSION
1 × S damage-control package
1 × M reserve missile magazine

SELECTABLE PROTECTION
~60 t additional local armor / splinter protection
```

Operational anti-ship load:

```text
8 missiles in primary battery
+ 8 reload missiles
= 16 × 12 t anti-ship missiles
```

### 8.2. Doctrine

Корвет намеренно **не получает собственный крупный sensor suite**. Он способен действовать самостоятельно на коротких дистанциях, но максимальную ударную эффективность получает от внешнего target-quality track.

Это делает recon frigate, picket, station sensor network или carrier reconnaissance реальной частью торпедной доктрины.

Сильные стороны:

- дешёвая распределённая missile capacity;
- высокая подвижность;
- маленькая сигнатура относительно capital ships;
- способность насыщать defense большим числом независимых launch platforms.

Слабые стороны:

- ограниченная endurance;
- малая repair capacity;
- слабый собственный long-range track;
- один серьёзный penetration может вывести корабль из боя;
- после расхода missile load его ударная ценность резко падает.

---

## 9. Recon / EW Frigate reference design

### 9.1. Loadout

```text
WEAPON
2 × S PD laser
1 × S interceptor rack
1 × M medium laser
1 × M anti-ship missile battery

UTILITY
1 × S passive sensor
1 × S fire-control node
1 × M recon sensor suite
1 × M ECM suite

INTERNAL / MISSION
1 × S damage control
1 × S thermal buffer support
1 × M capacitor package
1 × M endurance/stores package
1 × M recon drone bay

PROTECTION
~350 t selectable protection
```

### 9.2. Critical balance property

Reference fit consumes about **95.6% sustained heat rejection**.

Это хорошо: разведывательный корабль получает сильную электронику не бесплатно. Одновременные:

- active search;
- ECM;
- continuous laser fire;
- aggressive computing / communication

должны создавать реальный thermal-management problem.

Frigate therefore cannot be simultaneously best scout, best jammer and sustained brawler.

---

## 10. Escort / PD Destroyer reference design

### 10.1. Loadout

```text
WEAPON
4 × S PD laser
2 × M fleet interceptor battery
1 × M anti-ship missile battery
1 × L area-defense interceptor battery

UTILITY
passive sensor
fire-control node
M sensor suite
M ECCM

INTERNAL
reinforced magazines
capacitor reserve
damage control
thermal reserve

PROTECTION
~2 500 t selectable protection
```

Основная ценность эсминца — не direct anti-capital DPS, а **defensive throughput**.

Он первый hull, который может без чрезмерного компромисса одновременно нести:

- L area-defense system;
- несколько M interceptor batteries;
- локальную PD;
- достаточно сенсорной/ECCM поддержки.

---

## 11. General-Purpose Cruiser reference design

### 11.1. Loadout

```text
WEAPON
4 × S PD
2 × M coilgun
2 × M anti-ship missile battery
2 × L coilgun

UTILITY
S passive + fire control
M recon sensor
M ECM
M ECCM
L long-range sensor

INTERNAL / MISSION
capacitor reserve
magazine
stores
repair complex
command center
recon drone bay
long-endurance stores module

PROTECTION
~6 500 t selectable reinforcement
```

### 11.2. Why cruiser exists

Он не максимален ни в одной отдельной характеристике. Его преимущество — **полный набор возможностей в одном корпусе**:

- самостоятельный sensor picture;
- meaningful direct fire;
- missiles;
- ECM;
- repair;
- command;
- drones;
- большой delta-v;
- длительная автономность.

Именно поэтому cruiser является хорошим expedition / independent-operation hull, но не самым cost-efficient specialist в крупном fleet battle.

---

## 12. Battlecruiser Raider reference design

### 12.1. Loadout

```text
WEAPON
4 × S PD
2 × M coilgun
2 × M anti-ship missile battery
2 × L coilgun
1 × L heavy torpedo battery
1 × XL capital kinetic weapon

UTILITY
passive/fire-control/decoy
M sensor
M ECM
M ECCM
L long-range sensor

INTERNAL / MISSION
large capacitor reserve
missile magazines
command center
2 × repair complex
long-range stores

PROTECTION
~15 000 t selectable reinforcement
```

### 12.2. Pursuit check

Loaded sustained acceleration:

```text
battlecruiser ≈ 0.133 m/s²
cruiser       ≈ 0.100 m/s²
relative      ≈ 0.033 m/s²
```

При одинаковой начальной скорости и прямолинейном преследовании:

```text
initial separation = 1 000 km
ideal catch time ≈ 2.2 h

initial separation = 10 000 km
ideal catch time ≈ 6.8 h
```

Для 10 000 km case примерно:

```text
battlecruiser spends ≈ 3.3 km/s delta-v
cruiser spends ≈ 2.5 km/s delta-v
```

Следовательно, battlecruiser действительно способен навязать бой cruiser на tactical/operational scale, но его `41.5 km/s` nominal delta-v не делает его бесконечным преследователем.

---

## 13. Battleship reference design

### 13.1. Loadout

```text
WEAPON
6 × S PD laser
2 × M coilgun
2 × M fleet interceptor battery
2 × L coilgun
1 × L heavy torpedo battery
1 × L area-defense battery
2 × XL capital kinetic weapon

UTILITY
full S fire-control/decoy layer
M sensor + ECM + ECCM
L long-range sensor + heavy ECM

INTERNAL
large capacitor reserve
magazines
command center
2 × repair complex
XL capital storage / protected service complex

SELECTABLE PROTECTION
~65 000 t heavy reinforcement in addition to baseline structural protection
```

### 13.2. Capital-gun energy

Одна reference XL kinetic weapon:

```text
projectileMass = 1 000 kg
muzzleVelocity = 30 000 m/s
muzzleEnergy = 450 GJ
```

Две батареи дают очень высокую anti-capital / station-breaking capability, которой принципиально нет у корветов и фрегатов.

Battleship therefore exists для задач, где нужна концентрация:

- capital direct fire;
- station reduction;
- armored fleet center;
- command/redundancy;
- prolonged battle under damage.

Он **не должен** быть наиболее экономичным способом уничтожения корветов.

---

## 14. Fleet Carrier reference design

### 14.1. Ship fit

```text
WEAPON
8 × S PD
4 × M fleet interceptor battery
2 × L area-defense battery

UTILITY
passive/fire-control/decoy layer
2 × M sensor
M ECM
M ECCM
L sensor
L heavy ECM

INTERNAL
repair complexes
command center
stores
capacitors
magazines
1 × XL aviation support complex

MISSION
4 × XL hangar complex
```

No XL direct-fire weapon.

### 14.2. Reference air wing

```text
120 small craft total
reference average wet mass ≈ 76 t
craft wet mass aboard ≈ 9 120 t
aviation reserve reaction mass / ordnance ≈ 18 000 t
ship spare parts / aviation spares ≈ 5 000 t
```

Recommended doctrine mix for balance testing:

```text
48 interceptors
48 strike craft
12 recon / EW craft
12 utility / reserve craft
```

Only the interceptor is fully specified in v0.2; the other craft are mass/placeholders for carrier logistics until their own design pass.

### 14.3. Power reserve

Baseline installed systems use only about `3.17 GW` of the `15 GW` ship electrical bus.

Это **не избыточная бесплатная мощность**: carrier должен иметь несколько GW reserve для simultaneous flight operations, rapid servicing, launch/recovery machinery, damaged-craft recovery, workshop load and recharge cycles.

---

## 15. Bulk Freighter reference design

```text
length = 240 m
beam = 58 m
height = 48 m
gross volume ≈ 280 600 m³
dry mass = 28 000 t
cargo capacity = 90 000 t
reaction mass = 25 000 t
full departure mass = 143 000 t
max thrust = 12 MN
sustained thrust = 4 MN
civilian exhaust velocity = 80 km/s
rated electrical power = 300 MW
waste heat = 60 MW
crew = 48
```

### 15.1. Loaded vs empty physics

Fully loaded:

```text
max acceleration ≈ 0.084 m/s²
sustained acceleration ≈ 0.028 m/s²
delta-v ≈ 15.4 km/s
```

Same vessel with no cargo but full reaction-mass tanks:

```text
mass = 53 000 t
max acceleration ≈ 0.226 m/s²
delta-v ≈ 51.0 km/s
```

Это один из важнейших emergent results v0.2:

> cargo state сам меняет тактическое и стратегическое поведение корабля без `loadedFreighterPenalty`.

Полный freighter намного хуже убегает, тормозит и меняет маршрутную скорость. Порожний — значительно подвижнее.

---

## 16. Fleet Tanker reference design

```text
length = 250 m
beam = 65 m
height = 55 m
gross volume ≈ 375 400 m³
dry mass = 40 000 t
own reaction mass = 30 000 t
deliverable reaction mass = 90 000 t
other fleet stores = 10 000 t
full departure mass = 170 000 t
max thrust = 25 MN
sustained thrust = 8 MN
exhaust velocity = 100 km/s
rated electrical power = 600 MW
waste heat = 120 MW
crew = 72
```

Fully loaded:

```text
max acceleration ≈ 0.147 m/s²
sustained acceleration ≈ 0.047 m/s²
delta-v while retaining delivery cargo ≈ 19.4 km/s
```

После передачи 100 000 t снабжения fleet tanker становится значительно легче. Если его own tanks всё ещё полны:

```text
mass ≈ 70 000 t
max acceleration ≈ 0.357 m/s²
available delta-v ≈ 56.0 km/s
```

То есть tanker — не просто «cargo ship with different icon». Его outbound и return legs физически различаются.

---

## 17. First-pass missile-defense saturation harness

Final missile-defense probability model ещё нельзя честно считать без seeker, tracking error, interceptor acceleration and engagement geometry.

Для ранней проверки доктрины вводится **не damage model**, а временная метрика:

```text
defensive engagement opportunity
```

Она означает возможность системы сформировать отдельное качественное intercept engagement за 60 s. Она **не равна гарантированному уничтожению цели**.

Balance seeds:

```text
S PD laser                       = 3 opportunities / 60 s
S short-range interceptor rack   = 4 / 60 s
M fleet interceptor battery      = 6 / 60 s
L area-defense battery           = 16 / 60 s
```

Эти числа являются calibration placeholders и должны быть заменены физическим engagement solver, когда появятся seeker/track/acceleration models.

### 17.1. Torpedo-corvette salvo

Reference M anti-ship battery может выпустить initial concentrated salvo:

```text
2 missiles per corvette
```

24 torpedo corvettes:

```text
48 missiles in a coordinated first wave
```

### 17.2. Unescorted battleship defense

Battleship:

```text
6 × S PD       -> 18 opportunities
2 × M defense  -> 12
1 × L defense  -> 16
--------------------------------
nominal total  -> 46 / 60 s
```

Следствие:

> 48-weapon wave уже подходит к saturation threshold даже до учёта промахов, damaged channels, decoys and unfavorable geometry.

Это именно желаемое поведение: unescorted battleship не обязан погибнуть от 24 corvettes, но обязан считать такую атаку серьёзной угрозой.

### 17.3. Battleship + one escort destroyer

Destroyer добавляет:

```text
4 × S PD       -> 12
2 × M defense  -> 12
1 × L defense  -> 16
--------------------------------
additional     -> 40 / 60 s
```

Combined nominal defense:

```text
46 + 40 = 86 engagement opportunities / 60 s
```

Та же 48-missile wave теперь проходит через существенно более глубокий defensive envelope.

Это подтверждает базовую доктрину:

> destroyer остаётся полезным рядом с battleship, потому что добавляет специализированную defensive throughput, которую невыгодно бесконечно наращивать на самом battleship.

---

## 18. Why the battleship is not dominated by missile corvettes

Материальная масса одного battleship действительно огромна относительно корвета. Это намеренно и похоже на реальную военно-морскую логику дорогих capital assets.

Battleship оправдывает существование не через лучший `damage / credit` против любой цели, а через возможности, которые невозможно распределить по корветам без потери функции:

- XL capital direct fire;
- massive protected command infrastructure;
- high-end sensor / ECM integration;
- deep damage-control and repair reserve;
- station-breaking firepower;
- large magazines and prolonged endurance;
- ability to remain combat-capable after localized damage;
- fleet-center coordination.

Torpedo corvettes, напротив, являются дешёвым **threat projection / saturation tool** и поэтому являются естественным контрдавлением против плохо прикрытых capital ships.

Если будущая экономика покажет, что corvette swarm всегда рациональнее любого heavy fleet even after escorts/logistics, балансировать надо через реальные costs:

- compact high-performance drives;
- missile/seeker cost;
- crew losses;
- maintenance;
- tanker/tender requirement;
- strategic endurance;
- sensor dependence;
- replacement and shipyard throughput;

а не через скрытый `capital ship takes -80% damage from corvettes`.

---

## 19. Sensor support requirement

Для long-range kinetic fire не вводить абстрактный `weapon accuracy bonus from recon frigate`.

Использовать physical track quality:

```text
position covariance
velocity covariance
angular error
range error
timestamp / track age
estimated maneuver envelope
```

Огонь разрешается, когда predicted miss uncertainty относительно цели и projectile time-of-flight находится в допустимом envelope.

Recon frigate ценен потому, что:

- находится ближе к противнику;
- обеспечивает другой observation geometry;
- поддерживает track during emitter silence;
- уменьшает uncertainty;
- передаёт target solution другим кораблям.

Это должно позволить cruiser/battleship использовать дальнобойное оружие лучше, не добавляя процентный buff.

---

## 20. First acceptance outcomes

### A. Interceptor vs corvette

Expected:

- interceptor значительно быстрее и способен навязать close engagement;
- он не способен заменить corvette стратегически из-за отсутствия FTL, маленького endurance and dependency on carrier.

**PASS conceptually.**

### B. Torpedo corvette vs isolated capital

Expected:

- отдельный corvette почти не представляет гарантированной угрозы battleship;
- coordinated swarm способен приблизиться к saturation.

Reference 24-corvette / 48-missile first wave находится примерно на уровне nominal battleship defensive throughput.

**PASS as calibration seed.**

### C. Same swarm vs battleship + destroyer

Escort raises nominal defensive opportunities from ~46 to ~86 / 60 s.

**PASS:** destroyer has a clear fleet role independent of direct DPS.

### D. Recon frigate

Reference fit reaches ~95.6% heat envelope.

**PASS:** high-end sensors/ECM create a real thermal trade-off rather than a free utility stack.

### E. Battlecruiser pursuit

BC sustained acceleration `0.133 m/s²` vs cruiser `0.100 m/s²` produces meaningful pursuit advantage while cruiser retains slightly better endurance.

**PASS:** role emerges from propulsion/mass.

### F. Carrier

Carrier has powerful layered defense and strategic strike reach through craft, but no XL direct-fire weapon and a large fraction of mass/volume is tied to aviation infrastructure.

**PASS conceptually; strike-craft design needs v0.3.**

### G. Bulk freighter

Full cargo changes max acceleration from ~0.226 to ~0.084 m/s² and full-tank delta-v from ~51 to ~15.4 km/s.

**PASS strongly:** cargo physics materially affects economy and risk.

### H. Fleet tanker

Tanker is slow and vulnerable outbound, significantly more mobile after offload.

**PASS:** logistics ships have distinct operational state and create escort/route-planning gameplay.

---

## 21. Data-model consequences

Production implementation should not store only derived final stats.

Reference design should point to physical inputs:

```text
HullDefinition
ModuleDefinition[]
AmmunitionLoad[]
ReactionMassLoad
MissionPayload[]
```

Central calculator derives:

```text
designDryMassKg
operationalMassKg
maxAccelerationMps2
sustainedAccelerationMps2
deltaVMps
continuousPowerMarginW
continuousHeatMarginW
radiatorRequirementM2
weaponStoredEnergyJ
magazineMassKg
crewLoad
```

This is important for damaged/refitted ships: characteristics must change from state rather than remain a static archetype number.

---

## 22. Required deterministic validation tests when this reaches runtime

At minimum:

1. `TorpedoCorvetteDesignValidationTest`
   - all modules fit slots;
   - hardware mass <= fitting budget;
   - operational mass produces expected acceleration within tolerance.

2. `ReconFrigateThermalMarginTest`
   - reference fit remains below but near sustained heat rejection;
   - enabling an additional high-heat module can force overload.

3. `DestroyerDefenseCompositionTest`
   - defensive batteries expose more engagement capacity than direct-fire specialist of comparable fitting mass.

4. `BattlecruiserPursuitEnvelopeTest`
   - loaded BC has higher sustained acceleration than reference cruiser;
   - no class-name conditional used.

5. `LoadedFreighterMobilityTest`
   - cargo mass alone reduces acceleration and delta-v.

6. `FleetTankerOffloadMobilityTest`
   - after transferring cargo, same unchanged engines produce higher acceleration.

7. `CarrierMissionVolumeTest`
   - 4 hangar complexes + aviation support + air-wing payload remain within volume/mass envelopes.

8. `SaveLoadShipPhysicalStateContinuationTest`
   - reaction mass, ammo, cargo and module condition survive persistence and reproduce the same derived metrics.

---

## 23. What v0.2 intentionally does not pretend to know

Still not final:

- precise IR/active detection ranges;
- missile seeker physics;
- hit probability;
- armor penetration solver;
- nuclear / shaped-charge warheads;
- exact laser material-ablation model;
- shield model;
- FTL mass/power cost;
- exact financial prices;
- shipyard construction time;
- small-craft strike/recon variants;
- detailed rotational inertia from final hull geometry.

Ни один из этих пробелов не должен временно закрываться магическими class modifiers.

---

## 24. Next step: Ship Mathematics v0.3

Следующий pass должен сосредоточиться на **combat interaction physics**, а не добавлять ещё корпуса.

Priority:

1. sensor / track covariance model;
2. missile/interceptor acceleration and seeker envelope;
3. deterministic salvo-resolution prototype;
4. kinetic effective-range model from target maneuver uncertainty;
5. laser aperture / wavelength / dwell-time baseline;
6. first physical armor / fragmentation abstraction;
7. detailed strike-craft and recon-craft designs;
8. industrial-cost model from materials, module complexity and shipyard throughput;
9. first fleet-cost matchup matrix.

Только после этого следует решать, нужно ли менять hull slot counts v0.1.

---

## 25. Canonical conclusions v0.2

1. Current hull grid supports physically coherent reference designs without exceeding mass/power/heat envelopes.
2. Different roles naturally hit different engineering constraints.
3. Torpedo corvette is an externally-supported saturation platform, not a miniature cruiser.
4. Recon/EW frigate is heat-limited, making electronics specialization a real trade-off.
5. Escort destroyer has a clear defensive-throughput niche next to capital ships.
6. Cruiser remains the independent general-purpose hull.
7. Battlecruiser pursuit role emerges from thrust-to-mass ratio.
8. Battleship concentrates unique XL firepower, protection, redundancy and endurance rather than being universal anti-everything DPS.
9. Carrier is dominated by aviation infrastructure and retains large operational power reserve rather than carrying XL guns.
10. Cargo mass produces strong emergent civilian handling differences.
11. Fleet tanker naturally changes behavior before and after offload.
12. No class in the current test set requires an arbitrary percentage combat bonus to justify its existence.

---

## 26. External engineering references

- NASA TechPort / Direct Fusion Drive: Phase studies report roughly `2.5–5 N/MW` and `Isp ~10 000 s` for the studied DFD concept.
- NASA NTRS, *Mission Analysis for High Specific Impulse Deep Space Exploration*: fusion propulsion concepts are discussed across very high specific-impulse regimes.
- NASA Small Spacecraft Technology, Thermal Control: spacecraft thermal balance ultimately includes radiative heat rejection to space and radiator performance depends on area, emissivity and temperature.

These references support orders of magnitude and physical relationships only. They do not imply that Star Empires capital-ship engines are buildable with present-day technology.
