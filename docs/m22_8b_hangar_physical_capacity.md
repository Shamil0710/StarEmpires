# M22.8B — hangars, bays and physical capacity

## Scope

M22.8B adds the physical occupancy boundary required before launch/recovery scheduling. It does not
add a fighter-only movement engine, mission AI, free replacement, launch clocks or tactical combat
rules. Those remain later M22.8 slices.

## Accepted authority reuse

- Every small craft remains one persistent `SmallCraftId` with the exact Stage-17.5 engineering
  state introduced by M22.8A.
- Current craft mass is recomputed by the ordinary `DerivedShipCalculator` from the fitted hull,
  installed modules, current consumables and physical module damage.
- Craft envelope is the authored Stage-17.5 hull `boundingDimensionsM`.
- Ship bays exist only where the installed production fit contains a
  `HANGAR_SMALL_CRAFT` module.
- Bay support mass comes from the existing authored `supported_craft_mass_kg` capability.
- Bay physical envelope and integration volume use the already-authored hangar module geometry.
- Installed module integrity is the current physical bay condition. Damage reduces effective
  aggregate supported mass and usable volume; it never deletes, heals, teleports or silently
  relocates an embarked craft.
- Stations/outposts use the same `BayDefinition` model with explicit SI envelope, volume, support
  mass and condition. No station-role or class-name bonus exists.

## Capacity model

One bay is constrained simultaneously by:

1. a real three-dimensional single-craft envelope;
2. total current embarked-craft mass;
3. summed physical craft bounding-envelope volume;
4. current physical bay condition.

Orthogonal craft orientation is allowed by comparing sorted dimensions. No arbitrary
"fighters-per-carrier" number participates in acceptance. Craft count exists only as diagnostics.

Existing occupancy can become over-capacity after damage. This is represented explicitly by
`INOPERABLE`, `OVER_MASS`, `OVER_VOLUME` or `OVER_MASS_AND_VOLUME`; the registry retains every
individual craft and leaves physical consequences to later launch/recovery/repair logic.

## Finite occupancy

Embarked craft carry exactly one persistent assignment to one stable physical bay and one state:

- `PARKED`
- `SERVICING`
- `READY`
- `LAUNCHING`
- `RECOVERING`

All five states consume the same craft mass and envelope in B. M22.8C will add queue order, cycle
time, service throughput and physical launch/recovery consequences.

`HostKind` is stored explicitly as `SHIP` or `STATION`; it is never inferred from an ID prefix.
A later runtime projection that changes the host family for an occupied `BayId` fails closed.

## Persistence and migration

The M22.8 campaign envelope advances to version 2 and adds an independently versioned hangar
sidecar. Native M22.8A version-1 saves are read explicitly: every craft is preserved and the new
hangar sidecar is empty. Stage-21 migration remains non-granting. No migration path creates a craft,
bay assignment, supply or replenishment.

## Deferred intentionally

M22.8B does not author production small-craft hulls/fits; that remains M22.8J. Existing carrier
hangar modules are sufficient to prove the common physical capacity boundary. B also does not define
launch rates, recovery queues, refuel/rearm/repair work, mission command, tactical materialization or
carrier doctrine.

## Acceptance evidence

Automated tests cover:

- physical envelope/mass/volume admission without class-count rules;
- capacity loss under real module damage without craft disappearance;
- resolution of the production Empire carrier hangar module;
- identical station-bay capacity semantics;
- individual occupancy state transitions without changing physical usage;
- duplicate/unknown craft and host-family mismatch rejection;
- deterministic hangar-sidecar round trip;
- native M22.8A migration with zero invented occupancy;
- complete campaign capture/restore preserving an individual craft's bay and handling state.
