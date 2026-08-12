package com.spacesim.content;

import com.spacesim.constants.Constants;
import com.spacesim.model.ItemType;
import com.spacesim.model.Recipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentCatalogLoaderTest {
    @Test
    void defaultCatalogMatchesCurrentDenseItemContractDuringMigration() {
        ContentCatalog catalog = ContentCatalogLoader.loadDefault();

        assertEquals(ContentCatalogLoader.CURRENT_SCHEMA_VERSION, catalog.getSchemaVersion());
        assertEquals(ItemType.values().length, catalog.getItems().size());
        assertEquals(4, catalog.getRecipes().size());

        for (ItemType legacy : ItemType.values()) {
            ContentCatalog.ItemDefinition item = catalog.findItem(legacy.getId());
            assertNotNull(item);
            assertEquals(legacy.getCodeName(), item.codeName());
            assertEquals(legacy.getDisplayName(), item.displayName());
            assertEquals(legacy.getCategory(), item.category());
            assertEquals(legacy.getBasePrice(), item.basePrice(), 0.0001f);
            assertEquals(legacy.isMineable(), item.mineable());
        }
    }

    @Test
    void runtimeRecipeResolvesPersistentItemIdsIntoDenseArrays() {
        ContentCatalog catalog = ContentCatalogLoader.loadDefault();

        Recipe recipe = catalog.createRuntimeRecipe("recipe.steel_smelting");

        assertEquals("Выплавка стали", recipe.name);
        assertEquals(4f, recipe.durationSeconds, 0.0001f);
        assertEquals(2, recipe.getInputAmount(Constants.ITEM_ORE));
        assertEquals(1, recipe.getInputAmount(Constants.ITEM_ENERGY));
        assertEquals(2, recipe.getOutputAmount(Constants.ITEM_STEEL));
        assertEquals(0, recipe.getOutputAmount(Constants.ITEM_WEAPONS));
    }

    @Test
    void fileOrderDoesNotChangeDenseRuntimeOrder() {
        ContentCatalog catalog = ContentCatalogLoader.parse("""
                {
                  "schemaVersion": 1,
                  "items": [
                    {"id":"item.second","runtimeId":1,"codeName":"Second","displayName":"Второй","category":"MATERIAL","basePrice":2,"mineable":false},
                    {"id":"item.first","runtimeId":0,"codeName":"First","displayName":"Первый","category":"MATERIAL","basePrice":1,"mineable":true}
                  ],
                  "recipes": []
                }
                """);

        assertEquals("item.first", catalog.getItems().get(0).id());
        assertEquals("item.second", catalog.getItems().get(1).id());
        assertEquals(0, catalog.findItem("item.first").runtimeId());
    }

    @Test
    void duplicatePersistentItemIdIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ContentCatalogLoader.parse("""
                        {
                          "schemaVersion": 1,
                          "items": [
                            {"id":"item.ore","runtimeId":0,"codeName":"Ore","displayName":"Руда","category":"MATERIAL","basePrice":10,"mineable":true},
                            {"id":"item.ore","runtimeId":1,"codeName":"Other","displayName":"Другое","category":"MATERIAL","basePrice":11,"mineable":false}
                          ],
                          "recipes": []
                        }
                        """));

        assertTrue(exception.getMessage().contains("Повторяющийся item content ID"));
    }

    @Test
    void runtimeItemIdsMustBeDense() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ContentCatalogLoader.parse("""
                        {
                          "schemaVersion": 1,
                          "items": [
                            {"id":"item.first","runtimeId":0,"codeName":"First","displayName":"Первый","category":"MATERIAL","basePrice":1,"mineable":true},
                            {"id":"item.third","runtimeId":2,"codeName":"Third","displayName":"Третий","category":"MATERIAL","basePrice":3,"mineable":false}
                          ],
                          "recipes": []
                        }
                        """));

        assertTrue(exception.getMessage().contains("плотными"));
    }

    @Test
    void recipeCannotReferenceUnknownItem() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ContentCatalogLoader.parse("""
                        {
                          "schemaVersion": 1,
                          "items": [
                            {"id":"item.ore","runtimeId":0,"codeName":"Ore","displayName":"Руда","category":"MATERIAL","basePrice":10,"mineable":true}
                          ],
                          "recipes": [
                            {"id":"recipe.bad","displayName":"Плохой","durationSeconds":1,"inputs":{},"outputs":{"item.missing":1}}
                          ]
                        }
                        """));

        assertTrue(exception.getMessage().contains("неизвестный item"));
    }

    @Test
    void unsupportedSchemaVersionIsRejectedBeforeWorldCreation() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ContentCatalogLoader.parse("""
                        {"schemaVersion": 2, "items": [], "recipes": []}
                        """));

        assertTrue(exception.getMessage().contains("Неподдерживаемая версия"));
    }

    @Test
    void unknownRuntimeRecipeIdFailsLoudly() {
        ContentCatalog catalog = ContentCatalogLoader.loadDefault();

        assertThrows(IllegalArgumentException.class,
                () -> catalog.createRuntimeRecipe("recipe.does_not_exist"));
    }
}
