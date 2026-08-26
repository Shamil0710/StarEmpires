# Star Empires — фракции: gameplay, balance и visual identity bible

> **Версия:** 1.0  
> **Статус:** CANONICAL CROSS-FACTION DESIGN CONTRACT  
> **Дата:** 2026-08-26  
> **Scope:** семь крупных фракций, их системная игровая идентичность, физическая цена преимуществ,
> контр-игра, визуальный язык кораблей и персонажей, а также граница core/post-core производства.  
> **Важно:** документ не создаёт faction-only authority и не заменяет физику, экономику, дипломатию,
> войну, территорию, knowledge или persistence существующих стадий.

Связанные authority:

- `docs/factions/faction_roster_and_development_horizon.md`;
- `docs/factions/empire_systemic_identity.md`;
- `docs/factions/industrial_union_systemic_identity.md`;
- `docs/factions/post_core_faction_horizon.md`;
- `docs/factions/empire_visual_bible.md`;
- `docs/factions/industrial_union_visual_bible.md`;
- `docs/characters/character_master_prompt.md`;
- `docs/factions/faction_balance_validation_framework.md`;
- `docs/factions/faction_implementation_roadmap.md`.

Если этот документ конфликтует с simulation authority, authoritative code/tests и профильный stage
contract имеют приоритет. Если он конфликтует с отдельным старым визуальным экспериментом, приоритет
имеет этот документ вместе с утверждённой faction visual bible.

---

# 1. Канонический roster и production boundary

Долгосрочный мир содержит семь крупных фракций:

1. **Империя**;
2. **Индустриальный Союз**;
3. **Директорат**;
4. **Лига Свободных Систем**;
5. **Пограничная Конфедерация**;
6. **Консорциум**;
7. **Кочевой Флот**.

До закрытия Stage 23 production-complete являются только:

- **Империя** — primary gold slice;
- **Индустриальный Союз** — mandatory contrast slice.

Остальные пять фракций являются каноническими, но входят в **post-core faction program**. Детальный
дизайн сейчас нужен для архитектурной совместимости, будущего art direction и предотвращения
одинаковых «скинов», однако он не превращает их в Stage-21/22/23 exit criteria.

---

# 2. Главный принцип фракционной асимметрии

Фракция — не таблица бонусов.

```text
география + материальная база + институты + policy + doctrine + история
→ разные lawful решения в одних системах
→ разные заказы, конструкции, маршруты, флоты и договоры
→ разные физические преимущества и уязвимости
→ узнаваемое поведение без hidden faction cheat
```

Запрещённая модель:

```text
if faction == X:
    production *= 1.20
    damage *= 1.10
    sensorRange *= 1.15
```

Допустимая модель:

```text
повторяющаяся серия
+ подготовленная оснастка
+ общие компоненты
+ стабильная поставка
→ меньше changeover и diversity burden
→ измеримо выше throughput именно этой линии
→ дороже сменить серию и опаснее потерять общий bottleneck
```

Каждое фракционное преимущество обязано отвечать на три вопроса:

1. **Какая существующая authority создаёт результат?**
2. **Какие реальные ресурсы, время, масса, объём, работа, риск или политическая цена уплачены?**
3. **Как другой актор может противодействовать этому через общие правила?**

Если хотя бы одного ответа нет, преимущество остаётся flavour и не получает simulation effect.

---

# 3. Сводная gameplay-матрица

| Фракция | Основная сила | Цена силы | Естественная уязвимость | Главный стратегический вопрос |
|---|---|---|---|---|
| **Империя** | институциональная непрерывность, резервы, тяжёлая ремонтопригодная техника | высокий capital lock, maintenance и supply footprint | перерастянутые линии, медленная смена курса, дорогая потеря | «Можем ли мы надёжно удерживать обязательство десятилетиями?» |
| **Индустриальный Союз** | стандартизация, серии, bulk throughput, восполнение | сырьевой голод, retooling inertia, hub concentration | разрыв коридоров и common bottleneck | «Может ли промышленная система воспроизводить нужный темп?» |
| **Директорат** | precision, automation, information quality, capability density | дорогие specialised inputs, thermal/maintenance complexity | saturation и разрыв precision-chain | «Стоит ли высокая эффективность сложности и зависимости?» |
| **Лига Свободных Систем** | распределённый частный капитал, route diversity, быстрый market response | слабее централизованная мобилизация, risk flight, политическая фрагментация | рост риска, кризис доверия, collective-action failure | «Какие incentives сделают действие выгодным множеству автономных акторов?» |
| **Пограничная Конфедерация** | salvage, substitution, refit, распределённая низкоинфраструктурная устойчивость | heterogeneous support, ниже peak efficiency, больше ручной работы | длительная высокоинтенсивная война и лишение salvage base | «Что можно сохранить, заменить и заставить работать доступными средствами?» |
| **Консорциум** | ownership, concessions, capital и economic control без annexation | leverage/debt exposure, зависимость от контрактной легитимности | коалиционная регуляция, default, национализация с последствиями | «Как контролировать поток и актив, не оплачивая суверенитет напрямую?» |
| **Кочевой Флот** | mobile economic nodes, relocation, non-territorial resilience | уязвимые habitat/industry carriers, access/refuel dependence | denial транзита, разделение convoy, потеря мобильного ядра | «Где весь флот сможет безопасно жить, работать и двигаться дальше?» |

