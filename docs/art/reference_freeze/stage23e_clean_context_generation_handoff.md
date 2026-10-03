# Stage 23E — Clean-Context Reference Generation Handoff

**Purpose:** resume blocked Stage-23E visual-reference work in a fresh image-generation context without
repeating rejected batches or losing accepted geometry.

This is an execution handoff, not a replacement for:

- `docs/stage23e_visual_reference_freeze.md`;
- `docs/stage23e_generation_prompt_catalog.md`;
- faction visual bibles;
- `docs/art/reference_freeze/stage23e_reference_manifest.tsv`.

## 1. Why a clean context is required

The current image context has reproducibly ignored object/faction/target instructions:

- Union station requests produced Imperial station boards;
- a one-candidate Union proof still produced seven Imperial concepts;
- energetic anomaly produced a Stage-23E UI/dashboard;
- explicit refinery clean-master edit produced a Stage-23E dashboard;
- small-craft/derelict edit requests produced unrelated station sheets.

These are recorded hard rejects with zero reference authority.

Do not "prompt harder" inside the contaminated context.

## 2. Clean-session protocol

Start the next image session with the image task itself.

Before the first image call:

1. do **not** recap project status in visual/dashboard language;
2. do **not** show any rejected station sheet/dashboard as an image reference;
3. load only the exact visual-DNA images needed for that one family;
4. state the canonical output lock and one asset-role prompt;
5. request one object per image;
6. if a proof call returns the wrong class/faction, stop that family immediately and record the reject.

A family may use five sequential one-candidate calls to satisfy `GENERATE_5_SELECT_1` when that is more
reliable than one multi-image backend request. The canonical contract remains exactly five independent
candidate images.

## 3. Do not regenerate already accepted clean masters

Four references already have accepted 1024x1024 clean-master byte identities:

| Reference | Candidate | SHA-256 | Remaining blocker |
| --- | --- | --- | --- |
| Empire refinery | candidate_02 | `2b5fbf5d23262c6d2bac619b106495ca16d33c37de1637bfbb2b0faa80ee9fe8` | persist exact PNG |
| Empire high-tech | candidate_05 | `2f59e3d89d97ec14ce0bcc3de93204aad367fe6686cc7a047dc61524bbe7395c` | persist exact PNG |
| Empire frontier | candidate_03 | `8a57860f9e6bb8aa273d30966d64a1d6428e493048cb2debef01cd45dc1fafd2` | persist exact PNG |
| Union industrial station | candidate_01 | `1980e0faedc652f2ed8a243c02b1b2f7638a83c61165de8692be57b4673b0163` | persist exact PNG |

Use `stage23e_pending_master_contracts.tsv` to validate recovered bytes.

Do not replace these four accepted masters merely because a new session has a working generator.

## 4. Resume order

### 4.1 Binary persistence first

If the runtime has binary Git write capability, persist the four accepted masters above before any new
generation. Then run their committed-byte QA and freeze only after exact-head CI.

### 4.2 Empire carrier small-craft base

The original batch-001 candidate 03 remains useful art direction, but no provable source/master PNG was
recovered.

Therefore this reference requires a **new valid five-candidate discovery batch**.

Inputs:

- real Empire production corvette;
- real Empire production carrier;
- Empire faction visual bible;
- Generation Catalog small-craft base prompt;
- authoritative 28 x 12 x 6 m / 180 t envelope already recorded by the reference program.

Output rules:

- five independent single-craft images;
- strict top-down;
- forward/right;
- transparent;
- no fit-specific weapon/shield;
- no atmospheric wing/fins;
- no miniature-capital-ship treatment.

Only after the base is `FROZEN` may Empire interceptor/defence/strike derivations begin.

### 4.3 Union mining-outpost proof, then Union station queue

First make one **non-authoritative proof** image for the Union mining outpost.

Provide only Union production visual DNA, preferably current Union corvette + freight references.

Proof must demonstrate:

- graphite/mill-steel construction;
- olive/slate utility armor;
- light standardized assembly panels;
- restrained ochre/amber service accents;
- muted teal instrumentation;
- repeated modular extractor/handling/storage grammar;
- absolutely no Imperial ivory/burgundy/heraldry/citadel language.

If proof passes, discard it as proof and execute the required five-candidate mining-outpost batch.

Then continue remaining Union station roles in manifest order:

1. volatile depot;
2. refinery complex;
3. high-tech hub;
4. trade/logistics hub;
5. naval ordnance depot;
6. frontier multipurpose.

The Union industrial station does **not** need generation; its clean master is already accepted.

### 4.4 Special locations

Before a five-candidate anomaly batch, require one proof image that is:

- transparent;
- text/UI-free;
- non-solid;
- localized/bounded field phenomenon;
- no station/ship/portal architecture.

If proof passes:

1. energetic anomaly — five candidates;
2. resonant resource phenomenon — five candidates, visually distinct and explicitly an overlay around an
   existing resource body rather than a second asteroid.

### 4.5 Geometry-preserving edits

After proof that image targeting works again:

- Union interceptor/defence/strike: paint the already-authoritative technical overlays without changing
  the frozen base hull;
- escort-hull derelict: preserve existing production geometry and only suppress normal live-state
  navigation/service emissions;
- never regenerate an unrelated hull to solve an edit failure.

## 5. Immediate hard-reject rules

Hard reject before scoring if any output:

- is the wrong object class;
- is the wrong faction;
- is a dashboard/UI/status sheet;
- contains text/labels;
- contains multiple candidates in one opaque presentation board;
- changes supplied immutable geometry during a derive/edit pass;
- invents a gameplay capability;
- bakes transient exhaust/fire/explosion;
- cannot be separated without repainting geometry.

A transparent multi-object backend canvas may be segmented **for candidate review only** under the
existing normalization exception. It cannot itself become `reference_master.png`.

## 6. Persistence discipline

For every accepted reference:

1. canonical PNG must physically exist in Git;
2. manifest must record its real Git blob SHA;
3. provenance must record generation/recovery identity;
4. grayscale/downscale/silhouette/anchor review must reference the committed bytes;
5. exact-head CI must pass;
6. only then may status become `FROZEN`.

Selection is not freeze.

## 7. Current clean-context stopping condition

The reference pre-production gate closes only when all 49 manifest rows are `FROZEN`.

Until then, broad Stage-23E production-layer generation remains blocked.


## 8. Machine-readable current work queue

The exact current unresolved-reference sequence is pinned in:

`docs/art/reference_freeze/stage23e_reference_work_queue.tsv`.

`Stage23EReferenceWorkQueueTest` requires that this queue contains every and only non-`FROZEN`
manifest row exactly once, with the current status, a concrete next action, proof gate and done
condition.

Update the queue in the same commit whenever a reference transitions to `FROZEN` or its blocker/action
changes.
