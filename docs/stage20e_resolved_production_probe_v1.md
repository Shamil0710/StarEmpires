# Stage 20E — Resolved coordinated freight production probe v1

> Status: **CANDIDATE AUTHORITATIVE PRODUCTION ACCEPTANCE PATH / NO PHYSICAL WORLD CHANGE**  
> Version: `stage20e.resolved-production-seed-probe.v1`

## Purpose

The historical `Stage20GeneratedWorldProductionProbe` remains the reproducible physical generation/evidence pipeline used by the v1/v2 representative profiles. It already generates, in deterministic order:

```text
macro geometry
→ jump topology
→ local SI layouts
→ jump-edge physical state
→ resource hosts / finite occurrences
→ extraction logistics and production capacity
→ physical supply closure
→ faction-start diagnostics / placement
```

Its final historical economic gate is single-supplier and therefore cannot be the final Stage-20E production authority after the accepted finite-fleet frontier/combiner work.

This slice does not rewrite or rerun those physical layers. Instead it introduces a v3 production profile and a resolved wrapper that consumes the exact generated evidence once and replaces only the final acceptance authority.

## V3 representative profile

`stage20e.representative-production-probe-profile.v3` preserves the complete v2 `ProbeInputs` exactly and adds one explicit object:

`stage20e.coordinated-freight-acceptance-profile.v1`

Therefore v3 changes neither:

- macro geometry/topology policy;
- infrastructure placement policy;
- corrected bootstrap requirements;
- faction identities or placement policy;
- fitted FTL plans;
- representative freight payload/transport physics.

The coordinated freight policy provides:

```text
physical capacity = derived 13 freighters / accepted ordinary start
search work bound = 2,000 nodes / commodity
```

The capacity remains physical calibration authority; the node count remains a computational bound. Search exhaustion maps to unresolved authority.

## Resolved production path

`Stage20ResolvedGeneratedWorldProductionProbe` executes:

```text
Stage20GeneratedWorldProductionProbe.run(seed, v3.inputs)
→ exact unchanged physical generation evidence
→ inspect already-generated faction-start placement
→ if placement ACCEPTED:
     create the physical route evaluator with the explicit derived fleet capacity
     run Stage20ResolvedFreightAcceptance
→ compose Stage20GeneratedWorldSeedAcceptance.v2
```

The historical `economicAcceptance` retained inside the source `ProbeResult` is diagnostic provenance only for this v3 wrapper. It is not consulted by the authoritative resolved whole-seed decision.

No topology/resource/layout/supply layer is rerun after seeing freight feasibility, so coordinated freight cannot trigger a hidden rescue roll or world repair.

## Whole-seed semantics

```text
topology rejected
→ no placement / no coordinated freight
→ REJECTED_SEED

placement rejected
→ no synthetic freight evaluation
→ REJECTED_SEED

placement unresolved
→ no synthetic freight evaluation
→ UNRESOLVED_AUTHORITY

placement accepted + freight accepted
→ ACCEPTED

placement accepted + complete freight infeasible
→ REJECTED_SEED

placement accepted + freight frontier unresolved
→ UNRESOLVED_AUTHORITY
```

## Fixed-corpus evidence policy

`Stage20ResolvedProductionProbeCorpusDiagnostics` replays fixed root seeds `1..16` through the complete v3 wrapper and emits:

- final resolved whole-seed status;
- placement status;
- exact freight combiner status where applicable;
- normalized whole-seed failure/blocker reasons;
- bounded freight search nodes.

The test applies no accepted-seed-rate target. Any difference from earlier component-level freight evidence must be investigated causally, not tuned away.

CI marker:

```text
STAGE20E_RESOLVED_PRODUCTION_PROBE_CORPUS_BEGIN
...
STAGE20E_RESOLVED_PRODUCTION_PROBE_CORPUS_END
```

## Explicit non-authorities

This slice does not:

- change generated topology or spatial geometry;
- change resource occurrence, producer capacity or bootstrap demand;
- change payload, FTL cadence, local handling or the derived 13-freighter capacity;
- create freight ownership, `FleetId`, cargo, inventory, money or delivery execution;
- change prices/buffer stocks/delivered monetary cost;
- repair a failed generated seed;
- replace historical v1/v2 probe evidence.

## Acceptance gate

Before this slice can be accepted:

1. dependency PRs for resolved whole-seed composition and coordinated-freight production policy must be accepted;
2. exact merge-ref Java 17 `clean verify` must pass;
3. fixed 1..16 marker output must be recorded and inspected;
4. unresolved search, if any, must remain `UNRESOLVED_AUTHORITY`;
5. no physical authority may be changed in response to corpus results.

## Next causal slice

After this resolved production path is accepted, Stage 20E can return to the already prepared physical-plan reconstruction and ownership chain:

```text
accepted resolved freight
→ selected rich physical plan reconstruction
→ finite owned freight pool = committed + reserve
→ generated-world/runtime bootstrap bridge
→ actual persistent FleetId assets
```

Only after real owned transport exists should delivery execution, inventory buffers, price pressure and delivered monetary cost be integrated.
