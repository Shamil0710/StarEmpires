# Stage 20A Closure — Representative Propulsion v2

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A representative-ship scale calibration / closure remediation  
**Date:** 2026-08-19

## 1. Purpose

Close the missing propulsion-role coverage needed before Stage 20B without pretending that calibration references are production ship content.

The Stage-20 plan requires nine representative roles:

1. early civilian freighter;
2. loaded bulk freighter;
3. mining ship;
4. patrol/corvette;
5. escort destroyer;
6. cruiser;
7. capital combatant;
8. fleet tanker/logistics support;
9. carrier/aviation group.

Before this slice, the current machine-readable profile covered only five roles. The missing set was:

```text
EARLY_CIVILIAN_FREIGHTER
MINING_SHIP
CRUISER
CARRIER_AVIATION
```

This slice expands the calibration-only reference catalog to all nine roles while preserving `PROVISIONAL_ACCEPTED_REFERENCE` authority and mandatory Stage-22 review.

## 2. Authority boundary

The v2 catalog is **not** a production ship catalog.

It does not create:

- a canonical civilian hull;
- a canonical mining hull;
- a production cruiser fit;
- a production carrier fit;
- faction technology;
- final construction cost;
- final stores/endurance;
- final sustained-thrust policy.

It provides only enough mass / reaction-mass / thrust / exhaust-velocity evidence for Stage-20 spatial and FTL calibration.

Every v2 reference remains:

```text
PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

Production engineering still supersedes the matching provisional reference whenever an accepted fitted ship exists. The current escort destroyer therefore continues to come from `fit.escort_destroyer_schema_v1`, not from the calibration JSON.

## 3. Provenance model

The v1 catalog had one root provenance because all five numeric references came directly from the v1.0 representative baseline.

That is no longer sufficient. v2 therefore requires explicit **per-reference source evidence**.

Three provenance classes exist:

### 3.1 Existing v1.0 references

The corvette, escort reference, battleship, loaded bulk freighter and loaded fleet tanker retain their exact accepted v1.0 values.

Their per-reference provenance points directly to:

```text
docs/benchmarks/ship_mathematics_v1_0_design_baseline.json
→ representativeReferenceDesigns/<ROLE>
```

### 3.2 Cruiser / carrier promotion for Stage-20 calibration

`docs/benchmarks/ship_reference_designs_v0_2.json` is historically `authoring-benchmark-only`, so its values are **not silently treated as production content**.

For Stage-20 calibration only, this closure explicitly accepts two already-authored physically closed seeds:

```text
benchmark.ship.general_cruiser_v0_2
benchmark.ship.fleet_carrier_v0_2
```

They are promoted only to:

```text
PROVISIONAL_ACCEPTED_REFERENCE
```

and retain source provenance back to the v0.2 benchmark plus this acceptance document.

### 3.3 New bounded authoring seeds

No accepted or historical physical propulsion design was found for:

```text
EARLY_CIVILIAN_FREIGHTER
MINING_SHIP
```

Legacy `ShipType.MINING_SHIP` defines only the gameplay/economic role and cargo compatibility; it does not own mass, thrust or reaction-mass engineering. Therefore no legacy gameplay speed/cargo value is promoted into Stage-20 physics.

The two missing profiles are explicitly authored here as calibration-only seeds inside existing accepted capability bounds.

## 4. Bounded authoring rules

The new civilian/mining seeds obey all of the following constraints:

1. use the existing civilian bulk-drive exhaust-velocity family:

```text
80,000 m/s
```

rather than inventing a new propulsion technology;

2. remain below the accepted reference ordinary-FTL translated-mass ceiling:

```text
100,000,000 kg
```

so Stage-20 can calibrate at least one ordinary civilian logistics path without a hidden mass bypass;

3. stay slower in departure acceleration than military patrol/corvette capability;

4. remain materially more mobile than the loaded 143,000,000 kg bulk freighter where an early light freighter role requires it;

5. keep mining mobility near the existing heavy-logistics envelope rather than turning an industrial vessel into a warship;

6. use the same physical equations as every other representative:

```text
acceleration = thrust / departure mass
delta-v = exhaust velocity × ln(initial mass / dry-after-reaction mass)
mass flow = thrust / exhaust velocity
```

7. remain Stage-22-review-required and may be replaced by future production civilian/mining fits without changing Stage-20 architecture.

## 5. Early civilian freighter seed

Calibration role:

```text
EARLY_CIVILIAN_FREIGHTER
```

Authoring seed:

```text
design dry mass       = 8,000,000 kg
cargo / mission mass = 12,000,000 kg
reaction mass         = 8,000,000 kg
departure mass        = 28,000,000 kg
thrust                = 5,600,000 N
exhaust velocity      = 80,000 m/s
```

Derived closure:

```text
initial acceleration = 0.2000000000 m/s²
delta-v              ≈ 26,917.779 m/s
```

This places it:

- well below the reference 100,000,000 kg jump-drive translated-mass ceiling;
- above loaded bulk-freighter mobility;
- below patrol/corvette acceleration;
- inside the existing civilian 80 km/s exhaust-velocity family.

Representative route consequences from the existing variable-mass Stage-20 route solver are approximately:

```text
100,000 km  → 12.08 h rest-to-rest
1,000,000 km → 36.55 h rest-to-rest
```

These are calibration consequences, not target world distances or travel-time promises.

## 6. Mining ship seed

Calibration role:

```text
MINING_SHIP
```

Authoring seed:

```text
design dry mass       = 24,000,000 kg
ore / mission mass    = 18,000,000 kg
reaction mass         = 14,000,000 kg
departure mass        = 56,000,000 kg
thrust                = 7,000,000 N
exhaust velocity      = 80,000 m/s
```

Derived closure:

```text
initial acceleration = 0.1250000000 m/s²
delta-v              ≈ 23,014.566 m/s
```

The dry mass intentionally carries the heavier industrial plant assumption while mission/cargo mass represents carried ore, consumables and mission load at the calibration point.

Representative route consequences are approximately:

```text
100,000 km   → 15.37 h rest-to-rest
1,000,000 km → 46.40 h rest-to-rest
```

Again these are probe consequences, not generated-world constants.

## 7. Cruiser reference

The v0.2 general-purpose independent cruiser is accepted unchanged for calibration:

```text
departure mass = 70,279,000 kg
reaction mass  = 25,000,000 kg
thrust         = 28,000,000 N
exhaust        = 100,000 m/s
acceleration   ≈ 0.3984120434 m/s²
delta-v        ≈ 43,962.969 m/s
```

Its translated mass is inside the current 100,000,000 kg reference-drive envelope. This does **not** mean every future production cruiser must use that drive or mass; it means this Stage-20 calibration reference can exercise medium-heavy military inter-system cadence without extrapolating the reference drive.

## 8. Carrier / aviation reference

The v0.2 fleet-carrier seed is accepted unchanged as the `CARRIER_AVIATION_GROUP` propulsion reference:

```text
departure mass = 508,143,000 kg
reaction mass  = 200,000,000 kg
thrust         = 90,000,000 N
exhaust        = 100,000 m/s
acceleration   ≈ 0.1771154970 m/s²
delta-v        ≈ 50,019.894 m/s
```

The v0.2 mission mass already represents the carrier aviation load case, including the authored air-wing/reserve package in that design benchmark.

The reference remains far above the current 100,000,000 kg translated-mass limit and must therefore stay explicitly `EXCEEDS_TRANSLATED_MASS_LIMIT`; no hidden multiple-drive or carrier exception is introduced.

## 9. FTL consequence

With v2, current ordinary-FTL compatibility should include at least:

```text
TORPEDO_CORVETTE
ESCORT_DESTROYER
EARLY_CIVILIAN_FREIGHTER
MINING_SHIP
CRUISER
```

while these remain explicitly overmass:

```text
BATTLESHIP
BULK_FREIGHTER_LOADED
FLEET_TANKER_LOADED
CARRIER_AVIATION_GROUP
```

This closes the current **civilian ordinary-FTL coverage** gap without pretending that the heavy bulk logistics fleet can use the same reference drive.

## 10. Readiness impact

If implementation and exact-head CI accept this profile, exactly two Stage-20A readiness requirements should change:

```text
REPRESENTATIVE_PROPULSION_COVERAGE
BLOCKING → SATISFIED

CIVILIAN_ORDINARY_FTL_COVERAGE
BLOCKING → SATISFIED
```

Expected blocking requirement count:

```text
15 → 13
```

`REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE` remains blocking because v2 intentionally does not invent mission stores duration or final sustained-vs-max thrust policy.

## 11. Regression requirements

Tests must prove:

- all nine required representative roles are present;
- exactly one escort remains production-authoritative and all other current references remain provisional;
- every provisional reference has non-blank per-reference provenance;
- mass recomposition, acceleration and rocket-equation delta-v close for all references;
- early freighter and mining ship remain under the FTL translated-mass ceiling;
- carrier remains overmass;
- changing a physical seed changes derived route/FTL outputs rather than being hidden behind fixed world constants;
- the readiness gate removes exactly the two intended blockers and no others.

## 12. Deferred work

This slice does not close:

- stores/endurance;
- sustained-vs-max thrust consequence matrix;
- production civilian/mining/cruiser/carrier fits;
- Stage-22 content balance/review;
- route semantic bands;
- topology bands;
- station geometry;
- representative sensor/weapon target matrices.
