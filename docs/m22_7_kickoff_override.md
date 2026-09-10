# M22.7 kickoff override — human sprite review deferred

> Decision date: 2026-09-11.
> Scope owner decision: the current human sprite approval gate is explicitly deferred until after release so M22.7 integration work can begin now.

## What this decision means

- Human sprite approval is **DEFERRED**, not passed.
- Existing automated asset/content legality, manifest/fingerprint, engine-binding and runtime fallback checks remain mandatory.
- Any already accepted M22.6 balance/content evidence remains authoritative; this override must not silently change frozen balance numbers.
- The deferred human visual review must remain visible as post-release debt and must not be reported as completed evidence.
- M22.7 may begin from current `main` under this explicit owner override, but causal defects discovered during integration must still be fixed in the shared simulation authority and regression-tested.

## Immediate M22.7 priority

1. Unified production campaign composition.
2. Simulation-time scheduling and deterministic logistics dispatch.
3. Diagnose and fix transports/ships that appear stuck at stations or have no observable movement.
4. Preserve the same fleet/logistics authority for player and AI.
5. Continue asset/content resolver integration, while deferring only the human sprite acceptance checkpoint.

Tracking issue: #359.
