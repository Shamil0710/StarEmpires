# Star Empires — Development Roadmap

> Canonical core-development status and transition document.
>
> Last synchronized: **2026-08-14 after Stage-15 completion, PR #51 inertial/jump hardening and PR #52 documentation merge**.
>
> Historical detail before Stage 11: `docs/archive/development_roadmap_pre_stage11_2026-08-13.md`.
> Major completion records: `docs/stage11_autonomous_faction_expansion.md`, `docs/stage12_playable_actor.md`, `docs/stage13_combat_vertical_slice.md`, `docs/stage14a_player_mining.md`, `docs/stage14b_ship_progression.md`, `docs/stage14c_playable_navigation.md`, `docs/stage14_complete_player_economic_loop.md`, `docs/stage15_player_fleets.md`, `docs/post_stage15_inertial_jump_hardening.md`.
> Cross-cutting plans: `docs/ui_navigation_roadmap.md`, `docs/ai_behavior_roadmap.md`, `docs/cumulative_route_risk_model.md`, `docs/flight_dynamics_and_combat_depth_roadmap.md`, `docs/ship_pricing_roadmap.md`, `docs/stage16_construction_timing.md`.

---

# 1. Project goal and invariant

**Star Empires** is a 2D top-down space sandbox-RPG/strategy with a living physical economy and a world that continues to exist independently of the player.

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

Primary invariant: **player and AI reuse the same physical world rules whenever practical.**

No separate player economy, passive-income substitute, virtual delivery, instant travel/construction, hidden resource grant, scripted replacement or player-only combat/movement formula without an explicit justified design decision.

---

# 2. Production stack

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

Stage 8.5 decision remains **`KEEP_LIBGDX`**. Reconsider presentation technology only after a new measured fundamental limitation appears.

---

# 3. Milestones

| Milestone | Goal | Stages | Status |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | correct/scalable economic core | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | multi-system factions, logistics, construction, autonomous expansion | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | player ship, travel, trade, mining/combat, real ship progression, readable local play | 12–14 | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | player fleets, stations, faction, strategic war | 15–18 + 17.5 | **ACTIVE — Stage 16** |
| **v0.5 RPG & Living World** | exploration, NPC, missions, reputation | 19–20 | PLANNED |
| **v0.6 Content & Balance Alpha** | breadth + long-run stability | 21 | PLANNED |
| **v0.7 Polish / Release Candidate** | UX, onboarding, performance, save hardening | 22 | PLANNED |

Repository-administration debt remains: mandatory branch protection for `main` is not configurable through the currently available connector action. Functional CI gates remain mandatory before every core merge.

---

# MILESTONE v0.1 — ECONOMIC SANDBOX

**COMPLETE.**

## Stage 0 — Repository health

**COMPLETE — PR #1.** Clean Java-17 build, JUnit, JaCoCo, strict Javadoc, runnable shaded desktop JAR and GitHub Actions.

## Stage 1 — Deterministic time

**COMPLETE — PR #2.** Fixed step `0.1s`, pause/time scale, named deterministic RNG streams, explicit system ordering and FPS-independent simulation results.

## Stage 2 — Money / economic invariants

**COMPLETE — PR #3.** Authoritative integer milli-credits, finite liquidity, atomic bilateral trade, `EconomicLedger` and explicit source/sink/transfer/transform semantics.

## Stage 3 — Identity / persistence

**COMPLETE — PR #4.** Stable `EntityId`, versioned state, bounded codecs, safe replacement and deterministic continuation tests.

## Stage 4 — Data-driven content

**COMPLETE — PR #5.** Versioned JSON catalog with stable content IDs, items, recipes, factions, ships, stations, validation, fingerprint and save binding.

## Stage 5 — Local logistics / route planning

**COMPLETE — PR #6.** Pure bounded `TradeRoutePlanner`, immutable market directory, profit/time scoring, deterministic tie-breaks and stale-route policy.

## Stage 6 — Headless scalability / observability

**COMPLETE — PR #7/#8.** Large headless economic benchmark, accounting diagnostics, supply-chain failure detection and machine-readable reports.

