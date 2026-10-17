# 23B: paid liquid-storage expansion contract

Status: IMPLEMENTED SLICE / full validation running, native v28. Stage23B remains
ACTIVE/PARTIAL; ordinary supply and whole-stage completion are not claimed.

The existing acquired `industry.2` has real operating allocations and compatible assembly
capability, but no liquid store/interface. The separately built bootstrap batch plant can
use conserved donors at that station; volatile inputs still cannot arrive. An admitted
refinery has the opposite problem: storage exists, but no operating-resource supply.
Building storage on the working station avoids inventing a reactor, crew or heat source.

The extension is a separately authored structural storage package, with its own exact
material bill, installed mass, finite construction work, host/location eligibility, maximum
installation count and declared installation geometry. No available internal volume or
support pool may be inferred from a station's outer radius. Its bill must use basic alloys
and conductors, without chemicals that would recreate the same bootstrap dependency.
`Stage23StationStorageCatalog` declares one internal package for
`station.infrastructure.industrial_station` at `location.orbital_station`: installed mass
1,000,000 kg, 10,000,000 kg liquid capacity, bill 700,000 kg structural alloy + 200,000 kg
light alloy + 100,000 kg conductor metal, 160,000 engineering work-seconds. The bounded
internal installation interface is new explicit content, not an inference from frozen
placement radius or a claim of a modeled free-volume calculation. No stock is included.

The passive STORAGE role exposes no processing, fabrication, maintenance, staffed-labor or
electrical production capability. Fake positive process power/work values are forbidden.
It has a passive projection, with allocation commands rejected. The package's typed capacity/interface effect
is distinct from process capability and is proved by completed construction.

Existing Stage18 construction owns full material reservation, finite engineering work,
completion and cancellation. Installed identity and retained COMPLETE order must agree
exactly on station, definition, instance and location. A pending/cancelled order, arbitrary
installed row, wrong host, duplicate package or unsupported old content declaration cannot
authorize capacity. Repeated adoption derives base plus unique proofs, never current plus
delta. No second authority or unconstrained saved-capacity override should be introduced.

Live adoption must validate the entire batch before changing the existing storage object.
Only its capacity map may expand monotonically; commodity/product maps and process, cargo,
module and construction reservations retain their existing identity and values. The node,
industrial station and infrastructure endpoint must all point at that same store. Updating
only the industrial node is insufficient: ordinary BUY/SELL, LOAD/UNLOAD and propellant
handling read the infrastructure endpoint.

The new handler keeps its identity, aggregate mass rate and max unit mass. It adds the liquid
class interface only after the paid package is complete. It creates no additional interval
budget and does not bypass compatible storage, physical berth or ownership admission.

Restore order stays world → industry → infrastructure → freight → arrival → live queues →
personal custody/repair/refit/handling/yard. Industry must validate completed/installed proofs,
derive exact expected capacity/handler, compare the saved capacity map, restore stock, then
bind the node to that restored store. Infrastructure receives this proved store/handler.
Nonspecialized endpoints without construction evidence retain strict base-archetype capacity.
Capture already persists construction orders, installed states and storage snapshots, so a
parallel capacity sidecar is unnecessary.

Historical `Stage23BootstrapMaterialsCatalog` fingerprints must remain immutable. A separate
additive runtime composition and content declaration must identify the new facility,
construction and typed storage-effect semantics. Current native version is 28, with content
declaration schema2 and unchanged nineteen payloads. Genuine v21–27 validate their original
schema1/historical fingerprints. An old
header must not admit a tank or expanded capacity. Historical saves without one restore
unchanged and receive no tank, work, material, money or new operating resources.

Acceptance must cover refusal without bill, partial work and exact reload, cancellation,
paid completion, repeat adoption, shared-store identity, reservation survival, unchanged
handling rate, actual liquid unloading and a chemical batch using conserved existing
operating allocations. Negative saves must cover expanded capacity without proof, missing
installed/COMPLETE counterparts, wrong host/location, duplicates and old-header framing.
Ordinary mining, refining and delivery of the full construction/processing bills remain
separate end-to-end evidence; a transaction fixture cannot stand in for those journeys.

Evidence: `target/stage23b-storage-extension-foundation-tests.log` — 16 tests PASS;
`target/stage23b-storage-extension-composed-tests.log` — 7 tests PASS. The chemical
targeted run passed all 11 tests, including 100 kg actual reserved input → 66 kg chemical
output on common ticks, partial native reload and conservation of all four operating
allocation totals. Its first verify failed solely on one missing Javadoc return tag,
which was corrected; this is not recorded as a successful verify. New focused negatives
cover wrong host/location, two distinct COMPLETE tanks and an atomic mixed valid/invalid
adoption batch. Storage full gate: `target/stage23b-storage-extension-native28-full-verify.log`
— **2696 tests, 0 failures/errors, 1 ordinary skip, BUILD SUCCESS**, 14:42,
2026-10-16 09:57:13 +03:00, coverage/Javadoc/package PASS. The subsequent actual ore/fuel
exchange exposed a separate pre-existing FIFO complete-series boundary; its fix and the
clarified passive UI label are under the next gate:
`target/stage23b-storage-fifo-native28-full-verify.log`. Independent read-only review
found no blockers. Material, ownership, berth and
historical construction-work fixtures remain explicit. Actual entire construction work
and ordinary mining/refining/delivery of the bill remain separate acceptance work.

The current runtime declarations are facilities
`30909014e1ca3e8b529e2e0f4258052e15e2b101e1a0f33d6e6fc9154797d6e0`,
construction `5df481035143f4d1396a5522991e249ba2c49fa4d28c6433677fa6eb37002f43`,
typed effect `b17db48cc4ee2c077651b5d41474888dd32ecd7c141126a5061e9c4003752f11`.
The actual ordinary acquired `industry.2` checkpoint migrated exactly from native v27;
node/station/endpoint share one store, the production card and real assembly line are
available, and preview correctly rejects the missing 700000/200000/100000 kg bill.
`target/stage23b-actual-storage-extension-native28-audit-utf8.log` — PASS without any
material, work, geometry, money or capacity fixture. This read-only audit does not prove
ordinary construction completion.
2026-10-16 final storage/FIFO gate: `target/stage23b-storage-fifo-native28-full-verify.log`
— **2698 tests, 0 failures, 0 errors, 1 skip; BUILD SUCCESS**, 17:17,
10:20:56 +03:00. Coverage thresholds, Javadoc and package passed. This supersedes the
pending gate status above and includes the passive-storage UI clarification and FIFO fix.
The ordinary ore-for-propellant continuation also passed with exact native reload:
`target/stage23b-actual-ore-for-propellant-complete-v28.log`. Its continuation purchased
832786 kg of water, sold 115755 kg of already mined ore, and ended with
1999999.8836804298 kg of onboard propellant and 119494.494 credits. Purchases funded
the market's subsequent purchases of ore; no money or materials were granted.
Stage23B remains ACTIVE/PARTIAL: the ordinary construction bill and remaining broad
acceptance journeys are not completed by these results.
