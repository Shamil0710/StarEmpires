# Star Empires — Ship Hull, Module and Fleet Doctrine Baseline

> Статус: **Design baseline v0.1**  
> Дата фиксации: **2026-08-15**  
> Назначение: единая концептуальная и математическая основа для корпусов, модулей, проектирования кораблей, боевой доктрины, логистики и дальнейшей балансировки.
>
> Этот документ фиксирует направление дизайна. Числа, помеченные как *balance seed*, являются стартовыми коэффициентами для симуляции и тестов, а не окончательными production-константами.

---

## 1. Базовые решения

### 1.1. Корабельная классификация строится по логике ВМФ, но не копирует её буквально

Используется знакомая иерархия ролей — патрульный корабль, корвет, фрегат, эсминец, крейсер, линейный крейсер, линкор, носители и вспомогательные суда — однако название класса описывает **место корабля в доктрине**, а не жёстко заданный набор вооружения.

Корабль формируется слоями:

```text
Hull Size
→ Hull Architecture
→ Doctrine Class
→ Specialization
→ Ship Design
→ Variant / Refit
→ Ship Instance
```

Пример:

```text
medium hull
→ military medium architecture
→ frigate
→ escort frigate
→ Kestrel-class
→ Kestrel Mk II
→ FNS Resolute
```

### 1.2. Больший корабль не является прямым улучшением меньшего

Главный балансный инвариант:

> **Ни один класс не должен становиться бесполезным только потому, что игрок открыл более крупные корпуса.**

Большие корпуса дают возможности, которые невозможно эффективно разместить на малых: тяжёлое вооружение, большие сенсоры, командные комплексы, глубокие магазины боеприпасов, ангары, броню, автономность и ремонтную инфраструктуру.

Малые корпуса сохраняют преимущества в:

- цене постройки;
- времени строительства;
- стоимости эксплуатации;
- ускорении и манёвренности;
- малой сигнатуре;
- рассредоточении риска;
- численности;
- способности перехватывать и сопровождать быстрые цели;
- локальном патрулировании и полицейских задачах.

### 1.3. Роль должна возникать из характеристик и компоновки

Запрещён как основной механизм баланс вида:

```text
Frigate: +20% sensor range
Destroyer: +20% missile damage
```

Роль должна по возможности возникать из реальных параметров:

- размера и массы корпуса;
- допустимых hardpoint;
- мощности реактора;
- тяги;
- теплоотвода;
- внутреннего объёма;
- сенсорной апертуры;
- сигнатуры;
- экипажа;
- автономности;
- установленных модулей.

Классовые бонусы допустимы только там, где они представляют реальную конструктивную особенность и не могут быть выражены базовой моделью.

---

## 2. Каноническая система измерений

### 2.1. Authoritative simulation использует метрическую систему / SI

Внутренние физические данные кораблей и модулей должны храниться в метрических единицах. Не вводятся произвольные `mass points`, `power points`, `range units` и подобные абстрактные единицы в качестве authoritative physics data.

| Величина | Каноническая единица | Допустимое отображение в UI |
|---|---:|---:|
| длина / размер корпуса | m | m, km |
| расстояние | m | m, km, Mm |
| площадь | m² | m² |
| объём | m³ | m³ |
| масса | kg | kg, t, kt |
| время | s | s, min, h, d |
| скорость | m/s | m/s, km/s |
| ускорение | m/s² | m/s², при необходимости доли `g` только как UI-справка |
| сила / тяга | N | N, kN, MN, GN |
| момент силы | N·m | kN·m, MN·m |
| энергия | J | kJ, MJ, GJ, TJ |
| мощность | W | kW, MW, GW, TW |
| температура | K | K; °C допустим только для удобства UI |
| тепловая мощность | W | kW, MW, GW |
| плотность | kg/m³ | kg/m³ |
| расход массы | kg/s | kg/s, t/s |

Угловая скорость внутри симуляции хранится в `rad/s`; UI может показывать `deg/s`.

### 2.2. Масштаб игры не отменяет SI

Игра может применять глобальное масштабирование расстояний или времени ради читаемого top-down gameplay, но такое масштабирование должно быть:

