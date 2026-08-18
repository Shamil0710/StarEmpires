# Stage 19I-L — Tactical formation behavior

## Purpose

This slice closes the Stage 19I requirement for observable tactical formation keeping, break/recovery behavior, and explicit compact/dispersed scenario variants without introducing a second movement or fleet-combat engine.

The repository did not contain a reusable exact-local tactical `FormationPhysics` owner. Existing Stage 15 formation orders are strategic FOLLOW/ESCORT behavior and therefore are not reused as tactical physics.

## Authority boundary

`TacticalFormationPlanner` is a deterministic policy/geometry layer only. It does not:

- mutate `TransformComponent`;
- grant thrust;
- consume reaction mass;
- change mass, acceleration, sensor or weapon statistics;
- read hidden hostile transforms;
- resolve collisions or damage.

It consumes only:

- the actor's own current cross-axis position and velocity;
- its current physically derived acceleration capability;
- an authored own-side formation objective and stable slot index;
- whether higher-priority survival policy currently permits formation control.

The resulting normalized cross-axis correction is executed through the existing production chain:

```text
TacticalFormationPlanner
  -> LiveTacticalBattleControlRuntime
  -> ShipEngineeringRuntime
  -> physical reaction-mass/thrust state
  -> FlightDynamics.advancePhysical
  -> TransformComponent
```

Formation therefore cannot create virtual acceleration or bypass engineering constraints.

## Current formation frame

Stage 19I acceptance battles close primarily along world X. The formation policy intentionally controls only the world-Y cross axis while existing tactical range/intercept policy retains X authority.

This is sufficient to prove formation behavior in the current exact-local acceptance geometry without prematurely introducing a generic rotated fleet-frame/navigation layer. A later spatial/navigation stage may generalize the formation frame once the world-space model requires it.

## Authored acceptance variants

### Compact 4v4

- center Y: `700 m`
- slot spacing: `120 m`
- slot tolerance: `5 m`
- observable break distance: `80 m`

This matches the existing balanced 4v4 authored line at `520 / 640 / 760 / 880 m`.

### Dispersed 4v4

- center Y: `700 m`
- slot spacing: `240 m`
- slot tolerance: `5 m`
- observable break distance: `80 m`

The desired physical line span is therefore `720 m`, compared with `360 m` in the compact fixture. The acceptance test requires that the two scenarios produce materially different physical Y spans through the same production thrust/flight runtime.

### Scaled 32-ship compact live/headless fixture

`Stage19ScaledLiveTacticalFactory` now authors one compact objective for both 16-ship sides:

- center Y: `710 m`
- slot spacing: `100 m`
- slot tolerance: `5 m`
- observable break distance: `80 m`

These values exactly match the existing `mixed16v16()` initial Y positions (`-40 ... 1460 m`, 100 m spacing), so the formation objective does not inject an artificial initial rearrangement into the established saturation fixture.

All distances above are explicit acceptance-scenario geometry. They are not doctrine bonuses and are not declared final combat balance values.

## Keeping, break and recovery

Per actor the planner exposes:

- `NO_OBJECTIVE` — no authored formation objective;
- `KEEPING` — within slot tolerance with bounded lateral motion;
- `RECOVERING` — physically correcting/braking toward the slot;
- `BROKEN` — beyond break distance, unable to maneuver, or overridden by survival policy.

Stable diagnostic reasons distinguish:

- `IN_TOLERANCE`;
- `SLOT_ERROR`;
- `LARGE_SLOT_ERROR`;
- `SURVIVAL_OVERRIDE`;
- `CANNOT_MANEUVER`.

Recovery braking uses the actor's own physical stopping distance:

```text
stopping distance = lateral speed^2 / (2 * available acceleration)
```

When a ship is already moving toward its slot and would otherwise cross the tolerance before stopping, the planner requests acceleration opposite the current lateral velocity. This avoids an arbitrary target-speed threshold and bounds repeated left/right correction around the slot.

## Survival priority

Formation is subordinate to `TacticalSurvivalPlanner`.

When survival selects `RETREAT`, `DISENGAGE` or `PURSUE`:

- the actor reports `BROKEN / SURVIVAL_OVERRIDE` for formation;
- formation correction is exactly zero;
- the survival maneuver retains movement authority;
- retreat/disengagement fire restrictions remain unchanged.

The formation layer therefore cannot trap a damaged/depleted ship in a slot or override the existing resource/damage survival path.

## Acceptance evidence

`TacticalFormationPlannerTest` proves:

- deterministic stable slot geometry;
- large-error break classification;
- correction toward the slot;
- stopping-distance braking;
- zero formation thrust during survival override;
- wider authored dispersed geometry.

`LiveTacticalFormationAcceptanceTest` proves in one shared 4v4 production runtime:

- a healthy ship starting beyond break distance enters `BROKEN`;
- it subsequently passes through physical `RECOVERING` behavior;
- it can regain `KEEPING`;
- slot error is materially reduced rather than deadlocking;
- late motion remains bounded below the authored break distance;
- a simultaneously damaged ship yields formation authority to survival policy;
- compact and dispersed objectives produce different physical fleet spans through real engineering/thrust/flight;
- identical initial state and tick schedule yield identical control and formation fingerprints.

The scaled live debug projection also exposes, per selected actor:

- formation mode;
- status and reason;
- slot index/count;
- current signed slot error.

Repeated debug reads are acceptance-tested against both the authoritative combat fingerprint and formation fingerprint and cannot advance or mutate simulation authority.

## Known boundary

This slice does not claim a final generic fleet-navigation or rotated formation-frame system. The formation axis is intentionally bound to the current Stage 19I exact-local X-closing fixture.

Stage 19 / Stage 19I must not be marked complete solely from this slice. The remaining exit-contract items still require an explicit line-by-line audit and any missing acceptance evidence before Stage 20 begins.
