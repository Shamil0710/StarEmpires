# Stage 18B — Extraction / Source Compatibility

> Status: **COMPLETE — implementation and full branch gate green; becomes canonical on merge to `main`**  
> Stage owner: Stage 18 Resources / Industry / Infrastructure Foundation  
> Previous slice: **Stage 18A — resource/schema ontology COMPLETE**  
> Next slice after merge: **Stage 18C — refining / material production**

## 1. Purpose

Stage 18B turns the Stage-18A vocabulary into the first authoritative physical material-flow boundary:

```text
finite physical source
→ compatible extraction method
→ required physical capability
→ finite interval power/work/maintenance budget
→ grade/yield-limited recovery
→ compatible Stage-18 storage
→ extracted feedstock mass
+ explicitly accounted waste
```

The slice deliberately does not process ore/feedstock into engineering materials. That belongs to Stage 18C.

## 2. Compatibility architecture

The existing early-game mining loop remains a legacy compatibility path:

- `AsteroidComponent` stores integer legacy resource units;
- `MiningSystem` transfers integer `ItemType` units into the legacy `InventoryComponent`;
- the legacy path still uses `ItemType.ORE` and its existing save/runtime semantics.

Stage 18B does **not** reinterpret one legacy cargo unit as one kilogram. No implicit quantity conversion is introduced between the old inventory and the new physical economy.

The new physical path uses Stage-18 ontology IDs and ontology-native mass quantities in kilograms. A later explicit migration/integration slice must define any conserved conversion from legacy inventory units; semantic successor IDs from Stage 18A are not quantity-conversion rules.

## 3. Data-driven extraction catalog

Production extraction content is versioned in:

- `Stage18ExtractionCatalog`;
- `Stage18ExtractionCatalogLoader`;
- `data/content/stage18-extraction-v1.json`.

The catalog has its own deterministic semantic SHA-256 fingerprint and validates every occurrence/capability reference against the Stage-18A ontology.

Baseline methods:

| Method | Source | Environment | Required capability |
| --- | --- | --- | --- |
| `extraction.asteroid_excavation` | natural occurrence | `FREE_BODY` | `capability.extraction.asteroid_excavation` |
| `extraction.surface_mining` | natural occurrence | `SURFACE` | `capability.extraction.surface_mining` |
| `extraction.deep_mining` | natural occurrence | `DEEP_SUBSURFACE` | `capability.extraction.deep_mining` |
| `extraction.thermal_volatiles` | natural occurrence | `VOLATILE_BEARING` | `capability.extraction.thermal_volatiles` |
| `extraction.salvage_recovery` | pre-accounted salvage stream | `SALVAGE_SITE` | `capability.process.recycling` |

Each method authors ordinary physical/process requirements:

- compatible source/occurrence types;
- required capability tags;
- engineering work-seconds per source kilogram;
- electrical/process energy joules per source kilogram;
- maintenance work-seconds per source kilogram;
- maximum gross-source throughput in kg/s;
- method-side recovery fraction.

These definitions create no reserve, power, work, maintenance capacity or storage by themselves.

## 4. Finite physical source state

`Stage18ExtractionRuntime.PhysicalSourceState` stores:

```text
stable source ID
source kind
source/occurrence type ID
physical extraction environment
output commodity ID
initial accessible mass kg
remaining accessible mass kg
grade fraction
source-side recovery fraction
source-specific required capability tags
```

The state therefore distinguishes an occurrence from extracted cargo exactly as required by the Stage-18 plan.

For a natural source, `sourceTypeId` must resolve to a Stage-18 occurrence type. The selected output must be an `EXTRACTED_FEEDSTOCK` explicitly listed by that occurrence type.

A request larger than the final remaining reserve is capped only by that finite reserve and may complete with `EXTRACTED_DEPLETED`. A depleted source cannot produce more material.

## 5. Grade, recovery and mass accounting

For one committed operation:

```text
recovered output mass
= gross source mass removed
× source grade
× source recovery fraction
× extraction-method recovery fraction
```

Then:

```text
gross source mass removed
= recovered output mass
+ discarded/process waste mass
```

The runtime never creates net material mass. Waste is explicit in `ExtractionResult` even when it is not persisted as a tradable commodity/world object.

A valuable by-product is not invented implicitly; it requires an explicit later process/content definition.

## 6. Shared finite engineering interval

Stage 18B reuses the Stage-17.5G industrial work semantic rather than creating a second abstract production currency:

> `workRate` means engineering work-seconds completed per simulation second.

`ExtractionCapability.openInterval(durationSeconds)` creates a shared finite interval budget:

```text
available electrical/process energy = availablePowerW × interval seconds
available engineering work          = workRate × interval seconds
available maintenance service-work  = maintenanceWorkRate × interval seconds
```

Multiple extraction attempts can share the same `IntervalBudget`. A successful operation consumes its committed share. A later operation cannot reuse power/work/maintenance already spent earlier in the same interval.

Maintenance is represented here as finite service-work capacity only. Stage 18B does not claim that spare parts/materials are free; real maintenance material/component recipes and facility coupling belong to later Stage-18 industrial slices.

## 7. Physical storage boundary

`Stage18ExtractionRuntime.PhysicalCargoStore` is deliberately separate from legacy `InventoryComponent`.

