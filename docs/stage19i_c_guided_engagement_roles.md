# Stage 19I-C — Guided Ammunition Engagement Roles

Status: IMPLEMENTED IN THIS SLICE, PENDING CI/MERGE

## Purpose

Stage 19I now uses the same fitted guided launcher hardware for offensive strike missiles and physical defensive interceptors. Runtime must not infer ammunition purpose from content IDs, doctrine names, payload presence or feed names.

Guided ammunition therefore declares an explicit content semantic:

- `STRIKE` — guided body intended for ordinary ship/large-object engagement;
- `INTERCEPTOR` — guided body intended for defensive ordnance-body engagement.

The role is routing metadata only. It grants no accuracy, thrust, delta-v, damage, range, seeker quality, support-channel capacity or launcher-cycle modifier.

## Content

Current authored roles are:

- `ammo.test_anti_ship_missile_2t_v1` → `STRIKE`;
- `ammo.test_interceptor_750kg_v1` → `INTERCEPTOR`;
- production `ammo.interceptor_1t_v1` → `INTERCEPTOR`.

Schema-v1 guided content that predates the field defaults to `STRIKE` for compatibility. Current project resources author the role explicitly.

## Runtime routing

`ShipGuidedWeaponEngineeringAdapter.deriveGuidedMounts(...)` remains the ordinary offensive path and now returns only `STRIKE` loads.

An explicit overload accepts a required `GuidedEngagementRole`; the forthcoming layered-defense runtime must request `INTERCEPTOR` directly.

This means a compatible launcher loaded with interceptor ammunition is physically valid but invisible to ordinary ship-target guided-fire execution. Conversely, defensive code cannot accidentally consume a strike missile unless it explicitly requests the wrong authored role and fails acceptance coverage.

## Determinism/content fingerprint

`engagementRole` participates in the ammunition catalog semantic fingerprint. Changing a missile between strike/interceptor routing is therefore a content-semantic change, not an invisible runtime policy tweak.

## Stage boundary

This slice does not materialize interceptor launches itself. That remains the next Stage 19I-C gate together with `LayeredDefenseScheduler`, finite interceptor ammunition, launcher readiness/support channels and physical body-body interception.
