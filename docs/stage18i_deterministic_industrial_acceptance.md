# Stage 18I — Deterministic industrial acceptance and persistence

Status: final Stage-18 implementation/acceptance slice on `agent/stage18i-industrial-acceptance-persistence`.

## Purpose

Stage 18I is the integration contract proving that Stage 18A-H form one closed physical economy and that the kg-native industrial state survives deterministic save/load checkpoints. It is not another production system.

The acceptance universe is hand-authored. Stage 20 procedural generation is not allowed to hide missing resources, capabilities or recipes by inventing convenient supply.

## Industrial persistence extension

The existing core `GameState` / `GameStateCodec` continue to persist the ordinary world and Stage-17.5 ship engineering state. Stage 18 does not reinterpret legacy integer `InventoryComponent` quantities as kilograms.

`Stage18IndustrialState` persists only the new Stage-18 physical extension:

```text
simulation tick
finite natural/salvage sources
canonical station kg storage
installed Stage-18E facility states + owning station
installed Stage-18G yard states + owning station
Stage-18H construction orders
queued/in-progress Stage-18B-D process orders + physically reserved inputs
```

`Stage18IndustrialStateCodec` is deterministic, bounded, fail-closed on invalid magic/schema/truncation/trailing bytes and content-bound by an industrial semantic fingerprint. Its filesystem writer replaces the target atomically when supported.

## Industrial content fingerprint

`Stage18IndustrialContentFingerprint` binds an industrial checkpoint to:

- Stage-17.5 ship engineering geometry/mass;
- Stage-18A resource ontology;
- Stage-18B extraction methods;
- Stage-18C refining recipes;
- Stage-18D manufacturing recipes;
- Stage-18D physical module/ammunition identities, unit masses and storage classes;
- Stage-18E facility definitions;
- Stage-18F station infrastructure;
- Stage-18G shipyard definitions;
- Stage-18H facility-construction bills;
- Stage-18I ship-consumable interface bindings.

Changing physical industrial semantics therefore cannot silently resume an old checkpoint under different rules.

## Multi-line facility capability network

Final integration exposed a legitimate recipe-composition requirement: some recipes need capabilities installed on different physical lines, such as beneficiation plus advanced-materials processing.

`Stage18FacilityCapabilityNetwork` combines only current `ACTIVE` Stage-18E snapshots:

```text
capability tags = union(active physical facility tags)
process power = sum(already-limited active process power)
engineering work rate = sum(already-limited active work rate)
maintenance work rate = sum(already-limited active maintenance rate)
```

Each line has already been constrained by condition, allocated power, heat rejection, staffing and maintenance availability. The network is therefore a physical industrial campus, not a station-class multiplier.

## Physical ship consumable servicing

Stage-17.5 already models reaction mass as a mass-bearing ship interface, but Stage 18 required an economy bridge so docking cannot create it for free.

The current binding is:

```text
module.main_drive_escort_v1
propellant_feed / REACTION_MASS
<- commodity.material.purified_water
amountPerKg = 1
```

`Stage18ShipConsumableService` requires the authored binding, the correct installed module and interface, remaining interface capacity and sufficient canonical Stage-18F station stock before mutating either inventory or `ConsumableState`.

The current reactor runtime has no authored operational fuel interface/burn rate. Stage 18I therefore proves the reactor-fuel production/storage chain without inventing a burn law. A future reactor-consumption model can use the same explicit servicing pattern.

## Hand-authored minimal industrial universe

The acceptance harness uses four physical storage nodes:

```text
station.acceptance.mine
station.acceptance.volatile
station.acceptance.industry
station.acceptance.depot
```

Finite occurrences are authored for all baseline feedstock families: water ice, volatiles, carbonaceous feedstock, metallic ore, light-metal minerals, conductor ore, strategic-metal ore, silicates and fissiles.

Feedstock is depleted by Stage-18B extraction and reaches the industrial station through Stage-18F logistics. Explicit Stage-18E process/fabrication lines provide refining/manufacturing capability. No supply is created from credits, station level, player ownership or AI-only virtual stock.

## End-to-end acceptance chain

`Stage18IndustrialAcceptanceHarness` executes:

```text
finite occurrence
-> compatible Stage-18B extraction
-> Stage-18F physical logistics
-> Stage-18C refining
-> engineering materials / industrial consumables
-> Stage-18D heavy/electrical/precision components
-> Stage-18D module + ammunition manufacturing
-> physical ammunition transfer to depot
-> Stage-18G hull/material/module settlement
-> persistent ship construction
-> physical water reaction-mass loading
-> physical damage
-> Stage-18G repair inputs + work
-> destructive damage
-> Stage-18H bounded wreck composition
-> existing Stage-18B salvage recovery through recycling facility
-> Stage-18H physical facility construction
-> industrial persistence checkpoint
-> save/load
-> deterministic completion replay
```

## Mapping to the mandatory Stage-18 acceptance contract

1. **Water chain** — water ice is extracted, purified water is refined, and 20,000 kg is physically removed from station stock into the escort main-drive reaction-mass interface.
2. **Bulk metal chain** — metallic ore becomes structural alloy and participates in heavy components, hull construction, repair and facility construction.
3. **Electrical bottleneck** — electrical components have a distinct recipe/input/facility path.
4. **Precision bottleneck** — precision components require high-tech material and precision fabrication capability.
5. **Strategic material bottleneck** — advanced production measurably depletes the finite strategic-metal source.
6. **Shipyard chain** — a real `hull.escort_destroyer_v1` fit is built from the Stage-18G mass-closed hull bill plus pre-manufactured module products, producing persistent `EntityId(18001)`.
7. **Ammunition/consumables** — rail ammunition is physically manufactured and transferred to a depot; reaction mass consumes station water; reactor fuel is physically produced from fissile feedstock.
8. **Repair chain** — a damaged built ship consumes damage-scaled Stage-18 materials/components and finite yard work while preserving identity.
9. **Salvage/recycling chain** — destructive damage creates a wreck bounded by actual constructed state; the existing salvage-recovery method then imposes an additional ordinary recovery loss.
10. **Save/load equivalence** — a half-built facility plus an in-progress refining order with reserved feedstock is checkpointed; original and restored continuations must end in byte-identical industrial payloads.

The required conservation relation is:

```text
constructed wreck mass
> accessible salvage mass
> recovered commodity mass
```

## Definition of Done

Stage 18 is complete only when the Stage-18I branch passes the repository-wide Java-17 `./mvnw --batch-mode --no-transfer-progress clean verify` gate, including:

- the full unit/integration/acceptance suite;
- strict Javadoc with warnings treated as failure;
- JaCoCo thresholds;
- normal and shaded desktop JAR packaging.

After that gate is green, Stage 20 may consume a closed economic language rather than inventing missing economic rules during world generation.

## Downstream ownership

Stage-18 completion does not imply final content/balance completion. Downstream owners remain:

- Stage 19 — strategic/tactical warfare against real economic assets and logistics;
- Stage 20 — physical world/resource/industry geography generation;
- Stage 21 — expanded ship/technology content using the finalized ship research;
- Stage 22 — broader content families, faction doctrines, balance and long-run soak.

The invariant entering those stages is:

```text
physical occurrence
-> extraction
-> logistics
-> refining
-> materials / consumables
-> components
-> real products / infrastructure
-> ship / station operation
-> damage / wear
-> physical repair / service
-> bounded destruction / salvage / recycling
```
