# Star Empires — канонический roadmap разработки

> **Последняя синхронизация: 2026-08-17 / Stage 17.5F shields / armor / compartments / subsystem damage implementation.**  
> Этот файл — authoritative status/dependency roadmap. Исторические snapshots находятся в `docs/archive/` и не являются текущим планом.

## 1. Главный инвариант

**Star Empires** — 2D top-down space sandbox/RPG/strategy с живой физической экономикой и миром, существующим независимо от игрока.

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

Главный architectural contract:

> **Игрок и AI используют одни и те же физические, информационные, политические и экономические правила везде, где это практически возможно.**

Без отдельного architecture decision запрещены:

- player-only economy/combat rules;
- passive income вместо real transfers;
- virtual deliveries;
- hidden resource grants;
- scripted asset replacement;
- мгновенное ordinary travel/construction/refit;
- class-name performance bonuses;
- UI, напрямую мутирующий authoritative simulation state.

## 2. Milestones

| Milestone | Цель | Stages | Статус |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | deterministic economic core | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | multi-system factions/logistics/construction/expansion | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | player ship/travel/trade/mining/combat/progression | 12–14 | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | fleets/stations/player faction/combat depth/industry/warfare | 15–19 + 17.5 | **ACTIVE — Stage 17.5G NEXT** |
| **v0.5 RPG & Living World** | calibrated world generation/discovery/NPC/missions/reputation | 20–21 | PLANNED |
| **v0.6 Content & Balance Alpha** | technology/content breadth + long-horizon balance | 22 | PLANNED |
| **v0.7 Polish / RC** | UX/onboarding/performance/save hardening | 23 | PLANNED |

Manual merge gate remains mandatory while `main` is unprotected:

```text
branch from exact green main
→ clean verify on exact PR head
→ inspect exact diff/head SHA
→ merge that exact SHA only
→ post-merge CI on exact new main SHA
```

## 3. Completed foundation

### Stages 0–6 — v0.1 Economic Sandbox

**COMPLETE.** Java-17 CI/JUnit/JaCoCo/Javadoc, deterministic fixed time, integer milli-credits, conserved ledger semantics, stable identity/persistence, data-driven content, local logistics and headless observability.

### Stages 7–11 — v0.2 Living Galactic Economy

**COMPLETE.** `Galaxy → Sector → StarSystem`, faction treasury/economy, dynamic markets, physical construction/destruction, inter-system logistics, autonomous expansion and Stage-8.5 `KEEP_LIBGDX` decision.

### Stages 12–14 — v0.3 Playable Space Sandbox

**COMPLETE.** Player state/ownership, physical travel/docking/manual trade/mining, shared combat vertical slice, ship progression, UI and deterministic first-loop acceptance.

### Stage 15 — player fleets

**COMPLETE.** Persistent multiple `FleetId`, autonomous orders, shared movement, physical trade/mining, risk/threat context, global map and ordinary jump FSM.

### Stage 16 — player construction / owned stations

**COMPLETE.** Real construction sites, wallets, material delivery, build time, remote continuation, persistent completed stations, ownership reconciliation and destruction without free replacement.

Canonical records:

- `docs/stage16_player_construction.md`;
- `docs/stage16_construction_timing.md`;
- `docs/stage16_acceptance_matrix.md`;
- `docs/stage16_completion_record.md`.

## 4. Stage 17 — собственная фракция игрока

**COMPLETE — 17A–17H.**  
Canonical final closeout: `docs/stage17h_persistence_transition_completion_record.md`.

Цель достигнута: независимый игрок с существующими Stage-15/16 assets становится обычным faction actor без замены физических IDs, бесплатной территории/капитала и player-only diplomacy/economy.

```text
physical economy / territory / security state
→ measurable interests and dependencies
→ doctrine + diplomatic history
→ common command / strategic decision
→ legal access / tariff / treasury / logistics / production consequences
→ changed physical world state
→ changed future interests and policy
```

### 17A–17E — identity / treasury / assets / territory / diplomacy

**COMPLETE.** Закрыты:

- dynamic player-created faction identity + persistence;
- founding без денег/земли/assets grant;
- id-preserving affiliation existing fleets/stations;
- separate personal wallet and public treasury;
- conserved transfers;
- presence/claim/stabilization/control/recognition/concession as distinct legal states;
- shared treaty/embargo lifecycle;
- legal market-access resolver;
- customs/treaty consequences through ordinary transactions;
- structural dependency from physical supply/access state.

