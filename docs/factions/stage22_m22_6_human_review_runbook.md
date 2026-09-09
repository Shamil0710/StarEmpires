# Stage 22 M22.6 — human review runbook for B18–B20

> Status: **PROTOCOL ONLY — NO HUMAN GATE RESULT RECORDED**  
> Scope: execution and evidence format for the mandatory M22.6 B18, B19 and B20 human lanes.  
> Authority: `faction_balance_validation_framework.md` Gate E and the canonical B18–B20 scenario definitions.  
> Frozen manifest: `stage22.core_pair_freeze_manifest.v3`  
> Frozen fingerprint: `6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4`

This document does **not** lower, redefine or satisfy the canonical thresholds. It exists so a later
human review is reproducible, blinded where required, tied to an exact release-candidate checkout and
archived as raw evidence rather than summarized from memory.

Automated tests may prepare samples, validate asset bindings or calculate arithmetic from already
recorded answers. They may **not** invent, infer, simulate or replace a human answer.

---

## 1. Required release-candidate identity

Every review packet and every response row records all of:

```text
buildSha
freezeManifestVersion
freezeFingerprint
scenarioSuiteVersion
reviewPacketVersion
reviewerAnonymousId
reviewStartedAtUtc
reviewCompletedAtUtc
```

For the current M22.6 frozen machine candidate, `freezeManifestVersion` must be
`stage22.core_pair_freeze_manifest.v3` and `freezeFingerprint` must be
`6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4`. `buildSha` must be the exact
candidate commit that passed the final normal CI and 100-pair RC workflow; it is intentionally not
hard-coded in this protocol before those workflows finish.

The review is invalid for release sign-off when the tested build SHA or freeze fingerprint differs
from the final release-candidate SHA/fingerprint. Discovery fingerprints from an earlier branch head
are not accepted as a substitute.

Reviewer identity may be pseudonymous. It only needs to remain stable inside the archived session so
answers cannot be silently duplicated or reassigned.

---

## 2. B18 — player causal explanation

Canonical threshold: **at least 80%** of recorded human event tasks identify the correct primary causal
dependency after the event.

### 2.1. Packet construction

Before showing a task to a reviewer, freeze an answer-key row containing:

```text
taskId
scenarioId
seed
permutation
startCheckpointRef
endCheckpointRef
primaryDependencyId
acceptedEquivalentAnswers
visibleEvidenceRefs
```

`primaryDependencyId` must name a real causal authority/state transition, for example a physical supply
loss, depleted magazine, unavailable relay, route denial, missing production input or finite retool
obligation. A composite faction power score is not a valid answer key.

The reviewer receives the normal player-visible UI/event presentation and may inspect the same
player-facing drill-down that the tested build exposes. The hidden answer-key dependency is not shown.

### 2.2. Recorded human response

```text
reviewerAnonymousId
taskId
freeTextPrimaryCause
selectedCauseId        # optional when the UI uses choices
freeTextCounteraction  # diagnostic; not part of the 80% threshold unless separately declared
answeredAtUtc
```

Scoring occurs only after the response is frozen. A row is correct when the answer matches the
predeclared primary dependency or an explicitly predeclared equivalent answer. Post-hoc expansion of
the answer key requires a documented review note and rescoring of all affected rows.

### 2.3. Result

Archive raw numerator and denominator:

```text
correctPrimaryCauseAnswers
totalScoredAnswers
accuracy = correctPrimaryCauseAnswers / totalScoredAnswers
```

Gate B18 passes only when `accuracy >= 0.80` and every scored response is tied to the same accepted RC
identity. Excluded responses remain in the archive with an explicit exclusion reason.

---

## 3. B19 — grayscale ship blind review

Canonical thresholds:

- **at least 90%** correct core-faction distinction by grayscale ship silhouette;
- **at least 80%** correct role-family reading inside the core faction.

The existing automated alpha-mask test is only preflight. It cannot satisfy either percentage.

### 3.1. Sample preparation

Use the exact primary production visual bound to every required Stage-22 role for both core factions.
For each sample:

1. remove faction name, ship name, role label, heraldry text and UI labels;
2. remove color information;
3. retain the actual silhouette/shape used by the RC asset, with no hand-redrawn reviewer proxy;
4. keep one deterministic `sampleId` mapped privately to the expected faction and role;
5. present samples in an order that does not alternate factions or leak the expected answer.

