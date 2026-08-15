package com.spacesim.persistence;

import com.spacesim.constants.Constants;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameStateMigrationTest {
    @Test
    void stage3BinaryV1МигрируетПятьItemSlotsВТекущуюCapacity() {
        GameState baseline = SimulationSession.createDemo(0x51A7E4L).snapshot();
        EntityState legacyEntity = new EntityState(
                new EntityId(1L),
                null,
                null,
                new EntityState.InventoryState(100, List.of(11, 22, 33, 44, 55)),
                null,
                new EntityState.MarketState(
                        List.of(1, 2, 3, 4, 5),
                        List.of(0f, 1f, 2f, 3f, 4f),
                        List.of(10f, 20f, 30f, 40f, 50f),
                        List.of(9f, 18f, 27f, 36f, 45f),
                        List.of(0d, 0.1d, 0.2d, 0.3d, 0.4d),
                        List.of(true, false, true, false, true),
                        true),
                new EntityState.ProductionState(
                        List.of(new EntityState.RecipeState(
                                "legacy",
                                2f,
                                List.of(1, 0, 2, 0, 0),
                                List.of(0, 0, 0, 3, 0))),
                        0,
                        0.75f),
                new EntityState.PriceHistoryState(
                        20,
                        List.of(
                                List.of(1f),
                                List.of(2f, 3f),
                                List.of(),
                                List.of(4f),
                                List.of(5f))),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        GameState currentWithLegacyShape = new GameState(
                GameState.CURRENT_VERSION,
                baseline.rootSeed(),
                baseline.clock(),
                baseline.nextEntityIdValue(),
                baseline.eventRandomState(),
                baseline.asteroidRandomState(),
                baseline.events(),
                baseline.asteroidSpawner(),
                baseline.priceRecorder(),
                baseline.ledger(),
                List.of(legacyEntity));

        byte[] currentBytes = GameStateCodec.encode(currentWithLegacyShape);
        byte[] withoutV3MarketBaseline = removeSecondConsecutiveBlock(
                currentBytes,
                encodedIntegerList(List.of(1, 2, 3, 4, 5)));
        byte[] legacyBytes = Arrays.copyOf(withoutV3MarketBaseline, withoutV3MarketBaseline.length - 1);
        ByteBuffer.wrap(legacyBytes).putInt(8, GameState.LEGACY_STAGE3_VERSION);

        GameState migrated = GameStateCodec.decode(legacyBytes);
        EntityState entity = migrated.entities().get(0);

        assertEquals(GameState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(Constants.MAX_ITEMS, entity.inventory().stock().size());
        assertEquals(List.of(11, 22, 33, 44, 55), entity.inventory().stock().subList(0, 5));
        assertEquals(0, entity.inventory().stock().get(5));
        assertEquals(0, entity.inventory().stock().get(Constants.MAX_ITEMS - 1));

        assertEquals(Constants.MAX_ITEMS, entity.market().tradableItems().size());
        assertEquals(true, entity.market().tradableItems().get(0));
        assertFalse(entity.market().tradableItems().get(5));
        assertEquals(0f, entity.market().sellPrices().get(5), 0f);
        assertEquals(0d, entity.market().consumptionRemainder().get(5), 0d);
        assertEquals(entity.market().targetStock(), entity.market().configuredTargetStock(),
                "Legacy effective target must migrate conservatively into the configured baseline");

        EntityState.RecipeState recipe = entity.production().recipes().get(0);
        assertEquals(Constants.MAX_ITEMS, recipe.inputs().size());
        assertEquals(2, recipe.inputs().get(2));
        assertEquals(3, recipe.outputs().get(3));
        assertEquals(0, recipe.outputs().get(5));

        assertEquals(Constants.MAX_ITEMS, entity.priceHistory().history().size());
        assertEquals(List.of(2f, 3f), entity.priceHistory().history().get(1));
        assertEquals(List.of(), entity.priceHistory().history().get(5));
        assertNull(entity.archetype());
    }

    @Test
    void binaryV2СохраняетArchetypeИМигрируетEffectiveTargetВBaseline() {
        GameState baseline = SimulationSession.createDemo(0x51A7E5L).snapshot();
        List<Integer> target = integerSlots(12);
        EntityState v2EntityShape = new EntityState(
                new EntityId(2L),
                null,
                null,
                new EntityState.InventoryState(100, integerSlots(20)),
                null,
                new EntityState.MarketState(
                        target,
                        floatSlots(0f),
                        floatSlots(10f),
                        floatSlots(9f),
                        doubleSlots(0d),
                        booleanSlots(true),
                        false),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new EntityState.ArchetypeState("station.agrodome"));
        GameState current = new GameState(
                GameState.CURRENT_VERSION,
                baseline.rootSeed(),
                baseline.clock(),
                baseline.nextEntityIdValue(),
                baseline.eventRandomState(),
                baseline.asteroidRandomState(),
                baseline.events(),
                baseline.asteroidSpawner(),
                baseline.priceRecorder(),
                baseline.ledger(),
                List.of(v2EntityShape));

        byte[] currentBytes = GameStateCodec.encode(current);
        byte[] v2Bytes = removeSecondConsecutiveBlock(currentBytes, encodedIntegerList(target));
        ByteBuffer.wrap(v2Bytes).putInt(8, GameState.ITEM_CAPACITY_ARCHETYPE_VERSION);

        GameState migrated = GameStateCodec.decode(v2Bytes);
        EntityState entity = migrated.entities().get(0);

        assertEquals(GameState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals("station.agrodome", entity.archetype().contentId());
        assertEquals(target, entity.market().targetStock());
        assertEquals(target, entity.market().configuredTargetStock());
    }

    @Test
    void valueLayerV2НеМожетИзобрестиНеСуществовавшийConfiguredBaseline() {
        GameState baseline = SimulationSession.createDemo(0x51A7E6L).snapshot();
        List<Integer> effectiveTarget = integerSlots(25);
        List<Integer> syntheticConfiguredTarget = integerSlots(1);
        EntityState entity = new EntityState(
                new EntityId(3L),
                null,
                null,
                new EntityState.InventoryState(100, integerSlots(20)),
                null,
                new EntityState.MarketState(
                        effectiveTarget,
                        syntheticConfiguredTarget,
                        floatSlots(0f),
                        floatSlots(10f),
                        floatSlots(9f),
                        doubleSlots(0d),
                        booleanSlots(true),
                        false),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new EntityState.ArchetypeState("station.agrodome"));
        GameState version2 = new GameState(
                GameState.ITEM_CAPACITY_ARCHETYPE_VERSION,
                baseline.rootSeed(),
                baseline.clock(),
                baseline.nextEntityIdValue(),
                baseline.eventRandomState(),
                baseline.asteroidRandomState(),
                baseline.events(),
                baseline.asteroidSpawner(),
                baseline.priceRecorder(),
                baseline.ledger(),
                List.of(entity));

        GameState migrated = GameStateMigration.toCurrent(version2);
        EntityState migratedEntity = migrated.entities().get(0);

        assertEquals(GameState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(effectiveTarget, migratedEntity.market().targetStock());
        assertEquals(effectiveTarget, migratedEntity.market().configuredTargetStock(),
                "Schema v2 had no provenance field, so only its effective target is historical truth");
        assertEquals("station.agrodome", migratedEntity.archetype().contentId());
    }

    private static List<Integer> integerSlots(int firstValue) {
        List<Integer> values = new ArrayList<>(Constants.MAX_ITEMS);
        values.add(firstValue);
        while (values.size() < Constants.MAX_ITEMS) {
            values.add(0);
        }
        return List.copyOf(values);
    }

    private static List<Float> floatSlots(float firstValue) {
        List<Float> values = new ArrayList<>(Constants.MAX_ITEMS);
        values.add(firstValue);
        while (values.size() < Constants.MAX_ITEMS) {
            values.add(0f);
        }
        return List.copyOf(values);
    }

    private static List<Double> doubleSlots(double firstValue) {
        List<Double> values = new ArrayList<>(Constants.MAX_ITEMS);
        values.add(firstValue);
        while (values.size() < Constants.MAX_ITEMS) {
            values.add(0d);
        }
        return List.copyOf(values);
    }

    private static List<Boolean> booleanSlots(boolean firstValue) {
        List<Boolean> values = new ArrayList<>(Constants.MAX_ITEMS);
        values.add(firstValue);
        while (values.size() < Constants.MAX_ITEMS) {
            values.add(false);
        }
        return List.copyOf(values);
    }

    private static byte[] encodedIntegerList(List<Integer> values) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * (values.size() + 1));
        buffer.putInt(values.size());
        for (Integer value : values) {
            buffer.putInt(value);
        }
        return buffer.array();
    }

    private static byte[] removeSecondConsecutiveBlock(byte[] source, byte[] block) {
        int first = indexOf(source, block, 0);
        if (first < 0) {
            throw new AssertionError("First market target block not found in fixture binary");
        }
        int second = indexOf(source, block, first + block.length);
        if (second != first + block.length) {
            throw new AssertionError("Configured market baseline is not adjacent to effective target block");
        }
        byte[] result = new byte[source.length - block.length];
        System.arraycopy(source, 0, result, 0, second);
        System.arraycopy(source, second + block.length, result, second, source.length - second - block.length);
        return result;
    }

    private static int indexOf(byte[] source, byte[] block, int fromIndex) {
        for (int index = Math.max(0, fromIndex); index <= source.length - block.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < block.length; offset++) {
                if (source[index + offset] != block[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return index;
            }
        }
        return -1;
    }
}
