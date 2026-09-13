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

Stable `ShipGlyph.entityId` values are also tracked. A destruction sequence is spawned only on an
observed `wreck=false -> wreck=true` transition. A wreck first materialized from an existing saved
state does not produce a false explosion or lingering heat effect.

The primary destruction burst combines a larger additive flash, hot core, plasma particles and
ballistic hot-fragment slivers. Its world-space radius and fragment count are derived from the
projected physical hull `lengthM` / `widthM`, so capital ships read as materially larger events than
small combatants without introducing a separate visual-only size class.

A newly destroyed ship also receives a short presentation-only wreck lifecycle:

- restrained residual hot-hull glow that fades over roughly one to three seconds;
- two to five deterministic secondary detonations, with count and lifetime scaled from hull length;
- deterministic off-center pulse positions seeded from `entityId` and pulse index;
- small bounded secondary hot-fragment and plasma emissions.

The residual tracker follows the still-present wreck glyph position and expires automatically. It
never persists into save data and never rearms while a ship remains a wreck. If the wreck leaves the
snapshot, the residual presentation effect is discarded immediately.

All fragments are cosmetic only; they do not become authoritative debris and do not cause secondary
damage.

## Missile detonation boundary

`GUIDED_MISSILE` and `INTERCEPTOR` body glyphs currently describe bodies that still exist in the
authoritative combat projection. The snapshot does not expose a dedicated detonation/destruction
event for those bodies. This VFX layer therefore deliberately does **not** infer a missile explosion
from a body disappearing between frames, because disappearance may also mean culling, interception,
expiry, snapshot filtering, or another non-detonation lifecycle transition.

A later missile-explosion pass should consume an explicit stable combat/presentation event rather
than reconstructing authority from absence.

## Determinism and bounded cost

Random-looking particle motion is generated from stable event/entity IDs. Given the same event,
the emitted particle pattern is reproducible. Particle aging uses presentation frame time only and
is capped per frame so a stall cannot advance effects by an arbitrarily large amount.

Hard presentation budgets:

- maximum active particles: `512`;
- maximum active flashes: `96`;
- maximum remembered impact event IDs: `2048`;
- maximum simultaneous lingering wreck-effect trackers: `48`.

Oldest presentation entries are discarded when the visual budget is exceeded. This degradation is
allowed because VFX never owns simulation truth.

## Visual language

The default palette is intentionally restrained:

- engines / beams / shields: cool blue-cyan;
- armor interaction: warm amber;
- penetration / hot fragments: red-orange;
- destruction core: warm ivory/orange;
- lingering wreck heat: dim red-orange;
- decoys: muted violet.

The goal is readable engineering/emissive feedback, not neon cyberpunk or large cinematic halos.
Secondary detonations are intentionally smaller and dimmer than the primary destruction burst so
large ships feel massive without filling the screen with persistent bloom.

## Tests

`TacticalVfxStateTest` covers:

- one-shot triggering for repeated impact snapshots;
- deterministic particle emission from stable event identity;
- one-shot destruction on an alive-to-wreck transition;
- no phantom destruction or lingering heat when an already-wrecked object is first materialized;
- destruction scale derived from physical hull dimensions;
- deterministic secondary detonation placement for stable entity identity;
- automatic expiry of lingering wreck effects without rearming;
- bounded particle/flash/event-memory/wreck-effect budgets under saturation;
- capped presentation-frame integration.

## Deferred Stage-23 work

This foundation intentionally does not mark Stage 23E complete. Remaining final-art work includes:

- optional framebuffer / shader bright-pass bloom if later profiling justifies it;
- user-facing reduced-intensity / accessibility VFX profile;
- explicit authoritative/presentation missile-detonation events and their VFX;
- final authored explosion sprites/textures where appropriate;
- final audio coupling;
- performance profiling on the final tactical content density;
- final destruction-state art and longer-lived non-authoritative debris treatment.
