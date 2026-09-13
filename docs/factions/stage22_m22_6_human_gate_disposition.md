# Stage 22 M22.6 — human gate disposition

Status: milestone-specific owner disposition for the current M22.6 core-pair freeze.

This document does **not** change the canonical Gate-E thresholds in
`faction_balance_validation_framework.md` and does not mark deferred evidence as passed.

## Current M22.6 release disposition

The project owner explicitly deferred all three human-only review lanes that are not yet meaningful
against the production game presentation. For M22.6 this means:

- **B19 — grayscale ship blind review:** `DEFERRED_BY_OWNER`. Preserve the existing >=90% faction
  distinction and >=80% role-family thresholds and review protocol for later visual acceptance, but
  do not block the M22.6 simulation/content freeze on this lane. Tracked in issue #361.
- **B20 — shared character-style blind review:** `DEFERRED_BY_OWNER`. Preserve the existing >=90%
  shared-style threshold and review protocol for later visual acceptance, but do not block the M22.6
  simulation/content freeze on this lane. Tracked in issue #361.
- **B18 — player causal explanation:** `DEFERRED_BY_OWNER`. Preserve the existing >=80% causal-
  comprehension threshold and review intent, but do not block M22.6 while the review is presented
  through a purpose-built packet rather than the complete production UI. Tracked in issue #370.

Deferred means `DEFERRED_BY_OWNER`, never `PASS`. Existing human-response packets remain diagnostic
material and cannot be relabelled as successful acceptance.

## Why B18 is deferred until the production UI exists

The intended B18 question is not whether a reviewer can solve a text multiple-choice exercise. It is
whether a player can understand the simulation's causal chain from information exposed by the ordinary
game client: what happened, the immediate cause, the visible evidence for that diagnosis, and the
player action or dependency that would resolve or mitigate it where applicable.

At M22.6 the frozen B18 tooling presents those facts through a dedicated review packet. That remains
useful as a deterministic test harness, but it is not a strong acceptance test of production UI causal
readability. The owner therefore defers the human gate until the production UI/UX is sufficiently
complete to expose those states in-game.

Issue #370 preserves B18 as a later blocking human/RC acceptance requirement. The eventual in-client
review must still cover the same causal families: missing physical input, lost freight, broken
datalink, absent ammunition stock, unrepaired damage, paid replacement burden, lost treaty access,
and unfinished retool cost.

An assistant, classifier or language model may not supply or semantically infer the reviewer's answer
when the deferred gate is eventually activated.

## Reproducible B18 tooling retained for later acceptance

The repository keeps `tools/stage22/b18_review.py` as the deterministic baseline for packet identity,
scenario catalog and scoring. Its canonical threshold remains `correct / total >= 0.80`.

The tooling is retained rather than removed so that the later production-UI human review can reuse the
same causal catalog and fail-closed identity/scoring rules where appropriate. A future UI-integrated
review may extend the evidence format, but it must not silently weaken the threshold or relabel a
deferred review as passed.

## Exact-head verification after disposition change

The owner disposition changes the acceptance policy but does not change simulation/content behavior.
The disposition commit intentionally carries the `[m22-rc]` marker so ordinary Java-17 CI and all seven
canonical Stage-22 paired RC lanes rerun on the new exact HEAD before merge.

Historical green runs remain useful context but are not substituted for exact-head verification after
this policy change.

## M22.6 close condition

M22.6 may advance to final merge when:

1. the exact final candidate passes ordinary Java-17 `clean verify`;
2. the exact final candidate passes the Stage-22 paired RC workflow and required paired lanes;
3. B18 is explicitly recorded as `DEFERRED_BY_OWNER` and tracked in issue #370 for production-UI
   human/RC acceptance with its >=80% threshold preserved;
4. B19 and B20 remain explicitly recorded as `DEFERRED_BY_OWNER`, with their canonical thresholds
   unchanged for later visual acceptance in issue #361;
5. no new unresolved blocker exists on the final candidate.

This disposition permits M22.6 to close without fabricating human evidence. It does not waive the
future UI/visual human gates and does not permit any deferred lane to be reported as `PASS`.
