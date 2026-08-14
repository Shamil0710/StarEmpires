# Star Empires — Development Roadmap

> Canonical core-development status and transition document.
>
> Last synchronized: **2026-08-14** after Stage 14C merge.
>
> Detailed historical roadmap before Stage 11: `docs/archive/development_roadmap_pre_stage11_2026-08-13.md`.
> Completion records: `docs/stage11_autonomous_faction_expansion.md`, `docs/stage12_playable_actor.md`, `docs/stage13_combat_vertical_slice.md`, `docs/stage14a_player_mining.md`, `docs/stage14b_ship_progression.md`, `docs/stage14c_playable_navigation.md`.
> Presentation/navigation plan: `docs/ui_navigation_roadmap.md`.
> Cross-cutting plans: `docs/ai_behavior_roadmap.md`, `docs/cumulative_route_risk_model.md`, `docs/flight_dynamics_and_combat_depth_roadmap.md`, `docs/ship_pricing_roadmap.md`.

---

## 1. Project goal and invariant

**Star Empires** is a 2D top-down space sandbox-RPG/strategy with a living economy and a world that continues to exist independently of the player.

Progression target:

```text
one ship
→ trader / miner / mercenary
→ several ships
→ company and autonomous fleets
→ owned stations
→ player faction
→ territory, diplomacy and war
→ regional / galactic power
```

Primary invariant: **player and AI reuse the same physical world rules whenever possible.** No separate player economy, passive-income substitute, virtual delivery, instant construction/travel, scripted replacement or hidden resource grant without an explicit design decision.

---

## 2. Production stack

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

Stage 8.5 decision remains **`KEEP_LIBGDX`**. Reconsider the presentation stack only after a new measured fundamental limitation appears.

---

## 3. Milestones

| Milestone | Goal | Stages | Status |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | correct/scalable economic core | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | multi-system factions, construction, logistics, autonomous expansion | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | player ship, travel, trade, mining/combat, first progression loop | 12–14 | **ACTIVE — Stage 14E** |
| **v0.4 Fleet & Empire Sandbox** | player fleets, stations, faction, strategic war | 15–18 + 17.5 | PLANNED |
| **v0.5 RPG & Living World** | exploration, NPC, missions, reputation | 19–20 | PLANNED |
| **v0.6 Content & Balance Alpha** | breadth + long-run stability | 21 | PLANNED |
| **v0.7 Polish / Release Candidate** | UX, onboarding, performance, save hardening | 22 | PLANNED |

Repository-administration debt remains: mandatory branch protection for `main` is not configured through the available connector API. Functional CI gates remain mandatory before every core merge.

---

# MILESTONE v0.1 — ECONOMIC SANDBOX

**COMPLETE.**

## Stage 0 — Repository health

**COMPLETE — PR #1.** Clean JDK-17 build, JUnit, JaCoCo, strict Javadoc, runnable shaded desktop JAR and GitHub Actions.

## Stage 1 — Deterministic time

**COMPLETE — PR #2.** Fixed step `0.1s`, pause/time scale, named deterministic RNG streams, explicit system ordering and FPS-independent result.

## Stage 2 — Money / economic invariants

**COMPLETE — PR #3.** Authoritative `long` milli-credits, finite liquidity, atomic bilateral trade, EconomicLedger and explicit source/sink/transform/transfer semantics.

## Stage 3 — Identity / persistence

**COMPLETE — PR #4.** Stable EntityId, versioned state, bounded codecs, safe replacement and deterministic continuation tests.

## Stage 4 — Data-driven content

**COMPLETE — PR #5.** Versioned JSON catalog with stable content IDs, items, recipes, factions, ships, stations, validation, fingerprint and save binding.

## Stage 5 — Local logistics / route planning

**COMPLETE — PR #6.** Pure bounded TradeRoutePlanner, immutable MarketDirectory, profit/time scoring, deterministic tie-breaks and stale-route policy.

