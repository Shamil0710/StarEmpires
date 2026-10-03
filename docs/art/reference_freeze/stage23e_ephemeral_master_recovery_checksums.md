# Stage 23E — Ephemeral Selected-Master Recovery Checksums

**Status:** RECOVERY EVIDENCE ONLY — NOT CANONICAL REFERENCE AUTHORITY

This record preserves hashes/geometry facts for selected working files that existed during the active
reference-review session but are **not** repository-persisted canonical masters.

A hash match may prove that a recovered file is the same reviewed working file. It does not by itself
promote the file to `FROZEN` or waive clean-master/QA requirements.

## Empire refinery complex

Reference: `ref.empire.station.refinery_complex.v1`  
Selected candidate: `candidate_02`, 94/100  
Ephemeral filename: `candidate_02_reference_master_1024.png`

- size: 740,524 bytes
- SHA-256: `2b5fbf5d23262c6d2bac619b106495ca16d33c37de1637bfbb2b0faa80ee9fe8`
- dimensions: 1024x1024
- alpha>16 bounds: x 148..875, y 92..931
- all four corner alpha values: 0

This file remains non-authoritative because the selected board-derived geometry has not completed the
independent clean-master acceptance path. The later explicit image-edit attempt was hard-rejected.

## Empire high-tech hub

Reference: `ref.empire.station.high_tech_hub.v1`  
Selected candidate: `candidate_05`, 94/100  
Ephemeral filename: `candidate_05_reference_master_1024.png`

- size: 458,750 bytes
- SHA-256: `2f59e3d89d97ec14ce0bcc3de93204aad367fe6686cc7a047dc61524bbe7395c`
- dimensions: 1024x1024
- alpha>16 bounds: x 92..931, y 298..725
- all four corner alpha values: 0

Recovery of this exact file avoids repeating candidate selection, but freeze still requires repository
persistence plus the normal clean-master acceptance/QA gate.

## Empire frontier multipurpose station

Reference: `ref.empire.station.frontier_multipurpose.v1`  
Selected candidate: `candidate_03`, 95/100  
Ephemeral filename: `candidate_03_reference_master_1024.png`

- size: 752,620 bytes
- SHA-256: `8a57860f9e6bb8aa273d30966d64a1d6428e493048cb2debef01cd45dc1fafd2`
- dimensions: 1024x1024
- alpha>16 bounds: x 92..931, y 104..919
- all four corner alpha values: 0

Recovery of this exact file avoids repeating candidate selection, but it is not a canonical master until
the clean-master and freeze gates are satisfied.

## Industrial Union industrial-station selected preview

Reference: `ref.industrial_union.station.industrial_station.v1`  
Selected candidate: `candidate_01`, 92/100  
Recovered preview filename: `candidate_01_preview.png`

- size: 51,019 bytes
- SHA-256: `ae94ebb1fce06dbb9c0dd25b2490cbf605ac1de1f615a3a03097e48ae0733723`
- dimensions: 512x512
- alpha>16 bounds: x 53..458, y 48..466
- all four corner alpha values: 0

This is only the selected **review preview**, not a clean master. The accepted review still requires an
unambiguous stowed gantry and a distinct stowed assembly rig in the final 1024x1024 clean master.

## Recovery rule

If any of these files re-enters a later session:

1. calculate SHA-256 before modifying it;
2. match against this record;
3. if it matches, preserve the existing candidate-selection authority;
4. do not mark `FROZEN` until the file is committed under the canonical reference package;
5. run role-specific grayscale/downscale/silhouette/anchor QA;
6. let `Stage23EReferenceManifestIntegrityTest` verify the final repository source.
