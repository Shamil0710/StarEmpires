# Star Empires — AI Behavior, Risk and Doctrine Roadmap

> Cross-cutting plan for civilian behavior, threat-aware routing, combat tactics, fleet coordination and faction doctrine.  
> Added: **2026-08-14**; synchronized with revised Stage 18–23 ordering on **2026-08-16**.  
> This document complements `docs/development_roadmap.md` and never creates an AI-only simulation.

---

## 1. Goal

AI decisions should emerge from authoritative state:

- current objective and economic opportunity;
- known danger and uncertainty;
- fitted ship capabilities;
- cargo, ammunition, reaction mass and damage;
- fleet composition and escort support;
- faction doctrine and risk tolerance;
- Stage-18 logistics/industrial dependencies;
- later commander/NPC preferences.

AI chooses **intent/commands**. Ordinary movement, trade, mining, production, combat, diplomacy and ownership systems apply the outcome.

---

## 2. Decision architecture

```text
observations / world events / intelligence
        ↓
Threat Intelligence / Danger Assessment
        ↓
Opportunity + Risk Utility
        ↓
Role + fitted capabilities + fleet + doctrine
        ↓
chosen strategic/tactical intent
        ↓
shared authoritative command APIs
        ↓
physical simulation / economy / diplomacy
```

No AI-only acceleration, free cargo, hidden resource supply, instant repair or omniscient map knowledge.

### 2.1 Threat intelligence

Danger information distinguishes:

- directly observed threats;
- recent local combat;
- faction/shared intelligence;
- known hostile fleet strength;
- piracy/ship-loss history;
- war/front/blockade state;
- stale intelligence whose confidence decays.

Persistent threat record should preserve location, source/type, affected relation context, estimated strength, event age, confidence/freshness, severity components and revision/version for cache invalidation.

### 2.2 Danger assessment

Do not store one permanent magic danger number. Preserve explanatory components and derive a scalar only when a planner needs comparison.

Possible components:

```text
combat activity
hostile strength
recent civilian losses
piracy risk
blockade/front intensity
route/chokepoint exposure
friendly security presence
safe-haven availability
intel freshness/confidence
```

### 2.3 Profit / danger utility

Conceptually:

```text
utility = expected objective/economic value
        - travel/time cost
        - expected danger cost
        - expected asset-loss cost
        - switching/commitment cost
```

Danger depends on actor-specific state: cargo/ship value, damage, mobility, defense, escort strength, doctrine, urgency and intelligence confidence.

### 2.4 Hysteresis

Use minimum commitment times, switching penalties, enter/exit thresholds, threat-memory decay and explicit emergency overrides. AI must not oscillate every simulation tick.

---

## 3. Civilian behavior

Civilian/economic ships should interrupt routine work under credible attack, evaluate ordinary escape/jump/safe-haven options, retain real cargo/damage state and later resume/replan/abandon their task.

Typical differences:

- unarmed freighter flees early;
- armed transport may return fire while disengaging;
- fast courier favors escape;
- protected convoy may continue when escort keeps risk below doctrine threshold;
- miner abandons extraction when continued work is unsafe.

### 3.1 Strategic route choice

Economic planners compare:

```text
short / profitable / dangerous
vs
longer / less profitable / safer
```

using current knowledge, ship/fleet capability and doctrine.

### 3.2 Physical economic consequences

```text
war/piracy raises known danger
→ civilian traffic reroutes
→ route throughput changes
→ Stage-18 resource/component deliveries change
→ inventories/production/shipyards feel shortage
→ prices and construction/repair times react
→ factions can escort, secure or build alternate infrastructure
```

No abstract wartime production penalty is needed when physical flows already explain the effect.

---

## 4. Tactical combat behavior

Advanced tactical AI consumes **Stage-17.5 capability APIs** rather than class names.

It may reason about:

- acceleration / braking / turning;
- delta-v / reaction mass;
- sensor/track quality;
- weapon engagement envelopes;
- ammunition and thermal endurance;
- shields/armor/subsystem damage;
- ECM/ECCM;
- target value;
- current fleet objective.