1. явно задокументировано;
2. едино для всех кораблей;
3. отделено от физических данных контента;
4. не использоваться как скрытый способ балансировать отдельные классы.

Например, если визуальная дистанция боя сжата относительно реалистичной, `WeaponDefinition` всё равно хранит согласованную метрическую дальность в рамках нашей игровой физики, а renderer/camera применяют общий presentation scale.

---

## 3. Иерархия определения корабля

### 3.1. Hull Size

Размерная категория задаёт пределы возможной конструкции, но не является классом ВМФ.

Предварительная шкала module/hardpoint size:

```text
S  = Small
M  = Medium
L  = Large
XL = Capital
```

Для расчётов fitting budget можно использовать номинальный вес размера:

```text
S  = 1
M  = 2
L  = 4
XL = 8
```

Это **не означает**, что `1 × L` полностью взаимозаменяем с `4 × S`. Размер определяет также геометрию, опоры, питание, охлаждение, скорость наведения, допустимую отдачу и другие ограничения.

### 3.2. Hull Architecture

Определяет физическую компоновку:

- конструкционную массу;
- полезный внутренний объём;
- расположение реакторного отсека;
- двигательную архитектуру;
- броневую ёмкость;
- расположение hardpoint;
- доступные firing arcs;
- внутренние отсеки;
- crew support;
- радиаторы;
- максимальные размеры отдельных модулей.

### 3.3. Doctrine Class

Доктринальная классификация фракции:

- Patrol Craft;
- Corvette;
- Frigate;
- Destroyer;
- Cruiser;
- Battlecruiser;
- Battleship;
- Carrier family;
- Auxiliary / Support family.

Она нужна для UI, ИИ, доктрины флота и мира, но не должна сама по себе магически менять физику.

### 3.4. Specialization

Примеры:

- escort;
- missile;
- torpedo;
- point-defense;
- reconnaissance;
- electronic warfare;
- command;
- assault / troop transport;
- carrier;
- logistics;
- repair;
- mining;
- salvage;
- exploration.

### 3.5. Ship Design

Конкретный серийный проект с фиксированным корпусом и набором модулей. Это экономически производимый объект.

### 3.6. Variant / Refit

Модернизация или специализированный вариант исходного проекта. Старые корабли могут переоборудоваться при наличии совместимости и производственных мощностей.

### 3.7. Ship Instance

Конкретное судно мира с именем, датой постройки, верфью, экипажем, капитаном, текущими повреждениями, боекомплектом, топливом, износом и историей.

---

## 4. Корабль ограничивается несколькими инженерными бюджетами одновременно

Один слот не должен быть универсальной валютой fitting. Конструкция проверяется сразу по нескольким ограничениям.

### 4.1. Масса

```text
totalMassKg = dryHullMassKg
            + armorMassKg
            + moduleMassKg
            + ammunitionMassKg
            + fuelMassKg
            + cargoMassKg
            + storesMassKg
```

```text
totalMassKg <= maxOperationalMassKg
```

Масса непосредственно влияет на движение:

```text
accelerationMps2 = availableThrustN / totalMassKg
```

### 4.2. Внутренний объём

```text
usedInternalVolumeM3 <= availableInternalVolumeM3
```

Особенно важен для:

- ангаров;
- грузовых отсеков;
- топливных баков;
- жилых модулей;
- магазинов боеприпасов;
- реакторов;
- госпиталей;
- мастерских;
- производственных модулей.

### 4.3. Энергетика

```text
continuousPowerMarginW = reactorContinuousOutputW
                       - continuousPowerDemandW
```

Импульсные потребители могут использовать аккумуляторы/конденсаторы:

```text
storedEnergyJ >= peakActionEnergyJ
```

Это создаёт выбор между оружием, сенсорами, РЭБ, двигателями и защитой.

### 4.4. Тепловой бюджет

Модули генерируют тепло, которое корабль должен отводить.

```text
netHeatW = generatedHeatW - radiatorDissipationW
```

Если `netHeatW > 0`, тепловой запас корабля растёт. Последствия перегрева могут включать:

