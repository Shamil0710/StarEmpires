package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.persistence.EntityId;

/**
 * Transient manual-mining intent shared between a player-facing adapter and the authoritative
 * {@link com.spacesim.systems.MiningSystem}.
 *
 * <p>The command is deliberately not persisted. Persistent physical consequences remain in the
 * asteroid's finite resource, the ship inventory and {@link MiningComponent} counters. The command
 * only selects a target and requests extraction; it never moves the ship, creates resources or
 * awards money.</p>
 */
public final class MiningCommandComponent implements Component {
    /** Readable result of the most recent manual-mining evaluation. */
    public enum Status {
        /** No manual mining target or request is active. */
        IDLE("Добыча не активна"),
        /** Target is valid and in range; extraction may be started. */
        READY("Цель готова к добыче"),
        /** Extraction is requested and the shared mining pipeline is working. */
        MINING("Идёт добыча"),
        /** A mining request has no selected asteroid. */
        NO_TARGET("Не выбран астероид"),
        /** The selected persistent ID no longer resolves to a usable asteroid. */
        INVALID_TARGET("Цель добычи недоступна"),
        /** The asteroid contains a resource incompatible with the installed mining equipment. */
        INVALID_RESOURCE("Неподходящий ресурс"),
        /** The selected asteroid is outside the equipment extraction range. */
        OUT_OF_RANGE("Астероид вне дальности добычи"),
        /** The real ship inventory has no free capacity. */
        CARGO_FULL("Грузовой отсек заполнен"),
        /** The selected finite resource has been exhausted. */
        DEPLETED("Ресурс астероида исчерпан"),
        /** The active hull does not provide a compatible mining role/equipment set. */
        INCOMPATIBLE_SHIP("Корабль не оборудован для этой добычи"),
        /** Mining component data is damaged or otherwise not executable. */
        INVALID_CONFIGURATION("Некорректная конфигурация добычи"),
        /** The controlled ship is docked and cannot mine. */
        DOCKED("Сначала необходимо отстыковаться");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        /** @return localized player-readable reason/status text */
        public String getDisplayName() {
            return displayName;
        }
    }

    /** Persistent local asteroid ID selected for manual mining, or {@code null}. */
    public EntityId targetAsteroidId;
    /** Continuous extraction intent; the system revalidates all physical constraints every tick. */
    public boolean miningRequested;
    /** Most recent authoritative evaluation of the command. */
    public Status status = Status.IDLE;
    /** Whole resource units physically transferred during the most recent fixed tick. */
    public int extractedLastTick;

    /** Creates an idle transient mining command. */
    public MiningCommandComponent() {
    }

    /** Clears target, request and diagnostic state without changing any physical resource state. */
    public void clear() {
        targetAsteroidId = null;
        miningRequested = false;
        status = Status.IDLE;
        extractedLastTick = 0;
    }
}