Таблица не задаёт числовые статы. Она задаёт причинные цепочки и тестируемые условия.

---

# 4. Общие оси различия

Для сравнения используются не «уровни цивилизации», а независимые оси:

- централизация решения;
- форма собственности;
- территориальность;
- capital intensity;
- производственная commonality;
- скорость смены производственной серии;
- component diversity;
- route concentration;
- supplier diversity;
- repair-versus-replace preference;
- information quality/freshness;
- automation/crew dependence;
- willingness to accept attrition;
- legalism and treaty memory;
- sovereignty sensitivity;
- tolerance to substitute inputs;
- mobility of economic infrastructure;
- war termination and recovery path.

Ни одна ось не должна быть строго лучше во всём диапазоне. Например:

- высокая стандартизация снижает diversity burden, но повышает common-mode risk;
- высокая автоматизация снижает crew demand, но требует electronics/power/maintenance;
- сильная централизация ускоряет coherent mobilization, но повышает цену неверного общего решения;
- распределённый рынок быстро находит прибыльный маршрут, но может одновременно уйти из опасного
  региона в момент, когда государству особенно нужна freight capacity.

---

# 5. Империя

## 5.1. Gameplay formula

```text
государственная преемственность
+ централизованный procurement
+ стратегические резервы
+ тяжёлая serviceable engineering
+ repair/refit network
→ высокая staying power при подготовке
→ дорогая и медленная чрезмерная экспансия
```

## 5.2. Институты и экономика

Империя предпочитает:

- формальные государственные заказы;
- reserve floors критических материалов, ammunition, reaction mass и repair parts;
- крупные защищённые repair/refit hubs;
- мобилизационные приоритеты с реальным вытеснением гражданских очередей;
- долгий lifecycle корпуса и модернизацию через ordinary refit;
- признанный контроль, access и treaty continuity.

Преимущество возникает, когда до кризиса оплачены резервы, инфраструктура и запас capability.
Мобилизация не увеличивает output из воздуха: она переставляет очереди, расходует stock и создаёт
послевоенный backlog.

## 5.3. Fleet doctrine

Предпочтительный флот:

- тяжёлые, защищённые line/escort combatants;
- layered PD и резервированные sensors;
- axial kinetic/VLS там, где fit это оправдывает;
- tanker, replenishment и repair support как обязательная часть operation;
- reserve/home-defense group;
- staged reinforcement вместо глубокого импровизированного броска.

Империя сохраняет повреждённый дорогой capability, если retreat/repair реально достижимы. Она не
получает повышенный withdrawal threshold по имени: решение учитывает objective, readiness, supply,
repair opportunity и политическую цену потери.

## 5.4. Сильные стороны

- живучесть подсистем через armour, compartmentation и redundancy;
- высокая доля repairable damage при доступе к инфраструктуре;
- устойчивость кратковременного route disruption за счёт оплаченных reserves;
- сильная formal diplomacy и долговременная treaty memory;
- способность последовательно удерживать подготовленную оборону.

## 5.5. Ограничения

- высокая dry mass, стоимость и shipyard work;
- большой расход maintenance/spares/freight;
- медленная замена capital loss;
- institutional commitment и switching cost;
- дальнее владение быстро становится security/logistics burden;
- reserve hoarding может вытеснить гражданское потребление и инвестиции.

## 5.6. Контр-игра

- растянуть операции по нескольким направлениям;
- изолировать repair/refit hubs и replenishment train;
- заставить тратить резервы на ложные/второстепенные угрозы;
- избегать невыгодного frontal attrition и атаковать tempo/route assumptions;
- использовать дипломатическую цену слишком широких обязательств.

## 5.7. Корабельный visual language

Полная authority: `docs/factions/empire_visual_bible.md`.

Обязательные cues:

- длинная осевая масса;
- защищённый бронированный нос;
- визуально самая тяжёлая центральная цитадель;
- сегментированные armor belts;
- recessed/sectional radiators;
- массивный serviceable stern;
- крупные access panels, replacement plates и multiple-generation refit history;
- graphite/gunmetal, warm ivory, restrained burgundy, редкая old brass;
- симметрия и строгая иерархия без baroque/fantasy ornament.

Корабль должен отличаться от Союза даже в grayscale: у Империи читается цельная защищённая
цитадель и длительная история конкретного объекта, у Союза — ритм повторяемых секций.

## 5.8. Character overlay

Всегда применяется поверх `docs/characters/character_master_prompt.md`.

- статус читается через крой, материал, чистоту и точность;
- enlisted/technicians: worn blue-grey/navy, dirty ivory protection, regulated repairs;
- officers: structured pressure-compatible uniform, restrained burgundy rank accents;
- senior command: warm ivory command layer, rare brass insignia, no costume excess;
- profession остаётся видимой: engineer, medic и logistician не являются одним officer recolor;
- традиция выражается современной практичной формой, не буквальной исторической униформой.

