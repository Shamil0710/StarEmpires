package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapLabelLayoutTest {
    private static final MapLabelLayout.Box MAP = new MapLabelLayout.Box(0f, 0f, 900f, 560f);
    private static final float GAP = 4f;
    private static final float MIN_LEADER = 8f;

    @Test
    void lonePlateSitsRightOfItsObjectWithoutLeader() {
        var placed = place(List.of(request("a", 300f, 200f, 10f, 0, false)));
        assertEquals(1, placed.size());
        var plate = placed.get(0).plate();
        assertEquals(314f, plate.x(), 1e-4f);
        assertEquals(192f, plate.y(), 1e-4f);
        assertNull(placed.get(0).leader(), "adjacent plate needs no leader");
        assertFalse(placed.get(0).onObject());
    }

    @Test
    void neighbourPlatesAreSeparatedAndNeverCoverMarkers() {
        var requests = List.of(request("a", 300f, 200f, 10f, 0, false), request("b", 300f, 212f, 10f, 1, false));
        var placed = place(requests);
        assertEquals(2, placed.size());
        assertFalse(placed.get(0).plate().overlaps(placed.get(1).plate()));
        assertNoForeignMarkerCovered(markersOf(requests), placed);
        assertTrue(placed.get(1).plate().right() <= 300f, "second plate moves to the left side");
    }

    @Test
    void unlabelledMarkersStillBlockPlates() {
        // A fleet label must not land on a nearby resource that is not labelled itself.
        var fleet = request("fleet", 310f, 200f, 12f, 0, false);
        var markers = List.of(new MapLabelLayout.Marker("fleet", 310f, 200f, 12f),
                new MapLabelLayout.Marker("resource", 350f, 200f, 10f));
        var placed = MapLabelLayout.place(List.of(fleet), markers, MAP, List.of(), GAP, MIN_LEADER);
        assertEquals(1, placed.size());
        assertNoForeignMarkerCovered(markers, placed);
    }

    @Test
    void crowdedGroupUsesTheSecondRingWithLeaders() {
        var requests = new ArrayList<MapLabelLayout.Request>();
        for (int index = 0; index < 9; index++) {
            requests.add(request("contact." + index, 400f + (index % 3) * 6f, 280f + (index / 3) * 6f, 8f, index, false));
        }
        var placed = place(requests);
        assertTrue(placed.size() >= 6, "most of a nine-contact cluster stays labelled: " + placed.size());
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                assertFalse(placed.get(i).plate().overlaps(placed.get(j).plate()),
                        placed.get(i).id() + " overlaps " + placed.get(j).id());
            }
            assertTrue(placed.get(i).plate().inside(MAP));
        }
        assertNoForeignMarkerCovered(markersOf(requests), placed);
        assertTrue(placed.stream().anyMatch(p -> p.leader() != null), "displaced plates point back with a leader");
        for (var placement : placed) {
            var leader = placement.leader();
            if (leader == null) continue;
            var owner = requests.stream().filter(r -> r.id().equals(placement.id())).findFirst().orElseThrow();
            float start = (float) Math.hypot(leader.x1() - owner.anchorX(), leader.y1() - owner.anchorY());
            assertEquals(owner.clearance(), start, 1e-3f, "leader starts on the clearance edge");
            assertTrue(placement.plate().x() - 1e-3f <= leader.x2() && leader.x2() <= placement.plate().right() + 1e-3f);
            assertTrue(placement.plate().y() - 1e-3f <= leader.y2() && leader.y2() <= placement.plate().top() + 1e-3f);
        }
    }

    @Test
    void plateFlipsInsideNearTheMapEdge() {
        var placed = place(List.of(request("edge", 890f, 550f, 6f, 0, false)));
        assertEquals(1, placed.size());
        assertTrue(placed.get(0).plate().inside(MAP));
        assertTrue(placed.get(0).plate().right() <= 890f, "plate goes left of an object at the right edge");
    }

    @Test
    void crowdedOptionalLabelIsHiddenButTheSelectionIsKept() {
        var requests = new ArrayList<MapLabelLayout.Request>();
        for (int index = 0; index < 40; index++) {
            requests.add(request("wall." + index, 100f + (index % 8) * 4f, 100f + (index / 8) * 4f, 4f, 0, false));
        }
        requests.add(request("optional", 112f, 108f, 4f, 5, false));
        requests.add(request("selected", 114f, 110f, 4f, 9, true));
        var placed = place(requests);
        assertTrue(placed.stream().noneMatch(p -> p.id().equals("optional")), "crowded optional label is hidden");
        var selected = placed.stream().filter(p -> p.id().equals("selected")).findFirst();
        assertTrue(selected.isPresent(), "selection is always labelled");
        assertTrue(selected.get().plate().inside(MAP));
    }

    @Test
    void selectionNearTheTitleIsPlacedClearOfIt() {
        var title = new MapLabelLayout.Box(0f, 502f, 850f, 58f);
        var selected = new MapLabelLayout.Request("selected", 300f, 559f, 21f, 80f, 20f, 0, true);
        var placed = MapLabelLayout.place(List.of(selected), List.of(new MapLabelLayout.Marker("selected", 300f, 559f, 21f)),
                MAP, List.of(title), GAP, MIN_LEADER);
        assertEquals(1, placed.size());
        assertFalse(placed.get(0).plate().overlaps(title), "the selection label avoids the map title");
        assertTrue(placed.get(0).plate().inside(MAP));
    }

    @Test
    void hullFillingTheViewGetsItsPlateOnTheHullClearOfOtherMarkers() {
        var placed = place(List.of(request("freighter", 450f, 280f, 600f, 0, true)));
        assertEquals(1, placed.size());
        var placement = placed.get(0);
        assertTrue(placement.onObject());
        assertNull(placement.leader());
        assertEquals(450f, placement.plate().x() + placement.plate().width() / 2f, 1e-3f);
        assertEquals(280f, placement.plate().y() + placement.plate().height() / 2f, 1e-3f);

        var offCentre = place(List.of(request("freighter", 880f, 5f, 600f, 0, true))).get(0);
        assertTrue(offCentre.plate().inside(MAP), "plate on a huge hull is clamped into the map");

        // A contact right at the hull centre keeps its marker visible.
        var markers = List.of(new MapLabelLayout.Marker("freighter", 450f, 280f, 20f),
                new MapLabelLayout.Marker("contact", 450f, 280f, 10f));
        var shifted = MapLabelLayout.place(List.of(request("freighter", 450f, 280f, 600f, 0, true)), markers,
                MAP, List.of(), GAP, MIN_LEADER).get(0);
        assertTrue(shifted.onObject());
        assertNoForeignMarkerCovered(markers, List.of(shifted));
    }

    @Test
    void markerJustOffTheMapEdgeStillBlocksPlates() {
        // The neighbour's centre lies outside the map but its marker reaches inside.
        var markers = List.of(new MapLabelLayout.Marker("object", 790f, 200f, 23f),
                new MapLabelLayout.Marker("edge", 905f, 200f, 20.88f));
        var request = new MapLabelLayout.Request("object", 790f, 200f, 23f, 80f, 20f, 0, false);
        var placed = MapLabelLayout.place(List.of(request), markers, MAP, List.of(), GAP, MIN_LEADER);
        assertEquals(1, placed.size());
        assertNoForeignMarkerCovered(markers, placed);
    }

    @Test
    void selectedHugeHullUnderTheTitleStillGetsAPlateClearOfIt() {
        var title = new MapLabelLayout.Box(0f, 502f, 850f, 58f);
        var request = new MapLabelLayout.Request("hull", 300f, 559f, 1200f, 80f, 20f, 0, true);
        var placed = MapLabelLayout.place(List.of(request), List.of(new MapLabelLayout.Marker("hull", 300f, 559f, 20f)),
                MAP, List.of(title), GAP, MIN_LEADER);
        assertEquals(1, placed.size(), "the selection is always labelled");
        assertTrue(placed.get(0).onObject());
        assertFalse(placed.get(0).plate().overlaps(title));
        assertTrue(placed.get(0).plate().inside(MAP));
    }

    @Test
    void largeHullWithRoomIsLabelledPastItsEdge() {
        var placed = place(List.of(request("freighter", 400f, 280f, 245f, 0, true))).get(0);
        assertFalse(placed.onObject());
        assertEquals(400f + 245f + GAP, placed.plate().x(), 1e-3f);
        assertTrue(placed.plate().inside(MAP));
    }

    @Test
    void hullClearanceKeepsPlatesOffARotatedHull() {
        // 300 x 300 px hull turned 45 degrees reaches 212 px from its centre along the axes.
        float clearance = MapLabelLayout.hullClearance(300f, 300f);
        assertEquals(212.13f, clearance, 1e-2f);
        for (float heading : new float[] {0f, 30f, 45f, 90f, 135f}) {
            var hull = SelectionBrackets.around(450f, 280f, 300f, 300f, heading, 0f, 0f);
            var plate = place(List.of(request("hull", 450f, 280f, clearance, 0, true))).get(0).plate();
            assertTrue(plate.x() >= hull.right() - 1e-3f || plate.right() <= hull.left() + 1e-3f
                            || plate.y() >= hull.top() - 1e-3f || plate.top() <= hull.bottom() + 1e-3f,
                    "plate clears the hull bounds at heading " + heading);
        }
        // The diagonal bound holds a long hull at every heading.
        float longClearance = MapLabelLayout.hullClearance(490f, 100f);
        for (float heading = 0f; heading < 180f; heading += 15f) {
            var hull = SelectionBrackets.around(0f, 0f, 490f, 100f, heading, 0f, 0f);
            assertTrue(Math.max(hull.right(), hull.top()) <= longClearance + 1e-3f, "heading " + heading);
        }
    }

    @Test
    void platesAvoidFixedMapOverlays() {
        var title = new MapLabelLayout.Box(0f, 500f, 620f, 60f);
        var scaleBar = new MapLabelLayout.Box(0f, 0f, 200f, 40f);
        var requests = List.of(request("near-title", 300f, 490f, 8f, 0, true),
                request("near-bar", 60f, 50f, 8f, 1, false));
        var placed = MapLabelLayout.place(requests, markersOf(requests), MAP, List.of(title, scaleBar), GAP, MIN_LEADER);
        assertEquals(2, placed.size());
        for (var placement : placed) {
            assertFalse(placement.plate().overlaps(title), placement.id() + " covers the map title");
            assertFalse(placement.plate().overlaps(scaleBar), placement.id() + " covers the scale bar");
        }
    }

    @Test
    void placementIsDeterministicByPriorityThenId() {
        var a = request("b", 300f, 200f, 10f, 1, false);
        var b = request("a", 300f, 212f, 10f, 1, false);
        var c = request("z", 300f, 206f, 10f, 0, false);
        assertEquals(List.of("z", "a", "b"), place(List.of(a, b, c)).stream().map(MapLabelLayout.Placement::id).toList());
        assertEquals(place(List.of(a, b, c)).stream().map(MapLabelLayout.Placement::plate).toList(),
                place(List.of(c, b, a)).stream().map(MapLabelLayout.Placement::plate).toList());
    }

    private static List<MapLabelLayout.Placement> place(List<MapLabelLayout.Request> requests) {
        return MapLabelLayout.place(requests, markersOf(requests), MAP, List.of(), GAP, MIN_LEADER);
    }

    /** Markers of small objects: the marker radius equals the clearance. */
    private static List<MapLabelLayout.Marker> markersOf(List<MapLabelLayout.Request> requests) {
        return requests.stream()
                .map(r -> new MapLabelLayout.Marker(r.id(), r.anchorX(), r.anchorY(), Math.min(r.clearance(), 20f)))
                .toList();
    }

    private static MapLabelLayout.Request request(String id, float x, float y, float clearance, int priority,
            boolean required) {
        return new MapLabelLayout.Request(id, x, y, clearance, 60f, 16f, priority, required);
    }

    private static void assertNoForeignMarkerCovered(List<MapLabelLayout.Marker> markers,
            List<MapLabelLayout.Placement> placed) {
        for (var placement : placed) {
            for (var marker : markers) {
                if (marker.id().equals(placement.id())) continue;
                assertFalse(placement.plate().coversDisc(marker.x(), marker.y(), marker.radius()),
                        placement.id() + " covers the marker of " + marker.id());
            }
        }
    }
}
