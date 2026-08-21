# Stage 20E — Finite freight portfolio allocator v1

Status: **allocator semantics candidate; no seed-acceptance promotion yet**.

This slice follows the bootstrap freight-capacity requirement established in PR #275. The calibration authority derives the minimum representative freight-service capacity per ordinary faction start from upstream physics/economics; this allocator defines how one finite per-start inter-system fleet may be shared between essential commodities and multiple physical suppliers without double counting ships.

## Scope

`Stage20FreightPortfolioAllocator` allocates one ordinary faction-start system at a time. Inputs are:

- authoritative explicit-neighbor topology;
- non-reserved Stage-20E physical supply-throughput closure;
- one start system;
- versioned essential commodity requirements;
- one finite remote-freighter budget;
- a physical route evaluator that evaluates the same route with an explicit integer number of allocated freighters.

The allocator does not change topology, supply, demand, route time, FTL, payload or station handling. It is read-only.

## Local supply

Supply already produced in the start system is credited first from the system-level physical supply closure. It consumes **zero inter-system freighters**. This matches the existing faction-start dependency-diagnostic distinction between local production and imported delivery.

The allocator does not claim local logistics are free in the wider economy: local extraction/export capacity has already been bounded upstream. This rule only states that a same-system requirement does not reserve a jump-capable inter-system freight ship.

## Remote supplier curves

For each time-admitted remote supplier, the allocator evaluates cumulative delivered throughput with 1, 2, ... up to the finite start budget.

The cumulative curve is producer-capped and must be:

- monotone non-decreasing;
- composed of non-increasing positive marginal throughput increments;
- on the same explicit neighbor route with the same delivery time for every allocation count.

These invariants match the current physical freight form `min(k × payload / cycle, endpoint handling ceiling, producer capacity)`. A route that becomes unavailable, changes path/time because only the allocated ship count changed, decreases throughput or gains a larger later marginal is rejected as inconsistent authority rather than silently accepted.

## Minimum allocation

For one commodity, only the **next prefix marginal** of each supplier route is eligible. The allocator repeatedly takes the largest deterministic next marginal until the remaining demand is covered or the per-start budget is exhausted. Equal marginal capacity is resolved by supplier system ID.

Because each accepted route curve is concave/non-increasing in marginal capacity, this produces the minimum integer ship count for that commodity. A second freighter on a route cannot be selected unless the first has already been selected.

Each essential commodity is planned independently, then the minimum remote ship counts are summed. The start is accepted only if:

1. every commodity is individually satisfiable within the full finite budget; and
2. the sum of those minimum allocations is no greater than the one shared start fleet.

This prevents the same freighter from being counted once for water and again for metallic ore.

## Explicit failure semantics

Per commodity:

- `INSUFFICIENT_ADMITTED_SUPPLY` — local plus route-time-admitted producer capacity is below demand;
- `INSUFFICIENT_FREIGHT_CAPACITY` — producer capacity exists, but the finite remote fleet cannot transport enough;
- `SATISFIED` — a deterministic minimum local/remote portfolio exists.

Per start:

- `REQUIREMENT_UNSATISFIED` — at least one commodity has no feasible plan;
- `SHARED_FLEET_EXHAUSTED` — every commodity is individually feasible, but their minimum ship counts exceed the one finite shared pool.

## Important remaining boundary

This v1 allocator is intentionally **single-start**. It does not yet reserve one supplier's finite producer capacity simultaneously across several placed faction starts. Therefore it must not be promoted directly into whole-seed economic acceptance as if two factions could both consume the same producer capacity.

A later Stage-20E whole-placement allocation/ownership slice must resolve:

- shared producer-capacity reservation between placed starts;
- which start/faction controls or is guaranteed the calibrated freight-service capacity;
- initial ship/service materialization and ownership semantics;
- supplier/facility ownership concentration authority.

Only after that boundary is explicit should portfolio throughput replace the historical single-supplier final gate.