Запрещено менять hand-painted ink-and-gouache STYLE LOCK на photorealistic/3D «ради реализма формы».

## 5.9. Player-facing feel

Игрок встречает дорогие, хорошо обслуживаемые корабли, формальные contracts/access rules, развитые
repair yards, крупные государственные заказы, устойчивые конвои и серьёзные последствия нарушения
обязательств. Сила доступна через отношения, рынок, службу и собственную промышленность, а не menu unlock.

---

# 6. Индустриальный Союз

## 6.1. Gameplay formula

```text
industrial coordination
+ standardized families
+ repeated series
+ bulk logistics
→ высокий воспроизводимый throughput
→ сильная зависимость от сырья, коридоров и common bottlenecks
```

## 6.2. Институты и экономика

Союз предпочитает:

- batch/framework procurement;
- длительные производственные серии;
- общую номенклатуру engines/reactors/components/ammunition;
- большие refining/component/assembly hubs;
- scheduled bulk flows и depot network;
- инвестиции в устранение измеримого bottleneck.

Преимущество серии существует только пока есть оснастка, familiarity/workflow, component continuity и
стабильная очередь. Смена design family оплачивает changeover либо теряет накопленную специализацию.

## 6.3. Fleet doctrine

- массовые common platforms;
- предсказуемые combined-arms formations;
- сильные escorts, tankers, freighters и repair/replenishment assets;
- повторяемые missile/kinetic programs, если их поддерживает ammunition chain;
- attrition допускается, когда replacement lead time и material flow это подтверждают;
- withdrawal наступает, когда broken bottleneck делает продолжение невосполнимым.

## 6.4. Сильные стороны

- низкий component diversity burden внутри поддержанной family;
- стабильный queue throughput и bulk procurement;
- быстрое реальное восполнение стандартных unit при живой сети;
- простая training/spares/service commonality;
- сильная масштабная логистика и shipyard utilization.

## 6.5. Ограничения

- высокий постоянный спрос на raw/processed materials;
- концентрация уязвимых hubs и chokepoints;
- retooling cost и series inertia;
- common-component failure поражает сразу большую долю fleet;
- replacement competition с гражданской экономикой;
- predictable platform families облегчают противнику planning.

## 6.6. Контр-игра

- нарушить high-volume route, а не обязательно выиграть генеральное сражение;
- атаковать один common component/facility capability;
- вынудить быстро менять doctrine/series;
- создавать heterogeneous threat, против которого единая серия не оптимальна;
- заставить расходовать replacement capacity быстрее физического пополнения input.

## 6.7. Корабельный visual language

Полная authority: `docs/factions/industrial_union_visual_bible.md`.

- sectional massing вместо одной центральной цитадели;
- повторяемые trapezoid/shallow-hex modules;
- recognisable common engine banks;
- standard PD/VLS/sensor/service housings;
- важные cargo/handling/refuel/repair interfaces;
- foundry graphite, mill steel, machine slate, workshop olive, assembly grey;
- oxide ochre/safety amber как service language, instrument teal локально;
- clean heavy-duty modern engineering, не scrapyard и не retro-Soviet.

## 6.8. Character overlay

- статус через qualification, function, responsibility и equipment precision;
- standardized graphite/slate/olive utility families;
- geometric role/rank tabs, limited ochre/teal accents;
- technicians/workers выглядят профессионально и социально значимо, не бедно;
- officers ближе к senior technical staff, чем к имперской аристократической службе;
- износ организован: grease, abrasion, stitched repair без rags/scavenger aesthetic.

Общий Character Master Prompt остаётся неизменным medium/style authority.

## 6.9. Player-facing feel

Большие производственные рынки, common spare/module families, длинные contracts, заметные freight
corridors, крупные yards и множество jobs вокруг shortage/convoy/bottleneck. Быстрое восполнение
возможно только если игрок помог сохранить материальную сеть.

---

# 7. Директорат

> **Production status:** POST-CORE DIRECTION LOCK; отдельная production visual bible создаётся только
> после Faction Architecture Review.

## 7.1. Gameplay formula

```text
precision manufacturing
+ automation
+ high-performance systems
+ superior actor-bounded information quality
→ высокая capability density
→ specialised, дорогая и хрупкая dependency chain
```

## 7.2. Институты и экономика

Директорат координирует исследовательские, инженерные и security institutions вокруг measurable
technical capability. Он предпочитает:

- precision components и специализированные facilities;
- automation там, где power/electronics/maintenance окупаются;
- меньшие серии более сложных systems;
- качественные sensors, datalink, fire control и EW;
- раннее действие на основании свежей, но всё ещё actor-bounded информации;
- защиту IP, rare suppliers и technical personnel.

## 7.3. Fleet doctrine

- компактные high-capability ships;
- recon/EW/fire-control network;
- точное дальнее применение ограниченного боезапаса;
- automation и меньший crew там, где fitting это подтверждает;
- операции с высокой подготовкой target solution;
- избегание длительной тупой attrition, разрушающей редкие assets.