### v0.1 DoD

The economic core is deterministic, conserves money/goods under explicit rules, saves with stable identity, scales headlessly and exposes measurable failures. **Completed.**

---

# MILESTONE v0.2 — LIVING GALACTIC ECONOMY

**COMPLETE.**

## Stage 7 — World hierarchy / simulation levels

**COMPLETE — PR #9.** `Galaxy -> Sector -> StarSystem`, typed stable IDs, topology, `WorldState`, one full-rate active system and bounded remote strategic updates.

## Stage 8 — Factions as economic actors

**COMPLETE — PR #10.** Treasury, budgets, subsidies, diplomacy, territory, market access, taxes/tariffs, strategic demand and persistence. Policies move real money/resources.

## Stage 8.5 — Graphics / technology validation

**COMPLETE — `KEEP_LIBGDX`.** Production-like sprite/VFX seam, presentation/simulation separation, real-GPU validation and Java-17 CI.

## Stage 9 — Dynamic economy

**COMPLETE.**

- lifecycle create/remove and persistence;
- construction with real funding/materials/build time;
- destruction with physical loss/salvage/economic shock;
- bottleneck diagnosis and investment response;
- replacement/recovery benchmark after producer destruction.

Stage 9 DoD: economy can physically degrade, diagnose a bottleneck, invest and recover without scripted respawn. **Completed.**

## Stage 10 — Inter-system logistics

**COMPLETE — PR #23.**

- persistent world-level `FleetId`;
- authoritative jump FSM with deterministic timing and mid-transit persistence;
- weighted multi-hop galactic routing;
- bounded discovery/revision invalidation;
- real supplier purchase → fleet transit → destination revalidation → physical sale;
- unsold cargo remains aboard.

## Stage 11 — Autonomous faction expansion

**COMPLETE — PR #24–#27.** Technical record: `docs/stage11_autonomous_faction_expansion.md`.

- deterministic opportunity ranking;
- persistent strategic growth plans;
- real faction budget/fleet/material transport;
- ordinary Stage-9 construction;
- deterministic physical competition;
- no automatic conquest shortcut.

### v0.2 end-to-end

```text
living multi-system economy
→ destruction / shortage
→ AI investment and recovery
→ physical inter-system logistics
→ persistent expansion plan
→ real construction supply
→ new station / economic node
→ deterministic territorial growth
```

**v0.2 complete.**

---

# MILESTONE v0.3 — PLAYABLE SPACE SANDBOX

**COMPLETE.**

Detailed Stage-14 closure: `docs/stage14_complete_player_economic_loop.md`.

## Stage 12 — Player State, Ownership, Travel and Manual Trade

**COMPLETE — PR #29–#32.** Technical record: `docs/stage12_playable_actor.md`.

Key result:

- player state is an envelope above player-agnostic `WorldState`;
- ownership is independent from faction membership;
- player directly controls an existing `FleetId` through fixed-tick intent;
- docking requires physical range;
- travel reuses Stage-10 jump FSM;
- manual trade reuses the same `TradeController` as AI;
- real cargo remains in the real ship inventory;
- wallet/ownership/discovery/docking persist through save/load.

## Stage 13 — Combat Vertical Slice

**COMPLETE — PR #35.** Technical record: `docs/stage13_combat_vertical_slice.md`.

- data-driven first weapon/hull combat data;
- shared player/AI target+fire command component;
- shared range/cooldown/shield/hull resolver;
- deterministic simple CombatAI target selection;
- lethal results go through ordinary world destruction/salvage;
- no player-only damage or combat reward path.

Advanced tactical combat AI remains intentionally deferred until richer movement/fitting/armor/shield/weapon capability exists.

## Stage 14 — First Complete Player Economic Loop

**COMPLETE — 14A PR #39, 14B PR #41, 14C PR #43, final 14D/14E PR #45.**

Functional final merge:

`0393eccf790269651bcedbdfd8e4eaf8b60ca06a`

Final remaining-slice validation: **CI #1010**, workflow run `31811876633`, **431/431 tests** plus strict Javadoc, JaCoCo and shaded desktop packaging.

