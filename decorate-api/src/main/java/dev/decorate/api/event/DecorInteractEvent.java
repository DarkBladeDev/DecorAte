package dev.decorate.api.event;

import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.SurfacePose;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired before a player interacts with an existing placed item (pickup, rotate, or toggle pose).
 */
public class DecorInteractEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final PlacedItem placedItem;
    private final Action action;
    private @Nullable SurfacePose nextPose;
    private boolean cancelled;

    /**
     * Creates a new interaction event.
     *
     * @param player interacting player
     * @param placedItem target placed item
     * @param action requested interaction action
     * @param nextPose proposed updated pose (null when {@code action == PICKUP})
     */
    public DecorInteractEvent(
            Player player,
            PlacedItem placedItem,
            Action action,
            @Nullable SurfacePose nextPose
    ) {
        this.player = Objects.requireNonNull(player, "player must not be null");
        this.placedItem = Objects.requireNonNull(placedItem, "placedItem must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.nextPose = nextPose;
    }

    public Player getPlayer() {
        return player;
    }

    public PlacedItem getPlacedItem() {
        return placedItem;
    }

    public Action getAction() {
        return action;
    }

    public @Nullable SurfacePose getNextPose() {
        return nextPose;
    }

    public void setNextPose(@Nullable SurfacePose nextPose) {
        this.nextPose = nextPose;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Direct interaction action performed on a placed item.
     */
    public enum Action {
        PICKUP,
        ROTATE,
        TOGGLE_POSE
    }
}
