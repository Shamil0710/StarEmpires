# M22.8A — Small-craft identity and persistence foundation

Status: **IMPLEMENTED CANDIDATE — exact-head CI and merge required for acceptance**  
Parent gate: `M22.8 — Carrier / Small-Craft Operations`

## Scope closed by this slice

M22.8A adds the persistence/identity foundation for individual physical fighters, interceptors, strike craft and reusable combat/support drones without implementing hangars, launch/recovery or missions early.

Implemented contract:

- every physical small craft has a campaign-stable `SmallCraftId`;
- IDs are positive, monotonic and allocated through a persisted next-ID watermark;
- one `SmallCraftState` owns stable faction/design identity plus the accepted Stage-17.5 installed fit, runtime consumables/power/thermal state and instance damage/shield/maintenance/weapon continuity;
- ammunition and reaction mass therefore remain physical Stage-17.5 interface loads, not M22.8 counters;
- `designId` is an authored production fit ID from the accepted Stage-22 core-pair engineering catalog; arbitrary small-craft hull/module strings are not accepted;
- `SmallCraftFitAuthority` resolves `designId` through `Stage22CorePairEngineeringCatalogLoader`, requires the persisted `InstalledFit` to match the authored fit exactly, and reuses the ordinary `ShipFittingValidator` for hull/module/mount, mass/volume, power/heat and consumable-interface budgets;
- the registry applies that same production-fit authority on completed-craft registration, restore and physical-state replacement, so persistence or commit-back cannot bypass fitting rules;
- `SmallCraftEngineeringMaterializationBridge` plus registry-bound materialize/commit methods provide the M22.8A runtime seam from persistent individual state to the ordinary Stage-17.5 `EngineeringComponent` and back, preserving the same craft identity and exact consumable/damage/maintenance continuity without granting or resetting resources;
- that materialization seam intentionally carries **engineering state only**. Hangar occupancy, local position, mission state and Stage-19 tactical entity lifecycle remain owned by B/E/H rather than being invented early in A;
- this slice does **not** author fighter/interceptor hulls or modules early: M22.8J remains responsible for real small-craft content. Acceptance tests intentionally use an existing Stage-22 production fit only to prove the shared content/fitting path;
- the M22.8 sidecar reuses `EntityState.EngineeringState` and `EngineeringStatePersistenceMapper` instead of defining a second fighter engineering schema;
- persistent rows are deterministically sorted by craft ID and reject duplicates or allocator watermark rollback;
- `Stage228GeneratedCampaignPersistentState` wraps the accepted Stage-21I checkpoint unchanged and adds only the M22.8 small-craft sidecar;
- native M22.8 campaign files persist that envelope through `Stage228GeneratedCampaignPersistenceCodec`, while supported Stage-20.5/21A-I saves migrate through the accepted Stage-21 chain into an empty non-granting sidecar;
- the player-facing `GeneratedWorldCommandGame` F8/F9 path uses the M22.8 authority/envelope directly while retaining the existing save-file path so previously written campaigns remain discoverable and migratable;
- Stage-21 adoption initializes an empty sidecar. It does not synthesize craft, ammunition, propellant, repairs or replacements;
- `Stage228CampaignAuthority` delegates ordinary world progression to the accepted `GeneratedCampaignCoordinator` and owns no second campaign clock.

## Explicit non-scope

The following remain owned by later M22.8 slices and are intentionally absent here:

- hangar/bay capacity and occupancy — M22.8B;
- launch/recovery/turnaround queues — M22.8C;
- mission vocabulary and shared PLAYER/AI command validation — M22.8D;
- Stage-19 tactical materialization/commit-back and local movement — M22.8E/H;
- carrier doctrine/AI — M22.8F;
- Stage-18 manufacture/delivery/replacement integration — M22.8G;
- carrier UI/content/balance/performance/failure hardening — M22.8I–N.

`SmallCraftRegistry.reserveIdentityForCompletedProduction()` assigns identity only. The API explicitly does not manufacture an asset or grant resources; M22.8G must bind its use to completed physical production/delivery authority.

## Candidate acceptance evidence

Automated tests added by this slice cover:

- no small craft are seeded into a new campaign;
- adopting an accepted Stage-21I save grants zero craft;
- exact craft-state capture/restore preserves ammunition quantity/mass, reaction mass, damage and maintenance age;
- a reserved-but-not-yet-registered identity is not reused after save/load;
- duplicate IDs and allocator-watermark rollback fail closed;
- current M22.8 campaign envelope capture/restore preserves the accepted Stage-21 state and the M22.8A sidecar exactly;
- native M22.8 bytes round-trip deterministically and legacy Stage-21 native bytes migrate with zero small craft;
- a registered craft must resolve to an authored production fit and match that fit exactly;
- an unknown `designId`, fit drift during physical-state replacement, or malformed persisted fit fails closed through the shared production fitting authority;
- the production-backed fixture uses real Stage-22 authored mount/interface IDs and therefore exercises ordinary Stage-17.5 ammunition/reaction-mass validation rather than a parallel fighter schema;
- engineering materialization does not mutate or remove the persistent craft, and dematerialized commit preserves the same ID/faction/design while retaining simulated ammunition/reaction-mass depletion, damage and maintenance age exactly;
- materialized fit drift and unknown craft identities fail closed without mutating the persistent registry.

The production F8/F9 wiring is compiled as part of the normal client build and uses the same codec exercised by the persistence acceptance tests.

This document does not mark M22.8A accepted until the implementation PR passes exact-head CI, merges to `main`, and post-merge verification is checked.
