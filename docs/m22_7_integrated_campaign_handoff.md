# M22.7 — Integrated Campaign Handoff

Status: **COMPLETE — PR #360 merged; Stage-22 closure recorded**  
Accepted implementation head: `327870db987408781d323457a07d6a870e82a872`  
Exact-head CI: #6703 / run `35087562701` — **SUCCESS**  
Merged to `main`: `22015454773a1e430c50a8743149addfe8947b6f`  
Final acceptance: issue #368 — **CLOSED / completed**  
Production entry point: `run-generated-world.bat` -> `DesktopLauncher --generated-world` -> `GeneratedWorldCommandGame`

This document is the accepted Stage-22 handoff contract. It describes the runtime that exists after M22.7 and the evidence used to close Stage 22. It does not start Stage 23 and does not create a new gameplay authority. Final closure evidence is also recorded in `docs/stage22_completion_record.md`.

## 1. Runtime composition and ownership

`GeneratedCampaignCoordinator` is the final composition root for a generated campaign. It owns orchestration only; accepted subsystem owners remain authoritative for their own state.

The composition order is:

1. Stage-20/20.5 generated playable runtime through `GeneratedCampaignSession` and `Stage20GeneratedWorldRuntimeBridge.LiveRuntime`;
2. Stage-21A living-faction actor lifecycle;
3. Stage-21B strategic intents;
4. Stage-21C diplomacy plus the accepted Stage-19 warfare/conflict state;
5. Stage-21D fleet command/order state;
6. Stage-21E strategic operations;
7. Stage-21F territorial transitions;
8. Stage-21G settlement recovery;
9. Stage-21H NPC mission/reputation/story state;
10. Stage-21I final content/profile and checkpoint envelope.

The coordinator does not duplicate the world, economy, freight, diplomacy, warfare, operation, recovery or mission models. Read-only UI projections consume the same composed runtime.

### Deterministic scheduling

`GeneratedCampaignSession` partitions presentation deltas so one slice cannot cross more than one authoritative fixed tick. Autonomous freight policy is scheduled from authoritative simulation ticks, with a canonical period of 0.4 simulation seconds. Presentation frame cadence is therefore not the gameplay clock.

Time-scale controls are applied uniformly to every system clock. Ordinary supported scales are 1x, 2x, 4x and 8x. At 8x the campaign executes eight times as many authoritative fixed ticks for the same presentation-time probe; it does not create an alternate fast-forward authority.

M22.7E also observes physical freight delivery deadlines on completed authoritative ticks before the next autonomous decision. This keeps delay/shortage consequences deterministic across different frame partitioning and across save/load.

### Opening freight commitment

The calibrated generated-world routes remain physical. For the default public seed, the accepted Stage-20 route authority gives the ordinary bootstrap orders one-way travel times of roughly 992,669–993,259 simulation seconds (about 11.5 days). M22.7 does **not** shorten those distances or travel times to satisfy a first-hour test.

Stage-20 bootstrap freight rows describe already accepted essential-supply commitments, not offers created at campaign second zero. On creation of a **new** integrated campaign, `GeneratedCampaignInitialFreightCommitment` therefore retains the stable first essential bootstrap order as the oldest opening obligation and marks that obligation due at the campaign epoch. No cargo, route, fleet, mass, propulsion capability or order identity is invented or accelerated. The ordinary freight autopilot must still load real source inventory into the real assigned fleet and dispatch it through the accepted physical route.

`Stage20FreightRuntime.dispatchOutbound` preserves an already-authoritative delivery deadline. Dispatching late may not silently rewrite the obligation to `dispatch time + ETA`, because that would erase source-side lateness before it can become a shortage observation. After a completed return trip reaches the source, the existing recurring-cycle logic creates the next deadline from the physical one-way duration. Save-game restore never reapplies the opening normalization; it restores the exact persisted deadline and missed-delivery history.

## 2. Final save contract

The native save format is `Stage21IGeneratedWorldRuntimePersistentState`:

