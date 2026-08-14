# Star Empires — Ship Mathematics v0.1

> Статус: **engineering / balance seed v0.1**  
> Дата: **2026-08-15**  
> Связан с: `docs/ship_hull_module_and_fleet_doctrine.md`, `docs/flight_dynamics_and_combat_depth_roadmap.md`  
> Цель: перевести корабельную доктрину в физически осмысленные численные диапазоны, пригодные для data-driven реализации и последующей симуляционной балансировки.

---

## 1. Что в этом документе считается «реалистичным»

Star Empires не использует современную ракетную технику как потолок возможностей мира: при таком ограничении крупные боевые корабли с заметным ускорением, мощным вооружением и длительной автономностью практически невозможны.

Вместо этого принимается следующая модель:

1. **Обычная локальная механика подчиняется ньютоновской физике.** Масса, тяга, ускорение, импульс, энергия, тепло и реактивная масса считаются явно.
2. **Основной субсветовой двигатель относится к зрелой fusion-era технологии.** Он значительно превосходит современную электрореактивную и ядерно-электрическую тягу, но всё ещё является реактивным двигателем: корабль должен выбрасывать рабочее тело и платить за delta-v реальной массой.
3. Номинальная скорость истечения для первой модели: `100 000 m/s` (100 km/s), что соответствует `Isp ≈ 10 200 s`.
4. Рабочее тело и fusion fuel — разные сущности. Масса fusion fuel мала по сравнению с массой reaction mass. В качестве плотного рабочего тела мир может использовать воду, аммиак, метан и другие доступные летучие вещества, которые затем переводятся в высокоэнергетическую плазму.
5. **Тяговая мощность не равна электрической мощности бортовой сети.** Основной двигатель должен по возможности передавать энергию непосредственно потоку плазмы/реактивной массы. Иначе радиаторы становятся физически невозможными.
6. `maxAcceleration` — короткий боевой/аварийный режим. `sustainedAcceleration` — режим, который корабль способен поддерживать существенно дольше без мгновенного разрушения теплового и топливного бюджета.
7. FTL и энергетические shields, если они сохраняются в сеттинге, являются отдельными сверхсовременными/экзотическими технологиями. Их нельзя объявлять «реалистичными» через обычную физику; их масса, энергия и ограничения должны моделироваться отдельно.

Внешняя инженерная опора для порядка величин:

- NASA Direct Fusion Drive studies дают порядок `Isp ~ 10 000 s` и несколько ньютонов тяги на мегаватт fusion power для ранних концептов;
- NASA studies по fusion propulsion рассматривают скорость истечения свыше `100 km/s` как характерный целевой диапазон для высокоэнергетической межпланетной тяги;
- spacecraft heat rejection в вакууме в конечном счёте ограничивается радиационным теплообменом.

Эти источники не доказывают реализуемость наших двигателей сегодня. Они дают физически осмысленный масштаб, от которого можно строить зрелую технологию мира игры.

---

## 2. Каноническая система единиц

Authoritative simulation data хранится в SI / метрической системе.

| Величина | Authoritative unit |
|---|---|
| длина | m |
| площадь | m² |
| объём | m³ |
| масса | kg |
| время | s |
| скорость | m/s |
| ускорение | m/s² |
| сила / тяга | N |
| крутящий момент | N·m |
| энергия | J |
| мощность | W |
| температура | K |
| массовый расход | kg/s |
| areal density брони | kg/m² |
| давление при необходимости | Pa |

UI может показывать km, t, MN, MW, GW, GJ, TJ и °C, но конверсия выполняется только на presentation boundary.

---

## 3. Основные формулы

### 3.1. Полная масса

```text
totalMassKg = dryReferenceMassKg
            + propellantMassKg
            + ammunitionDeltaKg
            + cargoMassKg
            + nonReferenceEquipmentDeltaKg
            + damageDebrisOrAttachedMassKg
```

`dryReferenceMassKg` для таблиц ниже означает корпус + штатные core systems + стандартную боевую комплектацию без рабочего тела, расходников и переменного груза.

### 3.2. Ускорение

```text
accelerationMps2 = availableThrustN / totalMassKg
```