## Stage 6 — Headless scalability / observability

**COMPLETE — PR #7/#8.** 100 stations / 500 economic agents / 100 simulated hours, accounting diagnostics, supply-chain failure detection and machine-readable benchmark reports.

### v0.1 DoD

Economic core is deterministic, physically conserves money/goods under transfer rules, saves with stable identity, scales headlessly and exposes measurable supply-chain failures. **Completed.**

---

# MILESTONE v0.2 — LIVING GALACTIC ECONOMY

**COMPLETE.**

## Stage 7 — World hierarchy / simulation levels

**COMPLETE — PR #9.** `Galaxy -> Sector -> StarSystem`, stable typed IDs, topology, WorldState, one full-rate active system and bounded remote strategic updates.

## Stage 8 — Factions as economic actors

**COMPLETE — PR #10.** Treasury, budgets, subsidies, diplomacy, territory, market access, taxes/tariffs, strategic demand and persistence. Faction policy moves real money/resources.

## Stage 8.5 — Graphics / technology validation

**COMPLETE — `KEEP_LIBGDX`.** Production-like sprite/VFX pipeline, presentation/simulation separation, real-GPU validation and Java-17 CI.

## Stage 9 — Dynamic economy

**COMPLETE.**

- **9A Lifecycle:** deterministic create/remove and persistence.
- **9B Construction:** real project funding, material demand/delivery and build time.
- **9C Destruction:** physical removal, explicit cargo/resource fate and economic shock.
- **9D Bottleneck response:** shortage analysis, pressure hysteresis and AI investment.
- **9E Resilience:** critical producer loss -> shortage -> investment -> physical replacement -> recovery.

Stage 9 DoD: economy can physically degrade, diagnose a bottleneck, invest and recover without scripted respawn. **Completed.**

## Stage 10 — Inter-system logistics

**COMPLETE — PR #23, main `9aeddb8`.**

- **10A:** persistent world-level FleetId; local EntityId may change between systems.
- **10B:** authoritative jump FSM with deterministic timing and mid-transit persistence.
- **10C:** weighted multi-hop galactic path/trade scoring using Stage-5 economics.
- **10D:** bounded topology/sector market discovery with deterministic candidate order and revision invalidation.
- **10E:** real supplier purchase -> persistent fleet transit -> live destination revalidation -> physical sale; unsold cargo remains aboard.

Stage 10 DoD: resources physically cross StarSystems aboard persistent fleets and route choice respects time, access, cargo, liquidity and tariffs. **Completed.**

## Stage 11 — Autonomous faction expansion

**COMPLETE — PR #24–#27.** Technical record: `docs/stage11_autonomous_faction_expansion.md`.

- **11A — PR #24, main `4cbc83e`:** deterministic bounded opportunity ranking from real territory, jump time, resources, market pressure, construction cost, treasury and diplomacy.
- **11B — PR #25, main `cea8562`:** persistent StrategicGrowthState.Plan, lifecycle, stable PlanId, support fleets, budget and migration-safe persistence.
- **11C — PR #26, main `ff1a4bc`:** real faction fleet buys construction materials, jumps between systems, delivers them to an ordinary Stage-9 project and claims only after physical completion.
- **11D — PR #27, main `f5b58c7`:** deterministic competition by physical project completion timing + stable PlanId tie-break; no automatic conquest of foreign territory.

Stage 11 DoD: faction independently chooses an economically/strategically justified unclaimed system, persists intent, allocates real budget/fleet, transports physical resources, builds a real station and establishes a new node. **Completed.**

### v0.2 end-to-end

```text
living multi-system economy
→ destruction / shortage
→ AI investment and recovery
→ physical inter-system logistics
→ frontier evaluation
→ persistent expansion plan
→ real construction supply
→ new station / economic node
→ deterministic territorial growth
```

**v0.2 complete.**

---

