# Star Empires — дорожная карта разработки

> Канонический документ статуса, зависимостей и переходов между этапами разработки.
>
> Последняя синхронизация: **2026-08-15 после закрытия Stage 17C и подробной фиксации политико-экономической архитектуры Stage 17D–17F / Stage 18; `Ship Mathematics v1.0 Design Baseline` остаётся accepted foundation для 17.5 / 19 / 21. Фактический runtime-статус — Stage 17 ACTIVE, следующий implementation slice — 17D.**
>
> Начиная с Stage 16 новая и содержательно изменяемая проектная документация ведётся **на русском языке**. Имена классов, enum, content ID, API, формулы и технические идентификаторы сохраняются в оригинальном виде.

Основные stage-документы:

- `docs/stage11_autonomous_faction_expansion.md`;
- `docs/stage12_playable_actor.md`;
- `docs/stage13_combat_vertical_slice.md`;
- `docs/stage14_complete_player_economic_loop.md`;
- `docs/stage15_player_fleets.md`;
- `docs/post_stage15_inertia_and_jump_hardening.md`;
- `docs/stage16_player_construction.md`;
- `docs/stage16_construction_timing.md`;
- `docs/stage16_acceptance_matrix.md`;
- `docs/stage16_completion_record.md`;
- `docs/stage17_5_combat_depth_implementation_plan.md`;
- `docs/stage19_physical_world_generation_plan.md`;
- `docs/stage21_content_balance_plan.md`.

Ship Mathematics / cross-stage foundation:

- `docs/ship_hull_module_and_fleet_doctrine.md`;
- `docs/ship_mathematics_v0_1.md`–`docs/ship_mathematics_v0_9.md`;
- **`docs/ship_mathematics_v1_0_design_baseline.md` — ACCEPTED DESIGN BASELINE**;
- `docs/benchmarks/ship_mathematics_v1_0_design_baseline.json`;
- `docs/ship_mathematics_v1_roadmap_integration_contract.md`;
- `docs/flight_dynamics_and_combat_depth_roadmap.md`;
- `docs/ai_behavior_roadmap.md`;
- `docs/ui_navigation_roadmap.md`;
- `docs/cumulative_route_risk_model.md`;
- `docs/ship_pricing_roadmap.md`.

---

# 1. Цель проекта и главный инвариант

**Star Empires** — 2D top-down космическая sandbox-RPG/strategy с живой физической экономикой и миром, существующим независимо от игрока.

Целевая прогрессия:

```text
один корабль
→ торговец / шахтёр / наёмник
→ несколько собственных кораблей
→ компания и автономные флоты
→ собственные станции
→ собственная фракция
→ территория, дипломатия и война
→ региональная / галактическая держава
```

Главный инвариант:

> **Игрок и AI используют одни и те же физические, информационные и экономические правила везде, где это практически возможно.**

Без отдельного explicit architecture decision запрещены:

- отдельная «экономика игрока»;
- player-only combat/movement formula;
- passive income как замена реальному движению денег/товаров;
- virtual deliveries;
- скрытые resource grants;
- scripted replacement уничтоженных активов;
- мгновенное обычное путешествие/строительство;
- class-name combat bonuses, не выводимые из физического fit;
- отдельные authoritative `armorPoints`, `sensorPoints`, `stealthRating`, если они не являются UI-derived значениями принятой модели;
- UI, напрямую мутирующий authoritative simulation state.

---

# 2. Технологический стек

- Java 17;
- libGDX 1.14.2 / LWJGL3;
- Ashley ECS 1.7.4;
- VisUI 1.5.9;
- Maven Wrapper;
- JUnit + JaCoCo;
- GitHub Actions;
- data-driven JSON content catalog;
- deterministic fixed-tick simulation;
- versioned bounded binary persistence.

Решение Stage 8.5 остаётся **`KEEP_LIBGDX`**. Presentation technology пересматривается только при новом измеренном фундаментальном ограничении.

---

# 3. Основные milestones

| Milestone | Цель | Stages | Статус |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | корректное и масштабируемое ядро экономики | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | многосистемные фракции, логистика, строительство, автономная экспансия | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | корабль игрока, travel/trade/mining/combat/progression | 12–14 | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | флоты, станции, собственная фракция, combat depth, стратегическая война | 15–18 + 17.5 | **ACTIVE — Stage 17** |
| **v0.5 RPG & Living World** | physically calibrated world generation, discovery, NPC, missions, reputation | 19–20 | PLANNED |
| **v0.6 Content & Balance Alpha** | technology/content breadth и долговременная стабильность | 21 | PLANNED |
| **v0.7 Polish / Release Candidate** | UX, onboarding, performance, save hardening | 22 | PLANNED |

Административный долг: branch protection `main` не настраивается доступным connector API. Поэтому full CI gate остаётся ручным обязательным условием перед core merge.

---

# MILESTONE v0.1 — ECONOMIC SANDBOX

**COMPLETE.**

## Stage 0 — здоровье репозитория

**COMPLETE — PR #1.** Java-17 clean build, JUnit, JaCoCo, strict Javadoc, runnable desktop JAR и CI.

## Stage 1 — детерминированное время

**COMPLETE — PR #2.** Fixed step `0.1s`, pause/time scale, named RNG streams, explicit system order.

## Stage 2 — деньги и экономические инварианты

**COMPLETE — PR #3.** Integer milli-credits, finite liquidity, atomic bilateral trade, `EconomicLedger`, source/sink/transfer/transform semantics.

## Stage 3 — identity и persistence

**COMPLETE — PR #4.** Stable `EntityId`, versioned bounded codecs, migration-safe replacement and deterministic continuation.

## Stage 4 — data-driven content

**COMPLETE — PR #5.** Versioned JSON catalog со stable content IDs, validation, fingerprint и save binding.

## Stage 5 — локальная логистика и route planning

**COMPLETE — PR #6.** Bounded route planner, profit/time scoring, stale-route policy, deterministic tie-breaks.

## Stage 6 — headless scalability / observability

**COMPLETE — PR #7/#8.** Headless economic benchmark, accounting diagnostics и bottleneck observability.

### v0.1 DoD

Экономическое ядро детерминировано, сохраняет деньги/товары через явные rules, масштабируется headless и выдаёт diagnostics.

---

# MILESTONE v0.2 — LIVING GALACTIC ECONOMY

**COMPLETE.**

## Stage 7 — иерархия мира и уровни симуляции

**COMPLETE — PR #9.** `Galaxy → Sector → StarSystem`, typed IDs, topology, `WorldState`, bounded remote updates.

## Stage 8 — фракции как экономические акторы

**COMPLETE — PR #10.** Treasury, budgets, subsidies, diplomacy, territory, market access, taxes/tariffs, strategic demand и persistence.

## Stage 8.5 — технологическое направление

**COMPLETE — `KEEP_LIBGDX`.** Presentation/simulation separation validated.

## Stage 9 — динамическая экономика

**COMPLETE.** Physical station lifecycle, funded/materialized construction, destruction/salvage/economic shock, bottleneck-driven investment/recovery.

## Stage 10 — межсистемная логистика

**COMPLETE — PR #23.** Persistent `FleetId`, finite jump FSM, weighted multi-hop routing, physical supplier purchase/transit/sale.