### 17F — faction policy / strategic economy

**COMPLETE — 17F.1–17F.7.**

- doctrine;
- fiscal policy;
- treasury/station/construction trade-offs;
- stock/production policy;
- resilience policy;
- bounded persistent policy review / anti-oscillation;
- shared player/AI `FactionPolicyCommand` path.

Hard rule:

```text
policy authoring
≠ money/cargo/output creation
```

Policy consequences materialize only through ordinary market/logistics/production/treasury/construction systems.

### 17G — faction management / strategic projection

**COMPLETE — PR #133.**

Canonical closeout: `docs/stage17g_faction_management_completion_record.md`.

Implemented immutable management/global-map projections and a command facade delegating to existing authoritative treasury, policy, diplomacy and territorial law.

### 17H — persistence / migration / final transition gate

**COMPLETE — PR #137 implementation.**

Final acceptance now covers the required chain with a real completed Stage-16 station and real binary persistence boundary:

```text
independent player with Stage-16 fleet + completed station
→ found world-defined faction
→ affiliate the same FleetId + station EntityId
→ conserved personal→treasury capitalization
→ shared fiscal-policy authoring
→ ordinary station→treasury fiscal transfer
→ treaty / embargo through shared diplomacy
→ access changes through ordinary legal resolver
→ claim begins with zero stabilization and no instant sovereignty
→ PlayableWorldStateCodec encode/decode/re-encode
→ restore PlayerRuntime
→ diplomacy/access/policy/territory/assets persist
→ no money/cargo/ID duplication or reset
```

17H additionally locks a real pre-Stage17 migration fixture:

```text
Playable schema v5
+ World schema v8 / world file format v2
→ current Playable v5 + World v9
```

Migration preserves local physical `GameState`, money, FleetIds, construction/fleet allocator watermarks and independent-player state while adding only neutral/zero Stage-17 defaults. No player faction, treaty, territory, treasury or resource is invented.

Persistence inventory is now synchronized in `docs/persistence_model.md`:

- `GameState` schema v4 with fitted Stage-17.5 engineering state;
- `WorldState` schema v9 / file format v8;
- `PlayableWorldState` schema v5 / file format v1.

`PlayableWorldState` remains v5 intentionally because Stage 17.5C adds no new serialized PlayerState field; fitted engineering belongs to local `GameState`, while Stage-17 institutional state remains inside `WorldState`.

## 5. Stage 17 → 17.5 transition gate

**UNBLOCKED after Stage-17H merge/post-merge verification.**

Accepted research/design prerequisite:

- `docs/ship_mathematics_v1_0_design_baseline.md` — **ACCEPTED DESIGN BASELINE**;
- `docs/benchmarks/ship_mathematics_v1_0_design_baseline.json`;
- `docs/ship_mathematics_v1_roadmap_integration_contract.md`;
- `docs/ship_hull_module_and_fleet_doctrine.md`;
- `docs/flight_dynamics_and_combat_depth_roadmap.md`;
- `docs/stage17_5_combat_depth_implementation_plan.md`.

Completed production slices:

> **Stage 17.5A — COMPLETE: versioned material/hull/module/protection/slot/hardpoint/compartment content schema, strict loader/validation, machine-readable demonstrator fit and stable semantic fingerprint.**

Canonical 17.5A closeout: `docs/stage17_5a_production_ship_content_schema.md`.

> **Stage 17.5B — COMPLETE: immutable runtime engineering fit/load state, deterministic fitting validator, central common-budget derived-ship calculator, physical-mass `FlightDynamics` bridge and ID-preserving legacy-archetype compatibility seam.**

Canonical 17.5B closeout: `docs/stage17_5b_derived_ship_calculator_and_fitting_validator.md`.

> **Stage 17.5C — COMPLETE: persistent reaction mass, thrust/jet-power closure, shared power/storage/load shedding, local/coolant/ship/radiator thermal runtime, fitted mass/energy/power/heat/time FTL and composition with the single neighbor-only FleetJumpService FSM.**