# MILESTONE v0.3 — PLAYABLE SPACE SANDBOX

**ACTIVE.** Stages 12 and 13 are complete; Stage 14A, Stage 14B and Stage 14C are complete; **Stage 14E is the current core slice before final Stage 14D acceptance.**

## Stage 12 — Player State, Ownership, Travel and Manual Trade

**COMPLETE — PR #29–#32. Final functional main `89da8dc`.**

Technical record: `docs/stage12_playable_actor.md`.

### 12A — Persistent player state

**COMPLETE — PR #29, main `3a7efe1`.**

- PlayerState: wallet, reputation, optional affiliation, owned/active FleetIds, discoveries and home system.
- PlayableWorldState is an envelope above WorldState; the independent world remains player-agnostic.
- bounded playable codec and atomic save;
- raw pre-Stage-12 WorldState migrates with `playerState = null`, never with fabricated assets;
- deterministic continuation coverage.

### 12B — Ownership

**COMPLETE — PR #30, main `998f373`.**

- player ownership is independent of faction/legal membership;
- buy/sell transfers existing FleetId ownership and real money atomically;
- no entity duplication or respawn;
- physical destruction reconciles owned/active FleetIds.

### 12C — Direct control / docking / travel

**COMPLETE — PR #31, main `342659b`.**

- transient intent-driven input;
- physical Transform changes only in `PlayerDirectControlSystem` fixed ticks;
- direct control suppresses conflicting autonomous TradeAI/mining on the active ship;
- read-only PlayerShipView for camera/HUD/selection;
- docking requires physical range and persists;
- playable schema v2 migrates v1 as undocked;
- pause/time scale reuse SimulationClock;
- jump uses the existing Stage-10 WorldSimulation jump FSM;
- same FleetId survives destination materialization and the active system follows it.

### 12D — Manual market interaction

**COMPLETE — PR #32, main `89da8dc`.**

- PlayerMarketService reuses the same authoritative TradeController as AI;
- physical cargo stays on the real ship InventoryComponent;
- station stock/wallet/market remain ordinary economic components;
- player wallet/reputation/affiliation are adapted through a synchronous non-persistent proxy;
- access, prices, liquidity, capacity and ledger behavior are shared with AI;
- read-only PlayerMarketView exposes future UI data.

Final PR #32 CI #866 passed **407/407 tests**, coverage, strict Javadoc and desktop packaging.

### Stage 12 DoD

Accepted physical loop:

```text
own existing FleetId
→ direct fixed-tick flight
→ physical dock
→ manual buy via TradeController
→ real cargo aboard ship
→ undock
→ Stage-10 jump
→ same FleetId in second system
→ direct flight + dock
→ manual sell via TradeController
→ destination stock changes
→ wallet/reputation/cargo persist through save/load
```

No virtual delivery, player-only price formula, instant travel or UI Transform mutation. **Stage 12 completed.**

## Stage 13 — Combat Vertical Slice

**COMPLETE — PR #35. Functional merge main `8023b780`.**

Technical record: `docs/stage13_combat_vertical_slice.md`.

- canonical content has data-driven weapon definitions and `ship.guard_frigate` references `weapon.pulse_laser_mk1`;
- shared `CombatCommandComponent` carries target/fire intent for both player and AI;
- shared `CombatRuntimeComponent` carries weapon/cooldown runtime state;
- `CombatController` is the single range/cooldown/shield/hull damage boundary;
- `CombatAISystem` chooses only a target and uses deterministic nearest + lowest-EntityId tie-break;
- `PlayerRuntime` exposes target/fire commands without applying damage directly;
- lethal shots are queued out of Ashley iteration and resolved through ordinary `WorldSimulation.destroyEntity(..., DestructionPolicy.salvageResources())`;
- physical FleetId loss and resource salvage are observable consequences;
- no scripted respawn, replacement grant or player-only damage formula.

Final PR #35 CI #915 passed **412/412 tests**, JaCoCo, strict Javadoc and desktop packaging.

