# Stage 19I-J — ammunition, power and thermal tactical consequences

Status: implementation/acceptance slice after scaled live/headless parity.

## Purpose

The Stage-19 exit contract requires damage, ammunition, heat, power and reaction mass to materially affect tactical AI decisions. Damage and reaction-mass behavior already have explicit 8v8 acceptance. This gate closes the remaining finite-ammunition and engineering power/thermal decision evidence without inventing balance reserves or bypassing production physics.

## Hard-zero ammunition readiness

`TacticalSurvivalPlanner.OwnReadiness` now carries two local physical facts:

- whether every currently operational weapon is finite-ammunition dependent;
- total physical ammunition item count across the ship's installed ammunition interfaces.

The dependency flag is not authored by doctrine ID. `LiveTacticalBattleControlRuntime` derives it each tick from actual installed, surviving `WEAPON_AMMUNITION` modules:

- a module with an `AMMUNITION` interface is finite-ammunition dependent;
- an operational weapon module without an `AMMUNITION` interface is a non-ammunition weapon;
- a ship is considered wholly finite-ammunition dependent only when it has at least one operational ammunition weapon and no operational non-ammunition weapon.

This prevents the C directed-energy fit from being incorrectly treated as completely disarmed merely because its finite PD feed is empty.

No percentage reserve has been added. The new `AMMUNITION_DEPLETED` survival reason is a hard physical zero only. When such a ship can retreat, normal survival routing applies; when its authored safe point is its current position it disengages rather than manufacturing movement. Fire authorization is revoked by the existing survival override path.

## Initial physical resource authoring

`LiveTacticalInitialReadinessService` remains a scenario/acceptance initial-state seam and now supports:

- clearing all physical ammunition interfaces;
- setting current shared stored bus energy;
- setting current local stored heat on an installed mount.

These methods modify only existing authoritative runtime inputs. They do not cache derived power, heat, sensor, weapon or AI output.

## Power consequence

The power acceptance keeps the E-fit reactor at integrity `0.16`, above the existing `0.15` subsystem retreat threshold, and clears shared bus energy.

Therefore the survival planner must still report `READY`. The actual difference is produced downstream by existing production owners:

```text
reduced physical reactor output + zero stored bus energy
→ engineering grant cannot fund active radar
→ no radar measurement
→ no actor-local TrackState
→ tactical intent has no selected target
→ no fire authorization
```

A fresh same-fit comparator under the same geometry must acquire a target and remain fire-authorized.

## Thermal consequence

The thermal acceptance starts an otherwise pristine E-fit with its fitted `utility_sensor` local heat exactly at that module's authored `localThermalCapacityJ`.

On the first sensing tick the existing engineering grant cannot add the radar operation's local heat. Consequently no measurement or track is created and tactical policy has no target/fire authorization. A cold same-fit comparator acquires and selects a hostile track on the same tick.

The test derives thermal capacity from the installed module definition; it does not hardcode a substitute temperature or arbitrary thermal threshold.

## Acceptance matrix

`LiveTacticalResourceConstraintAcceptanceTest` requires:

1. hard-zero ammunition on a finite-ammunition-only E-fit produces `AMMUNITION_DEPLETED`, disengagement at its current safe point and no fire authorization;
2. an otherwise identical fresh E-fit remains `READY`, selects a real actor-visible target and is fire-authorized;
3. clearing finite PD ammunition on an intact C beam fit does **not** produce `AMMUNITION_DEPLETED`;
4. real power denial removes tracks and changes target/fire decisions while survival remains `READY`;
5. a thermally saturated fitted sensor removes same-tick radar/track/target/fire while a cold comparator succeeds;
6. two identically authored ammunition-depleted 8v8 battles retain exact deterministic control fingerprints.

## Authority boundary

No doctrine/class combat bonus, virtual ammunition, free sensor operation or direct enemy-state read is introduced. Power and heat are not copied into tactical morale variables: their AI consequence remains the production engineering-operation denial and resulting actor-local information loss.

## Remaining Stage 19I work

After this gate is green and merged, perform a line-by-line exit audit against `stage19_scaled_live_tactical_ai_acceptance.md`. Stage 19 / Stage 19I must remain open if any mandatory scenario, tooling or behavior row lacks direct acceptance evidence.
