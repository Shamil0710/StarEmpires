# Star Empires — AI Behavior, Risk and Doctrine Roadmap

> Cross-cutting plan for civilian behavior, threat-aware routing, combat tactics, fleet coordination and faction doctrine.
>
> Added: **2026-08-14** after Stage 13 established the first shared deterministic combat pipeline.
>
> This document complements `docs/development_roadmap.md`. It does not create a second AI-only simulation: AI decisions must continue to execute through the same physical movement, travel, trade, mining, combat, destruction and ownership rules used elsewhere.

---

## 1. Goal

AI should not behave as one generic script with different stats. Its decisions should emerge from:

- current objective and economic opportunity;
- known danger and uncertainty;
- ship hull, shields, mobility, cargo and equipment;
- weapon range/profile and current combat capability;
- fleet composition and escort support;
- faction doctrine and risk tolerance;
- later, commander/NPC preferences where persistent identity matters.

Two ships observing the same situation should be allowed to make different rational choices because their roles, equipment, cargo, escorts or faction doctrine differ.

Examples:

- an unarmed freighter attacked by raiders should abandon its trade routine and flee toward safety;
- a valuable convoy may accept a moderately dangerous route when protected by strong escorts;
- a lightly armed independent trader may take a longer route around an active war zone even when the direct route is more profitable;
- a long-range missile ship should try to preserve range rather than behave like a short-range brawler;
- a damaged combat group should be able to break contact instead of fighting until every ship is destroyed;
- an aggressive militarist faction and a conservative mercantile faction should evaluate the same danger differently without hidden combat bonuses.

---

## 2. Architectural model

The intended decision stack is:

```text
world events / observations
        ↓
Threat Intelligence / Danger Assessment
        ↓
Opportunity + Risk Utility Scoring
        ↓
Role / Ship / Fleet / Faction Doctrine
        ↓
chosen intent or tactical state
        ↓
shared authoritative command APIs
        ↓
movement / jump / trade / mine / combat systems
```

The AI chooses **intent**. It does not directly mutate physical outcomes.

### 2.1 Threat intelligence rather than omniscience

AI should not automatically know every combat event in the galaxy.

Danger information should eventually distinguish:

- directly observed threats;
- recent combat known in the current system;
- faction/shared intelligence;
- known hostile fleet strength;
- piracy or repeated ship-loss history;
- formal war/front/blockade state;
- stale information whose confidence decays over time.

A strategic threat record should preserve at least:

- location: system and, when useful, route/jump link;
- source/type of threat;
- affected faction/relation context;
- estimated hostile strength;
- event age;
- confidence/freshness;
- severity components;
- revision/version for cached route decisions.

This prevents a civilian ship on the far side of the galaxy from reacting instantly to information it could not reasonably possess.

### 2.2 Danger assessment

Do not reduce the entire threat model to one permanently stored magic number. Preserve explanatory components, then derive a scalar when a decision requires comparison.

Possible components:

```text
combat activity
hostile strength
recent civilian losses
piracy risk
blockade/front intensity
route/chokepoint exposure
friendly security presence
station/safe-haven availability
intel freshness/confidence
```

The resulting `DangerAssessment` may expose a normalized danger score for routing/UI while retaining the underlying reasons for debugging and later balancing.

### 2.3 Profit / danger utility

Civilian and economic AI should evaluate opportunities approximately as:

```text
utility = expected economic/objective value
        - travel/time cost
        - expected danger cost
        - expected asset-loss cost
        - opportunity-switching cost
```

Danger cost should depend on the actor, not only the system.

For example:

```text
expected danger cost = danger
                     × route exposure
                     × actor risk aversion
                     × vulnerable asset value
                     × probability-of-loss estimate
                     × intel confidence
```

Actor-specific inputs may include:

- cargo value;
- ship replacement value;
- current hull/shield condition;
- speed and escape capability;
- defensive equipment;
- combat capability;
- escort strength;
- fleet size/composition;
- faction risk tolerance;
- urgency/importance of the current objective.

Therefore a route can be unacceptable to an unescorted freighter but acceptable to an escorted military supply convoy.

### 2.4 Hysteresis and decision stability

AI must not oscillate every tick when profit or danger crosses a threshold by a tiny amount.

Use where appropriate:

- minimum commitment time;
- route/target switching penalty;
- danger enter/exit hysteresis;
- threat-memory decay;
- cooldown before reevaluation after an accepted route/order;
- explicit emergency conditions that may override commitment immediately.

---

## 3. Civilian behavior

Civilian ships should prioritize survival differently from combat ships.

### 3.1 Immediate response to attack

A civilian or economically tasked ship under credible attack should be able to:

1. interrupt or suspend its current trade/mining/logistics task;
2. stop attempting routine docking/trade/mining actions while actively threatened;
3. evaluate local escape vectors / jump availability / nearby safe station;
4. request escape through ordinary movement/jump APIs;
5. retain its real cargo and damage state while fleeing;
6. resume, replan or abandon the original task after danger falls below a stable threshold.

The exact response depends on capability:

- unarmed freighter: flee very early;
- armed transport: may return fire while disengaging;
- fast courier: favor escape speed;
- heavily escorted convoy: may continue if escorts keep threat below doctrine threshold;
- miner: abandon asteroid and seek safety before resuming production.

### 3.2 Strategic conflict avoidance

Economic route planners should eventually include risk in addition to profit/time/access.

A civilian ship should be able to choose:

```text
short/high-profit/high-danger route
vs
longer/lower-profit/safer route
```

based on its own risk profile.

Relevant factors include:

- recent combat in candidate systems;
- hostile/pirate presence;
- war or blockade state;
- recent friendly/civilian losses;
- route alternatives;
- cargo margin and cargo value;
- ship value and current damage;
- escort availability;
- expected delay from rerouting;
- faction policy.

### 3.3 Economic consequences

Threat-aware civilian behavior must affect the living economy physically.

Examples:

```text
war zone becomes dangerous
→ civilian traffic reroutes
→ throughput through the region falls
→ stations receive fewer inputs
→ shortages and prices rise
→ safer routes become busier/more profitable
→ factions may allocate escorts/security or build alternate infrastructure
```

This is preferable to applying an abstract wartime production penalty detached from actual ship movement.

---

## 4. Tactical combat behavior

Stage 13 proves only the shared attack/damage pipeline. Later combat AI should choose tactics from the actual combat envelope.

### 4.1 Weapon-aware behavior

Tactical decisions should inspect weapon characteristics such as:

- minimum / preferred / maximum effective range;
- burst/cooldown behavior;
- projectile speed where projectiles exist;
- tracking/accuracy or target-class effectiveness when introduced;
- ammunition/energy constraints when introduced;
- firing arcs / hardpoints when introduced;
- area damage or friendly-fire risk when introduced.

Examples:

- long-range weapons: maintain or open distance, kite where mobility permits;
- short-range high-DPS weapons: close aggressively when survival odds allow;
- slow heavy weapons: prefer large/slow targets and favorable firing windows;
- missile/limited-ammo platforms: avoid wasting ammunition on low-value targets;
- point-defense/screen ships: stay near protected assets instead of chasing arbitrary enemies.

### 4.2 Ship-model and equipment awareness

A ship's behavior should account for:

- acceleration/top speed/turning capability;
- shield vs hull durability;
- current damage;
- weapon layout and effective range bands;
- cargo or mission importance;
- repair/ammunition/energy state where implemented;
- specialized equipment such as mining gear, sensors, ECM or defensive systems when those mechanics exist.

The same weapon profile on a slow armored ship and on a fast fragile ship should not necessarily yield the same tactic.

### 4.3 Tactical states/actions

Do not hard-code one monolithic behavior tree. A practical target is a hierarchical utility/state model with reusable actions, for example:

```text
HOLD_OBJECTIVE
APPROACH
MAINTAIN_RANGE
KITE / OPEN_RANGE
BREAK_CONTACT
FLEE_TO_SAFETY
ESCORT / SCREEN
INTERCEPT
FOCUS_FIRE
PROTECT_ASSET
REGROUP
```

Utility chooses the state/intention; ordinary movement and combat systems execute it.

### 4.4 Retreat and survival

Military ships should not always fight to destruction.

Retreat decisions may depend on:

- own hull/shields;
- local friendly vs hostile combat power;
- objective importance;
- escape route availability;
- fleet cohesion;
- remaining ammunition/energy where applicable;
- faction doctrine;
- whether protected civilians/logistics still require cover.