### Stage 13 DoD

A player-controlled ship and an AI ship can enter the same physical combat model, acquire targets, exchange deterministic data-driven weapon fire, validate range/cooldown, damage shields/hull, destroy a real asset through the ordinary world destruction pipeline and expose a physical salvage/loss consequence. **Completed.**

Deliberate seams remain for later stages: projectile/VFX breadth, tactical pursuit and fleet doctrine, diplomacy-aware rules of engagement, high-fidelity remote warfare, persistent sub-second cooldown/target intent and a complete player-facing combat HUD.

## Stage 14 — First Complete Player Economic Loop

**ACTIVE — current core stage.**

Goal: convert the proven travel/trade/mining/combat/progression primitives into the first coherent hour of actual play, while improving navigation/readability enough that a human can understand and evaluate the loop.

Target loop:

```text
explore / read local situation
→ find opportunity
→ trade / mine / fight
→ earn or preserve real credits/resources
→ buy / switch / improve a real ship
→ take a larger opportunity
```

### 14A — Player mining

**COMPLETE — PR #39, main `f652b2aa`.** Technical record: `docs/stage14a_player_mining.md`.

- `MiningCommandComponent` carries transient target/extraction intent and readable rejection/status reasons;
- `PlayerMiningService` is a command/read adapter and never mutates Transform, inventory, asteroid reserve or wallet directly;
- `PlayerMiningView` exposes target reserve/distance/range, real cargo/free capacity and current mining status for later HUD use;
- player and autonomous miners reuse one physical `MiningSystem` extraction boundary;
- manual mining consumes finite `AsteroidComponent.remainingResource` and puts the same whole units into the active ship's real `InventoryComponent`;
- a player-controlled miner is never moved, returned to base or auto-sold by `MiningSystem`;
- mining itself does not award money; credits are earned only through ordinary `PlayerMarketService` / `TradeController` sale;
- persistent cargo and asteroid reserve survive save/load while transient manual command/target intentionally do not resume.

PR #39 CI #942 passed **418/418 tests**, JaCoCo, strict Javadoc and desktop packaging.

### 14B — Ship purchase / active-ship progression

**COMPLETE — PR #41, functional main `0b9df2d3`.** Technical record: `docs/stage14b_ship_progression.md`.

- `PlayerShipSaleOffer` identifies a real seller station, an already-existing physical FleetId and an explicit positive price;
- `PlayerShipPurchaseView` exposes readable eligibility/rejection status without mutating the world;
- `PlayerShipProgressionService` revalidates physical docking, seller market/wallet/faction, live in-system fleet, transit state, seller/ship faction match and affordability;
- successful purchase delegates the atomic money + ownership transfer to the existing Stage-12 `PlayerOwnershipService`;
- the real player wallet decreases and the real seller-station wallet increases; no player-only ship-value money source/sink exists;
- purchase never spawns, clones, relocates or resets the offered ship, its cargo or its identity;
- active-owned-FleetId switching is separate from purchase and does not recreate or teleport an entity;
- switching requires the target to be owned, live, materialized and not in jump transit, and requires the player to be undocked;
- a moving current ship must be stopped through ordinary direct-control intent/fixed-tick processing before control can be released;
- transient combat/mining intents are cleared on the old active ship before rebinding direct control;
- ownership, active FleetId, wallet, candidate cargo and position survive save/load.

PR #41 final CI #960 passed **421/421 tests**, JaCoCo, strict Javadoc and desktop packaging.

The sale price is deliberately explicit at this stage. `docs/ship_pricing_roadmap.md` makes the future replacement mandatory: a shared valuation/shipyard layer must derive the quote from live material/component prices, actual fitting and condition when available, production complexity/ship type, local market pressure, seller-faction relationship and seller-faction commercial margin. The Stage-14B ownership/payment path remains the final transfer boundary.