Никаких скрытых `class handling bonus` поверх этой зависимости.

### 3.3. Реактивный расход

Для первой модели:

```text
massFlowKgPerSec = thrustN / exhaustVelocityMps
```

При `ve = 100 000 m/s`:

```text
1 MN thrust -> 10 kg/s reaction mass
```

### 3.4. Минимальная kinetic jet power

```text
jetPowerW = 0.5 × thrustN × exhaustVelocityMps
```

Это физический минимум энергии, уходящей в кинетическую энергию истечения. Реальная fusion power всегда выше из-за потерь и неидеальной передачи энергии.

При `ve = 100 km/s`:

```text
1 MN thrust -> 50 GW jet power
```

Поэтому даже сравнительно небольшой военный корабль является энергетически чрезвычайно мощной машиной.

### 3.5. Delta-v

```text
deltaV = exhaustVelocity × ln(initialMass / finalMass)
```

В первой сетке `initialMass = combatDepartureMass`, `finalMass = dryReferenceMass`.

### 3.6. Радиатор

```text
radiatedPower = emissivity × sigma × area × (T_radiator^4 - T_space^4)
```

Для design seed принимается:

```text
emissivity = 0.90
T_hotRadiator = 1100 K
```

При таком режиме эффективная излучательная способность составляет примерно:

```text
74.7 kW/m²
```

`effectiveRadiatingArea` считает реально излучающие поверхности. Двухсторонняя панель может иметь меньшую геометрическую planform area, если обе стороны имеют хороший view factor в глубокий космос.

Crew/electronics thermal loops должны работать при существенно более низкой температуре и могут использовать отдельные радиаторы/тепловые насосы.

---

## 4. Референсная сетка корпусов

Числа ниже — центральные reference designs, а не жёсткая граница класса. Конкретные фракции могут отклоняться примерно на ±20–30% при сохранении доктринальной роли.

`Gross volume` рассчитан как reference bounding box × form factor `0.45`. Реальный корпус не обязан быть прямоугольным.

| Класс | L × B × H | Gross volume | Dry reference mass | Combat departure mass | Reaction mass | Mean loaded density | Optimal crew |
|---|---:|---:|---:|---:|---:|---:|---:|
| Patrol craft | 35 × 9 × 8 m | ~1 130 m³ | 300 t | 430 t | 130 t | ~379 kg/m³ | 14 |
| Corvette | 65 × 16 × 13 m | ~6 080 m³ | 1 400 t | 2 000 t | 600 t | ~329 kg/m³ | 36 |
| Frigate | 110 × 24 × 20 m | ~23 760 m³ | 5 500 t | 8 000 t | 2 500 t | ~337 kg/m³ | 90 |
| Destroyer | 170 × 34 × 28 m | ~72 830 m³ | 15 000 t | 22 000 t | 7 000 t | ~302 kg/m³ | 160 |
| Cruiser | 280 × 55 × 45 m | ~311 850 m³ | 45 000 t | 70 000 t | 25 000 t | ~224 kg/m³ | 320 |
| Battlecruiser | 410 × 75 × 60 m | ~830 250 m³ | 120 000 t | 180 000 t | 60 000 t | ~217 kg/m³ | 560 |
| Battleship | 620 × 110 × 85 m | ~2 608 650 m³ | 350 000 t | 550 000 t | 200 000 t | ~211 kg/m³ | 1 000 |
| Fleet carrier | 560 × 125 × 90 m | ~2 835 000 m³ | 300 000 t | 500 000 t | 200 000 t | ~176 kg/m³ | 1 600 incl. air wing |

### 4.1. Почему крупные корпуса становятся менее плотными

Это намеренно.

При росте корабля быстрее растут:

- резервные проходы;
- compartment spacing;
- ангарные/ремонтные объёмы;
- fuel/reaction-mass tanks;
- магистрали;
- разнесение критических систем;
- void/spaced armor volumes;
- обслуживание крупных механизмов.

Поэтому capital ship не должен масштабироваться как сплошной металлический куб.

### 4.2. Crew ranges

Рекомендуемые диапазоны штатной численности:

| Класс | Minimum combat-capable | Optimal | High-manpower / low-automation |
|---|---:|---:|---:|
| Patrol | 8 | 14 | 20 |
| Corvette | 24 | 36 | 50 |
| Frigate | 60 | 90 | 120 |
| Destroyer | 120 | 160 | 220 |
| Cruiser | 250 | 320 | 450 |
| Battlecruiser | 450 | 560 | 750 |
| Battleship | 800 | 1 000 | 1 400 |
| Fleet carrier | 1 200 | 1 600 | 2 200 |

Численность влияет прежде всего на:

- damage control;
- ремонт;
- обслуживание вооружения;
- sortie rate авиакрыла;
- восстановление после повреждений;
- endurance при потерях экипажа.

Автоматизация уменьшает crew, но добавляет стоимость, энергопотребление и зависимость от вычислительных/электронных систем.

---

## 5. Тяга, ускорение и delta-v

Номинальный exhaust velocity для v0.1:

```text
ve = 100 000 m/s
```

| Класс | Max accel | Sustained accel | Max thrust at combat mass | Mass flow at max | Minimum jet power at max | Nominal full-tank delta-v |
|---|---:|---:|---:|---:|---:|---:|
| Patrol | 1.50 m/s² | 0.45 m/s² | 0.645 MN | 6.45 kg/s | 32.3 GW | 36.0 km/s |
| Corvette | 1.10 m/s² | 0.30 m/s² | 2.20 MN | 22 kg/s | 110 GW | 35.7 km/s |
| Frigate | 0.80 m/s² | 0.20 m/s² | 6.40 MN | 64 kg/s | 320 GW | 37.5 km/s |
| Destroyer | 0.60 m/s² | 0.15 m/s² | 13.2 MN | 132 kg/s | 660 GW | 38.3 km/s |
| Cruiser | 0.40 m/s² | 0.10 m/s² | 28.0 MN | 280 kg/s | 1.40 TW | 44.2 km/s |
| Battlecruiser | 0.50 m/s² | 0.13 m/s² | 90.0 MN | 900 kg/s | 4.50 TW | 40.5 km/s |
| Battleship | 0.25 m/s² | 0.06 m/s² | 137.5 MN | 1 375 kg/s | 6.88 TW | 45.2 km/s |
| Fleet carrier | 0.18 m/s² | 0.05 m/s² | 90.0 MN | 900 kg/s | 4.50 TW | 51.1 km/s |

### 5.1. Почему battlecruiser быстрее cruiser/battleship

Это его доктринальная цена.

Battlecruiser отдаёт часть:

- armor mass;
- redundancy;
- sustained fire capacity;
- mission flexibility

за disproportionately large propulsion installation. Поэтому он способен навязать дистанцию крейсеру и уйти от линкора, но хуже выдерживает прямой обмен тяжёлым огнём.

### 5.2. Почему max acceleration не является «крейсерским режимом»

На максимальной тяге быстро растут:

- расход reaction mass;
- тепловая нагрузка;
- нагрузка на nozzle/magnetic confinement;
- структурная вибрация;
- maintenance debt.

`maxAcceleration` следует трактовать как минуты, а не часы непрерывной работы без последствий.

### 5.3. Пример торможения

Для скорости `1 000 m/s` идеальная тормозная дистанция:

```text
d = v² / (2a)
```

- Patrol при 1.5 m/s²: ~333 km, ~11.1 min.
- Frigate при 0.8 m/s²: ~625 km, ~20.8 min.
- Cruiser при 0.4 m/s²: ~1 250 km, ~41.7 min.
- Battleship при 0.25 m/s²: ~2 000 km, ~66.7 min.

Это подтверждает, что тяжёлый корабль нельзя заставить разворачиваться и останавливаться как истребитель без нарушения собственной физики.

---

## 6. Электрическая энергетика и тепло

Основная propulsion power не проводится через обычную ship electrical bus.

Электросеть обслуживает:

- сенсоры;
- computing;
- ECM/ECCM;
- active cooling;
- pumps;
- life support;
- turret drives;
- lasers;
- coilgun/railgun charging;
- capacitor recharge;
- hangar/repair systems;
- FTL auxiliary loads, если они позже будут определены.

