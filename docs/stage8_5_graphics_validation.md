# Stage 8.5 — Graphics / Technology Validation

## Purpose

Stage 8.5 is an evidence-based decision gate before Stage 9. Its job is to prove whether the current Java/libGDX/LWJGL3 presentation stack can support the intended 2D space-sandbox rendering model without coupling graphics to authoritative simulation state.

The Stage-8.5 decision is complete: **`KEEP_LIBGDX`**. The authoritative final rationale and post-integration evidence are recorded in `docs/stage8_5_technology_decision.md`.

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

## 8.5C — Presentation asset geometry and orientation contract

The presentation package defines an explicit asset contract instead of deriving gameplay-relevant placement from transparent PNG pixels.

`ShipSpriteSpec` stores:

- stable presentation-only `assetId`;
- base texture resource path;
- optional emissive texture resource path;
- intended world width / height;
- normalized pivot;
- explicit elliptical collision/selection footprint width / height;
- authored `SourceFacing`;
- ordered visual hardpoints in authored sprite space.

A compatibility radius constructor remains available for compact/circular assets. Existing constructors default source art to `SourceFacing.RIGHT`; assets authored in the opposite direction opt in explicitly.

### Canonical forward convention

The runtime presentation convention is now explicit:

```text
RUNTIME FORWARD = RIGHT / positive local X
```

Source textures do not need to be destructively edited to match that convention. `SpriteOrientationTransform` mirrors left-authored assets horizontally around their declared pivot and applies the same transform to visual hardpoint positions and directions.

This prevents the sprite, engine exhaust and weapon muzzles from disagreeing after an orientation correction.

Important rules:

- texture paths and asset IDs do not become save/entity identity;
- collision size is explicit and is not inferred from alpha bounds;
- hardpoint IDs must be unique within a ship sprite specification;
- source-facing metadata is presentation-only;
- hardpoints remain authored-space metadata and are normalized only while rendering;
- emissive art is optional and can be added without changing simulation state.

### Selected project asset — heavy corvette

The first authored project ship selected for the real-art gate is classified as a **heavy corvette**.

Its production-facing presentation definition is `ProjectShipSprites.whiteHeavyCorvette01()`:

```text
asset ID: ship.heavy_corvette.white_01
rendered size: 120 x 72 world units
pivot: 0.50, 0.50
explicit footprint: 86.4 x 41.8 world units
source facing: LEFT
runtime facing at rotation 0: RIGHT
main engines: 3
weapon hardpoints: 5
utility hardpoints: 1
```

The source sprite uses normalized hardpoint coordinates with `(0,0)` at bottom-left. In authored space the engines are on the right and the nose is on the left. At runtime the presentation transform mirrors the whole visual contract so the nose points right like the procedural validation ships, engine exhaust points left, and forward weapon directions point right.

Canonical base texture path:

```text
src/main/resources/assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_base.png
```

Optional emissive mask path:

```text
src/main/resources/assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_emissive.png
```

## 8.5D — Engine / emissive validation behavior

When the heavy-corvette texture exists:

- the hero ship uses the real authored sprite;
- source-left art is normalized to runtime-right without modifying the PNG;
- all three engine hardpoints receive additive engine glow after the same orientation transform;
- particle exhaust originates from the transformed hardpoints and uses transformed directions;
- the validation beam starts at the transformed `weapon_nose_primary`;
- an optional emissive texture uses the same horizontal normalization;
- the HUD reports `source LEFT -> runtime RIGHT`.

When the texture is absent, the representative load still runs with the deterministic procedural fallback and the HUD explicitly reports the missing real asset.

## 8.5E/F — Desktop graphics validation

The validation scene is separate from the normal game and does **not** create `SimulationSession` or authoritative world state.

### Recommended Windows launch

Use the dedicated root script:

```text
run-graphics-validation.cmd
```

The script checks Java/Maven Wrapper, verifies the heavy-corvette asset path, builds/tests the JAR, prints the review controls, and launches `--graphics-spike`.

`run-graphics-validation.cmd --build-only` may be used to build without opening the graphics window.

### Interactive review modes

The same runtime now supports three explicit views:

```text
1 — REPRESENTATIVE
2 — TACTICAL
3 — CLOSE-UP
H — toggle hardpoint markers
R — toggle hero rotation
ESC — exit
```