### 14C — Playable navigation, HUD and readability baseline

**COMPLETE — PR #43, functional main `649224f8`.** Technical record: `docs/stage14c_playable_navigation.md`.

- bounded mouse-wheel world zoom follows the active physical ship while HUD scale stays screen-space and independent;
- `PlayableCameraState` holds bounded presentation zoom and is headless-tested;
- local minimap data is captured from authoritative ECS state through deterministic `LocalMinimapModel` / immutable `LocalMinimapSnapshot`;
- minimap displays player, stations, friendly/relevant fleets, temporary Stage-13 hostiles, asteroids and salvage;
- player ownership takes precedence over the temporary faction-hostility rule, so purchased FleetIds remain friendly even if their retained `FactionComponent` differs;
- nearest-hostile player target selection likewise excludes all locally materialized owned FleetIds;
- `PlayableMapEntityFilter` applies zoom-based declutter without changing discovery or simulation state;
- HUD exposes FleetId/owned count, credits, system, position, speed, cargo, docking/transit/time state;
- combat HUD reads real hull/shields/range/cooldown/target-distance/fire state from Stage-13 components;
- mining HUD reads real status/range/reserve/cargo/extraction data from Stage-14A views;
- market HUD reads live access, stock, cargo and buy/sell prices;
- `M/R`, `T/F` and `TAB` submit ordinary mining, combat and Stage-14B switching commands rather than mutating physical state from presentation code;
- GPU render/application shells remain outside core line coverage while camera/minimap classification/declutter/layout decisions are extracted into headless-tested logic.

PR #43 final CI #988 passed **426/426 tests**, strict Javadoc, JaCoCo line/branch gates and desktop shaded-JAR packaging.

The **full global galaxy/empire map is not a Stage-14 requirement**. Its functional layers grow with Stage 15–18 fleet/empire systems, while exploration/mission layers arrive with Stage 19–20.

### 14E — Flight-dynamics baseline

**ACTIVE — current core slice before Stage 14D.**

Target from `docs/flight_dynamics_and_combat_depth_roadmap.md`:

- representative hull dry mass;
- cargo contributes physical mass;
- thrust-limited acceleration rather than instantaneous velocity assignment;
- non-instant braking;
- player and equivalent AI/local movement share the same physical limits;
- deterministic proof that a light ship and a loaded freighter accelerate/stop differently;
- speed/mass/acceleration/braking diagnostics sufficient for the Stage-14C HUD and tuning.

This is deliberately a game-friendly inertial model with flight assist allowed; assistance may choose thrust but may not bypass acceleration/braking limits. The implementation must not create a player-only physics path: player and AI express movement intent, while one shared authoritative flight layer determines achievable velocity/position changes.

### 14D — First-hour acceptance and telemetry

**PLANNED immediately after Stage 14E.**

Build one internal playable scenario without debug income/resource grants that exercises the complete loop and records at least:

- credits/hour and net profit/loss;
- trade/mining/fight contributions;
- travel and idle downtime;
- cargo utilization;
- damage/losses;
- time/opportunity cost to reach the first real ship-progression event.

The acceptance must prove that the same physical world and economy continue running around the player throughout the loop.

### v0.3 DoD

First internal playable hour without debug grants: travel + economy + finite mining + combat + real ship progression, with camera zoom, a readable HUD, a functional local minimap and the shared inertial movement baseline sufficient to understand and test what is happening on screen.

---

# MILESTONE v0.4 — FLEET & EMPIRE SANDBOX

**PLANNED.**

## Stage 15 — Player fleets / autonomous orders

Persistent grouping and orders: HOLD, MOVE, TRADE, MINE, ESCORT, PATROL, FOLLOW. Owned autonomous economic fleets reuse existing planners rather than passive-income formulas.

This is the first major civilian/strategic behavior phase from `docs/ai_behavior_roadmap.md`:

