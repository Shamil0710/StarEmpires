# Star Empires v0.7 RC release notes template

## Candidate identity

- Application: `0.7.0-rc.<N>`
- Source SHA: `<git-sha>`
- Content version: `se-content-1`
- Content fingerprint: `<sha256>`
- Save: `core=4 / envelope=2 / campaign=16 / m22.8.generated-campaign.v16`
- Generator profile: `se-gen-3` (preceding opening alias `se-gen-2`; Stage-23 entry alias `se-gen-1`; underlying saved generation IDs retained)
- Package SHA-256: `<sha256>`
- Build environment/provenance: `<jdk/os/workflow/run>`

## Scope

- MUST_SHIP changes:
- MAY_SHIP changes:
- POST_RC requests explicitly not included:

## Compatibility

- Current development manifest: `stage23b.freight-initial-mining-reserve.v1` requires native v16;
  historical manifests restore without issuing equipment or assets. 23B remains ACTIVE/PARTIAL.
- Supported save inputs:
- Migrations exercised:
- Content-ID aliases/retirements:
- Generator-profile compatibility:

## Fixed defects

For every BLOCKER/CRITICAL item record issue, owner, PR/SHA and acceptance evidence.

## Known issues

List only issues allowed by `docs/release/rc_known_issues_v1.tsv`, including player impact and
workaround.

## Validation

- full Java-17 verify:
- deterministic campaign corpus:
- tactical/carrier corpus:
- performance/long-session:
- accessibility/localization:
- migration/corruption recovery:
- clean-machine install/run/save/load:
- exact packaged-artifact launch:

## Notes for testers

- focus areas:
- diagnostics bundle instructions:
- rollback/recovery instructions:
