# Stage 23E visual-reference generation run 000

Status: **DESIGN REVIEW ONLY — NOT A CANONICAL REFERENCE**

## Purpose

This run started the visual-reference production phase and tested whether a single broad generation
request could produce useful faction/object direction before the strict five-candidate pipeline.

Generated review artifact:

- `docs/art/reference_freeze/generated/stage23e_visual_reference_set_review_v0.svg`
- source generation id: `099d6fd6-d70a-40d6-a66e-33ea1d6b22c6`
- embedded repository preview: 480x320 JPEG inside SVG, used only for lightweight documentation

The source generation produced a multi-object reference board covering ships, stations, small craft,
resource bodies, special locations and ordnance.

## Result

The image is **useful as an art-direction overview** but it fails the canonical-reference hard gate.

Hard-rejection reasons:

1. multiple objects are present on one canvas;
2. the image is a reference board / collage rather than one candidate per image;
3. a dark presentation background is baked in;
4. scale captions and text are baked into the visual;
5. some object labels/roles do not exactly match the frozen RC taxonomy;
6. object geometry cannot be extracted as a production master with clean alpha;
7. no single asset in the sheet may be treated as a selected reference.

Therefore:

- no `ref.*.v1` reference is frozen by this run;
- no candidate score is assigned;
- no production base/damage/emissive/wreck layer may be derived from this sheet;
- the sheet remains a review artifact only.

## Useful findings

Despite failing the production gate, the sheet confirms several high-level directions worth retaining:

- Empire reads best with heavier protected axial masses, ivory/gunmetal structure and restrained
  burgundy/brass/cyan accents;
- Industrial Union reads best with repeated modular blocks, material-handling geometry and
  orange/ochre service accents;
- station silhouettes can remain strongly faction-distinct without relying only on hue;
- resource/special-location presentation must be kept visually simpler than faction infrastructure;
- small craft require much stricter physical-hull continuity than the overview board provides.

These are observations only. Canonical authority remains the faction bibles and Stage-23E prompt/spec
documents.

## Next production action

Run the strict pilot pair independently:

1. `ref.empire.station.naval_ordnance_depot.v1`
2. `ref.industrial_union.station.industrial_station.v1`

For each:

- generate exactly five separate images;
- strict top-down orthographic;
- one station only;
- transparent background;
- 1024x1024 target reference canvas;
- no labels/UI/background/VFX;
- score each surviving candidate using the 100-point reference scorecard;
- select/freeze only if the candidate reaches the documented thresholds.

This run demonstrates why the strict one-object pipeline is required and should not be repeated as a
substitute for the five-candidate reference freeze.
