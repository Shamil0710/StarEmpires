# Stage 23B independent-pilot opening profile v1

Status: IMPLEMENTED SLICE / 23B ACTIVE/PARTIAL. Exact-head verification is recorded on PR #413.

Profile: `se-pilot-start-1`; canonical SHA-256: `ffa51e9b33e364b8bc360fd7c5edcda8415465bab842c5527fe32548e2bca675`.
This is an explicitly confirmed new-game initial condition after the unchanged Stage-20 generator.
It never runs during decode, restore, travel or ordinary economy ticks. Character creation must
finish before Save becomes available in the fresh client; F8 cannot overwrite a prior save while
the initial purchase is still unconfirmed. Load remains available. Start eligibility is deliberately
not inferred from an uninitialized historical checkpoint. It does not regenerate saves
or change the accepted Stage-20 generator/calibration. The release alias advances from `se-gen-1`
to `se-gen-2` because the explicit new-game composition adds physical market entities; existing
saved generator identities are retained. Canonical profile input:

```json
{"dockMaxSpeedMps":1,"dockRangeM":1000,"handling":"one-existing-fixed-interval-per-completed-tick","id":"se-pilot-start-1","quotesMilliCreditsPerKg":{"commodity.material.purified_water":[5000,4500],"commodity.material.structural_alloy":[50000,45000],"commodity.ore.metallic":[10000,9000]},"reservePriceMilliCredits":25000000,"savingsMilliCredits":100000000,"stationLiquidityMilliCredits":10000000}
```

The first existing empty IDLE reserve is sold by its existing faction for 25,000 credits. The
independent player starts with disclosed savings of 100,000 credits and retains 75,000 after the
real conserved treasury payment. No new hull, faction treasury, cargo or strategic authority is
granted. Fleet faction identity remains its immutable generated registry origin; personal ownership
is the existing PlayerState authority and the player has no faction affiliation.

Only physical endpoints in that ship's starting system receive an ordinary market wallet marker.
Each starts with 10,000 credits, credited through an explicit new-game money source and recorded in
the same system ledger. Markers contain no inventory. Existing Stage-18 endpoint storage is the
only commodity stock; an empty store offers no goods. Opening prices are per SI kilogram and do not
reinterpret legacy item-count units. These finite opening quotes are not a dynamic market model.

Docking checks exact hierarchical physical range and speed; neither docking nor undocking moves the
ship. BUY/SELL uses the shared TradeController for wallet/access/customs and Stage-18 logistics for
real storage, capacity and compatible finite handling. One successful handling operation per ship
per completed tick is evidenced in the ordinary persisted ledger, so reload cannot reset its budget.
Tick zero has no budget. Preview restores an isolated exact checkpoint; foreign or stale confirmation
is rejected before mutation. No second simulation clock is installed. WASD applies real fitted
thrust on completed campaign ticks, zero input coasts, X brakes with finite propulsion and fuel.

## Owning save-schema change

Freight schema 1 advances to **2**, retaining freight binary file version 1 and the campaign v5
outer envelope. New manual lots have `player-market-cargo:<fleet>` order identity and exact endpoint
provenance. They require an IDLE physical freighter, matching physical hold mass and a composed
PlayerState that owns the fleet. The original transport-order path keeps its existing checks.
Schema-1 saves adopt to 2 without creating goods or funds; manual lots falsely labelled schema 1
and unsupported schemas are rejected. Decode never changes input bytes or the live campaign.
Manual source endpoints must remain ordinary infrastructure endpoints.

## Evidence and remaining work

`GeneratedCampaignPilotStartTest` and `GeneratedCampaignPilotPhysicalTest` cover pure/stale/foreign
preview, conserved starter purchase, physical cell crossing/fuel/coasting/braking, docking admission,
real cargo/wallet transfer, handling budget persistence and v5 round trips. Dock/trade tests position
the ship through a labelled physical fixture; they do not prove human navigation to the station.
The software-EGL smoke adds keyboard start/purchase from an ordinary fresh seed without a player
fixture. Exact final results are recorded in the UI evidence document after verification.

Inter-system player travel, mining, fitting, construction/supply, fleet/faction and carrier command
loops, production NPC offers and human B18 acceptance remain mandatory in #412/#370. This slice
must not mark 23B complete or unlock 23C.

Fixed 1–16 opening corpus result: **12 opening/round-trip passes, 4 generation rejections**.
Seeds 4, 6, 8 and 10 are rejected by the unchanged production probe before player initialization
(`industrial specialization candidates require an accepted resolved seed`). No seed was replaced
or retried. This is not an all-seed PASS or closure of campaign-generation acceptance. All twelve
materialized campaigns accepted the exact disclosed purchase and non-granting restore. The complete
machine-readable result is `docs/benchmarks/stage23b-pilot-opening-corpus-v1.json`; reproduction
uses `tools/qa/Stage23BPilotOpeningCorpus.java`.
