# Stage 23A — RC version identity policy v1

Status: **FROZEN FOR v0.7 RC TRAIN**

Current development checkpoint update (2026-10-06): native schema/file/runtime is v14.
This supersedes the v13/v12 development notes below. Paid-refit schema 3 and equipment-custody
schema 2 retain real held money and actor rights independently from storage. Native v1–v13
cannot claim those new fields; migration preserves existing data without asset or money grants.
Root payload framing, freight schema 7 and yard construction schemas 1/2 remain unchanged.
Campaign paid service and private module transport are connected and targeted checks pass;
ordinary acquisition and full 23B verification remain incomplete. The RC train is unchanged.

Current development checkpoint update (2026-10-06): native schema/file/runtime is v13.
This supersedes v12 envelope references in the historical entries below. Repair schema 2 preserves
actual held money and original civilian operator together with physical work; owner-only states
still encode schema 1. Supported native v1–v12 cannot reconstruct or claim the new payment.
Freight schema 7, finished-product handling and yard construction schemas 1/2 retain their framing.
New-game seller intelligence uses existing player/knowledge/journal payloads and never runs on
resume. This does not change the product RC train or establish full 23B acceptance.

## Application version

Product version train:

`0.7.0-rc.N`

where `N` is monotonically increasing for distributed RC candidates. The current Maven
`1.0-SNAPSHOT` value is legacy source-build metadata only and must not be presented as the v0.7
product version. Stage 23H owns embedding the product version into the distributable package.

## Content pack version

Release label:

`se-content-1`

The label never replaces the deterministic semantic content fingerprint. Any content-affecting
change must record the new fingerprint; ID removal/rename additionally requires migration/alias
evidence.

## Save compatibility identity

Stage-23 entry baseline:

- core `GameState`: `core=4`;
- `ContentBoundSaveCodec` envelope: `envelope=2`;
- M22.8 generated campaign checkpoint: `campaign=4`;
- campaign runtime identity: `m22.8.generated-campaign.v4`.

A schema-affecting change increments the owning schema/file version and adds supported old-version
fixtures. Failed migration must leave the original checkpoint unchanged.

## 23B composed player checkpoint change

The Stage-23 entry baseline above remains historical evidence. The current 23B checkpoint advances
only the composed owner to `campaign=5` / `m22.8.generated-campaign.v5`. Core, content envelope and
the embedded Stage-21/A/B/C/M formats retain their accepted identities. Native v1–v4 adopt without
initializing a player. This persistence change alone does not initialize a player. The subsequent explicit new-game opening profile is recorded below.

Change control and migration evidence: `docs/release/stage23b_player_checkpoint_change_control.md`.
A generator-profile change is not implied: no generated starter assets or new funding are added.

## Generator profile version

Stage-23 entry governance profile: `se-gen-1`.

Current new-game release profile: `se-gen-3` (unchanged accepted Stage-20 generator plus explicitly confirmed `se-pilot-start-2`; preceding opening alias was `se-gen-2`).

These are release metadata aliases. `se-gen-2` records the subsequent new-game physical market/player composition; it does not relabel saved Stage-20 generation identities.
Any change that can alter newly generated physical world state requires a generator-profile bump and
representative seed-corpus rerun. Existing saves are never silently regenerated because the profile
changed.

## Candidate identity tuple

Every release note and RC artifact must record:

```text
applicationVersion
sourceSha
contentVersion
contentFingerprint
saveCoreVersion
saveEnvelopeVersion
campaignSaveVersion
campaignRuntimeVersion
generatorProfile
packageSha256
```

Two candidates that differ in any field are different release identities.

## 23B physical pilot slice

Explicit new-game initialization uses `se-pilot-start-1`; its numeric fingerprint, disclosed money
sources and unchanged generator boundary are recorded in
`docs/release/stage23b_pilot_start_profile_v1.md`. Freight owning schema advances from 1 to 2 for
manual physical cargo provenance, with non-granting schema-1 adoption. Freight file format 1,
composed campaign v5, core/envelope/content and underlying Stage-20 generation identities remain unchanged; the new-game release alias advances to `se-gen-2`.

## 23B global physical-market composition