## 7.4. Сильные стороны

- capability per mass/crew на выбранных advanced systems;
- быстрее формирует качественный track/decision при наличии sensors/network;
- высокая precision strike/EW coordination;
- специализированная automation снижает некоторые recurring labour demands;
- может выигрывать engagement до входа противника в preferred geometry.

## 7.5. Ограничения

- rare precision/electrical/material inputs;
- specialised yards и technicians;
- высокий heat/power/maintenance pressure;
- дорогой replacement и низкая substitutability;
- network degradation резко снижает преимущество;
- saturation заставляет расходовать дорогие channels/ammunition.

## 7.6. Контр-игра

- дешёвая saturation и distributed decoys;
- повреждение datalink/sensor chain;
- продолжительная кампания вместо короткого calibrated engagement;
- blockade precision components и specialised repair;
- принуждение к боям в нескольких местах одновременно.

## 7.7. Корабельное визуальное направление

Силуэт:

- компактная directional масса с аккуратными faceted transitions;
- встроенные sensor apertures и recessed weapon/thermal systems;
- меньшая, плотная protected core без имперской тяжёлой цитадели;
- точная повторяемая геометрия, но не индустриальные серийные блоки Союза;
- тонкие functional sensor shoulders допустимы, хрупкие fantasy fins — нет;
- radiator/thermal geometry compact, segmented и технологически demanding.

Палитра-authoring anchors:

| Назначение | Цвет | HEX |
|---|---|---|
| carbon structure | Carbon Black | `#20262A` |
| cool metal | Precision Titanium | `#737F84` |
| ceramic cover | Pale Ceramic Grey | `#C5C9C5` |
| deep technical accent | Directorate Indigo | `#4A526D` |
| sensor/instrument | Cold Verdigris | `#57908D` |
| warning | Muted Vermilion | `#9B5044` |

Износ: малозаметный, локальный и быстро исправляемый; допустимы replaced precision panels и service
seals, но не sterile showroom. Запрещены glossy white utopia, cyberpunk neon, floating hologram noise,
alien biomorphism и «магически бесшовный» корпус без access.

## 7.8. Character overlay direction

- высококачественная practical technical clothing;
- статус через access, certification, instrument precision и controlled silhouette;
- carbon/cool-grey/pale ceramic base, indigo/verdigris accents;
- compact diagnostics, sensor/EW or automation interfaces как реальные props;
- лица живые, усталые и индивидуальные; не безупречные technocratic mannequins;
- officers/research directors не носят glossy futuristic couture.

Стиль изображения — только общий hand-painted ink-and-gouache Character Master Prompt.

---

# 8. Лига Свободных Систем

> **Production status:** POST-CORE DIRECTION LOCK.

## 8.1. Gameplay formula

```text
private initiative
+ distributed ownership
+ market access
+ route diversity
→ высокая адаптивность и торговая плотность
→ risk flight и трудная collective mobilization
```

## 8.2. Институты и экономика

Лига задаёт law/access/guarantee framework, но значительная freight/investment capacity принадлежит
локальным и частным акторам. Решения возникают из:

- expected profit;
- route risk и insurance;
- access/tariffs;
- capital availability;
- subsidy/guarantee через реальный treasury;
- local political consent;
- competition between routes and suppliers.

## 8.3. Fleet doctrine

- множество patrol/escort contracts и local defense forces;
- licensed/common civilian frames с разнообразными fits;
- convoy security, privateers/mercenaries только через lawful contracts;
- высокая route flexibility;
- слабее единая тяжёлая line fleet до успешной коалиционной мобилизации;
- emphasis на protection of commerce, hubs и transit.

## 8.4. Сильные стороны

- быстрое перенаправление частного freight к выгодным/безопасным routes;
- supplier diversity и много точек торговли;
- адаптивный import/export mix;
- экономическая дипломатия и market access;
- возможность мобилизовать большой distributed capital при доверии.

## 8.5. Ограничения

- freight может уйти из опасного региона;
- разные системы спорят о burden sharing;
- гарантия требует реального бюджета и доверия;
- военная standardization ниже core-industrial powers;
- crisis response запаздывает без заранее согласованных contracts.

## 8.6. Контр-игра

- повысить perceived route risk;
- разделить локальные интересы;
- атаковать insurer/guarantee capital и доверие, не создавая деньги/паники скриптом;
- перекрыть ключевой market access;
- вынудить флот к cohesion-intensive line battle.

## 8.7. Корабельное визуальное направление

Силуэт:

- узнаваемые licensed frame standards, но несколько manufacturer lineages;
- открыто читаемые cargo/mission pods и docking interfaces;
- более широкие, адаптивные civilian-derived masses;
- functional truss допустим там, где защищённость не обязательна;
- индивидуальные owner/service modifications без хаотического kitbash;
- military ships выглядят как профессиональные derivatives, не пираты.

Palette anchors:

| Назначение | Цвет | HEX |
|---|---|---|
| deep hull | Free-System Marine | `#273841` |
| structural | Port Steel | `#647176` |
| light panels | Sailcloth Grey | `#C7C1AF` |
| civic accent | Oxidized Teal | `#3F7774` |
| merchant accent | Burnt Copper | `#9A6643` |
| safety | Dock Amber | `#C28B36` |

Вариативность выше, чем у Союза: local paint, operator marks и manufacturer geometry допустимы в
рамках общих interfaces. Запрещены colourful toy ships, racing stripes everywhere, yacht luxury,
generic pirate kitbash и визуальная идентичность только через логотип.

## 8.8. Character overlay direction

- practical civilian spacewear вместо единой государственной формы;
- profession, operator и local-system affiliation читаются отдельными layers;
- layered jackets, pressure-compatible base, contract/access badges;
- качество одежды различается по достатку, но не превращается в fashion catalogue;
- restrained teal/copper/marine accents, личная настройка и lived-in detail;
- военные/охранные роли имеют совместимое standard gear, но сохраняют local identity.

---

# 9. Пограничная Конфедерация

> **Production status:** POST-CORE DIRECTION LOCK.

## 9.1. Gameplay formula

```text
scarcity
+ salvage
+ refit
+ substitute inputs
+ distributed workshops
→ устойчивость без идеальной supply chain
→ heterogeneous maintenance и ниже peak efficiency
```

## 9.2. Институты и экономика

Конфедерация координирует автономные удалённые системы через mutual aid, access и shared defense,
но production опирается на малые workshops, salvage, foreign hulls и допустимые substitutes.

Substitution всегда имеет цену: больше mass/work, ниже life/performance, больше maintenance или хуже
commonality. Salvage всегда bounded и происходит из физически потерянных assets.

## 9.3. Fleet doctrine

- mixed-generation hulls;
- distributed patrol/raider/defense groups;
- refit старых и чужих platforms;
- ambush, local knowledge и asymmetric logistics;
- repair/salvage tenders;
- нежелание принимать длительную high-consumption line war.

## 9.4. Сильные стороны

- выдерживает отсутствие preferred component;
- использует low-capability facilities и distributed repair;
- быстро меняет mission fit доступными modules;
- возвращает bounded salvage в производство;
- меньше зависит от одного capital yard.

## 9.5. Ограничения

- heterogeneous spare/training burden;
- выше maintenance work на единицу capability;
- ниже peak performance и reliability при substitutes;
- труднее крупная стандартизированная операция;
- истощение wreck/salvage pool и workshops снижает resilience.

## 9.6. Контр-игра

- deny salvage/recovery area;
- поддерживать длительный operational pressure;
- атаковать distributed depots/workshops по actor-bounded intel;
- использовать threats, требующие precision/common response;
- лишить доступа к foreign/common markets.

## 9.7. Корабельное визуальное направление

- прочный базовый spine старых/разных поколений;
- видимые adapter plates и функциональные refit seams;
- асимметрия только у mission equipment, cranes, sensors или repairs;
- разные поколения engine/sensor housings могут сосуществовать;
- protective patching и replacement panels, но корпус остаётся обслуженным;
- silhouette readable, не pile of junk.

Palette anchors:

| Назначение | Цвет | HEX |
|---|---|---|
| base hull | Frontier Charcoal | `#353A3A` |
| faded light | Weathered Bone | `#BEB7A2` |
| local blue | Dusty Range Blue | `#526D78` |
| utility green | Workshop Sage | `#68705E` |
| patch/primer | Oxide Primer | `#8D5040` |
| service | Sun-Faded Ochre | `#B08445` |

Запрещены pervasive rust, hanging wires, pirate spikes, random armour plates, Mad Max и «бедность как
единственная культура». Отличие от Союза — heterogeneous history, а не плохое качество.

## 9.8. Character overlay direction

- layered repaired workwear из нескольких compatible equipment generations;
- local/confederate patches и role markings;
- practical survival/repair gear без перегруза props;
- visible hand repairs, faded fabric и replacement fasteners;
- community discipline и профессиональная уверенность;
- никакой caricature hillbilly/scavenger/pirate aesthetic.

---

# 10. Консорциум

> **Production status:** POST-CORE DIRECTION LOCK.

## 10.1. Gameplay formula

```text
capital
+ ownership
+ concessions
+ debt
+ infrastructure control
→ влияние без немедленной аннексии
→ leverage, legitimacy и contract-network risk
```

## 10.2. Институты и экономика

Консорциум требует строгого разделения:

```text
sovereignty ≠ asset ownership ≠ concession ≠ economic control ≠ access
```

Он инвестирует в mines, terminals, yards, freight и debt instruments, получает contract revenue и
может оказывать pressure через lawful rights. Ни один default не перекрашивает систему автоматически.

## 10.3. Fleet doctrine

- security/escort forces вокруг assets и corridors;
- contract fleets и hired capacity через реальные payments;
- efficient logistics/control ships;
- deterrence, precision security и blockade leverage;
- меньше желания оплачивать full sovereign occupation;
- withdrawal/settlement при отрицательной asset economics.