Canonical 17.5C closeout: `docs/stage17_5c_propulsion_power_thermal_ftl.md`.

> **Stage 17.5D — COMPLETE: channelized physical signatures, physical sensor measurements without hard range walls, covariance-bearing tracks, geometry/freshness/datalink fusion, active-radar emission, explicit ECM/ECCM/deception mechanics and one shared player/AI information model.**

Canonical 17.5D closeout: `docs/stage17_5d_signatures_sensors_tracks_datalink_ew.md`.

> **Stage 17.5E — COMPLETE: individual physical kinetic/guided bodies, deterministic fire-control without hit-chance or hard-range abstractions, central physical ammunition depletion, beam spot/dwell physics, guided propulsion/seeker/datalink state, formation/resource-limited layered defense and dense deterministic projectile storage independent from rendering/Ashley.**

Canonical 17.5E closeout: `docs/stage17_5e_weapons_ammunition_guidance_layered_defense.md`.  
Projectile representation invariant: `docs/stage17_5e_projectile_representation_invariant.md`.

> **Stage 17.5F — COMPLETE: finite geometry/reserve/power/heat shield runtime, bounded no-extrapolation heavy-impact material response with `STOPPED` / `RICOCHET` / `PERFORATED`, explicit compartment/mount damage topology and damage-aware common ship/sensor/weapon capabilities.**

Canonical 17.5F closeout: `docs/stage17_5f_shields_armor_compartments_subsystem_damage.md`.

## 6. Stage 17.5 — Combat Depth / Ship Fitting Foundation

**ACTIVE — 17.5A–17.5F COMPLETE; 17.5G NEXT.**

Implementation sequence:

- **17.5A — COMPLETE:** production `MaterialDefinition` / `HullDefinition` / `ModuleDefinition` / protection/slots/hardpoints/compartments + versioned loader/fingerprint;
- **17.5B — COMPLETE:** central deterministic derived-ship calculator + fitting validator + physical-load movement bridge + ID-preserving compatibility seam;
- **17.5C — COMPLETE:** propulsion/reaction mass/power/thermal/FTL runtime + persistent engineering state + single-FSM fitted jump integration;
- **17.5D — COMPLETE:** signatures/sensors/tracks/datalink/EW + fitted sensor adapter + common player/AI information model;
- **17.5E — COMPLETE:** kinetic/beam/guided/PD/ammunition + individual physical bodies + linked weapon/ammunition content + deterministic pooled projectile representation;
- **17.5F — COMPLETE:** finite shields + bounded armor/material response + compartments/subsystem damage + damage-aware derived/sensor/weapon capabilities;
- **17.5G — NEXT:** shipyard/refit/repair/maintenance economy seam;
- **17.5H:** capability APIs/UI/full migration surfaces, including final damage/shield composition with live engineering grants, final engineering-grant/weapon power-heat commit, binary sensor-knowledge persistence and weapon loadout/launcher-cycle persistence where required by live capability APIs;
- **17.5I:** deterministic aggregate acceptance **plus mandatory Combat Test Content Pack and Tactical Prototype Visual Set**.

Stage-17.5F activates authoritative local compartment/module damage and damage-aware central capability derivation. Stage-17.5H must consume this state at live engineering/API/persistence boundaries rather than reset `DamageState` to pristine; Stage-17.5F deliberately does not fork the existing Stage-17.5C runtime to bypass that integration slice.

### Mandatory Stage 17.5 exit content gate

Before Stage 17.5 can be marked COMPLETE, production schemas/runtime must support a compact representative set of hulls, equipment, ammunition and fits sufficient to assemble:

- kinetic line fleet;
- missile strike fleet;
- high-mobility / beam fleet;
- defensive / EW fleet;
- balanced control fleet.

These test assets are **production-valid but content-provisional**:

- they use the same authoritative fitting, mass/volume/power/heat, sensors, weapons, protection, damage, consumable and persistence rules as future production content;
- their names, faction identity, technology placement, final balance and visual design are not automatically canonical;
- Stage 22 must re-author/rebalance/replace them according to the accepted technology, industrial, faction and visual paradigms or explicitly promote individual definitions after review.

At least one interactive end-to-end battle must be inspectable using temporary top-down ship sprites plus prototype projectile/missile/beam/interception/shield/impact/damage/wreck visuals. Rendering/VFX remain presentation-only and must be replaceable without changing authoritative simulation state.

