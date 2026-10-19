# Star Empires v0.7 RC release notes template

## Candidate identity

- Application: `0.7.0-rc.<N>`
- Source SHA: `<git-sha>`
- Content version: `se-content-1`
- Content fingerprint: `<sha256>`
- Save: `core=4 / envelope=2 / campaign=31 / m22.8.generated-campaign.v31`
- Generator profile: `se-gen-3` (preceding opening alias `se-gen-2`; Stage-23 entry alias `se-gen-1`; underlying saved generation IDs retained)
- Package SHA-256: `<sha256>`
- Build environment/provenance: `<jdk/os/workflow/run>`

## Scope

- MUST_SHIP changes:
- MAY_SHIP changes:
- POST_RC requests explicitly not included:

## Compatibility

- Native v29 retains paid small-craft build escrow, actual shared-yard work and completed hulls awaiting explicit claim. Historical v1–28 receive an empty queue; loading grants no products, work, craft IDs or deliveries.

- Native v27 retains explicit existing-station industrial admission; historical v1–26 receive no admission or operating grants.
- Native v28 supports paid passive liquid-storage construction; exact capacity and liquid interface require completed work and an installed structure. Historical saves receive no extension.

- Native v26 retains explicit personal craft property independently of carrier ownership.
  Historical v1–25 receive no inferred property rights, acquisitions or replacement craft.
- Native v25 retains exact local craft positions/velocities and physical tick watermarks;
  historical v1–24 receive empty local-flight state without inferred craft positions or launches.
- Native v24 retains retired command-group membership/order history without restoring lost
  assets or issuing new events on load. Historical native v1–23 cannot claim retirement.

- Native v23 retains explicitly recorded crew observations; genuine v1–22 restore unknown
  staffing without crew grants. Native v22 retains exact consecutive extraction receipts;
  v21 declares additive industrial catalogs. Stage-20.5 format 5 retains sensor knowledge and
  admits historical formats 1–4. Stage-22 manifest v4 pins only this persistence extension;
  the unchanged historical v3 manifest remains available. 23B remains ACTIVE/PARTIAL.

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
