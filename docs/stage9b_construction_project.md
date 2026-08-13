# Stage 9B — Persistent Construction Project

**Status:** COMPLETE

## Core model

A construction project is a persistent world-level object rather than a finished station spawned by a timer.

Each project owns:

- stable `ConstructionProjectId` allocated at world level;
- owner faction content ID;
- target station archetype;
- target `StarSystemId` and position;
- physical construction-site `EntityId` while non-terminal;
- data-driven required/delivered materials;
- minimum project funding and current physical project-wallet balance;
- target-system build duration in fixed ticks;
- deterministic state machine and transition timestamps;
- completed station ID after success.

State machine:

```text
PLANNED
  ↓ full minimum funding
FUNDED
  ↓ target-system tick advances
AWAITING_MATERIALS
  ↓ physical materials fulfilled
BUILDING
  ↓ buildDurationTicks
COMPLETED
```

`CANCELLED` is allowed only before any construction material is delivered. This first policy is conservation-safe without inventing a material salvage/refund destination.

## Physical construction site

A non-terminal project owns an ordinary local Ashley market entity with empty inventory/wallet, market targets equal to required materials, owner faction and price history. It is created through Stage 9A while economically empty.

`MarketDirectory` indexes a funded site as a normal consumer, so existing TradeAI can discover it and sell required steel/energy using the ordinary bilateral trade path. There is no virtual construction-delivery formula.

The site is not a completed station for Stage-8 fiscal policy: it is excluded from generic station-liquidity subsidy, station tax and foreign-territory station tariff. Construction liquidity enters only through explicit project funding and is spent through real trades.

## Data-driven construction definitions

Production station archetypes declare `construction` requirements in `catalog-v1.json`, including minimum funding, build seconds and material amounts. These values participate in the semantic content fingerprint. Current production requirements deliberately reuse existing steel + energy rather than expanding content prematurely.

## Funding, delivery and completion

Funding is a real atomic transfer:

```text
faction treasury → construction-site wallet
```

with `MONEY_TRANSFER` ledger accounting. Manual owner delivery physically moves inventory units; ordinary TradeAI delivery uses normal market trade and spends site liquidity.

On completion:

1. all required materials must physically exist at the site;
2. `BUILDING` advances on the target-system clock, including remote coarse simulation;
3. required goods become explicit `RESOURCE_SINK` entries because tradable commodities are transformed into the station asset;
4. unused project money returns to the owner treasury;
5. the empty site is removed through Stage 9A;
6. the finished station is created through Stage 9A with target archetype/market/production metadata but **zero bootstrap stock and zero magic starting credits**;
7. project persists `COMPLETED` with the completed station ID.

## Persistence

`WorldState` advances from v3 to v4 and stores construction-project allocator watermark plus ordered project states. v1/v2/v3 legacy worlds migrate to v4 without invented projects or economic value. The codec bounds project/material counts and validates site/station references. Runtime restore verifies persisted project wallet/material state against the physical site.

## Verification

Automated coverage includes:

- project creation and stable IDs;
- data-driven construction requirements and fingerprint sensitivity;
- treasury funding transfer;
- partial delivery persistence;
- v4 binary round-trip and v1/v2/v3 migration;
- restore validation;
- site discovery through existing `MarketDirectory` consumer path;
- site exclusion from completed-station fiscal/subsidy policy;
- active and remote target-system progression;
- cancellation refund before delivery;
- explicit rejection of cancellation after first physical delivery;
- material fulfillment → BUILDING → COMPLETED;
- construction material ledger sinks;
- construction-site removal;
- finished station with no bootstrap money/stock source;
- money/resource conservation boundaries.

Before roadmap finalization, exact-head Java 17 CI passed all 324 tests, coverage, strict Javadoc and desktop packaging. The final branch checkpoint repeats the same gate after recording Stage 9B completion.

## Consequence

Stage 9B is complete. Stage 9C — Destruction and Economic Shock — is the next active substage.
