# Stage 22 — Local Logistics Cadence / Expansion Discipline Rebalance

**Status:** IMPLEMENTED / PENDING EXACT-HEAD CI  
**Date:** 2026-09-20  
**Scope:** cross-cutting Stage-22 balance correction; does not replace or expand M22.8 carrier authority.

## Problem

Playable campaign evidence showed that the accepted Stage-20 physical model was behaving correctly but
the provisional content-distribution distances were too sparse for ordinary gameplay cadence.

The previous v1 authoring allowed:

| Semantic route | v1 |
| --- | ---: |
| station → station | 10,000–100,000 km |
| station → resource field | 50,000–500,000 km |
| jump arrival → major hub | 100,000–1,000,000 km |
| inner → outer system | 1,000,000–10,000,000 km |

The physical route solver then correctly turned those distances into long acceleration/coast/braking
legs. The defect was therefore generation/balance authoring, not ship physics.

## Accepted correction candidate

The Stage-22 reviewed v2 profile keeps the large system scale but makes routine economic geography
denser:

| Semantic route | v2 |
| --- | ---: |
| station → station | **4,000–30,000 km** |
| station → resource field | **10,000–150,000 km** |
| jump arrival → major hub | **40,000–200,000 km** |
| inner → outer system | **1,000,000–10,000,000 km unchanged** |

The station minimum remains above the current maximum closed station stand-off, and the jump-to-hub minimum remains independently guarded by the existing
`jumpToHubMinimumRemainsBeyondEveryClosedStationStandOff` regression. The Stage-20 physical
stand-off authority is not weakened.

## 60 / 30 / 10 placement distribution

Routine generated targets no longer sample their whole semantic interval uniformly.

For every semantic band, the deterministic one-sample distribution is:

- **60% near zone:** first 25% of the allowed distance span;
- **30% middle zone:** next 35% of the span;
- **10% far zone:** final 40% of the span.

This deliberately produces dense economic clusters while retaining a bounded long-distance tail.
Station collision/traffic/defensive envelopes still raise the effective minimum where necessary.

The wide `INNER_TO_OUTER_SYSTEM` band is unchanged because it describes meaningful outer-system
space rather than routine hub logistics. ACTIVE_LOCAL materialization also retains that 1 Gm inner/outer
threshold instead of inheriting the newly compact routine-infrastructure maximum.

## Faction anti-sprawl policy

The default autonomous expansion policy now treats one-hop growth as ordinary.

Default positive weights become:

```text
resources          25
unmet demand       20
market network     10
proximity          30
construction cost  15
```

The search horizon remains three hops, but a target beyond the routine one-hop frontier must beat the
best routine candidate by an additional **30% utility per extra hop**:

```text
1 hop: ordinary
2 hops: >= 130% of best ordinary candidate
3 hops: >= 160% of best ordinary candidate
```

If no routine candidate exists, the faction is not deadlocked and may consider the reachable distant
frontier normally.

Explicit legacy/custom `ExpansionOpportunityPolicy` callers retain their previous behavior through
the original nine-argument constructor. The anti-sprawl gate is therefore a production-default
balance rule rather than a hidden global restriction.

## Authority boundaries

This correction does **not**:

- multiply ship speed;
- change thrust, reaction mass, delta-v or braking physics;
- change FTL spool/transit/cooldown law;
- shrink the physical star-system envelope;
- introduce teleportation or virtual freight;
- bypass station collision/defense stand-off;
- create a second construction or expansion authority.

It changes only:

1. versioned content-distribution distance authoring;
2. deterministic placement distribution inside those bands;
3. the default strategic opportunity-selection policy.

## Persistence / generation

The route calibration is versioned from:

`stage20a.local-route-semantic-bands.v1`

to:

`stage20a.local-route-semantic-bands.v2`

The old v1 resource remains in the repository as historical generation evidence. Frozen Stage-20
probe/corpus replay explicitly uses v1 together with the original uniform distance sampler. Current
playable generation uses v2 together with the 60/30/10 clustered sampler. This split prevents a
Stage-22 balance correction from rewriting accepted historical evidence.

Existing persisted physical positions are not silently moved; this is a generation/balance change for
newly generated placements and future expansion decisions.

## Regression evidence

Added/updated tests cover:

- exact v2 semantic distance bands;
- jump minimum still beyond every closed station stand-off;
- deterministic placement and request-order independence;
- physical positions remain inside their accepted semantic band;
- 120-seed corpus verifies near-zone dominance and a bounded far tail;
- station operational/defensive separation remains enforced;
- deterministic faction opportunity ranking;
- an unjustified two-hop candidate is suppressed by the distance-discipline gate;
- a sufficiently exceptional remote resource can still justify two-hop expansion;
- explicit legacy/custom expansion policies remain source-compatible.

## Expected gameplay result

Routine traffic should concentrate around developed hubs and nearby extraction zones, producing more
visible deliveries and shorter economic feedback loops. Large distances remain meaningful for outer
system resources, special locations and deliberate strategic expansion instead of being the default
cost of every ordinary transaction.
