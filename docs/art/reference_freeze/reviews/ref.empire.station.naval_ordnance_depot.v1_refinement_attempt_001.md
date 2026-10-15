# ref.empire.station.naval_ordnance_depot.v1 — refinement attempt 001

**Status:** REJECTED — geometry-preserving refinement did not occur  
**Target candidate:** batch 001 / candidate 02  
**Source generation ID:** `63b293b4-3423-470b-a445-6a01deffca93`

The intended operation was a constrained refinement of the selected candidate 02:

- preserve its primary radial/citadel geometry;
- make one stowed loading arm explicit;
- make one protected armored magazine door/track explicit;
- retain strict top-down presentation.

The connected image backend instead produced a new five-design sheet rather than a geometry-preserving
edit of candidate 02.

This output is therefore **not accepted** into the reference candidate lineage.

Reasons:

1. parent geometry was not preserved;
2. five new designs were introduced instead of one constrained edit;
3. using any of them as the selected reference would silently discard the recorded batch-001
   selection decision;
4. the reference workflow forbids geometry drift during a post-selection refinement pass.

No manifest status changes are made from this attempt.

The reference remains:

- status: `SELECTED`;
- selected candidate: `candidate_02`;
- geometry frozen: `false`.

Next valid action is a geometry-preserving edit/authoring pass based directly on the accepted
candidate-02 source, or a deliberate decision to reopen candidate selection as a new batch.
