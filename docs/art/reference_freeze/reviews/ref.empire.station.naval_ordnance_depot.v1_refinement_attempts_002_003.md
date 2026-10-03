# ref.empire.station.naval_ordnance_depot.v1 — refinement attempts 002–003

**Status:** REJECTED — backend did not preserve selected geometry

## Attempt 002

Generation ID: `783ad2ed-63db-488b-a771-56cf779d6118`

Requested: constrained edit of batch-001 candidate 02.

Actual: a newly designed multi-candidate presentation/reference board with its own selected candidate,
labels, anchor views and close-ups.

Reject reasons:

- parent geometry was not preserved;
- multiple new candidates were introduced;
- generator-side selection was invented;
- text/UI/background were baked in;
- this bypasses the recorded batch-001 decision.

## Attempt 003

Generation ID: `f75624b8-ff74-4ced-a3b2-5dd7a07bfc92`

Requested again with the original candidate-02 image explicitly supplied as reference.

Actual: five newly redesigned station concepts on one canvas.

Reject reasons:

- edit operation still did not preserve the parent geometry;
- the output is a new candidate sheet, not a constrained edit;
- accepting it would silently replace the selected batch-001 reference lineage.

## Decision

No change to the live reference row:

- status: `SELECTED`;
- selected candidate: `candidate_02`;
- score: 83/100;
- geometry_frozen: `false`.

The selected design remains valid as the visual direction, but the connected image backend is not
reliable enough for geometry-preserving local edits in this workflow.

## Workflow response

Do **not** keep regenerating the selected Empire depot indefinitely. Preserve the current selection and
continue the two-pilot validation with the Industrial Union industrial station. After both pilot
families are selected, return to the Empire depot for a deterministic/manual component-authoring pass
for the loading arm and magazine door/track.

This keeps the visual-reference programme moving without lowering the freeze gate.
