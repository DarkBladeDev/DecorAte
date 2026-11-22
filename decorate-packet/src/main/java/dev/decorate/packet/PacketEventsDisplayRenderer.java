package dev.decorate.packet;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.core.geometry.PlacementGeometry;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * PacketEvents 2.14.0 {@link DisplayRenderer} spawning virtual {@code EntityTypes.ITEM_DISPLAY}
 * and {@code EntityTypes.INTERACTION} entities exclusively over the network.
 */
public final class PacketEventsDisplayRenderer implements DisplayRenderer {

    private final PacketSender packetSender;
    private final PacketMetadataBuilder metadataBuilder;
    private final IntSupplier entityIdSupplier;
    private final Supplier<Collection<? extends Player>> onlinePlayersSupplier;
    private final BiPredicate<Player, PlacedItem> observerFilter;

    private final Map<UUID, VirtualEntityPair> entitiesByItem = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> itemByInteractionEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> viewersByItem = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> visibleItemsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveHoverLabel> hoverLabelsByPlayer = new ConcurrentHashMap<>();

    public PacketEventsDisplayRenderer(BiPredicate<Player, PlacedItem> observerFilter) {
        this(
                PacketSender.PACKET_EVENTS,
                new PacketMetadataBuilder(PacketEventsDisplayRenderer::convertSerializedItem),
                SpigotReflectionUtil::generateEntityId,
                Bukkit::getOnlinePlayers,
                observerFilter
        );
    }

    public PacketEventsDisplayRenderer(
            PacketSender packetSender,
            PacketMetadataBuilder metadataBuilder,
            IntSupplier entityIdSupplier,
            Supplier<Collection<? extends Player>> onlinePlayersSupplier,
            BiPredicate<Player, PlacedItem> observerFilter
    ) {
        this.packetSender = Objects.requireNonNull(packetSender, "packetSender must not be null");
        this.metadataBuilder = Objects.requireNonNull(metadataBuilder, "metadataBuilder must not be null");
        this.entityIdSupplier = Objects.requireNonNull(entityIdSupplier, "entityIdSupplier must not be null");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier must not be null");
        this.observerFilter = Objects.requireNonNull(observerFilter, "observerFilter must not be null");
    }

    @Override
    public void spawnForPlayer(Player player, PlacedItem item) {
        VirtualEntityPair pair = entitiesByItem.computeIfAbsent(item.id(), id -> {
            int displayId = entityIdSupplier.getAsInt();
            int interactionId = entityIdSupplier.getAsInt();
            itemByInteractionEntityId.put(interactionId, item.id());
            return new VirtualEntityPair(displayId, UUID.randomUUID(), interactionId, UUID.randomUUID());
        });

        Set<UUID> playerItems = visibleItemsByPlayer.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        if (!playerItems.add(item.id())) {
            return;
        }
        viewersByItem.computeIfAbsent(item.id(), k -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());

        Vec3d displayPos = item.pose().exactPosition();
        WrapperPlayServerSpawnEntity spawnDisplay = new WrapperPlayServerSpawnEntity(
                pair.displayEntityId(),
                Optional.of(pair.displayUuid()),
                EntityTypes.ITEM_DISPLAY,
                new Vector3d(displayPos.x(), displayPos.y(), displayPos.z()),
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        );
        WrapperPlayServerEntityMetadata displayMeta = new WrapperPlayServerEntityMetadata(
                pair.displayEntityId(),
                metadataBuilder.buildItemDisplayMetadata(item, 0)
        );

        Vec3d interactionPos = PlacementGeometry.computeInteractionAnchor(item.pose());
        WrapperPlayServerSpawnEntity spawnInteraction = new WrapperPlayServerSpawnEntity(
                pair.interactionEntityId(),
                Optional.of(pair.interactionUuid()),
                EntityTypes.INTERACTION,
                new Vector3d(interactionPos.x(), interactionPos.y(), interactionPos.z()),
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        );
        WrapperPlayServerEntityMetadata interactionMeta = new WrapperPlayServerEntityMetadata(
                pair.interactionEntityId(),
                metadataBuilder.buildInteractionMetadata(item.pose())
        );

        packetSender.send(player, spawnDisplay);
        packetSender.send(player, displayMeta);
        packetSender.send(player, spawnInteraction);
        packetSender.send(player, interactionMeta);
    }

    @Override
    public void updateForPlayer(Player player, PlacedItem item) {
        VirtualEntityPair pair = entitiesByItem.get(item.id());
        if (pair == null) {
            spawnForPlayer(player, item);
            return;
        }
        WrapperPlayServerEntityMetadata displayMeta = new WrapperPlayServerEntityMetadata(
                pair.displayEntityId(),
                metadataBuilder.buildItemDisplayMetadata(item, 2)
        );
        packetSender.send(player, displayMeta);
    }

    @Override
    public void destroyForPlayer(Player player, UUID itemId) {
        clearHoverTextIfMatching(player, itemId);
        Set<UUID> playerItems = visibleItemsByPlayer.get(player.getUniqueId());
        if (playerItems == null || !playerItems.remove(itemId)) {
            return;
        }
        Set<UUID> viewers = viewersByItem.get(itemId);
        if (viewers != null) {
            viewers.remove(player.getUniqueId());
        }
        VirtualEntityPair pair = entitiesByItem.get(itemId);
        if (pair != null) {
            packetSender.send(player, new WrapperPlayServerDestroyEntities(pair.displayEntityId(), pair.interactionEntityId()));
        }
    }

