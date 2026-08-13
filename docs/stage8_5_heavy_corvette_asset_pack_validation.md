# Stage 8.5 — Heavy Corvette Asset-Pack Validation

## Purpose

This validation pass checks the complete production-like visual pack for `ship.heavy_corvette.white_01` independently from the representative rendering benchmark.

The pack is stored under:

```text
src/main/resources/assets/ships/heavy_corvette/heavy_corvette_white_01/
```

Required PNG resources:

```text
heavy_corvette_white_01_base.png
heavy_corvette_white_01_emissive.png
heavy_corvette_white_01_damage.png
heavy_corvette_white_01_engine_idle.png
heavy_corvette_white_01_engine_thrust.png
```

## Runtime validation

Launch on Windows:

```text
run-heavy-corvette-asset-validation.cmd
```

or launch the packaged JAR directly:

```text
java -jar target/star-empires-*-all.jar --asset-pack-validation
```

The inspector reads the actual PNG files with libGDX `Pixmap` before uploading them as textures. It reports:

- real canvas width/height for every layer;
- visible alpha bounds;
- whether `base`, `emissive` and `damage` have identical canvas dimensions;
- higher-alpha engine attachment bounds used to derive source `LEFT_CENTER` anchoring;
- current engine state and layer toggles.

## Controls

```text
E — OFF -> IDLE -> THRUST
D — damage overlay on/off
L — emissive layer on/off
H — hardpoint markers on/off
R — continuous rotation on/off
ESC — exit
```

## Orientation rule

Source art remains authored nose-left. Runtime forward is right.

The same `SpriteOrientationTransform` is used for:

- base texture;
- emissive texture;
- damage overlay;
- hardpoint positions;
- engine VFX placement.

The source PNG files must not be manually flipped.

## Engine VFX attachment

Engine VFX files use this source-space convention:

```text
attachment: LEFT_CENTER
plume: RIGHT
```

The validator does not assume that the left edge of the PNG canvas is the nozzle. It measures alpha bounds at runtime and uses the left edge of a higher-alpha core as the attachment X coordinate. The vertical attachment coordinate is the center of that core.

The visible effect is scaled relative to the rendered heavy-corvette height and placed independently on all three main-engine hardpoints. This makes the validation resilient to transparent margins and different idle/thrust canvas aspect ratios.

## Measured pack

All five resources use a `1536 x 1024` canvas.

```text
BASE          alpha [22,51]-[1457,950]   1436 x 900
EMISSIVE      alpha [43,89]-[1359,897]   1317 x 809
DAMAGE        alpha [18,65]-[1373,902]   1356 x 838
ENGINE IDLE   alpha [194,289]-[1293,711] 1100 x 423
ENGINE THRUST alpha [19,229]-[1519,776]  1501 x 548
```

`base/emissive/damage`: **full-frame canvas MATCH**.

## Real-GPU acceptance — 2026-08-13

Dedicated Windows real-GPU review passed.

- Engine OFF — **PASS**;
- Engine IDLE — **PASS**;
- Engine THRUST — **PASS**;
- emissive alignment — **PASS**;
- damage alignment — **PASS**;
- three main engine hardpoints — **PASS**;
- source-left to runtime-right normalization — **PASS**.

No manual engine-VFX scale or attachment adjustment is required from the current review.

The damage layer reads visually as **heavy/severe damage**. When multi-stage damage presentation is introduced, this texture should be treated as the high-damage state rather than as a generic low-damage overlay.

Detailed review evidence:

- `docs/stage8_5_asset_pack_real_gpu_review_2026-08-13.md`;
- `docs/stage8_5_asset_pack_smoke_2026-08-13.md`.

## Acceptance result

**ASSET PACK REAL-GPU REVIEW: PASS**

The dedicated asset-pack review is no longer a Stage-8.5 blocker.

Next implementation step:

1. use the approved authored engine-state assets in the main Representative graphics spike instead of the hero ship's procedural exhaust;
2. repeat Representative real-GPU metrics after the integration;
3. record reference machine CPU/GPU/RAM/driver information;
4. finalize emissive/bloom policy;
5. issue the Stage-8.5 technology decision.