- civilian task interruption and flee/resume/replan behavior;
- economic route utility combines profit/time with actor-specific danger cost;
- route risk accumulates across every traversed system and jump/link rather than inspecting destination danger alone;
- escort strength, cargo/ship value, damage and mobility affect willingness to accept danger;
- fleet cohesion/order execution respects the mobility of protected/critical ships.

Begin the functional global-map layer for owned fleet selection, movement/order context and known-system navigation using the existing galaxy topology/route planner rather than a UI-only route model.

## Stage 16 — Player construction / station ownership

Player uses the same Stage-9 ConstructionProject: chooses archetype/system/location/funding, delivers materials manually or through owned logistics, and receives a real operating station. Global-map/management UI gains owned stations, projects and supply requirements from authoritative state.

Risk-aware construction logistics may reroute, request escort or suffer physically longer supply times through dangerous corridors rather than receiving abstract wartime penalties.

## Stage 17 — Player faction

Data-driven founding requirements followed by reuse of Stage-8 treasury, territory, relations, access, taxes, subsidies and strategic policies. Global-map layers gain faction/territory context.

Introduce data-driven faction doctrine for civilian risk tolerance, escort preference and broad aggression/retreat policy. Faction differentiation should come from decisions/policy rather than hidden combat bonuses.

## Stage 17.5 — Combat Depth / Ship Fitting Foundation

**PLANNED prerequisite gate before advanced tactical combat AI.**

Do not build final sophisticated weapon-aware AI against the temporary Stage-13 combat envelope. Establish enough stable combat depth first:

- several materially different ship/hull classes;
- armor mechanics beyond generic hull HP;
- richer shield capacity/recharge/delay/overload or damage-interaction rules as justified;
- several weapon categories with distinct range/use envelopes;
- equipment/fitting foundation that can alter mobility, defense or weapons;
- equipment/armor/cargo mass integration with the shared flight model;
- stable authoritative combat-capability query data;
- deterministic enriched-combat acceptance tests.

See `docs/flight_dynamics_and_combat_depth_roadmap.md` for the advanced tactical-AI activation gate.

## Stage 18 — Strategic warfare / territory / politics

Stage 18 combines warfare with advanced combat behavior only after the Stage-17.5 gate is satisfied:

- formal hostility/war/peace;
- defend/attack/escort/blockade/capture objectives;
- advanced weapon/range/mobility-aware tactical AI over the enriched combat model;
- coordinated retreat, pursuit, escort/screen/intercept and fleet doctrine;
- military construction and replacement logistics;
- faction-shared threat intelligence with confidence/freshness/decay;
- runtime jump-route availability and whole-route cumulative risk shared by planner + executor;
- civilian war-zone avoidance and military objective-vs-risk decisions;
- blockade -> physical traffic rerouting/throughput loss -> shortage/price/economic response benchmark;
- explicit territory transition rule;
- Stage-11 economic outposts integrate with military conquest here;
- global map exposes fleets, territory, fronts, route risk/blockades and relevant economic overlays without replacing authoritative planners.

### v0.4 DoD

Player grows from one ship into autonomous fleets/stations/faction and wages wars whose results change physical assets, trade routes, supply chains and territory. Civilian and military AI respond to known danger through shared physical movement/logistics rules rather than abstract global penalties.

---

# MILESTONE v0.5 — RPG & LIVING WORLD

**PLANNED.**

## Stage 19 — Exploration / discovery / world generation

Persistent discovered systems/routes/stations/resources; deterministic seed-driven galaxy generation; faction/economy bootstrap; data-driven anomalies, derelicts and special locations. Global/local maps respect discovery state and add exploration/anomaly layers.

Threat/routing decisions respect information availability: actors do not become omniscient merely because the global simulation knows about a distant conflict.

## Stage 20 — NPC / missions / reputation / progression