It stores:

- capacity by Stage-18 storage-class ID;
- mass by Stage-18 commodity ID.

Only mass-based ontology commodities are accepted. Commodity mass can enter only storage matching the commodity's authored storage class. Capacity in an unrelated tank/hold is not interchangeable.

This is the first physical Stage-18 storage settlement seam. Stage 18F remains responsible for making the richer storage/logistics model authoritative across ships, stations and transport instead of maintaining a second isolated cargo system.

## 8. Atomic settlement / rejection semantics

`Stage18ExtractionRuntime.extract(...)` validates before committing:

1. request and stable method ID;
2. finite source availability;
3. source kind;
4. extraction environment;
5. occurrence compatibility;
6. physically valid output commodity;
7. required method and source-specific capabilities;
8. method throughput for the interval;
9. finite energy;
10. finite engineering work;
11. finite maintenance work;
12. compatible remaining storage capacity.

If any requirement fails, the operation commits nothing:

```text
source reserve unchanged
+ cargo unchanged
+ interval energy unchanged
+ engineering work unchanged
+ maintenance work unchanged
```

Successful settlement mutates all coupled physical states together.

Stable rejection outcomes include:

- `DEPLETED`;
- `METHOD_NOT_FOUND`;
- `SOURCE_KIND_INCOMPATIBLE`;
- `ENVIRONMENT_INCOMPATIBLE`;
- `OCCURRENCE_INCOMPATIBLE`;
- `OUTPUT_INCOMPATIBLE`;
- `MISSING_CAPABILITY`;
- `THROUGHPUT_LIMIT`;
- `INSUFFICIENT_POWER`;
- `INSUFFICIENT_WORK`;
- `INSUFFICIENT_MAINTENANCE`;
- `STORAGE_FULL`.

## 9. Salvage boundary

`extraction.salvage_recovery` operates only on `SALVAGE_STREAM`, never on a geological occurrence.

This Stage-18B source is explicitly **pre-accounted**. The runtime can consume a finite salvage stream but cannot manufacture that stream from nothing.

Stage 18H owns the authoritative origin chain:

```text
actually manufactured ship/station/infrastructure
→ real damage/destruction state
→ physically available wreck/debris material
→ bounded salvage stream
→ recovery/recycling
```

Therefore the Stage-18B salvage path is a compatibility/process seam, not a random-loot or free-resource generator.

## 10. Acceptance coverage

`Stage18ExtractionCatalogLoaderTest` verifies:

- the required production method set;
- stable source/environment semantics;
- deterministic extraction fingerprint;
- ontology reference validation;
- rejection of duplicate/invalid methods;
- rejection of salvage definitions masquerading as geological extraction.

`Stage18ExtractionRuntimeTest` verifies:

- finite reserve depletion;
- grade/source-yield/method-yield recovery;
- exact source/output/waste mass accounting;
- finite power consumption;
- finite engineering work consumption;
- finite maintenance service-work consumption;
- compatible storage capacity;
- shared same-interval budget contention;
- no mutation on rejected operations;
- method/source/environment/occurrence/output compatibility;
- method and source-specific capability requirements;
- throughput limits;
- final-reserve depletion behavior;
- bounded pre-accounted salvage recovery.

On implementation head `d669843c0e2271d84fccc48bae69008375deff76`, the repository full gate passed:

```text
Tests run: 892
Failures: 0
Errors: 0
Skipped: 0
JaCoCo: PASS
strict Javadoc: PASS
desktop shaded package: PASS
```

The documented exact head, PR head and post-merge `main` must still pass the normal repository gates before the slice is considered merged canonical state.

## 11. Explicit non-goals / deferred ownership

Stage 18B does not implement:

- feedstock → engineering-material refining recipes — Stage 18C;
- industrial component/module/ammunition recipes — Stage 18D;
- installed-facility capability architecture — Stage 18E;
- authoritative ship/station storage and logistics integration — Stage 18F;
- shipyard material/component integration — Stage 18G;
- derivation of salvage mass from destroyed manufactured assets — Stage 18H;
- full industrial-loop persistence acceptance — Stage 18I;
- procedural resource placement/geology — Stage 20;
- implicit legacy `ItemType` unit → kilogram conversion — forbidden until an explicit conserved migration exists.

`PhysicalSourceState` and `PhysicalCargoStore` are shaped for later persistence mapping, but Stage 18B does **not** claim that these new physical states have already been added to the production save envelope.

## 12. Exit decision

Stage 18B satisfies its implementation DoD:

- occurrence → extraction-method compatibility is explicit;
- accessible reserve is finite;
- grade and bounded yield affect recovered mass;
- extraction consumes ordinary finite power, engineering work and maintenance work;
- asteroid/surface/deep/volatile/salvage baseline paths are data-driven;
- incompatible extraction cannot mutate physical state;
- recovered mass requires compatible physical storage;
- no player-only efficiency, hidden supply or virtual resource path exists;
- no legacy quantity is silently reinterpreted as kilograms;
- no salvage mass can originate inside the extraction runtime itself.

After exact documented-head, PR-head, exact-SHA merge and post-merge `main` gates pass, **Stage 18B is canonical COMPLETE and Stage 18C becomes NEXT**.