- снижение скорострельности;
- ограничение реактора;
- снижение тяги;
- отключение модулей;
- повреждение оборудования;
- повышение ИК-сигнатуры.

### 4.5. Экипаж и автоматизация

```text
crewEfficiency = f(currentCrew, optimalCrew, automation, damage)
```

Недостаток экипажа ухудшает:

- ремонт;
- damage control;
- обслуживание;
- перезарядку там, где она требует персонала;
- устойчивость к длительной эксплуатации.

Автоматизация снижает crew requirement, но требует цены, массы, энергии и создаёт собственные точки отказа.

### 4.6. Сигнатура

Сигнатура не должна быть простой константой класса. На неё влияют:

- размер корпуса;
- тепловыделение;
- мощность реактора;
- работа двигателей;
- активные сенсоры;
- работа РЭБ;
- стрельба;
- состояние радиаторов.

Концептуально:

```text
signature = baseHullSignature
          × reactorLoadFactor
          × thermalFactor
          × propulsionFactor
          × activeEmissionFactor
```

Конкретные sensor models будут определены отдельно.

### 4.7. Эксплуатационная нагрузка

Помимо цены строительства учитываются:

```text
operatingCost = maintenance
              + crew
              + fuel
              + ammunition
              + spareParts
              + docking
              + scheduledOverhaul
```

Это основной системный ответ на вопрос, почему фракция не должна строить только capital ships.

---

## 5. Категории слотов

### 5.1. CORE

Обязательные или полууниверсальные системные позиции:

- reactor;
- main drive;
- maneuvering thrusters;
- FTL/jump system;
- command/computing core;
- life support.

### 5.2. WEAPON

Вооружение и его физические hardpoint.

Hardpoint должен знать минимум:

- allowed size;
- mount type;
- firing arc;
- recoil/structural limit;
- power connection limit;
- cooling connection limit.

### 5.3. UTILITY

- sensors;
- fire control;
- ECM;
- ECCM;
- communications;
- decoys;
- targeting support;
- additional cooling;
- specialized defensive electronics.

### 5.4. INTERNAL

- ammunition storage;
- fuel;
- batteries/capacitors;
- additional crew facilities;
- repair stores;
- internal armor/bulkheads;
- reserve systems;
- cargo expansion.

### 5.5. MISSION

Mission slots определяют профессию корабля:

- hangar;
- troop compartment;
- hospital;
- laboratory;
- mining equipment;
- salvage equipment;
- repair workshop;
- refinery/production;
- command center;
- additional cargo;
- deep-range fuel/stores.

Mission slot не должен без ограничений превращаться в дополнительный weapon slot. Адаптеры возможны позднее, но должны иметь физически объяснимую цену по массе, объёму, эффективности и/или survivability.

---

## 6. Предварительная сетка военных корпусов

Это стартовая fitting-сетка для прототипирования. Она должна быть проверена математической симуляцией до фиксации production balance.

| Класс | Weapon | Utility | Internal | Mission | Основной смысл |
|---|---|---|---|---|---|
| Patrol Craft | `2S` | `2S` | `2S` | `1S` | патруль, полиция, таможня |
| Corvette | `3S + 1M` | `2S + 1M` | `2S + 1M` | `1M` | screen, interception, torpedo attack |
| Frigate | `3S + 2M` | `2S + 2M` | `2S + 2M` | `1S + 1M` | escort, recon, EW, дальний патруль |
| Destroyer | `4S + 3M + 1L` | `2S + 2M` | `2S + 3M + 1L` | `2M` | специализированный флотский combatant |
| Cruiser | `4S + 4M + 2L` | `2S + 3M + 1L` | `3S + 4M + 2L` | `1M + 1L` | самостоятельные дальние операции |
| Battlecruiser | `4S + 4M + 3L + 1XL` | `3S + 3M + 1L` | `4S + 5M + 3L` | `2L` | быстрый тяжёлый рейдер / hunter |
| Battleship | `6S + 4M + 4L + 2XL` | `4S + 3M + 2L` | `6S + 6M + 4L + 1XL` | `2L + 1XL` | тяжёлая линия / breakthrough |

CORE slots задаются самой архитектурой корпуса и не включены в таблицу, потому что их набор зависит от конкретной технологической базы и propulsion architecture.

