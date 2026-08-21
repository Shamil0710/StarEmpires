# Stage 20E — Resolved whole-seed acceptance v2 seam

> Status: **CANDIDATE PRODUCTION WHOLE-SEED COMPOSITION / NO WORLD REPAIR**  
> Version: `stage20e.generated-world-seed-acceptance.v2`

## Purpose

The historical whole-seed composition consumes `Stage20EconomicThroughputAcceptance`, whose contract is single-supplier and does not model simultaneous finite multi-commodity freight allocation.

The accepted Stage-20E frontier work now provides a stronger production authority:

```text
accepted faction-start placement
→ per-commodity resolved physical frontiers
→ exact finite-fleet cross-commodity combination
→ Stage20ResolvedFreightAcceptance
```

This slice adds a separate resolved-freight composition path without deleting or mutating the historical v1 composition used by baseline evidence.

## Ordering contract

The v2 order is mandatory:

```text
topology
→ faction-start placement
→ coordinated freight acceptance only when placement is ACCEPTED
→ whole-seed decision
```

A topology-rejected seed carries neither placement nor freight evidence.

A placement-rejected or placement-unresolved seed must not carry a synthetic freight report because there is no accepted ordinary start set to service.

An accepted non-empty placement must carry coordinated freight evidence for exactly the same placed faction set.

## Status mapping

```text
complete coordinated freight ACCEPTED
→ whole seed ACCEPTED, absent another rejection

complete coordinated freight INFEASIBLE
→ whole seed REJECTED_SEED
→ COORDINATED_FREIGHT_INFEASIBLE

coordinated freight UNRESOLVED_FRONTIER
→ whole seed UNRESOLVED_AUTHORITY
→ COORDINATED_FREIGHT_AUTHORITY_UNRESOLVED
```

Search-budget exhaustion therefore cannot become physical rejection.

Placement rejection remains `FACTION_START_PLACEMENT_REJECTED`; placement authority uncertainty remains `FACTION_START_AUTHORITY_UNRESOLVED`.

## Historical compatibility

`Stage20GeneratedWorldSeedAcceptance.compose(...)` remains the historical v1 single-supplier path and continues to emit:

`stage20e.generated-world-seed-acceptance.v1`

The new production candidate path is:

`Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(...)`

and emits:

`stage20e.generated-world-seed-acceptance.v2`

The two APIs have different names deliberately: Java generic erasure would make overloads distinguished only by `Optional<T>` ambiguous, and silent semantic substitution would make the old baseline unreproducible.

## Authority boundary

This slice does not:

- generate or repair topology;
- change faction-start placement;
- derive freight capacity;
- run frontier search itself;
- change resources, demand, payload, FTL or local route timing;
- create ownership, ships, cargo, inventory or money;
- alter the production probe yet.

It only maps already authoritative generation outputs into a whole-seed status.

## Regression boundary

Tests require:

1. accepted coordinated freight maps to whole-seed `ACCEPTED`;
2. complete freight infeasibility maps to `REJECTED_SEED`;
3. unresolved freight maps to `UNRESOLVED_AUTHORITY` rather than rejection;
4. rejected placement requires no freight result and rejects synthetic freight evidence;
5. accepted placement cannot silently omit coordinated freight authority;
6. historical v1 composition remains source-compatible.

## Next causal slice

After this composition seam is accepted, update `Stage20GeneratedWorldProductionProbe` so that the representative production path:

1. performs placement first;
2. derives the already accepted finite capacity (`13/start`) from its calibration authority;
3. builds the physical allocated-route evaluator through the accepted factory;
4. calls `Stage20ResolvedFreightAcceptance` only for accepted placement;
5. calls `composeResolvedFreight(...)` for the final whole-seed decision;
6. measures the unchanged fixed 1..16 corpus without a pass-rate target.
