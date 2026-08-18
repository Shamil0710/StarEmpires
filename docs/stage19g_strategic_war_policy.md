# Stage 19G — Strategic war objectives, escalation and coercive diplomacy

**Status:** implementation slice.

Stage 19G adds a deterministic strategic policy layer above the physical warfare and economy seams completed in Stages 19E–19F. It does not create a second combat engine, a hidden war-score currency, a diplomacy bonus system, or omniscient strategic knowledge.

## 1. Causal contract

```text
named political objective
+ actor-visible objective evidence
+ own authoritative physical sustainment
+ actor-observed physical pressure on opponent
        ↓
StrategicWarPolicyService
        ↓
HOLD / ESCALATE / OFFER_SETTLEMENT /
SEEK_SETTLEMENT / ACCEPT_SETTLEMENT / DE_ESCALATE
        ↓
ordinary fleet orders / logistics / diplomacy application by later owners
```

The result is policy only. It cannot damage ships, refill ammunition, alter storage, spawn replacements, change route topology or grant combat statistics.

## 2. Named objectives instead of war score

Every conflict input contains explicit `WarObjective` entries with stable IDs and real subject identities such as a route, system, facility or asset. Objectives may be mandatory or optional.

Each objective has an actor-bounded `ObjectiveEvidence` state:

- `UNKNOWN`;
- `OBSERVED_UNMET`;
- `OBSERVED_MET`;
- `OBSERVED_IMPOSSIBLE`.

No objective may become satisfied because an unrelated numeric score crossed a threshold. A mandatory objective is satisfied only by observed evidence or by explicit visible settlement terms granting that exact objective ID.

## 3. Physical sustainment evidence

`PhysicalWarEvidence` carries physical quantities rather than an abstract readiness meter:

- number of currently operational own combat ships;
- own reaction mass in kilograms;
- current repair-material demand in kilograms;
- compatible repair material physically available in kilograms;
- confirmed own destroyed constructed mass;
- confirmed own undelivered cargo mass;
- observed/confirmed opponent destroyed constructed mass;
- observed/confirmed opponent undelivered cargo mass.

Own data may be authoritative to the actor. Opponent quantities must already have been observed or confirmed through that actor's information model. The service has no world/ECS lookup and therefore cannot inspect hidden enemy stockpiles, fleets, facilities or replacement queues.

`Policy` contains explicit political thresholds for sustainment and coercive offers. These thresholds do not alter physical state. They only decide what political action the actor is willing to take given measured consequences.

## 4. Decision precedence

The deterministic decision order is:

1. accept a visible settlement only when it explicitly grants every unresolved mandatory objective;
2. de-escalate when every mandatory objective is already observed as met;
3. seek settlement when a mandatory objective is observed impossible or current physical operations cannot be sustained;
4. offer settlement when actor-observed opponent destroyed mass or denied cargo crosses an explicit coercive-policy threshold;
5. otherwise escalate one represented political level while below `GENERAL_WAR`;
6. at maximum represented escalation, hold.

This is not a prediction of rational behavior for every future faction personality. It is the minimum inspectable production policy language required before Stage 19H persistence and later content/doctrine expansion.

## 5. Actor isolation

Two factions may make different strategic decisions in the same authoritative universe because their observations differ.

Example:

```text
physical enemy loss exists
├─ actor A observed/confirmed it → may justify OFFER_SETTLEMENT
└─ actor B did not observe it    → the loss is not available to B's policy
```

A hidden blockade, hidden loss or hidden shortage therefore cannot create diplomatic pressure for an actor that does not know about it.

## 6. Relationship to Stage 19F

Stage 19G consumes results from the existing physical owners rather than replacing them:

- ammunition shortage is still a real Stage-18 finished-product shortage;
- reaction mass is still physical interface mass loaded from canonical commodity stock;
- repair demand remains derived from actual damage and real repair bills;
- replacement ships still require physical shipyard construction;
- blockade/interdiction effects still emerge from physical routing and combat consequences.

Strategic policy cannot resolve a shortage by changing a scalar.

## 7. Acceptance

Stage 19G acceptance requires:

1. identical inputs are deterministic regardless of objective-list ordering;
2. a visible offer cannot be accepted unless all unresolved mandatory objectives are explicitly granted;
3. observed completion of mandatory objectives de-escalates without a synthetic victory score;
4. an impossible mandatory objective seeks settlement rather than inventing victory;
5. insufficient physical reaction mass prevents continued escalation;
6. uncovered physical repair demand prevents continued escalation when policy requires repair coverage;
7. observed opponent physical losses may justify a coercive offer only through explicit physical thresholds;
8. the same physical universe may yield different decisions for actors with different observed evidence;
9. maximum represented escalation cannot increase beyond `GENERAL_WAR`;
10. strategic evaluation is read-only and grants no physical capability.

## 8. Transition to Stage 19H

Stage 19H owns persistence and aggregate warfare acceptance. It should persist the conflict's named objectives, escalation state and strategic decisions without duplicating physical economy/combat state, then prove deterministic save/load continuation across the full causal chain.
