# Stage 23A — RC scope lock and release governance v1

Status: **FROZEN FOR v0.7 RC TRAIN**

This document is the canonical human-readable governance contract for Stage 23 after the accepted
Stage-22 physical/content baseline. It does not add a new simulation authority.

## 1. Product scope authority

Machine-readable feature/scope registry:

- `docs/release/rc_feature_manifest_v1.tsv`.

Allowed scope classes are:

- `MUST_SHIP` — required for Stage 23 / v0.7 RC acceptance;
- `MAY_SHIP` — may land only after MUST_SHIP work is safe and does not delay an RC gate;
- `POST_RC` — explicitly outside the v0.7 blocker set.

A new request after 23A defaults to `POST_RC` until an explicit scope-change decision promotes it.
A blocker fix is never used as a vehicle for unrelated feature work.

## 2. Severity model

| Severity | Meaning | RC rule |
| --- | --- | --- |
| BLOCKER | build/install/start/save/load impossible, authoritative data loss/corruption, security/privacy breach, or exact RC artifact cannot be produced | must close before any RC candidate |
| CRITICAL | a MUST_SHIP loop is unusable, deterministic/authority invariants are violated, or a practical exploit invalidates economy/combat/persistence | must close before 23J |
| MAJOR | materially impaired behavior with a bounded workaround and no authority/data-loss violation | requires explicit owner + accepted disposition before 23J |
| MINOR | localized defect that does not prevent the intended loop | may remain only as a documented known issue |
| COSMETIC | presentation-only imperfection with no misleading authoritative state | may remain only when explicitly accepted |

Severity and scope are independent: a POST_RC feature request is not a defect merely because it is
valuable, and a MUST_SHIP defect is not automatically BLOCKER if a safe bounded workaround exists.

## 3. Release blocker ownership and evidence

Every issue that is a release gate must record:

- one roadmap acceptance owner (`23B` … `23J`);
- reproduction or deterministic trigger;
- required closure evidence;
- affected build/content/save/generator identity;
- exact PR/SHA/test/manual charter that closes it.

Current issue dispositions are machine-readable in `docs/release/rc_known_issues_v1.tsv`.

## 4. Change control after freeze

| Change class | Default after 23A | Required evidence |
| --- | --- | --- |
| simulation mechanics / authority | POST_RC unless required to close BLOCKER/CRITICAL | architecture impact, affected deterministic tests, save/AI/player parity review |
| numeric balance / authored content | allowed only inside the owning 23x slice | affected B-scenario/content regression + new semantic fingerprint |
| content ID remove/rename | blocked without migration/alias decision | reverse-reference audit + supported-save evidence |
| save schema / migration | blocked without explicit version bump | old/new fixtures, non-destructive failure, deterministic continuation |
| generator behavior/profile | blocked without generator-profile bump | representative seed corpus + compatibility/new-world-only decision |
| UI/VFX/audio | allowed in owning slice when presentation-only | state-provenance/readability/accessibility regression |
| docs/governance | allowed when it reflects accepted reality | governance contract test + normal exact-head gate |

No PR may combine an unrelated `MAY_SHIP`/`POST_RC` feature with a release blocker fix.

## 5. Known issue policy

A known issue may ship only when:

1. it is not BLOCKER or CRITICAL;
2. the player impact is described;
3. a workaround or explicit lack of workaround is recorded;
4. it does not hide authority/data-loss/security risk;
5. 23I/23J records an accepted disposition.

Human visual approval issue #361 remains explicitly POST_RC. It is not converted into PASS evidence.

## 6. Provisional content gate

The authoritative ID-level inventory is `Stage22ContentInventory.buildDefault()`. Stage 23A freezes
the following rule:

- every definition with `SourceMaturity.PROVISIONAL` must already have an explicit
  `REAUTHOR`, `REPLACE` or `RETIRE` disposition;
- `PRESERVE` or `PROMOTE` is forbidden for that provisional set at RC entry;
- the Stage-22 production core-pair engineering/ammunition catalogs must have no ID intersection with
  the provisional inventory.

This keeps Stage-17.5/19 acceptance fixtures available for regression without shipping them as
player-facing production content.

## 7. Version identity

Canonical version policy: `docs/release/rc_versioning_v1.md`.

Every candidate is identified by the tuple:

`applicationVersion + sourceSha + contentVersion + contentFingerprint + saveVersions + generatorProfile`.

A source SHA alone is insufficient release identity.

## 8. Legacy PR disposition at RC freeze

These old branches are not accepted implementation baselines:

- #376, #356, #284, #242 and #210 — **SUPERSEDED / DO NOT MERGE**;
- #377 audio production bible — **REFERENCE INPUT FOR 23E**, stale branch; re-author/cherry-pick only as needed;
- #243 asset/hull manifest — **REFERENCE INPUT FOR 23E/23F**, stale draft; not authoritative by itself.

## 9. Issue intake

Repository issue templates separate:

- `.github/ISSUE_TEMPLATE/rc_blocker.md` for defects/release gates;
- `.github/ISSUE_TEMPLATE/feature_request.md` for MAY_SHIP / POST_RC requests.

This distinction is part of the 23A exit gate.