## Stage 11 — автономная экспансия фракций

**COMPLETE — PR #24–#27.** Persistent opportunity/growth plans, real budgets/fleets/material supply, ordinary construction and physical competition.

### v0.2 DoD

Живая экономика может деградировать, логистически реагировать, инвестировать и физически расширяться без scripted respawn.

---

# MILESTONE v0.3 — PLAYABLE SPACE SANDBOX

**COMPLETE.** Подробности: `docs/stage14_complete_player_economic_loop.md`.

## Stage 12 — Player State / ownership / travel / manual trade

**COMPLETE — PR #29–#32.** Player wrapper над player-agnostic world, explicit ownership, shared trade controller, physical docking/travel, persistent player state.

## Stage 13 — Combat Vertical Slice

**COMPLETE — PR #35.** Shared player/AI target+fire, simple range/cooldown/shield/hull resolver, ordinary destruction/salvage. Этот resolver остаётся вертикальным срезом и будет заменён/расширен Stage 17.5.

## Stage 14 — полный игровой экономический цикл

**COMPLETE — PR #39/#41/#43/#45.** Trade, mining, ship progression, combat, UI, deterministic one-hour acceptance и shared inertial flight.

Текущая movement основа:

```text
total mass
→ thrust / mass
→ acceleration / braking
```

PR #51 распространил shared `FlightDynamics` на generic TradeAI/Mining и закрыл direct-position movement debt.

### v0.3 DoD

Игрок проходит физически связанный цикл flight → trade → mining → ship progression → combat → persistence, пока мир живёт независимо.

---

# MILESTONE v0.4 — FLEET & EMPIRE SANDBOX

**ACTIVE — Stage 17.**

## Stage 15 — флоты игрока / автономные приказы

**COMPLETE — PR #47/#48/#49; hardening #51.**

- multiple owned `FleetId`;
- persistent `HOLD/MOVE/TRADE/MINE/ESCORT/PATROL/FOLLOW`;
- shared inertial movement;
- physical trade/mining;
- civilian flee baseline;
- cumulative whole-route risk;
- global map fleet/order/threat context;
- ordinary jump FSM.

## Stage 16 — строительство игрока и владение станциями

**COMPLETE — PR #56–#70.**

Канонические документы: `docs/stage16_player_construction.md`, `docs/stage16_construction_timing.md`, `docs/stage16_acceptance_matrix.md`, `docs/stage16_completion_record.md`.

Финальный Stage-16 gate: **484/484 tests**, PR #70. Construction использует real site/wallet/material delivery/build time, remote continuation, ordinary station materialization, ownership reconciliation и destruction без free replacement.

---

# Stage 17 — собственная фракция игрока

**ACTIVE — текущий основной runtime stage.**

Цель: превратить независимого игрока с owned fleets/stations в обычного faction actor без замены существующих `FleetId`/`EntityId` и без отдельной player-only политико-экономической модели.

Stage 17 переиспользует Stage-8 faction core: treasury, budgets, subsidies, directed relations, territory, market access, fiscal levies, stock/production policy и persistence. Новые political/diplomatic layers расширяют этот core, а не создают отдельную player-only или scripted diplomacy subsystem.

## Политико-экономическая архитектура взаимодействия фракций — общий contract Stage 17–18

Дипломатия не является отдельной шкалой «нравится / не нравится» и не выдаёт абстрактные бонусы. Она должна быть следствием реального положения фракций в мире: ресурсов, рынков, логистики, территории, военной угрозы, договорных обязательств и институциональной доктрины.

Базовая причинная цепочка:

```text
physical economy / territory / security state
→ measurable interests and dependencies
→ institutional doctrine + diplomatic history
→ proposal / policy / strategic decision
→ access / tariff / treasury / logistics / production consequences
→ changed physical world state
→ changed interests and future diplomacy
```

### Государственные интересы

Каждая faction оценивает не абстрактную «силу соседа», а конкретные интересы, вычисляемые из authoritative world state:

- **economic security** — доступ к критическим ресурсам, рынкам, производственным цепочкам, shipyard/repair capability и транспортным маршрутам;
- **logistics security** — длина и уязвимость supply lines, chokepoints, наличие альтернативных маршрутов и запасов;
- **territorial security** — собственные controlled systems, спорные claims, важность пограничных систем и инфраструктуры;
- **industrial resilience** — зависимость от одного поставщика, одной системы или одного типа производства;
- **fiscal health** — treasury, station liquidity, construction/replacement burden и возможность финансировать выбранную policy;
- **military security** — доступная информация о чужих силах, мобилизации, присутствии возле границы и способности защитить routes/territory;
- **strategic opportunity** — ресурсы, рынки, незанятые или слабо защищённые системы, союзники, возможность снизить опасную зависимость;
- **treaty credibility** — соблюдение прошлых соглашений, нарушения, выполненные обязательства и накопленные grievances.

Эти показатели являются diagnostics/inputs для decision engine. Они не дают скрытых `+20% trade` или `-15% combat` бонусов.

### Институциональная доктрина faction

Различия между государствами выражаются не магическими faction modifiers, а весами и порогами общей decision model. Для authored faction и будущей faction игрока предусматривается persistent/data-driven **doctrine profile**.

Минимальные axes:

- `tradeOpenness` — готовность допускать чужие рынки/капитал и зависеть от внешней торговли;
- `securityPosture` — терпимость к риску и чужому присутствию рядом с критической инфраструктурой;
- `expansionPreference` — склонность инвестировать в новые territory/infrastructure;
- `sovereigntySensitivity` — насколько болезненно воспринимаются чужие claims, bases и строительство;
- `treatyLegalism` — вес договорных обязательств и цена нарушения собственного слова;
- `interventionism` — готовность нести расходы ради союзника или баланса сил;
- `economicResiliencePriority` — готовность платить более высокую цену за diversification, reserves и domestic production.

Doctrine меняет **приоритеты решения**, но не физические возможности. Торгово открытая faction всё равно не может импортировать отсутствующий товар; милитаристская faction не получает бесплатный флот; legalist не обязан принимать невыгодный договор.

### Directed diplomatic state: relation недостаточно

Существующий `FactionRelationState[-100..100]` сохраняется как компактная directed summary/compatibility input, но итоговая дипломатическая модель не должна сводиться к одному числу.

Для пары `A → B` планируются отдельные persistent/derived составляющие:

- **relation** — общий текущий политический тон;
- **trust / credibility** — ожидание, что B выполнит обещание;
- **perceivedThreat** — оценка военной/территориальной угрозы на основании доступной информации;
- **grievances / claims** — конкретные причины конфликта: нарушение договора, contested territory, экспроприация, атака, blockade и т.п.;
- **obligations** — действующие договорные обязательства A перед B;
- **economicInterdependence** — измеримая зависимость торговли/промышленности A от B;
- **treaties** — explicit юридические соглашения и их clauses.

Состояние остаётся направленным: A может критически зависеть от B и бояться его, тогда как B почти не зависит от A. Поэтому не вводится правило вида `relation < -50 = война` или `relation > 80 = союз`.

### Общий deterministic decision evaluator

AI faction и counterpart игрока оценивают diplomatic proposal через общую объяснимую utility model:

