# Stage 9B — Persistent Construction Project

**Status:** implementation / verification in progress

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

`CANCELLED` is allowed only before any construction material is delivered. This deliberate first policy makes cancellation conservation-safe without inventing a material salvage/refund destination. Later designs can expand it explicitly.

## Physical construction site

A non-terminal project owns an ordinary local Ashley Entity with:

- `TransformComponent`;
- empty `InventoryComponent` sized to the total requirements;
- empty `WalletComponent`;
- `MarketComponent` whose target stocks equal the required construction materials;
- owner `FactionComponent`;
- `PriceHistoryComponent`.

The site is created through the Stage-9A lifecycle boundary while economically empty.

This is important: project demand is visible to the existing market/trade stack. A normal trader can discover the site as a consumer, sell required steel/energy through the ordinary bilateral trade path, and receive real project money. There is no virtual construction-delivery formula.

## Data-driven construction definitions

Production station archetypes now declare a `construction` object in `catalog-v1.json`:

```json
{
  "fundingCredits": 40000.0,
  "buildSeconds": 35.0,
  "materials": {
    "item.steel": 180,
    "item.energy": 120
  }
}
```

The definition participates in the content fingerprint. A station archetype without this section remains loadable for narrow legacy/test catalogs but is explicitly not constructible through Stage 9B.

## Funding and material movement

Funding is a real atomic transfer:

```text
faction treasury → construction-site wallet
```

and is recorded as `MONEY_TRANSFER` in the local economic ledger.

Manual/owner delivery physically moves units from a local source entity inventory to the same site inventory. Ordinary TradeAI delivery instead uses the existing trade transaction and spends site wallet liquidity.

## Completion

Once requirements are fulfilled:

1. project enters `BUILDING` at the target-system clock tick;
2. active or remote simulation advances the same target clock;
3. after `buildDurationTicks`, required goods are consumed from site inventory;
4. each consumed material is recorded as a `RESOURCE_SINK` with a station-construction reason because the tradable commodity pool has been transformed into a persistent station asset;
5. unused project money is returned to owner treasury with `MONEY_TRANSFER` accounting;
6. the now-empty construction site is structurally removed through Stage 9A;
7. `ArchetypeEntityFactory.createConstructedStation(...)` creates a finished station with the target market/production/archetype metadata but **zero initial goods and zero magic starting credits**;
8. that empty station enters the simulation through Stage-9A lifecycle create;
9. project becomes `COMPLETED` and persists its completed station ID.

Bootstrap `startingCredits`/`initialStock` remain bootstrap semantics only and are not reused as construction rewards.

## Persistence

`WorldState` schema advances from v3 to v4 and stores:

- `nextConstructionProjectIdValue`;
- ordered `ConstructionProjectState` records.

WorldState v3 migrates neutrally to v4 with no projects and allocator watermark `1`. The binary codec includes bounded project/material counts and terminal/non-terminal entity references.

Runtime restore validates project state against the physical construction-site inventory/wallet and target station/content definitions.

## Verification target

The Stage-9B PR is not mergeable until exact-head Java 17 CI proves:

- project creation and deterministic IDs;
- treasury funding transfer;
- partial delivery persistence;
- v4 binary round-trip;
- restore validation;
- active and remote target systems;
- cancellation refund before delivery;
- material fulfillment → BUILDING → COMPLETED;
- construction material ledger sinks;
- construction site removal;
- finished station has no bootstrap money/stock source;
- money/resource conservation invariants.
