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

## Acceptance review

The asset pack passes this review when:

- all five files load;
- base/emissive/damage report `full-frame canvas MATCH`;
- base has no baked long exhaust plume;
- emissive lights align with the base hull when toggled;
- damage aligns with the same hull when toggled;
- engine `OFF` leaves no visible thrust plume;
- engine `IDLE` attaches cleanly to all three nozzles;
- engine `THRUST` attaches to the same nozzles and extends behind the runtime-right ship;
- rotation preserves layer and engine alignment;
- hardpoints remain attached through the full rotation.

After this dedicated review passes, the accepted engine-state rendering can be integrated into the representative Stage-8.5 graphics spike without changing its benchmark workload definition.
