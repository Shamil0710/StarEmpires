# Stage 20.5B — Freight fleet, cargo order and lot materialization v1

## Status

Implemented as the second runtime-integration seam from
`stage20_5_runtime_visual_integration_plan_v1.md`.

The bridge consumes the saved Stage-20K generated campaign and the exact closed Stage-20F
specialization. It does not invoke generation during bootstrap or restore.

## Runtime contract

`Stage20FreightRuntimeMaterializer` expands every accepted Stage-20E ownership slot exactly once:

- every materialized asset receives a persistent positive `FleetId`;
- the stable faction ID and exact ownership ordinal are retained;
- Stage-20E committed slots remain essential-service orders and cannot be reused;
- Stage-20F industrial allocations consume only their explicitly assigned reserve slots;
- remaining reserve slots become idle owned physical ships rather than hidden capacity;
- every active assignment is an ordinary persisted transport order with an explicit neighbor route,
  source and destination endpoints, commodity, provenance, delivery deadline and ready-again cycle;
- initial holds and cargo-lot registries are empty.

The materializer cross-checks the accepted ownership authority against the canonical saved
`FREIGHT_OWNERSHIP_SLOT` rows. It also verifies every route hop against the saved topology and takes
initial physical positions from the saved local major-hub placements.

## Physical hull and capacity authority

The current repository does not yet contain a Stage-22 production cargo hull. The bridge therefore
uses a named compatibility authority for the production-valid but content-provisional
`hull.test_bulk_freighter_v1` / `fit.test_bulk_freighter_baseline_v1` pair. The authority:

- pins the exact engineering-catalog fingerprint;
- retains the accepted `12,000,000 kg` Stage-20 payload;
- derives a fully loaded ship through `DerivedShipCalculator`;
- rejects a load that exceeds the hull mass or integration-volume envelope;
- carries a mandatory Stage-22 review marker.

No legacy trader archetype is silently interpreted as this freighter.

## Cargo conservation and provenance

`Stage20FreightRuntime` owns mutable freight lifecycle state. Loading and unloading call
`Stage18LogisticsRuntime.transferCommodity` between ordinary `Stage18StationStorage` instances.
Consequently:

1. an order alone cannot create inventory;
2. source storage loses mass atomically before an aboard lot is recorded;
3. every lot retains fleet, order, commodity, source endpoint, accepted source provenance and load
   time;
4. unloading moves the same physical mass to destination storage and consumes lots FIFO;
5. capture/restore retains FleetIds, ownership ordinals, holds, lots, routes, kinematics, deadlines
   and delivery totals exactly.

The total hold mass is additionally limited by the validated hull capacity across storage classes,
so per-class station capacity cannot be used to exceed the ship-wide cargo envelope.

## Route and loss behavior

Outbound and return travel advance only one persisted neighbor hop at a time. Each completion
requires explicit arrival kinematics; Stage 20.5D binds those arguments to the saved jump-edge
arrival authority.

The ready-again cycle is reconstructed from the retained physical evidence as
`allocatedFreighters × payloadKg / sustainableThroughputKgPerSecond`, which is the exact cycle used
by the Stage-20J cadence acceptance and does not require regenerating the world.

Destruction marks the same FleetId permanently destroyed, removes its physical aboard inventory and
lot provenance, and creates no replacement. The associated route therefore loses future delivery
capacity until a later ordinary construction/replacement mechanic supplies a real new asset.

## Acceptance coverage

`Stage20FreightRuntimeMaterializerTest` proves:

- exact one-to-one owned-slot/FleetId materialization;
- no essential-slot reuse by industrial routes;
- zero initial cargo and lots;
- source-to-hold-to-destination mass conservation with provenance;
- explicit route-hop progression and physical kinematics retention;
- deterministic sidecar capture/restore;
- destruction loss without respawn or replacement;
- fail-closed restore on saved-world identity mismatch.

## Remaining Stage 20.5 work

- Stage 20.5D: bind live jump completion to exact saved edge arrival position and velocity.
- Stage 20.5E: add the minimum playable top-down sprite pack and runtime identity binding.
- Final generated-world playable acceptance and closure of the five saved runtime boundaries.
