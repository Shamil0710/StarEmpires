# Stage 19J.4 — Mouse Selection Implementation Record

**Parent:** `docs/stage19j_tactical_validation_viewer.md`  
**Scope:** presentation-only ship selection for the interactive tactical validation viewer

## Implemented contract

- left mouse click on a visible tactical ship selects that ship;
- left mouse click on empty tactical space clears selection;
- selection stores only the stable authoritative entity ID;
- selection persists across ordinary simulation ticks while the entity remains present in the immutable visual snapshot;
- selection is cleared when the entity disappears from the snapshot or when the current scenario is reset;
- overlapping markers resolve deterministically to the closest marker center, with entity ID as the final stable tie-break;
- selected ships receive a high-contrast double-ring highlight using their ALPHA/BETA side palette plus a heading tick;
- the HUD reports selected entity ID, side, presentation role, integrity and wreck state.

## Screen-space semantics

libGDX pointer input uses a top-left Y origin while `WorldMapLayout` and the tactical camera use bottom-left screen coordinates. The desktop viewer converts click Y before hit testing:

```text
screenY = graphicsHeight - 1 - inputY
```

`ShipHitTestService` then uses the same `WorldMapLayout` projection as tactical rendering. Marker hit bounds include the Stage-19J role-specific silhouette envelope and a small interaction padding so narrow kinetic/beam hulls and wider missile/defensive-EW appendages remain practically clickable.

## Authority boundary

The implementation is deliberately outside combat authority:

```text
authoritative runtime
→ immutable TacticalPrototypeVisualSnapshot
→ ShipHitTestService
→ ShipSelectionController(entityId only)
→ TacticalSelectionOverlayRenderer / HUD
```

The selection path does **not**:

- issue tactical orders;
- change the actor's authoritative selected target;
- create or improve sensor tracks;
- alter movement, weapons, ammunition, shields, damage, power, heat or engineering state;
- keep a destroyed/disappeared entity alive for UI purposes.

## Regression coverage

`ShipSelectionControllerTest` covers:

1. deterministic nearest-marker selection under overlap;
2. rejection of empty tactical space;
3. persistence across a changed snapshot while the same entity remains present;
4. click-empty deselection;
5. stale-selection clearing when the entity no longer exists in the snapshot.

## Stage gate

19J.4 may be considered complete only after Java-17 CI/tests/Javadoc/package verification succeeds on the exact PR head and that exact head is merged into `main`.
