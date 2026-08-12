# White Heavy Corvette 01 — asset drop folder

This directory is the Stage-8.5 production-like sprite integration point for the first **heavy corvette** visual.

## Required base sprite

Place the supplied transparent PNG here with the exact filename:

```text
white_heavy_corvette_01_base.png
```

Expected repository path:

```text
src/main/resources/assets/ships/heavy_corvette/white_heavy_corvette_01/white_heavy_corvette_01_base.png
```

The source art convention currently expected by `ProjectShipSprites.whiteHeavyCorvette01()` is:

- top-down view;
- nose points left;
- main exhaust points right;
- transparent background;
- no automatic collision geometry is inferred from alpha bounds.

## Optional emissive texture

Later, an emissive-only mask may be placed beside the base sprite as:

```text
white_heavy_corvette_01_emissive.png
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

The Stage-8.5 graphics spike automatically attempts to load the base sprite from this directory. If the PNG is missing it uses the procedural validation fallback and reports that state in the HUD.
