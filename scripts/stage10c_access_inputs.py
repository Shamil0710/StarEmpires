from pathlib import Path


def patch(path_name, replacements):
    path = Path(path_name)
    text = path.read_text()
    for label, old, new in replacements:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{path_name}: {label}: expected 1 marker, found {count}")
        text = text.replace(old, new, 1)
    path.write_text(text)


patch("src/main/java/com/spacesim/trade/FleetTradeProfile.java", [
    ("field", "    private final ShipType shipType;\n    private final int[] stock;",
     "    private final ShipType shipType;\n    private final int factionId;\n    private final int[] stock;"),
    ("legacy delegate", "            ShipType shipType,\n            int[] stock,\n            float[] reputation) {\n        if (!Float.isFinite(x) || !Float.isFinite(y)) {",
     "            ShipType shipType,\n            int[] stock,\n            float[] reputation) {\n        this(x, y, movementSpeed, walletBalanceMilliCredits, inventoryCapacity, totalStock, cargoSpace,\n                specializedItem, hasShipComponent, shipType, -1, stock, reputation);\n    }\n\n    /**\n     * Creates a planning profile with explicit runtime faction membership.\n     *\n     * @param x current X coordinate\n     * @param y current Y coordinate\n     * @param movementSpeed movement speed\n     * @param walletBalanceMilliCredits wallet balance\n     * @param inventoryCapacity physical inventory capacity\n     * @param totalStock total physical cargo\n     * @param cargoSpace AI cargo limit\n     * @param specializedItem specialization item or -1\n     * @param hasShipComponent whether ShipComponent exists\n     * @param shipType cargo policy or null\n     * @param factionId runtime faction ID or -1\n     * @param stock copied stock array\n     * @param reputation copied reputation array\n     */\n    public FleetTradeProfile(\n            float x,\n            float y,\n            float movementSpeed,\n            long walletBalanceMilliCredits,\n            int inventoryCapacity,\n            int totalStock,\n            int cargoSpace,\n            int specializedItem,\n            boolean hasShipComponent,\n            ShipType shipType,\n            int factionId,\n            int[] stock,\n            float[] reputation) {\n        if (!Float.isFinite(x) || !Float.isFinite(y)) {"),
    ("validation", "        if (specializedItem < -1 || specializedItem >= Constants.MAX_ITEMS) {\n            throw new IllegalArgumentException(\"Некорректная специализация товара\");\n        }",
     "        if (specializedItem < -1 || specializedItem >= Constants.MAX_ITEMS) {\n            throw new IllegalArgumentException(\"Некорректная специализация товара\");\n        }\n        if (factionId < -1 || factionId >= Constants.MAX_FACTIONS) {\n            throw new IllegalArgumentException(\"Некорректный runtime faction ID флота\");\n        }"),
    ("assignment", "        this.shipType = shipType;\n        this.stock = Arrays.copyOf(stock, stock.length);",
     "        this.shipType = shipType;\n        this.factionId = factionId;\n        this.stock = Arrays.copyOf(stock, stock.length);"),
    ("getter", "    /** @return физически свободная вместимость inventory */",
     "    /** @return runtime faction ID или {@code -1} */\n    public int factionId() {\n        return factionId;\n    }\n\n    /** @return физически свободная вместимость inventory */"),
    ("memo", "                && shipType == other.shipType\n                && Arrays.equals(stock, other.stock)",
     "                && shipType == other.shipType\n                && factionId == other.factionId\n                && Arrays.equals(stock, other.stock)"),
])

patch("src/main/java/com/spacesim/systems/TradeAISystem.java", [
    ("profile faction", "        ShipComponent ship = sm.get(fleet);\n        return new FleetTradeProfile(",
     "        ShipComponent ship = sm.get(fleet);\n        FactionComponent faction = fm.get(fleet);\n        return new FleetTradeProfile("),
    ("constructor faction", "                ship != null,\n                ship == null ? null : ship.type,\n                inventory.stock,",
     "                ship != null,\n                ship == null ? null : ship.type,\n                faction == null ? -1 : faction.factionId,\n                inventory.stock,"),
])