The answer-key manifest records:

```text
sampleId
assetRef
assetFingerprintOrDigest
expectedFactionId
expectedRoleId
```

### 3.2. Recorded human response

```text
reviewerAnonymousId
sampleId
reportedFactionId
reportedRoleId
confidenceOptional
answeredAtUtc
```

Do not reveal whether a previous answer was correct until the reviewer has completed the packet.

### 3.3. Result

Calculate from raw judgments, never from the number of distinct files:

```text
factionCorrect
factionJudgments
factionAccuracy = factionCorrect / factionJudgments

roleCorrect
roleJudgments
roleAccuracy = roleCorrect / roleJudgments
```

Gate B19 passes only when `factionAccuracy >= 0.90` and `roleAccuracy >= 0.80` on the accepted RC
identity. Keep per-role confusion counts in the archived report so a high aggregate score cannot hide
one unreadable role family.

---

## 4. B20 — shared character style blind review

Canonical threshold: **at least 90%** of reviewed character samples pass the shared-style checklist.

The canonical shared authority is `docs/characters/character_master_prompt.md`. Empire and Industrial
Union overlays remain separate faction visual authorities; passing B20 must not erase those overlays.

### 4.1. Sample preparation

The packet uses the exact reviewed character sample files intended for the RC visual manifest. Remove
captions that disclose faction or role. Keep the rendered character itself unchanged.

For each sample freeze:

```text
sampleId
assetRef
assetFingerprintOrDigest
factionId
roleId
masterPromptVersionOrRef
factionVisualProfileRef
```

The human checklist evaluates the shared style lock, including hand-painted 2D RPG illustration,
non-photorealistic/non-3D rendering, restrained ink-and-paint linework, muted opaque watercolor or
gouache-like treatment, limited shading and human-made imperfection. Faction/role readability is
recorded separately so a shared-style pass cannot conceal a missing faction overlay.

### 4.2. Recorded human response

```text
reviewerAnonymousId
sampleId
sharedStylePass       # true/false, human-entered
reportedFactionId     # diagnostic
reportedRoleId        # diagnostic
failureReasonOptional
answeredAtUtc
```

### 4.3. Result

```text
sharedStylePasses
sharedStyleJudgments
sharedStyleAccuracy = sharedStylePasses / sharedStyleJudgments
```

Gate B20 passes only when `sharedStyleAccuracy >= 0.90` on the accepted RC identity. Machine image
classification, prompt inspection, alpha-mask comparison or assistant self-review is not a human
judgment and is excluded from the denominator.

---

## 5. Evidence archive layout

Recommended retained layout:

```text
docs/evidence/stage22/m22_6/human-<rc-short-sha>/
  README.md
  packet_manifest.json
  b18_answer_key.json
  b18_responses.csv
  b19_answer_key.json
  b19_responses.csv
  b20_answer_key.json
  b20_responses.csv
  summary.json
```

`README.md` records reviewer-count information, execution environment, exclusions and limitations.
Raw responses are retained alongside the summary. Personally identifying information is neither
required nor desirable.

`summary.json` records the exact integer numerators/denominators and calculated ratios rather than only
`PASS`/`FAIL`.

---

## 6. Sign-off checklist

A human-gate result may be referenced by the M22.6 balance report only when all boxes are true:

- [ ] review packet uses the final candidate `buildSha`;
- [ ] packet uses `stage22.core_pair_freeze_manifest.v3`;
- [ ] packet uses frozen fingerprint `6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4`;
- [ ] B18 answers were collected before answer-key disclosure;
- [ ] B19 samples were genuinely grayscale/blinded and not label-leaking screenshots;
- [ ] B20 judgments were made on actual reviewed character samples;
- [ ] raw responses are archived;
- [ ] exclusions have explicit reasons;
- [ ] arithmetic is reproducible from raw rows;
- [ ] B18 accuracy is at least 80%;
- [ ] B19 faction accuracy is at least 90%;
- [ ] B19 role accuracy is at least 80%;
- [ ] B20 shared-style accuracy is at least 90%;
- [ ] no automated/assistant-generated judgment is counted as human evidence.

Until recorded responses satisfying this checklist exist, B18–B20 remain human blockers, M22.6
remains open and the Stage-22 freeze cannot be signed off or merged.