### 6.1. Reference electrical bus

| Класс | Rated electrical output | Sustained waste heat design point | Hot radiator effective area @ 1100 K |
|---|---:|---:|---:|
| Patrol | 50 MW | 10 MW | ~135 m² |
| Corvette | 150 MW | 30 MW | ~400 m² |
| Frigate | 400 MW | 80 MW | ~1 070 m² |
| Destroyer | 1.0 GW | 200 MW | ~2 680 m² |
| Cruiser | 3.0 GW | 600 MW | ~8 030 m² |
| Battlecruiser | 8.0 GW | 1.5 GW | ~20 100 m² |
| Battleship | 20 GW | 4.0 GW | ~53 500 m² |
| Fleet carrier | 15 GW | 3.0 GW | ~40 200 m² |

`Rated electrical output` не означает, что вся эта мощность превращается в тепло внутри корпуса одновременно. Значительная часть оружейной энергии покидает корабль в beam/projectile energy; некоторые системы работают импульсно.

### 6.2. Combat heat buffer

Корабль должен иметь ограниченный thermal accumulator, позволяющий:

- временно поднять оружейную мощность выше continuous cooling envelope;
- частично сложить/экранировать уязвимые радиаторы;
- пережить краткий период повреждения cooling loop.

Стартовые capacity seeds:

| Класс | Thermal buffer |
|---|---:|
| Patrol | 20 GJ |
| Corvette | 60 GJ |
| Frigate | 200 GJ |
| Destroyer | 600 GJ |
| Cruiser | 2 TJ |
| Battlecruiser | 6 TJ |
| Battleship | 20 TJ |
| Fleet carrier | 15 TJ |

Это не бесплатная энергия. После боя накопленное тепло надо сбросить, что создаёт реальный post-combat recovery period.

---

## 7. Fitting budgets

Количество hardpoint не является единственным ограничением.

Каждый design проходит одновременно:

```text
slotCompatibility
massBudget
volumeBudget
continuousPowerBudget
peakPower / storageBudget
heatBudget
crewBudget
centerOfMass / geometry validation
ammunition / propellant storage validation
```

### 7.1. Non-core fitting reserve

| Класс | Fitting mass budget | Selectable equipment / mission volume budget |
|---|---:|---:|
| Patrol | 80 t | 250 m³ |
| Corvette | 450 t | 1 500 m³ |
| Frigate | 1 800 t | 6 000 m³ |
| Destroyer | 5 000 t | 18 000 m³ |
| Cruiser | 16 000 t | 75 000 m³ |
| Battlecruiser | 40 000 t | 200 000 m³ |
| Battleship | 110 000 t | 600 000 m³ |
| Fleet carrier | 120 000 t | 1 100 000 m³ |

`fittingMassBudget` и `volumeBudget` запрещают заполнить каждый hardpoint самым тяжёлым допустимым модулем.

---

## 8. Размерные классы модулей

S/M/L/XL — **integration classes**, а не абстрактные единицы объёма.

Они задают максимальный уровень структурной, кабельной, охлаждающей и геометрической интеграции. Один XL slot нельзя автоматически заменить восемью S slots.

| Size | Typical module mass | Typical integration length | Typical module volume | Continuous bus interface | Typical use |
|---|---:|---:|---:|---:|---|
| S | 1–50 t | 2–6 m | 10–100 m³ | up to ~10 MW | PD, small sensor, decoy, light internal system |
| M | 30–300 t | 5–15 m | 100–800 m³ | up to ~50 MW | missile battery, medium gun, ECM, large sensor |
| L | 200–2 000 t | 12–35 m | 800–8 000 m³ | up to ~250 MW | heavy gun, command center, large reactor auxiliary, heavy mission system |
| XL | 1 000–15 000 t | 30–80+ m | 5 000–100 000+ m³ | up to ~2 GW | capital weapon, major hangar, strategic sensor, capital mission complex |

Это soft validation envelope. Специализированный module subtype может отклоняться, но обязан платить реальными mass/volume/power/heat costs.

Weapon peak discharge может значительно превышать continuous bus interface, если модуль содержит собственный pulsed-power storage. В таком случае отдельно хранятся:

