# Stage 20F — Industrial Shipyard Installation Plan v1

> Status: **ACCEPTED STAGE-20F INSTALLED-YARD AUTHORITY**
> Implementation: `stage20f.industrial-shipyard-installation-plan.v1`
> Input authority: accepted canonical initial station inventory

## Purpose

A generated station archetype containing fabrication facilities is not automatically a shipyard.
`Stage20IndustrialShipyardInstallationPlan` closes the last missing Stage-20F authority through exact
Stage-18G state:

```text
explicit selected-station yard row
→ canonical installed-yard identity and explicit faction owner
→ exact authored Stage-18G yard definition
→ exact generated Stage-18E support-facility slots
→ canonical active support projections
→ non-reused station services and residual engineering work
→ Stage18ShipyardRuntime projection
→ all-or-nothing installed-yard authority
```

Every selected station has an explicit row. An empty yard list is authoritative absence and creates
no capability.

## Canonical identity and ownership

Installed yard IDs are deterministic pre-runtime identities:

```text
<generated station placement ID>.yard.<contiguous station-local ordinal>
```

Every yard has an explicit stable faction owner. Every required support facility must have that same
owner; station position, nearest start and role labels never infer ownership.

## Required support facilities

The exact yard definition supplies its required Stage-18E facility-definition IDs. A selected
operating facility may satisfy a requirement directly. Any remaining requirement must appear exactly
once as a supplemental installed state for a real unselected generated slot, with the canonical
facility instance ID and orbital location.

No unrelated supplemental facility is accepted. Every support snapshot is reprojected through
`Stage18FacilityRuntime`, and every yard snapshot is reprojected through
`Stage18ShipyardRuntime` at the generated station node.

## No resource double-use

Selected facilities, supplemental supports and yards share the already-authored station service
pool. The plan accounts together:

- facility process power plus yard integration power;
- facility heat rejection;
- facility staffed labor plus yard labor;
- facility maintenance work.

Support engineering work already committed to selected recipes is subtracted before yard work is
available. Each yard must fit the residual work of its own required supports, and all installed yards
share the union residual pool. This conservative v1 rule cannot double-count one support snapshot.

## Authority closure

Acceptance removes only `INSTALLED_SHIPYARDS`. Because all preceding Stage-20F missing authorities
are already closed, the accepted yard report has an empty missing-authority set. Any inactive support,
inactive yard, owner mismatch or shared-resource overclaim leaves `INSTALLED_SHIPYARDS` unresolved
and commits no partial station row.

## Production coverage

The fixed seed-1 integration selects a remote-input refining process at a real generated industrial
station, supplies canonical heavy-fabrication and assembly support states and projects one active
orbital escort yard. Negative cases prove disabled support, station-power overclaim, noncanonical yard
identity and owner mismatch fail closed. A separate accepted case proves explicit empty-yard absence
does not grant shipbuilding.

## Final Stage-20F slice

`docs/stage20f_operational_industrial_specialization_v1.md` indexes final roles only from the closed
process and active-yard evidence. It adds no new production authority or percentage bonus.
