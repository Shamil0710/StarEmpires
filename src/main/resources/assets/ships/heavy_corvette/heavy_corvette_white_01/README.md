# Heavy Corvette White 01 — asset folder

This directory is the Stage-8.5 production-like sprite integration point for the first **heavy corvette** visual.

## Base sprite

Canonical filename:

```text
heavy_corvette_white_01_base.png
```

Repository path:

```text
src/main/resources/assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_base.png
```

The authored source convention is:

- top-down view;
- source nose points **left**;
- source main exhaust points **right**;
- transparent background;
- no collision geometry is inferred from alpha bounds.

The runtime presentation convention is different by design:

```text
runtime forward = RIGHT / positive local X
```

`ProjectShipSprites.whiteHeavyCorvette01()` declares `SourceFacing.LEFT`. The presentation transform mirrors the texture, hardpoint positions and visual directions together, so the source PNG itself should **not** be manually flipped.

## Optional emissive texture

An emissive-only mask may be placed beside the base sprite as:

```text
heavy_corvette_white_01_emissive.png
```

The emissive mask must use the same authored orientation and pixel alignment as the base texture. Runtime orientation normalization will be applied to both layers together.

## Current presentation contract

- asset ID: `ship.heavy_corvette.white_01`;
- source facing: `LEFT`;
- runtime facing at rotation `0`: `RIGHT`;
- rendered size: `120 x 72` world units;
- pivot: `(0.50, 0.50)`;
- explicit elliptical footprint: `86.4 x 41.8` world units;
- 3 main engine hardpoints;
- 5 weapon hardpoints;
- 1 utility hardpoint.

Use `run-graphics-validation.cmd` and switch to Tactical (`2`) or Close-up (`3`) to verify the authored asset. Close-up enables hardpoint markers automatically; `H` toggles them and `R` toggles rotation.
