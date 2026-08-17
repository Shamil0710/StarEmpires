# Stage 18H — Bounded salvage, recycling and physical facility construction

Status: implementation slice on `agent/stage18h-salvage-recycling-construction`.

## Purpose

Stage 18H closes two missing physical loops in the Stage-18 economy:

```text
constructed ship state
→ destruction / damage
→ bounded salvage streams
→ existing Stage-18B recycling recovery
→ recovered ordinary commodities
```

and

```text
ordinary Stage-18 materials/components
→ physical delivery to construction site
→ finite engineering work/time
→ installed Stage-18E facility
```

The governing rule is that neither destruction nor construction may create mass or capability from an abstract station/faction bonus.

## Legacy boundaries remain explicit

The existing Stage-9 `DestructionService` and Stage-16 `ConstructionProjectService` continue to own their historical save-bound legacy inventory semantics.

They use integer `InventoryComponent` quantities. Stage 18H does **not** reinterpret those integers as kilograms.

Instead Stage 18H adds the kg-native industrial boundary required by the Stage-18 ontology while preserving the same Stage-16 construction causality:

```text
physical delivery
→ waiting for all required material
→ finite work/time
→ completion
```

Final integration of these snapshots into the complete world save codec remains Stage 18I.

# 1. Bounded ship salvage

`Stage18SalvageRuntime` is the only Stage-18H author of pre-accounted salvage streams for the current ship baseline.

It derives mass only from physical construction definitions that already exist:

- bare hull composition comes from the mass-closed Stage-18G hull bill;
- installed module composition comes from Stage-18D mass-closed manufacturing profiles and the authoritative physical module mass;
- current compartment/module integrity can only reduce accessible mass.

No `SALVAGE_STREAM` may appear from nothing.

## 1.1 Hull salvage baseline

The current engineering model does not yet assign each specific construction commodity to individual hull compartments. Stage 18H therefore uses an explicit V1 approximation:

```text
hull survival fraction
= arithmetic mean(current compartment integrity)
```

For every Stage-18G hull input:

```text
accessible hull salvage mass
= original constructed input mass × hull survival fraction
```

This approximation is deliberately conservative in one important sense: it never increases recoverable mass. Stage 22 may author richer compartment-material mappings if that creates useful gameplay distinctions.

## 1.2 Module salvage

For each actually installed module:

```text
original module commodity mass
= product unit mass × Stage-18D manufacturing input fraction

accessible module commodity mass
= original module commodity mass × mount-local module integrity
```

A module absent from the fitted ship contributes zero salvage. A destroyed module cannot recover its pristine material mass.

## 1.3 Closed wreck accounting

For each salvage commodity stream:

```text
constructed mass
= accessible pre-recovery mass
+ irrecoverable damage/destruction loss
```

And for the whole represented wreck:

```text
total constructed hull + module mass
= total accessible pre-recovery salvage
+ total irrecoverable damage loss
```

The pristine current escort demonstrator therefore represents exactly:

```text
12,000,000 kg bare hull
+ 7,520,000 kg installed modules
= 19,520,000 kg represented constructed mass
```

Carried ammunition, reaction mass, reactor fuel, cargo and other consumables are intentionally not folded into structural salvage. They retain their own physical inventory/consumable fate and must not be duplicated through wreck composition.

# 2. Existing recycling path is reused

Each non-empty `SalvageStream` can be converted into the Stage-18B `PhysicalSourceState` that was deliberately reserved for later salvage integration:

```text
sourceKind = SALVAGE_STREAM
environment = SALVAGE_SITE
grade = 1
sourceRecoveryFraction = 1
required capability = capability.process.recycling
```

Actual recovery still uses the existing method:

```text
extraction.salvage_recovery
```

whose current baseline applies:

- finite throughput;
- process energy;
- engineering work;
- maintenance work;
- compatible storage;
- 0.70 recovery fraction.

Therefore even an undamaged pristine material stream experiences a second bounded process loss:

```text
constructed mass
≥ accessible salvage source mass
≥ recovered commodity mass
```

The already-authored `facility.processing.recycling` supplies this capability. Stage 18H does not add a second recycling throughput system.

# 3. Physical construction catalog

`Stage18FacilityConstructionCatalog` provides a data-driven physical bill for every current Stage-18E facility definition.

