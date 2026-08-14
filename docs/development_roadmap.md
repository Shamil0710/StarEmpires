# Star Empires — Development Roadmap

> Canonical core-development status and transition document.
>
> Last synchronized: **2026-08-14 after complete Stage 14 / milestone v0.3 merge**.
>
> Historical detail before Stage 11: `docs/archive/development_roadmap_pre_stage11_2026-08-13.md`.
> Major completion records: `docs/stage11_autonomous_faction_expansion.md`, `docs/stage12_playable_actor.md`, `docs/stage13_combat_vertical_slice.md`, `docs/stage14a_player_mining.md`, `docs/stage14b_ship_progression.md`, `docs/stage14c_playable_navigation.md`, `docs/stage14_complete_player_economic_loop.md`.
> Cross-cutting plans: `docs/ui_navigation_roadmap.md`, `docs/ai_behavior_roadmap.md`, `docs/cumulative_route_risk_model.md`, `docs/flight_dynamics_and_combat_depth_roadmap.md`, `docs/ship_pricing_roadmap.md`.

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
| **v0.4 Fleet & Empire Sandbox** | player fleets, stations, faction, strategic war | 15–18 + 17.5 | **ACTIVE — Stage 15** |
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

**COMPLETE — PR #45.**

`PlayerDirectControlSystem` now uses shared `FlightDynamics` instead of assigning velocity instantly.

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
- `FlightCommandComponent` + `AutonomousFlightSystem` expose the same physical executor for future autonomous local orders;
- `PlayerFlightService/View` expose read-only speed/mass/acceleration/braking/stopping diagnostics;
- equivalent player/autonomous intent produces identical physical evolution in deterministic tests.

Important deliberate compatibility seam: legacy `TradeAISystem` / `MiningSystem` direct local movement is not falsely declared migrated. Stage 15 owned autonomous orders should consume the new shared flight seam and progressively retire direct movement where local high-fidelity execution is required.

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

**ACTIVE — Stage 15.**

## Stage 15 — Player Fleets / Autonomous Orders

**ACTIVE — current core stage.**

Goal: let the player own several ships and delegate real persistent work to them without passive-income abstractions or a second AI simulation model.

### 15A — Persistent fleet orders foundation

Implement persistent, deterministic player orders with stable identity/state:

- `HOLD`;
- `MOVE`;
- `TRADE`;
- `MINE`;
- `ESCORT`;
- `PATROL`;
- `FOLLOW`.

Order state must survive save/load and keep referencing existing physical `FleetId`s.

### 15B — Shared inertial local order execution

The first Stage-15 movement debt is to consume Stage-14 `FlightCommandComponent` / `AutonomousFlightSystem` / `FlightDynamics` for owned autonomous local movement.

Requirements:

- player and autonomous local ships remain under the same mass/thrust constraints;
- cargo affects autonomous mobility the same way it affects player mobility;
- no command may teleport or directly assign a physically impossible velocity;
- local high-fidelity movement may coexist with lower-fidelity remote strategic travel only where consequences stay compatible;
- progressively retire legacy direct-position movement paths when an owned autonomous order uses local execution.

### 15C — Autonomous economic orders

`TRADE` and `MINE` reuse existing economic planners/controllers and real inventories:

```text
order
→ planner chooses physical opportunity
→ ship physically moves / jumps
→ ordinary buy / extraction
→ real cargo aboard FleetId
→ physical move / jump
→ ordinary sale / delivery
```

No passive income, virtual delivery or invented cargo.

### 15D — Civilian survival / replanning

First major civilian behavior tranche from `docs/ai_behavior_roadmap.md`:

- suspend trade/mining when attacked or danger becomes unacceptable;
- flee through real movement/jumps;
- retain actual cargo/damage;
- resume/replan/abort after safety;
- use hysteresis/commitment so decisions do not oscillate every update.

### 15E — Cumulative whole-route risk

Implement the first practical model from `docs/cumulative_route_risk_model.md`.

Fundamental invariant:

> **Route danger is evaluated across the entire traversed route, not only the destination.**

Initial exposure form:

```text
RouteExposure =
    Σ(systemDanger × systemExposure × confidence)
  + Σ(linkDanger × linkExposure × confidence)
```

Actor-specific utility must account for, at minimum:

- ship replacement value;
- cargo value;
- current damage;
- acceleration/braking/escape mobility from the shared flight model;
- escort strength;
- doctrine/risk tolerance seam;
- intel freshness/confidence;
- alternate routes/safe havens where known.

