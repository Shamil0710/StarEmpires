# Star Empires — канонический roadmap разработки

> **Последняя синхронизация: 2026-08-19 / Stage 19 COMPLETE — Stage 19J ACCEPTED; Stage 20 ACTIVE.**  
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
- UI, напрямую мутирующий authoritative simulation state;
- отдельная simplified large-battle physics только потому, что в бою много кораблей;
- viewer-owned movement/targeting/combat authority.

## 2. Milestones

| Milestone | Цель | Stages | Статус |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | deterministic economic core | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | multi-system factions/logistics/construction/expansion | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | player ship/travel/trade/mining/combat/progression | 12–14 | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | fleets/stations/player faction/combat depth/industry/warfare | 15–19 + 17.5 | **COMPLETE** |
| **v0.5 RPG & Living World** | calibrated world generation/discovery/NPC/missions/reputation | 20–21 | **ACTIVE — Stage 20** |
| **v0.6 Content & Balance Alpha** | technology/content breadth + long-horizon balance | 22 | PLANNED |
| **v0.7 Polish / RC** | UX/onboarding/performance/save hardening | 23 | PLANNED |

Manual merge gate remains mandatory while `main` is unprotected:

```text
branch from exact green main
→ clean verify on exact PR head
→ inspect exact diff/head SHA
→ merge that exact SHA only
→ verify resulting main / available post-merge CI
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

**COMPLETE — 17F.1–17F.7.** Doctrine, fiscal policy, treasury/station/construction trade-offs, stock/production policy, resilience policy, bounded persistent review/anti-oscillation and shared player/AI `FactionPolicyCommand` path.

Hard rule:

```text
policy authoring
≠ money/cargo/output creation
```

Policy consequences materialize only through ordinary market/logistics/production/treasury/construction systems.

### 17G — faction management / strategic projection

**COMPLETE.** Immutable management/global-map projections and command facade delegate to authoritative treasury, policy, diplomacy and territorial law.

Canonical closeout: `docs/stage17g_faction_management_completion_record.md`.

### 17H — persistence / migration / final transition gate

**COMPLETE.** Existing fleet/station identities survive faction founding, capitalization, policy, diplomacy, territorial state and binary persistence without free resources or ID replacement.

Canonical closeout: `docs/stage17h_persistence_transition_completion_record.md`.

## 5. Stage 17.5 — Combat Depth / Ship Fitting Foundation

**COMPLETE — 17.5A–17.5I.**

Accepted research/design prerequisite:

- `docs/ship_mathematics_v1_0_design_baseline.md` — **ACCEPTED DESIGN BASELINE**;
- `docs/benchmarks/ship_mathematics_v1_0_design_baseline.json`;
- `docs/ship_mathematics_v1_roadmap_integration_contract.md`;
- `docs/ship_hull_module_and_fleet_doctrine.md`;
- `docs/flight_dynamics_and_combat_depth_roadmap.md`;
- `docs/stage17_5_combat_depth_implementation_plan.md`.

Completed slices:

- **17.5A — COMPLETE:** production material/hull/module/protection/slot/hardpoint/compartment content schema, loader/validation and semantic fingerprint;
- **17.5B — COMPLETE:** central deterministic derived-ship calculator, fitting validator, physical-load movement bridge and ID-preserving compatibility seam;
- **17.5C — COMPLETE:** propulsion/reaction mass/power/thermal/FTL runtime, persistent engineering state and single-FSM fitted jump integration;
- **17.5D — COMPLETE:** signatures/sensors/tracks/datalink/EW, fitted sensor adapter and common player/AI information model;
- **17.5E — COMPLETE:** kinetic/beam/guided/PD/ammunition, individual physical bodies, deterministic fire control, physical beam geometry/dwell and layered defense;
- **17.5F — COMPLETE:** finite shields, bounded no-extrapolation material response, compartments/subsystem damage and damage-aware capabilities;
- **17.5G — COMPLETE:** shipyard/refit/repair/maintenance capability and physical input/work economy seam;
- **17.5H — COMPLETE:** capability APIs/UI/live composition, common engineering grants and persistence continuity;
- **17.5I — COMPLETE:** deterministic multi-fleet acceptance, five doctrine fits, saturation matrix, persistence and tactical visual acceptance.

Canonical records:

- `docs/stage17_5a_production_ship_content_schema.md`;
- `docs/stage17_5b_derived_ship_calculator_and_fitting_validator.md`;
- `docs/stage17_5c_propulsion_power_thermal_ftl.md`;
- `docs/stage17_5d_signatures_sensors_tracks_datalink_ew.md`;
- `docs/stage17_5e_weapons_ammunition_guidance_layered_defense.md`;
- `docs/stage17_5f_shields_armor_compartments_subsystem_damage.md`;
- `docs/stage17_5g_shipyard_refit_repair_maintenance.md`;
- `docs/stage17_5h_capability_ui_persistence.md`;
- `docs/stage17_5i_implementation_record.md`;
- `docs/stage17_5i_combat_test_content_visual_acceptance.md`.

Stage-17.5 test assets remain **production-valid but content-provisional**. Stage 22 must re-author/rebalance/replace them or explicitly promote individual definitions after review.

Hard invariants retained:

- no player-only combat physics;
- no class-name bonuses;
- no free ammunition/reaction mass;
- no final global-HP-only survivability model;
- every module uses shared mass/volume/power/heat/economy budgets;
- persistent ↔ tactical materialization cannot reset state;
- Combat Test Content Pack cannot use hidden test-only combat stats;
- tactical presentation cannot become authoritative combat state.

## 6. Stage 18 — Resources / Industry / Infrastructure Foundation

**COMPLETE.** Stage 18 defines **what physically/economically exists** before world generation decides **where it exists**.

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

Implemented production foundation includes:

- versioned resource ontology;
- extraction compatibility and finite feedstocks;
- refining/material transformation;
- heavy/electrical/precision component layer;
- manufacturing recipes for modules/ammunition;
- facility capability requirements;
- station infrastructure/storage/logistics;
- shipyard/refit/repair industrial integration;
- facility construction;
- salvage/recycling;
- deterministic industrial acceptance and persistence;
- warfare supply integration using ordinary physical stocks rather than hidden replenishment.

Baseline raw families remain:

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

Canonical implementation plan: `docs/stage18_resources_industry_infrastructure_plan.md`.

Stage 18 supplies the physical cost/replenishment basis consumed by Stage 19 warfare and the ontology consumed by Stage 20 resource geography.

## 7. Stage 19 — Strategic Warfare / Coercive Diplomacy / Advanced Combat Behavior

**COMPLETE — 19A–19J accepted.**

Canonical acceptance artifacts:

- `docs/stage19_scaled_live_tactical_ai_acceptance.md`;
- `docs/stage19i_exit_evidence_matrix.md`;
- `docs/stage19j_tactical_validation_viewer.md` — **COMPLETE STAGE-19J CONTRACT**;
- `docs/stage19j7_long_run_acceptance_record.md` — **FINAL LONG-RUN CLOSEOUT EVIDENCE**.

Stage 19 consumes Stage-17 political state, Stage-17.5 physical ship capability and Stage-18 industrial/logistics network.

```text
crisis / war goal
→ mobilization + treasury pressure
→ ammunition / reaction mass / repair / replacement demand
→ physical logistics/readiness
→ actor-bounded tactical operations
→ real losses/blockades/industrial/territory effects
→ negotiated political/economic outcome
```

Strategic warfare is physical rather than an abstract production debuff: interdiction, route disruption, supply consumption, destruction and recovery operate through ordinary state/economy/logistics systems.

### Stage 19I scaled tactical authority — accepted

Accepted shared chain:

```text
scenario physical state
→ actor-bounded AI + TrackState
→ engineering power / heat / reaction mass
→ FlightDynamics
→ fire control
→ kinetic / fitted beam / guided execution
→ PD / interceptor / EW / decoy
→ shield / material / compartment / subsystem damage
→ changed capability / AI
→ next fixed tick
→ read-only live projection
```

Mandatory scale ladder is green on the same production runtime:

- 1v1 regression;
- shared 4v4;
- mixed 8v8;
- damaged/depleted 8v8;
- 16v16 / 32 exact-local ships;
- dense 32-ship saturation with kinetic + STRIKE + interceptor + decoy bodies concurrently.

Accepted behavior/evidence includes:

- actor-local target selection and reassignment;
- range/pursuit/disengagement behavior;
- compact/dispersed formation keeping, break and recovery;
- authored withdrawal objective with formation yielding to survival authority;
- finite full/partial/depleted ammunition;
- reaction-mass depletion;
- power and thermal denial through production engineering→sensor→track causality;
- fresh/pre-damaged behavior differences;
- EW/ECCM, passive/datalink missile warning and physical deception;
- finite interceptor rounds/cooldowns/support channels;
- no immediate fixed-tick A→B→A target/order/formation churn in the scaled soak;
- deterministic live/headless parity;
- pause/resume, exact single-step, deterministic reset and X1/X2/X4/X8 fixed-tick batching;
- read-only debug projection and runnable scaled live viewer;
- measured body/sensor/AI/ordnance/memory workload.

Final Stage-19I hardening additionally closed:

- fitted C-fit beam emitters through actor-local fire control + physical beam geometry/dwell + real incremental power/heat; no unauthored laser-armor-DPS coefficient is invented;
- explicit provisional 2,000 kg material-response envelope for the authored strike missile, with 2,001 kg still rejected rather than extrapolated;
- deterministic same-tick guided impact ordering audit with a non-vacuous detector and accepted scaled evidence of real ship-impact candidates with zero physically earlier interceptor contacts suppressed by current phase ordering.

### Stage 19J — tactical validation viewer, scenario coverage, readability, and inspection — COMPLETE

Stage 19J closed the mandatory final Stage-19 hardening slice rather than deferring combat validation usability to Stage 23. Accepted scope includes:

- one interactive production-runtime viewer path for **1v1 Legacy Duel, 4v4 Balanced, 8v8 Mixed, 8v8 Damaged/Depleted, 16v16 Mixed and 16v16 Saturation**;
- scenario-aware deterministic reset and a unified launcher/menu/direct scenario argument;
- distinct schematic visual language for **KINETIC, MISSILE, BEAM, DEFENSIVE/EW and BALANCED** ships;
- side-readable rendering: ALPHA cool cyan/blue, BETA warm orange/red, plus non-color cues;
- mouse ship selection, empty-space deselection and selected-ship highlight;
- read-only inspection panel with enlarged schematic model preview plus authoritative identity, condition, kinematics, weapon/ammunition, target/track and engineering data where available;
- clearer HUD with scenario/tick/speed/zoom/alive/selection information;
- camera zoom/pan and optional labels sufficient for both small and 32-ship battles;
- long-run regression/soak coverage crossing normal damage, depletion and stale/lost-contact states without uncaught exceptions;
- no viewer-owned combat truth, no hidden information shortcut and no simplified per-scale combat engine.

Final Stage-19J soak evidence includes all six scenarios, with the 16v16 Saturation case completing **600 simulated seconds / 12,000 fixed ticks**, observing real track loss and damage while remaining exception-free. The closeout soak also discovered and fixed a low-energy residual-projectile calibration-boundary defect without weakening the strict no-extrapolation material-response validation API.

Canonical detailed contract: `docs/stage19j_tactical_validation_viewer.md`.  
Canonical long-run closeout: `docs/stage19j7_long_run_acceptance_record.md`.

The Stage-17.5/19 combat content remains provisional. Stage 22 still owns final content re-authoring/balance and may replace the Stage-19 provisional 2 t calibration promotion with final material evidence.

## 8. Stage 20 — Physical World Generation / Discovery

**ACTIVE — Stage 19J exit gate passed.**

Stage 20 answers **where the already-defined world exists**. It must calibrate generated geometry around the physical/industrial/tactical behavior proven by Stages 17.5–19, rather than inventing map distances first and forcing combat/logistics to fit afterward.

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
- Stage-17.5/19 sensor/signature/track/fire-control behavior;
- kinetic/beam/guided/PD/formation physical engagement geometry;
- Stage-18 station/shipyard/infrastructure physical footprint and approach geometry;
- local star-system space without gameplay map edge or hard movement wall;
- strict separation of physical coordinate space, generated operational/content envelope and render/materialization window;
- numerical-precision/floating-origin or equivalent strategy preserving physical distances at far local coordinates;
- bounded simulation LOD without off-screen state loss/clamp/teleport;
- Stage-18 resource occurrence rules and finite reserves;
- extraction compatibility;
- infrastructure/shipyard requirements;
- logistics/economic geography;
- sensor-consistent discovery;
- Stage-19 tactical and strategic response times;
- bounded mostly-dormant scalability architecture;
- sectors as spatial/strategic regions rather than list partitions;
- explicit neighbor graph with measurable structural diversity rather than a sequential chain;
- hubs, forks, cycles, alternate paths, gateways, remote/frontier pockets and bounded chokepoints;
- machine-readable anti-linearity, route-redundancy, articulation/bridge and gateway-concentration diagnostics;
- spatially correlated resource geography derived from Stage-18 physical host/environment conditions plus local deterministic variance;
- regional comparative advantage instead of uniform self-sufficiency or `sector = production bonus` shortcuts;
- essential economic viability through physically reachable supply chains without requiring every system/sector to produce everything;
- strategic scarcity/dependency strong enough to create trade, stockpiling, infrastructure, diplomacy, security, expansion and warfare incentives;
- faction-start placement after topology/resource generation, with asymmetric but recoverable starts and anti-accidental-monopoly checks;
- whole-route delivered-cost/dependency analysis over actual neighbor edges;
- deterministic world-quality gate with `ACCEPT / DETERMINISTIC_REPAIR / REJECT_SEED / EXPLICIT_SCENARIO_OVERRIDE` semantics;
- bad seeds or spatial profiles rejected/recalibrated before materialization, never rescued by hidden supplies/deposits or speed/range shortcuts.

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

Stage 20 cannot be marked COMPLETE until representative seed batches demonstrate simultaneously:

- connected production topology where required, but not predominantly chain-like;
- meaningful alternate-route coverage in core/developed regions while chokepoints remain bounded strategic features;
- coherent acceleration/braking/delta-v/travel-time differences between representative ships;
- sensor detection/track/fire-control and weapon/PD/formation envelopes remain physically meaningful rather than screen-space circles;
- station size/spacing and jump-arrival stand-off are compatible with traffic, logistics and defensive geometry;
- ships can move beyond visible/generated activity extents without world-edge clamp/delete/teleport while far state remains deterministic through LOD;
- far-coordinate numerical precision remains inside calibrated tolerance;
- resource clusters are physically plausible and regionally recognizable without uniform sector bonuses;
- typical starts are viable but meaningfully dependent on external trade/supply for part of growth or advanced industry;
- critical dependencies and gateway concentration are measurable from authoritative state;
- no normal seed requires hidden restock, teleport, emergency deposit, hidden movement/range multiplier or faction-only generation exception;
- player, NPC traders, faction logistics and warfare all consume the same generated geometry/resources.

Detailed plan: `docs/stage20_physical_world_generation_plan.md`.

Current Stage-20F foundation: `docs/stage20f_industrial_specialization_candidate_plan_v1.md`.
It reconstructs exact generated station/facility/storage/extraction/process candidate evidence from
one accepted root seed, but deliberately does not promote candidates into operational specialization
until facility operating state, initial inventory, shared-input reservation, owned input freight and
installed-yard authority are explicit.

## 9. Stage 21 — RPG / Living World

**PLANNED after Stage 20.**

NPCs, missions, discovery and reputation consume authoritative physical/economic/political state rather than a disconnected scripted world.

Living-world state must use persistent identity, relevance/cadence/event wakeups and deterministic deadlines; no `all NPCs × full AI × every tick` architecture.

## 10. Stage 22 — Content / Technology / Balance Alpha

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

Stage 22 explicitly owns the content review of the Stage-17.5/19 provisional Combat Test Content Pack. Test hulls/modules/ammunition/fits and provisional calibration definitions must be re-authored, rebalanced, replaced or explicitly promoted according to the accepted technology ladder, Stage-18 industrial ontology, faction engineering doctrine and faction visual language. **Prototype identity is never automatic canon.**

No isolated `Mk II = +25% all stats` parallel system.

Detailed plan: `docs/stage22_content_balance_plan.md`.

## 11. Stage 23 — Polish / Release Candidate

**PLANNED.**

UX/onboarding/accessibility/performance/content validation/save hardening after fundamental simulation/content architecture is stable.

Stage 23 replaces remaining prototype tactical presentation with production ship/projectile/VFX assets where not already finalized, without creating a new economy/physics model. It closes profiler budgets, migration diagnostics, long-session stability and release hardening.

## 12. Scalability cross-stage contract

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

## 13. Current immediate sequence

```text
Stage 17 COMPLETE
→ Stage 17.5 Combat Depth / Ship Fitting Foundation COMPLETE
→ Stage 18 Resources / Industry / Infrastructure COMPLETE
→ Stage 19 Strategic Warfare / Coercive Diplomacy / Advanced Combat Behavior COMPLETE
   → Stage 19J Tactical Validation Viewer / Scenario Coverage / Readability / Inspection COMPLETE
→ Stage 20 Physical World Generation / Discovery ACTIVE
→ Stage 21 RPG / Living World
→ Stage 22 Content / Balance Alpha + re-author/review provisional combat content
→ Stage 23 RC / final presentation replacement and polish
```

**Immediate implementation priority is Stage 20.** Stage 19J has closed the interactive scenario/readability/selection/inspection/camera/long-run runtime gate, with final evidence recorded in `docs/stage19j7_long_run_acceptance_record.md`. Stage 20 now consumes the accepted Stage-17.5/18/19 physical capability baseline for calibrated topology, local geometry, resource geography and discovery implementation.
