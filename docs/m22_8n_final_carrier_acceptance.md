# M22.8N — Final carrier acceptance / soak

Status: **IMPLEMENTED / EXACT-HEAD CI AND DEPENDENCY MERGE GATES PENDING**

M22.8N is the final machine acceptance gate for the Stage-22 carrier / embarked-small-craft
slice. It composes the already accepted Stage 17.5, Stage 18, Stage 19, Stage 21 and M22.8 A-M
authorities; it does not introduce a carrier-only economy, combat engine, clock or aggregate wing
state.

## 1. Final causal corpus

Canonical acceptance class:

`src/test/java/com/spacesim/world/Stage228FinalCarrierAcceptanceTest.java`

The corpus proves four complementary deterministic scenarios.

### PLAYER physical lifecycle + checkpoint continuation

`playerIndustryMissionRecoveryAndSaveLoadFormOneContinuousPhysicalChain`

Proves the same persistent `SmallCraftId` through:

```text
Stage-18 hull/module inputs + finite yard work
→ fresh manufactured production J craft
→ ordinary physical delivery to a Stage-18 service bay
→ canonical purified-water stock consumption into the authored reaction-mass interface
→ finite turnaround inspection
→ ordinary physical station-to-carrier relocation receipt
→ carrier-side SERVICING → READY
→ PLAYER mission through the shared D validator
→ deterministic C launch and physical handoff
→ exact E commit-back with real reaction-mass expenditure
→ PLAYER return/recovery through the same D/C lifecycle
→ physical SERVICING occupancy
→ M schema-v4 binary save/load continuation
```

The checkpoint must preserve the exact craft identity, consumed reaction mass, mission history,
carrier-wing association and recovered servicing state.

### AI + real Stage-19 exact combat authority

`aiPathUsesRealStage19ExactAuthorityWithoutAParallelSmallCraftCombatEngine`

Proves an AI-issued mission passes the same D validation/launch path, then enters
`SmallCraftTacticalEncounterService.production(...)`. The production bridge delegates to the
accepted `Stage19ExactTacticalEncounterResolver`; both physical sides are present and the result
either commits the same persistent craft identity or records physical destruction.

### Permanent loss → strategic degradation → funded physical replacement

`physicalLossDegradesStrategicWingThenFundedIndustryCreatesOnlyFreshReplacement`

Proves:

- two individually manufactured/supplied carrier craft form one explicit H wing;
- a real deployed AI mission can suffer permanent E-layer loss;
- the original identity disappears without allocator rewind or resurrection;
- H readiness derives a lower surviving availability and one physical lost craft;
- recovery funding transfers finite money to a procurement wallet before build settlement;
- Stage-18 inputs/work are still required after funding;
- replacement receives a fresh monotonic identity;
- replacement reaches the carrier only after an ordinary physical delivery receipt and enters
  `SERVICING`, never free `READY`;
- save/load preserves both the missing destroyed identity and the fresh replacement identity.

### Dense-wing deterministic long run

`denseWingLongRunDoesNotGrantCraftReuseIdsOrMaterializeDormantState`

The dense case uses **256 persistent individual craft**, drains the deterministic launch queue
through the physical deck authority, then advances **1,000 additional fixed ticks** with no queued
work. Acceptance requires:

- canonical launch order;
- no duplicate identities;
- no allocator rewind;
- no hidden craft creation;
- no physical-state rewrite from deck scheduling;
- empty final bay/deck operation state after all physical launch handoffs.

This supplements the accepted M22.8L small/medium/dense performance evidence rather than replacing
it.

## 2. Integration seam found by N

The final corpus exposed one missing composition seam between accepted G/J behavior:

```text
production station
→ physical station delivery
→ canonical station stock refuel
→ carrier assignment
```

Before N, G could prove initial production delivery and station-local supply independently, but had
no lawful API for moving an already-serviced craft from a station bay to a carrier bay. N therefore
adds the minimal `SmallCraftPhysicalLogisticsService.transferReadyCraft(...)` extension.

The method:

- requires an already-existing craft;
- requires `READY` occupancy in a physical **station** bay;
- preflights the destination through the existing B capacity authority;
- delegates arrival proof to the existing G `PhysicalDeliveryAuthority`;
- leaves the source untouched when arrival is rejected;
- preserves identity, fit, damage and consumables;
- on successful arrival, removes source occupancy and enters destination `SERVICING`;
- never grants fuel, ammunition, repair, readiness or a replacement.

This is an extension of the existing G physical-delivery authority, not a competing logistics
system.

## 3. Authority map

M22.8N reuses:

- Stage 17.5: fitting, engineering runtime, physical consumables, damage and shipyard work;
- Stage 18: resource ontology, station storage, manufactured modules, yards and finite settlement;
- Stage 19: exact local tactical resolver, tracks/weapons/PD/EW/damage authority;
- Stage 21: ordinary fleet identity/readiness/strategic projection and treasury semantics;
- M22.8A: persistent individual craft identity;
- M22.8B: physical hangar capacity/occupancy;
- M22.8C: launch/recovery/turnaround;
- M22.8D: shared PLAYER/AI mission validation;
- M22.8E: exact tactical materialization/commit-back;
- M22.8G: manufacture, physical delivery and supply;
- M22.8H: carrier-wing strategic readiness and post-battle recovery;
- M22.8J: production small-craft content;
- M22.8L: bounded dense-wing scale architecture;
- M22.8M: unified mission/logistics/wing persistence and fail-closed migration.

## 4. Merge/closure gates

M22.8N and Stage 22 are **not COMPLETE** until all are true:

1. M22.8M PR #405 is accepted on exact-head CI and merged.
2. The rebased J/G reaction-mass binding PR #406 is accepted on exact-head CI and merged.
3. The N branch is rebased/synchronized onto that resulting `main`.
4. The final N corpus passes full Java-17 repository verification on the exact final head.
5. No unresolved blocking review threads remain.
6. The exact accepted N head is merged.
7. Resulting `main` is verified.
8. Only then are the M22.8 contract, Stage-22 completion record and canonical roadmap marked
   COMPLETE, and Stage 23 becomes OPEN/NEXT.

Until those gates are satisfied this document is implementation evidence, not a completion claim.
