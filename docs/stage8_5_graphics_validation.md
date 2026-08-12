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

## 8.5C — Presentation asset geometry contract

The presentation package defines an explicit asset contract instead of deriving gameplay-relevant placement from transparent PNG pixels.

`ShipSpriteSpec` stores:

- stable presentation-only `assetId`;
- base texture resource path;
- optional emissive texture resource path;
- intended world width / height;
- normalized pivot;
- explicit elliptical collision/selection footprint width / height;
- ordered visual hardpoints.

A compatibility radius constructor remains available for compact/circular assets, but broad ships should use explicit footprint width/height. `collisionRadius()` is now only a conservative compatibility bound; new code should prefer the explicit dimensions.

`VisualHardpoint` stores normalized sprite-space coordinates, a presentation role (`ENGINE`, `WEAPON`, `UTILITY`) and a visual direction. Hardpoint coordinates and visual direction are not authoritative thrust or weapon physics.

Important rules:

- texture paths and asset IDs do not become save/entity identity;
- collision size is explicit and is not inferred from alpha bounds;
- hardpoint IDs must be unique within a ship sprite specification;
- hardpoint metadata is defensive/immutable after construction;
- emissive art is optional and can be added without changing simulation state.

The generic asset-contract exact head `dc472857a2d84b5729db29d533f5f41ea4c79132` passed CI.

### Selected project asset — heavy corvette

The first authored project ship selected for the real-art gate is classified as a **heavy corvette**.

Its production-facing presentation definition is `ProjectShipSprites.whiteHeavyCorvette01()`:

```text
asset ID: ship.heavy_corvette.white_01
rendered size: 120 x 72 world units
pivot: 0.50, 0.50
explicit footprint: 86.4 x 41.8 world units
source orientation: nose left / exhaust right
main engines: 3
weapon hardpoints: 5
utility hardpoints: 1
```

The source sprite uses normalized hardpoint coordinates with `(0,0)` at bottom-left. Engine visual directions point right (`0°`) and forward weapon visual directions point left (`180°`).

The required base texture must be added with the exact repository path:

```text
src/main/resources/assets/ships/heavy_corvette/white_heavy_corvette_01/white_heavy_corvette_01_base.png
```

An optional future emissive mask is expected beside it as:

```text
src/main/resources/assets/ships/heavy_corvette/white_heavy_corvette_01/white_heavy_corvette_01_emissive.png
```

The directory already contains a `README.md` with the asset-drop contract, so the folder exists in Git before the binary PNG is added.

## 8.5D — Engine / emissive validation behavior

The desktop spike now attempts to load the heavy-corvette base texture automatically.

When the texture exists:

- ship zero becomes the real heavy-corvette sprite;
- its declared world size and pivot control drawing;
- all three declared engine hardpoints receive additive engine glow;
- particle exhaust for the hero ship originates from those hardpoints and follows their visual directions;
- the validation beam starts at `weapon_nose_primary`;
- an optional emissive texture is overlaid in the additive effects pass when present;
- the HUD reports `REAL HEAVY CORVETTE` and whether the emissive asset is present.

When the texture is absent, the representative load still runs with the deterministic procedural fallback and the HUD explicitly reports the missing real asset.

This keeps the performance workload reproducible while making authored-art validation impossible to confuse with fallback rendering.

## 8.5E/F — Desktop graphics spike

The validation scene is separate from the normal game and does **not** create `SimulationSession` or authoritative world state.

### Recommended Windows launch

Use the dedicated root script:

```text
run-graphics-validation.cmd
```

The script:

1. checks Java / Maven Wrapper availability;
2. reports whether the heavy-corvette PNG is present at the expected path;
3. runs `clean package` and package-phase tests;
4. locates the shaded `star-empires-*-all.jar`;
5. launches it with `--graphics-spike`.

`run-graphics-validation.cmd --build-only` may be used to build without opening the graphics window.

The equivalent direct JAR launch remains:

```text
java -jar target/star-empires-1.0-SNAPSHOT-all.jar --graphics-spike
```

### Representative workload

The workload is encoded by `GraphicsValidationProfile.representative()`:

- viewport target: `1920 x 1080`;
- ships: `50`;
- asteroids/background objects: `500`;
- active additive particles: `2000`;
- total representative world/effect objects: `2550`;
- heavy-corvette hardpoint-driven effects when the real sprite is present;
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
- active workload counts and viewport size;
- whether the hero asset is the real heavy corvette or the procedural fallback;
- whether the optional emissive texture is active.

Frame-time statistics use a rolling 240-frame window.

### Automated build and software-GL smoke evidence

The original graphics-spike exact head `f078714ddcb9b1eafe82703fbe095628f8794142` passed the full Java 17 CI pipeline and produced the packaged desktop JAR.

That exact CI artifact was additionally launched under Linux Xvfb with `LIBGL_ALWAYS_SOFTWARE=1` at a `1920x1080` virtual screen. The application rendered for several seconds without a shader compilation, framebuffer, SpriteBatch or LWJGL runtime exception. A captured frame visibly contained the representative ships, asteroids, engine trails, additive glow, beam and metrics HUD.

One captured software-renderer frame reported approximately:

```text
FPS: 52
Average frame time: 20.20 ms
P95 frame time: 29.03 ms
Max frame time: 82.09 ms
Draw calls: 16
Max sprites/batch: 2051
Heap in use: 6.3 MiB
```

These numbers are **smoke evidence only**. They were produced by a virtual/software graphics environment and MUST NOT be used to accept or reject libGDX performance. The purpose of this run is runtime API/visual-path validation, not a reference-machine benchmark.

The heavy-corvette-aware version requires a new smoke/reference run after the real PNG is added, because the texture upload, authored alpha bounds and hardpoint placement cannot be validated while the file is absent.

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

Heavy corvette HUD state: REAL / FALLBACK
Emissive state: ON / MISSING
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

For the real heavy corvette specifically inspect:

- tactical-size silhouette readability;
- wider-zoom readability;
- whether the sprite appears too visually noisy when downscaled;
- three main engine glow origins;
- exhaust direction under ship rotation;
- nose weapon beam origin;
- pivot/rotation stability;
- transparent-edge behavior;
- footprint plausibility relative to the visible hull;
- emissive alignment if the optional mask exists.

## Decision guideline

The roadmap target remains 60 FPS at 1920x1080 on the documented reference developer machine. The decision must not use FPS alone: p95 frame time, visible artifacts, batching behavior, post-processing stability, asset workflow and maintainability all count.

A representative 60 FPS frame budget is approximately `16.67 ms`; therefore average frame time below that value with materially worse p95 spikes still requires investigation.

## Still required before Stage 8.5 closes

- add the selected heavy-corvette base PNG at the documented asset path;
- execute the representative scene and validate its actual pivot/scale/engine/weapon placement;
- optionally create the emissive mask after base-art placement is accepted;
- execute and record the representative desktop run on the reference developer machine;
- inspect visual quality at tactical and wider zoom levels;
- decide whether a dedicated bloom pass is required beyond the current additive glow + color/vignette post-process proof;
- write the final `KEEP_LIBGDX` or `MIGRATION_RECOMMENDED` decision with evidence;
- update `docs/development_roadmap.md` with exact final verification and activate Stage 9 only after the gate passes.