## 10.4. Сильные стороны

- быстро концентрирует capital на прибыльном bottleneck;
- контролирует flows через ownership/concessions;
- создаёт cross-border networks;
- использует diplomacy/finance раньше annexation;
- может нанимать/субсидировать physical capacity.

## 10.5. Ограничения

- debt/default exposure;
- contract breach и legitimacy risk;
- asset concentration;
- меньшая надёжность hired forces при funding/contract failure;
- political coalition может ограничить concessions/access;
- чужой sovereign control остаётся реальной границей.

## 10.6. Контр-игра

- конкурировать за contracts и refinance debt;
- создать regulatory/sovereign coalition;
- диверсифицировать suppliers/terminals;
- законно прекратить concession с компенсацией/последствиями либо принять breach cost;
- атаковать leverage chain, а не выдуманный influence bar.

## 10.7. Корабельное визуальное направление

- clean rectilinear volumes и protected commercial cores;
- интегрированные cargo/service interfaces;
- controlled surface hierarchy и easily auditable asset sections;
- premium, но не luxurious manufacturing;
- security ships компактны и профессиональны, без Directorate sensor-first silhouette;
- ownership/department bands и technical marks локальны, не превращают hull в billboard.

Palette anchors:

| Назначение | Цвет | HEX |
|---|---|---|
| primary | Ledger Charcoal | `#252C2D` |
| structural | Contract Steel | `#677174` |
| premium light | Warm Pearl Grey | `#C9C5B8` |
| corporate deep | Equity Green | `#315E54` |
| data/service | Audit Teal | `#4B8481` |
| ownership accent | Restrained Copper | `#9A714C` |

Запрещены cyberpunk megacorp neon, black-and-gold luxury cliché, supercars in space, omnipresent logo,
menacing spikes и sterile Directorate recolor.

## 10.8. Character overlay direction

- tailored technical/business layers, пригодные для station/ship work;
- status через material quality, access credential и restrained precision;
- role separation: asset manager, engineer, security, negotiator, freight operator;
- charcoal/pearl/deep-green/teal with limited copper;
- лица не рекламно идеальны; усталость, ambition и осторожность читаются;
- practical tablet/credential/inspection tool максимум как один prop.

---

# 11. Кочевой Флот

> **Production status:** POST-CORE DIRECTION LOCK.

## 11.1. Gameplay formula

```text
fleet = habitat + industry + storage + governance + security + mobility
→ economic node может перемещаться
→ потеря/изоляция core carrier угрожает всей социальной системе
```

## 11.2. Институты и экономика

Флот живёт через transit, anchorage, refuel, extraction и service rights. Mobile facilities используют
обычные inputs/power/work/time/storage и сохраняют identity/queue/inventory при movement.

Сила не в «игнорировании территории», а в способности переносить часть экономики. Цена — огромная
ценность habitat/industrial carriers и зависимость от маршрута/доступа.

## 11.3. Fleet doctrine

- convoy civilisation с layered escort;
- разведка безопасного маршрута и diplomacy access;
- mobile depots, repair, refining/assembly where supported;
- defense-in-depth вокруг habitation/industry;
- избегание неподвижного attritional siege;
- dispersed travel с carefully controlled regrouping.

## 11.4. Сильные стороны

- может уйти от deteriorating geography;
- часть production/storage не привязана к одной системе;
- высокая route knowledge и expedition endurance;
- non-territorial diplomacy и flexible concentration;
- loss of static control не равна потере всей экономики.

## 11.5. Ограничения

- habitat/industry ships медленны, заметны и незаменимы;
- нуждаются в reaction mass, maintenance и safe service windows;
- denial transit/anchorage создаёт existential pressure;
- dispersed elements могут быть разделены;
- mobile production ограничена installed capability/cargo/power;
- catastrophe несёт одновременно military, economic и social loss.

## 11.6. Контр-игра

- дипломатически закрыть или контролировать transit/anchorage;
- вынудить core convoy изменить маршрут и расходовать stores;
- отделить escorts/foragers от slow core;
- установить наблюдение через ordinary sensors/intelligence;
- предлагать доступ/торговлю, создавая зависимость и мирный leverage;
- атака habitats должна иметь соответствующие political/reputation consequences, не запрет-скрипт.

## 11.7. Корабельное визуальное направление

- protected axial thrust/utility spine;
- clusters of habitat, storage, processing и docking modules;
- крупные coupling collars и tug/service interfaces;
- radiators/rotating habitat только при physical justification;
- coherent modular accretion: корабль рос, но не является random kitbash;
- escorts визуально родственны utility/coupling language core fleet;
- broad/long convoy silhouettes distinct from all territorial navies.

Palette anchors:

| Назначение | Цвет | HEX |
|---|---|---|
| deep structure | Void Soot | `#22292A` |
| habitation light | Warm Hull Bone | `#C2B9A5` |
| lineage accent | Caravan Turquoise | `#477A78` |
| civic textile/mark | Clay Red | `#985348` |
| utility | Nomad Slate | `#596466` |
| docking/service | Hearth Amber | `#BD8538` |