A loaded freighter must naturally evaluate the same geographic route as more dangerous than its empty state when real mass, lower mobility and greater cargo value justify that result. Do not implement this as an arbitrary `fullCargoDanger` multiplier.

No omniscience: actors react only to threat information they possess or receive.

### 15F — Escort / convoy / patrol / follow

Group-order execution should respect physical members:

- convoy mobility is constrained by slow/critical protected ships;
- escorts reduce vulnerability but do not magically improve transport acceleration;
- cohesion and regroup behavior are deterministic;
- escort/patrol/follow must not duplicate or teleport members.

### 15G — First functional global-map layer

Begin global map functionality only as corresponding systems become real:

- known topology;
- owned fleet selection;
- current orders;
- route preview from authoritative planner;
- known systems/stations;
- route-risk diagnostics where available.

The map submits ordinary orders; it does not own pathing or movement rules.

### Stage 15 DoD

At minimum, prove that multiple owned persistent FleetIds can receive saved autonomous orders, physically execute representative move/trade/mine/escort-style behavior through shared movement/economy rules, react to known danger, choose between routes using cumulative whole-route risk, and remain understandable/manageable through the first global-map layer.

## Stage 16 — Player construction / station ownership

**PLANNED.**

Player uses ordinary Stage-9 construction projects: real funding, real materials, build time, physical logistics and ownership. No instant station placement.

## Stage 17 — Player faction

**PLANNED.**

Reuse Stage-8 treasury, territory, relations, access, taxes/subsidies and policies. Introduce data-driven faction doctrine for civilian risk, escort preference and broad aggression/retreat choices.

## Stage 17.5 — Combat Depth / Ship Fitting Foundation

**PLANNED prerequisite before advanced tactical combat AI.**

Required capability foundation:

- several materially different hull classes;
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
- universal route avoidance or suicidal profit chasing.

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
- **V3 Station language:** construction, industrial, mining, trade, military, colony, faction differentiation.
- **V4 Combat VFX:** weapons, shields/hits/destruction/salvage.
- **V5 Playable navigation/readability:** Stage-14 camera/HUD/local minimap baseline — **COMPLETE baseline**.
- **V6 Strategic map / empire UI:** topology/navigation first, then fleet/orders, territory, trade flows, shortages, cumulative danger and wars alongside Stage 15–18.

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

Local high-fidelity player/autonomous movement uses shared mass/thrust/acceleration limits. Flight assist may simplify intent but may not grant instantaneous acceleration/braking unavailable to equivalent ships. Cargo/equipment/armor affect mobility through authoritative physical data as those systems mature.

## AI information / route risk

Danger decisions use available observations/intelligence rather than automatic global omniscience. Strategic risk evaluates the **full traversed route — systems and links —** with actor-specific exposure/vulnerability, not destination danger alone.

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
13. Legacy direct local movement is retired progressively in favor of shared Stage-14 flight physics as autonomous orders are implemented; do not perform a risky all-at-once rewrite unrelated to a vertical slice.
14. Generated ship pricing must eventually use live economic/material/component/fitting/condition/relationship inputs while retaining authoritative real-asset ownership transfer.
15. Update this roadmap only after factual completion/merge evidence exists.

---

# 7. Current next step

**ACTIVE: Stage 15 — Player Fleets / Autonomous Orders.**

Stage 14 is factually complete at PR #45 / main `0393eccf790269651bcedbdfd8e4eaf8b60ca06a`, with final CI #1010 passing **431/431 tests**, the full 3600-second integrated acceptance, strict Javadoc, JaCoCo and desktop packaging.

Immediate implementation order:

1. define persistent deterministic player fleet-order identity/state and bounded persistence;
2. implement `HOLD` and `MOVE` first, routing local owned-autonomous movement through Stage-14 `FlightCommandComponent` / `AutonomousFlightSystem` / `FlightDynamics`;
3. add `TRADE` and `MINE` orders using existing planners/controllers and physical cargo;
4. add civilian suspend/flee/resume/replan behavior without omniscience;
5. implement cumulative whole-route risk over systems + links, including real mobility/cargo/escort inputs and deterministic diagnostics;
6. add `ESCORT`, `PATROL`, `FOLLOW` and convoy-cohesion behavior;
7. expose the first functional global-map fleet/order/route layer from authoritative state;
8. add persistence, determinism, physical-economy and risk-choice acceptance matrices;
9. only after Stage15 DoD proceed to player construction/stations (Stage16).

Do **not** begin advanced weapon-aware tactical AI now. Do **not** invent a separate fleet movement/economy model. Stage 15 should build directly on the physical player/economy/flight foundations proven by Stage 14.