#### 1 — Representative

Keeps the performance workload used for the first real-GPU run:

- ships: `50`;
- asteroids/background objects: `500`;
- active additive particles: `2000`;
- framebuffer + full-screen shader post-process;
- authored heavy corvette as the hero ship;
- hardpoint-driven engine/weapon effects.

This is the mode whose FPS/frame-time numbers should be compared against the Stage-8.5 performance target.

#### 2 — Tactical

Reduces background load and enlarges the hero/nearby ships so relative class scale and silhouette readability can be inspected without losing surrounding context.

Current review load:

- ships: `7`;
- asteroids: `140`;
- particles: `560`;
- heavy-corvette preview scale: `2.2x`.

#### 3 — Close-up

Shows one heavy corvette at `6x` preview scale with a lighter background workload. Entering close-up automatically enables hardpoint markers.

Current review load:

- ships: `1`;
- asteroids: `40`;
- particles: `240`.

Hardpoint marker legend:

- engine — cyan;
- weapon — red;
- utility — yellow.

Each marker also has a short direction indicator after runtime orientation normalization. `R` continuously rotates the hero so pivot stability, transformed engine exhaust and weapon directions can be inspected through a full turn.

### Real developer-GPU run captured on 2026-08-13

A user-supplied Windows screenshot confirms that the authored heavy-corvette texture was actually loaded and rendered on a real desktop GPU. Before the orientation-normalization change the HUD reported:

```text
viewport: 2560 x 1369
ships: 50
asteroids: 500
particles: 2000
objects: 2550
FPS: 3240
average frame time: 0.35 ms
p95 frame time: 0.46 ms
max frame time: 0.84 ms
draw calls: 16
max sprites/batch: 2053
heap: 265.5 MiB
post-process: ON
hero asset: REAL HEAVY CORVETTE
hardpoint VFX: ON
emissive: MISSING / OPTIONAL
```

These results strongly exceeded the Stage-8.5 60 FPS target for the representative scene and motivated the orientation correction. A later post-integration Representative run with the approved authored engine/emissive pipeline also passed on the reference Windows machine.

The same screenshot also exposed an important visual-contract issue: the authored heavy corvette faced left while procedural ships faced right. That observation directly motivated the explicit `SourceFacing` and runtime-right normalization now implemented.

### Earlier software-GL smoke evidence

The original graphics-spike exact head `f078714ddcb9b1eafe82703fbe095628f8794142` passed the full Java 17 CI pipeline and produced the packaged desktop JAR. That exact artifact also rendered successfully under Linux Xvfb/software OpenGL. Those software-renderer FPS numbers remain smoke evidence only and are not used to accept or reject libGDX performance.

## Completed real-GPU visual review

Tactical, Close-up and dedicated asset-pack inspection confirmed:

- runtime-right nose direction relative to procedural ships;
- tactical-size silhouette readability;
- three transformed main-engine origins;
- exhaust direction and attachment under rotation;
- transformed nose-weapon beam origin;
- pivot/rotation stability;
- transparent-edge behavior;
- emissive alignment;
- damage-layer alignment;
- authored engine `OFF / IDLE / THRUST` states.

The remaining four weapon hardpoints are intentionally provisional attachment seams until production weapon/turret art exists; this does not block the rendering-stack decision.

## Decision guideline

The roadmap target remains 60 FPS at 1920x1080 on the documented reference developer machine. The final post-integration Representative run at 2560x1369 reached approximately 2376 FPS, 0.43 ms average frame time, 0.60 ms p95, 1.68 ms max and 35 draw calls with 50 ships, 500 asteroids, 2000 particles, authored heavy-corvette thrust/emissive and post-processing enabled. Together with architecture and visual evidence, this supports `KEEP_LIBGDX`.

## Stage 8.5 close status

**COMPLETE — `KEEP_LIBGDX`.**

- final decision record exists;
- roadmap marks Stage 8.5 complete and Stage 9A active;
- real-GPU hardware profile is documented in the final decision record;
- authored engine/emissive integration is present in the Representative spike;
- dedicated production bloom is deferred as a configurable future quality feature, not a technology blocker;
- `BloomMode = OFF / LIGHT / FULL` is scheduled for Stage 13 / V4 and final graphics settings in Stage 22.