The subsequent `se-pilot-start-2` commissions finite markets at all existing physical endpoints and
adds stock-responsive SI-kilogram quotes through the shared MarketSystem scarcity rule. Because
new-game physical composition changes, the release alias advances to `se-gen-3`; the full fixed
seed corpus must be rerun. Existing markers retain v1 policy and existing saves are not
recommissioned, refunded or regenerated. Owning save schemas/formats remain unchanged. Numeric
policy/fingerprint and source declarations: `docs/release/stage23b_pilot_start_profile_v2.md`.

## 23B personal extraction cargo

Owning freight schema 4 distinguishes finite-source personal extraction lots from paid market lots.
The existing binary file format 1 and field layout remain unchanged. Supported schemas 1/2/3 adopt
without resource or ownership grants, and new extraction semantics falsely marked as an older
schema reject. Composed campaign v5 and underlying generation/opening-resource identities are
unchanged. Details: `docs/release/stage23b_player_checkpoint_change_control.md`.

## 23B personal mining commands

Owning freight schema 5 appends persistent personal mining intents and their last processed tick.
Historical schemas 1–4 retain their exact original layouts and adopt with no intent or equipment.
World fingerprint and physical-position normalization preserve these rows. Native composed campaign
v5, generation identity and opening-resource profiles are unchanged. The mining module's explicit
5 km working envelope and 1 m/s drift limit are SI engineering content, not legacy map conversions.
Command and continuation evidence: `docs/ui/stage23b_production_ui_consolidation.md`.

## 23B manufactured mining equipment

The generated-campaign session explicitly admits the common authored freight/mining product
vocabulary during both new-session and saved-session restoration. No finished opening products,
equipment, money or ownership are granted. Product mass/storage metadata and the existing ordinary
industrial inventory codec are reused, so this slice changes no owning save schema, file framing,
generation identity or numerical opening profile. Same-hull fitting remains a proposal until actual
manufacturing, shipyard work, command rights and material consideration have been settled.

### 23B native manufacturing queue and sample evidence

Personal manufacturing now executes explicit `player-manufacturing:v1:` rows in the accepted
Stage18 process format. Exact reserved recipe mass, fractional progress and completed-tick
watermark retain the existing industrial schema/file framing. Physical custody still occupies
station capacity and is rebound from orders before a restored runtime is exposed. Historical
unrelated process rows stay passive; restore does not seed jobs, stock, facilities or ownership.

Recovered personal excavation cargo can add actual sample evidence to the existing actor-local
discovery sidecar. It does not generate reserve/grade estimates or copy another actor's knowledge.
Campaign framing remains v5, freight remains its current independently versioned schema, and no
opening economy/profile or generation identity changes in this manufacturing/evidence slice.

### 23B personal journal envelope v6 (2026-10-04)

The subsequent personal-journal increment supersedes the v5 framing statement above: native
M22.8 campaign **schema/file v6** appends a bounded schema-1 journal after the optional player
payload. Native v1–v5 remain readable with an empty non-granting journal. The player's existing
contract, Stage18 industrial/discovery and freight v5 retain their independent formats. The journal
retains up to 2048 actual events and persists acknowledgement; it never reconstructs historical
actions on migration or reload. This is an active 23B development format, not a final RC declaration.


### 23B individual used-equipment envelope v7 (2026-10-04)

Current development framing is now native M22.8 schema/file v7, superseding the v6
statement above. Schema-1 individual module custody follows the journal and preserves
physical identity, station, original removal tick, integrity and service age. Native v1–v6
remain readable with empty equipment custody; v6 keeps its existing journal unchanged.
Pristine product counts and industrial/player/freight/discovery codecs retain their own
formats. Storage occupancy combines actual stock, production reservations and used equipment.
Ordinary player yard queues and equipment logistics remain unfinished. No final RC is declared.

### 23B reserved repair envelope v8 (2026-10-04)

Current development framing is native M22.8 schema/file v8. A bounded schema-1 repair
queue follows used-equipment custody and retains the actual source fit/damage, station,
fleet/asset, installed yard, reserved physical commodity masses, work and completed-tick watermark.
Native v1–v7 remain readable with empty repair work; migration preserves existing owners,
including v7 used-equipment custody and journal. Industrial/player/freight formats are unchanged.
Repair reservations share actual storage capacity with stock, manufacturing and used modules.
This implements own-station repair, not commercial-yard payment or ordinary yard acquisition.
Stage 23B remains ACTIVE/PARTIAL; no final RC is declared.