Износ показывает долгую жизнь и регулярный communal maintenance. Запрещены «космические цыгане»,
pirate caravan, mystical sails, fantasy fins, случайные hanging modules и бедность как экзотика.

## 11.8. Character overlay direction

- adaptable layered shipboard clothing и pressure-compatible base;
- vessel/family/role lineage marks без этнического стереотипа;
- practical repair, navigation и habitat gear;
- личная вещь допустима только как restrained lived-in detail;
- warm bone/slate/turquoise/clay accents;
- hierarchy через responsibility for ship systems/community, не ornate tribal costume;
- всё остаётся в общем hand-painted grounded RPG style.

---

# 12. Grayscale silhouette matrix

| Фракция | Главная масса | Центральный ритм | Корма | Внешние системы | Узнаваемость без цвета |
|---|---|---|---|---|---|
| Империя | длинная осевая | цельная тяжёлая цитадель | массивная serviceable | recessed/protected | бронированный нос + цитадель |
| Индустриальный Союз | плотная sectional | повторяемые modules | common engine bank | standardized cassettes | серийный modular rhythm |
| Директорат | компактная faceted | integrated precision core | tight high-performance | embedded apertures | точная faceted sensor geometry |
| Лига | широкая адаптивная | licensed frame + mission pods | varied compatible drives | cargo/docking visible | operator/manufacturer modularity |
| Конфедерация | прочная heterogeneous | refit seams и adapters | mixed generations | functional asymmetry | обслуженный multi-generation refit |
| Консорциум | clean rectilinear | protected asset volume | controlled integrated | audited service zones | premium commercial/security massing |
| Кочевой Флот | spine + clusters | habitat/industry accretion | tugged/common thrust spine | coupling/docking dominant | mobile settlement/convoy architecture |

Новый hull отклоняется, если его можно без изменения silhouette перенести другой фракции и решить
разницу только цветом.

---

# 13. Character differentiation matrix

Общий art medium одинаков для всех. Различается социальная и материальная культура.

| Фракция | Как читается статус | Базовая одежда | Износ | Запрещённый shortcut |
|---|---|---|---|---|
| Империя | rank, fit, material, restraint | hierarchical naval/technical | regulated long service | literal historical costume |
| Союз | qualification/function | standardized industrial/technical | organized high-use | Soviet/post-apocalypse worker |
| Директорат | access/certification/precision | advanced technical | minimal/local service replacement | glossy sterile technocrat |
| Лига | profession/operator/local affiliation | layered civilian pressurewear | personal, maintained | fashion crew/pirate recolor |
| Конфедерация | responsibility/skill/community | repaired mixed-generation workwear | visible skilled repair | scavenger caricature |
| Консорциум | access/asset responsibility/material quality | tailored technical-commercial | controlled professional use | neon black-gold megacorp |
| Кочевой Флот | system/community responsibility | adaptable shipboard layers | communal long-life maintenance | exotic tribal stereotype |

Для любого персонажа обязательна сборка:

```text
Character Master Prompt
+ approved faction overlay
+ role/profession requirements
+ individual appearance/personality/background
→ one canonical character prompt
```

Faction overlay никогда не отменяет:

- irregular hand-drawn linework;
- muted opaque watercolor/gouache;
- visible brush decisions;
- limited shading;
- non-idealized face;
- transparent background;
- no photorealism / 3D / glossy modern AI concept art.

---

# 14. Pairwise strategic ecology

Эта таблица задаёт потенциальные cooperative/conflict drivers, но не scripted отношения.

| Пара | Естественная кооперация | Естественное трение | Проверяемая контр-игра |
|---|---|---|---|
| Империя ↔ Союз | long-term supply + state security | reserve control vs throughput demand | hub disruption против repair depth |
| Империя ↔ Директорат | mature heavy base + advanced systems | autonomy/IP/security | saturation vs precision, attrition vs repair |
| Империя ↔ Лига | protected routes + commerce | tariffs, formal control, local autonomy | market rerouting vs border security |
| Империя ↔ Конфедерация | frontier protection + repair markets | claims, central authority | overextension vs distributed resistance |
| Империя ↔ Консорциум | capital for infrastructure | sovereignty vs concession ownership | legal coalition vs fiscal leverage |
| Империя ↔ Кочевой Флот | escorted transit/service | border/anchorage/control | fixed strongpoints vs relocation |
| Союз ↔ Директорат | bulk base + precision components | scale vs complexity/IP | mass throughput vs specialised bottleneck |
| Союз ↔ Лига | framework contracts + route market | long series vs spot flexibility | corridor concentration vs route diversity |
| Союз ↔ Конфедерация | common goods + salvage inputs | standards vs substitutes | throughput vs low-infrastructure resilience |
| Союз ↔ Консорциум | industrial finance | ownership of hubs/queues | volume leverage vs debt leverage |
| Союз ↔ Кочевой Флот | supplies/repair in exchange for access | stationary corridors vs mobility | hub power vs mobile diversification |
| Директорат ↔ Лига | advanced exports + distributed capital | IP, export controls, risk pricing | rare supply vs market rerouting |
| Директорат ↔ Конфедерация | repair knowledge + recovered tech | IP/salvage legitimacy | precision superiority vs substitution |
| Директорат ↔ Консорциум | capital-intensive technology | ownership/IP/control | finance exposure vs specialised dependency |
| Директорат ↔ Кочевой Флот | navigation/sensor services | tracking, access, rare inputs | information quality vs mobility |
| Лига ↔ Конфедерация | border trade, repair, risk contracts | smuggling/access standards | private flow vs local resilience |
| Лига ↔ Консорциум | investment and market expansion | distributed ownership vs concentration | competition/regulation vs leverage |
| Лига ↔ Кочевой Флот | mobile markets/transit contracts | insurance/access volatility | route choice vs guarantee capacity |
| Конфедерация ↔ Консорциум | capital for frontier assets | debt/concession vs autonomy | substitution/default coalition vs ownership |
| Конфедерация ↔ Кочевой Флот | salvage, repair, distributed trade | scarce anchorage/resources | local workshops vs mobile industry |
| Консорциум ↔ Кочевой Флот | leasing, supply and service contracts | ownership claims over mobile assets | mobility/default vs contract leverage |