- schema version: `12`;
- runtime contract: `stage21i.generated-world-final-gate.v12`;
- embedded authority chain: complete Stage21H -> Stage21A -> Stage20 runtime;
- metadata: deterministic migration provenance (`sourceFormat`, migrated flag, migration tick).

`GeneratedCampaignAuthorityCheckpoint` captures one atomic chain containing:

- Stage-20/20.5 generated world, active-system identity, authoritative clocks, materialized industrial state, freight fleets/orders/cargo lots and exact local physical mirrors;
- Stage-21A living actors;
- Stage-21B strategic intents;
- Stage-21C diplomacy lifecycle and Stage-19 warfare state;
- Stage-21D fleet command state;
- Stage-21E operation state;
- Stage-21F territorial transitions;
- Stage-21G settlement recovery;
- Stage-21H NPC mission/reputation/story state;
- Stage-21I final-format identity and migration provenance.

Stable fleet/order/cargo-lot IDs and allocator watermarks belong to the existing authorities and are restored from the checkpoint; M22.7 does not remap them on load.

### Migration and failure policy

A current Stage-21H checkpoint is adopted into Stage-21I as native provenance (`stage21h.native`). Older states are accepted only through explicitly implemented migration paths. Unknown schemas/runtime versions, malformed/trailing checkpoint data, invalid migration provenance or inconsistent embedded authority state fail closed. No best-effort regeneration is allowed for a failed native restore.

The persistence acceptance suite verifies native encode/decode/restore, continuation at 8x, complete authority snapshot equivalence, stable actor/content identities, supported legacy lineage and malformed-input rejection without mutating the live campaign.

## 3. Causal first-hour campaign proof

The M22.7 first-hour acceptance journey uses the ordinary generated-campaign entrypoints at 8x for one hour of simulation time. It tracks one real freight identity rather than injecting a success state.

The continuity key is preserved across:

`transport order -> fleet -> cargo lot -> physical route -> delivery / crossed delivery deadline / physical loss -> causal observation -> faction decision`

The first-hour proof deliberately allows a crossed service deadline to be the physical consequence when the calibrated inter-system route itself is longer than one simulation hour. The deadline crossing is produced by the same ordinary freight runtime that owns the physical order and cargo; it is not a test-side flag or synthetic actor input.

A midpoint save is encoded, decoded and restored through the final Stage-21I persistence path after the real cargo lot exists and before the tracked opening obligation is observed as overdue. The same cargo-lot, fleet and transport-order identities must still be present after reload. The journey succeeds only when that same tracked chain creates a delivery, delay/shortage or physical-loss consequence which is then published as a causal observation and referenced by a faction decision.

This test is intentionally not a debug-scripted narrative. A failure to produce a consequence is treated as a production causality bug, not as a reason to relax the one-hour assertion or compress the physical route model.

## 4. Production-client smoke

`GeneratedWorldProductionClientSmokeTest` exercises the real `GeneratedWorldCommandGame` composition rather than a test-only client authority. The smoke covers:

- generated campaign boot;
- strategy/navigation projection;
- ship/fleet selection and focus paths used by logistics/military UI;
- strategy/logistics panels backed by the composed campaign;
- production save and reload of the same generated campaign.

Interactive rendering/FPS remains a human-observed concern; the automated smoke proves wiring and lifecycle, not GPU performance.

## 5. 1x / 8x throughput baseline

`GeneratedCampaignThroughputBaselineTest` restores the same captured campaign into two independent probes and advances 100 presentation frames of 0.1 seconds.

Expected deterministic work:

- 1x: exactly 100 authoritative fixed ticks;
- 8x: exactly 800 authoritative fixed ticks;
- generated baseline must contain non-trivial civilian freight traffic and multiple persistent transport orders.

The test prints `M22.7_THROUGHPUT_BASELINE` with freight-fleet/order counts, elapsed milliseconds and effective authoritative ticks/second. Wall-clock numbers are evidence, not a brittle pass/fail threshold; unexplained regressions are investigated against the previous green PR run rather than hidden by widening a timeout.

