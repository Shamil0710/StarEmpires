package com.spacesim.components;

import com.badlogic.ashley.core.Component;

/**
 * Transient ownership marker for an inactive player-owned FleetId executing Stage-15 orders.
 *
 * <p>Generic TradeAI and autonomous Mining must not write flight intent while this marker is
 * present. The persistent source of truth remains PlayerState/FleetId ownership and fleet orders;
 * this component is rebuilt by PlayerRuntime and is never serialized.</p>
 */
public final class DelegatedFleetComponent implements Component {
}
