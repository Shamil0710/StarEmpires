# Stage 23E — Visual Reference Blocker Ledger

**Purpose:** keep reference generation moving without silently lowering the freeze contract.  
**Rule:** a blocker changes execution order, not acceptance criteria.

## A. Image-context contamination

### A1. Industrial Union station family — BLOCKED

Affected immediate reference:

- `ref.industrial_union.station.mining_outpost.v1`.

Evidence:

- attempt 001 `a2ab3446-7063-47da-ab82-9ff38c900bd5`: Imperial station family;
- attempt 002 `8a772f8e-836a-4bd2-858f-ecbc61ed6a06`: still Imperial after reseeding with real
  Union corvette/freight production sprites;
- attempt 003 `e829d0e2-6bad-424a-85c8-9d8c6eedd64f`: even a one-candidate request returned seven
  Imperial station concepts.

Unblock condition:

1. clean image-generation context;
2. one single-object Union mining-outpost proof candidate;
3. correct Union palette/construction grammar;
4. only then execute the required five-candidate batch.

Do not consume batches for the remaining Union station roles until this proof succeeds.

### A2. Special-location generation — BLOCKED IN CURRENT IMAGE CONTEXT

Affected immediate reference:

- `ref.world.special.energetic_anomaly.v1`.

Evidence:

- attempt 001 `c0092715-d1f0-4c5e-b42d-1dcecd723ac4` returned a Stage-23E UI/dashboard graphic.

Unblock condition:

- clean image context must first produce one text-free transparent bounded anomaly/VFX proof image
  with no station/ship/UI content.

The resonant-resource phenomenon should not be attempted until the same proof succeeds.

### A3. Geometry-preserving image edits — BLOCKED IN CURRENT IMAGE CONTEXT

Affected references include:

- Empire small-craft base recovery;
- Union painted small-craft fit passes;
- escort-hull derelict dead-state cleanup.

Repeated edit requests have returned unrelated station sheets or project-status dashboards. The
refinery clean-master attempt `bb73dadd-1e91-4f2a-b5cb-4cd7cf8fae67` explicitly targeted the selected
candidate and still returned a dashboard, proving that target-preserving edits are unreliable in this
context. Do not treat those outputs as partial success.

## B. Selected reference with no repository-persisted canonical master

These references are **not freezeable** yet:

- `ref.empire.small_craft.base.v1`;
- `ref.empire.station.refinery_complex.v1`;
- `ref.industrial_union.station.industrial_station.v1` — clean master is now accepted; blocker is repository persistence only;
- `ref.empire.station.high_tech_hub.v1`;
- `ref.empire.station.naval_ordnance_depot.v1`;
- `ref.empire.station.frontier_multipurpose.v1`.

For refinery/high-tech/frontier a selected design and temporary geometry-preserving working master were
prepared during review, but repository authority is intentionally withheld until the exact master
binary is persisted under the reference package.

Unblock condition for every row:

1. exact selected geometry available as a clean transparent master;
2. master physically committed under `art_sources/stage23e/references/<reference_id>/selected/`;
3. source Git blob SHA recorded;
4. grayscale, 25%, 12.5%, silhouette and anchor/mechanical review committed;
5. exact-head CI passes.

## C. Derived Union small-craft fits

References:

- interceptor;
- defence;
- strike.

Current state:

- exact M22.8 capability differences are pinned;
- deterministic technical SVG composites exist;
- frozen base geometry is preserved;
- final painted aligned masters do not exist.

They remain `SELECTED`, never `FROZEN`, until a successful painted geometry-preserving pass exists.

## D. Existing derelict

`ref.world.special.escort_hull_derelict.v1` has a real repository production source, but remains
`SELECTED` because small live-looking service/navigation emissions still need deterministic
dead-state cleanup.

No redesign is required. Do not substitute a newly generated hull.

## E. Completed work that must not be reopened without a concrete defect

Current frozen references include:

- all 18 major-ship bases;
- Industrial Union small-craft base;
- Empire industrial station;
- Empire trade/logistics hub;
- Empire mining outpost;
- Empire volatile depot;
- four resource-body references.

The manifest-integrity CI gate verifies that every `FROZEN` row points at a real repository source,
the recorded Git blob SHA matches the bytes, the freeze review exists and
`geometry_frozen=true`.

## Execution order while blockers remain

1. keep exact-head CI green for frozen-reference integrity;
2. recover/persist already-selected clean masters where possible;
3. do not spend further Union/special image generations until clean-context proof succeeds;
4. continue non-image authority/QA preparation that cannot alter visual geometry;
5. after generator reset, resume blocked families in this order:
   - Empire small-craft base recovery;
   - Union mining-outpost proof -> full Union station queue;
   - special-location proof -> anomaly + resonance;
   - geometry-preserving fit/derelict edits;
6. never mark a row `FROZEN` merely because a design was selected.


## F. Ephemeral selected-master recovery evidence

Exact SHA-256/dimension/alpha facts for currently recoverable non-canonical working files are recorded
in:

`docs/art/reference_freeze/stage23e_ephemeral_master_recovery_checksums.md`.

This allows a later session to prove file identity without pretending that an ephemeral/local file was
already committed or frozen.


### B1. Union industrial station — reduced to persistence-only blocker

`ref.industrial_union.station.industrial_station.v1` no longer needs generation, reselection or geometric cleanup.

Accepted clean-master identity:

- SHA-256: `1980e0faedc652f2ed8a243c02b1b2f7638a83c61165de8692be57b4673b0163`;
- 1024x1024 RGBA;
- selected-preview alpha IoU: `0.9813522617901829`;
- explicit stowed gantry and distinct assembly rig present.

Remaining blocker is purely transport/persistence: commit the exact accepted PNG bytes under the
canonical reference package, then run final committed-byte QA and exact-head CI.