```text
storedEnergyJ
maxDischargeW
rechargePowerW
rechargeEfficiency
thermalLossW
```

---

## 9. Core slot classes

| Hull | Reactor | Main drive | FTL | Computer / command |
|---|---|---|---|---|
| Patrol | S | S | optional S | S |
| Corvette | M | M | S | S |
| Frigate | M | M | M | M |
| Destroyer | L | L | M | M |
| Cruiser | L | L | L | L |
| Battlecruiser | XL | XL | L | L |
| Battleship | XL | XL | XL | XL |
| Fleet carrier | XL | XL | XL | XL |

`XL drive` или `XL reactor` может внутренне быть кластером нескольких реакторных/двигательных камер. Slot описывает интеграцию в корпус, а не обязательно одну монолитную машину.

---

## 10. Базовые selectable slots v0.1

### 10.1. Patrol

```text
WEAPON   2S
UTILITY  2S
INTERNAL 2S
MISSION  1S
```

### 10.2. Corvette

```text
WEAPON   3S 1M
UTILITY  2S 1M
INTERNAL 2S 1M
MISSION  1M
```

### 10.3. Frigate

```text
WEAPON   3S 2M
UTILITY  2S 2M
INTERNAL 2S 2M
MISSION  1S 1M
```

### 10.4. Destroyer

```text
WEAPON   4S 3M 1L
UTILITY  2S 2M
INTERNAL 2S 3M 1L
MISSION  2M
```

### 10.5. Cruiser

```text
WEAPON   4S 4M 2L
UTILITY  2S 3M 1L
INTERNAL 3S 4M 2L
MISSION  1M 1L
```

### 10.6. Battlecruiser

```text
WEAPON   4S 4M 3L 1XL
UTILITY  3S 3M 1L
INTERNAL 4S 5M 3L
MISSION  2L
```

### 10.7. Battleship

```text
WEAPON   6S 4M 4L 2XL
UTILITY  4S 3M 2L
INTERNAL 6S 6M 4L 1XL
MISSION  2L 1XL
```

### 10.8. Fleet carrier

```text
WEAPON   8S 4M 2L
UTILITY  4S 4M 2L
INTERNAL 6S 6M 4L 1XL
MISSION  4XL
```

Carrier deliberately has no XL weapon hardpoint in the baseline. Если конструктор хочет capital gun + carrier air wing, это должен быть другой, значительно более компромиссный hull architecture.

---

## 11. Weapon engineering seeds

Ниже не финальный combat balance, а проверка того, что выбранные корабельные мощности дают разумный порядок оружейных характеристик.

### 11.1. Small point-defense laser

```text
sizeClass = S
mass = 12 t
volume = 20 m³
continuousElectricalDraw = 8 MW
beamPower = 5 MW
wasteHeatAtFullFire ≈ 3 MW
opticalAperture ≈ 0.5 m
```

Предполагаемая роль: ракеты, дроны, лёгкие аппараты.

### 11.2. Medium laser battery

```text
sizeClass = M
mass = 80 t
volume = 120 m³
continuousElectricalDraw = 50 MW
beamPower = 30 MW
opticalAperture ≈ 1.5 m
```

Не вводить отдельный magical damage multiplier. Дальность и эффективность должны позже возникать из aperture, wavelength, pointing error, target motion и material interaction.

### 11.3. Medium coilgun

```text
sizeClass = M
mass = 180 t
volume = 250 m³
projectileMass = 25 kg
muzzleVelocity = 15 000 m/s
muzzleEnergy = 2.81 GJ
nominalRechargePower = 60 MW
nominalCycle ≈ 60 s
```

### 11.4. Large coilgun

```text
sizeClass = L
mass = 1 000 t
volume = 1 500 m³
projectileMass = 150 kg
muzzleVelocity = 20 000 m/s
muzzleEnergy = 30 GJ
nominalRechargePower = 300 MW
nominalCycle ≈ 120 s
```

### 11.5. XL capital kinetic weapon

