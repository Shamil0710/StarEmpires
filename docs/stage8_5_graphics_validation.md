# Stage 8.5 — Graphics / Technology Validation

## Purpose

Stage 8.5 is an evidence-based decision gate before Stage 9. Its job is to prove whether the current Java/libGDX/LWJGL3 presentation stack can support the intended 2D space-sandbox rendering model without coupling graphics to authoritative simulation state.

The final decision is intentionally deferred until the remaining visual review is complete:

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

Canonical base texture path:

```text
src/main/resources/assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_base.png
```

Optional emissive mask path:

```text
src/main/resources/assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_emissive.png
```

The binary sprite is now stored under the canonical filename without browser-added suffixes such as `(1)`.

## 8.5D — Engine / emissive validation behavior

The desktop spike loads the heavy-corvette base texture automatically.

When the texture exists:

- ship zero becomes the real heavy-corvette sprite;
- its declared world size and pivot control drawing;
- all three declared engine hardpoints receive additive engine glow;
- particle exhaust for the hero ship originates from those hardpoints and follows their visual directions;
- the validation beam starts at `weapon_nose_primary`;
- an optional emissive texture is overlaid in the additive effects pass when present;
- the HUD reports `REAL HEAVY CORVETTE` and whether the emissive asset is present.

When the texture is absent, the representative load still runs with the deterministic procedural fallback and the HUD explicitly reports the missing real asset.

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

### Representative workload

The workload is encoded by `GraphicsValidationProfile.representative()`:

- ships: `50`;
- asteroids/background objects: `500`;
- active additive particles: `2000`;
- total representative world/effect objects: `2550`;
- heavy-corvette hardpoint-driven effects;
- engine/emissive glow;
- shield-style additive effect;
- beam/projectile-style geometry;
- damage-mark overlay;
- off-screen RGBA framebuffer;
- one full-screen shader post-process pass;
- metrics HUD.

The spike disables VSync and the foreground FPS cap so measured frame time is not intentionally clamped to 60 FPS.

### Real developer-GPU run captured on 2026-08-13

A user-supplied Windows screenshot confirms that the authored heavy-corvette texture was actually loaded and rendered on a real desktop GPU. The HUD reported:

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

These results strongly exceed the Stage-8.5 60 FPS target for this representative scene. Hardware model/driver details are still required before this run becomes a complete reference-machine record, but the runtime result already proves real authored texture upload, real GPU execution, hardpoint-driven VFX and framebuffer/post-processing coexist successfully.

The screenshot also shows the heavy corvette at a larger, readable class scale than the procedural ships, which is appropriate for its heavy-corvette classification. A dedicated tactical/wide-zoom review is still required before finalizing scale and art readability.

### Earlier software-GL smoke evidence

The original graphics-spike exact head `f078714ddcb9b1eafe82703fbe095628f8794142` passed the full Java 17 CI pipeline and produced the packaged desktop JAR. That exact artifact also rendered successfully under Linux Xvfb/software OpenGL. Those software-renderer FPS numbers are smoke evidence only and are not used to accept or reject libGDX performance.

## Remaining visual review

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
- emissive alignment if an optional mask is later added.

## Decision guideline

The roadmap target remains 60 FPS at 1920x1080 on the documented reference developer machine. The real-GPU run already exceeds that performance target by a very large margin for the current representative scene, but the final technology decision also considers visual correctness, batching behavior, maintainability and the final asset workflow.

## Still required before Stage 8.5 closes

- complete tactical/wide-zoom visual review of the real heavy corvette;
- verify or adjust engine and weapon hardpoint placement from close screenshots;
- record CPU/GPU/RAM/driver details for the reference machine;
- decide whether to create an emissive mask and/or dedicated bloom pass;
- write the final `KEEP_LIBGDX` or `MIGRATION_RECOMMENDED` decision with evidence;
- update `docs/development_roadmap.md` with exact final verification and activate Stage 9 only after the gate passes.
