# Star Empires — дорожная карта разработки

> Канонический документ статуса, зависимостей и переходов между этапами разработки.
>
> Последняя синхронизация: **2026-08-15 после принятия `Ship Mathematics v1.0 Design Baseline` (PR #91, CI #1516, merge `3ec2f6cab286dbcd39694c19a055d038c175b59c`) и подробной post-v1 декомпозиции Stage 17.5 / 19 / 21. Фактический runtime-статус остаётся Stage 17 ACTIVE.**
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

Stage 17 переиспользует Stage-8 faction core: treasury, budgets, subsidies, relations, territory, access, tariffs/taxes, policies и persistence.

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

Own station не равна sovereignty без ordinary territorial rule. Player использует те же control/access mechanics, что AI factions.

## 17E — diplomacy / market access / tariffs

Ordinary relations/access state и общий policy boundary.

## 17F — faction policies / strategic economy

Stage-8 budgets/subsidies/strategic demand доступны player UI через command layer, но реальные деньги/resources двигаются ordinary economy.

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

# Stage 18 — strategic warfare + advanced combat behavior

**PLANNED после Stage 17.5 COMPLETE.**

- formal war/peace/hostility;
- fronts/blockades/territorial objectives;
- weapon/range/mobility/sensor-aware tactical AI;
- escort/screen/intercept/retreat/pursuit;
- formation doctrine based on physical geometry;
- replacement/ammunition/repair logistics;
- shared threat intelligence confidence/freshness/decay;
- conflict-driven traffic rerouting/economic consequences;
- strategic map overlays.

Advanced AI consumes Stage-17.5 capability queries; it не получает omniscience и не дублирует combat physics.

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