A sacrificial last stand may be a valid doctrine choice, but must not be the universal default.

---

## 5. Fleet-level behavior

Once Stage 15 introduces persistent player/AI fleet orders, tactical decisions should consider the group rather than every ship behaving independently.

Fleet assessment should eventually include:

- combined combat strength by range band;
- number and value of civilian/logistics assets;
- escort/screen strength;
- damaged fraction;
- mobility of the slowest critical ship;
- target priority;
- objective and retreat conditions;
- formation/cohesion needs.

Potential coordinated behavior:

- escorts remain near convoy rather than chasing weak targets indefinitely;
- screens intercept enemies approaching valuable transports/carriers;
- compatible ships focus fire;
- long-range and close-range elements maintain different preferred distances;
- fleet retreats as a group when doctrine threshold is exceeded;
- damaged ships disengage while escorts cover them;
- pursuit stops when it would expose the protected objective or enter unacceptable danger.

---

## 6. Data-driven faction doctrine

Faction differences should primarily come from **policy and doctrine**, not invisible statistical cheats.

A future data-driven `FactionDoctrine`/behavior profile may contain parameters such as:

- civilian risk tolerance;
- acceptable route danger;
- aggression;
- retreat threshold;
- preferred engagement range bias;
- willingness to pursue;
- escort preference;
- convoy size preference;
- focus-fire tendency;
- formation/cohesion preference;
- willingness to enter hostile territory;
- response to piracy;
- protection priority for civilian/economic assets;
- acceptable expected economic loss for strategic objectives.

Example archetypes:

- mercantile faction: strong danger aversion for civilian traffic, high escort investment, early retreat;
- militarist faction: accepts greater combat exposure and strategic losses for objectives;
- frontier/mining faction: tolerates moderate remote-system risk but strongly protects ore logistics;
- pirate/raider faction: prefers isolated valuable targets and disengages from superior organized response.

These are examples, not mandatory final faction personalities.

---

## 7. Information, personality and later NPC behavior

Stage 20 can add persistent commander/NPC preferences above faction doctrine:

```text
final preference = faction doctrine
                 + ship/fleet role
                 + commander traits/experience
                 + current objective/state
```

A cautious captain and an aggressive captain from the same faction may therefore differ without violating the faction's broad strategic identity.

Personal traits must not grant knowledge the character does not possess. Decision inputs remain bounded by available observations/intelligence.

---

## 8. Observability and debugging

Complex AI is impossible to balance if its reasoning is invisible.

Important AI decisions should expose structured diagnostics such as:

- selected objective/target/route;
- candidate utilities;
- profit estimate;
- danger estimate and major contributing factors;
- risk tolerance used;
- reason for flee/retreat/continue decision;
- reason for route abandonment;
- doctrine/profile applied;
- deterministic tie-break reason.

These diagnostics are development/telemetry state, not necessarily permanent player-visible omniscient information.

Where useful, player UI may later present appropriate in-world summaries such as "route dangerous", "convoy rerouting", "fleet retreating" or known danger overlays.

---

## 9. Determinism, performance and simulation levels

### Determinism

- stable candidate ordering;
- explicit tie-breaks;
- named RNG only for intentionally stochastic doctrine/personality behavior;
- repeated equal state must produce equal decisions unless explicit RNG is part of the rule.

### Performance

Threat-aware routing and fleet tactics must remain bounded.

Prefer:

- cached per-system/per-link danger assessments with revision invalidation;
- reevaluation intervals rather than scoring the entire galaxy every fixed tick;
- local tactical decisions at full fidelity only where the simulation level supports it;
- reduced remote strategic combat/risk decisions that preserve compatible economic consequences.

Remote simulation may reduce tactical detail, but must not create a separate incompatible model where civilian losses, route risk or economic throughput mean something fundamentally different.

---

## 10. Stage integration plan

### Stage 14 — First complete player loop

Do not expand Stage 14 into the full AI rewrite. Establish only useful seams discovered during playable testing:

- preserve combat/danger events in a form that later threat intelligence can consume;
- expose enough combat state/reasons to diagnose civilian and combat AI behavior;
- if the playable scenario requires civilian survival behavior, implement the smallest shared "under attack -> disengage/flee" slice without adding full strategic routing doctrine.

### Stage 15 — Fleets and autonomous orders