### 14A — Player mining

**COMPLETE.**

- transient manual mining intent;
- finite real asteroid reserve;
- shared `MiningSystem` extraction;
- mined units go into active physical ship inventory;
- no mining-to-money shortcut;
- sale goes through ordinary market controller;
- persistent cargo/resource reserve survives save/load.

### 14B — Ship purchase / active-ship progression

**COMPLETE.**

- purchase transfers an already-existing real `FleetId`;
- real player wallet decreases and real seller wallet increases;
- no spawn/clone/teleport/reset on purchase;
- active-FleetId switching is separate from purchase;
- cargo/identity/position/ownership persist;
- explicit Stage-14 price is temporary; future live valuation is specified in `docs/ship_pricing_roadmap.md`.

### 14C — Playable navigation / HUD / local minimap

**COMPLETE.** Completion record: `docs/stage14c_playable_navigation.md`.

- bounded mouse-wheel zoom;
- active-ship follow;
- HUD scale independent from world zoom;
- local minimap from authoritative ECS state;
- ownership-aware marker classification;
- zoom declutter;
- readable economy/mining/combat feedback;
- presentation is read-only + ordinary commands only.

### 14E — Shared inertial flight baseline

**COMPLETE — PR #45, generalized for generic NPC local movement by PR #51.**

`PlayerDirectControlSystem` uses shared `FlightDynamics` instead of assigning velocity instantly.

Baseline dependency:

```text
dry hull / structure mass
+ real cargo mass
= total mass

thrust / total mass
= acceleration

braking thrust / total mass
= braking acceleration
```

Stage-14 compatibility choice: **1 cargo inventory unit = 1 normalized mass unit**. Per-item/equipment/armor/ammunition mass belongs to later fitting/content depth.

Implemented and validated:

- finite acceleration;
- finite braking/counter-thrust;
- loaded same-hull freighter responds worse than empty one;
- light combat ship responds better than loaded heavy carrier;
- direct player control and autonomous local movement share `FlightDynamics`;
- `PlayerFlightService/View` expose read-only speed/mass/acceleration/braking/stopping diagnostics;
- equivalent player/autonomous intent produces identical physical evolution in deterministic tests.

The former generic `TradeAISystem` / autonomous `MiningSystem` direct-position compatibility path was fully retired in PR #51. Generic local traders/miners now emit flight intent and use the same inertial integration boundary. New local movement code must not reintroduce direct `Transform.position`/impossible-velocity shortcuts.

### 14D — First-hour acceptance / telemetry

**COMPLETE — PR #45.**

Telemetry tracks without mutating gameplay:

- credits/hour and wallet delta;
- ordinary trade contribution;
- mined-cargo sale contribution;
- ship-purchase spending;
- travel/mining/combat/idle time;
- cargo utilization;
- damage/loss;
- time to first real ship progression.

Integrated deterministic acceptance executes:

```text
physical flight + dock
→ ordinary buy
→ undock + finite braking
→ Stage-10 jump
→ physical dock + ordinary sell
→ buy/switch real miner FleetId
→ finite asteroid mining
→ ordinary mined-cargo sale
→ buy/switch real combat FleetId
→ shared combat + destruction
→ continue living world to 3600 simulation seconds
→ save/load
→ continue inertial flight after restore
```

The acceptance intentionally does not reserve market capacity for the player. Live AI competition remains active during the test.

### v0.3 DoD result

A coherent first playable hour is mechanically proven without debug income/resource grants: physical travel, shared economy, finite mining, combat, real ship progression, readable local UI/minimap and persistent continuation all operate in one world. **v0.3 complete.**

---

# MILESTONE v0.4 — FLEET & EMPIRE SANDBOX

**ACTIVE — Stage 16.**

## Stage 15 — Player Fleets / Autonomous Orders

**COMPLETE — functional PR #47, #48 and #49; post-completion hardening PR #51.** Technical record: `docs/stage15_player_fleets.md` and `docs/post_stage15_inertial_jump_hardening.md`.

