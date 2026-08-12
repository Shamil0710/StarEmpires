package com.spacesim.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentArchetypeValidationTest {
    @Test
    void stationОтклоняетНеизвестнуюФракцию() {
        String json = baseCatalog(
                "{\"id\":\"station.test\",\"displayName\":\"Test\",\"inventoryCapacity\":100,"
                        + "\"startingCredits\":1000,\"factionId\":\"faction.missing\",\"recipeId\":null,"
                        + "\"markets\":[{\"itemId\":\"item.test\",\"initialStock\":1,"
                        + "\"targetStock\":10,\"consumptionPerSecond\":0}]}" );
        assertThrows(IllegalArgumentException.class, () -> ContentCatalogLoader.parse(json));
    }

    @Test
    void stationОтклоняетНеизвестныйRecipeИItem() {
        String badRecipe = baseCatalog(
                "{\"id\":\"station.test\",\"displayName\":\"Test\",\"inventoryCapacity\":100,"
                        + "\"startingCredits\":1000,\"factionId\":\"faction.test\","
                        + "\"recipeId\":\"recipe.missing\",\"markets\":[{\"itemId\":\"item.test\","
                        + "\"initialStock\":1,\"targetStock\":10,\"consumptionPerSecond\":0}]}" );
        assertThrows(IllegalArgumentException.class, () -> ContentCatalogLoader.parse(badRecipe));

        String badItem = baseCatalog(
                "{\"id\":\"station.test\",\"displayName\":\"Test\",\"inventoryCapacity\":100,"
                        + "\"startingCredits\":1000,\"factionId\":\"faction.test\",\"recipeId\":null,"
                        + "\"markets\":[{\"itemId\":\"item.missing\",\"initialStock\":1,"
                        + "\"targetStock\":10,\"consumptionPerSecond\":0}]}" );
        assertThrows(IllegalArgumentException.class, () -> ContentCatalogLoader.parse(badItem));
    }

    @Test
    void shipRoleОтклоняетНеполныеRoleSpecificПараметры() {
        String invalidMiner = catalogWithShip(
                "{\"id\":\"ship.bad_miner\",\"displayName\":\"Bad miner\",\"role\":\"MINING_SHIP\","
                        + "\"cargoCapacity\":50,\"movementSpeed\":100,\"startingCredits\":0,"
                        + "\"extractionPerSecond\":0,\"extractionRange\":10,\"dockingRange\":10,"
                        + "\"hull\":0,\"shields\":0,\"damagePerSecond\":0,\"weaponRange\":0}" );
        assertThrows(IllegalArgumentException.class, () -> ContentCatalogLoader.parse(invalidMiner));

        String invalidCombat = catalogWithShip(
                "{\"id\":\"ship.bad_combat\",\"displayName\":\"Bad combat\",\"role\":\"COMBAT_SHIP\","
                        + "\"cargoCapacity\":0,\"movementSpeed\":0,\"startingCredits\":0,"
                        + "\"extractionPerSecond\":0,\"extractionRange\":0,\"dockingRange\":0,"
                        + "\"hull\":100,\"shields\":0,\"damagePerSecond\":0,\"weaponRange\":100}" );
        assertThrows(IllegalArgumentException.class, () -> ContentCatalogLoader.parse(invalidCombat));
    }

    private String baseCatalog(String station) {
        return "{\"schemaVersion\":1,"
                + "\"items\":[{\"id\":\"item.test\",\"runtimeId\":0,\"codeName\":\"Test\","
                + "\"displayName\":\"Test\",\"category\":\"MATERIAL\",\"basePrice\":1,\"mineable\":false}],"
                + "\"recipes\":[],"
                + "\"factions\":[{\"id\":\"faction.test\",\"runtimeId\":0,\"displayName\":\"Test\"}],"
                + "\"shipArchetypes\":[],\"stationArchetypes\":[" + station + "]}";
    }

    private String catalogWithShip(String ship) {
        return "{\"schemaVersion\":1,"
                + "\"items\":[{\"id\":\"item.test\",\"runtimeId\":0,\"codeName\":\"Test\","
                + "\"displayName\":\"Test\",\"category\":\"MATERIAL\",\"basePrice\":1,\"mineable\":false}],"
                + "\"recipes\":[],\"factions\":[],"
                + "\"shipArchetypes\":[" + ship + "],\"stationArchetypes\":[]}";
    }
}
