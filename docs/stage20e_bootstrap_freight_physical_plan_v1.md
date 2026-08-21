# Stage 20E — Bootstrap freight selected physical plan v1

> Status: **CANDIDATE OWNERSHIP/MATERIALIZATION SEAM / NO NEW ECONOMIC AUTHORITY**  
> Version: `stage20e.bootstrap-freight-physical-plan.v1`

## Why this slice exists

The accepted per-commodity frontier stores the full physical supplier commitments needed to reconstruct an actual bootstrap freight service:

- stable faction/start identity;
- producer system;
- commodity;
- explicit ordered neighbor route;
- integer allocated remote freighters;
- committed delivered kg/s;
- authoritative producer-capacity reservation.

The exact cross-commodity combiner intentionally projects each rich option down to a ship-count vector and stable option ID. That is sufficient to prove whether the finite per-start freight fleet can satisfy all selected commodities, but it is not enough to materialize owned transport: a ship-count vector alone does not say which producer, route or throughput commitment the ships belong to.

This slice performs the missing deterministic reverse join.

## Reconstruction contract

Inputs:

```text
rich per-commodity FrontierReport set
+ ACCEPTED CombinationReport
```

For every `SelectedOption` from the combiner, reconstruction requires the exact source frontier version and exact option ID to exist in the supplied rich frontier.

The selected combiner vector must equal the rich option vector exactly. The result then retains the original:

```text
StartPlan
→ DemandPlan
→ SupplierCommitment
→ RouteAssessment

ProducerUsage
```

The aggregate ship usage reconstructed from all selected commodity plans must equal the combiner's accepted `remoteFreightersUsedByFaction` exactly.

Any missing option, frontier-version mismatch, vector mismatch, duplicate commodity or non-accepted combiner result fails closed.

## Why this precedes FleetId allocation

A concrete bootstrap freight asset must eventually answer:

```text
who owns it?
which start fleet pool does it consume?
which commodity commitment does it serve?
which producer does it load from?
which explicit neighbor route does it execute?
which committed throughput is it part of?
```

Creating `FleetId`s directly from only `{faction -> ship count}` would lose those physical causes and make later logistics a parallel authority. Therefore persistent ownership/materialization must consume this rich selected physical plan rather than the combiner projection alone.

## Explicit non-authorities

This slice does not:

- create a fleet or allocate `FleetId`;
- create cargo or inventory;
- move any entity;
- begin a jump or transfer;
- create money or ledger entries;
- reserve producer throughput a second time;
- change topology, resource occurrence, demand, route cadence, payload or the `13`-freighter/start bound;
- claim a legacy `item.*` trader archetype is equivalent to the Stage-20 reference freighter;
- persist a new ownership assignment yet.

It only restores physical evidence that already existed upstream and was intentionally projected away for the exact finite-fleet join.

## Regression boundary

The v1 acceptance tests require:

1. an accepted two-commodity combination reconstructs the exact producer routes and reservations;
2. aggregate reconstructed ship usage equals exact combiner usage;
3. input frontier ordering cannot change the result;
4. a tampered ship-count vector fails closed;
5. an absent selected rich option fails closed;
6. a non-accepted combiner result cannot become a physical ownership plan.

## Verification evidence

The initial dependency merge-ref Java 17 `clean verify` run `32491405418` completed successfully across tests, coverage, Javadoc and desktop-package verification. The upstream maximum-cap resolver is now merged into `main` as `f7ef11a79cf9599403d1004733b2c3c12159be61`.

This evidence-recording commit intentionally creates a new PR head after retargeting to current `main`; a fresh current-`main` merge-ref CI run is the final acceptance gate.

## Next causal slice

After this reconstruction seam is accepted, Stage 20E can allocate concrete persistent freight ownership against the returned remote commitments.

That materialization must reuse the existing world fleet authority:

```text
WorldSimulation.createEntity(FLEET)
→ system-local EntityId
→ FleetWorldService.registerLocal
→ stable FleetId
```

and must preserve the same `FleetId` through ordinary transfer/save-load/arrival. It must not introduce a second freight-ID registry.

The first ownership materialization slice should create no deliveries. Its purpose is only to bind the exact number of real fleet identities to the already selected remote commitments, with atomic rollback on partial creation failure. Inventory buffers, cargo execution, price pressure and delivered-cost accounting remain later causal slices.
