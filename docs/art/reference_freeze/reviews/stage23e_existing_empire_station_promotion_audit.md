# Stage 23E — Existing Empire Station Promotion Audit

**Status:** TWO EXISTING STATION MASTERS SELECTED FOR PROMOTION  
**Scope:** Empire industrial station + Empire trade/logistics hub  
**Decision:** use accepted Stage-20.5 production geometry instead of generating new five-candidate concept batches.

## 1. Sources reviewed

### Imperial industrial station

- reference: `ref.empire.station.industrial_station.v1`;
- source: `src/main/resources/assets/stage20_5/stations/imperial_industrial_station_v1.png`;
- Git blob SHA: `60c924e58ad2628f034b67a333eff5b18c3cc008`;
- existing runtime role: `Stage20MinimumPlayableSpriteCatalog.VisualRole.INDUSTRIAL_STATION`;
- authored canvas contract: 768x512 landscape;
- existing nominal presentation envelope: 1200 x 780 m.

The image was opened directly from the repository during this audit.

Observed geometry matches the Stage-23E industrial-station brief closely enough for promotion:

- protected central octagonal administration/service core;
- four large enclosed heavy production/service blocks;
- repeated service trunks and protected inter-module connections;
- strongly ordered work-zone hierarchy;
- enclosed, robust production language rather than exposed chaotic machinery;
- Imperial graphite/gunmetal, warm ivory and burgundy construction language;
- no visiting ship baked into the sprite.

Stage 23E may add derivative mechanical/door/gantry presentation, but it does not need a replacement
base concept to establish this station's identity.

### Imperial trade/logistics hub

- reference: `ref.empire.station.trade_logistics_hub.v1`;
- source: `src/main/resources/assets/stage20_5/stations/imperial_trade_hub_v1.png`;
- Git blob SHA: `79908fe3954eb972381d0809b14245432a08dd34`;
- existing runtime role: `Stage20MinimumPlayableSpriteCatalog.VisualRole.TRADE_DOCK_STATION`;
- authored canvas contract: 640x640 square;
- existing nominal presentation envelope: 1600 x 1000 m.

The image was opened directly from the repository during this audit.

Observed geometry matches the Stage-23E trade/logistics brief closely enough for promotion:

- protected central administration/traffic-control core;
- four clearly readable outer docking/clamp structures;
- ordered axial/cross traffic geometry;
- multiple cargo/service blocks around the core;
- obvious approach/docking interfaces without visiting ships;
- strong Imperial material/color identity;
- no decorative fantasy structures.

The existing asset already supplies the stable visual identity that a new five-candidate batch would
otherwise be trying to discover.

## 2. Runtime evidence

`Stage20MinimumPlayableSpriteCatalog` already binds these exact files to the corresponding ordinary
presentation roles:

- `imperial.industrial-station.v1 -> INDUSTRIAL_STATION`;
- `imperial.trade-hub.v1 -> TRADE_DOCK_STATION`.

Its resolver maps the accepted Stage-18 `station.infrastructure.industrial_station` archetype to the
industrial role and `station.infrastructure.trade_logistics_hub` to the trade role, while physical
station dimensions remain owned by `Stage20StationPhysicalGeometryProfile`.

Existing tests already verify that Stage-20.5 station PNGs carry real alpha/transparent corners and that
station resolution does not mutate simulation authority.

## 3. Why the Stage-20.5 shipyard is not promoted to another Stage-23E role

`imperial_shipyard_v1.png` is a valid existing production asset, but its large dedicated construction
berth communicates a shipyard-specific function. None of the currently unfilled Stage-23E references
is an exact shipyard reference.

It is therefore not reused as:

- naval ordnance depot;
- frontier multipurpose station;
- refinery;
- mining outpost;
- high-tech hub.

Doing so would save generation work by sacrificing role readability, which violates the Stage-23E
reference contract.

## 4. Freeze disposition

Both exact-role assets move from `GENERATE_5_SELECT_1 / PLANNED` to
`PROMOTE_EXISTING / SELECTED`.

They are **not FROZEN yet**. Remaining gate:

1. create or verify the canonical Stage-23E reference-master normalization without changing geometry;
2. grayscale review;
3. 25% and 12.5% downscale review;
4. silhouette review;
5. presentation-anchor review for docking/mechanical zones;
6. record exact canonical-master checksum/provenance.

No five-candidate concept generation is required for these two references unless later QA finds a
specific geometry defect that cannot be fixed without redesign.
