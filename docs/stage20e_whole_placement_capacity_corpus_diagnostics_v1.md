# Stage 20E — Whole-placement capacity corpus diagnostics v1

Status: **read-only evidence; does not change production acceptance**.

## Question

After the v2 bootstrap service-cadence correction, 15/16 fixed seeds already produce accepted faction-start placements, but the current production whole-seed economic gate still uses an older single-supplier/fleet interpretation.

Stage 20E now has two separate accounting foundations:

1. `Stage20FreightPortfolioAllocator` proves whether one placed start can satisfy all essential commodities with one finite per-start inter-system freighter pool.
2. `Stage20WholePlacementProducerCapacityReservation` proves whether already selected per-start portfolios can coexist without double-counting a physical producer's finite `SupplyKey` throughput.

This diagnostic composes those two foundations on the unchanged fixed seed corpus before either is promoted into whole-seed acceptance.

## Authority used

The production probe and faction-start placement remain the unchanged:

- `stage20e.representative-production-probe-profile.v2-candidate`;
- corrected `stage20e.bootstrap-requirements.v2`;
- fixed seeds 1..16.

The external post-placement freight-service budget comes from:

- `Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent()`;
- current result: 13 representative early civilian freighters per ordinary faction start.

The number 13 is not selected from corpus outcomes. It is derived from the accepted regional five-hop reference cycle and the total 75 kg/s essential demand authority.

## Physical parity

For the diagnostic 13-ship route evaluator:

- payload mass is copied unchanged from the production representative freight profile;
- fitted loaded and return FTL plans are unchanged;
- explicit generated jump topology is unchanged;
- Stage-20C local-layout travel times are unchanged;
- generated major-hub Stage-18 transfer rates are unchanged;
- source evidence ID and Stage-22 review flag are preserved.

Only the already-authorized integer service-capacity count passed to `Stage20PhysicalFreightRouteEvaluator` changes from the legacy representative count to the independently derived 13-ship requirement.

## Per-seed causal sequence

For each fixed seed:

1. run the unchanged v2-candidate production probe;
2. preserve its faction-start placement result;
3. for each accepted placement assignment, run `Stage20FreightPortfolioAllocator` with the derived 13-ship per-start budget;
4. if any start fails, classify the seed as `START_ALLOCATION_REJECTED` and do not invent a replacement placement or supplier;
5. if all starts have accepted finite portfolios, run `Stage20WholePlacementProducerCapacityReservation` across the complete placement;
6. classify selected-portfolio producer conflicts separately from accepted reservations.

## Interpretation boundary

`PRODUCER_RESERVATION_CONFLICT` does **not** prove the seed is economically impossible. The reservation layer checks the selected independently optimal portfolios; a different globally coordinated supplier/freighter mix may exist.

Likewise, a successful diagnostic does not grant 13 physical ships to a faction and does not establish ownership, starting inventory, monetary delivered cost or buffer stock.

No seed is retried, replaced or repaired because of the measured outcome. The historical v1 benchmark remains unchanged.

## Promotion rule

This evidence is intended to decide the next Stage-20E integration slice:

- if start-level 13-ship allocations still fail materially, inspect actual route/fleet physical assumptions before changing demand rates;
- if start allocations succeed but selected producer reservations conflict, introduce a globally coordinated supplier/fleet planner rather than treating the conflict as immediate seed rejection;
- if both layers are broadly feasible, integrate the finite portfolio/reservation semantics into a versioned current whole-seed acceptance path while preserving historical replay.

Stage 22 review remains required for provisional calibration/content assumptions.