# M22.6 runtime reliability continuation

Status: active; no balance freeze or completion is claimed.

## Long-route save failure

Required CI on `4512c52d28c3dd207806f5081f3f6810989c993c` failed in
`Stage22CorePairFreightProductionCausalAcceptanceTest` when its final generated-world checkpoint
contained more than 100,000 unread news articles. All other tests passed (1,975 tests, one error,
one existing skip). The ordinary event producer had no retention bound while `GameStateCodec`
already rejected more than 100,000 articles. A UI consumer is absent in inactive/headless sessions,
so long physical routes exposed a production save failure.

`GlobalEventManager` now retains the most recent 100,000 unread presentation articles in publication
order. At capacity, publishing one article expires the oldest article in constant time. Event
activation and explicit destruction news use the same queue. The event set, economic effects,
revision, simulation time and random stream do not depend on this presentation retention policy.
The queue is not a strategic knowledge or economic ledger authority.

The runtime and binary codec share the existing limit. No save schema, binary layout or supported
historical save limit changes. Oversized restored state is rejected, not silently truncated.
Null publication fails before evicting an existing article. Snapshot and save remain read-only.

Regression coverage includes mixed manual/event publications, overflow during automatic simulation
compared with a regularly consumed queue, invalid restored queue size, and binary save/load plus
continued overflow with byte-identical session state. The original B08 physical delivery/production
scenario retains its assertions and its final complete checkpoint; it does not drain news to hide
the overflow.

Full Java-17 source compilation succeeded locally using the available dependency JAR. Local Maven
could not resolve JaCoCo 0.8.15 because Maven Central is unavailable. This is not a full verify pass;
the required GitHub CI on each published source SHA remains the verification gate.

## Exact tactical handoff and executable continuation

The validated request now owns an immutable combatant list and rejects duplicate FleetIds, including
the same ship listed on opposing sides. The Stage-21E adapter rejects stale/future handoff ticks and
faction metadata that differs from the ordinary entity before detached combat runs. Encounter-ID
overflow is checked before any commit. Negative tests compare complete world checkpoint bytes to
ensure rejection leaves all resources, entities, allocators and physical kinematics untouched.

B01 now compares two actual committed encounters with and without a binary save followed by a full
`Stage20GeneratedWorldRuntimeBridge.restore` between them. The second encounter uses reconstructed
ordinary entities, with the original fit and spent stores. Its final complete checkpoint must equal
the uninterrupted run. This is executable save continuation, beyond codec encode/decode symmetry.

These corrections were recovered from the unpushed prior-session work and reconciled with the latest
remote branch. Stage 19 still owns combat, WorldSimulation owns FleetIds and Stage 20 owns physical
kinematics/freight. The fixture still uses legacy generated owners with installed core fits; it does
not prove operational decisions by the actual Empire/Union profiles.

## B08 physical counterfactuals and production after load

The same generated industrial order and finite starting cargo now run in four declared controls:
default/mirrored core fits, each against a pristine or critically damaged but still living interdictor.
Only the exact Stage-19 resolver may destroy that military FleetId. Surviving interdiction continues
to deny departure and leaves the actual refinery without input. Destroying the interdictor permits
the existing freight FSM to deliver its physical cargo and the existing refinery to consume it.

The denied branch reconstructs the world and restores the operation sidecar through their existing
binary codecs, then repeats both failed actions with identical complete checkpoint bytes. The
admitted branch saves at the destination before unloading; both uninterrupted and reconstructed
runtimes unload the same lot and perform actual refining, then must have identical result vectors
and complete checkpoint bytes. No free cargo, replacement order, synthetic production outcome or
test-only drain of pending news is used.

CI retains the four raw result vectors with build SHA, dirty-tree flag, content fingerprint, seed,
permutation, order identity, interdictor survival, physical masses and production outcome. These are
authority counterfactuals, not stochastic tuning results. The chosen damage conditions and legacy
generated ownership are explicit; the test cannot establish faction AI competence or B10 deployment
of unchanged core fits.

Local diagnostic executions of B01 and all four B08 controls completed using current compiled
production code. The scratch diagnostic harness substitutes assertions because JUnit artifacts are
unavailable locally; these runs do not replace the ordinary repository JUnit/Surefire/coverage gate.

## Remaining stage gates

The acceptance ledger remains active. Operational B06/B09/B10, campaign attrition/recovery,
competent core profile decisions, complete economic normalization, operational batches, final
freeze pins and recorded human B18–B20 evidence are still required. Neither M22.7 nor Stage 23
implementation is part of this continuation.
