# Stage23B: paid small-craft construction

Status: native v29 queue, commands, UI and conserved available-yard allocation implemented; full verify PASS. Ordinary acquisition and delivery remain open.
2026-10-16.

The additive `Stage23SmallCraftProductionCatalogs` keeps historical Stage22 loaders intact.
Exactly seven authored modules retain their engineering masses and existing family recipes;
two compact hulls retain their existing physical construction/service profiles. No resources
or craft are created by loading these catalogs. Campaign stores, manufacturing, handling, freight readers and validators now use this
additive runtime composition. Historical Stage22 loaders remain unchanged.

## Retained construction contract

A separate build-order owner must persist order ID, player actor, legal faction, station,
yard instance/definition, exact design and fitting, start tick, required/completed work,
reserved bare-hull material masses and finished module counts. Do not allocate SmallCraftId
until physical completion. Reject mismatching plan/work/bill on restore.

Full bill reservation must remove the available stock while retaining its occupied capacity.
Stage18StationStorage needs an independent build-reservation category; sharing the process,
repair, refit or construction categories would overwrite another queue's custody. The new
category must participate in usedCapacityKg, commodityLayerCapacityByStorageClassKg,
manufacturingLayerCapacityByStorageClassKg and preflightRefitReservation's temporary clone.
Composed restore must rebind this category from the build owner before accepting new storage.

Per-tick builds must consume the same station/yard budget map as repair and refit in
advancePersonalRepairsAtTick, with stable order iteration and one common completed-tick
watermark. Opening a fresh interval in each queue would duplicate available work. Pausing,
inactive or incompatible yards and absent operating resources must not advance work.
Cancellation before work returns exact escrow and capacity once; cancellation after partial
work requires an explicit salvage policy rather than restoring an untouched full bill.

The existing SmallCraftPhysicalLogisticsService.settleBuild is an atomic one-interval
settlement that consumes the full bill and work. It cannot be called again on escrowed
stock, and a fabricated large interval cannot represent accumulated ticks. A trusted
completion permit from the real queue is required. Preflight pristine physical state,
identity allocator, logistics, rights and personal property before consuming the permit.
The result remains unserviced, with empty consumables and pending physical delivery.

The catalog must be composed consistently across canonical station stores, manufacturing,
finished-product handling, freight persistence, custody validators and UI before producing
any new module in the campaign. Add a native schema/version boundary for the new queue
and reject historical headers carrying new build orders. Keep historical declarations
and explicit migrations non-granting.

Required acceptance: missing-bill refusal; exact reservation and retained occupancy;
shared work with repair/refit; partial reload and no repeated tick; inactive-yard suspension;
cancel once; completion exactly once; no early identity/ownership; empty consumables;
exact native restore; forged old-header rejection; actual physical delivery. Explicit
material/work/geometry fixtures establish transaction behavior, not ordinary acquisition.
Full gate completed: `target/stage23b-small-craft-catalog-full-verify.log` —
**2701 tests, 0 failures, 0 errors, 1 skip; BUILD SUCCESS**, 25:12,
2026-10-16 14:53:00 +03:00. Coverage thresholds, strict Javadoc and package passed.
This result supersedes the running gate status above. Independent catalog review found
no blockers. No production source changed after this gate started; later changes were
QA journey code and documentation. Stage23B remains ACTIVE/PARTIAL: this catalog slice
does not complete persistent small-craft construction, physical delivery or the other
remaining acceptance blocks. Native format remains v28.

## Persistent paid construction — native v29, 2026-10-16

The contract above is now implemented by ShipyardSmallCraftBuildWorkQueue and its
independent station-storage reservation. Six authored fits are exposed in INDUSTRY;
seven module recipes use the common runtime catalog. START reserves the exact complete
bill, real consecutive ticks share the repair/refit yard budget, and zero-work CANCEL
returns the escrow once. Completed work remains reserved until explicit owner-local CLAIM.
CLAIM allocates one pristine empty craft and pending delivery; it does not place it in a
carrier bay, grant consumables, or complete physical delivery.

Native v29 appends the bounded build sidecar as payload 20. Historical files migrate with
an empty queue; historical framing carrying the new production stock is rejected. Restore
checks authored bills/work, station and legal builder identity, elapsed work, actual yards,
and aggregate occupancy of all storage custody categories. Completion permits require the
exact canonical store and reject stale or duplicate use. Cancelling a zero-budget order
clears its cached plan before its identifier can be reused for another design.

