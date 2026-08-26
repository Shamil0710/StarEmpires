# Star Empires — post-core faction horizon

> Статус: **CANONICAL DESIGN HORIZON / NOT A STAGE-23 EXIT REQUIREMENT**  
> Scope: пять крупных фракций, намеренно отложенных до завершения основного этапа разработки.  
> Цель документа — сохранить их идентичность и заранее зафиксировать архитектурные требования, не реализуя prematurely новые fundamental systems.

---

## 1. Общая граница

После core pair — Империи и Индустриального Союза — долгосрочный roster расширяется пятью крупными фракциями:

1. **Директорат**;
2. **Лига Свободных Систем**;
3. **Пограничная Конфедерация**;
4. **Консорциум**;
5. **Кочевой Флот**.

Эти фракции являются каноном будущего мира, но до Stage 23:

- не требуют production-complete hull roster;
- не требуют complete visual bible;
- не требуют recurring NPC quota;
- не требуют release campaign presence;
- не являются balance gate;
- не должны провоцировать premature parallel authorities.

Core architecture, однако, не должна hardcode assumptions, делающие их невозможными.

---

# 2. Директорат

## 2.1 Core identity

**Директорат** — высокотехнологичная держава, которая делает ставку на precision engineering, automation, сложные производственные цепочки, сенсоры/EW и более высокую performance density, принимая взамен дороговизну, сложность обслуживания и критические bottlenecks.

Формула:

```text
precision
+ automation
+ high-performance systems
+ information quality
→ strong capability density
→ fragile specialized dependencies
```

## 2.2 Что должно отличать его механически

Не `+research` и не `+sensor range`.

Директорат должен использовать более сложные реальные inputs:

```text
advanced component
→ precision material/component inputs
→ specialized facility capability
→ tighter manufacturing tolerance
→ higher maintenance complexity
→ lower supplier substitution
```

Возможные сильные стороны:

- высокий specific power;
- компактные advanced modules;
- сильные sensor/EW chains;
- развитая automation;
- меньшая crew demand на часть функций;
- качественная actor-bounded intelligence.

Возможные слабости:

- precision-component bottleneck;
- expensive maintenance;
- specialized yards;
- higher power/thermal complexity;
- supply-chain fragility;
- expensive replacement.

## 2.3 Required future mechanics

Перед production implementation нужен audit следующих common systems:

### Manufacturing capability depth

Facility должна уметь отличаться не только work rate, но и capability/tolerance domain:

- precision manufacturing;
- optics/electronics;
- high-temperature/exotic handling;
- automation integration;
- advanced reactor/drive capability.

### Automation

Automation должна менять реальные budgets:

- crew/labour demand;
- electronics/components;
- power;
- maintenance complexity;
- failure/recovery behavior.

### Industrial dependency graph

AI должен видеть actor-allowed dependency evidence вида:

```text
fleet capability
→ module
→ component
→ facility
→ material source / route
```

### Information quality

Если Директорат получает информационное преимущество, оно должно идти через sensors/tracks/datalink/EW/intelligence freshness, а не omniscient world state.

## 2.4 Architecture risk

**MEDIUM.** Большая часть physical foundation уже существует, но content breadth должен углубить Stage-18/22 precision and capability model без создания отдельной technology game.

---

# 3. Лига Свободных Систем

## 3.1 Core identity

**Лига Свободных Систем** — децентрализованная торгово-политическая среда, где значительная часть freight, investment и economic activity выполняется частными или локально автономными акторами, а центральная власть чаще создаёт правила, гарантии и incentives, чем напрямую командует всей экономикой.

Формула:

```text
market access
+ private initiative
+ distributed ownership
+ trade networks
→ high adaptability and commerce
→ risk-sensitive and politically fragmented response
```

## 3.2 Mechanical identity

Лига не должна получать `trade income bonus`.

Её отличие возникает, если transport/investment decisions действительно зависят от:

- expected profit;
- route risk;
- access;
- insurance/subsidy;
- capital availability;
- private asset ownership;
- local political rules.

Пример:

```text
profitable route + low risk
→ private freight enters

war raises risk
→ freight withdraws / prices rise

state/association provides subsidy, guarantee, escort
→ route becomes viable again
```

## 3.3 Required future mechanics

### Aggregated private economic sector

Не требуется симулировать каждую фирму как NPC. Допустим агрегированный actor layer, если он сохраняет physical authority:

- capital;
- freight capacity;
- investment preference;
- risk tolerance;
- lawful ownership;
- real transaction settlement.

### Route risk premium

Risk должен влиять на реальные decisions/cost, а не remote production debuff.

### Subsidies/guarantees

Государство может компенсировать часть риска только через реальный treasury transfer/commitment.

### Credit/investment seam

Для полной версии desirable:

- lending;
- debt service;
- investment financing;
- default risk.

Но credit не должен вводиться ради одной фракции как isolated minigame.

## 3.4 Architecture risk

**HIGH relative to core pair.** Current faction economy mostly assumes strong faction-level coordination. Лига требует reusable private-sector actor model.

---

# 4. Пограничная Конфедерация

## 4.1 Core identity

**Пограничная Конфедерация** — сеть удалённых и неоднородных систем, привыкших жить при дефиците инфраструктуры, использовать salvage, ремонтировать старую технику, адаптировать чужие корпуса и заменять идеальные inputs доступными.

Формула:

```text
scarcity
+ improvisation
+ salvage
+ broad refit tolerance
→ resilience without perfect supply
→ lower efficiency / higher maintenance heterogeneity
```

## 4.2 Mechanical identity

Не `-repair cost` и не `+salvage yield` by faction name.

Конфедерация должна чаще использовать common mechanics:

- substitute inputs;
- salvage;
- mixed-generation components;
- refit;
- captured/foreign hull support;
- low-capability facilities;
- distributed small workshops.

## 4.3 Input substitution

Будущий recipe language должен при необходимости уметь выразить:

```text
preferred input
vs
allowed substitute
```

Substitution может иметь реальную цену:

- больше mass;
- больше work;
- меньше life/reliability;
- ниже performance;
- больше maintenance;
- worse component commonality.

## 4.4 Salvage/refit

Stage 18/17.5 уже создают значительную часть foundation.

Future package должен проверить:

- bounded physical recovery;
- traceable recovered components/materials;
- refit compatibility;
- foreign module integration;
- repair capability at small facilities;
- no duplication of destroyed assets.

## 4.5 Architecture risk

**MEDIUM/LOW relative to other horizon factions.** Больше всего reusable seams уже существуют; основная работа — расширение recipe/refit semantics и content validation.

---

# 5. Консорциум

## 5.1 Core identity

**Консорциум** — корпоративно-финансовая держава/система влияния, способная контролировать экономические активы, инфраструктуру и потоки даже там, где формальный суверенитет принадлежит другому государству.

Формула:

```text
capital
+ ownership
+ concessions
+ debt
+ infrastructure control
→ power without immediate annexation
```

## 5.2 Главный архитектурный вызов

Для Консорциума необходимо строго отделить:

```text
sovereignty
≠ ownership
≠ economic control
≠ access rights
```

Пример допустимого будущего состояния:

```text
System sovereign: League
Mine owner: Consortium company
Freight terminal owner: Consortium company
Military station owner: League
Shipyard concession holder: Consortium
```

Текущая архитектура не должна сводить всё к одному `system.owner`.

## 5.3 Required future mechanics

### Asset ownership model

Ownership должен быть persistent per physical/economic asset.

### Concession contracts

Нужны reusable rights:

- extraction right;
- port/terminal operation;
- construction/yard right;
- revenue share;
- duration;
- host sovereign;
- termination/breach conditions.

### Debt

Future generic contract может включать:

- principal;
- creditor;
- debtor;
- interest/service terms;
- maturity;
- collateral;
- default outcomes.

Default не должен автоматически recolor territory.

### Economic influence

Если UI показывает influence, лучше deriving it from:

- owned production share;
- throughput;
- infrastructure ownership;
- debt exposure;
- employment/contract exposure where model supports it.

Это presentation/analysis metric, а не новая sovereign authority.

## 5.4 Architecture risk

**VERY HIGH.** Консорциум требует cross-cutting ownership/legal/finance changes, которые должны быть общими для игрока и всех factions.

---

# 6. Кочевой Флот

## 6.1 Core identity

**Кочевой Флот** — мобильная цивилизация, в которой корабли и fleet structures являются не только transport/combat assets, но и населёнными, производственными, складскими и институциональными узлами.

Формула:

```text
fleet
= habitat
+ industry
+ storage
+ governance
+ security
+ mobility
```

Это самая фундаментальная horizon faction.

## 6.2 Главный architectural requirement