    @Override
    public void destroyAllForPlayer(Player player) {
        ActiveHoverLabel hoverLabel = hoverLabelsByPlayer.remove(player.getUniqueId());
        Set<UUID> playerItems = visibleItemsByPlayer.remove(player.getUniqueId());
        int itemCount = playerItems == null ? 0 : playerItems.size();
        int extraHover = hoverLabel == null ? 0 : 1;
        if (itemCount == 0 && extraHover == 0) {
            return;
        }
        int[] entityIds = new int[itemCount * 2 + extraHover];
        int idx = 0;
        if (playerItems != null) {
            for (UUID itemId : playerItems) {
                Set<UUID> viewers = viewersByItem.get(itemId);
                if (viewers != null) {
                    viewers.remove(player.getUniqueId());
                }
                VirtualEntityPair pair = entitiesByItem.get(itemId);
                if (pair != null) {
                    entityIds[idx++] = pair.displayEntityId();
                    entityIds[idx++] = pair.interactionEntityId();
                }
            }
        }
        if (hoverLabel != null) {
            entityIds[idx++] = hoverLabel.entityId();
        }
        if (idx > 0) {
            int[] trimmed = idx == entityIds.length ? entityIds : java.util.Arrays.copyOf(entityIds, idx);
            packetSender.send(player, new WrapperPlayServerDestroyEntities(trimmed));
        }
    }

    @Override
    public void broadcastSpawn(PlacedItem item) {
        for (Player player : onlinePlayersSupplier.get()) {
            if (observerFilter.test(player, item)) {
                spawnForPlayer(player, item);
            }
        }
    }

    @Override
    public void broadcastUpdate(PlacedItem item) {
        Set<UUID> viewers = viewersByItem.get(item.id());
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        for (Player player : onlinePlayersSupplier.get()) {
            if (viewers.contains(player.getUniqueId())) {
                updateForPlayer(player, item);
            }
        }
    }

    @Override
    public void broadcastDestroy(UUID itemId) {
        VirtualEntityPair pair = entitiesByItem.remove(itemId);
        if (pair != null) {
            itemByInteractionEntityId.remove(pair.interactionEntityId());
        }
        Set<UUID> viewers = viewersByItem.remove(itemId);
        Collection<? extends Player> onlinePlayers = onlinePlayersSupplier.get();
        for (Player player : onlinePlayers) {
            clearHoverTextIfMatching(player, itemId);
            if (pair != null && viewers != null && viewers.contains(player.getUniqueId())) {
                Set<UUID> playerItems = visibleItemsByPlayer.get(player.getUniqueId());
                if (playerItems != null) {
                    playerItems.remove(itemId);
                }
                packetSender.send(player, new WrapperPlayServerDestroyEntities(pair.displayEntityId(), pair.interactionEntityId()));
            }
        }
    }

    @Override
    public void showHoverText(Player player, UUID itemId, Component text, Vec3d position, float scale) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(position, "position must not be null");

        ActiveHoverLabel current = hoverLabelsByPlayer.get(player.getUniqueId());
        if (current != null && current.itemId().equals(itemId)) {
            return;
        }
        if (current != null) {
            packetSender.send(player, new WrapperPlayServerDestroyEntities(current.entityId()));
        }

        int labelEntityId = entityIdSupplier.getAsInt();
        hoverLabelsByPlayer.put(player.getUniqueId(), new ActiveHoverLabel(itemId, labelEntityId));

        WrapperPlayServerSpawnEntity spawnText = new WrapperPlayServerSpawnEntity(
                labelEntityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(position.x(), position.y(), position.z()),
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        );
        WrapperPlayServerEntityMetadata textMeta = new WrapperPlayServerEntityMetadata(
                labelEntityId,
                metadataBuilder.buildTextDisplayMetadata(text, scale)
        );
        packetSender.send(player, spawnText);
        packetSender.send(player, textMeta);
    }

    @Override
    public void clearHoverText(Player player) {
        if (player == null) {
            return;
        }
        ActiveHoverLabel removed = hoverLabelsByPlayer.remove(player.getUniqueId());
        if (removed != null) {
            packetSender.send(player, new WrapperPlayServerDestroyEntities(removed.entityId()));
        }
    }

    @Override
    public boolean hasVisibleItems(Player player) {
        if (player == null) {
            return false;
        }
        Set<UUID> items = visibleItemsByPlayer.get(player.getUniqueId());
        return items != null && !items.isEmpty();
    }

    private void clearHoverTextIfMatching(Player player, UUID itemId) {
        ActiveHoverLabel current = hoverLabelsByPlayer.get(player.getUniqueId());
        if (current != null && current.itemId().equals(itemId)
                && hoverLabelsByPlayer.remove(player.getUniqueId(), current)) {
            packetSender.send(player, new WrapperPlayServerDestroyEntities(current.entityId()));
        }
    }

    @Override
    public Optional<UUID> resolveItemByVirtualEntityId(int entityId) {
        return Optional.ofNullable(itemByInteractionEntityId.get(entityId));
    }

    /**
     * Returns the allocated virtual entity pair for testing and diagnostics.
     *
     * @param itemId placed item ID
     * @return virtual entity pair if allocated
     */
    public Optional<VirtualEntityPair> getVirtualEntities(UUID itemId) {
        return Optional.ofNullable(entitiesByItem.get(itemId));
    }

    private static ItemStack convertSerializedItem(byte[] bytes) {
        org.bukkit.inventory.ItemStack bukkitStack = org.bukkit.inventory.ItemStack.deserializeBytes(bytes);
        return SpigotConversionUtil.fromBukkitItemStack(bukkitStack);
    }

    public record VirtualEntityPair(
            int displayEntityId,
            UUID displayUuid,
            int interactionEntityId,
            UUID interactionUuid
    ) {
    }

    private record ActiveHoverLabel(
            UUID itemId,
            int entityId
    ) {
    }
}
