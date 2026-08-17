# Stage 17.5H — Shared Capability APIs / UI / Persistence

Status: **COMPLETE** after exact-head CI verification of the implementation branch.

This document is the canonical implementation record for Stage 17.5H. It closes the live composition, read-model and persistence seams left intentionally open by Stage 17.5F/G. Stage 17.5I remains responsible for deterministic multi-fleet end-to-end acceptance, Combat Test Content Pack and Tactical Prototype Visual Set.

## 1. Purpose

Stage 17.5H turns the already-authoritative Stage-17.5A–G physical ship model into one continuous ship-instance runtime that can be queried by UI/player/AI code and moved through ECS/materialization/save/load/refit boundaries without restoring pristine combat state or granting resources.

The governing rule is:

```text
fitted physical state
+ current consumables
+ current operating state
+ current local damage
+ current shield state
+ current maintenance state
+ current weapon continuity
+ system-local information state
→ shared capability/read APIs
→ player / AI / UI consumers
```

No derived capability is an independent source of truth.

## 2. Authoritative ship-instance state

`EngineeringComponent` is the live owner of the physical fitted ship instance required by Stage 17.5H:

- installed `InstalledFit`;
- Stage-17.5C `ShipEngineeringRuntime.RuntimeState`;
- Stage-17.5F compartment and module damage;
- fitted shield reserve/collapse/restart state;
- Stage-17.5G maintenance/service-age state;
- weapon feed/ammunition identity bindings;
- launcher-cycle cooldown continuity.

This state is composed through `ShipInstanceRuntimeState` rather than duplicated into player-only, AI-only, UI-only or shipyard-only stores.

Hard invariant:

> Materialization, dematerialization, save/load, migration and refit are not repair/rearm/recharge actions.

## 3. Damage-aware common engineering runtime

`ShipEngineeringRuntime` now exposes authoritative damage-aware initialization, operating, FTL-planning and derived-state paths.

The former pristine seam is closed:

- production ship-instance code supplies the current `DamageState`;
- current module integrity reduces surviving power supply/demand, thermal transfer/rejection and fitted capability through the same engineering definitions used by `DerivedShipCalculator`;
- destroyed or thermally unavailable mounts do not operate;
- reaction mass remains physically consumed;
- shared-bus energy is bounded by surviving storage capacity/discharge power;
- FTL planning uses current fitted mass, power/storage, local heat, cooldown and subsystem integrity.

Compatibility overloads that use `DamageState.pristine()` remain only as explicit legacy/test compatibility paths. They are not the production ship-instance authority.

A dedicated regression locks the no-double-scaling requirement: a drive at 50% integrity produces 50% of its healthy thrust, not 25%, and live runtime thrust remains consistent with the central damage-aware derived model.

## 4. Common incremental engineering grant boundary

`ShipEngineeringGrantService` is the shared admission/commit boundary for incremental operations such as sensors, beam firing and shield recharge.

For each requested operation it checks and atomically commits:

```text
surviving continuous power margin
+ surviving physical ENERGY_STORAGE discharge envelope
+ actually stored shared-bus energy
+ mount-local surviving thermal capacity
→ accept and commit
or
→ reject without state mutation
```

The service has no player/AI branch.

Consequences:

- no sensor gets free electrical power;
- no beam gets free electrical power or free heat disposal;
- no shield recharges from a virtual battery;
- a ship without an `ENERGY_STORAGE` module cannot draw non-existent stored energy;
- denied operations do not partially consume energy or create heat.

Ordinary coolant transfer/radiator rejection remains owned by the engineering operating tick; the grant service does not create a second thermal simulation.

## 5. Shared capability API

`ShipCapabilityService` provides a read-only Stage-17.5H projection over the exact current `EngineeringComponent`.

Current query surface includes:

- acceleration/thrust envelope;
- remaining reaction-mass delta-v;
- damage-aware FTL plan;
- fitted shield coverage/reserve/collapse/restart state;
- continuous thermal margin and stored/local heat headroom;
- physical ammunition mass/count and loaded feed identities;
- compartment/module integrity;
- ordinary repair need;
- scheduled-service age/overdue mounts;
- fitted sensor suite.

The projection recomputes from current authoritative state. It does not cache a second mutable capability model.

## 6. Read-only UI projection

The engineering UI projection consumes `ShipCapabilityService` rather than mutating ECS arrays/components directly.

The H read model exposes the physical quantities required for inspection of a fitted ship, including current mass/acceleration/delta-v, power/thermal condition, ammunition, sensors, protection/damage, shields and maintenance state where available.

UI remains presentation/read-model code:

> UI can request commands through authoritative services, but it is not allowed to repair, recharge, rearm, change integrity, add power or rewrite engineering state by editing displayed values.

## 7. Sensor / beam / shield operation composition

Stage 17.5H preserves the existing Stage-17.5D/E/F physics and adds the missing engineering authority boundary.

### Sensors

Sensor observations continue to use the common information model and physical measurement/track/EW rules. Their operating power/heat admission is routed through the common engineering grant boundary.

### Beams

Beam geometry, diffraction, jitter, dwell and target exposure remain Stage-17.5E physics. Electrical demand and waste heat are admitted/committed through the common engineering grant boundary before the operation becomes authoritative.

### Shields

Shield reserve, coverage, collapse and interaction remain Stage-17.5F physics. Recharge uses explicitly granted electrical power and cannot mint field reserve independently from the ship power system.

Rendering/VFX remain non-authoritative and are intentionally left to the Stage-17.5I prototype presentation gate / Stage 23 production replacement.

