# Star Empires — Development Roadmap

> Canonical core-development status and transition document.
>
> Last synchronized: **2026-08-14** after Stage 12 merge.
>
> Detailed historical roadmap before Stage 11: `docs/archive/development_roadmap_pre_stage11_2026-08-13.md`.
> Completion records: `docs/stage11_autonomous_faction_expansion.md`, `docs/stage12_playable_actor.md`.

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
| **v0.3 Playable Space Sandbox** | player ship, travel, trade, mining/combat, first progression loop | 12–14 | **ACTIVE** |
| **v0.4 Fleet & Empire Sandbox** | player fleets, stations, faction, strategic war | 15–18 | PLANNED |
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

**ACTIVE.** Stage 12 is complete; Stage 13 is the current core stage.

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

**ACTIVE — current core stage.**

Minimum scope:

- target acquisition/selection;
- positioning and range;
- at least one data-driven weapon definition;
- cooldown/fire state;
- shields/hull;
- deterministic damage and physical destruction;
- combat AI using the same authoritative fire/damage pipeline as player commands;
- salvage/resource-sink seam;
- economic aftermath of ship loss.

Player and AI must share one authoritative damage/destruction path. Ship loss must remove a real asset and create downstream replacement/economic pressure rather than a scripted respawn.

### Stage 13 DoD target

A player-controlled ship and an AI ship can enter the same physical combat model, acquire targets, exchange deterministic weapon fire through data-driven weapon state, damage shields/hull, destroy an asset through the ordinary destruction pipeline, and expose a measurable economic/salvage consequence.

## Stage 14 — First complete player economic loop

**PLANNED.**

```text
explore
→ find opportunity
→ trade / mine / fight
→ earn credits
→ upgrade or buy ship
→ take larger opportunities
```

Player mining must consume finite resources; ship purchase must use real wallet/ownership transfer. Add playtest telemetry such as credits/hour, profit/hour, travel downtime, cargo utilization and losses.

### v0.3 DoD

First internal playable hour without debug grants: travel + economy + mining/combat + progression to a better ship.

---

# MILESTONE v0.4 — FLEET & EMPIRE SANDBOX

**PLANNED.**

## Stage 15 — Player fleets / autonomous orders

Persistent grouping and orders: HOLD, MOVE, TRADE, MINE, ESCORT, PATROL, FOLLOW. Owned autonomous economic fleets reuse existing planners rather than passive-income formulas.

## Stage 16 — Player construction / station ownership

Player uses the same Stage-9 ConstructionProject: chooses archetype/system/location/funding, delivers materials manually or through owned logistics, and receives a real operating station.

## Stage 17 — Player faction

Data-driven founding requirements followed by reuse of Stage-8 treasury, territory, relations, access, taxes, subsidies and strategic policies.

## Stage 18 — Strategic warfare / territory / politics

- formal hostility/war/peace;
- defend/attack/escort/blockade/capture objectives;
- military construction and replacement logistics;
- runtime jump-route availability/risk overlay shared by planner + executor;
- blockade -> throughput loss -> shortage/price/economic response benchmark;
- explicit territory transition rule;
- Stage-11 economic outposts integrate with military conquest here.

### v0.4 DoD

Player grows from one ship into autonomous fleets/stations/faction and wages wars whose results change physical assets, trade routes, supply chains and territory.

---

# MILESTONE v0.5 — RPG & LIVING WORLD

**PLANNED.**

## Stage 19 — Exploration / discovery / world generation

Persistent discovered systems/routes/stations/resources; deterministic seed-driven galaxy generation; faction/economy bootstrap; data-driven anomalies, derelicts and special locations.

## Stage 20 — NPC / missions / reputation / progression

Persistent NPCs only where identity matters. Missions arise from actual world state: haul, deliver, mine, escort, bounty, investigate, defend. Shortage, loss, expansion, war and discovery should create dynamic contracts. Reputation connects to access, contracts and faction relations.

---

# MILESTONE v0.6 — CONTENT & BALANCE ALPHA

**PLANNED.**

## Stage 21 — Content breadth / balance / long-run stability

Expand resources, intermediates, components and civilian/military goods only after the corresponding mechanics are stable. Build coherent ship/station ecosystems and faction differentiation through real starting conditions, policies and doctrine rather than hidden bonuses.

Full-world benchmarks must include inter-system trade, construction, faction expansion, combat losses, player-owned assets and long duration. Detect inflation/deflation, dead economies, permanent shortages, uncontrolled entity/ledger growth, route-planner scaling and faction snowball.

---

# MILESTONE v0.7 — POLISH / RELEASE CANDIDATE

**PLANNED.**

## Stage 22 — UX / onboarding / performance / release hardening

- unified HUD and management UI;
- map layers, filters/search and notifications;
- onboarding for first trade/mining/combat/fleet/station;
- autosave/backup/corrupt-save UX and supported migration window;
- performance profiling of large combat, many remote systems, route planning, large asset lists and save/load;
- `BloomMode = OFF / LIGHT / FULL` production settings and baselines;
- clean build, regression, soak and save/load-soak release gates.

---

# 4. Parallel Visual / UX Track

Visual work may proceed in parallel but never substitutes a functional stage DoD.

- **V1 Ship sprite pipeline:** grounded top-down language, size grammar, hardpoints, pivots/collision conventions.
- **V2 Engine/movement animation:** idle/thrust/maneuver from real movement state.
- **V3 Station language:** construction, industrial, mining, trade, military, colony and faction differentiation.
- **V4 Combat VFX:** weapons, shields/hits/destruction/salvage and benchmarked BloomMode tiers alongside Stage 13.
- **V5 Strategic map / empire UI:** territory, fleets, trade flows, shortages and wars alongside Stage 15–18.

Gameplay never depends on a particular sprite asset. Presentation metadata remains data-driven over simulation archetypes.

---

# 5. Cross-cutting engineering rules

## Persistence

Every persistent domain object defines stable identity, schema/file-format ownership, bounded codec, migration policy and continuation tests.

## Determinism

Every planner/AI uses deterministic iteration/tie-breaks. RNG is named and used only when randomness is an explicit design requirement.

## Economic conservation

Every money/resource mutation uses transfer/source/sink/transform semantics and has ledger/invariant coverage. No hidden income/resource creation.

## Physicality

Construction, trade, expansion and warfare use real entities, cargo, wallets, travel and build time. Remote simulation may reduce fidelity but may not invent an incompatible economy.

## Shared player/AI core

Player-facing commands adapt to common simulation controllers. A separate player-only implementation requires explicit justification and tests proving invariants are still shared.

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
10. Update this roadmap only after factual completion/merge evidence exists.

---

# 7. Current next step

**ACTIVE: Stage 13 — Combat Vertical Slice.**

Immediate implementation order:

1. audit current ship/destruction/content seams and define one authoritative combat state/damage boundary;
2. add minimal data-driven weapon + persistent/runtime cooldown/fire state with deterministic targeting/range rules;
3. add shield/hull damage and route destruction through existing physical lifecycle/destruction services;
4. expose the same fire/target commands to player and combat AI;
5. add salvage/resource-sink/economic-loss seam without fabricated replacement assets;
6. build end-to-end acceptance where player and AI use the same combat pipeline and a destroyed ship changes physical/economic state;
7. only after Stage 13 DoD begin Stage 14.

Stage 12 is closed. Do not expand player-UI breadth or add a parallel player economy before Stage 13 proves combat through the same shared physical core.
