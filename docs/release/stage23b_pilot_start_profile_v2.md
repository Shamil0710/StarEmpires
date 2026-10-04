# Stage 23B physical-market opening profile v2

Status: IMPLEMENTED SLICE / 23B ACTIVE/PARTIAL. Exact-head verification belongs to PR #413.

Profile: `se-pilot-start-2`; release generator alias: `se-gen-3`. Canonical SHA-256:
`c90330307a1d1f5750ab18c05ddbd872203dcf414b6ae3c1f2560ade1e3a9f71`. Canonical policy input:

```json
{"baseSellPricesMilliCreditsPerKg":{"commodity.material.purified_water":5000,"commodity.material.structural_alloy":50000,"commodity.ore.metallic":10000},"dockMaxSpeedMps":1,"dockRangeM":1000,"handling":"one-existing-fixed-interval-per-completed-tick","id":"se-pilot-start-2","marketIdentityPrefix":"Generated market v2 ","marketScope":"all-existing-physical-endpoints","reservePriceMilliCredits":25000000,"savingsMilliCredits":100000000,"scarcityExponent":1.2,"scarcityMultiplierBounds":[0.5,2],"stationBuyFraction":0.9,"stationLiquidityMilliCredits":10000000,"targetStockClassCapacityFractions":{"default":0.25,"frontier_multipurpose":0.5,"refinery_complex":0.75}}
```

This supersedes v1 only for explicitly confirmed new games. Existing accepted Stage-20 generation,
calibration, root seeds and saved fingerprints are retained. Savings and reserve purchase remain
100,000 and 25,000 credits; no new hulls, cargo, faction treasury or affiliation are granted.
Each existing physical endpoint is commissioned once with 10,000 credits of disclosed finite market
working capital. Seed 1 has 72 endpoints / 720,000 credits of total market capital; actual count and
total are disclosed before new-game confirmation. Every source is recorded in its real local ledger
as `new-game-market-working-capital.v2`, after economically empty ordinary entity creation. Market
wallet markers contain no inventory. Cargo remains the existing Stage-18 storage and freight hold.
Neither restore, capture, docking nor visiting a system commissions or replenishes a market.

The ordinary persisted marker identity `Generated market v2 <endpoint>` binds price policy v2.
Unversioned `Generated market <endpoint>` markers retain the accepted static v1 quotes; old home-only
worlds stay home-only and receive no new money. The genuine accepted ancestor checkpoint, its
compressed/raw SHA-256 and source commit are retained in `src/test/resources/campaign/` and can be
reproduced with `tools/qa/Stage23BLegacyOpeningFixture.java` against that ancestor's packaged JAR.
No owning save schema, envelope or binary format changes are required for this existing identity
field. Unsupported markers are not treated as a v2 commissioned market.

V2 reuses MarketSystem's dimensionless stock-scarcity exponent over **actual SI kilograms**. Target
stock is a disclosed fraction of compatible physical class capacity: refinery 75%, frontier 50%,
other stations 25%. The multiplier is bounded to 0.5–2; station sale is rounded to a positive
milli-credit/kg, and station purchase is 90% of that price with integer truncation. The price reads
current physical stock; it does not write a replacement inventory or persisted price authority.
Legacy item prices retain their original float calculation and units. Handling, access, customs,
wallet/capacity admission and physical provenance keep their existing shared owners.

This allows a real bounded water trade from an abundant hub to a refinery, including across an
ordinary neighbor hop. Sale lowers the buyer's actual wallet and adds the carried kilogram to its
actual storage. Profit comes from conserved counterparties, not a payout source. No station can
buy beyond its wallet, compatible spare storage or per-tick handling. Quote-target fractions are
explicit market policy, not a claim that an arbitrary process-demand model has been implemented.

The fixed seed 1–16 rerun recorded 12 market-opening/roundtrip passes, zero opening failures and
four unchanged upstream generation rejections (4/6/8/10), without replacements or retries. See
`docs/benchmarks/stage23b-physical-market-corpus-v2.json`; overall corpus acceptance remains false.
Actual buy/hop/in-transit roundtrip/sale is covered by GeneratedCampaignPhysicalMarketTest. The
packaged-JAR UI journey completed a 2.50→7.32-credit/kg water trade and reload with conserved
4.82-credit profit. Full exact-head clean CI remains required; graphical evidence is not human PASS. Mining,
fitting, construction/supply, strategic/faction/carrier commands, production NPC offers and human
B18 acceptance remain mandatory; this profile does not close 23B or unlock 23C.
