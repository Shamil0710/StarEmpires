# Stage 19I-C — Guided Propulsion Burn Envelope

Status: IMPLEMENTED IN THIS SLICE, PENDING CI/MERGE

## Problem closed by this slice

`WeaponDefinition.GuidedWeapon` has always authored a finite `burnTimeSeconds`, but the pre-Stage-19I `GuidedWeaponBody` runtime persisted only remaining propellant. Repeated guidance ticks could therefore continue burning as long as fuel remained, even after the authored powered-burn lifetime should have expired.

That was acceptable as an unexercised Stage-17.5 seam, but it is not acceptable once Stage 19I drives guided bodies continuously in scaled battles.

## Physical correction

`GuidedWeaponBody` now persists `remainingPoweredBurnSeconds` alongside remaining propellant.

A burn is bounded simultaneously by:

- the requested deterministic simulation interval;
- remaining authored powered-burn lifetime;
- remaining physical propellant divided by authored mass flow.

Both propellant mass and powered-burn lifetime are consumed by the actual executed burn duration. Residual propellant remains part of body mass even when the propulsion lifetime has expired; it does not magically disappear and it cannot generate further delta-v.

`remainingDeltaVMps()` now reports physically deliverable remaining delta-v inside both the fuel and powered-burn limits rather than ideal rocket-equation delta-v from fuel alone.

## Acceptance

The slice requires:

- one oversized requested burn to stop exactly at authored `burnTimeSeconds`;
- residual unused propellant to remain physical body mass;
- later burn attempts after lifetime expiry to create no velocity or fuel change;
- fragmented per-tick burns to consume exactly the same finite total powered lifetime;
- existing guidance and Stage 19I guided-ordnance tests to remain compatible.

This is a physics hardening slice only. Guided impacts, layered defense, interceptors, EW/decoys and scale gates remain pending.
