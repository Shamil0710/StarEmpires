# Star Empires — Stage 17.5A Production Ship Content Schema

> Статус: **IMPLEMENTED — awaiting exact-head merge gate**  
> Stage: **17.5A**  
> Production package: `com.spacesim.content.ship`  
> Machine-readable content: `src/main/resources/data/content/ship-engineering-v1.json`

---

## 1. Цель slice

Stage 17.5A переносит принятую `Ship Mathematics v1.0 Design Baseline` из research-only документов в production content language, не начиная преждевременно derived physics/combat runtime Stage 17.5B–F.

Production boundary:

```text
versioned engineering content
→ validation
→ immutable ShipEngineeringCatalog
→ stable semantic fingerprint
→ future Stage-17.5B derived-state calculator
```

Legacy `com.spacesim.content.ContentCatalog` и текущий Stage-13 combat vertical slice намеренно не заменяются в этом slice. Они остаются compatibility path до миграции archetype → installed fit в Stage 17.5B.

---

## 2. Реализованный production schema

`ShipEngineeringCatalog` определяет:

- `MaterialDefinition`;
- `HeavyImpactResponseSurfaceDefinition` + explicit `CalibrationDomainDefinition`;
- ordered `ProtectionStackDefinition` / `ProtectionLayerDefinition`;
- `HullDefinition`;
- `SlotDefinition`;
- `HardpointDefinition`;
- `CompartmentDefinition`;
- `ModuleDefinition`;
- physical ammunition / consumable / reaction-mass interfaces;
- Stage-18 construction/material seams;
- maintenance/repair metadata;
- machine-readable `DemonstratorFitDefinition`.

Все физические поля используют SI-compatible names/semantics: meters, kg, N, N·s, W, J, seconds и dimensionless authored coefficients where explicitly stated.

`HullArchitecture`, doctrine/role naming и module-family naming не дают hidden performance bonuses.

---

## 3. Frozen module-family language

Production schema способен описать все 15 family contracts v1.0:

1. `REACTOR_POWER`;
2. `ENERGY_STORAGE`;
3. `MAIN_DRIVE`;
4. `MANEUVER_THRUSTERS`;
5. `FTL_JUMP`;
6. `THERMAL_CONTROL`;
7. `SENSOR_EW_FIRE_CONTROL`;
8. `COMMUNICATION_DATALINK`;
9. `SHIELD_FIELD`;
10. `ARMOR_PROTECTION`;
11. `WEAPON_AMMUNITION`;
12. `CREW_LIFE_SUPPORT_AUTOMATION`;
13. `CARGO_TANK_STORES`;
14. `HANGAR_SMALL_CRAFT`;
15. `MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE`.

Family-specific `capabilityParameters` are allowed only as typed-by-name physical authoring payload consumed by later family solvers. Они не создают parallel mass/power/heat/economy budgets.

---

## 4. Loader / validation contract

`ShipEngineeringCatalogLoader` фиксирует:

```text
schemaVersion = 1
migrationVersion = 1
```

Loader rejects:

- malformed/blank documents;
- unsupported or missing schema/migration version;
- duplicate stable IDs;
- unknown material/protection/response/hull/module/mount references;
- negative or non-finite mandatory numeric values;
- invalid `[0,1]` coefficients;
- inverted response-surface calibration bounds;
- unbounded definition/child/parameter collections;
- response surface without explicit calibration domain;
- duplicate slot/hardpoint/compartment/interface IDs;
- slot ↔ hardpoint mount-ID collision;
- invalid hardpoint arc;
- invalid crew/life-support or bare/max-mass relation;
- module that does not fit slot category/mass/dimensional envelope;
- module that does not fit hardpoint family/size/mass/dimensional envelope;
- more than one module assigned to one demonstrator mount;
- hidden `classBonus`, `roleBonus`, `doctrineBonus` or `performanceBonus` fields.

Stage-18 material/component IDs are currently validated syntactically as forward seams. Semantic resolution against the full Stage-18 ontology belongs to Stage 18 and must not be invented inside 17.5A.

---

## 5. Machine-readable demonstrator

`ship-engineering-v1.json` contains a schema demonstrator based on an escort-destroyer-sized hull:

- two engineering materials;
- one explicitly synthetic bounded heavy-impact response surface;
- one ordered protection stack;
- one hull with integration slots, external weapon hardpoint and three compartments;
- reactor, main drive, sensor/EW/fire-control, thermal-control and large kinetic weapon modules;
- one valid installed demonstrator fit.

The synthetic heavy-impact surface exists only to prove API/calibration-domain semantics; it is not a claim about real armor performance.

The demonstrator is content evidence for 17.5A only. Its module parameters are not frozen final balance content.

---

## 6. Determinism / immutability

Catalog materialization:

- sorts independent definition sets by stable ID;
- defensively copies nested collection/map state before exposing the catalog;
- preserves semantically ordered protection layers;
- computes SHA-256 semantic fingerprint from canonicalized engineering content;
- produces the same fingerprint on deterministic reload independent from JSON whitespace.

This is the content identity seam that later persistence/runtime code can fingerprint without storing derived physics results.

---

## 7. Automated acceptance

`ShipEngineeringCatalogLoaderTest` covers:

- production default content load;
- all 15 frozen module-family names;
- stable reload fingerprint;
- immutable exposed collections/maps;
- schema/migration rejection;
- malformed documents;
- unknown references;
- duplicate IDs;
- invalid physical values/bounds;
- incompatible slot/hardpoint fits;
- exceeded dimensional envelope;
- duplicate mount assignment;
- hidden class-bonus rejection;
- bounded collection limits;
- mandatory heavy-impact calibration domain.

The implementation was validated by the repository Java-17 `clean verify` gate after the public schema API was made doclint-clean.

---

## 8. Explicit non-goals

Stage 17.5A does **not** yet:

- calculate ship total mass or fit budgets;
- calculate acceleration/delta-v;
- solve power/thermal state;
- materialize runtime ammunition/reaction mass;
- resolve sensors/EW/fire-control;
- resolve weapons/PD/guidance;
- resolve shields/armor/compartment damage;
- persist installed runtime fits;
- migrate existing legacy `shipArchetypes` into authoritative fitted ships.

These are deliberately left to 17.5B–I.

---

## 9. Handoff to Stage 17.5B

Immediate next slice:

> **Stage 17.5B — central derived-ship calculator + fitting validator.**

Required handoff:

```text
ShipEngineeringCatalog
+ HullDefinition
+ installed module assignments
+ initial consumable state seam
+ future damage state seam
→ DerivedShipState
→ fit validation result
```

17.5B must introduce one authoritative derivation boundary and must not duplicate the budgets already defined by 17.5A.

It must also define the controlled compatibility/migration path from existing Stage-13/14 archetype ships into production fitted-state semantics without respawning existing persistent asset IDs.

---

## 10. Completion criterion

Stage 17.5A is complete when the exact implementation head passes the manual merge gate and lands in `main` with post-merge CI green:

```text
machine-readable engineering demonstrator
→ production JSON loader
→ strict validation
→ immutable versioned catalog
→ stable semantic fingerprint
→ full repository CI green
```

After that gate, roadmap implementation priority moves to **17.5B**.