```text
sizeClass = XL
mass = 8 000 t
projectileMass = 1 000 kg
muzzleVelocity = 30 000 m/s
muzzleEnergy = 450 GJ
nominalRechargePower = 2 GW
nominalCycle ≈ 240 s
```

Эти значения показывают важный балансный вывод: capital kinetic hit не должен просто снимать несколько процентов условного HP малого корабля. Прямое попадание такой энергии является катастрофическим; защита строится вокруг предотвращения попадания, damage localization и redundancy.

---

## 12. Реалистичная дальность кинетического оружия

У кинетического оружия нет физического `max range` в вакууме. Есть **effective range против маневрирующей цели**.

```text
timeOfFlight = range / projectileVelocity
possibleLateralDisplacement ≈ 0.5 × targetLateralAcceleration × timeOfFlight²
```

Пример M coilgun (`15 km/s`):

- 200 km → 13.3 s flight time;
- 1 000 km → 66.7 s;
- 3 000 km → 200 s.

Даже `1 m/s²` поперечного ускорения даёт потенциальное смещение:

- ~89 m за 13.3 s;
- ~2.2 km за 66.7 s;
- ~20 km за 200 s.

Поэтому тяжёлые пушки естественно сильнее против крупных/медленных целей, а не получают искусственный `-50% accuracy vs corvette`.

Sensor solution, salvo patterns, target constraints и surprise могут расширять effective range.

---

## 13. Missile / torpedo envelope seeds

Guided weapons нужны именно потому, что могут корректировать trajectory после пуска.

Стартовые size seeds:

| Weapon | Wet mass | Typical onboard count per mount | Own delta-v target | Role |
|---|---:|---:|---:|---|
| S interceptor | 0.5–2 t | 8–32 | 5–15 km/s | missile / drone interception |
| M anti-ship missile | 5–20 t | 4–16 | 15–40 km/s | corvette–cruiser targets |
| L torpedo | 50–200 t | 2–8 | 30–80 km/s | capital targets / long approach |

Missile range should not be a simple radius. It depends on remaining delta-v, target velocity, guidance updates, terminal reserve and desired intercept geometry.

---

## 14. Armor and survivability

### 14.1. Armor is not another HP bar

При высоких скоростях кинетическое оружие слишком энергоёмко для модели «линкор просто принимает сотни прямых попаданий».

Reference armor allocation as fraction of dry mass:

| Класс | Approx armor / protection mass allocation |
|---|---:|
| Patrol | ~5–8% |
| Corvette | ~8–12% |
| Frigate | ~10–14% |
| Destroyer | ~12–17% |
| Cruiser | ~15–20% |
| Battlecruiser | ~14–18% |
| Battleship | ~22–28% |
| Fleet carrier | ~7–10% |

### 14.2. What armor is for

Armor protects against:

- fragments;
- near misses;
- partial laser dwell;
- debris;
- secondary explosions;
- grazing kinetic impacts;
- splinters from intercepted missiles;
- localized penetrations reaching neighboring compartments.

Capital survivability must come from layers:

```text
early detection
→ missile interception
→ point defense
→ maneuver / deception
→ spaced armor
→ armored citadel
→ compartmentalization
→ redundant power/data/fluid paths
→ local fire suppression
→ damage control
```

This is the main reason an unescorted battleship remains a bad doctrine.

### 14.3. Armor data should be physical

Preferred authoritative fields:

```text
armorMaterialId
armorArealDensityKgPerM2
armorCoverageM2
spacedLayerDistanceM
criticalCitadelCoverageFraction
```

A first playable approximation may still derive aggregate armor durability from them, but the source data should remain physical.

---

## 15. Signature model

Не использовать абстрактный `signature = 40` как первичный физический параметр.

Хранить измеримые источники:

```text
radiatedWasteHeatW
radiatorTemperatureK
radiatingAreaM2
activeSensorEmissionW
communicationsEmissionW
engineThrustN
engineExhaustPowerW
projectedCrossSectionM2
```

Позже SensorModel переводит это в detection probability / track quality.

Design invariant:

> Горящий на большой тяге fusion warship не является «невидимым». Stealth в вакууме означает уменьшение/маскирование/управление сигнатурой и delaying a firing-quality track, а не магическое исчезновение.

