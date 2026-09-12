# Deterministic sector space backgrounds

The current-system map uses one of four packaged space backgrounds under the tactical grid and all interactive objects.

## Identity and determinism

Background choice is presentation-only and is a pure function of the persistent generated-world seed and stable `StarSystemId`. It does not consume the simulation RNG and does not introduce a second persistence authority. The same campaign and system therefore resolve the same background after restart, save/load, UI navigation, or a different rendering order.

The current selector applies a stable 64-bit mixing function to `(worldSeed, systemId)` and maps the result to the ordered background catalogue. Reordering, adding, or removing catalogue entries intentionally changes this presentation mapping and should be treated as a visual compatibility change.

## Rendering contract

Backgrounds are packaged under `assets/backgrounds/`. The renderer:

- loads the four textures once under the active libGDX context;
- uses an aspect-preserving center crop instead of stretching the image;
- dims the artwork so map markers, labels, grid lines and ship sprites retain priority;
- draws only inside the system-map panel and before the grid/object layers;
- disposes all owned GPU textures with the command UI.

This does not change system geometry, physical scale, simulation state, save format, ownership, navigation, or world generation.

## Regression coverage

`SectorSpaceBackgroundCatalogTest` verifies stable repeat selection, participation of the world seed, distribution across the complete four-image pack, and presence of valid packaged JPEG resources.
