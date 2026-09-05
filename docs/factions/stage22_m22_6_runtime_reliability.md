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

## Remaining stage gates

The acceptance ledger remains active. Operational B06/B09/B10, campaign attrition/recovery,
competent core profile decisions, complete economic normalization, operational batches, final
freeze pins and recorded human B18–B20 evidence are still required. Neither M22.7 nor Stage 23
implementation is part of this continuation.
