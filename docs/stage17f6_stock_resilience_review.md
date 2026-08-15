# Stage 17F.6 — bounded stock / resilience review

## Status

Implementation slice for Stage 17F.6 policy feedback / anti-oscillation.

This slice joins resilience-driven strategic stock review to the same persistent common review window already used by fiscal policy. It does **not** complete Stage 17F.6.

## Causal contract

```text
physical stock / market targets / legal access / suppliers / topology
→ Stage-17E/17F.5 structural dependence diagnostics
→ FactionResiliencePlanner recommendation
→ common Stage-17F.6 cadence claim
→ bounded stock-policy authoring step
→ explicit ordinary strategic-policy apply later
→ market prices / traders / logistics react physically
```

The reviewer does not create cargo, money, production output or market demand directly.

## Shared cadence

`FactionPolicyReviewCoordinator` now builds both fiscal and stock/resilience plans before mutation.

For each explicitly authorized autonomous faction:

1. derive the doctrine-backed fiscal profile;
2. build the read-only fiscal plan;
3. build the read-only resilience stock plan;
4. test and claim the persistent common review window once;
5. apply at most one bounded fiscal step and one bounded stock step inside that claim.

Fiscal and stock reviewers therefore cannot consume independent review windows or oscillate according to caller order.

## Stock adjustment semantics

`FactionStockResilienceReviewProfile` currently defines:

- a small absolute deadband in units;
- a maximum upward stock-floor change per review.

A resilience recommendation above the current persistent stock floor changes the floor only when the delta exceeds the deadband, and then by no more than one bounded step.

Production-policy choices are preserved exactly.

## Why downward adjustment is blocked for now

Automatic stock-floor reduction is deliberately **not** implemented in this slice.

The current economic core has two provenance gaps:

1. `FactionStockProductionPolicyState.stockPolicies()` does not distinguish an operator-authored/base strategic floor from a temporary resilience contribution.
2. `FactionStrategicPolicyEngine` applies demand to `MarketComponent.targetStock` by taking a maximum. `MarketComponent` stores only one mutable target value and does not retain a separate configured baseline or policy contribution.

Therefore simply lowering the persistent faction stock floor would not reliably lower physical market demand, and it could also overwrite an intentional player/AI base reserve.

When the current floor is materially above the newly recommended resilience target, the reviewer reports `blockedDecreaseItemCount` and preserves the existing floor.

This is an explicit correctness guard, not a completed recovery path.

## Required follow-up prerequisite

Before bounded downward recovery is enabled, market target provenance must become reversible and persistent. The intended invariant is conceptually:

```text
effective market target
= max(
    configured station baseline,
    base faction stock policy,
    active strategic-goal / resilience contribution
  )
```

The implementation may use an equivalent source-attribution model, but it must satisfy all of the following:

- base/configured station demand remains distinguishable from policy demand;
- player-authored base stock policy remains distinguishable from automatic resilience overlay;
- removing/reducing one contribution cannot erase another;
- save/load preserves the attribution required for deterministic continuation;
- old saves migrate conservatively and never silently reduce an existing physical target;
- applying/removing policy still creates no goods or money;
- ordinary Market/TradeAI/logistics remain responsible for the physical consequence.

Only after this prerequisite exists should Stage 17F.6 add bounded downward resilience recovery.

## Acceptance in this slice

`FactionStockResiliencePolicyReviewerTest` verifies:

- an upward recommendation moves by at most one configured step;
- a small delta inside the deadband is stable;
- production policy is preserved;
- a lower recommendation is reported as blocked and never auto-applied without provenance.

`Stage17F6StockResilienceCoordinatorAcceptanceTest` verifies on a live physical dependence scenario that:

- the first due common review claims exactly one window;
- resilience raises the stock policy by only one bounded step;
- fiscal and stock review share the same persistent claim;
- policy authoring does not change inventory, entity wallets, treasury, production policy or physical market targets;
- a repeated call in the same window claims nothing and cannot apply a second stock step.

## Remaining Stage 17F.6 work

After this slice:

1. add reversible persistent market-policy provenance;
2. enable bounded downward stock recovery through that provenance-safe path;
3. review whether production-policy switching needs an equivalent dwell/deadband rule;
4. run an aggregate long-horizon anti-oscillation acceptance covering fiscal + stock/resilience decisions across repeated shocks and recoveries;
5. only then mark Stage 17F.6 complete and proceed to Stage 17F.7 player/AI parity.
