# Stage 20E — Bootstrap freight ownership plan v1

> Status: **CANDIDATE OWNERSHIP AUTHORITY / NO RUNTIME ASSET CREATION**  
> Version: `stage20e.bootstrap-freight-ownership-plan.v1`

## Purpose

The accepted Stage-20E freight-capacity requirement is explicitly **per ordinary faction start** and is not a hidden galaxy-global transport pool. The selected physical freight plan identifies how many of those freighters are required by concrete remote producer commitments, but it does not yet state who owns the remaining capacity or how much reserve exists.

This slice closes that ownership gap without creating runtime ships.

For every accepted faction-start assignment it binds:

```text
stable faction identity
+ accepted start system
+ finite owned freight capacity
→ committed remote freight subset
+ uncommitted reserve subset
```

The full owned capacity comes directly from the resolved acceptance budget retained by the selected physical plan. Ownership accepts no second caller-supplied capacity map, so the already accepted finite fleet cannot be silently enlarged or reduced. The selected physical plan may consume only a subset of that exact capacity.

## Ownership semantics

For one placed faction start:

```text
ownedFreighterCount
= committedFreighterCount + reserveFreighterCount
```

`committedFreighterCount` must equal the aggregate selected physical-plan usage for that faction.

Every committed ship count is backed by a real remote `SupplierCommitment` retaining:

- source frontier version and selected option ID;
- Stage-18 commodity ID;
- stable owning faction/start identity;
- producer system;
- consumer faction-start system;
- explicit physical neighbor route;
- integer allocated freighters;
- committed delivered kg/s.

Each aggregate commitment receives a deterministic planning-only `CommitmentKey`. The owned pool can be expanded into an exact ordered list of `OwnershipSlot`s: committed slots carry the source key plus a per-commitment freighter ordinal, while reserve slots carry no commitment. These are not runtime IDs and do not compete with `FleetId` authority.

Commitment keys must be unique inside each owned faction pool. Duplicate source keys fail closed before aggregate allocations can expand into ambiguous logical freighter slots.

Local producer service consumes zero remote freight ownership slots.

The public ownership authority consumes one accepted `ResolvedProbeResult`. It takes placement and freight acceptance from that same root-seed object and reconstructs the selected physical plan internally, so a physical plan cannot be paired with an unrelated seed's placement even when the faction/start mapping happens to match.

The ownership capacity equals both the preserved acceptance budget and the `remoteFreighterBudget` carried by every selected `StartPlan`. The resulting `OwnershipReport` retains the complete immutable physical plan, generated root seed and placement-profile provenance for the later bootstrap bridge.

## Home system is not spawn position

`homeStartSystemId` means only:

> this finite freight pool belongs to the faction start selected in this system.

It does **not** mean:

- all ships already physically exist at one coordinate;
- they are docked at a particular station;
- they start at the consumer rather than the producer;
- they contain cargo;
- a delivery has already occurred.

Physical initial placement remains a later bootstrap-state/materialization authority.

## Why this is separate from FleetId

The existing runtime already owns fleet identity:

```text
WorldSimulation.createEntity(FLEET)
→ local persistent EntityId
→ FleetWorldService.registerLocal
→ stable FleetId
```

This slice must not create another persistent freight-ID namespace. It only establishes how many runtime fleet assets a later materializer is authorized to create, who owns the pool and which selected commitments consume its capacity.

## Fail-closed invariants

Ownership planning rejects when:

- faction-start placement is not accepted;
- placement and selected physical plan do not cover the same faction set;
- placement version differs from the selected physical authority;
- a selected physical start differs from the accepted placement;
- selected `StartPlan.remoteFreighterBudget` differs from ownership capacity;
- aggregate selected ship usage exceeds owned capacity;
- remote commitment counts do not reconstruct aggregate selected usage;
- a local commitment attempts to consume remote freighters.

## Explicit non-authorities

This slice does not:

- allocate `FleetId` or `EntityId`;
- create any Ashley entity;
- choose a physical spawn coordinate;
- preload cargo or inventory;
- transfer money;
- record ledger entries;
- execute a route/jump;
- change producer capacity;
- change topology/resources/demand/payload/cadence;
- change the accepted finite freight-capacity requirement.

The deterministic `CommitmentKey`, `CommitmentSlot` and `OwnershipSlot` values are immutable bootstrap evidence only. They authorize creation order and source binding; only `WorldSimulation` / `FleetWorldService` may allocate persistent runtime fleet identity.

## Next causal slice

After this ownership authority and the selected physical-plan reconstruction are accepted, Stage 20E needs the generated-world bootstrap bridge that turns accepted generation state into authoritative persistent world state.

That bridge must consume rather than duplicate:

- generated topology / local layouts / facilities / resource state;
- accepted faction-start placement;
- selected physical freight plan;
- this ownership plan;
- existing `WorldFactionIdentityState` and `WorldSimulation` fleet identity machinery.

Only inside that authoritative bootstrap-state path should actual Stage-20-compatible freight entities receive `FleetId`s. The materializer must create the exact owned count, preserve committed-versus-reserve provenance and roll back atomically if partial creation fails. It must not use legacy `item.*` trader archetypes as a substitute for the Stage-20 physical reference freighter unless a separate compatibility authority explicitly proves that equivalence.
