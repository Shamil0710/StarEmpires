# Stage 17F.6 — reversible resilience demand overlay

## Status

Implementation slice of Stage 17F.6 policy feedback / anti-oscillation.

This slice separates automatic resilience demand from operator/player/AI-authored base stock policy and enables symmetric bounded recovery through the reversible market-demand provenance introduced by PR #128.

Stage 17F.6 remains **IN PROGRESS** after this slice.

## Contract

Automatic resilience demand is stored only in the canonical persistent `policy.resilience` strategic goal of type `RESILIENCE`.

The reviewer:

- derives the effective target from the existing Stage-17F.5 `FactionResiliencePlanner`;
- subtracts no physical inventory and moves no money;
- treats base stock policy and non-resilience strategic goals as protected demand;
- creates an automatic overlay only when the resilience recommendation exceeds that protected demand;
- moves the overlay up and down only by bounded steps inside the shared Stage-17F.6 review cadence;
- removes a redundant overlay completely when base/non-resilience demand already covers the recommendation;
- never executes physical market demand changes directly.

Physical demand changes occur only through the ordinary explicit `applyFactionStrategicPolicy(...)` boundary, which recomputes effective market targets from the configured station baseline plus all active strategic demand sources.

## Acceptance

The slice is covered by:

- `FactionStockResiliencePolicyReviewerTest` for bounded increase/decrease, deadband stability, disappeared-risk release and release of overlay made redundant by protected base demand;
- `Stage17F6ResilienceOverlayAcceptanceTest` for canonical overlay persistence, validation and independence from base/other strategic goals;
- `Stage17F6StockResilienceCoordinatorAcceptanceTest` for the live shock → bounded overlay increase → explicit apply → recovery → bounded overlay decrease/removal cycle under the common review claim.

## Next Stage 17F.6 work

1. inspect automatic production/recipe switching for oscillation risk and add dwell/deadband semantics where required;
2. run a long-horizon aggregate fiscal + resilience shock/recovery acceptance with save/load continuation;
3. verify deterministic decisions for identical world states and explicit autonomous-faction sets;
4. close Stage 17F.6 only after the aggregate gate and roadmap/completion record are green.
