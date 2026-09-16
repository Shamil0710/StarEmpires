# Stage 22 — final completion record

Status: **COMPLETE**  
Recorded: 2026-09-16

Stage 22 / v0.6 Content & Balance Alpha is closed after M22.0–M22.7 acceptance.

## Final M22.7 evidence

- implementation PR: #360 — `M22.7 kickoff: integrated campaign handoff`;
- accepted implementation head: `327870db987408781d323457a07d6a870e82a872`;
- exact-head CI: workflow `CI` run #6703 / run id `35087562701` — **SUCCESS**;
- Stage 19J Long Soak on that PR head: intentionally **SKIPPED** by the PR workflow contract, not counted as green M22.7 evidence;
- final M22.7 acceptance umbrella: #368 — **CLOSED / completed**;
- merged PR #360 result on `main`: `22015454773a1e430c50a8743149addfe8947b6f`;
- M22.7A lifecycle #363 — **CLOSED / completed**;
- M22.7B deterministic scheduling #367 — **CLOSED / completed**;
- M22.7B stationary movement defect #362 — **CLOSED / completed**;
- M22.7C asset/content resolver #364 — **CLOSED / completed**;
- M22.7D/E persistence + first-hour causal bridge #365 — **CLOSED / completed**;
- M22.7F launcher/documentation truth #366 — **CLOSED / completed**.

The accepted runtime keeps one `GeneratedCampaignCoordinator` composition root, one authoritative simulation timeline, simulation-time autonomous scheduling, Stage-21I native composed persistence, exact physical freight identities/progress, production asset resolution, the first-hour causal order/fleet/cargo continuity proof, production-client smoke and the 1x/8x throughput baseline method.

The first-hour acceptance fix does **not** accelerate calibrated inter-system routes. Default-seed bootstrap one-way routes remain approximately 992,669–993,259 simulation seconds (~11.5 days). The shared freight runtime now preserves an already-authoritative delivery deadline on dispatch, and a new integrated campaign gives one deterministic oldest essential bootstrap obligation a campaign-epoch deadline. The ordinary autopilot still has to load real inventory into the real fleet/order and physical route. Save restore never reapplies that opening normalization.

## Deferred human review

Issue #361 (`Post-release human sprite approval debt`) deliberately remains **OPEN**. Human aesthetic review is deferred and is not represented as Stage-22 PASS evidence. Automated legality, manifest/fingerprint, engine-binding and runtime-fallback correctness remain mandatory and were not deferred.

The pre-existing human explanation-review disposition recorded by M22.6 remains separate from machine acceptance and is not retroactively promoted by this record.

## Stage 23 boundary

Stage 23 is now **unblocked for a separately approved kickoff**, but this completion record does not start Stage 23 and does not add any Stage-23 implementation. New work must begin from the accepted Stage-22 runtime and preserve the entry constraints in `docs/m22_7_integrated_campaign_handoff.md`.

The authoritative roadmap must mark Stage 22 COMPLETE and Stage 23 PLANNED / NOT STARTED once this closure-record PR is merged.