```text
utility =
    expectedEconomicBenefit
  + securityBenefit
  + strategicGoalAlignment
  + treatyAndTrustValue
  + doctrineFit
  - fiscalCost
  - sovereigntyCost
  - dependencyRisk
  - escalationRisk
  - opportunityCost
```

Каждый член utility должен выводиться из world state, doctrine или diplomatic history и быть доступен diagnostics/debug UI. Stable ordering/tie-breaks обязательны.

Игрок управляет policy собственной faction напрямую в пределах своих полномочий, но **не может принудительно заставить AI принять договор**. Предложение игрока оценивается тем же counterpart evaluator, что proposal одной AI faction другой.

### Economic interdependence

Взаимозависимость строится из реальной экономики, а не из abstract influence points. Минимальные metrics:

- доля critical-item imports от конкретной faction;
- доля exports/market revenue, зависящая от конкретного партнёра;
- концентрация поставщиков и покупателей;
- наличие альтернативного supplier/market и дополнительная стоимость маршрута;
- зависимость routes от чужих controlled systems/chokepoints;
- inventory buffer endurance при прекращении импорта;
- replacement time критической industrial capability;
- в будущем — зависимость от foreign shipyard/refit/repair capability.

Это позволяет получить естественные политические ситуации: слабая militarily faction может быть экономически незаменима; богатая держава может избегать войны из-за критической зависимости; embargo может ударить и по тому, кто его объявил.

### Treaty contract

Договор — persistent юридический объект, а не временный UI modifier. Он должен иметь stable ID, parties, clauses, дату вступления, optional expiry, notice/cancellation rules и breach semantics.

Планируемые clauses:

- bilateral/unilateral **market access**;
- **tariff ceiling / reduction / exemption**;
- **transit rights** через controlled territory;
- **non-aggression**;
- **construction / basing rights** в определённой territory;
- **resource supply agreement**, исполняемый через ordinary markets/orders/logistics, а не virtual delivery;
- **defense guarantee / mutual defense** — обязательство, военное исполнение которого реализуется Stage 18;
- **recognition / territorial settlement** для claims и control;
- **reparations / payments**, исполняемые conserved treasury transfers.

«Alliance» не является отдельным флагом дружбы: это набор explicit obligations и прав.

### Экономическая дипломатия

Политические решения обязаны воздействовать через уже существующую экономику:

```text
market access
→ кто физически может торговать

tariff / fiscal levy
→ реальный wallet transfer
→ изменение effective trade economics / station liquidity

embargo
→ закрытие legal access
→ route replanning
→ потеря поставщика/рынка
→ shortage / price / production response

strategic stock policy
→ targetStock floor
→ обычный market demand
→ TradeAI logistics
→ physical delivery

production policy
→ ordinary recipe selection
→ inputs / time / outputs

subsidy
→ treasury → station wallet
→ ordinary liquidity

reparations / treaty payment
→ treasury → treasury/wallet conserved transfer
```

Запрещены diplomatic effects, которые напрямую создают товары, деньги, production output или «урон экономике» без physical/economic механизма.

### Два разных типа тарифов

Нужно явно различать:

1. существующий Stage-8 **territorial fiscal levy**: surplus foreign station wallet → treasury контролирующей faction внутри её controlled system;
2. будущий **transaction/customs tariff**: часть конкретной внешнеторговой сделки.

Если вводится transaction tariff, он обязан:

- входить в expected route/trade cost **до** выбора маршрута;
- взиматься только при реально состоявшейся операции;
- записываться в ledger как conserved transfer;
- влиять на route choice и конкурентоспособность, а не существовать как UI percentage, оторванный от торговли.

### Escalation ladder

Политический конфликт развивается ступенчато:

```text
normal competition
→ diplomatic friction
→ tariff / access dispute
→ sanctions / embargo
→ formal demand / ultimatum
→ mobilization
→ blockade / limited armed coercion
→ formal war
→ ceasefire
→ settlement / peace treaty
```

Stage 17 реализует институциональные и экономические ступени. Armed coercion, blockade, war goals и formal war/peace принадлежат Stage 18, но используют те же treaties, claims, dependencies и grievances.

### Information boundary

Decision engine не должен навсегда зависеть от omniscient world state. На Stage 17 допустимо использовать authoritative state как временный источник данных, но API разделяет:

- **world truth**;
- **known/observed diplomatic-economic state**;
- **confidence/freshness**.

Stage 19 сможет подставить sensor/intelligence/comms latency без переписывания дипломатической логики.

### Граница внутренней политики

Stage 17 моделирует faction как **институционального стратегического актора**, а не симулирует население, парламент, корпорации и элиты фиктивными процентами.

Persistent governors, commanders, elite groups, legitimacy, regional interests и внутриполитическое давление вводятся только вместе с living-NPC layer Stage 20. Они должны модифицировать тот же doctrine/decision/treaty contract, а не создавать вторую параллельную дипломатию.

## 17A — player faction identity / creation contract

Persistent, migration-safe transition:

```text
independent PlayerState
→ explicit found/join action
→ stable world faction identity
→ ordinary faction state
```

Player faction не создаётся скрыто от самого факта владения станцией.

## 17B — affiliation существующих assets

Owned fleets/stations меняют legal/faction affiliation без respawn, ID replacement, cargo/wallet/condition reset. На `main` уже присутствуют Stage-17B slices для physical asset affiliation; Stage 17 остаётся ACTIVE до полного end-to-end gate.

## 17C — personal wallet ↔ faction treasury

**COMPLETE — PR #94/#95/#96, финальный aggregate gate PR #97.**

Personal wallet, faction treasury и station operating wallets остаются разными authoritative accounts. Explicit transfers `personal ↔ treasury` выполняются атомарно и ledger-visible в обоих направлениях. Ordinary station→treasury faction income не меняет personal wallet автоматически. Aggregate acceptance доказывает conservation трёх счетов, physical-state invariants и binary save/load.

## 17D — territory / control / construction access

**COMPLETE — PR #100.** `controlledSystems` теперь является persistent compatibility projection реального deterministic territorial process, одинакового для player и AI. Authoritative runtime различает presence, claim, stabilization, contested state, established control, recognition и explicit construction concessions; physical presence сама по себе sovereignty не создаёт.

Stage-17D construction authorization находится на authoritative world boundary: domestic construction разрешается в собственной jurisdiction, foreign construction в controlled territory требует explicit concession, а globally contested territory закрыто для ordinary construction до разрешения спора. Stage-11 autonomous expansion после физического anchor больше не получает мгновенный sovereignty: AI объявляет claim и проходит тот же stabilization/control lifecycle. Territorial state и construction rights входят в versioned binary persistence с backward migration.

### 17D.1 — territorial state model

Для StarSystem различаются как минимум:

- **presence** — faction имеет физические assets/traffic, но не получает sovereignty;
- **claim** — политически заявленная претензия без автоматического контроля;
- **control** — faction способна реально поддерживать юрисдикцию;
- **contested** — несколько factions имеют несовместимые control/claim основания;
- **recognition** — дипломатическое признание control/claim другими factions.

Существующий `controlledSystems` остаётся policy-compatible authoritative результатом control, но приобретение/потеря control проходит через deterministic ordinary rule.