Evidence:
- target/stage23b-small-craft-build-completion-tests-fixed.log: 25 tests passed,
  including actual consecutive build ticks with shared repair work, partial/ready reload,
  one empty craft, and forged legal builder rejection.
- target/stage23b-small-craft-build-joint-custody-tests.log: 7 tests passed;
  summed repair/build overflow refused both at snapshot construction and file import.
- target/stage23b-small-craft-build-cache-regression.log: 5 tests passed, including
  cancellation and identifier reuse with a different authored work requirement.
- target/stage23b-small-craft-build-javadoc.log: strict Javadoc passed.
- target/stage23b-ordinary-hub3-native29-migration.log: real v28 checkpoint migrated
  and exactly reloaded at tick 43523559, wallet 4900000 milli-credits, fleet:7,
  zero craft, six build rows and ten equipment rows. No grants or inferred work.

Current full gate: target/stage23b-small-craft-build-native29-full-verify.log, running.
The preceding 2701-test v28 catalog gate is historical evidence only. Current native format
is v29. Stage23B remains ACTIVE/PARTIAL: ordinary material acquisition/manufacturing,
physical delivery and the remaining acceptance blocks still require completion.

Actual supply audit: target/stage23b-ordinary-hub3-small-craft-supply-audit.log — PASS,
read-only, exact state unchanged. All seven manufacturing cards retain their authored module
names. Owned industry.2 has an ACTIVE orbital_escort yard, but no compatible active small-craft
yard and no finished small-craft modules. Its existing module manufacturing lines are compatible.
Empire fits require empire_capital_service; Union fits require series_complex; both designs
have authored construction bindings. The audit records exact yard/hull/module bills against
actual stock. It does not manufacture a product or establish ordinary material acquisition.

During the current full native v29 gate, GeneratedCampaignSmallCraftBuildTest passed
all four tests (75.26 s). The actual consecutive shared-budget completion reached
world tick 382910 for 842400 required work seconds, including repair contention and
mid-build/ready native round trips. The complete Maven gate is still running.

## Available conserved yard resources

A separate ALLOCATE_YARD_RESOURCES_AVAILABLE action supplies the largest balanced rated
fraction supported by the station's already allocated yard resources. Rated allocation stays
unchanged. Both commands require ordinary ownership and berth authority; the immutable candidate
must pass actual support projection before commit. UI shows predicted quantities and a separate
available-resources button. No new save schema is necessary for existing allocation fields.
Targeted integer/migration/integration gate: target/stage23b-yard-available-integer-and-migration-tests.log,
7 tests PASS, no failures/errors. Independent read-only review found no blockers. Materials,
constructed yards and support installations still require the ordinary acquisition path.

Migration hardening also covers standalone Stage21 containers and typed adoptStage21/restoreStage21.
The pre-fix negative reproduction failed as expected because new stock was accepted:
target/stage23b-small-craft-build-legacy-container-reproduction.log. The fixed guard is shared
with native vocabulary validation; genuine legacy migration remains accepted. Final full verify
has been restarted after these production edits and is running in the current full-gate log.

## Current native29 production gate — 2026-10-16 20:13:13 +03:00

Full mvn -Ptest-parallel verify completed: target/stage23b-small-craft-build-native29-full-verify.log,
2714 tests, 0 failures, 0 errors, 1 expected skip, BUILD SUCCESS, 18:11.
Both coverage files were merged, JaCoCo thresholds passed, strict Javadoc and desktop package passed.
This supersedes preceding in-progress entries and includes the central typed/file legacy guard,
module-card names, cancelled-order cache reuse fix and conserved available-yard allocation.
Independent reviews found no remaining blockers for these slices. Builder affiliation stays locked
until CLAIM or valid cancellation; ordinary affiliation preview refuses safely without live mutation.
No production source changed after the successful gate began. Subsequent changes are QA/test
continuation and documentation. This dirty working-copy gate is not exact-head CI or whole23B RC
acceptance. Stage23B remains ACTIVE/PARTIAL; ordinary material chains, delivery and other broad
acceptance blocks remain open.