---

## 16. Carrier mathematics

Reference fleet carrier имеет `4XL mission slots` и около `1.1 million m³` selectable mission/equipment budget.

Стартовая оценка авиакрыла:

```text
96–144 combat / utility small craft
```

в зависимости от:

- среднего размера craft;
- объёма reaction mass / munitions;
- количества maintenance bays;
- резервных аппаратов;
- sortie tempo.

Нужно считать не только число аппаратов, но и:

```text
hangarVolumeM3
craftMassKg
aviationReactionMassKg
aviationAmmunitionKg
maintenanceCrew
launchRecoveryRatePerMinute
repairThroughputKgPerHour
```

Так carrier не превращается в бесконечный источник истребителей.

---

## 17. Почему классы остаются полезными

### Patrol

Побеждает стоимостью присутствия. Это дешёвый способ иметь физический корабль в системе, досматривать трафик и ловить гражданские цели.

### Corvette

Лучшее отношение ускорения/стоимости среди самостоятельных боевых кораблей. Подходит для interception, torpedo attack, screening и patrol combat.

### Frigate

Минимальный корпус, на котором одновременно помещаются хорошие sensors, FTL autonomy, meaningful weapons и длительная endurance. Естественная база для recon/EW/escort.

### Destroyer

Первый корпус, который без чрезмерного компромисса несёт L weapon + достаточно S/M систем для fleet defense. Поэтому это specialist screen, а не «улучшенный фрегат».

### Cruiser

Первый действительно self-contained heavy combatant: глубокий запас reaction mass, большой mission volume, repair capacity, sensors и значительная firepower.

### Battlecruiser

Платит защитой за capital-grade firepower + заметно более высокое acceleration envelope. Нужен для raids, pursuit и уничтожения крейсерских соединений.

### Battleship

Концентрирует тяжёлые weapons, armor, redundancy и sustained battle endurance. Он дорог и инерционен; без screen становится удобной целью для guided weapons.

### Fleet carrier

Концентрирует мобильные sensors, interceptors и strike craft, но жертвует capital direct-fire armament и частью armor budget. Требует escort.

---

## 18. Data model proposal

### HullDefinition

```text
id
lengthM
beamM
heightM
grossInternalVolumeM3
referenceDryMassKg
maxOperationalMassKg
reactionMassCapacityKg
fittingMassBudgetKg
selectableVolumeBudgetM3
optimalCrew
minimumCrew
maxCrew
ratedElectricalPowerW
sustainedHeatRejectionW
radiatorEffectiveAreaM2
thermalBufferJ
maxForwardThrustN
sustainedForwardThrustN
reverseThrustN
lateralThrustN
rotationalTorqueNm
referenceExhaustVelocityMps
coreSlots[]
weaponSlots[]
utilitySlots[]
internalSlots[]
missionSlots[]
```

### ModuleDefinition

```text
id
slotCategory
sizeClass
massKg
volumeM3
continuousPowerW
peakBusPowerW
wasteHeatW
crewRequired
coolantFlowRequirementKgPerSec
structuralReactionForceN
storedEnergyJ
maxDischargeW
```

Specialized weapon fields:

```text
projectileMassKg
muzzleVelocityMps
beamPowerW
opticalApertureM
missileWetMassKg
missileDeltaVMps
magazineCapacity
```

### ShipDesignDefinition derived metrics

```text
dryMassKg
combatLoadedMassKg
remainingFittingMassKg
remainingVolumeM3
continuousPowerMarginW
continuousHeatMarginW
maxAccelerationMps2
sustainedAccelerationMps2
deltaVMps
nominalMassFlowKgPerSec
```

Derived values должны вычисляться централизованным deterministic design validator/calculator, а не дублироваться руками по системам.

---

## 19. Validation invariants

Design invalid, если выполняется хотя бы одно:

```text
installedMass > fittingMassBudget
installedVolume > selectableVolumeBudget
continuousPowerUse > ratedElectricalPower
continuousWasteHeat > sustainedHeatRejection
module.sizeClass > slot.maxSize
module.slotCategory incompatible with slot
crewRequired > supportedCrew
loadedMass > maxOperationalMass
reactionMass < 0
ammoMass < 0
```

