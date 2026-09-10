# Physical scale and inspection camera

The generated system map now uses a uniform pixels-per-metre projection for positions,
ship lengths/widths, station footprints and resolved resource dimensions. It no longer
spreads overlapping positions or stretches the X and Y axes independently. The snapshot
preserves resolved engineering dimensions instead of reducing them to an artwork binding.
Every hull found in the supplied engineering catalogue retains its physical dimensions,
even when its artwork uses a fallback role. Unknown content retains explicitly nominal
catalogue sizes; navigation anchors are markers, not invented physical bodies.

At overview distances, subpixel objects have selectable screen-space markers. The marker
and minimum click area do not enlarge the hull. Once resolved, hulls grow with camera zoom.
Transparent authoring margins are excluded from the visible object's dimensions.
Large sprites intersecting the viewport remain visible and are clipped at the map border.

Double-click an object on the system map, or a ship in Logistics/Military, to frame its
hull within 60% of the available viewport. Wheel zoom preserves the cursor anchor; middle
mouse pans; Home restores the fitted system overview. Projection coordinates and camera
pan use double precision until final screen coordinates, permitting inspection of a
100 m hull in a trillion-metre system. Tactical zoom increases from 6 to 256; tactical
hulls no longer receive role-dependent scale multipliers or fixed pixel size floors.

This is a presentation change. Simulation dimensions, mass, positions, collision geometry,
travel times, faction content and the M22.6 human acceptance state remain authoritative.
The current generated runtime still uses its installed minimum artwork catalogue; this
change does not claim to complete integration of every Stage-22 production asset.

Regression coverage: uniform axis scale, 10:1 hull length ratio at shared zoom, coincident
positions, deep-zoom focus/pan/cursor preservation and generated engineering hull sizes.
Manual check: launch run-generated-world.bat, pause with Space, double-click a local object,
zoom and pan across its edges, then Home. Repeat for a ship and station. Verify nearby
objects keep their relative proportions. Desktop/GL visual inspection is still required.