Goal achieved: the player can own several physical `FleetId`s, directly control one and delegate persistent real work to the others without passive-income abstractions or a second movement/economy implementation.

### 15A — Persistent fleet orders foundation

**COMPLETE.** Persistent deterministic orders with stable references:

- `HOLD`;
- `MOVE`;
- `TRADE`;
- `MINE`;
- `ESCORT`;
- `PATROL`;
- `FOLLOW`.

Order state survives save/load and references existing physical `FleetId`s.

### 15B — Shared inertial local order execution

**COMPLETE.**

```text
persistent fleet order / generic AI intent
→ FlightCommandComponent
→ AutonomousFlightSystem
→ FlightDynamics
→ authoritative Transform
```

Player-owned delegated fleets and generic local traders/miners now obey the same mass/thrust/acceleration/braking constraints as direct player control. Cargo affects autonomous mobility through the same mass calculation.

`DelegatedFleetComponent` prevents legacy/generic AI from overwriting Stage-15 order intent while a fleet is delegated and releases stale ownership if a former player fleet returns to generic AI.

### 15C — Autonomous economic orders

**COMPLETE.**

`TRADE` and `MINE` use real movement, Stage-10 jump transit, real inventories and existing authoritative economic/mining controllers. No passive income, virtual delivery or invented cargo.

### 15D — Civilian survival / replanning

**COMPLETE baseline.**

- observed attacks can interrupt civilian delegated work;
- flee uses shared physical movement;
- original persistent order is retained;
- deterministic hysteresis prevents immediate oscillation;
- work resumes after the observed threat clears.

### 15E — Cumulative whole-route risk

**COMPLETE baseline.**

Fundamental invariant:

> **Route danger is evaluated across the entire traversed route, not only the destination.**

Current exposure form uses known system/link intelligence with confidence/aging and actor-specific cargo, damage, mobility and real escort context. Danger scores remain exposure values, not fake probabilities.

### 15F — Escort / convoy / patrol / follow

**COMPLETE baseline.**

- `FOLLOW` resolves and physically follows the target FleetId;
- `ESCORT` requires a real co-located operational escort to reduce protected actor vulnerability and never erases raw route danger;
- `PATROL` retains a persistent waypoint cycle, physical dwell and real Stage-10 transit;
- no group order teleports members.

Advanced weapon-aware screening/cohesion tactics remain correctly gated behind Stage 17.5/18.

### 15G — First functional global-map layer

**COMPLETE baseline.**

The map exposes discovered topology, owned fleets, transit/order state and known threat intelligence only. It submits ordinary persistent commands and previews the same route planner used by execution. It cannot teleport fleets or reveal arbitrary hidden remote NPC state.

### Stage 15 DoD result

Multiple owned persistent FleetIds can receive saved autonomous orders, execute representative move/trade/mine/follow/escort/patrol behavior through shared movement/economy rules, react to observed danger, choose routes using cumulative whole-route risk and remain manageable through the first strategic map layer. **Stage 15 complete.**

### Post-Stage-15 hardening — PR #51

**COMPLETE.** Final hardening closed the generic-NPC movement debt and clarified player jump semantics.

- generic `TradeAISystem` and autonomous `MiningSystem` no longer directly move `Transform`;
- generic local NPC movement now shares `FlightDynamics`;
- same-hull cargo mass changes NPC acceleration exactly through the physical mass model;
- `FOLLOW`, `ESCORT` and `PATROL` execution and route-risk integration are covered by clean-build acceptance;
- `J` preserves the Stage-10 finite jump FSM rather than teleporting instantly;
- after arrival, the same `FleetId` materializes at the canonical local-system center `(1000, 700)` under the current map layout;
- active-system tracking follows the ship and camera projection centers it on screen;
- current Anchor → Corona demo travel takes about 13 fixed ticks (~1.3 simulation seconds at x1).

PR #51 validation: **CI #1151 / run `31826504541`, 454/454 tests**, strict Javadoc, JaCoCo and shaded desktop package all green.

## Stage 16 — Player construction / station ownership

**ACTIVE — current core stage.** Timing foundation already merged in PR #51; specification: `docs/stage16_construction_timing.md`.

