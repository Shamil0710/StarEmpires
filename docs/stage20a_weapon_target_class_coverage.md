# Stage 20A — Representative Weapon / Target-Class Coverage Closure

**Status:** IMPLEMENTED — exact-head CI required before merge  
**Requirement:** `WEAPON_REPRESENTATIVE_TARGET_COVERAGE`  
**Profile:** `stage20a.weapon-target-coverage.v1`  
**Date:** 2026-08-19

## Purpose

Close the Stage-20A readiness blocker requiring machine-readable `weaponTimeOfFlightBands` and `weaponEffectivenessBands` by representative target without inventing a hard weapon range or a class-name combat bonus.

The closure composes two already accepted authorities:

1. production-backed `Stage20WeaponSpatialCalibrationProfile` proves executable spatial evidence exists for kinetic direct fire, beams, guided strike and layered defense;
2. `docs/benchmarks/weapon_interaction_reference_v0_3.json` provides the accepted provisional target-geometry and P50 direct-fire class benchmark.

## Authority boundary

The v0.3 benchmark status is `authoring-benchmark-only`. Therefore this closure records it as:

```text
authority = PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

It is sufficient for Stage-20 world-scale calibration but does not promote v0.3 weapon or target values to final production content.

`P50` means the analytical v0.3 benchmark point where the single-shot hit probability is approximately 50% under its stated precision-track and maneuver assumptions. It is **not**:

- a hard `maxRange`;
- a generic damage chance;
- a class-name accuracy modifier;
- armor penetration/lethality;
- proof that every beam/guided weapon uses the same target response.

Material lethality continues to require the physical shield/armor/material/damage path and may not be inferred from P50.

## Accepted target references

The source authors geometry/maneuver references for:

```text
CORVETTE
FRIGATE
DESTROYER
CRUISER
BATTLECRUISER
BATTLESHIP
```

The source authors P50 direct-fire rows for exactly five classes:

| Weapon | Target | P50 range | TOF at P50 |
|---|---|---:|---:|
| M coilgun, 25 kg @ 15 km/s | Corvette | 263.038 km | 17.536 s |
| M coilgun, 25 kg @ 15 km/s | Frigate | 363.099 km | 24.207 s |
| L coilgun, 150 kg @ 20 km/s | Cruiser | 982.859 km | 49.143 s |
| L coilgun, 150 kg @ 20 km/s | Battlecruiser | 1,234.143 km | 61.707 s |
| XL kinetic, 1,000 kg @ 30 km/s | Battleship | 2,819.431 km | 93.981 s |

### Destroyer gap is preserved

v0.3 provides destroyer projected geometry and lateral acceleration but no P50 weapon row. Stage 20A does **not** interpolate one between frigate and cruiser.

Machine-readable closure therefore reports:

```text
unsupportedP50Targets = [DESTROYER]
```

Representative coverage is still sufficient because the source-backed P50 matrix spans small, medium and capital targets and Stage-20A requires representative rather than exhaustive target rows. Any future destroyer P50 row must be derived/authored explicitly and can supersede this profile version.

## Physical consistency checks

Every accepted P50 row is checked for:

```text
timeOfFlight = range / muzzleVelocity
muzzleEnergy = 0.5 * projectileMass * muzzleVelocity^2
singleShotHitProbability = 0.5
```

The target references also retain physical projected area, equivalent radius and benchmark lateral acceleration from v0.3. No missing target property is derived from class labels.

## Production-runtime seam

Closure additionally requires non-empty Stage-20A.5 production-backed evidence for all four runtime families:

```text
KINETIC_DIRECT_FIRE
BEAM_DIRECT_FIRE
GUIDED_STRIKE
LAYERED_DEFENSE
```

This requirement prevents an old benchmark table from claiming world-scale readiness after a production weapon family disappears or becomes non-executable.

The class-level P50 rows themselves remain kinetic direct-fire calibration evidence. Beam/guided material effectiveness is not fabricated from those rows.

## Readiness effect

After acceptance the Stage-20A gate is expected to change:

```text
WEAPON_REPRESENTATIVE_TARGET_COVERAGE:
  BLOCKING_STAGE20B_ENTRY -> SATISFIED

blocking requirement count:
  10 -> 9
```

Stage 20A remains `BLOCKED_FOR_STAGE20B` because independent physical/calibration blockers remain. The next blocker in dependency order is `PD_SAFE_INTERCEPT_GEOMETRY`.
