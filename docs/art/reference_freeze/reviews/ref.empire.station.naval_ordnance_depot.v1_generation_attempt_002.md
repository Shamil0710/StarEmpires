# ref.empire.station.naval_ordnance_depot.v1 — generation attempt 002

**Status:** REJECTED — backend returned a presentation board instead of separate candidates  
**Source generation ID:** `63c1fd50-81f3-4167-98e6-7b6c3a0bb9ca`

## Requested operation

Generate a valid five-candidate batch for:

`ref.empire.station.naval_ordnance_depot.v1`

Required contract:

- five separate images;
- one station per image;
- transparent background;
- strict top-down orthographic;
- no labels/UI/presentation framing;
- no generator-side candidate selection.

## Actual output

The image backend returned one presentation board containing:

- five candidate thumbnails;
- a generator-authored "SELECTED" label;
- an additional master-reference panel;
- silhouette and scale panels;
- anchor annotations;
- detail close-ups;
- dark presentation framing/background.

## Decision

The entire output is rejected as canonical-reference input.

Reasons:

1. multiple candidates are combined on one canvas;
2. UI/text/labels are baked into the image;
3. background/presentation framing violates the transparent one-object contract;
4. the generator invented a selection decision before project review;
5. extra master/anchor/detail content was generated outside the requested production step;
6. accepting it would bypass the documented hard-rejection and 100-point review process.

The generator-authored `SELECTED` mark has **zero authority**.

## Workflow correction

The next batch will be produced **one candidate per image-generation call**:

1. candidate 01 only;
2. candidate 02 only;
3. candidate 03 only;
4. candidate 04 only;
5. candidate 05 only.

Each call must request exactly one station and no comparative/presentation sheet.

No manifest/reference status changes are made by this failed attempt.
