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
initializing a player. The independent-pilot new-start policy is not yet implemented by this change.

Change control and migration evidence: `docs/release/stage23b_player_checkpoint_change_control.md`.
A generator-profile change is not implied: no generated starter assets or new funding are added.

## Generator profile version

RC governance profile:

`se-gen-1`

This is a release metadata alias for the accepted Stage-20 production generator/calibration set.
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