### 6.1. Patrol Craft

Не предназначен для генерального сражения. Ценность сохраняется за счёт минимальной стоимости владения, быстрой постройки и эффективного покрытия больших территорий большим числом единиц.

### 6.2. Corvette

Самый маленький полноценный боевой корабль. Основные задачи:

- перехват;
- ближний screen;
- торпедные атаки;
- борьба с малыми целями;
- разведка ближней зоны;
- защита станций и конвоев.

Тяжёлому оружию должно быть трудно эффективно сопровождать маневрирующий корвет.

### 6.3. Frigate

Рабочая лошадка флота. Основная ценность — не максимальный DPS, а сочетание:

- автономности;
- сенсоров;
- utility capacity;
- mission payload;
- умеренной стоимости.

Фрегат — естественная платформа для escort, recon и EW.

### 6.4. Destroyer

Флотский специалист. Получает первый полноценный доступ к тяжёлому hardpoint, но не должен быть универсальным заменителем фрегата.

Типовые варианты:

- point-defense destroyer;
- missile destroyer;
- strike destroyer;
- escort destroyer;
- EW/fire-control destroyer.

### 6.5. Cruiser

Крупнейшая базовая единица, рассчитанная на длительную самостоятельную операцию без постоянной поддержки крупного соединения.

Ценность:

- стратегическая автономность;
- крупные сенсоры;
- command capability;
- ремонтные и логистические резервы;
- тяжёлое вооружение без capital-level стоимости.

### 6.6. Battlecruiser

Не «улучшенный крейсер», а быстрый capital-grade hunter. Предназначен для:

- рейдов;
- уничтожения крейсеров;
- атаки транспортов и носителей;
- перехвата слабых соединений.

В прямом бою с полноценным линкором платит за скорость меньшей устойчивостью.

### 6.7. Battleship

Корабль генерального сражения и прорыва тяжёлой обороны.

Сильные стороны:

- sustained heavy fire;
- броня и структурная живучесть;
- тяжёлые батареи;
- большая энергетика.

Слабые стороны:

- высокая сигнатура;
- низкая манёвренность;
- высокая цена;
- дорогая эксплуатация;
- зависимость от screen/PD/escort;
- высокая стратегическая цена потери.

---

## 7. Носители — отдельное семейство, а не ступень после линкора

Carrier — это архитектурная специализация, где значительная часть объёма занята:

- hangar volume;
- обслуживанием аппаратов;
- топливом;
- боезапасом;
- ремонтными площадками;
- launch/recovery infrastructure.

Предварительное семейство:

- Escort Carrier;
- Light Carrier;
- Fleet Carrier;
- Heavy Carrier;
- поздний Supercarrier как редкий стратегический мегапроект, если он окажется нужен игре.

Носитель не должен одновременно иметь полноценный capital main battery и максимальный авиапарк без крайне высокой конструктивной цены.

### 7.1. Малые аппараты

Малые аппараты отделены от автономных кораблей и могут включать:

- fighter;
- interceptor;
- strike craft / bomber;
- reconnaissance craft;
- assault shuttle;
- cargo shuttle;
- repair craft;
- tug;
- combat/recon/repair drones.

Их ключевое отличие — ограниченная автономность и зависимость от базы/носителя.

---

## 8. Вспомогательные корабли являются обязательной частью доктрины

Военный флот не должен функционировать только боевыми кораблями.

Ключевые роли:

- fuel tanker;
- ammunition transport;
- fleet supply ship;
- repair ship;
- hospital ship;
- troop transport;
- salvage/rescue ship;
- tug;
- mobile base / depot;
- reconnaissance/sensor support.

Боевой флот, лишившийся логистики, должен терять operational endurance даже если не потерял ни одного крупного combatant.

Это связывает войну с живой экономикой и создаёт ценные цели помимо «убить самый большой корабль».

---

## 9. Гражданские семейства

Военная классификация не должна вытеснять гражданское судоходство.

### 9.1. Торговые

- courier;
- light freighter;
- container transport;
- bulk carrier;
- tanker;
- refrigerated/specialized transport;
- heavy transport.

