# 23B: initial NPC station legal capital v1

Status: IMPLEMENTED SLICE / validation in progress; Stage23B ACTIVE/PARTIAL.

Policy `stage23b.initial-npc-station-legal-capital.v1` is an explicit fresh-campaign
authoring decision. Previously, a nonspecialized endpoint outside a faction's controlled
start system had no economic operator at all: neither the physical infrastructure layout
nor its archetype contains an owner. Territorial neutrality does not establish a seller.
Purchase and restoration must never invent one.

The current default new campaign assigns existing civilian processing assets to existing
NPC economic actors. Each faction has at most one refinery role and one frontier role;
an already specialized or territorially owned asset fills that role first. Only existing
`refinery_complex` and `frontier_multipurpose` endpoints without an initial owner qualify.
These roles cover existing chemical/liquid processing and liquid/oversized handling;
the assignment does not activate a facility or supply its operating resources.

Initial faction systems come from accepted `FACTION_START` rows. A breadth-first traversal
computes physical topology hop distances. Candidate pairs sort globally by hop distance,
station ID, then faction stable ID. The next pair wins only if its role slot and asset are
still free. Unfilled slots remain explicit diagnostics. No stations are created to fill
shortages. Accepted generation, rows, fingerprints, geometry, stocks, facilities, freight,
wallets, treasury, territory and market opening liquidity retain their previous values.

One immutable plan is shared by fresh NPC posting and pilot-start preview/commit. The
fresh authority passes it explicitly into preview's isolated restored candidate. Initial
owner archives receive only the assigned station's class and position, with policy
provenance, no expiry and no resource estimates. Existing postings retain their ordering
and primary IDs; additional owner-local dispatchers follow them. No station coordinates
are issued to the pilot by this policy: seller briefing retains its existing permissions,
and additional NPC maps still require actual contact and conserved payment.

The plan supplies the operator when the already existing new-game market marker is
commissioned. A subsequent territorial change does not transfer an explicitly assigned
economic asset. The canonical specialized operator retains priority. `PURCHASE_STATION`
still needs an actual persisted faction component, existing treasury, affordable offer,
personal station location and exact physical berth; it never creates a seller.

The explicit three-argument `create(seed, profiles, reservePolicy)` preserves baseline
station ownership for historical callers. A four-argument overload selects station policy
independently from ship reserve policy. Default fresh creation uses the current manifest.
The authoring plan is transient; normal ECS faction and discovery/NPC state persist in
existing fields. Native format remains v27. Restore never computes or applies the plan,
including a tick-zero checkpoint, and never re-enables pilot start on a historical save.

Tests compare baseline/current frozen world, industrial state, freight, treasuries and
territory; check bounded deterministic selection, owner-local knowledge, exact reload,
preview purity, legal market identity, stable dispatcher, shortages and absence of
historical backfill. A purchase test uses explicitly declared funding/berth fixtures but
the seller is production-authored, with conserved treasury settlement and admission.
This does not prove ordinary acquisition of a refinery/frontier, its operating power,
chemical production or a completed yard. Those remain in `stage23b_remaining_work.md`.

Initial targeted regression: `target/stage23b-initial-station-capital-targeted.log`,
17 tests, zero failures/errors. Expanded regression, Javadoc and package gate:
`target/stage23b-initial-station-capital-targeted-verify.log`: **18 tests, zero
failures/errors, BUILD SUCCESS**, strict Javadoc/package; JaCoCo skipped for this focused
run. The full gate including the final count disclosure and disconnected-topology test is
`target/stage23b-initial-station-capital-full-verify.log`: **2690 tests, zero failures/errors,
one ordinary skip, BUILD SUCCESS** in 14:52 (2026-10-16 09:27:48 +03:00). Global coverage,
strict Javadoc and desktop package passed. Independent read-only review found no blockers.
The real acquired `industry.2` checkpoint also restored exactly and advanced from tick
27200080 to 27200081 with conserved wallet/fleet:
`target/stage23b-real-acquired-station-current-policy-integrity.log`.
This gate covers the initial legal-capital policy before the subsequent native v28 storage
extension and FIFO fix. Their later gates are recorded separately. The tested checkout
contains inherited uncommitted work, so this is not exact-head CI or the final isolated
clean gate of the whole stage.
The previous full 2688-test gate predates this separate policy and cannot validate it.
