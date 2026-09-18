# M22.8C — launch, recovery and finite turnaround

## Scope

M22.8C adds deterministic physical handling between M22.8B bay occupancy and later M22.8E
Stage-19 local-flight materialization. It also adds finite post-recovery turnaround requirements.
It does not create a second movement/tactical engine, mission AI, free replenishment, production
small-craft content or player-only carrier rules.

## Authoritative tick integration

`SmallCraftFlightDeckOperations` owns no clock. `GeneratedCampaignCoordinator` exposes a
post-fixed-tick observer seam and `Stage228CampaignAuthority` advances the deck service only after
an existing Stage-20/21 authoritative tick has completed. The exact
`GeneratedCampaignSession.fixedStepSeconds()` value is reused for deck work.

There is therefore no independent carrier timer and no wall-clock progression.

## Deterministic per-bay queue

Each physical bay has one `DeckProfile` containing explicit pristine launch/recovery work seconds.
One bay can execute at most one active handling operation.

Queue order is deterministic:

1. lower authoritative request tick;
2. bay identity;
3. operation kind, with `RECOVERY` before `LAUNCH` at the same tick/bay;
4. stable `SmallCraftId`.

Runtime and persistence use the same ordering contract.

Current physical bay integrity directly affects work throughput:

`completedWork = fixedStepSeconds × conditionFraction`

A zero-integrity bay performs zero handling work. Damage therefore delays operations through real
physical state, not a carrier-class modifier.

## Launch handoff — no teleport

A launch may be requested only for one individual `READY` craft already occupying the requested
bay.

State progression:

`READY → LAUNCHING → AWAITING_HANDOFF`

Completing deck work does **not** remove the craft from hangar occupancy. It remains physically
present in `LAUNCHING` state while the deck operation is `AWAITING_HANDOFF`. Only the
package-private physical-world handoff seam may release it after later Stage-19 integration has
materialized the same `SmallCraftId` in local flight.

Command/UI code therefore cannot make a craft disappear by completing a timer.

## Recovery — no silent parking

A returning craft is offered by the physical-world seam while it is still outside bay occupancy.
It may wait in queue even when a bay is full. Capacity/envelope are rechecked when recovery actually
starts.

Successful progression:

`outside → RECOVERING → SERVICING`

A failed recovery enters `FAILED_BLOCKED`; the craft remains `RECOVERING` and the physical bay is
blocked. It cannot silently become `PARKED`, `SERVICING` or `READY`. A later physical-world
resolution must explicitly confirm departure/diversion/loss after Stage-19 has applied the real
consequence.

## Finite turnaround

`SmallCraftTurnaroundService` requires an already recovered `SERVICING` craft and never creates
resources.

### Fuel / reaction mass and ammunition

Transfers name an installed mount, authored interface ID and `InterfaceKind`, plus:

- native interface amount;
- physical mass;
- item count where meaningful.

The target interface must exist on the installed Stage-17.5 module and its authored capacity may not
be exceeded. A `ServiceProfile` provides explicit physical transfer rates in native interface
units per simulation second.

Every turnaround also pays positive base inspection work. Required handling work is:

`inspection work + Σ(transfer amount / physical transfer rate)`

Completion requires the exact requested transfers plus all required handling work.

### Repair and scheduled maintenance

M22.8C does not invent repair coefficients. It reuses
`ShipyardEngineeringService.planRepair/completeRepair` and
`planMaintenance/completeMaintenance`.

Therefore damaged structure/modules retain the ordinary Stage-17.5G requirements for:

- physical construction/repair inputs;
- tooling and fabrication capability;
- precision;
- labor and automation;
- industrial power;
- engineering work seconds.

M22.8G will bind those requirements to live Stage-18 inventory/logistics consumption. C already
fails closed unless a complete `WorkSettlement` is supplied.

### Post-service capacity

Loaded fuel/ammunition changes the same craft's current physical mass. Before
`SERVICING → READY`, the completed state is recalculated through the shared engineering authority
and checked against the current damaged bay capacity while excluding only that same craft's old
footprint.

A turnaround that would make the craft too heavy or otherwise invalid fails before mutating its
persistent state.

## Save/load continuity

The M22.8 campaign envelope advances to v3 and adds an independently versioned flight-deck sidecar.

Persisted C state includes:

- physical `DeckProfile` values;
- queued requests and authoritative request ticks;
- active operation phase;
- remaining handling work;
- recovery failure classification.

Restore validates queue/active state against restored physical hangar occupancy:

- queued launch requires the same craft to be `READY` in the same bay;
- queued recovery must remain outside bay occupancy;
- active launch requires `LAUNCHING`;
- active recovery requires `RECOVERING`;
- `AWAITING_HANDOFF` carries no remaining deck work;
- impossible `QUEUED` active operations are rejected;
- one craft cannot appear in multiple operations;
- one bay cannot have multiple active operations.

Native M22.8A v1 and M22.8B v2 files migrate explicitly to v3. Existing physical state is preserved
and the new C sidecar starts empty; migration never invents launch/recovery work.

## Deferred intentionally

- M22.8D: mission/command vocabulary and player/AI shared order validation.
- M22.8E: actual Stage-19 local-flight entity materialization, recovery contact consequences and
  combat damage/loss.
- M22.8G: live Stage-18 inventory/logistics consumption and replacement production.
- M22.8J: authored production small-craft hulls/fits/assets.
- M22.8I: player-facing carrier UI.

## Acceptance evidence

Automated tests cover:

- deterministic fixed-tick launch/recovery sequencing;
- damage-scaled throughput and zero-integrity stop;
- no launch removal before physical handoff;
- recovery waiting outside a full bay;
- failed recovery blocking without silent teleport;
- duplicate/wrong-state command rejection;
- finite reaction-mass/ammunition delivery and handling work;
- authored interface-capacity rejection;
- repair/maintenance through ordinary shipyard plans and settlements;
- post-service bay-mass rejection without craft mutation;
- deterministic flight-deck sidecar round-trip;
- preserved recovery priority after save/load;
- fail-closed malformed active states;
- campaign save/load preserving `AWAITING_HANDOFF`;
- non-granting native M22.8B → v3 migration.