Goal: let the player create and own persistent stations by using the ordinary Stage-9 physical construction pipeline: real funding, real materials, physical logistics, build time and a real resulting station. No instant UI construction and no virtual material delivery.

### 16A — Player project-authoring boundary

Implement a player-facing construction service that can:

- enumerate buildable station archetypes from authoritative content/unlocks;
- validate chosen system/location;
- calculate project funding and required material bill;
- create an ordinary persistent `ConstructionProject` rather than spawning a station;
- expose human-readable rejection reasons without UI-side rule duplication.

### 16B — Material-driven construction duration

**FOUNDATION IMPLEMENTED in PR #51.** New projects already derive duration from the actual authored material bill:

```text
materialWork = Σ(requiredAmount × constructionHandlingWeight(itemCategory))

buildTimeSeconds =
    baseSetupSeconds
  + materialWork / baselineAssemblyRate
```

Current normalized handling work values:

- `MATERIAL = 1.00`;
- `GAS_LIQUID = 0.55`;
- `FINISHED_GOODS = 1.60`;
- baseline assembly rate = `12 work units / simulation second`.

These are **construction handling/work units, not fake kilograms**. The old authored `buildSeconds` is retained as base setup/archetype-complexity allowance instead of representing the entire build duration.

The computed `buildDurationTicks` is persisted when a project is created. Later balance/content changes therefore do not rewrite the duration of construction already under way.

Example from the current content: `station.mining_base` has 180 required units / 153 weighted work; 25 seconds base setup + 12.75 seconds material assembly = **37.75 simulation seconds**.

### 16C — Physical funding/material delivery

Player projects must use the same physical project requirements as AI/factions:

```text
player/company funding
+ physically delivered required goods
→ project becomes build-ready
→ ordinary construction ticking
→ completion
```

Required materials must exist in real inventories/markets/fleets and be transferred through authoritative controllers. Do not convert missing materials directly into a money surcharge or silently source them from nowhere.

### 16D — Completion / ownership / persistence

On completion:

- create the ordinary station entity/archetype through the existing lifecycle/content boundary;
- assign player ownership without rewriting unrelated faction/legal identity unless explicitly required by the future faction system;
- keep station identity stable and persistent;
- preserve construction/project state through save/load;
- ensure destroyed projects/stations follow normal physical destruction/economic consequences.

### 16E — Construction management / strategic-map UX

Expose from authoritative state:

- project location/archetype;
- funding status;
- delivered / missing materials;
- material-work estimate;
- current build duration/progress/ETA;
- ownership/resulting station;
- clear reasons why construction cannot start or continue.

The map/UI remains read-only plus ordinary construction/logistics commands.

### Stage 16 DoD

At minimum prove an end-to-end player station project:

```text
select buildable station archetype/location
→ fund ordinary project
→ source and physically deliver required materials
→ project starts only after real requirements are satisfied
→ build time derives from authoritative material/work policy
→ same world continues while project builds
→ real persistent station is created
→ player owns it
→ save/load preserves project or completed station
```

No debug grants, instant spawning or virtual deliveries are allowed in the Stage-16 acceptance.

## Future technology tiers — ships and structures

**PLANNED cross-cutting content/system requirement.** Introduce explicit data-driven technology tiers for both **station/building archetypes** and **ship/hull archetypes** once Stage-16 construction and the later fitting/content foundations make the distinction mechanically meaningful.

The tier is **not** a generic quality level and must not become an arbitrary `T2 × 2`, `T3 × 3` multiplier for price, damage, HP or construction time.

Intended semantics:

```text
tech tier
→ required technological knowledge/unlock
→ required component/material sophistication
→ minimum capable shipyard/construction facility
→ production/assembly complexity
→ possible specialized labor/tooling/time requirements
→ economic scarcity and therefore emergent price effects
```

### Structure/station tech tier

A future `StationArchetype.techTier` (or equivalent stable content field) should be able to influence:

- whether the player/faction possesses the required technology;
- minimum construction-site / yard capability;
- access to higher-tier or specialized components;
- assembly complexity and commissioning/setup work;
- which station modules/functions can be built;
- repair/upgrade infrastructure requirements where later introduced.

