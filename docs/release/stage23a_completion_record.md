# Stage 23A — Scope lock / release governance completion record

Status: **COMPLETE ON MERGE OF THE EXACT GREEN 23A HEAD**

Stage 23A freezes the v0.7 RC product surface without adding gameplay or a parallel authority.

## Delivered

- machine-readable RC feature manifest with `MUST_SHIP / MAY_SHIP / POST_RC`;
- acceptance owner + evidence mode for every MUST_SHIP loop;
- five-level severity model and release-gate rules;
- change-control for mechanics, content IDs, save schemas, generator profile and presentation;
- machine-readable current known-issue registry;
- explicit issue intake split between RC defects and feature requests;
- application/content/save/generator version identity policy;
- reproducible release-notes template;
- machine-enforced provisional-content gate using the Stage-22 ID-level inventory and the accepted
  Stage-22 production core-pair catalogs;
- stale PR disposition so pre-RC branches cannot masquerade as active implementation baselines.

## Current tracked release gates

- #370 — production-UI causal comprehension; owned by 23B + 23I;
- #375 — authoritative guided-ordnance event seam for final VFX; owned by 23E.

Explicit non-blocking/post-RC items:

- #361 — human sprite approval debt;
- #371 — optional future beam specialization.

## Automated acceptance

`Stage23AReleaseGovernanceContractTest` proves:

1. all mandatory RC loops have scope, owner and evidence;
2. all three scope tiers are represented and feature requests are explicitly separable;
3. release-gate issues have severity, owner and closure evidence;
4. issue templates preserve blocker-vs-feature separation;
5. version identity and release-notes fields are fixed;
6. every Stage-22 inventory definition still marked PROVISIONAL has explicit non-promotion
   disposition;
7. accepted Stage-22 production engineering/ammunition IDs do not intersect that provisional set;
8. canonical roadmap state advances to `23A COMPLETE / 23B NEXT`.

No Stage-23B production UI implementation is included in this stage.
