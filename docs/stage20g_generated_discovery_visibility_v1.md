# Stage 20G — Generated Discovery / Sensor-Consistent Visibility v1

**Status:** IMPLEMENTED CANDIDATE — exact-head CI required before merge

**Authority:** `stage20g.generated-discovery-bootstrap.v1` +
`stage20g.sensor-consistent-visibility.v1`

## Closed authority chain

Stage 20G now has one explicit flow from generated physical truth into actor-bounded knowledge:

```text
accepted resolved generated world
+ accepted faction starts
+ Stage-20F exact owner/station operational specialization
+ explicit bootstrap discovery authority
→ owner-local Stage20DiscoveryKnowledgeState
→ Stage20DiscoveryPersistentState
```

The bootstrap planner automatically grants only:

1. the assigned faction's start-system major hub through permanent start-placement intelligence;
2. exact Stage-20F operational stations owned by that faction through finite-freshness ownership intelligence;
3. resource knowledge supplied by explicit survey/map/intelligence grants.

All other generated infrastructure and occurrences remain `UNKNOWN`. The planner validates every
grant against exact generated IDs but never copies generated grade or reserve into observer state.

## Resource survey boundary

`ResourceKnowledgeGrant` contains observer-authored knowledge and evidence. For classified or
better knowledge, its family must match the generated occurrence's Stage-18 output family, but its
grade/mass estimate remains the non-degenerate interval defined by the observer authority.

The production-chain acceptance deliberately grants a `1,000–2,000 kg` estimate for a real generated
occurrence and verifies that exact interval survives bootstrap. This proves that the planner did not
substitute the occurrence's physical reserve. A second, ungranted generated occurrence remains
`UNKNOWN` for the same owner.

## Mobile visibility remains Stage 17.5

`Stage20MobileSensorVisibility` is a read-only projection over production `TrackState`:

| Stage-17.5 authority | Stage-20G coarse index | Extra truth granted |
| --- | --- | --- |
| `DETECTED` | `DETECTED` | none |
| `CLASSIFIED` | `CLASSIFIED` | none |
| `TRACKED` | `TRACKED` | none |
| `FIRE_CONTROL` | `TRACKED` + fire-control flag | none |

Position/range availability follows the track and covariance. Exact range, exact identity, velocity,
loadout and static-location knowledge are never manufactured. The system-local `targetId` is not
promoted to persistent `FleetId` authority.

## Physical sensor/world coupling

`Stage20DiscoverySensorGeometryAcceptance` consumes the existing Stage-20A production sensor target
matrix, track policy and physical route calibration. It introduces no map radius, screen distance,
closing-speed constant or new duration target.

Current accepted bright-capital result:

| Quantity | Derived value |
| --- | ---: |
| Representative target | `BATTLESHIP` |
| First physical detection | 72,794,887.604 m |
| Active classification | 764,704.257 m |
| Active tracked solution | 608,146.912 m |
| Active fire control | 511,388.558 m |
| Worst accepted military closing speed | 25,009.947 m/s |
| Detection-to-fire-control duration | 2,890.190 s |
| Required existing tracked-freshness horizon | 60.000 s |

The intermediate phase is therefore about 48.2 minutes at the worst accepted representative closing
speed, well above the existing 60-second tracked-freshness horizon. Detection remains physically far
ahead of fire control without implying exact target truth.

## Persistence and determinism

The result projects directly into the Stage-20G world-seed/version/fingerprint-bound discovery
sidecar accepted in PR #304. Owner states, entries, evidence and binary bytes remain deterministic.

## Acceptance evidence

Automated coverage closes:

- real seed-1 resolved generated-world → final Stage-20F operational report → discovery bootstrap;
- one start hub per placed faction;
- exact owned operational station visibility;
- explicit resource survey and ungranted-resource `UNKNOWN` behavior;
- persistent-sidecar projection;
- every Stage-17.5 mobile information-state mapping;
- absence of exact range/identity/velocity/loadout/static-location grants;
- deterministic current bright-capital geometry derivation;
- rejection of hand-authored/screen-scale duration substitution.

## Runtime handoff retained

Stage 20G does not hide the remaining live materialization seams:

- bootstrap owner knowledge must be attached to the eventual live player/faction intelligence store;
- generated static IDs must be rebound to runtime entity IDs without changing world-stable discovery IDs;
- local `SensorKnowledgeComponent` tracks and durable static discovery must be composed in player/AI
  views without merging their identity domains;
- live scan/visit/survey actions must create evidence/grants rather than reading occurrence truth;
- purchased/shared intelligence transport and latency remain the explicit Stage 20I seam.

The four Stage-20F runtime bridge requirements remain unchanged: source supply, persistent freight
fleets, cargo lots/orders and industrial entity materialization are still required before generated
industry becomes live runtime state.