The construction-duration policy should then evolve conceptually toward:

```text
materialWork = Σ(quantity × authoritativeUnitMassOrHandlingWork)

buildTime =
    (baseSetupTime + materialWork / effectiveAssemblyRate)
    × techTierFactor
    × complexityFactor
```

`effectiveAssemblyRate` should later be allowed to depend on the actual construction site/yard capability. Higher tier should usually require more sophisticated inputs and infrastructure; it does not need to be slower in every case if a sufficiently advanced yard has correspondingly better assembly capability.

### Ship tech tier

A future `ShipArchetype.techTier` (or equivalent) should influence:

- required shipyard class/capability;
- component/fitting technology prerequisites;
- production tooling and complexity;
- access/unlock rules;
- repair/refit requirements where relevant;
- valuation indirectly through real components, production cost, scarcity, seller policy and market conditions.

Tech tier must **not** replace hull class/role. A high-tier courier can remain physically smaller than a lower-tier freighter; tier expresses technological/industrial sophistication, while hull class expresses physical role/envelope.

### Tier integration rule

When tech tiers are introduced:

1. add stable bounded content fields and validation to ship/station archetypes;
2. define migration/default behavior for existing content explicitly;
3. expose capability checks through shared player/AI production APIs;
4. make construction/shipbuilding consume real tier-appropriate components and capable facilities;
5. integrate ship price through `docs/ship_pricing_roadmap.md` rather than a blanket tier price multiplier;
6. add deterministic acceptance proving that insufficient technology/facility capability rejects production and sufficient capability succeeds through ordinary physical production;
7. preserve already-started construction contracts: balance/tier edits must not silently rewrite persisted project duration/resources unless an explicit migration requires it.

## Stage 17 — Player faction

**PLANNED.**

Reuse Stage-8 treasury, territory, relations, access, taxes/subsidies and policies. Introduce data-driven faction doctrine for civilian risk, escort preference and broad aggression/retreat choices.

Technology ownership/unlocks should begin to become faction/player state here if not introduced earlier by a minimal Stage-16 construction requirement. The system must remain shared by player and AI factions.

## Stage 17.5 — Combat Depth / Ship Fitting Foundation

**PLANNED prerequisite before advanced tactical combat AI.**

Required capability foundation:

- several materially different hull classes;
- explicit ship tech-tier integration where sufficiently meaningful content exists;
- armor beyond generic hull HP;
- richer shield behavior;
- several weapon families/range envelopes;
- fitting/equipment foundation;
- equipment/armor/cargo/ammunition mass integration;
- stable combat-capability query APIs;
- deterministic enriched-combat tests.

## Stage 18 — Strategic Warfare + Advanced Combat Behavior

**PLANNED after Stage 17.5 gate.**

- formal war/peace/hostility;
- fronts/blockades/territory objectives;
- advanced weapon/range/mobility-aware tactical AI;
- escort/screen/intercept/retreat/pursuit;
- replacement logistics;
- shared threat intelligence with confidence/freshness/decay;
- conflict-driven traffic rerouting and economic consequences;
- strategic global-map overlays.

### v0.4 DoD

Player grows from one ship into autonomous fleets/stations/faction and wages conflicts whose consequences change physical assets, trade routes, supply chains and territory.

---

# MILESTONE v0.5 — RPG & LIVING WORLD

**PLANNED.**

## Stage 19 — Exploration / discovery / world generation

Persistent discovered systems/routes/stations/resources; deterministic seed-driven galaxy generation; anomalies, derelicts and special locations. Information availability remains explicit.

## Stage 20 — NPC / missions / reputation / progression

Persistent NPCs where identity matters. Missions arise from actual world state: haul, mine, escort, bounty, investigate, defend, shortage, expansion, war and discovery. Persistent commanders may apply bounded personality/doctrine modifiers without omniscience.

---

# MILESTONE v0.6 — CONTENT & BALANCE ALPHA

**PLANNED.**

## Stage 21 — Content breadth / balance / long-run stability