### 17D.2 — основания реального контроля

Control score/evidence строится только из world state:

- persistent station/infrastructure anchors;
- локальное security/military presence;
- способность снабжать и поддерживать инфраструктуру;
- отсутствие или сила rival control presence;
- непрерывность присутствия / stabilization time;
- contested deterministic resolution.

Одна owned station не перекрашивает систему мгновенно. Однократный пролёт fleet также не создаёт sovereignty.

### 17D.3 — legal construction access

Construction command проверяет jurisdiction:

- в собственной controlled territory — ordinary domestic construction;
- в чужой controlled territory — только при explicit construction/basing right или иной concession;
- в contested territory — возможно только согласно legal state, с созданием grievance/claim consequences;
- в unclaimed territory — через обычный claim/control process;
- illegal/military construction как акт принуждения относится к Stage 18.

Player и AI проходят один authorization boundary; UI не обходит его.

### 17D.4 — territorial consequences

Только реально controlled jurisdiction может:

- применять territorial fiscal policy;
- определять default foreign construction/access regime;
- быть объектом recognition/claim treaty;
- участвовать в будущих blockade/front/war goals;
- давать strategic routing/security context.

Control сам по себе не создаёт деньги, ресурсы или бесплатную инфраструктуру.

### 17D acceptance

```text
player faction owns station in unclaimed system
→ presence exists, control absent
→ ordinary claim/control requirements fulfilled over time
→ system becomes controlled
→ foreign construction denied by default
→ explicit treaty/concession grants construction right
→ save/load
→ same control, claim and legal access
```

## 17E — diplomacy / market access / tariffs

**ACTIVE.** 17E.1 persistent institutional diplomacy и 17E.3 market-access precedence реализуются первым production slice: explicit trust/credibility, grievances, treaty directory, embargo state и единый `embargo → treaty right → relation threshold` resolver поверх authored + world-defined faction identities.

Цель — перейти от «relation threshold открывает рынок» к explicit, persistent и объяснимой межгосударственной политике, сохранив текущий Stage-8 access core как рабочую основу.

### 17E.1 — diplomatic state hardening

К существующим directed `relations` добавляются bounded persistent structures для:

- trust/credibility history;
- grievances и territorial claims;
- treaty directory;
- obligations/guarantees;
- embargo/sanction clauses.

`relation[-100..100]` остаётся summary signal и backward-compatible input, но не является единственным источником решений.

### 17E.2 — proposal / response engine

Общий command/evaluator обрабатывает:

- offer / counteroffer;
- accept / reject;
- terminate with notice;
- breach;
- renew/expire.

AI оценивает proposal через common utility model интересов и doctrine. UI игрока показывает основные причины решения: ожидаемая выгода, зависимость, security/sovereignty concern, trust, fiscal cost.

### 17E.3 — market-access precedence

Effective legal access определяется в явном порядке:

```text
hard legal prohibition / embargo
→ explicit treaty right or exemption
→ ordinary relation-threshold policy
→ deny / allow
```

Market access остаётся transient ECS projection persistent diplomacy через общий refresh boundary; persistent state является источником истины.

### 17E.4 — tariffs и fiscal separation

Существующий `foreignTerritoryTariffBasisPoints` фиксируется как **territorial fiscal levy** с реальным station→treasury transfer.

Отдельный transaction/customs tariff вводится только вместе с trade-controller integration:

```text
quoted buy/sell economics
+ applicable customs tariff
+ route risk/time
→ route profitability
→ actual trade
→ customs wallet transfer
```

Route planner обязан знать tariff заранее. Никаких невидимых постфактум штрафов или бесплатного treasury income.

### 17E.5 — embargoes / sanctions

Embargo не применяет абстрактный debuff. Он запрещает определённый legal market access, после чего обычные systems:

- перестраивают маршруты;
- ищут альтернативных suppliers/markets;
- сталкиваются с увеличением ETA/cost;
- расходуют buffers;
- создают shortage/price/production consequences.

Embargo может причинять measurable cost обеим сторонам и поэтому тоже проходит AI utility evaluation.

### 17E.6 — treaties / credibility

Выполнение договора постепенно укрепляет trust; нарушение создаёт explicit breach/grievance и снижает credibility. Эффект не обязан быть симметричным.

Нарушение договора не «ломает игру»: договорный state меняется, access/obligations refresh-ятся, а экономические и будущие военные последствия продолжаются ordinary systems.

### 17E.7 — economic-dependence diagnostics

Для каждой значимой пары factions доступны read-only diagnostics:

- critical imports dependency;
- export/market dependency;
- alternative-route/supplier cost;
- chokepoint exposure;
- buffer endurance;
- estimated cost of access loss/embargo.

Эти значения используются AI и позже отображаются в faction-management UI.

### 17E acceptance

```text
A depends on B for critical input
→ A proposes trade-access treaty
→ B evaluates benefit, dependency risk, trust and doctrine
→ treaty accepted
→ access projected to real markets
→ physical trade grows
→ B imposes transaction tariff / A searches alternatives
→ breach or embargo removes legal access
→ routes physically change and shortage emerges
→ save/load preserves treaty, trust, access and economic consequences
```

## 17F — faction policies / strategic economy

Цель — дать player faction и AI factions общий набор государственных economic-policy решений. Policy не заменяет рынок: она изменяет бюджеты, правовые ограничения и strategic demand, после чего реагирует обычная экономика.

### 17F.1 — doctrine profile

Persistent/data-driven doctrine задаёт веса общей decision model, а не performance bonus. Player faction получает editable baseline doctrine в допустимых пределах; authored AI factions получают характерные profiles.

Doctrine влияет на:

- openness vs autarky;
- reserve vs growth preference;
- security vs efficiency;
- expansion willingness;
- treaty behavior;
- tolerance of dependency and fiscal stress.

### 17F.2 — fiscal policy

Faction может задавать:

- own-station tax rate;
- territorial foreign-station levy;
- treasury reserve floor;
- station liquidity-support policy;
- construction/investment budget priorities;
- после Stage 17.5/18 — military ammunition/repair/replacement reserve priorities.

Все выплаты и сборы являются real wallet transfers. «Budget» — authorization/priority над treasury, а не второй магический источник денег; отдельный sub-account допускается только как conserved persistent account.

### 17F.3 — fiscal trade-offs

Policy должна иметь реальные последствия:

- высокий tax быстрее наполняет treasury, но может ухудшить liquidity собственных stations;
- низкий reserve ускоряет expansion, но повышает риск неспособности финансировать emergency logistics/repair;
- subsidy поддерживает critical station, но уменьшает public treasury;
- protectionism снижает foreign dependence, но может повысить цены и увеличить логистическую дистанцию;
- open trade повышает efficiency, но может создать supplier/chokepoint dependency.

Ни один trade-off не реализуется flat multiplier, если его можно получить через wallets, markets, logistics и production.

### 17F.4 — strategic stock / production policy

Переиспользуется текущая философия `FactionStrategicPolicyEngine`:

```text
strategic goal / resilience policy
→ target stock floor / desired production recipe
→ ordinary market prices and demand
→ TradeAI logistics
→ physical inputs
→ timed production
```

