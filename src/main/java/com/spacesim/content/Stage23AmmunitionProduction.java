package com.spacesim.content;

import java.util.List;

/** Authored core ammunition admitted to personal manufacturing; owns no stock or recipes. */
public final class Stage23AmmunitionProduction {
    /** Existing Empire kinetic ammunition identity. */
    public static final String EMPIRE = "ammo.empire_axial_dart_150kg_v1";
    /** Existing Industrial Union kinetic ammunition identity. */
    public static final String UNION = "ammo.industrial_union_dart_140kg_v1";
    private Stage23AmmunitionProduction() { }
    /** @return existing authored rounds with admitted manufacturing bindings */
    public static List<String> products() { return List.of(EMPIRE, UNION); }
    /**
     * @param product existing product identity
     * @return whether personal round-count controls apply
     */
    public static boolean supports(String product) { return EMPIRE.equals(product) || UNION.equals(product); }
    /**
     * @param product admitted round identity
     * @return player-facing physical round name
     */
    public static String displayName(String product) {
        return switch (product) {
            case EMPIRE -> "Кинетический боеприпас Империи, 150 кг";
            case UNION -> "Кинетический боеприпас Союза, 140 кг";
            default -> throw new IllegalArgumentException("No admitted ammunition manufacturing product");
        };
    }
}