Expand resources, components, ships, stations and faction differentiation after mechanics stabilize.

This is also the main breadth/balance phase for a richer technology ladder once the underlying tier rules are stable. Expand tier diversity only after construction, shipbuilding/fitting and faction technology ownership have authoritative mechanics.

Long-run soak/benchmark matrices must detect:

- inflation/deflation;
- permanent shortages/dead economies;
- uncontrolled entity/ledger growth;
- route-planner scaling problems;
- faction snowball;
- civilians never travelling;
- civilians ignoring obvious known wars;
- engage/retreat or route-choice oscillation;
- escorts abandoning convoys;
- danger that never decays;
- universal route avoidance or suicidal profit chasing;
- higher technology tiers becoming mandatory linear upgrades instead of differentiated economic/industrial choices;
- high-tier production bypassing real component/facility bottlenecks.

---

# MILESTONE v0.7 — POLISH / RELEASE CANDIDATE

**PLANNED.**

## Stage 22 — UX / onboarding / performance / release hardening

- unify/polish HUD and management UI built earlier;
- production global/local map filters/search/notifications;
- input discoverability/accessibility/scaling;
- onboarding for first trade/mining/combat/fleet/station;
- autosave/backup/corrupt-save UX and supported migration window;
- profiling large combat, remote worlds, route planning, asset lists and save/load;
- final visual settings and release baselines;
- clean build/regression/soak/save-load-soak gates.

---

# 4. Parallel Visual / UX Track

Visual work proceeds in parallel but never substitutes a functional stage DoD.

- **V1 Ship sprite pipeline:** grounded top-down language, size grammar, hardpoints, pivots/collision conventions.
- **V2 Engine/movement:** idle/thrust/maneuver tied progressively to actual movement/thrust state.
- **V3 Station language:** construction, industrial, mining, trade, military, colony, faction differentiation; future technology tiers should remain visually legible through believable infrastructure/material sophistication rather than arbitrary ornament density.
- **V4 Combat VFX:** weapons, shields/hits/destruction/salvage.
- **V5 Playable navigation/readability:** Stage-14 camera/HUD/local minimap baseline — **COMPLETE baseline**.
- **V6 Strategic map / empire UI:** topology/navigation first, then fleet/orders, territory, trade flows, shortages, cumulative danger, construction and wars alongside Stage 15–18.

Gameplay never depends on one specific sprite asset. Presentation metadata remains data-driven over authoritative simulation archetypes.

---

# 5. Cross-cutting engineering rules

## Persistence

Every persistent domain object defines stable identity, schema/file-format ownership, bounded codec, migration policy and continuation tests.

## Determinism

Every planner/AI uses deterministic iteration and tie-breaks. RNG is named and used only where randomness is an explicit design requirement.

## Economic conservation

Every money/resource mutation uses transfer/source/sink/transform semantics and ledger/invariant coverage. No hidden income/resource creation.

## Physicality

Construction, trade, mining, progression, expansion and warfare use real entities, finite resources/cargo, wallets, travel and build time. Remote simulation may reduce fidelity but may not invent incompatible consequences.

## Shared player/AI core

Player-facing commands and AI intent adapt to common simulation controllers. Separate player-only implementations require explicit justification and invariant coverage.

## Movement physicality

All current high-fidelity local direct-player, delegated-fleet, generic-trader and generic-miner movement uses shared mass/thrust/acceleration limits through `FlightDynamics`. Flight assist may simplify intent but may not grant instantaneous acceleration/braking unavailable to equivalent ships. Cargo/equipment/armor affect mobility through authoritative physical data as those systems mature.

No new local AI/player movement system may directly snap normal movement position/velocity around the shared flight boundary unless the operation is an explicit structural materialization event such as arrival/spawn/load with documented semantics.

## Jump / structural materialization

Inter-system travel uses the Stage-10 finite jump FSM. A jump is not ordinary local movement and may structurally detach/materialize a persistent `FleetId`. Arrival coordinates must use a documented canonical local-system anchor and presentation must follow the authoritative active FleetId/system after materialization.

## AI information / route risk

