# ref.industrial_union.station.industrial_station.v1 — clean-master acceptance review

**Status:** CLEAN MASTER ACCEPTED — REPOSITORY PERSISTENCE STILL REQUIRED  
**Selected geometry:** batch-001 `candidate_01`, 92/100  
**Reference mode:** `GENERATE_5_SELECT_1`

## Accepted clean-master candidate

A geometry-preserving 1024x1024 transparent clean master for the already selected candidate 01 was
recovered and reviewed.

Pinned recovery facts:

- filename: `union_industrial_station_candidate01_clean_master_1024.png`;
- byte size: **828,751**;
- SHA-256:
  `1980e0faedc652f2ed8a243c02b1b2f7638a83c61165de8692be57b4673b0163`;
- canvas: **1024x1024 RGBA**;
- alpha>16 visible bounds: x **106..916**, y **96..933**;
- visible span: **811 x 838** px;
- corner alpha: **0 / 0 / 0 / 0**.

The clean master is **not yet repository authority** because its exact PNG bytes are not committed under
the canonical reference package.

## Geometry-preservation proof

The retained batch-001 review preview is 512x512. The clean master was downscaled to 512x512 and
compared by alpha occupancy against that selected preview.

- alpha-mask IoU: **0.9813522617901829**;
- intersection cells: **73,413**;
- union cells: **74,808**;
- differing alpha cells: **1,395**.

This is strong evidence that the selected primary silhouette and mass placement were preserved. The
residual difference is localized around cleaned edges and the explicitly authored mechanism details
required by the original clean-master gate.

## Required mechanism readability

The clean master makes two different manufacturing mechanisms explicit:

- representative **stowed gantry** on the left-central production structure;
- distinct **stowed assembly rig** on the right-central production structure.

Review anchors:

- service core: approximately `(512, 512)`;
- stowed gantry: approximately `(315, 450)`;
- stowed assembly rig: approximately `(730, 580)`;
- repeated production cells: `(260,350)`, `(765,350)`, `(260,650)`, `(765,650)`.

Every listed anchor has substantial authored alpha coverage within a 55 px review radius.

## Authority boundary

The reference does not define manufacturing throughput, build queues, dock/storage capacity,
construction recipes, station collision/physical envelope, ownership or production authority.

## Freeze gate

Before `FROZEN`:

1. persist the exact accepted PNG at
   `art_sources/stage23e/references/ref.industrial_union.station.industrial_station.v1/selected/reference_master.png`;
2. verify committed SHA-256 equals the pinned value;
3. record the Git blob SHA;
4. verify grayscale, 25%, 12.5%, silhouette and anchor review against committed bytes;
5. run exact-head CI;
6. only then set `geometry_frozen=true`.

No further image generation or candidate selection is required unless the recovered PNG fails byte
identity or final QA.