### 9.2. Промышленные

- miner;
- gas harvester;
- refinery ship;
- factory ship;
- repair vessel;
- salvage vessel;
- tug.

### 9.3. Исследовательские

- scout;
- survey vessel;
- scientific vessel;
- deep-range expedition ship;
- sensor platform.

### 9.4. Колониальные

- passenger ship;
- colony transport;
- construction ship;
- colony ship;
- mobile infrastructure carrier.

Гражданские корпуса используют ту же физическую и fitting-модель, а не отдельную упрощённую систему.

---

## 10. Масштабирование оружия

Каждое оружие должно иметь минимум:

```text
damage
penetration
rangeM
preferredRangeM
baseAccuracy
tracking
cooldownS
projectileSpeedMps
continuousPowerW
shotEnergyJ
heatPerShotJ / heatGenerationW
massKg
volumeM3
ammunitionType / ammunitionUse
firingArc
validTargetEnvelope
```

### 10.1. Balance seed для размеров оружия

Первый сравнительный коэффициент для тестовой симуляции:

| Параметр | S | M | L | XL |
|---|---:|---:|---:|---:|
| Damage | 1.0 | 2.4 | 5.5 | 12.0 |
| Tracking | 1.0 | 0.65 | 0.35 | 0.12 |
| Range | 1.0 | 1.35 | 1.8 | 2.4 |
| Penetration | 1.0 | 2.0 | 4.5 | 10.0 |

Это не production balance. Цель таблицы — проверить принцип:

> рост размера оружия повышает дальность, пробитие и burst/sustained damage, но ухудшает способность эффективно поражать малые быстрые цели.

### 10.2. Попадание

Первичная абстракция:

```text
hitChance = baseAccuracy
          + sensorSolution
          + trackingContribution
          - targetEvasion
          - rangePenalty
          - ecmPenalty
```

Финальная функция должна быть непрерывной, детерминированной и ограниченной разумным диапазоном.

`targetEvasion` должен выводиться из наблюдаемых параметров, а не из скрытого бонуса класса:

- angular velocity;
- target dimensions;
- relative distance;
- acceleration/maneuver capability;
- quality of target solution.

### 10.3. Броня и пробитие

Для первого математического прототипа допустима гладкая функция:

```text
damageMultiplier = penetration / (penetration + effectiveArmor)
```

Она является только стартовой моделью. Позже можно перейти к толщине/углам/секциям, если усложнение реально улучшит gameplay.

---

## 11. Сенсоры, целеуказание и РЭБ

Разведывательный корабль должен быть боевой ценностью, даже если почти не наносит прямого урона.

Ключевой принцип:

> максимальная дальность оружия не обязана равняться дальности собственного качественного target lock.

Пример принципа:

```text
weaponEffectiveRangeM > ownHighQualityLockRangeM
```

Внешний разведчик, сенсорная платформа или другой член флота может передать `TargetSolution`.

Это создаёт реальные роли для:

- reconnaissance frigate;
- sensor destroyer;
- command cruiser;
- scout craft;
- AWACS-like carrier craft.

РЭБ должен воздействовать на качество информации и наведения:

- lock quality;
- missile guidance;
- sensor range;
- communications;
- target classification;
- datalink reliability.

РЭБ-корабль может иметь низкий собственный DPS, но повышать эффективность всего соединения.

---

## 12. Доктринальная взаимозависимость классов

| Класс | Основная роль | Что он не должен заменять |
|---|---|---|
| Patrol Craft | контроль пространства в мирное время | полноценный combatant |
| Corvette | screen, intercept, torpedo pressure | дальнюю автономную платформу |
| Frigate | escort, recon, EW, patrol | тяжёлый line combatant |
| Destroyer | специализированная защита/удар | универсальный дальний cruiser |
| Cruiser | самостоятельная проекция силы | дешёвое массовое сопровождение |
| Battlecruiser | быстрый heavy hunter | battleship в line battle |
| Battleship | breakthrough и тяжёлая линия | screen, recon, logistics |
| Carrier | проекция силы малыми аппаратами | бронированный artillery battleship |
| Auxiliary | operational endurance | боевой корабль первой линии |

