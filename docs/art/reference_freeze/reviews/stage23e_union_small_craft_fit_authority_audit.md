# Stage 23E — Industrial Union Small-Craft Fit Reference Authority Audit

**Scope:** three `DERIVE_FROM_REFERENCE` references on the frozen Industrial Union small-craft base  
**Frozen base:** `ref.industrial_union.small_craft.base.v1`  
**Engineering authority:** `Stage228SmallCraftProductionProjection`

## 1. Recovered base-source verification

The retained source PNG for Union batch-001 candidate 04 was recovered from the conversation file
surface and hashed locally.

Recovered SHA-256:

`bca6d8404a6654c928ab5c04af29429e6a800fcf223205a1a39add233d29d423`

This exactly matches the `source_sha256` already recorded in the frozen base provenance. Therefore the
current 512x256 `reference_master.png` remains valid and no Union base regeneration is needed.

## 2. Shared equipment that must NOT differentiate the fits

All three production fits install the same:

- `module.small_craft_reactor_v1` on `core_reactor`;
- `module.small_craft_drive_v1` on `core_drive`;
- `module.small_craft_sensor_v1` on `utility_sensor`;
- `module.small_craft_radiator_v1` on `utility_thermal`.

Therefore Stage-23E art must not invent different engines, reactors, sensor noses or radiator systems
merely to distinguish interceptor/defence/strike.

The sensor is the same **Compact Intercept Sensor/Fire-Control Array** for all three fits.

## 3. The only authoritative fit differences

### Interceptor

Fit ID: `fit.industrial_union.small_craft.interceptor_v1`

Adds:

- `module.small_craft_beam_v1` on the single `weapon_primary` hardpoint.

Does not install:

- shield;
- kinetic strike mount.

Reference implication: one compact standardized beam cassette/housing only.

### Defence

Fit ID: `fit.industrial_union.small_craft.defence_v1`

Adds:

- the same `module.small_craft_beam_v1` on `weapon_primary`;
- `module.small_craft_shield_v1` on `utility_defense`.

Reference implication: preserve the same beam cassette as interceptor and add restrained visible
shield-emitter housing. Do not change sensor/engine family.

### Strike

Fit ID: `fit.industrial_union.small_craft.strike_v1`

Adds:

- `module.small_craft_kinetic_v1` on `weapon_primary`.

Does not install:

- shield;
- beam mount.

Reference implication: replace the beam cassette with one physically heavier/longer standardized
kinetic cassette/housing. No shield-emitter treatment.

## 4. Weapon presentation anchor

The Union hull owns exactly one `weapon_primary` hardpoint at:

- physical position: `(x=0, y=+0.30 * hullLength, z=0)`;
- Stage-20/23 presentation convention: physical `+y -> sprite +x`;
- resulting normalized review anchor: approximately `x=0.80, y=0.50`.

This is a presentation placement rule only. It does not create a second hardpoint or change weapon arc,
mount capacity or collision geometry.

## 5. Marker/readability contract

Existing M22.8 presentation authority already distinguishes the role markers:

- interceptor -> `INTERCEPTOR_WEDGE`;
- defence -> `DEFENCE_DIAMOND`;
- strike -> `STRIKE_SPEAR`.

Stage-23E fit references should remain visually compatible with those role readings without altering
the frozen base hull.

## 6. Image-backend attempt

A fit edit was attempted against the explicit frozen 512x256 Union reference master.

Generation ID: `59068005-bd36-4626-aa20-757734717d5d`

The backend returned an unrelated multi-station sheet instead of editing the supplied spacecraft.
Result: **HARD REJECT**.

No reference status changes to `SELECTED` or `FROZEN` until a real aligned fit visual exists.

## 7. Gate

`Stage23EUnionSmallCraftFitReferenceAuthorityTest` pins the engineering/content facts above so future
visual work cannot silently invent fit differences.

The test intentionally validates content authority, not artistic quality.