Canonical detailed acceptance contract: `docs/stage17_5i_combat_test_content_visual_acceptance.md`.

Hard invariants:

- no player-only combat physics;
- no class-name bonuses;
- no free ammunition/reaction mass;
- no final global-HP-only survivability model;
- every module uses shared mass/volume/power/heat/economy budgets;
- authoritative fit/consumable/damage state remains persistent and deterministic;
- persistent ↔ tactical materialization cannot reset state;
- Combat Test Content Pack cannot use hidden test-only combat stats;
- Tactical Prototype Visual Set cannot become authoritative combat state;
- Stage-17.5 test content cannot silently become final Stage-22 canon.

Detailed plan: `docs/stage17_5_combat_depth_implementation_plan.md`.  
17.5A implementation record: `docs/stage17_5a_production_ship_content_schema.md`.  
17.5B implementation record: `docs/stage17_5b_derived_ship_calculator_and_fitting_validator.md`.  
17.5C implementation record: `docs/stage17_5c_propulsion_power_thermal_ftl.md`.  
17.5D implementation record: `docs/stage17_5d_signatures_sensors_tracks_datalink_ew.md`.  
17.5E implementation record: `docs/stage17_5e_weapons_ammunition_guidance_layered_defense.md`.  
17.5F implementation record: `docs/stage17_5f_shields_armor_compartments_subsystem_damage.md`.  
17.5I combat content / visual acceptance: `docs/stage17_5i_combat_test_content_visual_acceptance.md`.

## 7. Stage 18 — Resources / Industry / Infrastructure Foundation

**PLANNED after 17.5 COMPLETE.**

Stage 18 defines **what physically/economically exists** before world generation decides **where it exists**.

Canonical material chain:

```text
resource occurrence
→ compatible extraction
→ finite feedstock
→ refining / purification
→ engineering material / consumable
→ industrial component
→ module / ammunition / infrastructure
→ ship / station
→ wear / repair / loss
→ bounded salvage / recycling
```

Baseline intentionally uses realistic but aggregated resource families. A separate commodity/process exists only when it creates a meaningful source, extraction/refining requirement, storage/logistics constraint, strategic bottleneck or substitution/recycling choice.

Baseline raw families:

- `WATER_ICE`;
- `VOLATILE_FEEDSTOCK`;
- `CARBONACEOUS_FEEDSTOCK`;
- `METALLIC_ORE`;
- `LIGHT_METAL_MINERALS`;
- `CONDUCTOR_ORE`;
- `STRATEGIC_METAL_ORE`;
- `SILICATE_MINERALS`;
- `FISSILE_MINERALS` where technology requires it.

Compact component layer:

- `HEAVY_COMPONENTS`;
- `ELECTRICAL_COMPONENTS`;
- `PRECISION_COMPONENTS`.

Implementation:

```text
18A resource/schema ontology
→ 18B extraction compatibility
→ 18C refining/materials
→ 18D components + module/ammunition recipes
→ 18E facility capabilities
→ 18F stations/storage/logistics
→ 18G shipyard/repair/refit industrial integration
→ 18H salvage/recycling/construction economy
→ 18I deterministic minimal-industrial-universe acceptance
```

Detailed plan: `docs/stage18_resources_industry_infrastructure_plan.md`.

## 8. Stage 19 — Strategic Warfare / Coercive Diplomacy / Advanced Combat Behavior

**PLANNED after Stage 18 COMPLETE.**

Consumes Stage-17 political state, Stage-17.5 physical ship capability and Stage-18 real industrial/logistics network.

```text
crisis / war goal
→ mobilization + treasury pressure
→ ammunition / reaction mass / repair / replacement demand
→ physical logistics/readiness
→ tactical operations
→ real losses/blockades/industrial/territory effects
→ negotiated political/economic outcome
```

Mines, depots, water/propellant sources, precision fabs, ammunition plants, shipyards and routes become ordinary physical strategic targets. War cannot apply abstract production penalties instead of actual disruption.

## 9. Stage 20 — Physical World Generation / Discovery

**PLANNED after Stage 19.**

Answers **where the already-defined world exists**.

Canonical generation contracts:

- `docs/stage20_physical_world_generation_plan.md`;
- `docs/inter_system_navigation_contract.md`;
- `docs/physical_trade_route_scoring_contract.md`;
- `docs/galaxy_topology_resource_geography_generation_contract.md` — **ACCEPTED CROSS-STAGE INVARIANT** for non-linear topology, resource geography, economic dependency and world-generation quality gates;
- `docs/spatial_scale_and_unbounded_system_space_contract.md` — **ACCEPTED CROSS-STAGE INVARIANT** for capability-calibrated local geometry, station scale, unbounded local space, numerical precision and LOD/world-boundary separation.

Must honor:

- SI physical scale and travel time;
- propulsion/FTL capability;
- ship acceleration/braking/delta-v and loaded-mass consequences;
- Stage-17.5 sensor/signature/track/fire-control behavior;
- kinetic/beam/guided/PD/formation physical engagement geometry;
- Stage-18 station/shipyard/infrastructure physical footprint and approach geometry;
- local star-system space without gameplay map edge or hard movement wall;
- strict separation of physical coordinate space, generated operational/content envelope and render/materialization window;
- numerical-precision/floating-origin or equivalent strategy that preserves physical distances at far local coordinates;
- bounded simulation LOD without off-screen state loss/clamp/teleport;
- Stage-18 resource occurrence rules and finite reserves;
- extraction compatibility;
- infrastructure/shipyard requirements;
- logistics/economic geography;
- sensor-consistent discovery;
- Stage-19 strategic response times;
- bounded mostly-dormant scalability architecture;
- sectors as spatial/strategic regions rather than list partitions;
- explicit neighbor graph with measurable structural diversity instead of a sequential-chain production topology;
- a mix of hubs, forks, cycles, alternate paths, gateways, remote/frontier pockets and bounded chokepoints;
- machine-readable anti-linearity, route-redundancy, articulation/bridge and gateway-concentration diagnostics;
- spatially correlated resource geography derived from Stage-18 physical host/environment conditions plus local deterministic variance;
- regional comparative advantage instead of uniform self-sufficiency or `sector = production bonus` shortcuts;
- essential economic viability through physically reachable supply chains without requiring every system/sector to produce everything;
- strategic scarcity and dependency strong enough to create real trade, stockpiling, infrastructure, diplomacy, security, expansion and warfare incentives;
- faction-start placement after topology/resource generation, with asymmetric but recoverable starts and anti-accidental-monopoly checks;
- whole-route delivered-cost/dependency analysis over actual neighbor edges;
- deterministic world-quality gate with `ACCEPT / DETERMINISTIC_REPAIR / REJECT_SEED / EXPLICIT_SCENARIO_OVERRIDE` semantics;
- bad seeds or bad spatial-scale profiles rejected/recalibrated before materialization, never rescued by runtime hidden supplies/emergency deposits or hidden speed/range shortcuts.

Locked Stage-20 generation causality:

```text
Stage-17.5 ship/sensor/weapon capability
+ Stage-18 station/infrastructure capability
+ Stage-19 tactical/response behavior
→ versioned spatial-scale calibration profile
→ macro regions
→ system placement
→ neighbor topology
→ topology quality gate
→ local operational geometry
→ regional physical conditions
→ Stage-18 resource occurrences
→ facilities / economic bootstrap
→ faction-start candidates
→ delivered-cost / dependency analysis
→ whole-world quality gate
→ authoritative generated world
```

Generator may place resources/facilities but cannot invent hidden emergency resources to rescue a bad seed. Generated `system extent` may describe where meaningful content is concentrated, but it cannot clamp/delete/teleport ships at a map edge. Ordinary inter-system travel remains explicit neighbor-edge transition even when local space is unbounded.

Stage 20 cannot be marked COMPLETE until representative seed batches demonstrate all of the following simultaneously:

- galaxy is connected where ordinary production topology requires it, but not predominantly chain-like;
- core/developed regions have meaningful alternate-route coverage while chokepoints remain bounded strategic features;
- generated local distances produce coherent acceleration/braking/delta-v/travel-time differences between representative ships;
- sensor detection/track/fire-control and weapon/PD/formation envelopes remain physically meaningful and do not collapse into screen-space circles;
- station size/spacing and jump-arrival stand-off are compatible with traffic, logistics and defensive geometry;
- ships can move beyond visible/generated activity extents without world-edge clamp/delete/teleport, while far state remains deterministic through LOD;
- far-coordinate numerical precision remains inside calibrated tolerance;
- resource clusters are physically plausible and regionally recognizable without becoming uniform sector bonuses;
- typical starts are viable but meaningfully dependent on external trade/supply for part of growth or advanced industry;
- critical dependencies and gateway concentration are measurable from authoritative state;
- no normal seed requires hidden restock, teleport, emergency deposit, hidden movement/range multiplier or faction-only generation exception;
- player, NPC traders, faction logistics and warfare all consume the same generated geometry/resources.

Detailed plan: `docs/stage20_physical_world_generation_plan.md`.

## 10. Stage 21 — RPG / Living World

**PLANNED after Stage 20.**

NPCs, missions, discovery and reputation consume authoritative physical/economic/political state rather than a disconnected scripted world.

Living-world state must use persistent identity, relevance/cadence/event wakeups and deterministic deadlines; no `all NPCs × full AI × every tick` architecture.

## 11. Stage 22 — Content / Technology / Balance Alpha

**PLANNED after Stage 21.**

Expands the accepted physical/manufacturable language:

- material/technology families where meaningful;
- reactors/drives/thermal/sensors/EW/weapons/shields/protection;
- hull families and variants;
- faction engineering doctrines;
- facilities/shipyards;
- fleet composition;
- world-scale logistics and macroeconomic soak;
- anti-universal-build validation;
- anti-linear-tier-obsolescence validation;
- deterministic content/performance fingerprints.

Stage 22 explicitly owns the content review of the Stage-17.5 Combat Test Content Pack. Test hulls/modules/ammunition/fits must be re-authored, rebalanced, replaced or explicitly promoted according to the accepted technology ladder, Stage-18 industrial ontology, faction engineering doctrine and faction visual language. **Stage-17.5 prototype identity is never automatic canon.**

No isolated `Mk II = +25% all stats` parallel system.

Detailed plan: `docs/stage22_content_balance_plan.md`.

## 12. Stage 23 — Polish / Release Candidate

**PLANNED.**

UX/onboarding/accessibility/performance/content validation/save hardening after fundamental simulation/content architecture is stable.

Stage 23 replaces remaining prototype tactical presentation with production ship/projectile/VFX assets where not already finalized, without creating a new economy/physics model. It closes profiler budgets, migration diagnostics, long-session stability and release hardening.

## 13. Scalability cross-stage contract

Canonical scalability document: `docs/simulation_scalability_architecture.md`.

Required architecture:

```text
deterministic persistent world
→ scheduled/event-driven strategic simulation
→ simulation LOD
→ bounded local materialization
→ exact tactical model where required
→ deterministic dematerialization
→ headless benchmarks + invariants
→ profiling
→ measured optimization
```

No world-wide tactical/render-rate tick.

## 14. Current immediate sequence

```text
Stage 17 COMPLETE
→ Stage 17.5A production ship engineering schema COMPLETE
→ Stage 17.5B derived-ship calculator + fitting validator COMPLETE
→ Stage 17.5C propulsion / reaction mass / power / thermal / FTL COMPLETE
→ Stage 17.5D signatures / sensors / tracks / datalink / EW COMPLETE
→ Stage 17.5E kinetic / beam / guided / PD / ammunition COMPLETE
→ Stage 17.5F shields / armor / compartments / subsystem damage COMPLETE
→ Stage 17.5G shipyard / refit / repair / maintenance seam NEXT
→ Stage 17.5H capability APIs / UI / persistence
→ Stage 17.5I deterministic multi-fleet acceptance + Combat Test Content Pack + Tactical Prototype Visual Set
→ Stage 18 Resources / Industry / Infrastructure
→ Stage 19 Strategic Warfare
→ Stage 20 Physical World Generation
→ Stage 21 Living World
→ Stage 22 Content / Balance Alpha + re-author/review Stage-17.5 provisional content
→ Stage 23 RC / final presentation replacement and polish
```

**Immediate implementation priority after the Stage-17.5F merge gate is Stage 17.5G. Stage 17.5 cannot close until the Stage-17.5I combat-content/visual exit gate passes.**
