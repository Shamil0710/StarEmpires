package com.spacesim.ui;

import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftMissionCommandService;
import com.spacesim.world.SmallCraftMissionCommandService.MissionCommand;
import com.spacesim.world.SmallCraftMissionCommandService.MissionContext;
import com.spacesim.world.SmallCraftMissionState;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;

import java.util.Objects;

/**
 * M22.8I player-command adapter for carrier small-craft operations.
 *
 * <p>The UI never mutates craft, hangars, deck queues or missions directly. Mission submission and
 * queued-launch cancellation are delegated to the accepted M22.8D shared PLAYER/AI validation path.
 * Rejections return the unchanged mission state plus a player-facing diagnostic supplied by the
 * domain validator.</p>
 */
public final class CarrierPlayerCommandAdapter {
    private final SmallCraftMissionCommandService missionCommands;

    /**
     * Creates the player UI adapter over the accepted shared mission command authority.
     *
     * @param missionCommands M22.8D validation/command service
     */
    public CarrierPlayerCommandAdapter(
            SmallCraftMissionCommandService missionCommands) {
        this.missionCommands = Objects.requireNonNull(
                missionCommands, "missionCommands");
    }

    /**
     * Submits one player mission through the exact same validator used by AI.
     *
     * @param state current mission state
     * @param craftId individual physical craft
     * @param type requested mission family
     * @param target actor-known target
     * @param context actor-bounded knowledge and physical planning context
     * @return accepted next state or unchanged state with meaningful rejection diagnostic
     */
    public CommandResult submitMission(
            SmallCraftMissionState state,
            SmallCraftId craftId,
            MissionType type,
            MissionTarget target,
            MissionContext context) {
        SmallCraftMissionState current = Objects.requireNonNull(state, "state");
        try {
            var accepted = missionCommands.submit(
                    current,
                    new MissionCommand(
                            Objects.requireNonNull(craftId, "craftId"),
                            OrderSource.PLAYER,
                            Objects.requireNonNull(type, "type"),
                            Objects.requireNonNull(target, "target")),
                    Objects.requireNonNull(context, "context"));
            return CommandResult.accepted(
                    accepted.state(),
                    accepted.mission().id(),
                    accepted.supersededMissionId());
        } catch (IllegalArgumentException exception) {
            return CommandResult.rejected(
                    current,
                    DiagnosticCode.INVALID_MISSION,
                    safeMessage(exception));
        } catch (IllegalStateException exception) {
            return CommandResult.rejected(
                    current,
                    DiagnosticCode.PHYSICAL_STATE_BLOCKED,
                    safeMessage(exception));
        }
    }

    /**
     * Cancels only a launch still inside the accepted M22.8D/C cancellable boundary.
     *
     * @param state current mission state
     * @param missionId queued launch mission identity
     * @return accepted next state or unchanged state with a diagnostic
     */
    public CommandResult cancelQueuedLaunch(
            SmallCraftMissionState state,
            long missionId) {
        SmallCraftMissionState current = Objects.requireNonNull(state, "state");
        try {
            SmallCraftMissionState next =
                    missionCommands.cancelQueuedLaunch(current, missionId);
            return CommandResult.accepted(next, missionId, 0L);
        } catch (IllegalArgumentException exception) {
            return CommandResult.rejected(
                    current,
                    DiagnosticCode.UNKNOWN_MISSION,
                    safeMessage(exception));
        } catch (IllegalStateException exception) {
            return CommandResult.rejected(
                    current,
                    DiagnosticCode.CANCELLATION_BLOCKED,
                    safeMessage(exception));
        }
    }

    /** Stable UI-level rejection families; detailed reason remains the domain diagnostic. */
    public enum DiagnosticCode {
        /** No error; command passed the common domain validator. */ NONE,
        /** Mission command failed ownership/knowledge/range/endurance/capability validation. */
        INVALID_MISSION,
        /** Current physical bay/deck/mission state cannot perform the requested command. */
        PHYSICAL_STATE_BLOCKED,
        /** Requested mission identity does not exist. */ UNKNOWN_MISSION,
        /** Launch has crossed the lawful cancellation boundary or is otherwise non-cancellable. */
        CANCELLATION_BLOCKED
    }

    /**
     * Immutable result consumed by the application/controller layer.
     *
     * @param accepted whether the validated command was accepted
     * @param state resulting state; exactly the input state on rejection
     * @param missionId accepted/current mission identity, or zero for rejected submissions
     * @param supersededMissionId prior mission cancelled by a lawful deployed retask, or zero
     * @param diagnosticCode stable UI diagnostic family
     * @param diagnosticDetail player-facing domain detail, empty on success
     */
    public record CommandResult(
            boolean accepted,
            SmallCraftMissionState state,
            long missionId,
            long supersededMissionId,
            DiagnosticCode diagnosticCode,
            String diagnosticDetail) {
        /**
         * Validates one adapter result.
         *
         * @param accepted whether the shared mission validator accepted the command
         * @param state resulting mission state; unchanged on rejection
         * @param missionId accepted/current mission identity, or zero on rejection
         * @param supersededMissionId prior mission replaced by a lawful retask, or zero
         * @param diagnosticCode stable rejection family, or NONE on success
         * @param diagnosticDetail player-facing validator detail, empty on success
         */
        public CommandResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            diagnosticDetail = diagnosticDetail == null ? "" : diagnosticDetail.strip();
            if (missionId < 0L || supersededMissionId < 0L) {
                throw new IllegalArgumentException(
                        "mission identities cannot be negative");
            }
            if (accepted) {
                if (missionId <= 0L
                        || diagnosticCode != DiagnosticCode.NONE
                        || !diagnosticDetail.isEmpty()) {
                    throw new IllegalArgumentException(
                            "accepted carrier UI command must carry mission identity and no error");
                }
            } else if (missionId != 0L
                    || diagnosticCode == DiagnosticCode.NONE
                    || diagnosticDetail.isEmpty()) {
                throw new IllegalArgumentException(
                        "rejected carrier UI command must carry diagnostic and no mission identity");
            }
        }

        private static CommandResult accepted(
                SmallCraftMissionState state,
                long missionId,
                long supersededMissionId) {
            return new CommandResult(
                    true,
                    state,
                    missionId,
                    supersededMissionId,
                    DiagnosticCode.NONE,
                    "");
        }

        private static CommandResult rejected(
                SmallCraftMissionState state,
                DiagnosticCode code,
                String detail) {
            return new CommandResult(false, state, 0L, 0L, code, detail);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.strip();
    }
}