The last pre-deadline-fix reference run recorded 26 freight fleets and 20 orders, with 100 ticks at 1x and 800 ticks at 8x. Final M22.7 acceptance is the exact-head green CI #6703 on `327870db987408781d323457a07d6a870e82a872`; no unverified wall-clock number is promoted by this document.

## 6. UI/icon/sprite resolution ownership

The production ship-visual hierarchy is documented in [`ui/stage22-production-ship-visual-resolver.md`](ui/stage22-production-ship-visual-resolver.md). In summary, the client resolves from runtime hull/profile identity through the Stage-21I faction profile and governed Stage-22/Stage-17 asset catalogs before an explicit presentation fallback is permitted.

Missing-asset diagnostics are stable and observable. Presentation fallback must not silently replace a registered real asset path. Asset selection remains read-only and cannot rebind simulation faction or hull identities.

Sector backgrounds are presentation-only and are documented separately in [`ui/sector-space-backgrounds.md`](ui/sector-space-backgrounds.md).

## 7. Launcher inventory

### Production generated campaign

- `run-generated-world.bat` — **production generated-world path**. Builds the executable JAR and starts `--generated-world` with an optional seed. F8/F9 save and restore the same integrated campaign.

### Scenario/development launchers

The following root scripts remain scenario/test utilities and are not an alternative campaign authority:

- `run-demo.bat`;
- `run-battle-test.bat`;
- `run-case4-test.bat`;
- `run-test-cargo-ambush.bat`;
- `run-stage165-battle.bat`.

They must not be cited as proof of generated-campaign lifecycle, persistence or first-hour causality.

## 8. Acceptance evidence and closure record

M22.7 closure is satisfied by the following exact evidence:

- composition-root and lifecycle tests are green on the accepted PR head;
- centralized production UI/asset resolver tests are green;
- final persistence/restart/malformed-input tests are green;
- first-hour causal identity journey is green;
- production-client smoke is green;
- 1x/8x throughput probe is part of the accepted CI suite and deterministic work counts are recorded;
- full Maven/JaCoCo/Javadoc/package CI #6703 is green on exact head `327870db987408781d323457a07d6a870e82a872`;
- documentation and launcher wording match the production path;
- M22.7 implementation issues #362–#367 are closed as applicable, with #364 already completed earlier;
- final acceptance issue #368 is closed;
- PR #360 is merged as `22015454773a1e430c50a8743149addfe8947b6f`;
- the Stage-22 closure is recorded in `docs/stage22_completion_record.md` and the authoritative roadmap.

Human sprite aesthetic review remains intentionally deferred in open issue #361 and is not represented as PASS evidence.

## 9. Stage-23 entry manifest — future work only

Stage 23 is unblocked for a separately approved kickoff after this accepted Stage-22 closure. This handoff does **not** start Stage 23. Any Stage-23 planning/implementation must begin from the final Stage-22 runtime rather than adding a parallel campaign layer.

The Stage-23 kickoff must preserve these non-negotiable entry conditions:

1. one `GeneratedCampaignCoordinator` composition root and one authoritative generated-world timeline;
2. Stage-21I native save remains the migration/rollback boundary until an explicitly versioned successor exists;
3. new systems must reference stable world/faction/fleet/order/content identities instead of presentation aliases;
4. new autonomous behavior must schedule from authoritative simulation time, not render cadence;
5. UI remains a read-only projection unless a command is routed back to an existing gameplay authority;
6. every new persistent owner requires codec/migration/restart evidence before its stage can close;
7. performance work must distinguish active/local simulation cost from global strategic/transport cost and preserve the 1x/8x baseline method;
8. build SHA plus relevant content/profile/checkpoint identifiers should be retained in diagnostic evidence where practical;
9. no Stage-23 feature may bypass the M22.7 first-hour causal chain or create a test-only success path.

Concrete Stage-23 feature scope belongs in the next approved roadmap slice; this manifest deliberately defines only entry constraints and rollback guards.
