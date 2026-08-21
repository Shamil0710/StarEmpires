# Stage 20F — Industrial Specialization Candidate Plan v1

> Status: **PROVISIONAL STAGE-20F FOUNDATION**
> Implementation: `stage20f.industrial-specialization-candidate-plan.v1`
> Input authority: one accepted `Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult`

## Purpose

Stage 20F must derive industrial specialization from ordinary Stage-18 facilities, inputs, storage
and physical routes. It must not turn a station/system label into output or a percentage bonus.

`Stage20IndustrialSpecializationCandidatePlan` establishes the first fail-closed boundary:

```text
accepted exact root seed
→ generated local SI station placements
→ exact Stage-18 station archetypes
→ exact facility-definition slots
→ physical storage / transfer limits
→ finite extraction-site capacities
→ facility-bound process capacities
→ existing input-limited throughput upper bounds
→ deterministic specialization candidates
```

The plan creates no runtime station, facility, inventory, yard, process order, fleet or bonus.

## Exact evidence retained

For every generated system, v1 retains:

- finite initial extraction sites with source, facility, method, reserve lifetime and resolved/unresolved
  export handling;
- every physical station placement and exact local SI position;
- the station's exact Stage-18 infrastructure archetype;
- its storage capacities, handled storage classes, transfer rate and maximum handled unit mass through
  that unchanged archetype definition;
- one canonical non-runtime slot for every facility definition installed by the archetype;
- every refining/component process capacity joined to the exact facility definition that provides it;
- the unchanged Stage-20E process/station ceiling and input-limited output ceiling.

`ProcessThroughputEvidence` now retains `facilityDefinitionId`. This closes an ambiguity that was
acceptable for aggregate Stage-20E supply accounting but not for Stage-20F: two physical facilities
at one station may expose the same recipe, so `(system, station, process)` was not sufficient
provenance for a specialization claim.

## Candidate status is not operational authority

`REACHABLE_UNRESERVED_UPPER_BOUND` means only that the existing Stage-20E theoretical closure found
positive physically deliverable input capacity under its accepted route-time profile. That capacity
is not reserved and may be shared by multiple candidate processes.

`INPUT_BLOCKED` means the same configured facility/process has zero input-limited output in the
retained physical closure. A station name cannot override this state.

Neither status says that the facility is powered, staffed, stocked or running at bootstrap time.

## Mandatory unresolved seams

Every v1 report retains all five missing authorities as machine-readable state:

1. `INSTALLED_FACILITY_OPERATING_STATE` — condition, allocated process power, heat rejection, labor
   and maintenance work required by `Stage18FacilityRuntime`;
2. `INITIAL_STATION_INVENTORY` — canonical Stage-18 commodity/product contents, not legacy inventory;
3. `RESERVED_INDUSTRIAL_INPUTS` — shared upstream capacity reserved across selected processes;
4. `OWNED_INDUSTRIAL_INPUT_FREIGHT` — exact input routes and ordinary owned freight assets;
5. `INSTALLED_SHIPYARDS` — explicit `Stage18ShipyardRuntime.InstalledYardState` bound to a generated
   physical station.

The v1 record rejects construction if any of these authorities is silently removed. Consequently
`operationallyAuthoritative()` is always false for this version.

In particular, a system cannot become a `shipbuilding center` merely because an industrial station
has heavy/assembly facilities. The current generated layout contains no installed-yard authority.

## Determinism and anti-rescue rules

- The public reconstruction API accepts one already accepted resolved production result.
- Layout root seeds must equal that result's exact root seed.
- Layouts must cover the accepted topology exactly once.
- Station facility slots must exactly equal the referenced Stage-18 archetype definition.
- Recomputed process-capacity keys must exactly equal retained throughput-evidence keys.
- Candidate ordering is canonical by system, station, facility and process identity.
- The reconstruction cannot inspect a failure and add a facility, resource, route, inventory or yard.

## Regression coverage

The production integration test uses accepted fixed seed `1` and proves:

- deterministic repeated reconstruction;
- exact topology/system and physical-station coverage;
- non-empty real facility and extraction evidence;
- every process row retains the same facility identity on both sides of the join;
- at least one real process has positive unreserved physical input closure;
- no unresolved operational authority can be dropped;
- caller-supplied candidate status cannot override physical throughput evidence.

## Next roadmap slice

The next Stage-20F change should introduce an explicit selection/reservation authority over these
candidates. It must allocate shared industrial inputs and their physical freight without double-use,
then bind explicit facility operating state and initial Stage-18 inventory. Installed-yard placement
must be a separate explicit authority before any shipbuilding specialization can be accepted.

Only after those seams close may a generated role label become an operational specialization.
