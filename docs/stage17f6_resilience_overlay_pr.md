# Stage 17F.6 resilience overlay PR scope

This branch implements the reversible automatic resilience-demand overlay on top of PR #128 market-demand provenance.

The implementation keeps base stock policy independent, uses the common policy-review cadence, supports bounded increase/decrease, and releases redundant automatic demand when protected non-resilience demand already covers the resilience recommendation.

Stage 17F.6 remains in progress after this slice; production-policy switching and aggregate long-horizon anti-oscillation remain follow-up work.
