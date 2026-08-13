# Stage 8.5 — Heavy Corvette Asset Pack Real-GPU Review — 2026-08-13

## Scope

Manual Windows / real-GPU review of the production-like heavy-corvette five-texture asset pack through `run-heavy-corvette-asset-validation.cmd`.

Validated resources:

- `heavy_corvette_white_01_base.png`
- `heavy_corvette_white_01_emissive.png`
- `heavy_corvette_white_01_damage.png`
- `heavy_corvette_white_01_engine_idle.png`
- `heavy_corvette_white_01_engine_thrust.png`

The validation application reports a common `1536 x 1024` canvas for all five files and `full-frame canvas MATCH` for the ship layers.

## Visual states reviewed

### Engine OFF — PASS

- no external exhaust plume is visible;
- illuminated engine cores/nozzles remain readable as part of the ship/emissive presentation;
- the cleaned base sprite no longer depends on a baked external thrust plume;
- runtime orientation remains `RIGHT` while source art remains `LEFT`.

### Engine IDLE — PASS

- three separate short blue-white plumes appear at the three main engine hardpoints;
- attachment is visually centered on the visible nozzles;
- the plume/core overlap is acceptable and does not visibly detach from the engine housing;
- IDLE is clearly distinguishable from OFF.

No manual scale or attachment correction is required from this review.

### Engine THRUST — PASS

- three thrust plumes remain centered on the same three engine hardpoints;
- plume length and intensity are clearly greater than IDLE;
- THRUST is immediately distinguishable from both OFF and IDLE;
- the effect points backward relative to normalized runtime forward;
- the automatic alpha-core attachment calculation produces a visually acceptable result.

No manual scale or attachment correction is required from this review.

### Damage overlay — PASS, severe-damage semantics

- damage features remain aligned with the hull and do not visibly drift relative to the base sprite;
- burns, impact marks and damaged plating remain readable at the inspection scale;
- the layer preserves the ship silhouette;
- visual severity is high enough that this asset should be treated as a **heavy/severe damage state** when a multi-stage damage system is introduced, rather than as a generic low-damage overlay.

The filename remains unchanged for Stage 8.5; severity semantics can be formalized when combat/damage presentation is implemented.

### Hardpoint overlay — PASS / existing provisional weapon policy retained

- hardpoint markers stay attached to the rotated/normalized ship coordinate system;
- main engine hardpoints remain consistent with the accepted OFF/IDLE/THRUST VFX;
- nose weapon origin remains accepted from the earlier real-GPU close-up review;
- remaining weapon/utility mount locations remain provisional until actual mounted weapon art is introduced.

## Emissive layer

The reviewed screenshots were captured with emissive enabled. No visible pixel-alignment drift is apparent between emissive content and the hull.

Stage-8.5 asset alignment is accepted. A later dedicated bloom/emissive policy may change intensity or compositing, but does not require regeneration/re-alignment of this asset pack based on the current evidence.

## Acceptance result

**ASSET PACK REAL-GPU REVIEW: PASS**

Accepted without manual engine-VFX scale/attachment correction:

- base layer;
- emissive alignment;
- engine OFF;
- engine IDLE;
- engine THRUST;
- damage alignment;
- three main engine hardpoints;
- source-left to runtime-right orientation normalization.

## Consequence for Stage 8.5

The dedicated heavy-corvette asset-pack review is no longer a blocker.

Next presentation step:

1. use the approved heavy-corvette engine-state assets in the main Representative graphics spike instead of the hero ship's procedural exhaust;
2. repeat the Representative real-GPU metrics after that integration;
3. record reference machine CPU/GPU/RAM/driver information;
4. finalize emissive/bloom policy;
5. issue the Stage-8.5 technology decision (`KEEP_LIBGDX` or evidence-based migration recommendation).