Policy не materialize-ит товар и не завершает производство мгновенно.

### 17F.5 — resilience policy

Faction может сознательно предпочесть:

- diversified suppliers;
- minimum strategic buffers;
- local production despite higher nominal cost;
- redundant routes/infrastructure;
- critical-item import limits.

Цена resilience должна проявляться как реальные дополнительные capital/logistics/operating costs.

### 17F.6 — policy feedback / anti-oscillation

AI пересматривает policy по bounded cadence и hysteresis:

```text
measure pressure / dependency / treasury / shortage
→ compare against doctrine thresholds
→ choose bounded policy adjustment
→ wait observation window
→ measure consequences
```

Запрещены every-tick tariff/tax/recipe oscillations. Decisions deterministic при одинаковом state.

### 17F.7 — player/AI parity

Player UI отправляет те же policy commands, которые может сформировать AI planner. Игрок получает больший уровень прямого контроля, но не отдельные экономические правила и не бесплатное исполнение policy.

### 17F acceptance

```text
faction has critical import dependency and weak treasury
→ policy chooses reserve + supplier diversification
→ strategic stock demand rises
→ ordinary traders establish more expensive alternative route
→ tax/subsidy transfers change real wallets
→ buffers improve while treasury/growth incur measurable cost
→ policy does not oscillate
→ save/load preserves doctrine, policy and economic state
```

## 17G — faction management UI / global map

Read-only authoritative model + commands для treasury, assets, territory, diplomacy, access/tariffs, policies и expansion context.

## 17H — persistence / migration / end-to-end acceptance

Final scenario:

```text
independent player with Stage-16 assets
→ found faction
→ same physical assets affiliated
→ transfer real capital
→ apply ordinary policy
→ economy reacts
→ territory/access only by legal rules
→ save/load
→ diplomacy/access persist
→ no duplication/reset/resources created
```

Stage 17 становится COMPLETE только после этого gate.

---

# Stage 17.5 — Combat Depth / Ship Fitting Foundation

**PLANNED — обязательный `Ship Mathematics v1.0` research gate ВЫПОЛНЕН, но Stage 17.5 ещё не ACTIVE, пока текущий Stage 17 не завершён и обычный stage-transition gate не пройден.**

Accepted foundation:

- PR **#91**;
- CI **#1516** — green full Java-17 verification;
- merge **`3ec2f6cab286dbcd39694c19a055d038c175b59c`**;
- `docs/ship_mathematics_v1_0_design_baseline.md`;
- `docs/benchmarks/ship_mathematics_v1_0_design_baseline.json`.

Подробный implementation plan: **`docs/stage17_5_combat_depth_implementation_plan.md`**.

Назначение Stage 17.5: **runtime promotion принятой модели**, не повторное исследование фундаментальной architecture.

## Frozen foundation

Все ship/module systems сходятся в общие budgets:

```text
mass / geometry / volume
power / stored energy
heat / coolant / rejection
crew / automation
ammunition / stores / reaction mass
thrust / acceleration / delta-v
signature / sensors / tracks
shield / weapons / protection
compartments / damage
maintenance / logistics / operating cost
```

Shields и FTL остаются fictional/exotic technology, но также платят mass/power/energy/heat/time и не получают отдельную бесплатную «магию».

## 17.5A — production schema

`HullDefinition`, `ModuleDefinition`, `MaterialDefinition`, `ProtectionStackDefinition`, physical slots/hardpoints/compartments, versioned content validation.

## 17.5B — central derived-ship calculator + fitting validator

Одна authoritative boundary рассчитывает total mass, volume, power/heat margins, crew, consumables, thrust/acceleration/Δv, signatures, sensors, shields, weapons, protection и logistics. Fit обязан одновременно проходить geometry/mass/volume/power/heat/crew/ammunition constraints.

## 17.5C — propulsion / reaction mass / power / thermal / FTL

Production `a=F/m`, mass flow, finite Δv, persistent reaction mass, local coolant + ship heat bus + radiators, peak energy and brownout policies, fitted jump mass/energy/spool/transit/cooldown.

## 17.5D — sensors / signatures / TrackState / datalink / EW

`DETECTED → CLASSIFIED → TRACKED → FIRE_CONTROL`; thermal/plume/RCS/optical channels; covariance and track age; distributed measurement geometry; ECM/ECCM/decoys through signal/measurement model.

## 17.5E — kinetic / beam / guided / PD / ammunition

Physical projectiles, beam dwell/thermal limits, missile propulsion/seeker/guidance, finite magazines, launcher cells/support channels, safe intercept geometry, layered deterministic defense scheduler.

## 17.5F — shields / armor / compartments / subsystem damage

Shield field reserve + interaction power + recharge/heat/coverage; bounded heavy-impact response surfaces; debris/spall; spatial compartment routing; damage changes real capabilities.

## 17.5G — shipyard / refit / repair / maintenance economy

Hull/modules require real materials/components/facility capability/work. Refit modifies same physical asset; repair consumes parts/materials/work; player and AI use common production/fitting boundary.

## 17.5H — capability APIs / UI / persistence

Stable queries for acceleration, Δv, jump, observation/track, fire solution, shield, thermal/ammo endurance, damage/repair. Fitting UI shows derived consequences. Authoritative fitting/consumables/damage/thermal/shield/FTL state survives save/load.

## 17.5I — full deterministic acceptance

Required regression spans representative civilian + military fits, mass/cargo effects, thermal damage, sensor-network geometry, combat saturation, shields, heavy impact, construction/refit/repair economy и persistence.

### Stage 17.5 hard invariants

1. no player-only combat physics;
2. no class-name performance bonus;
3. no independent magical `accuracy/range/PD chance`;
4. no free ammunition/reaction mass;
5. no global HP-only survivability;
6. no module outside common mass/volume/power/heat/economy contract;
7. no accepted fit with violated mandatory budget;
8. deterministic fixed-step behavior;
9. ordinary destruction/salvage/economic consequences preserved;
10. full CI green.

Stage 17.5 COMPLETE только когда freighter→battleship работают через один data-driven fitting/capability model и Stage 18 может безопасно строить advanced tactical AI поверх stable APIs.

---

# Stage 18 — strategic warfare + coercive diplomacy + advanced combat behavior

**PLANNED после Stage 17.5 COMPLETE.**

Stage 18 завершает вооружённую половину политической модели. Он не создаёт отдельную diplomacy subsystem, а использует Stage-17 treaties, claims, directed trust/grievances, economic dependencies, territory и treasury вместе с Stage-17.5 physical combat capabilities.

## 18A — formal conflict state / crisis escalation

War не выводится автоматически из одного `relation` threshold. Persistent conflict state хранит:

- participants;
- legal state: peace / crisis / war / ceasefire;
- cause / triggering grievance;
- start time;
- explicit war goals;
- treaty obligations and joined allies;
- optional escalation/ceasefire constraints.

Переход к войне должен быть отдельным strategic decision с оценкой security gain, expected cost, logistics readiness, treaty credibility и economic dependence.

## 18B — war goals / political objectives

Военные цели имеют world-state meaning:

- obtain/control/recognition of конкретной territory;
- remove foreign base / construction right;
- force market/transit concession;
- end blockade/embargo;
- impose or remove treaty clause;
- obtain reparations через real treasury transfer;
- defend/restore союзника по guarantee;
- ограниченная punitive goal без обязательного annexation.

