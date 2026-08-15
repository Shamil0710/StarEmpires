# Stage 17F.6 — bounded stock / resilience review

## Status

Implementation slice for Stage 17F.6 policy feedback / anti-oscillation.

This slice joins resilience-driven strategic stock review to the same persistent common review window already used by fiscal policy and upgrades the earlier upward-only guard to a separate reversible automatic demand overlay. It does **not** complete Stage 17F.6.

## Causal contract

```text
physical stock / market targets / legal access / suppliers / topology
→ Stage-17E/17F.5 structural dependence diagnostics
→ FactionResiliencePlanner recommendation
→ common Stage-17F.6 cadence claim
→ bounded automatic RESILIENCE overlay adjustment
→ explicit ordinary strategic-policy apply later
→ effective market target recomputed from all current demand sources
→ market prices / traders / logistics react physically
```

The reviewer does not create cargo, money, production output or market demand directly. Authoring changes only persistent policy state until `applyFactionStrategicPolicy(...)` is explicitly invoked.

## Shared cadence

`FactionPolicyReviewCoordinator` builds both fiscal and stock/resilience plans before mutation.

For each explicitly authorized autonomous faction:

1. derive the doctrine-backed fiscal profile;
2. build the read-only fiscal plan;
3. build the read-only resilience-overlay plan;
4. test and claim the persistent common review window once;
5. apply at most one bounded fiscal step and one bounded resilience-overlay step inside that claim.

Fiscal and resilience reviewers therefore cannot consume independent review windows or oscillate according to caller order.

## Separate automatic resilience overlay

Automatic resilience demand is no longer written into the common operator/player/AI base `FactionStockProductionPolicyState.stockPolicies()`.

It is stored as one canonical persistent strategic goal:

```text
goalId = policy.resilience
type   = RESILIENCE
demandFloors = item-sorted automatic stock-demand contribution
```

`WorldSimulation.findFactionResilienceDemandFloors(...)` reads the contribution. `updateFactionResilienceDemandFloors(...)` replaces only this contribution while preserving:

- base faction stock policy;
- production preferences;
- military goals;
- expansion goals;
- doctrine, relations, claims and other strategic state.

An empty resilience list removes the automatic goal. Content references are validated before mutation.

## Symmetric bounded adjustment

`FactionStockResilienceReviewProfile` defines:

- an absolute deadband in units;
- a maximum upward overlay step per review;
- a maximum downward overlay step per review.

The conservative default is:

```text
deadband = 2 units
max increase = 10 units / review
max decrease = 5 units / review
```

A nonzero recommendation inside the deadband is held stable. When Stage-17F.5 diagnostics no longer contain an item at all, its target is interpreted as exactly zero; the overlay is then reduced by bounded steps until it is fully removed. Exact zero is allowed to cross the deadband so a stale automatic demand tail cannot survive indefinitely.

Base stock policy is never modified by this automatic reviewer.

## Reversible physical market demand

PR #128 added persistent market-demand provenance:

```text
configuredTargetStock = station-configured baseline
targetStock           = current effective target
```

`FactionStrategicPolicyEngine` now recomputes physical demand on explicit apply as:

```text
effective target
= max(
    configured station baseline,
    base faction stock policy,
    automatic resilience overlay,
    other active strategic goals
  )
```

Therefore reducing or removing the resilience overlay can physically lower market demand without erasing the station baseline, an intentional base reserve, or another military/expansion demand source.

## Persistence

The resilience overlay uses the existing persistent `FactionStrategicGoalState` collection. `RESILIENCE` is a new goal type and survives ordinary `WorldStateCodec` save/load without a separate world-state source of truth.

Market baseline/effective provenance is separately persistent through local `GameState` schema v3 introduced by PR #128. Older v1/v2 saves migrate conservatively: their old effective target becomes the configured baseline, so migration cannot silently reduce historical demand.

## Acceptance

`FactionStockResiliencePolicyReviewerTest` verifies:

- bounded upward adjustment;
- bounded downward adjustment;
- nonzero deadband stability;
- disappeared risk releasing an overlay fully to zero across bounded reviews.

`Stage17F6ResilienceOverlayAcceptanceTest` verifies:

- the automatic overlay is independent from base stock policy;
- one canonical `policy.resilience` goal is installed;
- invalid item references are rejected before mutation;
- military/other goals survive overlay replacement and removal;
- base policy and overlay survive `WorldStateCodec` save/load independently.

`Stage17F6StockResilienceCoordinatorAcceptanceTest` verifies a live physical cycle:

```text
supplier/dependency shock
→ first due common review
→ resilience overlay increases by one bounded step
→ same-window retry cannot apply a second step
→ explicit strategic-policy apply raises physical target
→ risk is physically removed
→ later common review lowers only the automatic overlay
→ explicit apply falls back to the independent base stock floor
→ later review removes the overlay entirely
→ base stock floor remains active
```

Around review authoring, inventory, entity wallets, treasury and physical market targets remain unchanged. Physical target changes occur only at the explicit ordinary strategic-policy apply boundary.

## Remaining Stage 17F.6 work

After this slice:

1. review production-policy switching and add dwell/deadband semantics if automatic recipe selection can oscillate;
2. run a long-horizon aggregate anti-oscillation acceptance covering repeated fiscal and resilience shocks/recoveries across save/load continuation;
3. verify deterministic policy decisions for identical state and explicit autonomous-faction sets;
4. update the canonical roadmap / completion record only after the aggregate gate is green;
5. then proceed to Stage 17F.7 player/AI parity.
