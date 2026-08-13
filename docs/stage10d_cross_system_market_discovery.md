# Stage 10D — Cross-system Market Discovery

Status: COMPLETE candidate in PR #23.

## Purpose

Stage 10C deliberately accepted a bounded list of inter-system opportunities. Stage 10D provides the missing world-level discovery layer without turning every trade fleet into a full-galaxy market scanner.

The architecture keeps the existing local `MarketDirectory` as the market snapshot primitive. There is no second authoritative market representation.

## GalacticMarketIndex

`GalacticMarketIndex` owns one `MarketDirectory` per `StarSystem` and a deterministic sector membership index derived from immutable `GalaxyTopology`.

A rebuild:

1. visits systems in topology order;
2. rebuilds each local immutable market snapshot;
3. observes each local directory revision;
4. advances one aggregate world-market revision if any system changed.

Repeated rebuilds against an unchanged world do not advance the aggregate revision. This gives discovery results a cheap stale-state check that reacts to stock, prices, wallets and diplomacy/access changes already tracked by `MarketDirectory`.

## Explicit search bounds

`GalacticMarketDiscoveryPolicy` makes search complexity a first-class policy:

- maximum jump hops;
- maximum reachable systems inspected;
- maximum consumers inspected per system/item;
- maximum opportunities passed to the pure scorer;
- optional planning-only risk basis points per jump.

The current implementation also caps local suppliers inspected per item before candidate pairing.

Discovery therefore cannot silently degrade into:

```text
all fleets × all suppliers × all consumers in the galaxy
```

## Regional / sector ordering

Reachable systems are found by deterministic topology traversal. Candidate systems are ordered by:

1. fewer topology hops;
2. same-sector preference;
3. stable `StarSystemId`.

The system budget is applied before market pairing. Sector membership is indexed once rather than rediscovered by every fleet.

Weighted `GalacticPathPlanner` remains authoritative for route timing. Hop count is only the search horizon; the final candidate carries the Stage-10B-compatible weighted path.

## Candidate construction

For every fleet-compatible item, discovery considers only:

- bounded local suppliers in the fleet's current system;
- bounded remote consumers in bounded reachable systems;
- markets allowed by the fleet's faction access snapshot;
- pairs with an optimistic positive price spread.

The shortlist is ranked deterministically and truncated before it reaches `TradeRoutePlanner.findBestGalacticRoute(...)`.

The existing Stage-10C scorer then remains responsible for authoritative planning quantities, affordability, liquidity, demand, cargo capacity, tariff/risk cost and profit-per-time selection.

## Local travel estimate

Stage 10B still has no physical jump-gate anchor coordinates inside local systems. Stage 10D therefore estimates only the known fleet-to-supplier local leg and does not invent gate geometry.

Intermediate transit remains purely Stage-10B strategic jump time. A later navigation layer may add explicit gate anchors without replacing the discovery API.

## Stale discovery

Every discovery result carries the aggregate market-index revision. `Result.isCurrent(...)` refreshes the index and rejects a result if any underlying market/access snapshot changed.

Execution still revalidates all authoritative trade conditions through `TradeController`; a current planning revision is an optimization/correctness signal, not permission to bypass live state.

## Acceptance evidence

`Stage10InterSystemLogisticsAcceptanceTest` verifies:

- aggregate revision is stable across unchanged rebuilds;
- direct market mutation invalidates a previous discovery result;
- Core-sector membership is indexed deterministically;
- a one-hop horizon cannot discover a two-hop Frontier opportunity;
- raising the horizon to two hops discovers the same opportunity with a two-jump authoritative path.

## Definition of Done

Stage 10D is complete when cross-system opportunity discovery is bounded, deterministic, region-aware, configurable by horizon and stale-state aware, while the economic route scorer remains the Stage-5/10C `TradeRoutePlanner` rather than a parallel stack.