Дополнительно предупреждения, но не всегда hard fail:

```text
no point defense
no passive sensor
no FTL on long-range doctrine ship
thermal overload endurance < doctrine threshold
insufficient reaction mass for doctrine threshold
crew below optimal
center of mass outside drive control envelope
```

---

## 20. Balance acceptance scenarios

### A. Corvette swarm vs battleship without screen

Ожидание: battleship уничтожает отдельные корветы чрезвычайно быстро при попадании, но часть swarm должна получать шанс пройти через fire-control saturation / missile defense.

Если battleship без escort стабильно уничтожает swarm без риска, S/M defense слишком универсальна или guided weapon pressure слишком слаб.

### B. Battleship + destroyer screen

Ожидание: та же группа корветов/торпед становится значительно менее эффективной. Destroyer создаёт реальную ценность даже при меньшем direct DPS.

### C. Frigate recon + long-range cruiser

Ожидание: cruiser с внешним target-quality track реализует большую часть дальности вооружения. Без recon quality его effective engagement envelope заметно сокращается.

### D. Battlecruiser pursuit

Ожидание: battlecruiser способен догнать cruiser при сопоставимом запасе delta-v, но не должен хотеть прямой duel с battleship.

### E. Carrier without escort

Ожидание: carrier силён на дистанции через air wing, но опасно уязвим, если противник прорвался к direct-fire range или перегрузил его defensive screen.

### F. Loaded civilian freighter

При добавлении cargo mass тот же engine thrust обязан дать меньшее acceleration и большую braking distance без специальных scripted modifiers.

---

## 21. Что пока намеренно НЕ фиксируется

Следующие цифры нельзя честно закрепить до отдельного моделирования:

1. точные detection ranges сенсоров;
2. окончательные effective weapon ranges;
3. стоимость кораблей в credits;
4. construction time;
5. shield physics;
6. FTL mass/power model;
7. окончательные armor penetration equations;
8. final missile acceleration / seeker model;
9. detailed rotational inertia by exact sprite/hull geometry;
10. exact fighter/small-craft grid.

Для этих областей этот документ задаёт входные физические параметры, но не делает вид, что неопределённая технология уже просчитана.

---

## 22. Следующий математический шаг

`Ship Mathematics v0.2` должен построить simulation harness минимум для следующих reference designs:

1. interceptor / combat small craft;
2. torpedo corvette;
3. escort / PD destroyer;
4. recon/EW frigate;
5. general-purpose cruiser;
6. battlecruiser raider;
7. battleship;
8. fleet carrier;
9. bulk freighter;
10. fleet tanker.

Для каждого прогонять:

```text
mass breakdown
power balance
heat balance
acceleration
braking
delta-v
weapon recharge
ammo endurance
sensor support requirements
maintenance / crew pressure
fleet matchup outcomes
```

Только после этих прогонов числа слотов, masses и powers следует переводить из `balance seed` в production tuning constants.

---

## 23. Канонические выводы v0.1

1. Reference warship scale: **35 m / 430 t patrol craft → 620 m / 550 000 t battleship**.
2. Fleet carrier — отдельная архитектура, а не линкор с бесплатным ангаром.
3. Reference combat reaction-mass reserve даёт примерно **36–51 km/s delta-v** при `ve = 100 km/s`.
4. Боевые acceleration envelopes находятся примерно от **1.5 m/s² у patrol craft до 0.25 m/s² у battleship**, с отдельным более быстрым battlecruiser profile.
5. Capital propulsion требует **terawatt-class jet power**; это прямое следствие реактивной физики, а не условная игровая цифра.
6. Ship electrical buses находятся от десятков MW до десятков GW и отделены от основного propulsion energy path.
7. Heat rejection является реальным ограничением fitting и combat endurance.
8. S/M/L/XL — structural integration classes; mass, volume, power и heat budgets остаются независимыми ограничителями.
9. Armor не делает capital ships неуязвимыми: survivability опирается на layered defense и redundancy.
10. Роль класса должна возникать из физических параметров и компоновки, а не из скрытых процентных class bonuses.