### 23B physical refit envelope v9 (2026-10-04)

Current development framing is native M22.8 schema/file v9. A bounded schema-1 refit queue
follows repair work, retaining exact equipment escrow, source/target fitting, original ship,
station/installed yard, work and completed-tick watermark. Native v1–v8 remain readable with
empty refit work; v8 preserves pending repair, used modules and journal. Player, freight v5
and industrial payload shapes remain unchanged. Freight admits a specifically owner-bound
mining fitting with an eight-million-kilogram hold, retaining original generated pool/hull identity.
Ordinary baseline freight keeps its historical compatibility identifiers and twelve-million-kilogram hold.
The new freight excavation module has its own ID and paid production/service profiles; original
mining module definitions are retained. No opening inventory, ownership, work or money is granted.
This remains ACTIVE/PARTIAL development framing, not a final RC.

### 23B finished-product handling envelope v11 (2026-10-05)

The finished-product handling slice introduced native M22.8 schema/file v11, with bounded schema-1 pending finished-product handling
after the module transfer payload. Embedded freight remains schema 7. Historical v1–v10 framing
is retained and receives an empty handling queue without stock or completed work. Native admission
jointly validates physical source stock, personal ownership, idle fleet, conflicting work and tick
watermarks. Real LOAD/UNLOAD/CANCEL commands, finite physical ticks, reservations, continuation,
production UI and journal are integrated; ordinary asset acquisition remains open in 23B.

### 23B individual equipment transport envelope v10 (2026-10-04)

Current development framing is native M22.8 schema/file v10. Bounded module handling
schema 1 follows refit work; native v1–v9 migrate with no handling work grants.
Exact custody may now identify an existing personal freight hold. Freight schema 6 adds
one finite carried-equipment mass value per fleet after legal affiliation; schemas 1–5
decode with zero equipment mass. Ordinary stock and cargo-lot formats remain unchanged.
Composed validation requires exact aboard identities, full authored mass, compatible capacity,
personal ownership and matching physical engineering cargo. Equipment is not pristine stock.
This is ACTIVE/PARTIAL development framing; ordinary acquisition and other 23B work remain open.

### 23B finished-product freight schema 7 (2026-10-05)

The outer M22.8 envelope remains native v10. Its independently versioned freight sidecar is now
schema 7, appending bounded provenance for countable finished goods after mining intents. Original
schemas 1–6 retain their exact layouts and migrate with no finished goods. New rows retain shared
lot identity, existing personal fleet, authored product, whole count, actual source station and loading
time. Product counts must match the hold and contribute to full fitted mass with raw cargo and used
equipment. Destruction removes aboard goods without delivery. Future loading provenance is invalid.
Persistent player product handling/UI are still unfinished; this is not a final RC or stage acceptance.

### Current 23B native envelope v12 (2026-10-05)

This entry supersedes the outer-envelope status of the preceding development slices.
Current native schema/file is v12. Bounded yard-construction schemas 1/2 follow finished-product
handling; freight remains schema 7. Supported v1–v11 layouts migrate with empty yard construction,
without granting material, structures, work or operating resources. Player/world transitions retain
the queue. Existing generation fingerprints remain unchanged; physical yard bills have their own
authored fingerprint. Pending construction must agree with the real owner, storage and world tick;
completed evidence must retain the exact installed yard. Starting/cancelling physical construction,
shared tick work, journal and production UI are connected. Schema 2 retains partial bill-limited
site custody, with shared finite handling and no general warehouse grant. Schema 1 retains the
original full-bill warehouse reserve. Ordinary economic acquisition of all materials and
resource allocation to a working yard remain incomplete. This is ACTIVE/PARTIAL,
not a final RC or stage acceptance.
Текущий 23B native writer на 2026-10-06: **v15** (`m22.8.generated-campaign.v15`).
Он добавляет личное физическое условие supply contract; старые v1–v14 мигрируют без
новых обязательств, а новый predicate под старым header отклоняется. Это частичный
срез 23B, не завершение этапа или финальный проверенный release revision.
