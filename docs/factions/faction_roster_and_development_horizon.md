# Star Empires — канонический roster фракций и горизонт развития

> Статус: **CANONICAL FACTION DESIGN CONTRACT**  
> Дата решения: **2026-08-26**  
> Scope: политическая/экономическая/военная идентичность крупных фракций, граница основного этапа разработки и post-core horizon.  
> Этот документ определяет **что является каноном фракционного дизайна**, но не создаёт отдельную economy/diplomacy/warfare authority.

---

## 1. Зафиксированное решение

Канонический долгосрочный roster Star Empires состоит из **семи концептуально различных крупных фракций**:

1. **Империя**;
2. **Индустриальный Союз**;
3. **Директорат**;
4. **Лига Свободных Систем**;
5. **Пограничная Конфедерация**;
6. **Консорциум**;
7. **Кочевой Флот**.

Однако основной этап создания игры до завершения Stage 23 обязан полностью реализовать только две первые reference factions:

- **Империя** — primary gold-slice faction;
- **Индустриальный Союз** — mandatory contrast faction.

Оставшиеся пять фракций являются **POST-CORE DEVELOPMENT HORIZON**. Их концепты и будущие системные требования закреплены сейчас, чтобы текущая архитектура не закрыла путь к ним, но их production-complete реализация **не является exit criterion Stage 21, Stage 22 или Stage 23**.

Это решение заменяет прежние content-floor формулировки, требовавшие пять production-complete major factions до alpha/RC.

---

## 2. Главный фракционный инвариант

Фракции не являются наборами магических бонусов.

```text
one physical/economic world
→ common authoritative systems
→ faction institutions / doctrine / policy / geography / industrial structure
→ different lawful decisions
→ different physical/economic consequences
→ recognizable long-run behavior
```

Запрещено использовать как основу фракционной идентичности:

- `+X% production` без физической/институциональной причины;
- `+X% damage` по имени фракции;
- бесплатные ресурсы, ships, repair, ammunition или reaction mass;
- faction-only teleport / virtual freight;
- отдельную economy/warfare/diplomacy implementation для конкретной фракции;
- omniscient faction AI;
- scripted war/peace только ради flavour;
- автоматическое territorial recolor без Stage-17/21 physical/legal causality.

Фракционная асимметрия должна возникать из общих систем:

- институциональной структуры;
- policy/doctrine;
- procurement;
- industrial specialization;
- resource dependencies;
- technology/manufacturing capability;
- logistics;
- fleet composition;
- risk tolerance;
- diplomacy and legal culture;
- access/territory rules;
- actor-bounded information;
- persistence/history.

---

## 3. Статус семи фракций

| Фракция | Статус разработки | Роль в основном этапе | Основной mechanical proof |
|---|---|---|---|
| **Империя** | **CORE / REQUIRED** | Stage 21–23 gold slice | централизованное государство, мобилизация, резервы, тяжёлая ремонтопригодная инженерия |
| **Индустриальный Союз** | **CORE / REQUIRED** | Stage 21–23 contrast faction | массовая стандартизированная промышленность, специализация серий, ресурсный голод, fleet replacement throughput |
| **Директорат** | **POST-CORE HORIZON** | не блокирует Stage 23 | высокотехнологичная сложная промышленность, automation, precision bottlenecks, superior information quality |
| **Лига Свободных Систем** | **POST-CORE HORIZON** | не блокирует Stage 23 | частный сектор, private freight, market risk, subsidies/insurance/credit |
| **Пограничная Конфедерация** | **POST-CORE HORIZON** | не блокирует Stage 23 | salvage/refit/substitution, low-infrastructure resilience, heterogeneous fleets |
| **Консорциум** | **POST-CORE HORIZON** | не блокирует Stage 23 | ownership ≠ sovereignty, concessions, debt, economic control without annexation |
| **Кочевой Флот** | **POST-CORE HORIZON** | не блокирует Stage 23 | mobile economic nodes, moving industry/population, non-territorial access and migration routes |

---

## 4. Core pair: зачем именно Империя + Индустриальный Союз

Эта пара выбрана не только как две эстетически разные стороны, а как минимальный доказательный набор для общей simulation architecture.