Reusable tactical intents may include:

```text
HOLD_OBJECTIVE
APPROACH
MAINTAIN_RANGE
OPEN_RANGE
BREAK_CONTACT
FLEE_TO_SAFETY
ESCORT
SCREEN
INTERCEPT
FOCUS_FIRE
PROTECT_ASSET
REGROUP
```

Military ships do not universally fight to destruction. Retreat depends on state, objective, escape route, cohesion, logistics and doctrine.

---

## 5. Fleet-level behavior

Fleet assessment should consider:

- combat capability by engagement domain;
- logistics/civilian assets being protected;
- escort/screen strength;
- damaged fraction;
- mobility of slowest critical unit;
- ammunition/reaction-mass/repair endurance;
- target priority;
- objective/retreat conditions;
- formation/cohesion.

Expected coordinated behavior includes convoy protection, screening, focus fire, range separation between roles, group withdrawal, cover for damaged units and bounded pursuit.

---

## 6. Faction doctrine

Faction differences come primarily from policy/doctrine, not invisible stat cheats.

A behavior profile can include:

- civilian risk tolerance;
- acceptable route danger;
- aggression / retreat threshold;
- preferred engagement-range bias;
- willingness to pursue;
- escort/convoy preference;
- focus-fire/cohesion preference;
- willingness to enter hostile territory;
- piracy response;
- protection priority for economic assets;
- acceptable expected economic/industrial loss for strategic objectives.

Stage 17 already supplies institutional doctrine/policy state; later AI consumes it through shared decision boundaries.

---

## 7. Stage-18 industrial awareness

The revised roadmap deliberately places **Resources / Industry / Infrastructure** before strategic warfare.

AI strategic decisions should therefore be able to value real economic dependencies, for example:

```text
water / reaction-mass source
bulk refinery
precision/electronics fab
ordnance plant
logistics depot
repair yard
capital shipyard
critical transport corridor
```

A strategic AI should not see all stations as equivalent `economicPower` points.

Useful derived planning questions:

- which facilities are bottlenecks for current fleet replacement?
- which route supplies ammunition/reaction mass?
- which resource source has no substitute within acceptable logistics radius?
- how much production loss follows from a blockade according to ordinary inventories and throughput?
- can damaged fleets physically reach a repair/replenishment base?

The answer may use bounded cached projections, but outcomes remain Stage-18 physical economy state.

---

## 8. Information, personality and Stage-21 NPC behavior

Stage 21 may add persistent commander/NPC preferences above faction doctrine:

```text
final preference = faction doctrine
                 + ship/fleet role
                 + commander traits/experience
                 + current objective/state
```

Traits never grant information the actor does not possess.

---

## 9. Observability and debugging

Important decisions should expose structured diagnostics:

- selected objective/target/route;
- candidate utilities;
- profit/objective estimate;
- danger estimate + contributing factors;
- logistics/industrial dependency considered;
- risk tolerance;
- reason for flee/retreat/continue;
- doctrine/profile;
- deterministic tie-break.

Diagnostics are development telemetry, not player omniscience.

---

## 10. Determinism and scalability

Requirements:

- stable candidate ordering;
- explicit tie-breaks;
- named RNG only for intentional stochastic behavior;
- equal authoritative state + knowledge + doctrine → equal decision unless named RNG participates;
- cached system/link danger with revision invalidation;
- bounded reevaluation cadence;
- full tactical reasoning only in active interaction domains;
- remote strategic reasoning preserves compatible losses, inventories, route risk and economic consequences.

Scalability architecture remains authoritative for AI cadence/LOD.

---

## 11. Current stage integration plan

### Stage 14 — First complete player loop

Keep only useful seams: combat/danger events, observability and minimal under-attack disengage behavior where needed.

### Stage 15 — Fleets / autonomous orders

First major civilian/fleet behavior phase:

- reusable profiles;
- flee/resume/replan;
- route utility combining profit/time/danger;
- escort-aware routing;
- bounded MOVE/TRADE/MINE/ESCORT/PATROL behavior;
- fleet cohesion foundations.

