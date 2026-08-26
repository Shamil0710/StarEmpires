# Star Empires — Империя: каноническая systemic identity

> Статус: **CORE FACTION / REQUIRED THROUGH STAGE 23**  
> Faction role: primary production gold slice.  
> Этот документ определяет политическую, экономическую, военную и инженерную логику Империи. Он не вводит faction-only authority и не заменяет существующие Stage-17/18/19/21 systems.

---

## 1. Короткая формула

**Империя** — старая межзвёздная монархическая держава с сильной государственной иерархией, тяжёлой промышленностью, развитой инженерной культурой, глубокой ремонтной инфраструктурой и склонностью сохранять, модернизировать и многократно возвращать в строй дорогостоящие активы.

Её сила должна ощущаться как:

```text
institutional continuity
+ state procurement
+ strategic reserves
+ heavy serviceable engineering
+ redundancy
+ disciplined logistics
+ controlled mobilization
```

а слабости как:

```text
high capital cost
+ large logistics footprint
+ slower institutional adaptation
+ maintenance burden
+ expensive territorial commitments
```

Империя не получает бонус «потому что Империя». Её преимущества должны быть следствием того, **как она строит, снабжает, ремонтирует, хранит резервы и принимает решения**.

---

## 2. Политическая идентичность

### 2.1 Основная модель

Империя — централизованное иерархическое государство монархического типа с устойчивой административной традицией и сильными государственными институтами.

Ключевые свойства:

- легитимность строится на непрерывности государства и службе;
- административная вертикаль сильнее локальной автономии;
- вооружённые силы — государственный институт, а не совокупность частных контракторов;
- крупная инфраструктура и стратегическая промышленность рассматриваются как элементы безопасности;
- долгосрочные государственные обязательства важнее краткосрочной рыночной эффективности;
- статус и иерархия должны быть читаемы в институтах, форме, кораблях, названиях и процедуре принятия решений.

### 2.2 Политическая игра

Имперский AI должен предпочитать:

- формальные договоры и признание;
- контролируемые долгосрочные access arrangements;
- устойчивые границы;
- обеспечение стратегических маршрутов;
- постепенную интеграцию территорий;
- институциональную память о нарушенных обязательствах;
- сохранение face/credibility государства при кризисах.

Но он не обязан выбирать войну. Если доступ, торговля, признание или гарантии решают проблему дешевле, мирный исход является полностью допустимым.

---

## 3. Экономическая модель

### 3.1 Государственный procurement

Имперская экономика должна заметно использовать государственные заказы для:

- флота;
- shipyard expansion;
- strategic stockpiles;
- ammunition;
- propellant;
- repair components;
- frontier infrastructure;
- defensive stations;
- convoy/security capacity.

Procurement — это не бесплатное производство. Каждый заказ обязан пройти обычную цепь:

```text
treasury authority
→ lawful order / contract
→ Stage-18 inputs
→ compatible facilities
→ work/time
→ physical output
→ ownership / deployment
```

### 3.2 Стратегические резервы

Империя должна поддерживать выше среднего target reserves для ограниченного набора критических ресурсов и компонентов.

Reserve policy влияет на:

- минимальный stock floor;
- готовность к войне;
- доступность ресурсов гражданскому рынку;
- стоимость длительной мобилизации;
- способность пережить временный route disruption.

Запрещено материализовывать reserve как hidden emergency stock.

### 3.3 Дорогая, но устойчивая инфраструктура

Имперский стиль предпочитает:

- крупные repair/refit hubs;
- защищённые military logistics nodes;
- распределённые redundant stores;
- тяжёлые shipyard capabilities;
- инфраструктуру с запасом по обслуживанию крупных корпусов.

Цена — высокий capital lock и время строительства.

---

## 4. Мобилизация

Империя является главным core proof для общей мобилизационной системы.

Рекомендуемая policy state machine:

```text
NORMAL
→ PREPARED
→ PARTIAL_MOBILIZATION
→ WAR_ECONOMY
→ EMERGENCY_MOBILIZATION
→ DEMOBILIZATION
```

Exact names могут меняться, но semantics должны оставаться общими и reusable.

Мобилизация может законно изменять:

- procurement priority;
- reserve release rules;
- convoy/security priority;
- military service throughput;
- shipyard queue priority;
- repair/rearm priority;
- допустимый treasury reserve floor;
- willingness to defer civilian projects.

Она **не может** напрямую увеличивать output multiplier без физической причины.

Цена мобилизации должна проявляться в мире:

- вытеснение гражданских заказов;
- рост demand;
- depletion strategic stocks;
- pressure on freight;
- maintenance backlog вне приоритетных направлений;
- treasury strain;
- post-war recovery burden.