War remains valid only after actor-bounded interests, failed/insufficient alternatives, crisis and
legal warfare lifecycle. Таблица не задаёт initial relations roll.

---

# 15. Cross-faction balance constraints

## 15.1. Conditional advantage

Каждая фракция должна иметь условия, где её подготовленная модель сильна, и условия, где её цена
становится ограничением. Нельзя балансировать так, чтобы все были одинаковы в каждом сценарии.

## 15.2. No global Pareto winner

Ни одна фракция не может одновременно быть лучшей по:

- acquisition/construction cost;
- tactical objective performance;
- strategic mobility;
- information quality;
- ammunition/endurance;
- repair/replacement latency;
- logistics resilience;
- economic growth;
- diplomatic flexibility;
- post-war recovery.

## 15.3. Strength-cost proof

Для каждого signature strength acceptance corpus должен показать:

1. состояние, создающее advantage;
2. ordinary authority, через которую оно создано;
3. consumed/committed resources;
4. failure/counter condition;
5. save/load continuity;
6. player availability того же lawful path.

## 15.4. No forced 50/50

Asymmetric balance не требует искусственно равного win rate в каждом seed. Требуется отсутствие
универсального решения, объяснимая outcome diversity и жизнеспособные counters. Точные diagnostic
thresholds определяются `faction_balance_validation_framework.md` и калибруются на accepted corpus,
а не вписываются как faction multiplier.

---

# 16. Visual production acceptance

Каждый новый faction asset проходит четыре независимых gate.

## Engineering gate

- каждый крупный элемент имеет роль;
- видимые modules соответствуют fit count/location;
- propulsion/thermal/cargo/service geometry правдоподобна;
- base sprite не содержит baked exhaust, projectile или UI;
- damage/emissive/anchor layers согласованы.

## Faction gate

- silhouette узнаётся без цвета/герба;
- объект не является recolor другой фракции;
- material/wear/marking culture соответствует обществу;
- prohibited shortcuts отсутствуют.

## Gameplay gate

- hull category и role читаются на actual gameplay zoom;
- interactive nodes достаточно крупны для overlay/animation/damage;
- side/selection cues не зависят только от faction palette;
- насыщенная сцена остаётся различимой.

## Production gate

- stable content/asset ID и manifest;
- provenance/license;
- pivot/orientation/physical scale;
- deterministic anchor metadata;
- localization/icon binding;
- semantic fingerprint и save alias policy.

---

# 17. Не фиксировать до engineering/balance evidence

Этот документ намеренно не задаёт:

- проценты production/damage/armor/sensor;
- гарантированный fleet-size ratio;
- точные массы/slot counts будущих faction hulls;
- exact win rates;
- immutable political nomenclature post-core фракций;
- финальные stable IDs post-core actors;
- все manufacturer lineages;
- окончательные цвета shader output вместо authoring anchors.

Эти решения принимаются при реализации соответствующего package и обязаны сохранять указанные
tradeoff/counterplay/visual contracts.

---

# 18. Канонический итог

Семь фракций образуют не семь наборов бонусов, а семь способов организовать одну simulation:

```text
Империя            — сохранить и удержать подготовленную систему
Индустриальный Союз — воспроизводить масштаб через стандарты и потоки
Директорат          — концентрировать precision и информацию
Лига                — координировать автономный рынок incentives
Конфедерация        — выживать через ремонт, substitution и распределённость
Консорциум          — контролировать assets/flows через capital и contracts
Кочевой Флот        — переносить общество и экономику вместе с fleet
```

Их корабли, персонажи, UI, NPC, diplomacy и AI обязаны рассказывать ту же причинную историю, которую
создают экономика, производство, логистика, война и persistence. Если визуал обещает одну модель, а
simulation выполняет другую, faction package не считается production-complete.
