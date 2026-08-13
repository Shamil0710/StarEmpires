# Stage 11A — Expansion Opportunity Model

Status: IMPLEMENTATION CANDIDATE.

Stage 11A converts the Stage-8 `EXPANSION` intent from an abstract demand modifier into an explainable spatial decision. It is deliberately read-only: it ranks candidate systems but does not create construction projects, move fleets or change territory.

## Inputs

The analyzer uses only state already authoritative in the simulation:

- faction controlled systems and diplomacy;
- Stage-10 weighted jump paths;
- live remaining `AsteroidComponent` resources;
- live market target-stock deficits and market count;
- data-driven station construction funding;
- faction treasury affordability;
- current target/neighbor territory control.

Strategic asteroid-field landmarks are not treated as resource value because they currently contain geometry but no resource composition. Resource opportunity comes from the real finite asteroid entities instead.

## Candidate horizon

For every system not already controlled by the evaluating faction, the analyzer chooses the fastest authoritative path from any controlled source system. Candidates beyond `maxJumpHops` are excluded. This keeps the expansion frontier bounded and makes connectivity a real cost.

Foreign-controlled systems may be ranked but receive an explicit policy penalty and expose their current controller in the result. Stage 11A does not conquer them; Stage 11D owns competition/conflict resolution.

## Anchor construction cost

The first model chooses the cheapest constructible station archetype native to the faction, falling back to any constructible archetype only when no native option exists. If the treasury cannot physically fund that anchor, no expansion opportunity is returned.

This does not create or reserve money. Stage 11B will persist the plan; Stage 11C will use the existing Stage-9 construction API for physical funding/material delivery.

## Explainable metrics

Every `ExpansionOpportunity` stores:

- source and target systems;
- current target controller;
- authoritative jump path;
- proposed anchor archetype and funding requirement;
- remaining mineable units;
- aggregate unmet market demand;
- market count;
- hostile-neighbor diplomatic pressure;
- final normalized utility score.

## Scoring

`ExpansionOpportunityPolicy` makes weights explicit. Raw metrics are normalized against the current bounded candidate set, then combined as resource, demand, market-network, proximity and construction-cost benefits. Hostile-neighbor pressure is subtracted and foreign control applies a separate basis-point penalty.

No random input is used. Equal results are ordered by utility, jump time and stable `StarSystemId`.

## Deferred

Stage 11A intentionally does not yet persist an expansion plan, reserve a support fleet, fund a project or change territory. Those belong to 11B/11C. Military threat strength beyond existing diplomacy remains a future combat-era signal rather than an invented hidden value.