Идеальный универсальный флот не должен существовать. Допустимы различные doctrines:

- battleship-centered;
- carrier-centered;
- missile standoff;
- fast raider;
- corvette/torpedo swarm;
- sensor + long-range kinetic/energy;
- defensive escort / convoy doctrine.

У каждой doctrine должны существовать естественные контрмеры через физику, информацию, логистику и экономику.

---

## 13. Экономика и строительство

### 13.1. Цена

```text
constructionCost = hullMaterials
                 + moduleMaterials
                 + labor
                 + shipyardOverhead
                 + specializedComponents
```

### 13.2. Производственные ограничения

Крупный корабль требует:

- подходящего размера верфи;
- достаточной industrial capacity;
- специализированного оборудования;
- материалов;
- времени;
- доступной supply chain.

### 13.3. Премия за концентрацию силы

Стоимость должна расти быстрее, чем простое суммирование количества малых кораблей, потому что capital ship покупает редкую возможность концентрировать тяжёлую защиту, энергетику и вооружение в одной платформе.

Стартовый *economic balance seed*:

| Класс | Относительная цена |
|---|---:|
| Patrol | 1 |
| Corvette | 3 |
| Frigate | 7 |
| Destroyer | 15 |
| Cruiser | 35 |
| Battlecruiser | 80 |
| Battleship | 180 |

Это не production prices. Реальная стоимость должна выводиться из материалов, модулей, труда и верфей; таблица нужна как sanity-check порядка величин.

---

## 14. Data-driven модель реализации

Текущий `ShipType` в Star Empires остаётся runtime role enum и не должен разрастаться до перечисления каждой модели корабля. Это согласуется с существующим `docs/content_catalog.md`.

Корабельная система должна эволюционировать в data-driven слой.

### 14.1. HullDefinition

Минимальные поля будущей модели:

```text
id
hullSize
lengthM
beamM
heightM

emptyStructuralMassKg
maxOperationalMassKg
internalVolumeM3

baseStructure
baseSignature
crewMin
crewOptimal

coreSlots
weaponHardpoints
utilitySlots
internalSlots
missionSlots

armorCapacityKg
baseCoolingW
baseFuelCapacityKg
```

### 14.2. ModuleDefinition

```text
id
slotCategory
size
massKg
volumeM3
continuousPowerW
peakPowerW / actionEnergyJ
heatGenerationW
crewRequired
costData
maintenanceData
compatibilityTags
```

Тип модуля добавляет свои параметры: оружие — damage/range/tracking; двигатель — thrustN; радиатор — dissipationW; сенсор — detection/lock metadata и т. д.

### 14.3. ShipDesignDefinition

```text
id
hullId
installedModules
armorConfiguration
fuelConfiguration
crewConfiguration
roleMetadata
```

Derived stats вычисляются из hull + modules, а не дублируются вручную без необходимости.

### 14.4. ShipVariantDefinition

Опциональная ссылка на базовый design + изменённые fitting choices для Mk II/refit/специализации.

### 14.5. Ship Instance

Runtime entity хранит:

- stable design/variant ID;
- current hull/armor/module condition;
- current mass-changing stores;
- fuel;
- ammunition;
- cargo;
- crew;
- location/velocity;
- damage;
- operational history metadata там, где это действительно нужно persistence.

Не создаётся Java subclass на каждую модель корабля.

---

## 15. Обязательные балансные инварианты

1. Более крупный корпус не делает меньший автоматически устаревшим.
2. Любой capital ship имеет задачи, которые дешевле и эффективнее выполняют малые корабли.
3. Линкор без escort/screen должен иметь реальные уязвимости.
4. Carrier платит объёмом, защитой и стоимостью за авиагруппу.
5. Recon/EW/command корабль может быть ценным без высокого собственного DPS.
6. Логистические корабли определяют дальность и продолжительность операций.
7. Масса fitting реально влияет на движение.
8. Cargo mass реально влияет на движение.
9. Игрок и AI используют одинаковые physics limits.
10. Heavy weapon получает преимущество против крупных целей, но не является оптимальным против всех размеров.
11. Small weapon сохраняет ценность через tracking/PD/screen roles.
12. Стоимость владения capital ship является самостоятельным балансным ограничением.
13. Роль корабля должна быть читаема из его физики, fitting и mission package.
14. Одна оптимальная сборка для всех ситуаций считается дефектом баланса.
15. Все authoritative физические параметры используют метрическую систему/SI.

