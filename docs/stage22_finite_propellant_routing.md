# Finite propulsion and propellant-safe routing

**Status:** finite-propulsion foundation merged in PR #389; refuel-aware continuation is a closure candidate in PR #390.

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

- `faction.alpha` / Empire -> `fit.empire.freight.strategic_v1`;
- `faction.beta` / Industrial Union -> `fit.industrial_union.freight.strategic_v1`.

Each strategic freight variant adds the already accepted common Stage-22 FTL module to the authored
unused `utility_defense` slot, so inter-system freight no longer depends on legacy free jump timing.
The live `EngineeringComponent` owns finite drive reaction mass. Freight cargo transfers update the
same engineering consumable state so cargo mass continues to affect derived ship mass.

A new campaign starts these already-owned ships with explicit finite authored starting reaction mass.
That is campaign initial state, not a refuel operation. Runtime refueling must still pass through the
existing Stage-18 servicing/logistics authority. Core Empire and Industrial Union main-drive
`propellant_feed` interfaces are bound to finite
`commodity.material.purified_water` station stock through a Stage-22 servicing overlay. The original
Stage-18 default binding resource is unchanged because it participates in the persisted industrial
content fingerprint; adding the later production drives therefore does not invalidate historical
Stage-18 saves. Historical local freighters restored from saves that predate finite propulsion receive
the same one-time migration projection; it is not repeated after the engineering state exists.

## Exact local FTL approach

The Stage-20 approach between the current local position and the outgoing FTL anchor is no longer a
free linear translation for fitted ships.

For the accepted calibrated approach duration, the required maneuver budget is:

```text
dv_accel = |v_cruise - v_current|
dv_brake = |v_cruise|
dv_leg   = dv_accel + dv_brake
```

Before the approach starts, the complete acceleration-plus-braking delta-v is previewed against the
real engineering runtime. If current reaction mass, power, thermal condition or drive capability cannot
complete both impulses, the jump request fails while the ship is still in its current system and spends
no propellant. Once accepted, only the acceleration impulse is committed at departure; the matching
braking impulse is committed at the physical approach boundary before the ship is allowed to settle at
the outgoing FTL endpoint. The existing deterministic Stage-20 approach kinematics remain the spatial
authority.

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

The direct complete-route preflight itself never refuels, teleports or invents an alternate edge.
The later refuel-aware logistics layer may satisfy that shortfall only from canonical finite station
stock and then must re-run the same physical route safety checks before movement begins.

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
- refuel-aware planning is pure and cannot reserve or create station stock;
- empty local tanks can become route-feasible only through real accessible finite station stock;
- intermediate finite refueling is projected and committed only after physical arrival;
- the last wet system can proactively load enough mass to cross a later dry stretch while preserving
  the same complete-route 10% reserve invariant;
- spent bootstrap station stock and loaded ship reaction mass survive save/load without replenishment;
- player and Stage-21 strategic routing accept refuel-recoverable routes and reject physically unsafe ones;
- legacy non-fitted compatibility worlds still load and move through their explicit fallback;
- the full Java 17 `clean verify` gate is green.


## Refuel-aware route continuation

Generated-world fitted fleets may now plan beyond their current protected reaction-mass range only
through explicit physical refueling opportunities. The route planner projects each local maneuver
segment against the existing finite-propellant geometry and may insert a refuel stop only when all of
the following are true:

- the fitted drive exposes an authored `REACTION_MASS` interface with a Stage-22 commodity binding;
- a canonical station endpoint exists in that system;
- the endpoint has compatible `storage.liquid_tank` capacity and handling;
- current diplomacy/market access permits that fleet to use the endpoint;
- the station contains enough finite `commodity.material.purified_water` for the planner's
  backward-calculated minimum departure fuel requirement, which may intentionally carry extra fuel
  across later dry or understocked systems. The same 10% operational reserve is protected across each
  complete contiguous no-service stretch; the reserve horizon resets only at a system with physically
  accessible finite propellant stock.

For a fixed route, the planner first derives every local segment delta-v, then works backward from
the destination. At each node it calculates the minimum fuel that must arrive before any local service
and the minimum departure fuel required after service. This avoids the naive failure mode where a ship
leaves the last well-supplied station with only enough fuel for the next hop and later discovers that a
downstream system is dry.

Planning never mutates or reserves station stock. Execution is deliberately receding-horizon:
immediately before each new hop, only refueling in the fleet's current system is physically committed
through `Stage18ShipConsumableService`, station inventory is decremented, ship engineering state is
updated, and the complete remaining route is recalculated. A projected downstream station may be
emptied or become inaccessible before arrival; in that case the fleet stops at its current safe system
instead of beginning a hop that could strand it.

New generated campaigns receive a finite initial purified-water reserve only at station archetypes
that physically support liquid storage and transfer. The reserve is capped at 24,000,000 kg and at
50% of the otherwise free liquid-tank capacity. This is bootstrap inventory, not regeneration.
Captured station stock is persisted normally, and restoring an existing campaign never replenishes
spent propellant.

The current Stage-21 generated military endurance/combat drives also receive explicit Stage-22 water
servicing bindings. Stage-18's default servicing catalog remains unchanged so historical industrial
content fingerprints and old saves are not silently rewritten.