Нет победы, которая materialize-ит reward только потому, что заполнилась абстрактная war-score шкала. Progress оценивает реальное possession, blockade, losses, logistics и ability/willingness сторон продолжать войну.

## 18C — mobilization / readiness

Мобилизация проявляется в экономике до выстрелов:

```text
military goal
→ ammunition / fuel / repair / replacement stock demand
→ treasury budget pressure
→ production and logistics response
→ fleet readiness
```

Нельзя получить mobilized fleet через бесплатный spawn. Недостаток ammunition, replacement parts, reaction mass или shipyard capacity ограничивает реальную способность вести войну.

## 18D — blockade / interdiction

Blockade — physical operation fleets/assets на routes, jump chokepoints или возле markets.

Effective blockade зависит от:

- actual fleet presence and combat capability;
- sensor/track capability;
- route geometry;
- ability to intercept;
- defender/escort presence;
- alternative routes;
- resupply/endurance блокирующей стороны.

Кнопка `blockade` сама по себе не удаляет импорт. Traders reroute или прекращают рейс из-за реального legal/risk/access state.

## 18E — fronts / objectives / advanced tactical AI

Strategic objectives строятся из territory, infrastructure, logistics and intelligence. Tactical layer после Stage 17.5 использует общие capabilities:

- escort / screen / intercept;
- retreat / pursuit;
- formation doctrine;
- range / mobility / sensor-aware behavior;
- ammunition/endurance awareness;
- protection of logistics assets and chokepoints.

AI не получает omniscience и не дублирует combat physics.

## 18F — war economy / replacement consequences

Conflict обязан менять living economy:

- traffic rerouting;
- shortage and price shocks;
- ammo/repair/reaction-mass expenditure;
- damaged/destroyed physical assets;
- shipyard replacement backlog;
- treasury drain;
- construction delays;
- temporary loss of markets/routes;
- salvage/capture where ordinary mechanics permit.

Никакого scripted replacement уничтоженных fleets/stations.

## 18G — ceasefire / settlement / peace treaty

War завершается explicit settlement clauses:

- territorial recognition/control changes;
- withdrawal deadlines;
- market/transit/construction rights;
- tariff/access terms;
- treaty termination or guarantees;
- reparations with conserved transfers;
- demilitarized/basing restrictions, если соответствующие mechanics существуют.

Compliance восстанавливает credibility; breach создаёт новый grievance/crisis. Мир не сбрасывает отношения к фиксированному значению.

## 18H — information / intelligence

Strategic warfare использует confidence/freshness/decay. До Stage 19 допустим authoritative compatibility provider; после Stage 19 тот же decision API получает observed/intelligence state с communication latency.

## 18I — deterministic conflict acceptance

```text
trade-dependent factions
→ access/tariff dispute
→ embargo and measurable economic damage
→ ultimatum rejected
→ mobilization creates real stock/logistics demand
→ formal war
→ physical blockade reroutes traffic
→ shortages, losses and replacement backlog
→ ceasefire
→ reparations + territorial/access settlement
→ save/load continuation
→ no money/resources/fleets created by diplomacy or war state itself
```

### v0.4 DoD

Игрок развивается от одного корабля до fleets/stations/faction и участвует в войне, меняющей реальные assets, supply chains, territory и replacement economy.

---

# MILESTONE v0.5 — RPG & LIVING WORLD

**PLANNED.**

# Stage 19 — исследование / discovery / physically calibrated world generation

**PLANNED.** Подробный план: **`docs/stage19_physical_world_generation_plan.md`**.

Главное правило:

> **World generation использует физический scale Ship Mathematics v1.0 / Stage 17.5: расстояния выбираются вместе с travel time, acceleration/braking, Δv, jump timing, sensor visibility, logistics throughput и economic cadence.**

Не существует несвязанных `strategic/combat/sensor distance units`: authoritative local scale — SI.

## 19A — representative-ship scale calibration

Для freighter/miner/corvette/destroyer/cruiser/capital/tanker считать physical ETA, braking, Δv, reaction mass, jump spool/transit/cooldown и sensor exposure на representative routes.

## 19B — star-system physical geometry

Deterministic SI placement stations, jump zones, resource regions, celestial/operational anchors, anomalies/derelicts и transit volume с physical clearance/approach constraints.

## 19C — infrastructure spacing via logistics bands

Авторские labels вроде `SHORT_LOCAL_LOGISTICS` разрешены только как derived bands, которые переводятся в SI geometry через representative ship travel consequences.

## 19D — inter-system jump topology

Jump graph одновременно создаёт trade alternatives, borders/chokepoints/remoteness и реальные response times. Edge хранит explicit transit semantics; fitted ship добавляет mass/energy/spool/cooldown.

## 19E — resources + economic bootstrap

Resource distance → haul time → ship throughput → inventory buffer → price/industrial viability. Essential supply chains должны быть physically feasible либо намеренно обозначены как shortage scenario.

## 19F — sensor-consistent discovery

`UNKNOWN / DETECTED / CLASSIFIED / TRACKED / KNOWN_STATIC_LOCATION`; distant detection не даёт automatic precise range/identity/fire-control.

## 19G — anomalies / derelicts / special locations

Special content имеет physical position, detection/approach/hazard/value semantics и не живёт в отдельной arbitrary distance scale.

## 19H — communications/intelligence latency seam

Observation/transmission/receipt/freshness используют physical distance там, где latency включена design.

## 19I — economy cadence calibration

Mine/factory/buffer/construction rates сверяются с реальным freighter payload × round-trip time. Hidden market restock не заменяет transport.

## 19J — deterministic seed / persistence

Stable seed/version/IDs, bounded generation, materialized world persistence; generator update не переписывает старую campaign без migration policy.

## 19K — physical world acceptance

Scale, sensor, economy, tactical/strategic geometry и performance matrices на representative seeds.

### Stage 19 hard invariants

1. authoritative local distance = meters;
2. ETA derives from actual movement/jump capability;
3. mass/cargo affects logistics through shared physics;
4. no instant jump outside FSM;
5. visibility uses physical signature/sensor channels;
6. discovery ≠ omniscience;
7. production cadence checked against delivery latency;
8. accidental dead economy is generation defect;
9. player and AI inhabit same geometry;
10. same seed/version deterministically reproduces equivalent world.

---

# Stage 20 — NPC / missions / reputation / progression

**PLANNED.**

Persistent NPC там, где identity важна. Missions возникают из real world state: haul, mine, escort, bounty, investigate, defend, shortage, expansion, war, discovery.

Persistent commanders могут давать bounded personality/doctrine modifiers, но не omniscience и не нарушение Stage-17.5 physics.

## Internal politics / institutions

Только на этом этапе, когда появляются persistent NPC и living-world actors, допускается разворачивать внутреннюю политику faction:

- governors / admirals / ministers / corporate or regional elites;
- personal and institutional loyalties;
- legitimacy / political support;
- regional economic interests;
- corruption / patronage, если они имеют реальный resource flow;
- factions внутри государства и pressure на policy;
- political appointments / missions / crises.

