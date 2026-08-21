# Stage 20F — Industrial Input Route Evidence Plan v1

> Status: **PROVISIONAL STAGE-20F INPUT PROVENANCE**
> Implementation: `stage20f.industrial-input-route-evidence-plan.v1`
> Input authority: one accepted `Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult`

## Purpose

The specialization candidate plan retained each process's final input-limited output upper bound,
but a later reservation authority also needs the physical evidence behind that number. It must know
which finite supply keys were considered, which routes existed, which were admitted by the explicit
route-time boundary and how much non-reserved capacity each admitted route could deliver.

`Stage20IndustrialInputRouteEvidencePlan` exposes that provenance without changing the accepted
Stage-20E throughput result:

```text
exact facility/process candidate
→ exact Stage-18 recipe inputs and input/output mass ratios
→ every visible (commodity, source system) supply key
→ finite source capacity visible at that closure step
→ explicit physical source-to-processor route when feasible
→ route-time admission state
→ min(source capacity, physical route throughput)
→ unchanged non-reserved process output upper bound
```

## Machine-readable route states

Every candidate supply key for a required input is retained as one of:

- `ADMITTED` — an explicit neighbor route exists inside the accepted route-time boundary;
- `NO_FEASIBLE_ROUTE` — the physical route evaluator returned no route;
- `ROUTE_TIME_EXCEEDED` — a physical route exists but exceeds that boundary.

An admitted row retains both the source capacity and route throughput, then admits exactly their
minimum. Rejected rows admit zero. Present routes are revalidated against the generated topology, so
the evidence cannot contain a non-neighbor shortcut.

For each input commodity, the report also retains:

- exact recipe input kilograms per kilogram of output;
- the shared route-time boundary;
- summed admitted input kilograms per second;
- input-supported output kilograms per second before the process/station ceiling.

The process result remains the minimum of every input-supported output and the unchanged physical
process/station ceiling.

## Deliberately non-reserved

The retained supply identity is the existing Stage-20E `SupplyKey`: authoritative commodity plus
physical source system. Its finite capacity can include aggregate extraction or upstream process
output in that system. It is not a runtime inventory lot, facility instance or ownership identity.

The same supply key may support several candidate processes in this report. Likewise, each route was
assessed against the same representative physical freight authority independently. Therefore:

- upstream capacity is not deducted;
- route/fleet capacity is not allocated across candidates;
- no freight asset is owned or committed;
- no input is present in station inventory;
- no process is selected, powered, staffed or started.

All five unresolved authorities from the candidate plan remain mandatory, including
`RESERVED_INDUSTRIAL_INPUTS` and `OWNED_INDUSTRIAL_INPUT_FREIGHT`.

## Determinism and fail-closed joins

- Reconstruction starts from one accepted resolved root seed.
- The exact specialization candidate plan is reconstructed internally.
- Every facility/process candidate must have non-empty recipe-input evidence.
- Process coverage must equal the retained Stage-20E process-throughput coverage.
- Candidate supply capacity cannot exceed the final retained capacity for the same supply key.
- Processes, inputs and supply keys have canonical ordering.
- Callers cannot remove a missing authority or replace a candidate's physical input evidence.

## Regression coverage

Focused analyzer tests prove route-limited input output and preserve separate machine-readable states
for absent and over-time routes. The fixed accepted seed-1 production integration proves deterministic
whole-world reconstruction, exact process coverage, non-empty admitted route evidence, valid route
endpoints and neighbor-only paths, bounded source capacity and immutable missing-authority state.

## Next roadmap slice

The next Stage-20F authority should accept an explicit selected process/output-rate request rather
than infer one from a station label. It can then reserve shared `SupplyKey` capacity across all
selected inputs without double-use and bind each remote reservation to an explicit finite freight
allocation. Facility operating state and initial Stage-18 inventory remain separate later authorities;
installed-yard placement remains mandatory before shipbuilding specialization.
