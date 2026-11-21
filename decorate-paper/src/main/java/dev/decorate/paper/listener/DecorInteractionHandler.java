package dev.decorate.paper.listener;

import dev.decorate.api.event.DecorInteractEvent;
import dev.decorate.core.service.PlacementService;
import dev.decorate.packet.PacketInteractionListener;
import dev.decorate.paper.config.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles main-thread packet interaction callbacks for placed decorations
 * ({@code PICKUP}, {@code ROTATE}, {@code TOGGLE_POSE}).
 */
public final class DecorInteractionHandler implements PacketInteractionListener.InteractionHandler {

    public static final String PERMISSION_INTERACT = "decorate.interact";

    private final PlacementService placementService;
    private final MessageService messageService;

    public DecorInteractionHandler(PlacementService placementService, MessageService messageService) {
        this.placementService = Objects.requireNonNull(placementService, "placementService must not be null");
        this.messageService = Objects.requireNonNull(messageService, "messageService must not be null");
    }

    @Override
    public void onInteract(Player player, UUID itemId, DecorInteractEvent.Action action) {
        if (!placementService.isGlobalEnabled()) {
            messageService.send(player, "rejected.disabled");
            return;
        }
        if (!player.hasPermission(PERMISSION_INTERACT)) {
            messageService.send(player, "rejected.no-permission");
            return;
        }
        PlacementService.InteractOutcome outcome = placementService.handleInteraction(player, itemId, action);
        if (!outcome.accepted()) {
            switch (outcome.reason()) {
                case GLOBAL_DISABLED -> messageService.send(player, "rejected.disabled");
                case NOT_OWNER -> messageService.send(player, "rejected.not-owner");
                case PROTECTED_REGION -> messageService.send(player, "rejected.protected");
                default -> {}
            }
            return;
        }

        switch (action) {
            case PICKUP -> {
                if (outcome.returnedItem() != null) {
                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(outcome.returnedItem());
                    for (ItemStack extra : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), extra);
                    }
                }
                messageService.send(player, "picked-up");
            }
            case ROTATE -> {
                if (outcome.item() != null) {
                    messageService.send(player, "rotated",
                            MessageService.tag("yaw", String.format(Locale.ROOT, "%.1f", outcome.item().pose().yawDegrees())));
                }
            }
            case TOGGLE_POSE -> {
                if (outcome.item() != null) {
                    messageService.send(player, "pose-toggled",
                            MessageService.tag("pose", outcome.item().pose().poseMode().name()));
                }
            }
        }
    }
}