### Империя проверяет

- централизованную policy authority;
- государственный procurement;
- strategic reserves;
- mobilization/demobilization;
- territorial/security priorities;
- длительные maintenance/refit cycles;
- тяжёлую supply footprint;
- способность общей экономики поддерживать не «бонус», а дорогую институциональную модель.

### Индустриальный Союз проверяет

- production specialization;
- repeated manufacturing series;
- standardization effects;
- large industrial throughput;
- resource forecast and strategic shortage;
- logistics throughput as military power;
- быстрый replacement за счёт подготовленной промышленной базы, а не free respawn;
- альтернативную fleet doctrine внутри той же физики.

### Вместе они обязаны доказать

```text
same world evidence
→ different institutional interpretation
→ different goals / procurement / fleet composition
→ different resource and logistics pressure
→ different war/recovery behavior
→ no faction stat cheat
```

Если эти две фракции нельзя сделать отчётливо различимыми без hidden modifiers, Stage-22 faction differentiation считается архитектурно незавершённой.

---

## 5. Runtime identity и migration rule

Существующие Stage-20 generated-world faction IDs являются **runtime compatibility identities**, а не автоматически финальными lore identities.

На момент фиксации решения в репозитории уже существуют, среди прочих, идентификаторы вроде:

- `faction.imperial_directorate`;
- `faction.industrial_combine`;
- `faction.frontier_union`;
- `faction.free_ports`;
- `faction.research_consortium`;
- legacy neutral/trade/miner actors.

Правило:

1. **не переименовывать stable ID только ради lore clean-up без migration**;
2. current display name не имеет приоритета над этим каноническим faction roster;
3. Stage 22.0 обязан создать explicit mapping/disposition:
   - `PROMOTE_AS_CORE_FACTION`;
   - `ALIAS_WITH_MIGRATION`;
   - `KEEP_AS_TEST_ORGANIZATION`;
   - `MAP_TO_MINOR_ORGANIZATION`;
   - `RESERVE_FOR_POST_CORE_HORIZON`;
   - `RETIRE_WITH_MIGRATION`;
4. production save compatibility имеет приоритет над косметическим переименованием;
5. нельзя молча превратить существующий runtime actor в другую политическую сущность только потому, что имя похоже.

Предварительный design intent:

- current Imperial runtime lineage должен стать базой для **Империи**;
- current industrial runtime lineage должен стать базой для **Индустриального Союза**;
- точное stable-ID/display-name решение принимается Stage 22.0 после reverse-reference/save audit.

Для пяти horizon factions stable IDs **пока не фиксируются**, если это потребует преждевременной migration или ложного обещания production implementation.

---

## 6. Общая институциональная модель

Долгосрочно фракционную идентичность следует представлять композиционно:

```text
Faction identity
  ↓
Institutions
  ↓
Policies
  ↓
Doctrine/preferences
  ↓
Actor-bounded observations
  ↓
Strategic goals
  ↓
Shared commands / authoritative systems
  ↓
Physical/economic/political consequences
```

### Identity

Долговременные характеристики политической системы и культуры. Меняются редко и не являются temporary buff.

### Institutions

Определяют, какие lawful decision paths доступны и кто уполномочен ими пользоваться.

Примеры:

- state procurement;
- distributed local councils;
- private freight market;
- concession authority;
- mobile fleet assembly;
- emergency requisition.

### Policies

Изменяемые решения текущего режима:

- reserve target;
- mobilization posture;
- import tolerance;
- convoy priority;
- production-series commitment;
- strategic stockpile;
- tariff/access posture.

### Doctrine/preferences

Влияют на выбор решений и design/procurement, но не переписывают физику.

---

## 7. Общие systemic axes, которые должны оставаться reusable

Даже когда Stage 23 закрывается только с двумя core factions, следующие systems должны проектироваться без hardcode под них:

### 7.1 Strategic shortage and dependency

Фракция должна уметь оценивать:

```text
stock
+ production
+ expected consumption
+ committed projects
+ imports
+ route risk
+ wartime demand
→ projected shortage / dependency
```

### 7.2 Production specialization

Должна существовать возможность выразить выгоду/цену повторяющейся серии через реальные manufacturing constraints, tooling/workflow, component diversity и maintenance footprint.