Persistent NPCs only where identity matters. Missions arise from actual world state: haul, deliver, mine, escort, bounty, investigate, defend. Shortage, loss, expansion, war and discovery should create dynamic contracts. Reputation connects to access, contracts and faction relations. Map/UI layers may expose known mission/objective context without leaking undiscovered state.

Persistent commanders may specialize faction doctrine through bounded personality/risk/aggression preferences without gaining knowledge they do not possess.

---

# MILESTONE v0.6 — CONTENT & BALANCE ALPHA

**PLANNED.**

## Stage 21 — Content breadth / balance / long-run stability

Expand resources, intermediates, components and civilian/military goods only after the corresponding mechanics are stable. Build coherent ship/station ecosystems and faction differentiation through real starting conditions, policies and doctrine rather than hidden bonuses.

Full-world benchmarks must include inter-system trade, construction, faction expansion, combat losses, player-owned assets and long duration. Detect inflation/deflation, dead economies, permanent shortages, uncontrolled entity/ledger growth, route-planner scaling and faction snowball.

AI scenario/soak matrices must also detect pathological risk and doctrine behavior: civilians never travelling, civilians ignoring obvious wars, engage/retreat oscillation, escorts abandoning convoys, danger that never decays and route avoidance that permanently kills an economy without recovery paths.

---

# MILESTONE v0.7 — POLISH / RELEASE CANDIDATE

**PLANNED.**

## Stage 22 — UX / onboarding / performance / release hardening

- unify and polish the HUD/management UI established earlier rather than creating it for the first time here;
- production-quality map layers, filters/search, notifications, input discoverability and accessibility/scaling;
- onboarding for first trade/mining/combat/fleet/station;
- autosave/backup/corrupt-save UX and supported migration window;
- performance profiling of large combat, many remote systems, route planning, large asset lists and save/load;
- `BloomMode = OFF / LIGHT / FULL` production settings and baselines;
- clean build, regression, soak and save/load-soak release gates.

---

# 4. Parallel Visual / UX Track

Visual work may proceed in parallel but never substitutes a functional stage DoD.

- **V1 Ship sprite pipeline:** grounded top-down language, size grammar, hardpoints, pivots/collision conventions.
- **V2 Engine/movement animation:** idle/thrust/maneuver from real movement state; Stage 14E ties presentation to actual acceleration/braking state rather than decorative speed alone.
- **V3 Station language:** construction, industrial, mining, trade, military, colony and faction differentiation.
- **V4 Combat VFX:** weapons, shields/hits/destruction/salvage and benchmarked BloomMode tiers; Stage 13 establishes mechanics, later combat-depth stages improve presentation.
- **V5 Playable navigation/readability:** **Stage 14C baseline complete** — camera zoom, unified HUD and local minimap with mining/combat/economy context; later stages deepen UX rather than creating it from scratch.
- **V6 Strategic map / empire UI:** topology/navigation first, then territory, fleets, trade flows, shortages, cumulative route danger and wars alongside Stage 15–18.

Gameplay never depends on a particular sprite asset. Presentation metadata remains data-driven over simulation archetypes.

---

# 5. Cross-cutting engineering rules

## Persistence

Every persistent domain object defines stable identity, schema/file-format ownership, bounded codec, migration policy and continuation tests.

## Determinism

Every planner/AI uses deterministic iteration/tie-breaks. RNG is named and used only when randomness is an explicit design requirement.

## Economic conservation

Every money/resource mutation uses transfer/source/sink/transform semantics and has ledger/invariant coverage. No hidden income/resource creation.

Ship purchase quotes eventually come from the shared live-economy valuation model in `docs/ship_pricing_roadmap.md`; the quote may include data-driven seller margin/relationship effects but the purchase itself must still conserve the exact quoted amount between real wallets.

## Physicality

Construction, trade, mining, progression, expansion and warfare use real entities, finite resources/cargo, wallets, travel and build time. Remote simulation may reduce fidelity but may not invent an incompatible economy.

## Shared player/AI core

