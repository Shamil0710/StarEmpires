# Stage 22 — M22.7 closure record / superseded final completion

Status: **STAGE 22 REOPENED — M22.0–M22.7 accepted; M22.8 REQUIRED / PLANNED**  
Originally recorded: 2026-09-16  
Corrected: 2026-09-16

The original version of this record declared Stage 22 / v0.6 Content & Balance Alpha closed after M22.0–M22.7 acceptance. That final-stage conclusion was premature: the required carrier / embarked-small-craft gameplay slice was omitted from the authoritative sequence.

The M22.7 implementation and its evidence remain accepted. What is superseded is only the claim that M22.7 was the final Stage-22 gate.

Canonical correction and M22.8 contract: `docs/m22_8_carrier_small_craft_operations.md`.

## Accepted M22.7 evidence

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

The first-hour acceptance fix does **not** accelerate calibrated inter-system routes. Default-seed bootstrap one-way routes remain approximately 992,669–993,259 simulation seconds (~11.5 days). The shared freight runtime preserves an already-authoritative delivery deadline on dispatch, and a new integrated campaign gives one deterministic oldest essential bootstrap obligation a campaign-epoch deadline. The ordinary autopilot still has to load real inventory into the real fleet/order and physical route. Save restore never reapplies that opening normalization.

## Why Stage 22 is not complete yet

M22.7 proved the integrated campaign handoff, but it does not prove a production carrier/small-craft loop. Stage 22 therefore remains open until M22.8 closes the missing physical carrier operations chain:

```text
manufacture craft
→ deliver / assign
→ hangar / service
→ launch
→ independent physical mission
→ finite ammunition / propellant / damage / loss
→ recover or remain destroyed
→ turnaround / repair / replacement
→ persistent strategic readiness
```

The full scope, authority boundaries and exit criteria are defined in `docs/m22_8_carrier_small_craft_operations.md`.

## Deferred human review

Issue #361 (`Post-release human sprite approval debt`) deliberately remains **OPEN**. Human aesthetic review is deferred and is not represented as Stage-22 PASS evidence. Automated legality, manifest/fingerprint, engine-binding and runtime-fallback correctness remain mandatory and were not deferred.

The pre-existing human explanation-review disposition recorded by M22.6 remains separate from machine acceptance and is not retroactively promoted by this correction.

## Stage 23 boundary

Stage 23 is **BLOCKED / NOT STARTED** until M22.8 is accepted on an exact green implementation head, merged, and a new final Stage-22 completion record is created from that evidence.

The existing M22.7 handoff remains the runtime baseline for M22.8. The former statement that Stage 23 was unblocked immediately after M22.7 is superseded by the M22.8 correction contract and the authoritative development roadmap.

No Stage-23 branch or document may be treated as an active implementation baseline while M22.8 remains incomplete.