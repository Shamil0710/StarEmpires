# Stage 17F.6 — common policy review coordinator

## Status

Implementation slice for the shared anti-oscillation review lifecycle.

This document records the orchestration contract added after the persistent cadence/hysteresis foundation and the first fiscal reviewer.

## Problem

A single persistent `lastPolicyReviewTick` is shared by all strategic policy families of one faction. If fiscal, stock, resilience and later military policy reviewers each claim cadence independently, the first reviewer can consume the review window and prevent the others from observing the same strategic state.

That would also make policy behavior depend on call order instead of the authoritative world state.

## Contract

`FactionPolicyReviewCoordinator` owns the common cadence claim.

```text
explicit autonomous faction set
→ normalize / deduplicate / stable-ID sort
→ derive doctrine-backed response profile
→ build read-only policy plans
→ test common persistent cadence
→ claim the review window once
→ apply bounded plans inside the claimed window
```

The current slice coordinates the fiscal policy family. The seam is intentionally structured so stock/resilience reviewers can later add their own read-only plans before the same claim.

## Hard invariants

1. Autonomous factions are supplied explicitly by the caller. The coordinator does not discover or enable player factions implicitly.
2. Stable faction IDs are normalized, deduplicated and sorted before review. Identical state + identical command input therefore produces identical review order.
3. Planning is read-only and happens before policy mutation.
4. A faction claims at most one common policy-review window per cadence interval.
5. Repeating the coordinator in the same authoritative tick cannot apply another bounded fiscal step.
6. Fiscal execution still uses the existing common `WorldSimulation.updateFactionFiscalPolicy(...)` boundary.
7. Review does not execute tax collection, subsidies, construction funding or any other wallet transfer.
8. A stale fiscal plan is rejected if fiscal policy changes between planning and application.
9. An empty autonomous set changes no faction state.
10. The persistent review watermark remains the only shared lifecycle memory; no hidden utility score is introduced.

## Acceptance

`Stage17F6PolicyReviewCoordinatorAcceptanceTest` proves that:

- an unordered input with duplicate faction IDs produces one stable ordered review per faction;
- selected factions claim exactly one window when due;
- an unselected faction remains unreviewed;
- treasury balances do not move during policy review;
- a repeated call in the same window claims zero reviews and changes zero fiscal policies;
- an empty autonomous set is a no-op.

## Follow-up

The next Stage-17F.6 slices should add stock/resilience read-only plans to this same coordinator rather than creating additional independent cadence claims. Only after all policy families use the common lifecycle should the aggregate anti-oscillation gate mark 17F.6 complete.
