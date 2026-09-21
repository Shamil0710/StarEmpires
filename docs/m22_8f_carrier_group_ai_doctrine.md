# M22.8F — carrier-group AI and doctrine

Status: **IMPLEMENTED CANDIDATE — depends on accepted M22.8E and exact-head CI**  
Parent gate: `M22.8 — Carrier / Small-Craft Operations`

## Scope

M22.8F adds deterministic carrier-group doctrine and small-craft mission scheduling without creating a
new physical or tactical authority.

The slice reuses:

- Stage-21 `FleetCommandState.CommandGroupState` for carrier/escort membership;
- `FleetReadinessState` for carrier and escort readiness projection;
- actor-bounded `ObservationEvidence` for hostile range/threat inputs;
- M22.8A individual `SmallCraftId` physical state;
- M22.8B READY/embarked occupancy;
- M22.8C finite launch/recovery queues;
- M22.8D shared PLAYER/AI mission validation and `OrderSource.AI`;
- M22.8E / Stage-19 as the only exact tactical authority.

F does not move carriers or escorts itself. It returns doctrine directives that later M22.8H routes
through ordinary Stage-21 strategic movement/operation authorities.

## One-step deterministic scheduler

`CarrierGroupAiDoctrineService.advance(...)` may accept at most **one** new small-craft mission per
authoritative doctrine call.

This is intentional:

- a failed later opportunity cannot partially mutate several flight-deck queues;
- repeated ticks naturally form sequential strike packages;
- M22.8C remains responsible for finite launch ordering and throughput;
- multi-axis attacks are represented by distinct actor-authored `axisId` values on ordinary mission
  opportunities, not scripted damage events or hidden attack bonuses.

The selected mission is always submitted through
`SmallCraftMissionCommandService.submit(..., OrderSource.AI, ...)`.

## Carrier standoff and withdrawal

Carrier positioning is a doctrine decision, not a stat modifier.

A fresh actor-known threat may cause:

- `HOLD` when it remains outside the authored standoff envelope;
- `STANDOFF` when it crosses the standoff envelope but does not require full withdrawal;
- `WITHDRAW` when a severe fresh threat is already inside its observed effective range.

Own physical state may independently require withdrawal when:

- carrier readiness falls below doctrine threshold;
- escort readiness falls below doctrine threshold;
- persistent wing readiness/losses/depletion fall below doctrine threshold.

The standoff envelope uses:

```text
observed threat effective range × authored doctrine multiplier
```

The multiplier changes decisions only. It grants no sensor, weapon, movement or endurance capability.

Stale or future-dated hostile evidence is ignored for hostile doctrine and cannot trigger intercept,
strike, standoff or threat-driven withdrawal.

## Escort directives

F emits only intent:

- `SCREEN_CARRIER`;
- `INTERCEPT_SCREEN`;
- `STRIKE_SCREEN`;
- `COVER_WITHDRAWAL`.

Carrier and escort FleetIds must already be members of the supplied ordinary Stage-21 command group.
F does not directly mutate fleet placement, velocity, combat state or strategic orders.

M22.8H is responsible for translating these directives into lawful Stage-21 operations/orders.

## Wing readiness

Wing readiness is derived from the persistent wing roster supplied by the carrier-group authority.

A craft contributes as ready when it is either:

- physically embarked in `READY` state with no active mission; or
- physically deployed on an `ACTIVE` mission and not currently doctrine-depleted.

Destroyed/missing identities remain absent from the numerator, so physical losses reduce readiness
without creating a virtual replacement pool.

Doctrine recovery pressure is based on current physical state:

- zero ammunition, but only for craft that actually possess ammunition interfaces;
- reaction mass below the authored recovery reserve;
- mean compartment integrity below the authored threshold;
- minimum recorded subsystem integrity below the authored threshold.

These thresholds decide return behavior only and never refill or repair the craft.

## Mission priority

For each doctrine tick F chooses the next lawful mission in this order:

1. return/recover/divert a deployed craft when withdrawal or physical depletion requires it;
2. QRA/interception against a current actor-known threat;
3. restore minimum CAP;
4. anti-ship strike when current threat severity/range and active-strike limits allow it.

Every mission still passes M22.8D ownership, knowledge, command/datalink, physical capability and
delta-v validation.

A bad opportunity therefore fails through the same command path used elsewhere; F has no privileged
AI bypass.

## Multi-axis and sequential strikes

Distinct strike opportunities may carry different `axisId` values. F consumes at most one per
doctrine tick.

After the first craft receives a real D mission, that craft is no longer eligible as READY. A later
tick can select another ready craft/axis. M22.8C then owns the real launch queue.

This proves ordinary sequential and multi-axis scheduling without introducing volley scripts or
abstract wing damage.

## Recovery and survival

When a deployed craft becomes depleted or damaged, F may retask its current mission to
`RETURN`, `RECOVER` or `DIVERT` through D.

The old deployed mission is deterministically superseded; the new mission becomes `RETURNING`.
The craft remains physically outside the bay until E/C perform the real recovery handoff.

Carrier-group withdrawal similarly does not teleport the wing. It prevents fresh offensive launch
selection and prioritizes lawful deployed return/recovery opportunities.

## Acceptance evidence

Automated coverage proves:

- stale threat evidence cannot trigger intercept, strike or threat-driven standoff;
- a fresh threat causes QRA/interception through the common AI mission path;
- CAP is restored on a subsequent deterministic doctrine tick;
- repeated ticks can queue distinct multi-axis anti-ship strikes through the ordinary C launch queue;
- depleted deployed craft are retasked to recovery without teleportation;
- degraded carrier readiness produces withdrawal and blocks a fresh offensive launch;
- carrier and escort identities must belong to the existing Stage-21 command group;
- all selected missions persist `OrderSource.AI`.

## Deferred intentionally

- M22.8G — Stage-18 manufacture, supply, replacement and logistics demand;
- M22.8H — physical Stage-21 strategic carrier-group operation composition and directive execution;
- M22.8I — player-facing carrier UI;
- M22.8J — production carrier/small-craft content and visual binding;
- M22.8K/L — paired balance/counterplay and dense-wing performance evidence;
- M22.8M — full campaign save/load/migration hardening for F mission/doctrine continuity;
- M22.8N — complete integrated carrier causal-chain acceptance and soak.
