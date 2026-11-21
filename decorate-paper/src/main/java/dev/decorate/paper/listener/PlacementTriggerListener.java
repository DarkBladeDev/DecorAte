package dev.decorate.paper.listener;

import dev.decorate.api.model.Vec3d;
import dev.decorate.core.service.PlacementService;
import dev.decorate.paper.config.MessageService;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Objects;

/**
 * Listens for the configured surface placement trigger (default {@code Shift + Right Click})
 * and delegates raycast placement to {@link PlacementService}.
 */
public final class PlacementTriggerListener implements Listener {

    public static final String PERMISSION_PLACE = "decorate.place";

    private final PlacementService placementService;
    private final MessageService messages;

    public PlacementTriggerListener(PlacementService placementService, MessageService messages) {
        this.placementService = Objects.requireNonNull(placementService, "placementService must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (placementService.settings().requireSneakForPlace() && !player.isSneaking()) {
            return;
        }
        ItemStack handItem = event.getItem();
        if (handItem == null || isAir(handItem.getType())) {
            return;
        }
        if (!player.hasPermission(PERMISSION_PLACE)) {
            return;
        }

        RayTraceResult ray = player.rayTraceBlocks(
                placementService.settings().maxPlaceDistance(),
                FluidCollisionMode.NEVER
        );
        if (ray == null || ray.getHitBlock() == null || ray.getHitBlockFace() == null) {
            return;
        }

        Vec3d hitPos = new Vec3d(
                ray.getHitPosition().getX(),
                ray.getHitPosition().getY(),
                ray.getHitPosition().getZ()
        );
        PlacementService.PlaceOutcome outcome = placementService.tryPlace(
                player,
                ray.getHitBlock(),
                ray.getHitBlockFace(),
                hitPos,
                handItem
        );

        if (outcome.accepted()) {
            event.setCancelled(true);
            messages.send(player, "placed");
            return;
        }

        switch (outcome.reason()) {
            case PROFILE_NOT_PLACEABLE -> {
                event.setCancelled(true);
                messages.send(player, "rejected.not-placeable");
            }
            case FACE_NOT_ALLOWED -> {
                event.setCancelled(true);
                messages.send(player, "rejected.face-not-allowed");
            }
            case BLOCK_LIMIT_REACHED -> {
                event.setCancelled(true);
                messages.send(player, "rejected.block-limit");
            }
            case CHUNK_LIMIT_REACHED -> {
                event.setCancelled(true);
                messages.send(player, "rejected.chunk-limit");
            }
            case PLAYER_LIMIT_REACHED -> {
                event.setCancelled(true);
                int limit = placementService.resolvePlayerLimit(player);
                messages.send(player, "rejected.player-limit",
                        MessageService.tag("limit", limit <= 0 ? "unlimited" : String.valueOf(limit)));
            }
            case PROTECTED_REGION -> {
                event.setCancelled(true);
                messages.send(player, "rejected.protected");
            }
            default -> {
                // Leave vanilla behavior untouched when cancelled by an external listener or invalid face
            }
        }
    }

    private static boolean isAir(org.bukkit.Material material) {
        return material == null
                || material == org.bukkit.Material.AIR
                || material == org.bukkit.Material.CAVE_AIR
                || material == org.bukkit.Material.VOID_AIR;
    }
}
