# Stage 20H — deterministic special locations v1

Status: **IMPLEMENTED AND MERGED** — PR #306, exact-head CI run `32544301469`

## Purpose

Stage 20H adds anomalies, derelicts and resource-bound special sites to the already accepted
Stage-20 physical world. Every location remains inside an ordinary `StarSystemId` and uses
`LocalPhysicalPosition`; the generator has no pocket-map coordinate domain and no implicit
teleport transition.

## Authority chain

```text
accepted Stage20ResolvedGeneratedWorldProductionProbe
→ existing Stage-20C local SI layouts and traffic anchors
→ existing finite Stage-20E resource occurrences
→ accepted Stage-20A route bands and mining-ship propulsion/endurance
→ accepted Stage-20A/17.5 channelized target signatures
→ Stage-18G bare-hull BOM and Stage-18H salvage streams
→ Stage20SpecialLocationWorld
```

The current profile is `stage20h.special-location-profile.v1`. It authors only bounded rarity,
hazard and special-content policy. It does not replace physical distances, propulsion, sensor
channels, resource reserves or industrial content.

## Generated location kinds

| Kind | Detection/scan authority | Physical value |
|---|---|---|
| energetic anomaly | accepted passive thermal signature; passive classification | no resource or salvage value |
| escort-hull derelict | accepted radar-cross-section reference; active classification | finite streams derived from the production escort bare-hull BOM |
| resonant resource phenomenon | accepted radar-cross-section reference; physical survey classification | references one existing finite Stage-20E occurrence |

The derelict's provisional accessible fraction is explicitly authored as `0.4` and remains subject
to Stage-22 content/balance review. Each material stream closes as:

```text
constructed BOM mass
= accessible pre-recovery salvage
+ irrecoverable derelict loss
```

Ordinary Stage-18 salvage processing then applies its own recovery losses. Resource phenomena do not
create another deposit: their value is the already generated occurrence's finite accessible mass ×
grade × source recovery.

## Placement, approach and proximity

- anomaly/derelict offsets are sampled from accepted semantic local-route bands;
- resource phenomena use the exact position of the linked occurrence;
- nearest traffic proximity uses real station/jump anchors and SI distance;
- routine approach time is recalculated for the accepted `MINING_SHIP` sustained-thrust envelope;
- security remains explicitly `UNASSESSED`; no hidden security score is generated;
- a bounded deterministic coverage repair guarantees one location of every current kind without
  adding resources or faction assets.

For accepted representative root seed `1`, current output is deterministic:

| Measurement | Result |
|---|---:|
| total locations | 19 |
| anomalies | 7 |
| derelicts | 5 |
| resource phenomena | 7 |
| minimum routine mining-ship approach | 33,705.001 s |
| maximum routine mining-ship approach | 1,003,685.251 s |
| total accessible derelict salvage | 24,000,000 kg |

These are evidence for the current seed/profile, not universal constants.

## Discovery boundary

`Stage20SpecialLocationDiscovery` creates Stage-20G observations only after caller-supplied physical
sensor/recon/survey evidence. Weak observations remain `DETECTED`; classification obeys each
location's scan requirement; exact static SI location requires a physical visit/survey.

Special sites use persistent `StaticObjectKind.SPECIAL_LOCATION`, but discovery knowledge never
receives authoritative salvage mass or linked occurrence reserve. The existing Stage-20G codec
round-trips the new stable kind without creating knowledge for undiscovered locations.

## Acceptance

- same accepted seed/profile produces equal location worlds and stable IDs;
- every current location kind is represented by bounded deterministic generation/repair;
- every location uses ordinary unbounded local-system SI coordinates;
- traffic distance and approach time are physical and positive;
- derelict streams are finite Stage-18 `SALVAGE_STREAM` / `SALVAGE_SITE` sources;
- resource phenomena reference existing finite occurrences exactly;
- hazards and unassessed security remain explicit;
- discovery/persistence adds no omniscience or reserve-truth leak.

Stage 20I next owns physical communications/intelligence transport latency. Stage 20K owns the final
campaign snapshot and migration policy for generated authoritative state.