**First major AI behavior phase.**

Add:

- reusable fleet/ship behavior profiles;
- civilian task interruption and flee/resume/replan behavior;
- route utility that combines economic profit/time with danger cost;
- escort-aware willingness to enter risky systems;
- bounded risk-aware MOVE/TRADE/MINE/ESCORT/PATROL decisions;
- fleet-level objective/cohesion foundations.

This is the natural point for civilian ships to stop treating every economically valid route as equally safe.

### Stage 16 — Construction / station ownership

Risk-aware logistics begins affecting construction supply:

- construction convoys may reroute or request escort;
- dangerous supply corridors can delay projects physically;
- station security/safe-haven context may influence logistics behavior.

### Stage 17 — Player faction

Introduce data-driven faction doctrine/profile configuration and apply it consistently to civilian routing, escort preference, retreat thresholds and strategic risk tolerance.

### Stage 18 — Strategic warfare / territory / politics

**Second major AI behavior phase.**

Complete conflict-aware behavior:

- war/front/blockade threat fields;
- faction-shared intelligence and freshness/decay rules;
- tactical weapon/range-aware combat doctrine;
- fleet power comparison and coordinated retreat/pursuit;
- protected convoy/screen/intercept behavior;
- danger-aware strategic route planner/executor;
- war-zone avoidance by civilian traffic;
- military willingness to accept risk based on objective value/doctrine;
- measurable conflict -> rerouting -> throughput -> shortage/price effects.

### Stage 19–20 — Exploration / NPC / missions

- uncertainty from incomplete exploration/intelligence;
- commander/NPC risk/aggression preferences where persistent identity matters;
- missions such as escort, patrol, recon and bounty arise from actual threat state.

### Stage 21 — Content / balance / long-run stability

Run large scenario matrices to tune and prevent pathological behavior:

- civilians never travel because risk aversion is too high;
- civilians ignore obvious war zones because profit dominates too strongly;
- fleets oscillate between engage/retreat;
- escorts chase targets and abandon convoys;
- all factions converge on identical doctrine;
- permanent route avoidance kills economies with no recovery path;
- danger fields never decay after conflict ends;
- one faction snowballs because its doctrine accidentally dominates every situation.

---

## 11. Acceptance scenarios

At minimum, mature AI should eventually pass deterministic scenario tests like:

1. **Civilian attacked:** an unescorted freighter interrupts trade and attempts physical escape rather than continuing to dock/trade under fire.
2. **Profit vs danger:** two routes exist; a risk-averse civilian takes the safer route while a sufficiently protected/high-value convoy rationally accepts the riskier route.
3. **War-zone rerouting:** conflict raises known danger, traffic moves to alternate routes, and real economic throughput changes.
4. **Threat decay:** after hostilities cease and intelligence ages, civilian traffic gradually returns instead of avoiding the system forever.
5. **Long-range vs brawler:** ships with different weapon envelopes choose different preferred ranges while using the same authoritative combat pipeline.
6. **Retreat:** a damaged/outmatched fleet breaks contact according to doctrine rather than universally fighting to destruction.
7. **Escort behavior:** escorts prioritize protection of the convoy and do not chase an irrelevant fleeing enemy indefinitely.
8. **Faction differentiation:** two otherwise comparable fleets with different doctrines make predictably different but valid decisions.
9. **No omniscience:** an actor without knowledge of a distant new conflict does not reroute until the information becomes available through an allowed intelligence path.
10. **Determinism:** equal state + equal knowledge + equal doctrine produces equal decisions and stable tie-breaks.

---

## 12. Design constraints

1. AI chooses commands; authoritative simulation systems apply physical outcomes.
2. Civilian safety behavior must affect real logistics/economy rather than only animation.
3. Risk scoring retains explanatory components even when planners compare a scalar utility.
4. No global omniscient danger knowledge without an explicit information source.
5. Ship type, equipment, weapons, fleet composition and faction doctrine influence decisions through data/state, not scattered class-name conditionals.
6. Emergency response may override ordinary economic commitment, but normal replanning uses hysteresis to prevent oscillation.
7. Player-owned autonomous fleets should reuse the same behavior/planning components as equivalent AI fleets wherever possible.
8. Remote simulation may simplify tactics but must preserve compatible loss, danger and economic-throughput consequences.
