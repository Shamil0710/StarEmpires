# Stage 8.5 — Authored Representative Integration Evidence

Date: 2026-08-13

## Scope

This checkpoint moves the accepted heavy-corvette asset-pack rendering out of the dedicated inspector and into the main `--graphics-spike` Representative/Tactical/Close-up runtime.

The goal is to keep the existing benchmark workload comparable while removing the temporary procedural exhaust from the authored hero ship.

## Main graphics-spike behavior

The heavy corvette now uses the complete production-like asset pack in the main validation runtime:

```text
heavy_corvette_white_01_base.png
heavy_corvette_white_01_emissive.png
heavy_corvette_white_01_damage.png
heavy_corvette_white_01_engine_idle.png
heavy_corvette_white_01_engine_thrust.png
```

Default hero state in `--graphics-spike`:

```text
engine: THRUST
emissive: ON
damage: OFF
runtime forward: RIGHT
source art: LEFT
```

Controls shared with the main graphics validation window:

```text
1 — Representative
2 — Tactical
3 — Close-up
E — engine OFF -> IDLE -> THRUST
D — damage overlay ON/OFF
L — emissive ON/OFF
H — hardpoints ON/OFF
R — hero rotation ON/OFF
ESC — exit
```

## Benchmark-semantics preservation

Representative remains:

```text
ships: 50
asteroids/background objects: 500
procedural particle workload: 2000
review objects: 2550
```

The authored heavy corvette no longer receives the old procedural hero-exhaust particles. Instead, all 2,000 procedural particles are deterministically redistributed across the other 49 ships.

This preserves the Stage-8.5 particle workload while avoiding double-rendering procedural exhaust on top of the accepted authored thrust texture.

The hero still uses:

- three transformed engine hardpoints;
- authored `THRUST` or `IDLE` VFX attached with the same alpha-core placement validated in the dedicated asset inspector;
- additive engine glows;
- aligned emissive layer;
- optional aligned severe-damage overlay;
- transformed nose weapon origin;
- shield feedback;
- framebuffer + post-process path.

## Automated verification

Code integration commit:

```text
e8fa4c58779824264cb6e5a9283f23beacfb3d1c
```

GitHub Actions Java 17 verification: **SUCCESS**.

The subsequent launcher/hardware-profile checkpoint:

```text
83b7a5cb7df0345f716508809d045dceaf6c9f52
```

also passed the full GitHub Actions verification pipeline.

## Runtime smoke — exact integrated artifact

The exact CI artifact from `e8fa4c58779824264cb6e5a9283f23beacfb3d1c` was launched under Linux Xvfb with software OpenGL using `--graphics-spike`.

Observed Representative HUD:

```text
1920 x 1080
mode: REPRESENTATIVE
ships: 50
asteroids: 500
particles: 2000
review objects: 2550
hero: REAL HEAVY CORVETTE
engine: THRUST
emissive: ON
damage: OFF
canvas: MATCH
authored hero exhaust: active
procedural particles: redistributed to non-hero ships
runtime forward: RIGHT
draw calls: 17
post-process: ON
```

No shader, framebuffer, texture-loading or runtime presentation exception occurred.

The software-renderer frame-time/FPS values are intentionally **not** used as performance evidence. This run is correctness smoke only.

## Reference-hardware capture

`run-graphics-validation.cmd` now writes:

```text
target/stage8_5_hardware_profile.txt
```

on Windows after the clean package step.

The file attempts to capture:

- Windows caption/version/build;
- CPU model;
- physical/logical CPU counts;
- installed RAM;
- GPU model(s);
- GPU driver version(s);
- Java executable;
- processor identifier.

This removes manual transcription from the remaining reference-machine evidence step.

## Remaining Stage-8.5 evidence after this checkpoint

- run the updated Representative mode on the real Windows GPU and record final post-integration frame metrics;
- retain/upload the generated `target/stage8_5_hardware_profile.txt` values in Stage-8.5 evidence;
- make the final bloom/emissive policy decision;
- write the final technology decision: `KEEP_LIBGDX` or evidence-based `MIGRATION_RECOMMENDED`;
- only then close Stage 8.5 and unblock Stage 9.