### 7.3 Institutional policy without resource creation

Policy меняет приоритеты, заказы, reserve floors, допустимый риск и allocation — но не создаёт goods/credits/output.

### 7.4 Logistics resilience

Устойчивость определяется запасами, route redundancy, supplier diversity, local production, transport availability и security, а не одним abstract resilience stat.

### 7.5 Technology/industrial dependency graph

Advanced production должна иметь traceable material/component/facility bottlenecks, пригодные для AI valuation, blockade, diplomacy и strategic targeting.

### 7.6 Anti-churn

Institutional/strategic decisions должны иметь:

- commitment horizon;
- cooldown;
- switching cost;
- hysteresis;
- material-change wakeup;
- saved deadlines/history.

### 7.7 Player/AI parity

Если игрок возглавляет или создаёт фракцию, он должен использовать те же policy, treasury, construction, logistics, diplomacy and warfare contracts.

---

## 8. Roadmap ownership

### Stage 21 — Living World

Stage 21 отвечает за **почему/когда/где** фракция действует:

- actor-bounded observations;
- measurable interests;
- strategic goals;
- diplomacy/crisis/war lifecycle;
- fleet operations;
- territorial consequences;
- recovery;
- NPC/mission/reputation consequences;
- explainability and persistence.

Для core pair Stage 21I должен включить representative pairwise corpus, где Империя и Индустриальный Союз получают различимые lawful decisions из одной общей simulation, без требования финального Stage-22 ship/content breadth.

### Stage 22 — Content / Technology / Balance Alpha

Stage 22 отвечает за **чем именно** две core factions производственно отличаются:

- industrial doctrine;
- engineering families;
- hulls/fits;
- shipyards/facilities;
- fleet composition;
- supply/replacement economics;
- visual bibles;
- characters/mission content;
- pairwise anti-dominance acceptance.

Stage 22 production sequence:

1. governance/migration audit;
2. Imperial gold slice;
3. Industrial Union contrast slice;
4. shared civilian/minor ecosystem;
5. pairwise alpha balance and long-run soak.

### Stage 23 — RC

Stage 23 полирует и выпускает production-complete **core pair**. Пять horizon factions не являются release blockers.

### Post-core horizon

После завершения основного этапа каждая новая major faction добавляется отдельным architecture/content package с prerequisite audit. Порядок определяется стоимостью необходимых common systems, а не только lore priority.

---

## 9. Пост-Core horizon — приоритет архитектурной готовности

Рекомендуемый порядок после core release:

1. **Пограничная Конфедерация** — многие нужные seams (salvage/refit/repair) уже близки к существующей архитектуре;
2. **Директорат** — требует углубления технологической сложности, automation и precision manufacturing;
3. **Лига Свободных Систем** — требует полноценного private-economy/freight/risk layer;
4. **Консорциум** — требует ownership/sovereignty/debt/concession split;
5. **Кочевой Флот** — требует наиболее фундаментального mobile-economic-node model.

Это **не lore ranking** и не окончательный production order. Перед каждой post-core faction выполняется свежий architecture audit.

---

## 10. Документное разбиение

Канонический пакет состоит из:

- `docs/factions/faction_roster_and_development_horizon.md` — roster, scope, core/horizon boundary;
- `docs/factions/empire_systemic_identity.md` — полная core specification Империи;
- `docs/factions/industrial_union_systemic_identity.md` — полная core specification Индустриального Союза;
- `docs/factions/post_core_faction_horizon.md` — закреплённые концепты пяти последующих фракций и required future mechanics.

Visual-specific production bibles могут существовать отдельно, но обязаны ссылаться на systemic identity и не могут менять simulation rules.

---

## 11. Канонический acceptance statement

Основной этап Star Empires не требует семь production-complete крупных держав.

Он требует доказать на Империи и Индустриальном Союзе, что одна и та же физическая, экономическая, политическая и военная simulation поддерживает **содержательно разные государства без hidden faction cheats**.

Пять остальных фракций являются не discarded ideas, а **зафиксированным следующим горизонтом**, для которого core architecture должна сохранять расширяемость без premature implementation и scope creep.
