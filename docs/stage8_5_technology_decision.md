# Stage 8.5 — Technology Decision Record

**Date:** 2026-08-13  
**Decision:** `KEEP_LIBGDX`  
**Status:** ACCEPTED

## Decision

Star Empires keeps the current Java presentation stack:

- Java 17 project baseline;
- libGDX `1.14.2`;
- LWJGL3 desktop backend;
- Ashley `1.7.4` for local runtime ECS;
- VisUI `1.5.9` / Scene2D for UI where appropriate.

No migration to FXGL, jMonkeyEngine or direct LWJGL is recommended.

The Stage-8.5 evidence demonstrates that the current stack can support the intended 2D top-down presentation model while preserving the existing deterministic/headless simulation architecture.

## Why the gate passed

### Compatibility

The libGDX/VisUI upgrade passed the repository Java-17 CI regression suite without introducing an OpenGL dependency into headless simulation or changing save/content semantics.

### Presentation architecture

The project now has a presentation boundary with ordered rendering layers and explicit separation from authoritative simulation state. Rendering can remain disabled in headless benchmarks.

### Production-like asset pipeline

The heavy-corvette validation established:

- explicit sprite/world scale;
- pivot and footprint metadata;
- source-facing normalization;
- engine, weapon and utility hardpoints;
- base/emissive/damage layers;
- authored `OFF / IDLE / THRUST` engine states;
- runtime-right orientation for source-left artwork;
- additive emissive/VFX composition;
- framebuffer/post-process path;
- dedicated Tactical, Close-up and Representative validation modes.

The five-file heavy-corvette pack uses a common `1536 x 1024` canvas and passed real-GPU visual alignment review.

### Real-GPU representative result

Final post-integration Representative run on Windows:

```text
viewport: 2560 x 1369
mode: REPRESENTATIVE
ships: 50
asteroids: 500
procedural particles: 2000
review objects: 2550
hero: REAL HEAVY CORVETTE
engine: THRUST
emissive: ON
damage: OFF
canvas: MATCH
FPS: ~2376
average frame time: ~0.43 ms
p95 frame time: ~0.60 ms
max frame time: ~1.68 ms
draw calls: 35
heap: ~229.4 MiB
post-process: ON
```

The Stage-8.5 target was 60 FPS at 1920x1080 on a documented developer machine. The final representative run exceeds that target by a very large margin even at a larger viewport and after integrating authored base/emissive/engine assets.

The performance result is therefore not close to the technology rejection boundary.

## Reference developer machine

Captured by the validation launcher used for the accepted run:

```text
OS: Microsoft Windows 11 Pro 10.0.26100 build 26100
CPU identifier: AMD64 Family 25 Model 33 Stepping 2, AuthenticAMD
CPU physical cores: not captured in the accepted profile
CPU logical processors: not captured in the accepted profile
RAM: 31.92 GiB
GPU: NVIDIA GeForce RTX 4070
GPU driver: 32.0.15.9579
Local Java executable: C:\Users\Shalim\.jdks\openjdk-24.0.2\bin\java.exe
```

Important distinction:

- project compatibility baseline remains **Java 17** and is verified by GitHub Actions;
- the final real-GPU Windows run happened with the locally selected **JDK 24.0.2** executable.

The missing CPU core-count fields are not a Stage-8.5 blocker. The current branch hardware collector now uses PowerShell/CIM and records CPU name, physical cores and logical processors for subsequent benchmark captures.

## Constraints discovered

No fundamental rendering-framework limitation was found.

The important constraints are pipeline/design constraints rather than engine blockers:

1. source artwork orientation must be explicit and normalized as presentation metadata;
2. engine exhaust must not be baked into the base sprite when thrust state is dynamic;
3. collision/selection geometry must not be inferred from transparent PNG bounds;
4. damage textures should have explicit severity semantics; the current heavy-corvette damage layer represents severe/heavy damage;
5. expensive post-processing should remain configurable and benchmarked rather than silently becoming mandatory.

## Bloom / emissive policy

Stage 8.5 accepts additive emissive rendering and the existing post-process path as sufficient proof of capability. A dedicated production bloom implementation is deliberately deferred instead of blocking the technology decision.

Future rendering quality contract:

```text
BloomMode.OFF
BloomMode.LIGHT
BloomMode.FULL
```

Intended semantics:

- `OFF` — no bloom pass; performance/fallback/accessibility option;
- `LIGHT` — restrained production default for gameplay, using a bounded low-cost bloom path around emissive/VFX highlights;
- `FULL` — high-quality mode with stronger/multi-pass bloom appropriate for capable hardware and screenshots/cinematic presentation.

Planned roadmap placement:

- implement and benchmark the first production `BloomMode` pipeline alongside **Stage 13 / V4 Combat VFX**, when combat provides representative emissive/projectile/explosion stress scenes;
- expose graphics-quality selection, persistence and final performance thresholds in **Stage 22 — UX / performance / release hardening**;
- benchmark `OFF`, `LIGHT` and `FULL` separately; `FULL` must never become an implicit requirement for simulation correctness or gameplay readability.

## Migration alternatives rejected for now

### FXGL

Would provide higher-level 2D/UI conveniences but no demonstrated benefit that justifies replacing the current rendering/ECS integration.

### jMonkeyEngine

Would be relevant primarily if Star Empires changed to a predominantly 3D presentation model. Stage 8.5 confirmed the current 2D approach is viable.

### Direct LWJGL

Would increase low-level control but also substantially increase engine-maintenance cost. No requirement discovered in Stage 8.5 justifies discarding libGDX abstractions.

## Consequence

Stage 8.5 is complete. The production technology decision is `KEEP_LIBGDX`.

The next active core stage is:

**Stage 9A — Entity lifecycle infrastructure**, followed by the rest of Stage 9 Dynamic Economy.

Future visual work continues in parallel and does not block Stage 9 unless it reveals a new measured regression against an explicit quality/performance gate.

## Repository synchronization

The main development roadmap now marks:

- Stage 8.5 as `COMPLETE — TECHNOLOGY DECISION KEEP_LIBGDX`;
- Stage 9 as `ACTIVE — STAGE 8.5 GATE PASSED`;
- Stage 9A as the current implementation focus;
- `BloomMode = OFF / LIGHT / FULL` as planned production work in **Stage 13 / V4 Combat VFX**;
- persistent graphics-quality selection and final per-mode performance thresholds in **Stage 22**.

The Stage-8.5 validation record is also synchronized to this accepted decision, so no document continues to treat the technology decision as pending.
