# Stage 20.5D — live arrival authority integration v1

Status: implemented production seam and executable integration acceptance.

## Closed boundary

`Stage20LiveArrivalAuthorityIntegration` restores the canonical `JUMP_EDGE` rows captured by
Stage 20K and binds their directional endpoints to the existing ordinary `FleetJumpService`.
It does not create another transfer path. Topology validation, fitted-drive constraints, energy
commit, approach, pending, detached transit and cooldown remain owned by the existing jump FSM.

At request time the adapter resolves exactly one saved direct edge. The legacy Ashley transform is
given only a finite local float projection so the current renderer remains operable. On departure,
the origin-local hierarchical/double sidecar is released after the persistent entity is detached.
On arrival, the exact saved `LocalPhysicalPosition` and velocity are registered under the newly
allocated destination-local `EntityId`; the stable `FleetId` is unchanged.

## Persistence and restart law

- Endpoints are parsed from the saved campaign. They are never regenerated during restore or jump.
- A process-local departure marker detects duplicate live detach callbacks, but it is deliberately
  not required at arrival. A save restored while the fleet is already `IN_TRANSIT` therefore
  resumes the ordinary persisted FSM and can still install the exact destination endpoint.
- The current Stage 20D persisted endpoint stores a scalar non-negative arrival speed. Version 1
  maps it to local `+X` and zero `Y` using the explicit
  `stage20d.persisted-arrival-speed.local-positive-x.v1` convention. The current calibrated speed is
  zero, so no velocity information is discarded. A future directional schema must version this law.
- Capturing the destination materialization sidecar persists the exact hierarchical cells, local
  double offsets and velocity components under the destination entity identity.

## Invariants

- only saved ordinary neighbor edges are accepted;
- no direct non-neighbor shortcut is introduced;
- no discovery or faction knowledge is granted by physical arrival;
- no float value becomes physical position authority;
- no replacement fleet, cargo, energy or engineering resource is created;
- existing unbound worlds retain the legacy arrival behavior for backward compatibility.

## Acceptance coverage

`Stage20LiveArrivalAuthorityIntegrationTest` drives an ordinary world fleet through the live jump
FSM over a generated Stage 20 topology, verifies exact endpoint/velocity persistence and unchanged
discovery state, and rejects non-neighbor requests. Existing jump and persistence tests continue to
cover the unbound compatibility path.
