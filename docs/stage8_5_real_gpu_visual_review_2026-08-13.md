# Stage 8.5 — Real GPU visual review — 2026-08-13

This record captures the first real-Windows-GPU review after the ship-orientation normalization introduced by `SourceFacing` and `SpriteOrientationTransform`.

## Environment visible from the validation HUD

Both screenshots were captured at:

```text
viewport: 2560 x 1369
libGDX: 1.14.2
post-process: ON
hero asset: REAL HEAVY CORVETTE
source orientation: LEFT
runtime orientation: RIGHT
emissive: MISSING / OPTIONAL
```

CPU/GPU/RAM/driver model details are still required for the final reference-machine record.

## Tactical review

HUD evidence:

```text
mode: TACTICAL
ships: 7
asteroids: 140
particles: 560
review objects: 707
FPS: 3287
average frame time: 0.37 ms
p95 frame time: 0.53 ms
max frame time: 1.76 ms
draw calls: 25
max sprites/batch: 2053
heap: 247.4 MiB
hardpoints: ON
rotation preview: OFF
```

### Visual findings

- PASS — heavy corvette nose points right, matching the runtime forward convention and procedural validation ships;
- PASS — all three main exhaust plumes point left/backward;
- PASS — the heavy-corvette silhouette remains immediately distinguishable from the smaller procedural ships;
- PASS — the current `120 x 72` base world size with tactical preview scaling communicates a heavier class without making the ship dominate the entire view;
- PASS — nose beam originates at the forward/nose side after orientation normalization;
- PASS — transparent sprite edges remain clean against the dark background;
- PASS — no obvious orientation mismatch remains between sprite pixels and hardpoint-driven VFX.

The tactical view is therefore accepted as a successful Stage-8.5 class-scale/readability review for this asset. Final gameplay class ratios can still change later without invalidating the rendering technology decision.

## Close-up review

HUD evidence:

```text
mode: CLOSE-UP
ships: 1
asteroids: 40
particles: 240
review objects: 281
FPS: 2991
average frame time: 0.32 ms
p95 frame time: 0.47 ms
max frame time: 0.70 ms
draw calls: 24
max sprites/batch: 2053
heap: 29.5 MiB
hardpoints: ON
rotation preview: OFF
```

### Visual findings

- PASS — sprite mirror is visually correct; authored nose-left art presents as runtime nose-right;
- PASS — `weapon_nose_primary` is aligned with the visible nose axis and the validation beam begins at the expected forward point;
- PASS — three engine effect origins are aligned with the three visible main engine nozzles closely enough for the current production-like validation;
- PASS — engine particle directions agree with the normalized runtime orientation;
- PASS — pivot at `(0.50, 0.50)` is visually plausible at rotation 0 and does not create an obvious translation offset;
- PROVISIONAL PASS — the remaining four weapon hardpoints and one utility hardpoint are reasonable attachment seams, but the current source art does not visibly contain five mounted weapons, so their exact final positions should be revisited when actual weapon modules/turrets are authored;
- PASS — the sprite retains substantial panel/mechanical detail at close scale without revealing problematic transparent borders or filtering artifacts.

No hardpoint coordinate change is justified from these screenshots alone. Moving markers merely to coincide with painted lights/panel seams would make the metadata less semantically useful for future modular weapons.

## Important asset-pipeline finding — baked engine exhaust

The source PNG itself already contains bright blue main-engine exhaust/plume imagery. The validation renderer then adds dynamic hardpoint-driven glow and particles on top of it.

This is acceptable for the technology spike, but **not desirable for the final production asset contract**, because a base hull texture with baked thrust cannot cleanly represent:

- engines off / docked state;
- idle thrust;
- variable thrust intensity;
- damaged or disabled engines;
- animated exhaust frames;
- faction/technology-specific engine VFX.

Before Stage 8.5D is considered complete, the heavy-corvette asset should therefore be split into at least:

```text
heavy_corvette_white_01_base.png       # hull/no static exhaust plume
heavy_corvette_white_01_emissive.png   # optional self-lit hull/engine-core mask
engine exhaust animation/VFX           # dynamic presentation driven from hardpoints
```

The existing original PNG can remain as source/reference art, but the runtime `base` asset should ultimately have the baked blue exhaust removed.

## Review verdict

The real-GPU Tactical and Close-up reviews support the current technology direction:

```text
orientation normalization: PASS
tactical readability: PASS
heavy-corvette scale grammar: PASS for Stage 8.5
main engine hardpoints: PASS
nose weapon hardpoint: PASS
remaining weapon/utility hardpoints: PROVISIONAL PASS
transparent/filtering behavior: PASS
libGDX rendering capability: no new blocker observed
```

## Remaining Stage 8.5 evidence

The visual-review blockers are now reduced to:

1. repeat the **Representative** mode once after orientation normalization and record its real-GPU metrics;
2. record reference-machine CPU/GPU/RAM/driver details;
3. remove/split baked engine exhaust from the production base sprite and validate idle/thrust presentation;
4. decide/create the optional emissive mask and dedicated bloom strategy;
5. record the final `KEEP_LIBGDX` or evidence-based `MIGRATION_RECOMMENDED` decision.
