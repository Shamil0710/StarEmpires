# Star Empires — Stage 16 Construction Timing Foundation

> Status: **ACTIVE FOUNDATION — Stage 16 is not complete**
>
> Functional foundation merged in **PR #51** at `a32584a928d97a014dd2cbb32fdeaed4fe0c65eb`.
>
> Validation: **CI #1151**, run `31826504541`, **454/454 tests passed** plus strict Javadoc, JaCoCo and desktop packaging.

---

## 1. Design goal

Station construction time must emerge from the physical scale of the project instead of being a single arbitrary final timer stored on a station archetype.

The immediate Stage-16 rule is:

```text
materialWork =
    Σ(requiredAmount_i × constructionHandlingWeight_i)

buildTime =
    baseSetupSeconds
  + materialWork / baselineAssemblyRate
```

The existing authored `construction.buildSeconds` value is therefore reinterpreted as **base setup / archetype-complexity allowance**, not the complete build duration.

The material bill is authoritative: if a station needs more real components, the project normally requires more assembly work and therefore more time.

---

## 2. Why the current weight is not kilograms

The present content catalog does not yet expose authoritative physical mass for every item/component. Calling the current cargo units kilograms would create false precision and would later make the fitting/mass model harder to correct.

For this Stage-16 foundation, each item category therefore receives a normalized **construction handling / fabrication work** value:

| Category | Work per required unit |
| --- | ---: |
| `MATERIAL` | `1.00` |
| `GAS_LIQUID` | `0.55` |
| `FINISHED_GOODS` | `1.60` |

Interpretation:

- raw structural material is the reference work unit;
- bulk tanked fluid/energy cargo requires less assembly handling per inventory unit;
- finished assemblies/components require more placement, integration and testing work.

Current baseline site throughput:

```text
12 construction-work units / simulation second
```

These are balance parameters, not physical SI units.

---

## 3. Current authoritative formula

Implemented in `ConstructionDurationPolicy`:

```text
W = Σ(q_i × h_i)

T_material = W / R

T_total = T_setup + T_material
```

Where:

- `q_i` — exact required amount from the station construction material bill;
- `h_i` — normalized handling/fabrication work for the item's category;
- `R` — baseline assembly rate;
- `T_setup` — existing authored `buildSeconds`, now treated as setup/complexity allowance;
- `T_total` — build duration used when creating the real construction project.

The final time is converted into authoritative fixed ticks and stored in `ConstructionProjectState.buildDurationTicks`.

---

## 4. Example: mining base

Current `station.mining_base` requirements:

```text
120 steel × 1.00 = 120 work
 60 energy × 0.55 =  33 work
--------------------------------
material work       = 153 work
```

At 12 work/s:

```text
material assembly = 153 / 12 = 12.75 s
base setup        = 25.00 s
--------------------------------
calculated total  = 37.75 s
```

This is intentionally longer than the old standalone 25-second timer because physical material scale now contributes to construction time.

---

## 5. Persistence rule

The formula is evaluated **when a new project is created**.

After creation:

```text
calculated total seconds
→ authoritative fixed ticks
→ ConstructionProjectState.buildDurationTicks
→ persisted save contract
```

An ongoing project does not recalculate its duration on load.

This is important because future balance changes to assembly rate, category weights, tech tiers or complexity must not silently change an already-started construction project after save/load.

`ConstructionDurationIntegrationTest` proves that a new real `WorldSimulation` project receives the calculated tick duration and preserves the exact value through `WorldStateCodec` save/restore.

---

## 6. Planned tech-tier / complexity extension

The architecture leaves an explicit seam for the user's proposed technology/structure coefficient, but PR #51 deliberately does not invent arbitrary tiers before the content model defines them.

The intended future form is:

```text
baseWorkTime =
    baseSetupSeconds
  + materialWork / assemblyRate

finalBuildTime =
    baseWorkTime
  × techTierFactor
  × complexityFactor
  × siteCapabilityFactor
```

Possible meanings:

### `techTierFactor`

Represents technological difficulty, precision and integration requirements rather than simple physical size.

Examples of the future qualitative relation:

```text
simple storage / mining platform
< industrial refinery
< advanced shipyard
< high-tech research / military installation
```

Exact tiers and multipliers must be data-driven and introduced only when station technology classes become authoritative content.

### `complexityFactor`

Represents structural/system integration difficulty inside the same broad technology tier. It can distinguish, for example, a large but mechanically simple depot from a smaller but highly integrated electronics/weapon facility.

### `siteCapabilityFactor`

A later construction fleet, specialized builder, orbital yard, damaged site or upgraded construction infrastructure may change effective assembly throughput. This factor must come from real world assets/capabilities rather than a free UI bonus.

---

## 7. Future real-mass integration

When items/components receive authoritative unit mass, the formula should evolve without changing project persistence or the construction-site pipeline.

A likely shape is:

```text
materialWork_i =
    quantity_i
  × f(unitMass_i, fabricationClass_i, installationComplexity_i)
```

This keeps a useful distinction between:

- physically massive bulk structure;
- light but technically difficult electronics;
- preassembled modules;
- fluids/consumables that may be delivered but not structurally assembled.

Mass alone should therefore not necessarily become the only determinant of construction time.

---

## 8. Stage-16 boundary

This timing foundation does **not** mark Stage 16 complete.

Stage 16 still needs the player-facing construction/station-ownership vertical slice using the existing Stage-9 physical project pipeline:

```text
player chooses legal site / station archetype
→ real funding
→ physical construction site
→ real material demand
→ physical deliveries
→ formula-derived build duration
→ construction progress
→ real completed station
→ player ownership
→ ordinary station economy / logistics
→ save/load continuation
```

No instant placement, virtual materials or UI-only completion is allowed.

The duration policy is now the authoritative time boundary that this vertical slice should consume.
