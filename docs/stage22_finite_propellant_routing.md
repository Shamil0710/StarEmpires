# Finite propulsion and propellant-safe routing

**Status:** implementation candidate in PR #389.

## Purpose

Ordinary map flight, generated-world FTL departure approaches and strategic/freight routing must obey
the same finite Stage-17.5 engineering authority already used by tactical combat.

This closes the historical compatibility seam where a ship could move through
`FlightDynamics` or the Stage-20 local approach projection without spending reaction mass.

## Authoritative physical model

The authoritative propulsion chain is:

```text
EngineeringComponent
+ InstalledFit
+ RuntimeState
+ DamageState
+ OperatingCommand
-> ShipEngineeringRuntime.advance(...)
-> actual thrust + mass flow + next RuntimeState
-> FlightDynamics.advancePhysical(...)
```

No second fuel counter is introduced.

- `REACTION_MASS` is consumed only when thrust is actually requested.
- inertial coasting consumes no reaction mass;
- braking and vector changes require thrust and therefore reaction mass;
- power, thermal state and drive damage remain constraints of `ShipEngineeringRuntime`;
- fitted FTL continues to consume its existing electrical-energy/thermal budget separately.

`ProductionEngineeringRuntimeResolver` is the common production catalog-routing seam used by
ordinary fitted propulsion and fitted FTL.

Historical entities without `EngineeringComponent` retain the old movement path only as an explicit
save/content compatibility seam. New generated military and freight assets are fitted.

## Generated freight

Stage-20 freight persistence retains its historical hull/fit compatibility IDs so old checkpoints
remain readable. At live materialization the two accepted generated core identities are projected onto
their reviewed Stage-22 freight engineering assets:

- `faction.alpha` / Empire -> `fit.empire.freight.bulk_v1`;
- `faction.beta` / Industrial Union -> `fit.industrial_union.freight.bulk_v1`.

The live `EngineeringComponent` owns finite drive reaction mass. Freight cargo transfers update the
same engineering consumable state so cargo mass continues to affect derived ship mass.

A new campaign starts these already-owned ships with explicit finite authored starting reaction mass.
That is campaign initial state, not a refuel operation. Runtime refueling must still pass through the
existing Stage-18 servicing/logistics authority. Historical local freighters restored from saves that
predate finite propulsion receive the same one-time migration projection; it is not repeated after the
engineering state exists.

## Exact local FTL approach

The Stage-20 approach between the current local position and the outgoing FTL anchor is no longer a
free linear translation for fitted ships.

For the accepted calibrated approach duration, the required maneuver budget is:

```text
dv_accel = |v_cruise - v_current|
dv_brake = |v_cruise|
dv_leg   = dv_accel + dv_brake
```

Before the approach starts, that delta-v is previewed against the real engineering runtime. If current
reaction mass, power, thermal condition or drive capability cannot produce it, the jump request fails
while the ship is still in its current system. A successful preview is committed once and the existing
deterministic Stage-20 approach kinematics remain the spatial authority.

## Complete-route preflight

Before a fitted generated-world fleet begins a strategic or freight route, every remaining local
departure maneuver is evaluated.

The route preflight:

1. starts from the fleet's exact current local kinematics;
2. follows only the already accepted neighbor route;
3. for each intermediate system uses the persisted incoming and outgoing FTL anchors;
4. sums the required local maneuver delta-v;
5. derives the available reaction-mass delta-v from current total mass, effective exhaust velocity
   and finite reaction mass using the rocket equation;
6. protects a **10% current reaction-mass reserve**;
7. rejects dispatch before the first new hop when the complete remaining route cannot fit inside that
   budget.

The 10% value is an operational route-planning reserve, not extra fuel and not a propulsion multiplier.

A route failure does not auto-refuel, teleport or invent an alternate edge. The fleet remains safely in
the current system so the existing service/order layer can refuel it or issue another route.

## Acceptance requirements

The implementation is accepted only when automated evidence proves:

- fitted ordinary propulsion consumes reaction mass;
- zero-throttle inertial coast does not consume reaction mass;
- delta-v preview does not mutate authoritative state;
- an empty tank cannot produce a maneuver plan;
- generated freight materializes with finite fitted propulsion;
- generated freight cargo and engineering cargo mass stay synchronized;
- a complete generated route exposes physical fuel planning;
- strategic and freight dispatch reject a route whose remaining physical fuel budget is insufficient;
- legacy non-fitted compatibility worlds still load and move through their explicit fallback;
- the full Java 17 `clean verify` gate is green.
