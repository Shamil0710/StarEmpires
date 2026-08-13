# Heavy Corvette White 01 — complete asset pack

This directory is the canonical Stage-8.5 integration point for the first **heavy corvette** production-like visual pack.

## Upload exactly these five PNG files

```text
heavy_corvette_white_01_base.png
heavy_corvette_white_01_emissive.png
heavy_corvette_white_01_damage.png
heavy_corvette_white_01_engine_idle.png
heavy_corvette_white_01_engine_thrust.png
```

All five paths are registered in `ProjectShipSprites.whiteHeavyCorvette01Assets()` and covered by unit tests.

## Full-frame ship layers

The following three textures must use the **same canvas size, same pixel alignment and same authored ship placement**:

```text
heavy_corvette_white_01_base.png
heavy_corvette_white_01_emissive.png
heavy_corvette_white_01_damage.png
```

Rules:

- transparent background;
- top-down view;
- source nose points **LEFT**;
- source engines are on the **RIGHT**;
- do not manually flip the files;
- base sprite must not contain baked long engine plume;
- emissive contains only self-lit regions;
- damage is a transparent overlay that aligns pixel-perfect with base.

The runtime convention is:

```text
runtime forward = RIGHT / positive local X
```

`SourceFacing.LEFT` causes base/emissive/damage layers and hardpoints to be mirrored consistently by presentation code.

## Engine VFX layers

```text
heavy_corvette_white_01_engine_idle.png
heavy_corvette_white_01_engine_thrust.png
```

These are ship-specific exhaust sprites. Preferred authored convention for this pack:

- transparent background;
- horizontal top-down plume;
- source-space nozzle attachment is at the **LEFT-center** edge of the VFX image;
- source-space plume extends to the **RIGHT**, matching the source ship whose engines exhaust right;
- idle is shorter/dimmer than thrust;
- no ship hull or background is baked into the VFX texture.

After runtime orientation normalization the exhaust will be placed behind the right-facing ship and extend left.

Exact engine VFX scale/anchor will be validated from the uploaded images rather than guessed from filenames.

## Current ship presentation contract

- asset ID: `ship.heavy_corvette.white_01`;
- source facing: `LEFT`;
- runtime facing at rotation `0`: `RIGHT`;
- rendered size: `120 x 72` world units;
- pivot: `(0.50, 0.50)`;
- explicit elliptical footprint: `86.4 x 41.8` world units;
- 3 main engine hardpoints;
- 5 weapon hardpoints;
- 1 utility hardpoint.

## Validation

Run:

```text
run-graphics-validation.cmd
```

The script checks the canonical filenames before building. Existing visual review controls remain:

```text
1 - Representative
2 - Tactical
3 - Close-up
H - hardpoints
R - rotate
ESC - exit
```

Once the full five-file pack is uploaded, the next integration pass will bind damage and idle/thrust sprites to the existing normalized ship/hardpoint pipeline and validate exact VFX dimensions/alignment on the real asset.
