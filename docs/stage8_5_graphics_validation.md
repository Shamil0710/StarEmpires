# Stage 8.5 — Graphics / Technology Validation

## Purpose

Stage 8.5 is an evidence-based decision gate before Stage 9. Its job is to prove whether the current Java/libGDX/LWJGL3 presentation stack can support the intended 2D space-sandbox rendering model without coupling graphics to authoritative simulation state.

The final decision is intentionally deferred until a real desktop validation run is captured:

- `KEEP_LIBGDX`, or
- `MIGRATION_RECOMMENDED` with measured evidence and migration cost.

## 8.5A — Dependency compatibility

Stage branch baseline:

- Java 17;
- libGDX `1.14.2`;
- Ashley `1.7.4`;
- VisUI `1.5.9`.

The dependency-only exact head `dce0af88b778f1fe4e54122d9b60973f86149008` passed the existing GitHub Actions Java 17 verification suite. No simulation, persistence or economic code was changed for the upgrade.

## 8.5B — Presentation boundary

`com.spacesim.presentation.PresentationPipeline` defines a rendering-only orchestration seam with this stable high-level order:

```text
BACKGROUND
  -> WORLD
  -> EFFECTS
  -> OVERLAY
  -> UI
```

Properties:

- stable registration order inside each layer;
- unique pass IDs for diagnostics;
- presentation can be disabled as an explicit no-op;
- the core pipeline has no libGDX, Ashley, simulation or persistence imports;
- GPU resource ownership stays in concrete renderers/apps, not in the orchestration object.

The exact infrastructure head `03df8af7f2ba3fda191af0c8e804408ea5d44094` passed CI.

## 8.5E/F — Desktop graphics spike

The validation scene is separate from the normal game and does **not** create `SimulationSession` or authoritative world state.

Launch the packaged desktop JAR with:

```text
java -jar target/star-empires-1.0-SNAPSHOT-all.jar --graphics-spike
```

The spike deliberately uses procedural validation textures. They are not production art and do not complete the real ship-asset portion of Stage 8.5C. They exist to exercise the rendering path before final assets are coupled to it.

### Representative workload

The workload is encoded by `GraphicsValidationProfile.representative()`:

- viewport target: `1920 x 1080`;
- ships: `50`;
- asteroids/background objects: `500`;
- active additive particles: `2000`;
- total representative world/effect objects: `2550`;
- engine/emissive glow;
- shield-style additive effect;
- beam/projectile-style geometry;
- damage-mark overlay;
- off-screen RGBA framebuffer;
- one full-screen shader post-process pass;
- metrics HUD.

The spike disables VSync and the foreground FPS cap so measured frame time is not intentionally clamped to 60 FPS.

Press `ESC` to exit.

### HUD metrics

The desktop HUD reports:

- current FPS;
- rolling average frame time;
- rolling p95 frame time;
- rolling maximum frame time;
- SpriteBatch draw calls;
- maximum sprites per batch;
- approximate JVM heap in use;
- active workload counts and viewport size.

Frame-time statistics use a rolling 240-frame window.

## Required manual evidence

A Stage-8.5 decision run must record the actual developer machine instead of treating hardware as implicit.

Capture:

```text
Date:
OS:
CPU:
GPU:
RAM:
Java runtime:
Display / viewport:
Driver version if relevant:

FPS:
Average frame time:
P95 frame time:
Max frame time:
Draw calls:
Max sprites/batch:
Heap in use:
Visual defects observed:
```

A screenshot or short capture of the representative scene should accompany the numbers so visual correctness and performance are evaluated together.

## Decision guideline

The roadmap target remains 60 FPS at 1920x1080 on the documented reference developer machine. The decision must not use FPS alone: p95 frame time, visible artifacts, batching behavior, post-processing stability, asset workflow and maintainability all count.

A representative 60 FPS frame budget is approximately `16.67 ms`; therefore average frame time below that value with materially worse p95 spikes still requires investigation.

## Still required before Stage 8.5 closes

- integrate at least one real project ship sprite through a documented asset/pivot/scale contract;
- validate engine animation/emissive placement against that real sprite;
- execute and record the representative desktop run on the reference developer machine;
- inspect visual quality at tactical and wider zoom levels;
- decide whether a dedicated bloom pass is required beyond the current additive glow + color/vignette post-process proof;
- write the final `KEEP_LIBGDX` or `MIGRATION_RECOMMENDED` decision with evidence;
- update `docs/development_roadmap.md` with exact final verification and activate Stage 9 only after the gate passes.
