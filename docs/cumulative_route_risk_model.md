# Star Empires — Cumulative Route Risk Model

> Cross-cutting design for threat-aware route selection across the entire traveled path rather than only the destination system.
>
> Added: **2026-08-14**. Complements `docs/ai_behavior_roadmap.md` and `docs/flight_dynamics_and_combat_depth_roadmap.md`.

---

## 1. Core decision

A ship or fleet must never evaluate route danger from the destination system alone.

The relevant strategic quantity is **cumulative route exposure** across every system and jump/link traversed from origin to destination.

A route such as:

```text
Safe origin
→ low-risk system
→ active war zone
→ pirate chokepoint
→ safe destination
```

is not a safe route merely because the destination is safe.

The route planner must compare whole-path economic value, travel cost and accumulated danger before accepting a route.

---

## 2. Route danger is actor-specific

The world may expose shared threat information for systems and jump links, but the final risk cost belongs to the ship or fleet considering the route.

The same physical path may be:

- unacceptable to an unarmed freighter carrying expensive cargo;
- acceptable to a fast courier with low-value cargo;
- acceptable to a protected convoy;
- deliberately chosen by a military supply fleet whose strategic objective outweighs the danger.

Therefore the route planner should separate:

```text
world threat state
×
route exposure
×
actor vulnerability / doctrine
=
actor-specific route risk
```

---

## 3. Whole-route accumulation

For a candidate route containing systems `S1 ... Sn` and jump links `L1 ... Ln-1`, the first practical model should be conceptually equivalent to:

```text
routeExposure = Σ systemExposure(Si)
              + Σ linkExposure(Lj)
```

where each segment exposure is not merely its raw danger score.

A useful first-pass form is:

```text
systemExposure = knownSystemDanger
               × expectedTimeExposed
               × intelConfidence
               × actorVulnerability

linkExposure   = knownLinkDanger
               × expectedTransitExposure
               × intelConfidence
               × actorVulnerability
```

This ensures that several moderately dangerous systems can collectively become less attractive than one short dangerous segment, and that route length itself can increase accumulated exposure.

---

## 4. Sum of danger versus compounded loss probability

Two related measures should remain conceptually distinct.

### 4.1 Exposure score

A weighted sum is useful for deterministic route comparison, diagnostics and UI:

```text
cumulativeExposure = Σ weightedDangerOfEveryTraversedSegment
```

This is appropriate even while danger values are heuristic severity scores rather than calibrated probabilities.

### 4.2 Probability of loss

If later simulation data becomes good enough to estimate a probability of serious interception/loss per segment, total route risk should not be computed by simply adding probabilities.

Conceptually:

```text
routeSurvivalProbability = Π (1 - segmentLossProbability)
routeLossProbability     = 1 - routeSurvivalProbability
```

This naturally captures compounding risk across long routes.

Example:

```text
three independent 10% serious-loss segments

survival = 0.9 × 0.9 × 0.9 = 0.729
loss risk ≈ 27.1%
```

The implementation does not need calibrated probabilities in the first version. The architecture should simply avoid baking in an assumption that one destination danger value is sufficient forever.

---

## 5. Segment danger sources

System and link assessments may eventually include explanatory components such as:

- recent combat activity;
- known hostile fleet strength;
- piracy frequency;
- recent civilian/friendly losses;
- formal war/front status;
- blockade state;
- jump-gate/chokepoint ambush exposure;
- distance from friendly security;
- nearby safe station or refuge availability;
- sensor/intelligence confidence;
- age of the information;
- repeated historical losses on this route.

A jump link may therefore be dangerous even when both endpoint systems are relatively safe.

Example:

```text
safe system A
→ heavily raided jump corridor
→ safe system B
```

The route model must be able to price that corridor risk directly.

---

## 6. Time exposure matters

Danger should be weighted by how long the actor is exposed.

A fast ship and a slow loaded freighter traversing the same region should not necessarily receive the same route risk.

Relevant travel/exposure inputs include:

- strategic jump/transit duration;
- local travel required to reach a gate or station;
- docking/queue exposure if later modeled;
- acceleration and braking envelope;
- convoy speed limited by the slowest critical ship;
- detours needed to avoid local threats.

This ties route intelligence to the shared physical mobility model instead of treating strategic risk as unrelated metadata.

---

## 7. Cargo mass and inertia increase strategic risk naturally

Once the shared mass/inertia model exists, a loaded freighter should become more vulnerable without receiving a special arbitrary `loadedDangerPenalty`.

The causal chain should be:

```text
more/heavier cargo
→ greater total mass
→ weaker acceleration / longer braking
→ poorer escape capability
→ more time exposed / worse interception survival
→ higher actor-specific expected route loss
```

Cargo also increases the economic value at risk:

```text
expectedLossCost ≈ routeLossProbability
                 × (shipReplacementValue + cargoValue + missionValue)
```

Thus a full ore hauler may rationally avoid a route that the same hull would accept while empty.

---

## 8. Escort and fleet composition

Fleet risk cannot be reduced to the danger score of the lead ship.

The route evaluator should eventually consider:

- combat strength of escorts;
- protected civilian/logistics value;
- speed of the slowest protected vessel;
- damaged ships;
- formation/cohesion requirements;
- ability to disengage as a group;
- whether escorts are appropriate for the expected threat type;
- faction willingness to sacrifice escorts for the cargo/objective.

A strong escort may reduce the estimated probability or expected cost of interception, but it should not make danger disappear.

A route through overwhelming hostile strength may remain irrational even for an escorted convoy.

---

## 9. Route utility

Economic and civilian planners should compare candidate paths using the whole route.

Conceptually:

```text
routeUtility = expectedProfitOrObjectiveValue
             - travelTimeCost
             - cumulativeDangerCost
             - expectedAssetAndCargoLoss
             - routeSwitchingCost
```

where:

```text
cumulativeDangerCost = f(
    every traversed system,
    every traversed jump/link,
    expected exposure time,
    actor mobility,
    cargo/asset value,
    escort strength,
    faction doctrine,
    intelligence freshness
)
```

The planner should therefore be able to prefer:

```text
Route A
3 systems
high profit
one severe war-zone chokepoint

Route B
5 systems
lower profit
low/moderate danger throughout
```

and choose differently for different actors.

---

## 10. Hard constraints and utility penalties

Not every danger decision must be a soft utility trade-off.

Faction/actor doctrine may define both:

- **soft risk cost** — danger reduces route utility;
- **hard rejection thresholds** — some known conditions make a route invalid unless an explicit strategic override exists.

Examples of possible hard conditions:

- civilian route crosses a confirmed active blockade with no escape path;
- known hostile fleet strength exceeds a doctrine-specific multiple of escort strength;
- damaged civilian vessel lacks sufficient expected survival probability;
- faction policy forbids civilian transit through declared enemy territory.

Military or emergency missions may explicitly override civilian hard thresholds while still recording the expected loss cost.

---

## 11. Intelligence freshness and uncertainty

Whole-route risk must use only information available to the actor/faction.

Each segment assessment should therefore carry or derive:

- information source;
- timestamp/age;
- confidence;
- uncertainty;
- revision/version for cached route results.

Stale information should not vanish instantly. Confidence and danger influence may decay over time according to the information model.

Unknown does not necessarily mean safe.

Doctrine may distinguish:

```text
known safe
known dangerous
unknown / poorly observed
```

A cautious merchant faction may apply an uncertainty premium to poorly known routes, while an explorer or smuggler may tolerate it.

---

## 12. Replanning during travel

A route accepted at departure may become irrational later.

Reevaluation triggers may include:

- new combat report in a remaining system;
- blockade/front state changed;
- escort destroyed or heavily damaged;
- ship propulsion damage;
- cargo value changed;
- alternative route becomes available;
- threat information becomes stale or is contradicted;
- current route segment becomes physically inaccessible.

The planner should recompute **risk over the remaining path**, not restart from an abstract destination-only danger value.

Use hysteresis/switching cost so a convoy does not oscillate between two nearly equal routes whenever a danger score changes slightly.