Эти actors не получают отдельную «внутреннюю экономику». Они создают pressure/constraints для того же Stage-17 doctrine/decision/treaty/policy contract. Например, industrial interest может повышать utility protectionist policy, governor — сопротивляться high station tax, а military leadership — требовать larger strategic reserves; фактическое решение всё равно исполняется через ordinary treasury, markets, logistics и legal state.

Не вводить раньше Stage 20 декоративные `population happiness`, `parliament support` или `elite influence` bars без физических/NPC причин и gameplay consequences.

---

# MILESTONE v0.6 — CONTENT & BALANCE ALPHA

**PLANNED.**

# Stage 21 — ширина контента / technology / balance / long-run stability

**PLANNED.** Подробный план: **`docs/stage21_content_balance_plan.md`**.

`Ship Mathematics v1.0` и production Stage 17.5 являются механической основой. Stage 21 расширяет catalog/technology/faction doctrines **внутри этой модели**.

Главный invariant:

> **Новый module/hull/technology валиден только если преимущества, недостатки, производственная цена и operational consequences выражаются через v1.0 budgets/interfaces. Новый fundamental stat — Architecture Change Request.**

## Technology ladder

Не `tier = blanket +25%`. Improvements выражаются через specific power/thrust, exhaust velocity, material response, sensor noise/aperture/pointing, thermal performance, shield field/recharge, launcher/guidance, automation/manufacturing/maintenance и соответствующие economic costs.

## 21A–21G — engineering content families

Расширить:

- materials/components + heavy-impact response-surface datasets;
- reactors/energy storage/distribution;
- propulsion/maneuver/reaction-mass/FTL;
- thermal/radiator/coolant/storage systems;
- sensors/comms/fire-control/EW/decoys;
- kinetic/beam/guided/PD + real ammunition economy;
- shields + passive/spaced/citadel/localized protection.

Каждая family имеет physical/economic tradeoffs, а не скрытый rating.

## 21H — hull families and variants

Military + civilian/industrial hull breadth в иерархии `Size → Architecture → Doctrine → Specialization → Design → Variant/Refit`.

Anti-obsolescence: larger hull не должен автоматически отменять smaller; сравниваются acceleration, signature, crew/OPEX, docking/yard access, scouting/screen/response value, production time и logistics.

## 21I — faction engineering doctrines

Faction identity через реальные design preferences/procurement/industrial capabilities: thrust, armor/shields, missile/kinetic/carrier/EW doctrine, automation/manpower, endurance и fleet composition. Нет faction magic bonuses.

## 21J–21K — shipyard + lifecycle economy

Facility capability по berth/fabrication/precision/material/optics/reactor/drive/shield/FTL/ammo/work-rate axes. Build/refit/repair/maintenance/replacement используют real materials/components/work/money.

## 21L — fleet composition/doctrine balance

Patrol, convoy escort, missile group, carrier group, line battle group, raider, recon/EW, logistics train, civilian convoy. Метрики включают combat, sensor, ammunition/repair/reaction-mass endurance, OPEX и replacement cost/time.

## 21M — combat saturation/endurance soak

Sweep по attacker count, salvo/waves, escorts/spacing, sensors/EW, shields, thermal/magazine/damage state. Outputs: leakers, ammo expenditure, beam heat, shield reserve, subsystem damage, survival, repair burden, cost exchange ratio.

## 21N — world-scale logistics soak

На Stage-19 worlds проверять real trade/mining/ammo/repair/tanker/carrier/shipyard/reinforcement logistics. Distance должна создавать measurable economic geography.

## 21O — macro economy long-run soak

Inflation/deflation, dead economies, shortages/buffers, entity/ledger growth, backlog, runaway production, faction snowball, replacement economics, logistics collapse, resource monopolies, idle yards, ammo accumulation.

## 21P — anti-universal-build matrix

Если один fit одновременно лучший по DPS/defense/sensors/mobility/endurance/cost — это balance defect без explicit technology discontinuity. Проверять armor↔acceleration, shield↔power/heat, magazine↔volume/protection, sensors↔mass/cost, Δv↔payload, automation↔cost/power/vulnerability, carrier wing↔direct weapons.

## 21Q — anti-linear-tier-obsolescence

Advanced content может быть лучше, но availability/material/facility/maintenance/cost должны сохранять niches. `highest tier = only rational choice` для всей игры — defect.

## 21R — faction differentiation acceptance

Reference fleets/industrial support major factions должны отличаться engineering/economic doctrine и silhouettes/behavior, оставаясь в одной physics model.

## 21S — player progression / market availability

Access через relations, markets, industrial location, yard capability, component availability, salvage/capture/research systems — не бесплатный menu unlock, если это не explicit RPG abstraction.

## 21T — benchmark/fingerprint governance

Machine-readable representative hull/fits/technology/cost/combat/world-logistics/economy anchors. Lock intentional invariants, а не каждую цифру навсегда.

### Architecture change policy

Если новый content требует поля вне v1.0 contract, сначала проверить, можно ли выразить capability существующими physical parameters. Если нет — отдельный architecture proposal, migration и regression; не hidden JSON extension.

### Stage 21 completion gate

- content breadth alpha-ready;
- meaningful technology tradeoffs;
- factions engineering-distinct;
- viable civilian/military niches;
- no universal dominant fit;
- no automatic small-hull obsolescence;
- real high-tier bottlenecks;
- ammo/reaction-mass/repair logistics sustainable;
- world-scale economy stable on representative seeds;
- wars produce replacement/economic consequences;
- bounded save/load/soak;
- CI + long-run benchmark gates green.

---

# MILESTONE v0.7 — POLISH / RELEASE CANDIDATE

**PLANNED.**

# Stage 22 — UX / onboarding / performance / release hardening

- unified HUD/management UI;
- global/local map filters/search/notifications;
- input discoverability/accessibility/scaling;
- onboarding trade/mining/combat/fleet/station/faction;
- autosave/backup/corrupt-save UX and migration window;
- profiling large combat/world generation/remote worlds/route planning/asset lists/construction/save-load;
- final graphics settings/release baselines;
- clean regression/soak/save-load-soak gates.

---

# 4. Параллельный Visual / UX track

Visual work идёт параллельно, но не заменяет functional DoD.

- **V1 Ship sprite pipeline:** grounded top-down language, size grammar, hardpoints, pivots/collision conventions.
- **V2 Engine/movement:** VFX tied to actual thrust/maneuver/plume state; signature-relevant states do not lie visually where practical.
- **V3 Station language:** construction/industrial/mining/trade/military/colony/faction differentiation.
- **V4 Combat VFX:** weapons, shields, local hits, damage, destruction, salvage.
- **V5 Playable navigation/readability:** Stage-14 baseline COMPLETE.
- **V6 Strategic map / empire UI:** fleets/orders/construction/stations baseline; territory/diplomacy/war continue Stages 17–18; Stage 19 adds physically calibrated map/discovery scale.

Gameplay не зависит от одного sprite asset. Presentation metadata remains data-driven over authoritative definitions.

---

# 5. Сквозные инженерные правила

## Persistence

Каждый persistent domain object имеет stable identity, schema ownership, bounded codec, migration policy и continuation tests.

## Determinism

