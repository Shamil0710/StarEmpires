package com.spacesim;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.model.Recipe;
import com.spacesim.persistence.EntityIdAllocator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DemoWorldDataDrivenContentTest {
    @Test
    void изменениеJsonРецептаМеняетСозданноеПроизводствоБезИзмененияFactoryКода() {
        ContentCatalog catalog = ContentCatalogLoader.parse("""
                {
                  "schemaVersion": 1,
                  "items": [
                    {"id":"item.ore","runtimeId":0,"codeName":"Ore","displayName":"Руда","category":"MATERIAL","basePrice":10,"mineable":true},
                    {"id":"item.energy","runtimeId":1,"codeName":"Energy","displayName":"Энергия","category":"GAS_LIQUID","basePrice":5,"mineable":false},
                    {"id":"item.food","runtimeId":2,"codeName":"Food","displayName":"Продовольствие","category":"FINISHED_GOODS","basePrice":20,"mineable":false},
                    {"id":"item.steel","runtimeId":3,"codeName":"Steel","displayName":"Сталь","category":"MATERIAL","basePrice":50,"mineable":false},
                    {"id":"item.weapons","runtimeId":4,"codeName":"Weapons","displayName":"Вооружение","category":"FINISHED_GOODS","basePrice":150,"mineable":false}
                  ],
                  "recipes": [
                    {"id":"recipe.energy_generation","displayName":"Усиленная генерация","durationSeconds":4,"inputs":{},"outputs":{"item.energy":11}},
                    {"id":"recipe.food_growing","displayName":"Выращивание продовольствия","durationSeconds":6,"inputs":{"item.energy":2},"outputs":{"item.food":6}},
                    {"id":"recipe.steel_smelting","displayName":"Выплавка стали","durationSeconds":4,"inputs":{"item.ore":2,"item.energy":1},"outputs":{"item.steel":2}},
                    {"id":"recipe.weapons_assembly","displayName":"Сборка вооружения","durationSeconds":6,"inputs":{"item.steel":2,"item.energy":1},"outputs":{"item.weapons":1}}
                  ]
                }
                """);

        List<Entity> entities = DemoWorldFactory.createEntities(new EntityIdAllocator(), catalog);
        Entity powerPlant = byName(entities, "Энергоузел Корона");
        ProductionComponent production = powerPlant.getComponent(ProductionComponent.class);
        assertNotNull(production);
        Recipe recipe = production.getActiveRecipe();
        assertNotNull(recipe);

        assertEquals("Усиленная генерация", recipe.name);
        assertEquals(11, recipe.getOutputAmount(Constants.ITEM_ENERGY));
    }

    private Entity byName(List<Entity> entities, String name) {
        for (Entity entity : entities) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity != null && name.equals(identity.name)) {
                return entity;
            }
        }
        throw new AssertionError("Не найдена сущность: " + name);
    }
}
