package com.spacesim.constants;

public class Constants {
    public static final int MAX_ITEMS = 5; // Упростим до 5 для теста

    // IDs
    public static final int ITEM_ORE = 0;
    public static final int ITEM_ENERGY = 1;
    public static final int ITEM_FOOD = 2;
    public static final int ITEM_STEEL = 3;
    public static final int ITEM_WEAPONS = 4;

    public static final String[] ITEM_NAMES = {"Ore", "Energy", "Food", "Steel", "Weapons"};
    public static final float[] BASE_PRICES = {10f, 5f, 20f, 50f, 150f};

    // Фракции
    public static final int MAX_FACTIONS = 3;
    public static final int FACTION_NEUTRAL = 0;
    public static final int FACTION_TRADE_LEAGUE = 1;
    public static final int FACTION_MINERS = 2;
    public static final String[] FACTION_NAMES = {"Нейтралы", "Торговая лига", "Шахтёры"};

    // Репутация
    public static final float MIN_REPUTATION = -100f;
    public static final float MAX_REPUTATION = 100f;
    public static final float REPUTATION_TRADE_GAIN = 1f;
    public static final float MAX_REPUTATION_PRICE_BONUS = 0.15f;

    // Grid
    public static final int CELL_SIZE = 200;
}