To avoid an unnecessary SKU explosion, five reusable construction profiles define:

- input mass fractions;
- required fabrication capabilities;
- engineering work-seconds per installed kilogram.

Every Stage-18E facility then binds to:

```text
construction profile
+ installed physical mass
```

The loader requires:

- every construction profile to be mass-closed to 1.0;
- all inputs to be known mass-based Stage-18 commodities;
- no raw extracted feedstock as a direct construction input;
- all required capabilities to exist in the Stage-18 ontology;
- every one of the current 14 Stage-18E facility definitions to have exactly one physical construction binding.

These are baseline gameplay engineering masses, not claims about exact real-world plant mass.

# 4. Stage-16-style construction order

`Stage18FacilityConstructionRuntime` implements the kg-native equivalent of the existing Stage-16 causality without creating a second abstract strategic construction system.

A persistent-ready order contains:

```text
stable order ID
future installed facility instance ID
Stage-18E facility definition ID
target station/site ID
physical location tag
required commodity mass by ID
delivered commodity mass by ID
required engineering work
completed engineering work
status
```

Lifecycle:

```text
AWAITING_MATERIALS
→ READY_FOR_WORK
→ BUILDING
→ COMPLETE
```

`CANCELLED` is allowed only before the first engineering work-second is applied.

## 4.1 Delivery locality

Construction delivery consumes real commodity mass from the canonical Stage-18F storage belonging to the **same target station**.

Material on another station cannot be delivered directly by calling construction APIs. It must first arrive through ordinary Stage-18F logistics.

This prevents an implicit teleport path:

```text
remote stock → construction site
```

## 4.2 Work capability

Construction work is supplied by ordinary active Stage-18E facilities.

`projectCapability(...)` combines only current `ACTIVE` facility snapshots:

```text
capability tags = union(active facility tags)
engineering work rate = sum(active facility engineering work rates)
```

The construction profile's required capabilities must be present. Time or credits cannot replace a missing heavy/electrical/precision/assembly capability.

A shared interval `WorkBudget` is finite and cannot be reused across multiple orders.

## 4.3 Completion

Only after:

```text
all required physical kilograms delivered
+ required capability tags present
+ required finite engineering work completed
```

does construction return an ordinary:

```text
Stage18StationIndustrialNode.InstalledFacilityReference
```

This reference can be projected by the normal Stage-18E facility runtime. Stage 18I owns final insertion into persistent station/world state and save/load equivalence.

# 5. Cancellation and partially built structures

Before any engineering work begins, cancellation may atomically return physically delivered material to the same station if compatible storage has enough aggregate capacity.

After work begins, pristine return is forbidden:

```text
BUILDING project
≠ cancel → 100% pristine materials
```

A partially assembled/destroyed construction site must later use explicit destruction/salvage fate. This prevents construction-cancel exploits that duplicate or perfectly recover processed material.

# 6. Stage 18H acceptance covered

Tests prove:

1. pristine wreck salvage never exceeds actual constructed hull + module mass;
2. physical damage only reduces accessible salvage;
3. per-stream and whole-wreck mass accounting closes;
4. existing `extraction.salvage_recovery` imposes its ordinary additional 30% recovery loss;
5. all 14 current Stage-18E facilities have data-driven physical construction bills;
6. facility input mass closes exactly to authored installed mass;
7. raw ore/feedstock cannot bypass refining into construction;
8. materials must physically exist at the target station before delivery;
9. engineering work cannot start until all materials arrive;
10. missing fabrication capability cannot be substituted by elapsed time or credits;
11. finite work completes into an ordinary installed Stage-18E facility reference;
12. pre-work cancellation returns material instead of deleting it, while post-work pristine cancellation is forbidden.

# 7. Scope boundary

Stage 18H does not yet own:

- complete ECS/world destruction wiring for Stage-18 physical wreck snapshots;
- persistence of wreck streams and construction orders in the full world save codec;
- automatic insertion/removal of constructed facilities in a persistent station's installed-facility collection;
- save/load equivalence of the entire industrial chain;
- procedural resource/industry geography.

Those integration proofs are the explicit responsibility of **Stage 18I — deterministic industrial acceptance**, followed by Stage 20 world generation.
