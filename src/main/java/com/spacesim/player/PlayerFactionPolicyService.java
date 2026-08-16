package com.spacesim.player;

import com.spacesim.world.FactionPolicyCommand;
import com.spacesim.world.FactionPolicyCommandExecutor;

import java.util.Objects;

/**
 * Player-facing adapter for common faction-policy commands.
 *
 * <p>The service only resolves the player's current founded faction and delegates to the same
 * executor available to AI planners. It contains no policy mutation, free economic execution or
 * player-only rule.</p>
 */
public final class PlayerFactionPolicyService {
    private PlayerFactionPolicyService() {
        throw new AssertionError("Utility class");
    }

    /**
     * Submits one common faction-policy command for the player's current faction.
     *
     * @param runtime playable runtime with an initialized player
     * @param command common policy command
     * @return common execution report
     * @throws IllegalStateException when the player is still independent
     */
    public static FactionPolicyCommandExecutor.ExecutionResult submit(
            PlayerRuntime runtime,
            FactionPolicyCommand command) {
        PlayerRuntime checkedRuntime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        String factionId = checkedRuntime.player().factionContentId();
        if (factionId == null || factionId.isBlank()) {
            throw new IllegalStateException("Independent player has no faction policy authority");
        }
        return FactionPolicyCommandExecutor.execute(
                checkedRuntime.world(),
                factionId,
                Objects.requireNonNull(command, "Faction policy command not set"));
    }
}