### Stage 16 — Construction / stations

Risk-aware logistics can reroute construction supply, request escorts and physically delay projects.

### Stage 17 — Player faction

Faction doctrine/policy influences civilian risk, escorts, retreat and strategic preferences through common commands/state.

### Stage 17.5 — Ship Fitting / Combat Depth

Provides stable physical capability APIs and is the gate before sophisticated weapon-aware tactical behavior.

### Stage 18 — Resources / Industry / Infrastructure

Provides the physical economic dependency graph that strategic AI will later protect, exploit or attack.

AI work in Stage 18 is limited to industrial/economic decision seams needed by ordinary economy. It is **not** the major warfare-AI stage.

### Stage 19 — Strategic Warfare / Advanced Combat Behavior

**Second major AI behavior phase.**

Implement:

- war/front/blockade threat fields;
- faction-shared intelligence + freshness/decay;
- tactical weapon/range/track-aware combat doctrine;
- fleet power/capability comparison;
- coordinated retreat/pursuit;
- convoy/screen/intercept behavior;
- danger-aware strategic route planning;
- civilian war-zone avoidance;
- military risk acceptance based on objective/doctrine;
- targeting/protection of real Stage-18 industrial/logistics assets;
- conflict → rerouting/blockade/loss → physical throughput/repair/replacement consequence validation.

### Stage 20 — Physical World Generation / Discovery

AI routing/intelligence consumes generated physical distance/topology and incomplete discovery. Generated geography must not be bypassed by AI-only route shortcuts.

### Stage 21 — RPG / Living World

Add commander/NPC preferences, missions and reputation reactions based on authoritative threat/economic/political state.

### Stage 22 — Content / Balance / Long-run Stability

Run large scenario matrices to detect:

- excessive civilian risk aversion;
- civilians ignoring obvious wars;
- engage/retreat oscillation;
- escorts abandoning convoys;
- all factions converging on one doctrine;
- permanent route avoidance;
- non-decaying danger;
- one dominant doctrine;
- AI ignoring industrial bottlenecks;
- pathological attacks on low-value facilities while critical logistics are exposed.

### Stage 23 — Polish / RC

Tune diagnostics, player-readable intent presentation, performance and final behavior regressions. No new foundational AI model.

---

## 12. Acceptance scenarios

At maturity, deterministic scenarios include:

1. **Civilian attacked:** unescorted freighter interrupts trade and physically escapes.
2. **Profit vs danger:** risk-averse and protected actors make different rational route choices.
3. **War-zone rerouting:** known conflict moves traffic and real Stage-18 throughput changes.
4. **Threat decay:** traffic returns as stale danger decays.
5. **Range doctrine:** different fitted weapon/mobility envelopes yield different tactics.
6. **Retreat:** damaged/outmatched fleet breaks contact according to doctrine.
7. **Escort behavior:** escorts prioritize protected assets over irrelevant pursuit.
8. **Faction differentiation:** different doctrines produce predictable valid decisions without physical bonuses.
9. **No omniscience:** unknown distant conflict causes no instant reroute.
10. **Industrial target value:** loss/blockade of a real bottleneck changes planning through ordinary supply consequences.
11. **Logistics endurance:** fleet with insufficient ammo/reaction-mass/repair support changes operation rather than receiving virtual replenishment.
12. **Determinism:** equal state + knowledge + doctrine yields stable decisions.

---

## 13. Design constraints

1. AI chooses commands; authoritative systems apply outcomes.
2. Civilian safety behavior affects real logistics/economy.
3. Risk retains explanatory components.
4. No omniscient danger knowledge without information source.
5. Ship/fleet/faction differences come from data/state, not class-name conditionals.
6. Hysteresis prevents constant replanning.
7. Player-owned autonomous fleets reuse equivalent AI planning components.
8. Remote simplification preserves compatible loss/inventory/economic consequences.
9. Industrial target valuation derives from Stage-18 physical dependencies, not arbitrary target scores alone.
10. AI cannot bypass Stage-18 production, Stage-20 geography or Stage-17 diplomatic/access law.