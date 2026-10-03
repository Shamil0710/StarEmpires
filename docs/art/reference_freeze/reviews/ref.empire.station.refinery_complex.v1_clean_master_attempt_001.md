# ref.empire.station.refinery_complex.v1 — clean-master attempt 001

**Status:** HARD REJECT — explicit image-target edit ignored  
**Selected geometry authority:** batch-001 `candidate_02`, 94/100  
**Edit generation ID:** `bb73dadd-1e91-4f2a-b5cb-4cd7cf8fae67`

## Requested operation

Use the already selected refinery candidate as the exact image target and perform only a clean-master
pass:

- preserve primary geometry and orientation;
- one object only;
- transparent 1024x1024 canvas;
- no added/removed station modules;
- only clean alpha, edge cleanup and production-level surface refinement.

This was deliberately an **edit**, not a new concept-generation request.

## Actual output

The backend ignored the selected station target and returned a large Stage-23E
**reference-production/status dashboard** containing text, multiple unrelated station visuals, manifest
counts and workflow guidance.

The result violates:

- immutable selected geometry;
- one-object output;
- transparent background;
- no text/UI;
- clean-master edit semantics.

## Decision

**HARD REJECT.**

No pixel from this output has reference authority.

The refinery remains:

- selected geometry: `candidate_02`;
- score: 94/100;
- status: `SELECTED`;
- `geometry_frozen=false`;
- repository canonical master: still missing.

This attempt confirms that geometry-preserving image edits are not reliable in the active image context.
Do not substitute a newly generated refinery merely to unblock the freeze.
