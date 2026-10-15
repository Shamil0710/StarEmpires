# Stage 23E — Resource Body Reference Freeze QA

**Scope:** four promoted Stage-20.5 resource-body atlas regions  
**Source atlas:** `src/main/resources/assets/stage20_5/resources/resource_body_atlas_v1.png`  
**Source Git blob:** `e82bb4f8f37895b201c019619f8951a51aa220f4`  
**Decision:** freeze the four existing region geometries when the exact branch head passes CI

## Evidence

The prior promotion audit already established that the four 320x320 regions:

- map exactly to carbonaceous, water/ice, metallic and mineral/silicate presentation roles;
- contain distinct authored bodies;
- expose only the broad occurrence classification already available to the player;
- do not encode reserve amount, grade, extraction yield or hidden strategic value;
- require no anchors because they are passive natural bodies.

`Stage23EResourceReferenceFreezeTest` adds the remaining executable Stage-23E reference checks:

- transparent region corners;
- 25% grayscale/downscale readability;
- 12.5% grayscale/downscale readability;
- minimum silhouette span/occupancy at both review scales;
- tonal separation after color removal;
- unique 12.5% silhouette signatures;
- pairwise near-duplicate rejection.

## Authority boundary

The atlas remains presentation-only. Freezing these references does not make pixels authoritative for:

- resource amount;
- grade;
- extraction rate;
- occurrence dimensions;
- discovery state;
- hidden composition.

No emissive/value overlay may later encode those properties.

## Freeze disposition

After exact-head CI passes, these rows are `FROZEN`:

- `ref.world.resource.carbonaceous.v1`;
- `ref.world.resource.water_ice.v1`;
- `ref.world.resource.metallic.v1`;
- `ref.world.resource.mineral_silicate.v1`.

No new resource-body concept generation is required for the v0.7 reference freeze.
