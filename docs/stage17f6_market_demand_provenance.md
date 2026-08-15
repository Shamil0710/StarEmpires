# Stage 17F.6 — reversible market-demand provenance

## Status

Prerequisite implementation slice for Stage 17F.6 policy feedback / anti-oscillation.

This slice does **not** complete Stage 17F.6. It fixes the physical market-demand boundary required before automatic resilience buffers can safely decrease after a shock.

## Problem

Before this slice, `MarketComponent.targetStock` was both:

- the station's configured/base demand; and
- the mutable effective target after faction strategic policy.

`FactionStrategicPolicyEngine` only increased that value with a maximum. Once a policy raised demand, removing or lowering the policy could not safely lower the physical market target again because the original station baseline had been lost.

That made a symmetric Stage-17F.6 recovery controller impossible: lowering only persistent policy state would leave stale physical demand in the market.

## Market provenance contract

`MarketComponent` now stores two distinct arrays:

```text
configuredTargetStock = persistent station-configured baseline
targetStock           = current effective market target
```

`configureTradableItem(...)` initializes both to the configured station target. Strategic policy may change only the effective value. Disabling item trading clears both.

The effective target is recomputed by `FactionStrategicPolicyEngine` from current authoritative sources:

```text
effective target
= max(
    configured station baseline,
    current base faction stock policy,
    current strategic-goal demand floors
  )
```

The policy contribution remains capped by the station's actual inventory capacity as before. The configured baseline is not rewritten by policy application.

## Reversible apply

The strategic policy engine no longer treats the previous effective target as a baseline.

On every explicit apply it recomputes each tradable item's desired target from the configured baseline and the **current** aggregate strategic demand.

Consequences:

- adding/increasing a policy can raise effective demand;
- removing/decreasing that policy can lower effective demand;
- lowering one source cannot erase a higher remaining source;
- removing all strategic demand returns the market to its own configured baseline;
- authoring policy still does not move cargo, money or production output;
- physical market consequences still occur only through ordinary prices, TradeAI/logistics and production.

## Persistence

Local `GameState` schema advances from v2 to **v3**.

Schema v3 stores `configuredTargetStock` next to the existing effective `targetStock` in the market component payload.

Compatibility rules:

- **v3:** configured baseline and effective target are stored independently and round-trip exactly;
- **v2:** stable archetype IDs remain supported; because v2 had no target provenance, its historical effective target becomes the configured baseline;
- **v1:** five historical item slots are expanded as before, and the legacy effective target becomes the configured baseline.

The migration rule is deliberately conservative:

> An old save must never load with a lower market target merely because provenance did not exist when it was written.

The same rule applies to the public value-layer `GameStateMigration.toCurrent(...)`: a schema-v2 DTO cannot invent a different configured baseline even though the current Java record now contains the field.

## Acceptance

The slice verifies:

- configured baseline lifecycle in `MarketComponent`;
- mapper round-trip where configured baseline and effective target intentionally differ;
- current v3 binary round-trip with distinct baseline/effective values;
- binary v2 migration preserving archetype while deriving baseline from old effective target;
- value-layer v2 migration ignoring synthetic provenance that did not historically exist;
- binary v1 migration/padding with conservative baseline derivation;
- explicit strategic-policy removal restoring each station's own configured baseline;
- independent military/expansion demand goals remaining active when another stock policy is removed;
- repeated apply becoming a no-op once the recomputed effective target already matches current demand.

## What this slice deliberately does not do

It does not yet separate the **automatic resilience contribution** from the operator/player/AI-authored base `FactionStockProductionPolicyState.stockPolicies()`.

That is the next prerequisite. A dedicated resilience overlay must be persistent and independently removable so the anti-oscillation reviewer can decrease only its own contribution without deleting an intentional base reserve.

The intended next-stage effective demand therefore becomes conceptually:

```text
effective target
= max(
    configured station baseline,
    base faction stock policy,
    resilience overlay,
    other strategic goals
  )
```

After that overlay exists, Stage 17F.6 can enable bounded upward **and downward** resilience adjustments inside the already implemented common review cadence, then run the long-horizon aggregate anti-oscillation gate.