---

## 5. Административная и территориальная логика

Империя территориальна: ей важны признанный контроль, защищённые границы и доступ к ключевой инфраструктуре.

Но территория не является бесплатным map-color asset.

Имперская expansion logic обязана проходить Stage-17/21 chain:

```text
interest / security need
→ claim / diplomacy / crisis
→ physical operation if needed
→ occupation
→ supply + security
→ stabilization
→ recognition/control
```

Для дальних территорий должны возрастать реальные издержки:

- supply distance;
- convoy demand;
- security presence;
- repair support;
- administrative infrastructure;
- route vulnerability.

Если позже вводится explicit administrative-capacity model, он должен выводиться из physical/institutional reach, а не существовать как mana bar.

---

## 6. Инженерная философия

### 6.1 Основная формула

```text
heavy engineering
+ redundancy
+ protected central citadel
+ service access
+ modular modernization
+ long service life
```

Имперская техника должна чаще выигрывать не максимальным nominal stat, а способностью:

- сохранять работоспособность после damage;
- локализовать отказ;
- возвращаться в строй;
- принимать refit;
- жить в long maintenance cycle;
- использовать зрелые, хорошо поддерживаемые component families.

### 6.2 Tradeoffs

Это требует реальной цены:

- больше dry mass;
- больше internal volume на redundancy/service;
- выше component/material cost;
- крупнее support footprint;
- длиннее construction/refit time;
- возможна меньшая peak-specific performance против более компактной advanced конструкции.

### 6.3 Ремонтопригодность

Repairability не является flat modifier.

Она должна выражаться через:

- доступ к повреждённым compartments;
- replaceable modules;
- standardized interfaces внутри имперской industrial family;
- spare-part availability;
- repair facility capability;
- damage-control redundancy;
- maintained component inventory.

---

## 7. Корабельная доктрина

Имперский fleet roster должен естественно поддерживать:

- устойчивые patrol/security groups;
- convoy escort;
- line combat groups;
- heavy cruisers/capital units;
- fleet logistics;
- repair/replenishment support;
- layered defense;
- длительные operations с организованным тылом.

Предпочтения Stage 22 могут включать:

- heavy passive protection;
- protected missile/VLS sections;
- axial kinetic weapons where role-appropriate;
- compact distributed PD;
- redundant sensors;
- strong service/repair support;
- moderate-to-strong endurance.

Ни один пункт не является обязательным для каждого корпуса; role и physical budgets имеют приоритет.

---

## 8. Fleet behavior

Имперский AI в бою и кампании должен быть склонен:

- сохранять строй и прикрытие важных активов;
- ценить repairable damaged ships;
- не бросать дорогой capital asset без strategic reason;
- держать reserve/home-defense capacity;
- организовывать staged reinforcement;
- предпочитать подготовленный operation импровизированному глубокому рейду;
- прекращать наступление при разрушении supply assumptions.

Withdrawal threshold не должен быть просто выше/ниже по имени фракции. Решение выводится из:

- objective value;
- readiness;
- repair opportunity;
- ammunition/reaction mass;
- supply route;
- force preservation policy;
- commitment horizon.

---

## 9. Дипломатическая логика

Империя должна высоко ценить:

- treaty reliability;
- recognized territorial status;
- strategic access;
- alliance obligations;
- buffer/security relationships;
- formal reparations/settlement after conflict.

Предпочтительные инструменты до войны:

1. negotiation;
2. access/recognition bargain;
3. guarantee/alliance pressure;
4. embargo/coercion;
5. ultimatum;
6. limited or full military operation when issue remains material and feasible.

War goal должен быть traceable к persisted interest/crisis evidence.

---

## 10. Информационная модель

Империя не является omniscient.

Её возможное преимущество — institutional reporting network:

- formal patrol reports;
- convoy/security reports;
- station observations;
- treaty/diplomatic reporting;
- military command hierarchy.

Но данные остаются actor-bounded, staleable и sensor-consistent.

Сильная бюрократия может давать более устойчивую historical memory, но не скрытую world truth.

---

## 11. Industrial and logistics dependencies

Для balance Империя должна иметь реальные bottlenecks.

Особенно важны:

- heavy components;
- repair parts;
- strategic metals where required by final content;
- ammunition production;
- reaction-mass/fuel flow;
- capital shipyard capacity;
- long-haul freight capacity.

Большой запас помогает пережить disruption, но не отменяет finite consumption.

---

## 12. Visual identity bridge

Accepted Imperial visual language является обязательным production constraint.

Ключи:

