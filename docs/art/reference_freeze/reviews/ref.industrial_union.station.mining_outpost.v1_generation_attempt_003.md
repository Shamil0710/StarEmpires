# ref.industrial_union.station.mining_outpost.v1 — generation attempt 003

**Status:** HARD REJECT — persistent cross-faction/context drift  
**Generation ID:** `e829d0e2-6bad-424a-85c8-9d8c6eedd64f`

## Why this retry was different

After two invalid five-candidate calls, the retry deliberately changed execution shape:

- request **one candidate only**;
- explicitly restate Industrial Union production palette and modular construction grammar;
- explicitly prohibit ivory/cream hull, burgundy/red stripes, heraldic eagle panels, Imperial circular
  hubs and ornamental spires;
- require one mining-outpost object rather than a board.

The goal was to determine whether the failure was caused by multi-candidate packaging or by deeper
image-context contamination.

## Actual output

The backend returned a **seven-object Imperial station concept sheet** using the same cream/burgundy
family and Imperial construction motifs.

Therefore the failure is not merely candidate packaging. The active image context is carrying the wrong
faction/object family across calls.

## Decision

**HARD REJECT.**

- no candidate number;
- no score;
- no source/master path;
- no change from `PLANNED`;
- `geometry_frozen=false`.

Do not issue additional Union station generations in the same contaminated image context. Resume only
after a clean generator context can be established and prove itself with one valid Union single-object
candidate.
