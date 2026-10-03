# ref.industrial_union.station.industrial_station.v1 — candidate generation attempt 001

**Status:** REJECTED — backend target/context drift  
**Source generation ID:** `141157a2-2001-4514-94b6-2b8a38f88a5d`

## Requested operation

Generate only `candidate_01` for:

`ref.industrial_union.station.industrial_station.v1`

Required:

- exactly one Industrial Union industrial station;
- strict top-down orthographic;
- transparent background;
- no text/UI/reference board;
- no comparison;
- no automatic selection.

## Actual output

The backend returned an **Empire Naval Ordnance Depot final-reference presentation board**, including
silhouette, anchors, derived damage/wreck/marker examples and loading-arm animation.

This output belongs to neither the requested faction nor the requested asset and is therefore rejected
without scoring.

## Decision

- no Union candidate was created by this attempt;
- no Union manifest status changes;
- no generated selection/labels have authority;
- the output must not be used to freeze the Empire depot either, because it does not preserve the
  selected batch-001 geometry.

This confirms context/target bleed in the current image backend. Further reference production should
prefer deterministic extraction/authoring from already generated candidate sources rather than
repeated uncontrolled regeneration until the backend can reliably honor one-object targeting.
