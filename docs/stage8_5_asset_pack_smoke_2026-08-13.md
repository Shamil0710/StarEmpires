# Stage 8.5 — Heavy Corvette Asset-Pack Smoke Evidence — 2026-08-13

## Scope

This record captures the first automated/runtime inspection of the complete user-supplied heavy-corvette sprite pack after all five PNG files were added to the Stage-8.5 branch.

The exact CI artifact was launched with:

```text
--asset-pack-validation
```

under Linux Xvfb/software OpenGL. Software-renderer FPS is not performance evidence; this run is used only for resource loading, geometry inspection and runtime rendering correctness.

## Exact resource set

All five canonical resources loaded successfully:

```text
heavy_corvette_white_01_base.png
heavy_corvette_white_01_emissive.png
heavy_corvette_white_01_damage.png
heavy_corvette_white_01_engine_idle.png
heavy_corvette_white_01_engine_thrust.png
```

## Runtime-measured PNG geometry

All five PNG canvases are `1536 x 1024`.

Measured visible alpha bounds (`alpha >= 8`):

```text
BASE          [22,51]-[1457,950]   1436 x 900
EMISSIVE      [43,89]-[1359,897]   1317 x 809
DAMAGE        [18,65]-[1373,902]   1356 x 838
ENGINE IDLE   [194,289]-[1293,711] 1100 x 423
ENGINE THRUST [19,229]-[1519,776]  1501 x 548
```

Full-frame validation result:

```text
base/emissive/damage canvas MATCH
```

For the default THRUST state, the higher-alpha attachment-core analysis selected approximately:

```text
source attachment X: 34 px
source attachment Y center from bottom: 521.5 px
```

The validator maps this source `LEFT_CENTER` attachment to each of the three engine hardpoints and mirrors the VFX with the source-left ship into runtime-forward RIGHT.

## Runtime rendering smoke

The exact CI artifact rendered successfully for several seconds with:

- real base sprite;
- real emissive layer;
- real thrust VFX texture;
- three independent engine hardpoint attachments;
- source `LEFT` -> runtime `RIGHT` orientation normalization;
- no shader/LWJGL/texture-loading/runtime exception.

The three thrust effects visually attach to the three engine nozzles in the software-GL smoke frame. Final acceptance still requires the dedicated Windows real-GPU review of OFF, IDLE and THRUST plus emissive/damage toggles.

## Remaining manual review

Run:

```text
run-heavy-corvette-asset-validation.cmd
```

and verify:

1. `E` -> OFF: no baked long plume remains in the base texture;
2. `E` -> IDLE: all three idle effects attach cleanly;
3. `E` -> THRUST: all three thrust effects attach cleanly and extend behind the runtime-right ship;
4. `L`: emissive overlay remains pixel-aligned when toggled;
5. `D`: damage overlay remains pixel-aligned when toggled;
6. `R`: all layers and VFX remain attached through rotation;
7. `H`: engine/weapon/utility hardpoints remain consistent with the visible hull.

After this manual review passes, the approved engine-state rendering can replace the procedural hero exhaust inside the representative graphics spike.
