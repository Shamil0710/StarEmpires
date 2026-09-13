# Stage 22 M22.6 — human gate disposition

Status: milestone-specific owner disposition for the current M22.6 core-pair freeze.

This document does **not** change the canonical Gate-E thresholds in
`faction_balance_validation_framework.md` and does not mark deferred evidence as passed.

## Current M22.6 release disposition

The project owner explicitly deferred the visual human-approval debt to post-release tracking in
issue #361. For M22.6 this means:

- **B19 — grayscale ship blind review:** `DEFERRED_BY_OWNER`. Preserve the existing >=90% faction
  distinction and >=80% role-family thresholds and review protocol for later visual acceptance, but
  do not block the M22.6 simulation/content freeze on this lane.
- **B20 — shared character-style blind review:** `DEFERRED_BY_OWNER`. Preserve the existing >=90%
  shared-style threshold and review protocol for later visual acceptance, but do not block the M22.6
  simulation/content freeze on this lane.
- **B18 — player causal explanation:** **not deferred**. It remains the active human blocker for M22.6
  and must pass the existing >=80% threshold on the accepted release-candidate identity.

Deferred means `DEFERRED_BY_OWNER`, never `PASS`. Existing B19/B20 response packets remain diagnostic
material and cannot be relabelled as successful acceptance.

## Why B18 remains blocking

M22.6 is a balance/freeze milestone, so machine evidence must be understandable through player-facing
state rather than only through telemetry. B18 therefore verifies that a human reviewer can identify
the actual primary dependency after an event: missing physical input, lost freight, broken datalink,
absent ammunition stock, unrepaired damage, paid replacement burden, lost treaty access, or unfinished
retool.

An assistant, classifier or language model may not supply or semantically infer the reviewer's answer.

## Reproducible B18 tooling

The repository provides `tools/stage22/b18_review.py` with two fail-closed commands.

Prepare a blind packet for the exact candidate:

```bash
python3 tools/stage22/b18_review.py prepare \
  --build-sha <40-char-candidate-sha> \
  --output-dir target/stage22-b18-review
```

The command emits:

- `packet_manifest.json` — exact candidate/freeze identity and frozen task-catalog digest;
- `b18_review_form.md` — eight blind player-facing causal tasks with A/B/C/D choices;
- `b18_answer_key.json` — hidden, predeclared causal key;
- `b18_responses.csv` — empty response template.

After a human reviewer completes all eight tasks, score the frozen rows:

```bash
python3 tools/stage22/b18_review.py score \
  --answer-key target/stage22-b18-review/b18_answer_key.json \
  --responses target/stage22-b18-review/b18_responses.csv \
  --expected-build-sha <40-char-candidate-sha> \
  --expected-freeze-fingerprint 6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4 \
  --output target/stage22-b18-review/summary.json
```

The scorer rejects wrong candidate/freeze identity, unknown or duplicate tasks and incomplete reviewer
packets. It does no fuzzy NLP scoring. A selected choice is correct only when it matches the frozen key;
free text is accepted only when it exactly normalizes to an equivalent frozen before review. `PASS`
requires `correct / total >= 0.80`.

## M22.6 close condition

M22.6 may advance to final merge only when:

1. the exact final candidate passes ordinary Java-17 `clean verify`;
2. the exact final candidate passes the Stage-22 paired RC workflow and required paired lanes;
3. B18 has genuine human responses tied to that candidate and scores at least 80%;
4. B19 and B20 remain explicitly recorded as `DEFERRED_BY_OWNER`, with their canonical thresholds
   unchanged for later visual acceptance;
5. no new unresolved blocker exists on the final candidate.

Until item 3 is satisfied, PR #355 stays open and M22.7 must not be treated as the accepted successor
of a completed M22.6 freeze, even if exploratory work exists on another branch.
