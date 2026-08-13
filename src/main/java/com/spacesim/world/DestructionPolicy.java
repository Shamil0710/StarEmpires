package com.spacesim.world;

import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Explicit economic policy used before structural entity removal.
 *
 * @param resourceFate fate of all inventory stock
 * @param moneyFate fate of wallet balance
 * @param resourceRecipientEntityId required only for {@link ResourceDestructionFate#TRANSFER_TO_ENTITY}
 */
public record DestructionPolicy(
        ResourceDestructionFate resourceFate,
        MoneyDestructionFate moneyFate,
        EntityId resourceRecipientEntityId) {

    /**
     * Validates cross-field policy requirements.
     *
     * @param resourceFate fate of all inventory stock
     * @param moneyFate fate of wallet balance
     * @param resourceRecipientEntityId required only for {@link ResourceDestructionFate#TRANSFER_TO_ENTITY}
     */
    public DestructionPolicy {
        Objects.requireNonNull(resourceFate, "Resource destruction fate не задан");
        Objects.requireNonNull(moneyFate, "Money destruction fate не задан");
        if (resourceFate == ResourceDestructionFate.TRANSFER_TO_ENTITY) {
            Objects.requireNonNull(
                    resourceRecipientEntityId,
                    "TRANSFER_TO_ENTITY требует resourceRecipientEntityId");
        } else if (resourceRecipientEntityId != null) {
            throw new IllegalArgumentException(
                    "resourceRecipientEntityId допустим только для TRANSFER_TO_ENTITY");
        }
    }

    /** @return destructive policy suitable for catastrophic station loss */
    public static DestructionPolicy destroyAll() {
        return new DestructionPolicy(
                ResourceDestructionFate.DESTROY,
                MoneyDestructionFate.SINK,
                null);
    }

    /** @return policy that turns inventory into physical salvage while sinking wallet money */
    public static DestructionPolicy salvageResources() {
        return new DestructionPolicy(
                ResourceDestructionFate.SALVAGE,
                MoneyDestructionFate.SINK,
                null);
    }
}
