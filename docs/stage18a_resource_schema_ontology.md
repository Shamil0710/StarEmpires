# Stage 18A — Resource / Schema Ontology

> Status: **COMPLETE — merged by PR #159; post-merge `main` gate green**  
> Stage owner: Stage 18 Resources / Industry / Infrastructure Foundation  
> Next slice: **Stage 18B — extraction/source compatibility and finite occurrence state**

## 1. Purpose

Stage 18A establishes the stable, data-driven physical/economic vocabulary that later Stage-18 systems consume.

Canonical dependency remains:

```text
resource occurrence
→ extracted feedstock
→ refining / separation / purification
→ engineering material / industrial consumable
→ industrial component
→ module / ammunition / infrastructure
→ ship / station
→ operation / wear / damage
→ salvage / recycling
```

Stage 18A defines the schema and identities only. It does **not** create free reserves, production, recipes, facilities, extraction yield or world placement.

## 2. Compatibility architecture

The existing `ContentCatalog` remains authoritative for the current save-bound early-game item runtime and keeps its existing five historical item IDs:

- `item.ore`;
- `item.energy`;
- `item.food`;
- `item.steel`;
- `item.weapons`.

Stage 18A deliberately does **not** rewrite `catalog-v1.json`, dense item runtime IDs or the existing content/save fingerprint. Instead it introduces a separate versioned ontology:

- `Stage18ResourceOntologyCatalog`;
- `Stage18ResourceOntologyLoader`;
- `Stage18ResourceOntologySerializer`;
- `data/content/stage18-resource-ontology-v1.json`.

This prevents an ontology-only stage from silently converting old inventories or invalidating saves before physical conversion rules exist.

## 3. Canonical extracted feedstock families

The production ontology defines exactly the baseline families accepted by the Stage-18 design:

```text
WATER_ICE
VOLATILE_FEEDSTOCK
CARBONACEOUS_FEEDSTOCK
METALLIC_ORE
LIGHT_METAL_MINERALS
CONDUCTOR_ORE
STRATEGIC_METAL_ORE
SILICATE_MINERALS
FISSILE_MINERALS
```

Each uses a stable data-driven content ID under `commodity.feedstock.*` and a physical quantity basis of kilograms.

## 4. Engineering materials and industrial consumables

The ontology defines the baseline processed families:

```text
PURIFIED_WATER
INDUSTRIAL_GASES
INDUSTRIAL_CHEMICALS
STRUCTURAL_ALLOY
LIGHT_ALLOY
CONDUCTOR_METAL
REFRACTORY_ALLOY
CERAMIC_GLASS
CARBON_MATERIAL
ELECTRONIC_GRADE_MATERIAL
REACTOR_FUEL
```

`REACTOR_FUEL` remains an ontology family, not a claim that every reactor uses the same transported fuel. Technology-specific compatibility remains a later content/runtime concern.

## 5. Component layer

Stage 18A defines the compact component layer required to prevent ore-to-cruiser shortcuts:

```text
HEAVY_COMPONENTS
ELECTRICAL_COMPONENTS
PRECISION_COMPONENTS
```

No recipes or manufacturing yields are authored in 18A; Stage 18D owns real component and module/ammunition recipes.

## 6. Storage classes

The ontology introduces stable storage identities distinct from the old three-value `ItemCategory` model:

- `storage.dry_bulk`;
- `storage.liquid_tank`;
- `storage.pressurized_gas`;
- `storage.general_container`;
- `storage.hazardous_controlled`;
- `storage.high_value_controlled`;
- `storage.oversized`.

Each currently carries an explicit compatibility bridge to legacy `ItemCategory`. Stage 18F will make the richer storage classes authoritative for cargo/storage compatibility instead of inventing a second transport rule.

## 7. Process / capability tags

Stable capability IDs are defined for:

- asteroid/free-body excavation;
- surface mining;
- deep mining;
- thermal volatile extraction;
- atmospheric harvesting;
- beneficiation;
- volatile processing;
- bulk refining;
- advanced materials;
- chemical processing;
- heavy fabrication;
- electrical fabrication;
- precision/electronics fabrication;
- recycling.

These are capability identities only. They grant no output, multiplier or free throughput by themselves.

## 8. Resource occurrence boundary

Stage 18A defines **occurrence types** that identify which extracted feedstock families can physically exist in an occurrence.

It intentionally does not yet instantiate authoritative reserves.

```text
Stage 18A occurrence type
= stable physical resource-family vocabulary

Stage 18B occurrence state
= occurrence ID + host + finite reserve + grade/yield + extraction environment/capability constraints

Stage 20
= procedural placement/geography of those already-defined occurrence types/states
```

This keeps the roadmap dependency intact: Stage 18 defines what exists before Stage 20 decides where it exists.

## 9. Legacy migration dispositions

Every current `ItemType` has an explicit Stage-18 disposition:

| Legacy item | Stage-18 disposition |
| --- | --- |
| `item.ore` | semantic successor: `METALLIC_ORE`; no quantity conversion yet |
| `item.energy` | retain until utility/processed-consumable model is physical; no universal-fuel mapping |
| `item.food` | retain as legacy civilian good until life-support/agriculture integration |
| `item.steel` | semantic successor: `STRUCTURAL_ALLOY`; no quantity conversion yet |
| `item.weapons` | retain until Stage 18D connects real module/ammunition recipes |

A semantic successor is **not** an automatic save migration. Existing quantities remain unchanged until a later Stage-18 slice supplies an explicit conserved conversion path.

## 10. Determinism and validation

The loader rejects:

- unsupported schema versions;
- malformed/duplicate IDs;
- unknown storage references;
- occurrence types referencing non-feedstock commodities;
- duplicate occurrence feedstock references;
- legacy successor references to unknown commodities;
- missing mandatory production baseline families;
- a default legacy item without an explicit migration disposition.

The catalog exposes a semantic SHA-256 fingerprint independent of authored JSON ordering.

`Stage18ResourceOntologySerializer` provides deterministic canonical JSON. Acceptance verifies:

```text
load
→ canonical serialize
→ parse
→ canonical serialize
→ identical canonical JSON
→ identical semantic fingerprint
```

## 11. Explicit non-goals / deferred ownership

Stage 18A does not implement:

- finite reserve depletion — Stage 18B;
- grade/yield and extraction compatibility — Stage 18B;
- extraction work/power/maintenance settlement — Stage 18B;
- refining/material recipes — Stage 18C;
- component/module/ammunition recipes — Stage 18D;
- facility throughput/power/work/storage/location rules — Stage 18E;
- authoritative cargo/storage compatibility migration — Stage 18F;
- shipyard material integration — Stage 18G;
- salvage/recycling material closure — Stage 18H;
- procedural resource geography — Stage 20.

## 12. Acceptance evidence

Early implementation checkpoint `309cca98...` executed the full suite with **878 tests, 0 failures/errors/skips**. That checkpoint failed only the repository's strict Javadoc gate because compact record constructors lacked duplicated `@param` documentation.

The Javadoc defect was corrected on `e28a19a4...`; the final documented Stage-18A feature head passed the complete `clean verify` gate including tests, JaCoCo coverage, strict Javadoc and desktop packaging.

PR **#159** was merged. The resulting `main` commit is `267e2e6eae0124e860fed8295e5d570a60edbb53`, and post-merge CI **#2840** completed successfully.

## 13. Exit decision

Stage 18A satisfies its defined DoD and is canonical **COMPLETE**:

- production-grade data-driven ontology definitions exist;
- no class/doctrine bonus path is introduced;
- stable IDs are explicit;
- deterministic semantic fingerprint exists;
- deterministic serialization exists;
- all five current early-game items have an explicit migration/mapping disposition;
- no free resource, reserve, recipe, facility output or quantity migration is invented.

**Stage 18B is the active successor slice.**
