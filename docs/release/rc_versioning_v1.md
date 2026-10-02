# Stage 23A — RC version identity policy v1

Status: **FROZEN FOR v0.7 RC TRAIN**

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
