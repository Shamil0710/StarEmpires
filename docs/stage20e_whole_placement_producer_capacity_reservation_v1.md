# Stage 20E — Whole-placement producer-capacity reservation v1

Status: **integration foundation; does not yet replace whole-seed economic acceptance**.

## Purpose

PR #276 established deterministic finite freight allocation for one ordinary faction start. That solves intra-start freighter double-counting, but an independent plan for `faction.alpha` and an independent plan for `faction.beta` may still both claim the same finite producer throughput.

`Stage20WholePlacementProducerCapacityReservation` closes that accounting boundary for an already accepted `Stage20FactionStartPlacementGenerator.PlacementResult` and an already accepted `Stage20FreightPortfolioAllocator.AllocationReport` for every placed faction.

For each essential commodity the reservation layer constructs one deterministic maximum-flow network:

- source → producer capacity is capped by the exact `Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey` capacity;
- producer → faction-start service capacity is capped by the selected local or remote single-start portfolio arc;
- faction-start → sink capacity is the exact bootstrap service requirement.

The same producer edge is therefore shared by every faction start. A producer cannot be counted once per faction.

## Exact-demand reservation

The finite freight allocator exposes a route capacity after assigning an integer number of ships. The last required ship may expose more throughput than the remaining demand.

The whole-placement reservation does **not** reserve that unused surplus. Maximum flow reserves only the kg/s actually required by the selected whole placement. This avoids turning integer ship granularity into artificial producer consumption.

## Local supply is not free

Local service uses no inter-system freighter, but it still consumes physical producer throughput. A local faction and a remote faction importing from that same system therefore compete through the same finite source edge.

## Determinism and physical validation

The input is canonicalized by stable `faction.*` ID, commodity ID and `SupplyKey`. Remote route evidence is revalidated against the authoritative topology and the exact supplier-route time boundary. Reordering input maps cannot change the reservation result.

## Result semantics

`ACCEPTED` means the supplied selected single-start portfolios can coexist without exceeding any shared producer capacity.

`SELECTED_PORTFOLIO_CONFLICT` means at least one commodity cannot satisfy every placed start using **those selected portfolios** while respecting shared producer ceilings.

This is intentionally **not** equivalent to `REJECTED_SEED`. A different globally coordinated supplier/freighter mix may still exist. A later Stage-20E global portfolio planner must search that alternative space before producer conflict can become seed rejection.

## Non-goals / remaining authority

This slice does not:

- search alternative supplier portfolios after a selected-plan conflict;
- materialize or grant the 13-freighter per-start capacity requirement derived by PR #275;
- assign ship, extraction-site, station or producer ownership;
- create initial stock or buffer inventory;
- establish whole-route monetary delivered cost;
- mutate resource occurrence, extraction, facilities, topology, FTL, demand or the frozen v1 corpus baseline;
- replace the historical final economic acceptance path.

The next integration step should measure this reservation contract on the representative v2-candidate placement corpus and, where selected portfolios conflict, introduce bounded globally coordinated portfolio search rather than rejecting or repairing the physical seed.