---

## 16. Первые acceptance scenarios для будущей реализации

Перед массовым наполнением контентом система должна пройти хотя бы следующие детерминированные тесты.

### 16.1. Mobility

- пустой и полностью загруженный грузовик имеют разное ускорение и braking distance;
- корвет быстрее меняет velocity vector, чем cruiser при сравнимой технологии;
- тяжёлая броня повышает survivability и одновременно ухудшает mobility через массу.

### 16.2. Weapon size

- XL weapon существенно эффективнее S weapon против бронированной capital target;
- XL weapon имеет существенно меньшую вероятность эффективно сопровождать корвет;
- S/M PD остаётся полезной в fleet battle даже при наличии L/XL batteries.

### 16.3. Fleet composition

- battleship + escort выигрывает у изолированного battleship в сценарии missile/torpedo saturation при сравнимой общей цене;
- recon-enabled long-range group использует дальнобойное оружие лучше группы без качественного target solution;
- fleet без supply ships теряет operational endurance в длительной кампании.

### 16.4. Economics

- патрулирование безопасной системы крупными capital ships экономически невыгодно по сравнению с patrol/corvette force;
- capital ship остаётся оправдан там, где нужна концентрация тяжёлой силы;
- потеря большого корабля создаёт заметный промышленный и временной ущерб, а не только уменьшает combat power number.

---

## 17. Порядок реализации

Рекомендуемый порядок, чтобы не балансировать систему вокруг временных заглушек:

1. зафиксировать SI unit policy в data schema;
2. ввести `HullDefinition` и физическую массу/размеры;
3. ввести `ModuleDefinition` и slot-size compatibility;
4. добавить derived fitting budgets: mass, volume, continuous/peak power, heat, crew;
5. связать массу и thrust с общей inertial flight model;
6. собрать тестовые Corvette / Frigate / Destroyer / Cruiser / Battleship / Carrier / Freighter designs;
7. добавить weapon size, tracking и target-size interaction;
8. добавить sensor solution / external targeting / ECM foundation;
9. добавить эксплуатационную стоимость и supply dependence;
10. прогнать battle/economic simulation matrix;
11. корректировать slot counts и коэффициенты только по результатам наблюдаемого поведения;
12. после стабилизации модели массово наполнять фракции серийными designs и variants.

---

## 18. Связь с существующими документами

Этот baseline должен рассматриваться совместно с:

- `docs/content_catalog.md` — data-driven archetypes и stable IDs;
- `docs/flight_dynamics_and_combat_depth_roadmap.md` — масса, inertia, thrust и gate для advanced tactical AI;
- `docs/economic_invariants.md` — экономические инварианты;
- `docs/development_roadmap.md` — порядок развития проекта.

Если старый документ допускает абстрактные/game-scaled физические единицы, **для новых корабельных данных приоритет имеет установленная здесь политика SI/метрических authoritative units**. Presentation scale может быть игровым, но physical content data остаётся метрическим.

---

## 19. Открытые вопросы следующего design pass

Следующий этап должен уже перейти от относительных коэффициентов к `Ship Mathematics v0.1` и определить:

- реальные диапазоны длины корпуса в метрах для каждого hull envelope;
- dry mass в kg/t;
- max operational mass;
- thrust в N и типичное ускорение в m/s²;
- reactor output в W;
- energy storage в J;
- heat generation/dissipation в W;
- armor mass/thickness model;
- sensor ranges в m/km;
- weapon ranges и projectile speeds в m/s;
- fuel mass и расход в kg/s;
- crew ranges;
- construction material quantities;
- maintenance/operating cost model;
- окончательное число и геометрию hardpoint для тестовых корпусов.

До этого прохода приведённые slot counts и balance seeds являются **рабочей гипотезой**, которую разрешено менять ради достижения зафиксированных выше доктринальных и экономических инвариантов.
