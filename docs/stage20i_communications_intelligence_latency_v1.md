# Stage 20I — communications / intelligence latency v1

Status: **MERGED** — PR #307, exact-head CI run `32545005854` passed; merge
`b44e33ea5969daf3852aa6f5fd267d5807142d45` preserves the validated head tree exactly.

## Purpose

Stage 20I separates four authoritative times for transported knowledge:

```text
physical observation time
→ transmission start time
→ physical transmission duration
→ recipient receipt time and report age
```

It does not apply light-speed micromanagement to every UI action. It supplies the physical boundary
used when information is actually transmitted between persistent nodes or across strategic distance.

## Same-system electromagnetic transmission

`Stage20IntelligenceLatencyService.planLocal(...)` requires sender and receiver in the same
`StarSystemId` and derives:

```text
distance = source LocalPhysicalPosition.distanceTo(destination)
transmission seconds = distance / 299,792,458 m/s
```

There is no map-unit conversion, minimum UI delay or screen-distance shortcut. Cross-system nodes
are rejected by this mode.

## Inter-system intelligence

The current world has no authored FTL-radio capability. Inter-system reports therefore use an
explicit physical courier:

```text
generated source major hub
→ BULK_FREIGHTER_LOADED routine physical access to first jump-arrival anchor
→ Stage20PhysicalGalacticRoute over ordinary neighbor edges
→ routine physical access from final arrival anchor
→ generated destination major hub
```

The fitted one-edge `JumpPlan`, exact-coverage `Stage20JumpEdgeCatalog`, spool/transit/cooldown and
per-hop revalidation remain authoritative. No report can jump directly to a non-neighbor system.

Representative accepted seed `1`, first two distinct faction starts:

| Measurement | Result |
|---|---:|
| source system | 16 |
| destination system | 14 |
| ordinary jump hops | 3 |
| source hub access | 252,241.345 s |
| jump-route arrival | 795.000 s |
| destination hub access | 360,853.400 s |
| total physical transmission | 613,889.745 s |

This is seed/profile evidence, not a hidden universal communications constant.

## Freshness semantics

Delivery preserves the original observation time and original freshness horizon. The recipient gets
new transmission provenance (`PURCHASED_OR_SHARED_MAP_DATA` or
`PERSISTENT_INFRASTRUCTURE_BROADCAST`) but not a refreshed observation timestamp.

Therefore:

- a finite report may be `CURRENT` or already `STALE` at receipt;
- a durable physical survey remains `PERMANENT`;
- sending or buying old data cannot reset its age;
- Stage-20G merges the delayed observation normally and computes freshness at receipt time;
- the sender can transmit only an observation it actually holds; world truth is never queried by
  the latency service.

## Acceptance

- local propagation equals exact SI distance divided by vacuum light speed;
- cross-system local-radio shortcuts fail closed;
- courier endpoints match distinct generated systems and generated major hubs;
- every courier edge belongs to the accepted neighbor-only topology;
- source/destination access uses accepted representative propulsion/endurance;
- observation, send, duration, receipt and age remain distinct machine-readable values;
- recipient evidence preserves original observation/freshness rather than laundering stale data;
- permanent and finite-horizon reports retain correct Stage-20G freshness behavior.

Stage 20J next measures generated production/consumption/build cadence against these physical
logistics times; it must not replace delayed supply with hidden restock.