- old monarchic/imperial historical inspiration without literal historical costume copy;
- practical grounded hard-SF engineering;
- long axial ships;
- protected prow;
- visually strong central citadel;
- modular/serviceable stern;
- graphite/gunmetal base;
- warm ivory structural/administrative accents;
- restrained burgundy status accents;
- restrained brass only as hierarchy/precision detail;
- maintained service wear;
- no fantasy wings;
- no baroque space-cathedral excess;
- no steampunk machinery;
- no neon sci-fi noise.

Visual language обязана поддерживать systemic truth: тяжёлый, обслуживаемый, иерархический объект должен выглядеть таковым, а не просто носить имперский герб.

---

## 13. Character and institutional language

Для персонажей Империи:

- rank and function are readable without text;
- uniforms practical and hierarchical;
- dark navy/graphite, ivory, burgundy and restrained brass may recur according to role;
- engineers, medics, officers and logistics personnel должны отличаться профессией, не только shoulder patch;
- wear reflects service, not generic dirt;
- visual status remains controlled rather than luxurious fantasy ornament.

Character art follows the project Character Master Prompt and accepted faction visual code.

---

## 14. Player-facing gameplay identity

Игрок должен ощущать Империю через реальные interactions:

- доступ к сильным ремонтным/рефитным узлам;
- формальные требования доступа и статуса;
- устойчивые военные конвои;
- крупные государственные заказы;
- развитые рынки mature components;
- дорогие, обслуживаемые корабли;
- высокий institutional consequence нарушения договоров;
- возможность карьерного/политического взаимодействия через государственные структуры.

Не все рынки обязаны быть государственными, но strategic layer должен быть заметно institutionalized.

---

## 15. Stage mapping

### Stage 21I

Минимум доказать:

- Imperial decision traces explain reserve/security/access priorities;
- UI показывает interests, goals, crisis/war evidence, readiness и recovery без faction-only truth;
- representative corpus содержит peaceful, coercive and conflict outcomes;
- long-run persistence сохраняет institutional decisions и history.

### Stage 22.0

- exact stable-ID migration/disposition;
- systemic profile schema;
- production manifest;
- Imperial visual/systemic bible references;
- provisional content audit.

### Stage 22.1 — Imperial gold slice

Production-complete package:

- political/systemic profile;
- industrial doctrine;
- engineering families;
- six military base hull roles minimum;
- three civilian/support base hulls minimum;
- three signature station variants;
- reference fleet and support chain;
- six recurring NPCs;
- ten mission templates;
- two short faction chains;
- production visuals/audio/localization subset;
- peaceful/crisis/battle/loss/recovery/save-load acceptance.

### Stage 22 pairwise acceptance

Империя должна быть различима от Индустриального Союза по:

- silhouette;
- procurement;
- industrial organization;
- replacement behavior;
- fleet composition;
- supply pressure;
- diplomatic priorities;
- recovery path;
- NPC/institutional presentation.

### Stage 23

- production UI/readability;
- final visuals/audio;
- onboarding;
- supported save migration;
- long-session stability;
- exact-package RC proof.

---

## 16. Acceptance matrix

Имперская faction implementation считается валидной только если одновременно доказано:

1. Нет class/faction-name stat multiplier, создающего её основную силу.
2. Mobilization меняет lawful allocation/priority, а не materializes output.
3. Reserves физически существуют и могут исчерпаться.
4. Heavy engineering имеет mass/volume/cost/maintenance consequences.
5. Повреждения и ремонт проходят общий combat/repair authority.
6. Destroyed ships не возвращаются без ordinary replacement chain.
7. Дальний territorial commitment создаёт logistics/security burden.
8. Diplomacy/war goals causal and persisted.
9. AI действует только по actor-bounded observations.
10. Save/load сохраняет goals, deadlines, war/recovery history и identity.
11. Игрок может использовать те же systemic paths через обычные commands.
12. Визуальный язык соответствует реальной инженерной и институциональной структуре.

---

## 17. Не-кодируемые заранее детали

До Stage-22 content review не фиксировать без engineering evidence:

- конкретные проценты armor/reliability;
- точные hull masses;
- exact module stats;
- «национальные» weapon bonuses;
- конкретный superiority tier;
- гарантированный combat matchup outcome.

Systemic identity задаёт **направление tradeoffs**, а числовой balance принадлежит Stage 22.

---

## 18. Итоговая формула Империи

```text
old state
→ strong institutions
→ planned strategic reserves and procurement
→ heavy serviceable ships and infrastructure
→ high logistics/maintenance commitment
→ strong staying power when prepared
→ expensive overextension when supply and institutions cannot keep pace
```

Это и есть каноническая механическая идентичность Империи для основного этапа Star Empires.