Player-facing commands adapt to common simulation controllers. A separate player-only implementation requires explicit justification and tests proving invariants are still shared.

## Movement physicality

Local player/AI movement uses shared mass/thrust/acceleration limits once Stage 14E lands. Flight assist may simplify intent but may not grant instantaneous acceleration/braking unavailable to equivalent AI ships. Cargo/equipment/armor affect mobility through authoritative physical data as those systems exist.

## AI information and route risk

AI danger decisions use available observations/intelligence rather than automatic global omniscience. Strategic route risk evaluates the full traversed route — systems and links — with actor-specific exposure/vulnerability rather than destination danger alone.

## Presentation read-only boundary

HUD, minimap and global-map layers may read authoritative simulation/player state and submit ordinary commands, but they may not mutate physical/economic/combat/mining/ownership state directly or introduce UI-only gameplay rules.

## Observability / performance

Major systems require measurable metrics and benchmarks. Optimize from evidence or where algorithmic scaling is structurally unacceptable; avoid speculative micro-optimization.

---

# 6. Stage transition rules

1. `main` remains stable.
2. New core work starts from current green `main`.
3. Broken CI blocks merge and stage transition.
4. Each stage has an explicit vertical slice and DoD.
5. Persistent changes require migration/continuation coverage.
6. Economic changes require conservation/invariant coverage.
7. Deterministic decision code requires tie-break coverage.
8. Player and AI reuse common simulation APIs unless a separate path is explicitly justified.
9. Do not expand content breadth before mechanics stabilize.
10. UI/map layers remain read-only views + command adapters over authoritative state.
11. Advanced tactical combat AI does not begin before the combat-depth/flight-dynamics capability gate is satisfied.
12. Strategic danger-aware routing scores the entire traversed path, not only its destination.
13. Ship-sale valuation must eventually be derived from live economic/component/fitting/faction inputs rather than an arbitrary player-only price formula.
14. Update this roadmap only after factual completion/merge evidence exists.

---

# 7. Current next step

**ACTIVE: Stage 14E — Shared Mass / Thrust / Inertial Flight Baseline.**

Stage 14A is complete at PR #39 / main `f652b2aa` with CI #942 passing 418/418 tests. Stage 14B is complete at PR #41 / functional main `0b9df2d3` with CI #960 passing 421/421 tests. Stage 14C is complete at PR #43 / functional main `649224f8` with CI #988 passing 426/426 tests.

Immediate implementation order:

1. audit current ship-archetype movement data, `PlayerDirectControlSystem`, AI/local movement writers, Transform persistence and Inventory cargo seams;
2. introduce authoritative hull dry mass and propulsion/thrust capability with bounded content validation while preserving stable content IDs;
3. give cargo real mass contribution from item data so the same freighter can handle differently empty vs loaded without a scripted fullness penalty;
4. introduce one shared fixed-tick flight controller that turns player/AI movement intent into physically achievable acceleration/braking instead of instantaneous velocity assignment;
5. preserve game-friendly flight assist, but make braking require counter-thrust/time and forbid the assist from bypassing mass/thrust limits;
6. add deterministic acceptance proving a light ship accelerates/stops faster than a heavy ship and the same freighter accelerates/stops worse when loaded;
7. expose current total mass, speed, acceleration capability and useful braking diagnostics through the Stage-14C HUD/read model;
8. keep travel/jump/trade/mining/combat/persistence regression suites green and verify that movement changes do not create a player-only physics path;
9. after Stage 14E, assemble Stage 14D first-hour scenario + telemetry across trade/mining/combat/progression;
10. only after Stage 14 DoD close milestone v0.3 and begin Stage 15 player fleets/orders + cumulative route-risk civilian behavior + first functional global-map layer.

Do not expand advanced tactical combat AI or the full empire/global map now. The immediate core goal is **making ship mass, cargo load, acceleration and braking physically meaningful under one shared player/AI movement model**.