## 8. Refit and maintenance continuity

Stage 17.5H consumes the Stage-17.5G `ShipyardRefitContinuity` / maintenance handoff at the live component boundary.

A refit must preserve or explicitly reconcile, rather than reset:

- same physical ship identity;
- retained module condition;
- removed-module physical handoff state;
- compartment damage;
- scheduled-service age;
- local heat/coolant/power state where the corresponding mount survives;
- shield state where the emitter survives;
- weapon feed identity and launcher cooldown where the weapon/feed survives.

Refit therefore cannot act as implicit repair, cooldown reset, free recharge or rearm.

## 9. Persistence model

### 9.1 Core GameState remains schema v4

`GameStateCodec` remains the backward-compatible binary core payload. Stage 17.5H does not force an unrelated core schema bump merely to serialize the new ship-instance extension.

Neutral engineering snapshots that do not require H extension data continue to retain exact core round-trip compatibility.

### 9.2 Production content-bound envelope is v2

`ContentBoundSaveCodec` envelope v2 serializes deterministic Stage-17.5H extension state keyed to stable entity identity.

Persisted H ship-instance fields include:

- compartment integrity by ID;
- module integrity by fitted mount;
- shield reserve;
- shield accumulated heat;
- shield collapse state;
- shield restart lockout;
- emitter integrity;
- service age by mount;
- weapon feed/interface → ammunition content ID;
- launcher cooldown by mount.

The envelope also persists system-local sensor knowledge where it is design-valid:

- fused tracks;
- received measurements;
- pending datalink measurements/delivery times.

Derived capability values are not serialized. They are reconstructed from content + fit + authoritative runtime state after materialization.

### 9.3 Legacy migration is neutral, never generous

Envelope v1 and historical raw `GameStateCodec` saves remain readable.

Missing H data migrates as neutral/absent state. Migration must not invent:

- charged shield reserve;
- ammunition feed identity;
- repaired compartment/module integrity;
- reset maintenance age;
- reset launcher cooldown;
- sensor tracks/measurements;
- power, propellant or other resources.

## 10. Information-state boundary

Sensor knowledge is not a physical ship capability and therefore remains conceptually separate from engineering damage/consumables.

Persistence of sensor knowledge is system/local identity-domain continuity, not omniscient global truth. Stage 17.5I/19/20 must preserve the Stage-17.5D information rules when moving ships between tactical/materialized and strategic representations.

A save/load path may preserve what an actor legitimately knew; it may not reveal hidden targets or regenerate stale/fire-control-quality knowledge from physical object positions.

## 11. Player / AI parity

All H production services are ownership-neutral:

```text
same fitted ship state
+ same information state
+ same requested physical action
→ same capability projection
→ same power/heat admission
→ same persistence consequences
```

No Stage-17.5H API contains a player-only accuracy, energy, repair, shield, ammunition or cooldown rule.

## 12. Acceptance evidence

The H implementation is covered by the existing full repository suite plus dedicated tests for:

- damage-aware propulsion and derived/runtime agreement;
- common engineering grant admission/denial and no-free-storage behavior;
- Stage-17.5H persistence extension and legacy compatibility;
- engineering ECS mapper round-trip;
- refit continuity;
- read-only capability projection;
- read-only engineering UI projection;
- sensors / shields / weapon engineering adapters and prior Stage-17.5D/E/F invariants.

Implementation exact-head checkpoint before closeout documentation:

```text
branch: agent/stage17-5h-capabilities-ui-persistence
SHA:    005188b274205346c2becaf0904600fd19b7566e
CI:     #2675 / run 32006802443
result: SUCCESS
```

The preceding functional checkpoint reported 819 tests, 0 failures and 0 errors; the only remaining failure was Javadoc warnings, subsequently corrected before CI #2675 passed the complete test/coverage/Javadoc/package job.

## 13. DoD 17.5H

Stage 17.5H is complete when all of the following hold:

- [x] live engineering consumes current module damage instead of silently using pristine damage;
- [x] shared capability APIs derive from current fit/consumables/damage/operating state;
- [x] UI projection is read-only;
- [x] sensor/beam/shield incremental power/heat use is admitted through common engineering grants;
- [x] no virtual energy storage exists when the fit has no physical storage;
- [x] local compartment/module damage persists across ECS/save/load boundaries;
- [x] shield continuity persists;
- [x] weapon feed identity and launcher-cycle continuity persist;
- [x] Stage-17.5G refit/maintenance continuity reaches the live component boundary;
- [x] legacy saves migrate without resource/capability grants;
- [x] system-local information persistence does not become global omniscience;
- [x] player and AI use the same physical capability boundaries;
- [x] full repository CI is green on the exact implementation head before closeout.

## 14. Handoff to Stage 17.5I

Stage 17.5I is now the next implementation slice.

It must not redesign H into a separate combat-state authority. Instead it must exercise this exact persistence/capability composition in representative production-valid, content-provisional combat assets and scenarios.

Required H-derived acceptance in 17.5I includes:

```text
damage / ammo / reaction mass / power / heat / shields / cooldowns / tracks
→ tactical materialization
→ combat actions
→ changed authoritative state
→ dematerialize / save / load / rematerialize
→ same physically relevant continuation
```

Stage 17.5I also owns the mandatory Combat Test Content Pack, five fleet doctrines, deterministic fleet matrix and Tactical Prototype Visual Set defined by `docs/stage17_5i_combat_test_content_visual_acceptance.md`.
