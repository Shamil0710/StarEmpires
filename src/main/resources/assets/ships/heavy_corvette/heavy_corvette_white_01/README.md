# Heavy Corvette White 01 — asset folder

This directory is the Stage-8.5 production-like sprite integration point for the first **heavy corvette** visual.

## Required base sprite

The canonical base texture filename is:

```text
heavy_corvette_white_01_base.png
```

Expected repository path:

```text
src/main/resources/assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_base.png
```

The source art convention expected by `ProjectShipSprites.whiteHeavyCorvette01()` is:

- top-down view;
- nose points left;
- main exhaust points right;
- transparent background;
- no automatic collision geometry is inferred from alpha bounds.

## Optional emissive texture

An emissive-only mask may be placed beside the base sprite as:

```text
heavy_corvette_white_01_emissive.png
```

The intended emissive mask should contain engine cores, navigation/utility lights and other self-lit regions while keeping ordinary hull paint dark/transparent.

## Current presentation contract

- asset ID: `ship.heavy_corvette.white_01`;
- rendered size: `120 x 72` world units;
- pivot: `(0.50, 0.50)`;
- explicit elliptical footprint: `86.4 x 41.8` world units;
- 3 main engine hardpoints;
- 5 weapon hardpoints;
- 1 utility hardpoint.

The Stage-8.5 graphics spike automatically loads the base sprite from this directory. If the PNG is missing it uses the procedural validation fallback and reports that state in the HUD.