Economy должна в будущем уметь рассматривать узел как не обязательно неподвижный объект.

Долгосрочная abstraction:

```text
EconomicNode
├─ static planet/station
└─ mobile ship/fleet platform
```

Корабль с производством должен сохранять:

- facility identity;
- inventory;
- work queue;
- population/crew context where model exists;
- energy/capability;
- ownership;
- location;

при обычном physical movement.

## 6.3 Territory and rights

Кочевой Флот должен меньше зависеть от normal territorial control и больше от:

- transit rights;
- anchorage rights;
- refuel/service access;
- extraction rights;
- trade access;
- migration corridors;
- temporary assembly zones.

Это должно расширять existing access/treaty law, а не создавать nomad-only diplomacy.

## 6.4 Mobile industry

Future factory/refinery/yard ships должны выполнять ordinary Stage-18 work using:

- cargo inventory;
- installed capabilities;
- power/work/time;
- finite inputs;
- physical output storage.

Movement не должен reset production or inventory.

## 6.5 Population

Если population becomes authoritative later, mobile population must not be forcibly planet-bound.

Потеря habitation/industrial ship может тогда иметь реальные demographic/economic consequences.

До появления общего population authority нельзя создавать nomad-only population counter.

## 6.6 Architecture risk

**EXTREME relative to current core.** Требует generalization of static infrastructure assumptions и careful persistence/movement/materialization integration.

---

# 7. Cross-horizon reusable systems

Пять фракций требуют не пять отдельных механик, а набор reusable extensions.

| Common extension | Директорат | Лига | Конфедерация | Консорциум | Кочевой Флот |
|---|:---:|:---:|:---:|:---:|:---:|
| precision manufacturing depth | ★ |  |  |  |  |
| automation/labour depth | ★ |  |  |  | ★ |
| private economic actors |  | ★ |  | ★ |  |
| route risk/insurance/subsidy |  | ★ |  | ★ |  |
| credit/debt |  | ★ |  | ★ |  |
| ownership ≠ sovereignty |  |  |  | ★ | ★ |
| concessions/access rights |  | ★ |  | ★ | ★ |
| input substitution |  |  | ★ |  | ★ |
| expanded salvage/refit |  |  | ★ |  | ★ |
| mobile economic nodes |  |  |  |  | ★ |
| non-territorial strategic posture |  | ★ | ★ | ★ | ★ |

---

# 8. Future implementation policy

Каждая post-core faction проходит отдельный `Faction Architecture Review` до code work.

Review обязан ответить:

1. Какие existing authorities уже достаточны?
2. Какие requirements можно выразить content/configuration?
3. Какое minimum common-system extension действительно необходимо?
4. Может ли player-created faction использовать этот же extension?
5. Как состояние сохраняется?
6. Как оно влияет на existing saves/migration?
7. Есть ли hidden resource/ownership/knowledge shortcut?
8. Как feature ведёт себя в headless long-run simulation?
9. Какие acceptance fixtures доказывают advantage **и** cost?
10. Может ли extension существовать без конкретной faction hardcode?

Если ответ на пункт 10 — «нет», требуется отдельное архитектурное обоснование.

---

# 9. Production package requirement after core release

Когда horizon faction становится active development target, она получает полный пакет:

- systemic identity revision;
- lore/political bible;
- engineering/industrial doctrine;
- stable-ID/migration plan;
- visual bible;
- hull/fleet/station roster;
- NPC/mission/event vocabulary;
- player access/progression path;
- peaceful + crisis + war + loss + recovery history;
- pairwise acceptance against already-shipped factions;
- macro economy/logistics soak;
- persistence/migration tests;
- asset/localization/audio manifests.

Ни одна новая faction не должна добавляться только визуальным skin pack.

---

# 10. Канонический post-core statement

Директорат, Лига Свободных Систем, Пограничная Конфедерация, Консорциум и Кочевой Флот **не удалены из дизайна**.

Они зафиксированы как последовательный долгосрочный горизонт, который расширяет одну общую simulation:

- Директорат проверяет complexity/precision;
- Лига — private market agency;
- Конфедерация — scarcity resilience and substitution;
- Консорциум — ownership and finance separate from sovereignty;
- Кочевой Флот — mobile civilization/economic nodes.

Core release intentionally proves the architecture first on Империи и Индустриальном Союзе, а затем расширяет её без scope explosion основного этапа разработки.