Planner/AI/combat используют deterministic iteration/tie-breaks. RNG именован только там, где randomness — explicit design requirement.

## Economic conservation

Любое изменение денег/resources имеет transfer/source/sink/transform semantics и invariant coverage.

## Physicality

Construction, trade, mining, progression, expansion, fitting, warfare и world travel используют real entities, finite resources/cargo/ammunition/reaction mass, wallets и time. Remote simulation может снижать fidelity, но не создавать несовместимые consequences.

## Shared player/AI core

Player commands и AI intent адаптируются к общим controllers/capability APIs. Player-only implementation требует explicit justification.

## Movement physicality

Ordinary local movement идёт через shared `FlightDynamics`; no snap Transform movement кроме structural materialization events с documented semantics.

## Unified Ship Mathematics v1.0 module paradigm

**Все новые ship modules/equipment обязаны использовать accepted v1.0 common integration contract.** Где применимо, модуль участвует в mass/volume/geometry, power, stored energy, heat/coolant/rejection, crew/automation, ammo/consumables/reaction mass, signature, damage/maintenance и construction/economy.

Специализированная capability equation допустима; parallel hidden resource model без Architecture Change Request — нет.

## Exotic technology accounting

Shields/FTL могут быть fictional physics, но не освобождены от common engineering accounting. Они имеют mass/volume/power/energy/heat/time/damage/logistics constraints.

## Damage physicality

Damage проходит через protection → spatial impact/debris → compartments/subsystems → изменение реальных capability inputs. Global HP может существовать как presentation/structural aggregate, но не как единственная survivability mechanic.

## Sensor / information physicality

Detection, classification, tracking и fire-control различаются. Sensors/EW работают через physical signal/measurement/covariance model; AI не получает omniscience.

## World-scale physicality

**Stage 19 и любой дальнейший generated content обязаны использовать SI geometry, ship acceleration/braking/Δv/jump time, sensor/fire-control scale и logistics/economic latency.** World distance distributions замораживаются только после representative-ship calibration.

Combat scale, navigation scale, sensor scale и strategic map distance должны иметь однозначное физическое соответствие.

## Jump / structural materialization

Inter-system travel uses finite jump FSM. Stage 17.5 добавляет fitted mass/energy/spool/cooldown contract; Stage 19 калибрует edge transit distributions. Current test fixture не является final galaxy scale.

## AI information / route risk

Risk decisions используют доступные observations/intelligence; whole-route risk оценивает полный traversed path.

## Construction / shipyard physicality

Construction/refit/repair feasibility/time зависят от real project/material/component/facility inputs. Credits не заменяют missing capability/materials.

## Ownership vs faction identity

Ownership — отдельный persistent layer. Affiliation transition не заменяет physical asset.

## Technology tiers

Technology = data-driven engineering/manufacturing capability, не blanket multipliers. Player/AI use same checks.

## Presentation read-only boundary

UI reads authoritative state/derived queries and submits commands; direct mutation forbidden.

## Documentation language

Начиная с Stage 16 project documentation/roadmap/stage plans — русский; code/content identifiers сохраняются.

## Measure before optimization/balance

Крупные systems получают diagnostics/benchmarks. Balance changes делаются по measured scenarios/soak, не только spreadsheet DPS.

---

# 6. Правила перехода между stages

1. `main` остаётся стабильным.
2. Core work начинается от текущего green `main`.
3. Broken blocking CI запрещает merge/stage transition.
4. Каждый stage имеет explicit vertical slice + DoD.
5. Persistent changes требуют migration/continuation coverage.
6. Economic changes требуют conservation/invariant coverage.
7. Deterministic decision code требует stable tie-break coverage.
8. Player и AI используют общие APIs, если разделение не обосновано.
9. Не расширять массовый content breadth до стабилизации mechanics.
10. UI/map остаются views + command adapters.
11. Advanced tactical AI не начинается до Stage 17.5 COMPLETE.
12. Strategic danger routing оценивает весь путь.
13. Direct normal-movement `Transform` mutation не возвращается.
14. Ship pricing использует live economy/material/component/fitting/condition/relationship inputs и real asset transfer.
15. Construction/refit/repair time определяется real project/material/facility inputs.
16. Player ownership отделено от faction identity.
17. Tech tiers — system/content data, не blanket multipliers.
18. Новая/обновляемая документация с Stage 16 — русский язык.
19. Artifact publication failure due external quota non-blocking, если core `clean verify`/tests/Javadoc/JaCoCo/package green согласно policy.
20. Roadmap status меняется только по implementation/merge evidence либо explicit user plan decision.
21. **`Ship Mathematics v1.0 Design Baseline` research gate ВЫПОЛНЕН: PR #91, CI #1516, merge `3ec2f6cab286dbcd39694c19a055d038c175b59c`.** Это снимает research blocker, но не автоматически завершает Stage 17 и не переводит Stage 17.5 в ACTIVE.
22. **Stage 17.5 production implementation начинается только после Stage 17 completion/transition discipline и использует `docs/stage17_5_combat_depth_implementation_plan.md`; fundamental architecture v1.0 не меняется тихо.**
23. **Все новые modules/equipment используют v1.0 common integration contract; новый fundamental budget/stat требует Architecture Change Request.**
24. **Stage 19 world generation обязана пройти representative-ship physical scale calibration до freeze geometry distributions.**
25. **Stage 21 mass content не имеет права вводить parallel physics/economy ratings; technology и faction differentiation выражаются через v1.0 + real construction/maintenance/logistics.**

---

# 7. Текущий следующий шаг

**ACTIVE: Stage 17 — собственная фракция игрока.**

Фактическая база:

- Stage 15 COMPLETE — multiple owned fleets + persistent orders;
- Stage 16 COMPLETE — physical construction + owned ordinary stations;
- Stage-8 faction treasury/territory/relations/access/policies exists;
- Stage-17 identity/asset-affiliation и conserved treasury boundary уже находятся на `main`;
- `WorldState` остаётся player-agnostic;
- **Ship Mathematics research COMPLETE at v1.0 Design Baseline**;
- v1.0 PR #91 / CI #1516 / merge `3ec2f6cab286dbcd39694c19a055d038c175b59c`;
- detailed future plans prepared for 17.5 / 19 / 21.

Immediate Stage-17 order остаётся:

1. 17A identity/creation/persistence contract;
2. 17B complete asset affiliation including all lifecycle/transit seams;
3. 17C personal wallet ↔ faction treasury — COMPLETE (PR #97);
4. 17D territory/control/construction access — NEXT;
5. 17E diplomacy/market access/tariffs;
6. 17F faction policies;
7. 17G management/global-map UI;
8. 17H migration/conservation/save-load/full acceptance.

После Stage 17 COMPLETE следующий плановый production step — **Stage 17.5**, потому что его research prerequisite теперь выполнен. Его первая реализация должна начинаться с **17.5A schema/material/hull/module**, а не с нового combat feature поверх Stage-13 temporary resolver.

Не начинать Stage 18 advanced tactical AI до Stage 17.5 COMPLETE. Не превращать v1.0 authoring calibration values в «реальную физическую истину»: frozen частью являются architecture/units/budgets/interfaces, а конкретные fictional/material balance coefficients могут калиброваться внутри модели.