Emergency threat may override normal hysteresis.

---

## 13. Route risk should have economic consequences

Because civilian routing uses real physical travel, cumulative risk should create emergent economic geography.

Example:

```text
war begins in central corridor
→ cumulative exposure of routes through corridor rises
→ civilian trade chooses longer bypasses
→ travel time and shipping cost rise
→ corridor throughput falls
→ local shortages and price spreads increase
→ alternate systems gain traffic
→ demand for escorts/security increases
→ factions may protect corridor or build alternate infrastructure
```

No abstract global wartime production penalty is required to create these effects.

---

## 14. Player-facing use

The same route-risk model can later support the global map without exposing information the player does not know.

Useful known-information presentation may include:

- per-system danger;
- dangerous jump links/chokepoints;
- cumulative estimated risk for the selected route;
- major risk contributors;
- intelligence freshness;
- route alternatives such as `fastest`, `cheapest`, `safest`, or a balanced profile;
- warnings that a route became materially more dangerous after it was planned.

The UI is read-only over the authoritative danger/route model and submits ordinary route commands.

---

## 15. Stage integration

### Stage 14

No full implementation required. Preserve the seams needed to observe combat/loss events and actor mobility.

### Stage 15

Implement the first practical cumulative route-risk layer together with autonomous fleet orders and civilian behavior:

- evaluate all systems/links on candidate routes;
- actor-specific risk tolerance;
- profit/time/risk route utility;
- civilian rerouting;
- escort-aware route evaluation;
- deterministic candidate/tie-break behavior.

### Stage 17

Faction doctrine provides risk tolerance, uncertainty handling, enemy-territory rules and strategic overrides.

### Stage 18

War/front/blockade state becomes a major danger input; route-risk changes must produce measurable traffic and economic consequences.

### Stage 19–20

Exploration/intelligence quality affects confidence and unknown-route treatment; missions can explicitly request dangerous routes, escort or reconnaissance.

### Stage 21

Balance cumulative risk over long simulations so AI neither ignores danger nor permanently freezes inter-system trade.

---

## 16. Acceptance scenarios

At minimum, mature route-risk behavior should pass deterministic scenarios such as:

1. **Safe destination / dangerous path:** destination danger is low, but a war-zone intermediate system causes a risk-averse freighter to reject or reroute.
2. **Accumulation:** several moderately dangerous systems collectively make a long route less attractive than a shorter safer alternative.
3. **Dangerous link:** two safe systems connected by a high-risk chokepoint are not treated as a safe path.
4. **Loaded vs empty:** the same freighter can rationally choose different routes when loaded because cargo value/mass changes exposure and expected loss.
5. **Escort effect:** adding capable escorts can make a previously unacceptable route acceptable without reducing its underlying world danger to zero.
6. **Convoy slowest ship:** route exposure uses the mobility of the protected convoy, not the fastest escort.
7. **Mid-route replan:** a new threat in a remaining system causes deterministic reevaluation of the remaining path.
8. **Threat decay:** after conflict information ages and conditions improve, cumulative route risk falls and traffic can return.
9. **No omniscience:** an actor does not account for a conflict it has no valid information about.
10. **Economic consequence:** rerouting changes physical throughput and produces observable downstream supply/price effects.

---

## 17. Design constraints

1. Evaluate the complete traversed route, never destination danger alone.
2. Include both system and jump/link danger where supported by world topology.
3. Weight danger by expected exposure time and actor-specific vulnerability.
4. Keep raw threat components available for diagnostics; do not persist only one opaque scalar.
5. Cargo, ship value, damage, mobility, escort strength and doctrine may change route risk without changing world danger itself.
6. Use calibrated probability compounding only when probability estimates are meaningful; weighted exposure scores remain valid for earlier heuristic stages.
7. Unknown information is distinct from known safety.
8. Replan over the remaining route when material new information arrives, with hysteresis to prevent oscillation.
9. Player and AI route views must use the same authoritative risk data available to each actor, respecting knowledge limits.
10. Route danger must feed real travel/logistics decisions so conflict can alter the economy through physical traffic patterns.