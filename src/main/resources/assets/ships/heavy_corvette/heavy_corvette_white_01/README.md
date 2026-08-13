# Heavy Corvette White 01 — asset pack

This directory is the Stage-8.5 production-like sprite integration point for the first **heavy corvette** visual.

## Canonical files

The complete pack uses exactly these filenames:

```text
heavy_corvette_white_01_base.png
heavy_corvette_white_01_emissive.png
heavy_corvette_white_01_damage.png
heavy_corvette_white_01_engine_idle.png
heavy_corvette_white_01_engine_thrust.png
```

All five resource paths are registered by `ProjectShipSprites.whiteHeavyCorvette01Assets()`.

## Full-frame layers

These three files must use the same canvas size and pixel alignment:

```text
heavy_corvette_white_01_base.png
heavy_corvette_white_01_emissive.png
heavy_corvette_white_01_damage.png
```

Rules:

- transparent background;
- identical ship position/scale on the canvas;
- source nose points **LEFT**;
- source engines are on the **RIGHT**;
- the base texture must not contain a baked long engine plume;
- emissive contains only self-lit content intended for additive composition;
- damage is a transparent overlay over the base hull.

The runtime presentation convention is:

```text
runtime forward = RIGHT / positive local X
```

`ProjectShipSprites.whiteHeavyCorvette01()` declares `SourceFacing.LEFT`. The presentation transform mirrors base, emissive, damage, hardpoints and engine VFX consistently, so source PNG files must **not** be manually flipped.

## Engine VFX layers

Engine resources:

```text
heavy_corvette_white_01_engine_idle.png
heavy_corvette_white_01_engine_thrust.png
```

Source-space convention:

```text
attachment = LEFT_CENTER
plume direction = RIGHT
```

The dedicated validator reads the real PNG at runtime, measures visible alpha bounds and a higher-alpha attachment core, and aligns that source attachment with each of the three engine hardpoints. The VFX is then mirrored with the ship into runtime-forward RIGHT.

## Presentation contract

- asset ID: `ship.heavy_corvette.white_01`;
- source facing: `LEFT`;
- runtime facing at rotation `0`: `RIGHT`;
- rendered size: `120 x 72` world units;
- pivot: `(0.50, 0.50)`;
- explicit elliptical footprint: `86.4 x 41.8` world units;
- 3 main engine hardpoints;
- 5 weapon hardpoints;
- 1 utility hardpoint.

`asset_pack_manifest.json` is the machine-readable summary of these asset rules.

## Dedicated asset-pack validation

From the repository root on Windows run:

```text
run-heavy-corvette-asset-validation.cmd
```

The validation tool reports actual PNG dimensions and alpha bounds and checks whether base/emissive/damage share the same full-frame canvas.

Controls:

```text
E — cycle engine OFF -> IDLE -> THRUST
D — toggle damage overlay
L — toggle emissive layer
H — toggle hardpoint markers
R — toggle ship rotation
ESC — exit
```

This inspector is intentionally separate from `run-graphics-validation.cmd`: the latter remains the representative performance/technology spike, while the asset-pack validator focuses on exact layer alignment and engine-state presentation.
