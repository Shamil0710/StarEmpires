# Tactical bloom and explosion VFX foundation

## Scope

This slice adds presentation-only tactical VFX on top of `TacticalPrototypeVisualSnapshot`.
It does **not** own combat damage, projectile motion, hit resolution, destruction authority,
world persistence, save/load state, or deterministic simulation truth.

The renderer consumes immutable presentation snapshots only. VFX may be changed, disabled,
or replaced without changing authoritative combat outcomes.

## Continuous emissive cues

The additive VFX pass currently renders bounded local glow for:

- ship engines, driven by the authoritative projected `thrustFraction`;
- guided missile exhaust;
- interceptor exhaust;
- decoy emission with deliberately lower intensity;
- beam source/end points, scaled from delivered energy;
- severe damage hot spots.

The implementation intentionally uses a localized additive radial-glow pass rather than a
full-screen post-processing chain. This keeps the effect compatible with the current tactical
renderer and preserves small-sprite readability at different camera scales. Glow size is clamped
in screen pixels so zoom cannot create unbounded overdraw.

## Impact and destruction cues

Stable `ImpactGlyph.eventId` values trigger each impact VFX once:

- `SHIELD` -> cyan shield flash + short shield-arc particles;
- `ARMOR` -> amber flash + sparks;
- `PENETRATION` -> red/orange flash + hot fragments.

Stable `ShipGlyph.entityId` values are also tracked. A destruction burst is spawned only on an
observed `wreck=false -> wreck=true` transition. A wreck first materialized from an existing saved
state does not produce a false explosion.

A destruction burst combines a larger additive flash, hot core, plasma particles and ballistic
hot-fragment slivers. The fragments are cosmetic only; they do not become authoritative debris or
cause secondary damage.

## Determinism and bounded cost

Random-looking particle motion is generated from stable event/entity IDs. Given the same event,
the emitted particle pattern is reproducible. Particle aging uses presentation frame time only and
is capped per frame so a stall cannot advance effects by an arbitrarily large amount.

Hard presentation budgets:

- maximum active particles: `512`;
- maximum active flashes: `96`;
- maximum remembered impact event IDs: `2048`.

Oldest presentation entries are discarded when the visual budget is exceeded. This degradation is
allowed because VFX never owns simulation truth.

## Visual language

The default palette is intentionally restrained:

- engines / beams / shields: cool blue-cyan;
- armor interaction: warm amber;
- penetration / hot fragments: red-orange;
- destruction core: warm ivory/orange;
- decoys: muted violet.

The goal is readable engineering/emissive feedback, not neon cyberpunk or large cinematic halos.

## Tests

`TacticalVfxStateTest` covers:

- one-shot triggering for repeated impact snapshots;
- deterministic particle emission from stable event identity;
- one-shot destruction on an alive-to-wreck transition;
- no phantom destruction when an already-wrecked object is first materialized;
- bounded particle/flash/event-memory budgets under saturation;
- capped presentation-frame integration.

## Deferred Stage-23 work

This foundation intentionally does not mark Stage 23E complete. Remaining final-art work includes:

- optional framebuffer / shader bright-pass bloom if later profiling justifies it;
- user-facing reduced-intensity / accessibility VFX profile;
- final authored explosion sprites/textures where appropriate;
- final audio coupling;
- performance profiling on the final tactical content density;
- final destruction-state art and longer-lived non-authoritative debris treatment.
