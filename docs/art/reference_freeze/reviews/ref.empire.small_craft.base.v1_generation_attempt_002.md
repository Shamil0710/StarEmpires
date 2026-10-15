# ref.empire.small_craft.base.v1 — regeneration attempt 002

**Status:** REJECTED — object-class / output-format drift  
**Reference ID:** `ref.empire.small_craft.base.v1`  
**Expected mode:** `GENERATE_5_SELECT_1`  
**Source generation ID:** `c14b4992-9723-4a6e-8704-f3a2fc16bd10`

## Requested batch

Re-open the missing-source Empire carrier small-craft base as a fresh five-candidate batch because the
previously selected batch-001 candidate 03 is documented but its canonical PNG is not present in Git.

Requested constraints:

- five separate single-object images;
- compact Imperial carrier small craft;
- strict top-down orthographic;
- forward/right orientation;
- transparent background;
- physical authority 28 x 12 x 6 m, 180 t;
- narrow armored prow;
- protected cockpit/sensor block;
- compact protected systems body;
- short three-engine rear cluster;
- neutral attachment zones for interceptor/defence/strike overlays;
- no fit-specific weapon or shield hardware;
- no atmospheric wings/fins;
- no miniature-capital-ship treatment.

## Actual backend output

The image backend returned one multi-object presentation sheet containing five **space stations** rather
than five independent small-craft images.

The output therefore fails both mandatory gates:

1. wrong object class;
2. wrong output packaging (one board instead of five independent files).

## Decision

**HARD REJECT.**

The image is not numbered as a valid candidate, is not scored, is not normalized and has zero
reference authority. It must not be repurposed as a station reference because its generation
provenance and role brief belong to the small-craft batch.

The existing manifest disposition remains:

- `SELECTED`;
- `geometry_frozen=false`;
- batch-001 candidate 03 remains the documented preferred geometry, but no persisted master exists.

## Next action

Do not freeze or derive fit overlays from this reference.

Either recover the exact batch-001 selected PNG, or perform a new valid five-independent-image batch
when the image backend is reliably producing the requested spacecraft object class.