Danger decisions use available observations/intelligence rather than automatic global omniscience. Strategic risk evaluates the **full traversed route — systems and links —** with actor-specific exposure/vulnerability, not destination danger alone.

## Construction physicality

Construction duration and feasibility must derive from authoritative project/archetype/material/facility data. New projects persist their resolved construction contract so later balance changes do not silently alter work already under way. Missing physical materials/facility capability must not be replaced by a hidden currency shortcut.

## Technology tiers

Future ship/station technology tiers are data-driven production/technology constraints, not arbitrary stat/price multipliers. Tier consequences should emerge through real component requirements, capable yards/sites, production complexity, unlocks, logistics/scarcity and fitting capabilities. Player and AI use the same tier/capability checks.

## Presentation read-only boundary

HUD/minimap/global-map layers may read authoritative state and submit ordinary commands, but may not mutate economy/combat/mining/ownership/physics directly or introduce UI-only gameplay rules.

## Measure before optimization

Major systems require diagnostics/benchmarks. Optimize from evidence or structurally unacceptable algorithmic scaling rather than speculative micro-optimization.

---

# 6. Stage transition rules

1. `main` remains stable.
2. New core work starts from current green `main`.
3. Broken CI blocks merge/stage transition.
4. Every stage has explicit vertical slice and DoD.
5. Persistent changes require migration/continuation coverage.
6. Economic changes require conservation/invariant coverage.
7. Deterministic decision code requires tie-break coverage.
8. Player and AI reuse common APIs unless separation is explicitly justified.
9. Do not expand content breadth before mechanics stabilize.
10. UI/map layers remain read-only views + command adapters.
11. Advanced tactical combat AI does not begin before the combat-depth gate.
12. Strategic danger-aware routing scores the entire traversed path, not only destination.
13. Generic/local ship movement debt is **closed as of PR #51**: direct player, delegated fleet, generic TradeAI and autonomous mining use the shared inertial flight boundary. Do not reintroduce direct normal-movement `Transform` mutation.
14. Generated ship pricing must eventually use live economic/material/component/fitting/condition/relationship inputs while retaining authoritative real-asset ownership transfer.
15. Construction duration must derive from real project/material/facility complexity inputs; already-started projects retain their persisted resolved duration.
16. Future ship/station technology tiers must be explicit stable content/system data and must not be implemented as blanket price/stat multipliers.
17. Update this roadmap only after factual completion/merge evidence exists.

---

# 7. Current next step

**ACTIVE: Stage 16 — Player Construction / Station Ownership.**

Stage 15 is factually complete through PR #47/#48/#49, with post-completion inertial/jump hardening in PR #51. PR #51 merged as `a32584a928d97a014dd2cbb32fdeaed4fe0c65eb`; CI #1151 / run `31826504541` passed **454/454 tests**, strict Javadoc, JaCoCo and shaded desktop packaging. Documentation hardening was merged through PR #52; `main` after that merge was `0b3105e1af6d3ef6beba2e92963bab5012ff37c6`.

Immediate Stage-16 implementation order:

1. add the player-facing project-authoring/query boundary over ordinary Stage-9 construction projects;
2. preserve the already-merged material-driven construction-duration policy and expose its work/ETA diagnostics;
3. require real project funding and physical delivery of every required material;
4. create the resulting station only through the ordinary lifecycle/content boundary and transfer real ownership to the player;
5. persist/continue both in-progress projects and completed station ownership;
6. expose project/site/material/progress information through the strategic map/UI without UI-side construction rules;
7. add end-to-end deterministic acceptance from project selection through material logistics to a completed persistent player-owned station;
8. prepare, but do not prematurely fabricate, the future tech-tier fields/capability model for stations and ships; introduce them when technology/facility/component distinctions become authoritative enough to validate properly;
9. only after Stage-16 DoD proceed to Stage 17 player-faction integration.

Do **not** begin advanced weapon-aware tactical AI now. Do **not** create instant stations or virtual deliveries. Do **not** implement technology tiers as arbitrary linear upgrades; they must be grounded in the physical production/economy systems already established.