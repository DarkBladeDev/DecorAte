package dev.decorate.core.service;

import dev.decorate.api.event.DecorInteractEvent;
import dev.decorate.api.event.DecorPlaceEvent;
import dev.decorate.api.event.DecorRemoveEvent;
import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.ItemInspection;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PlacementMode;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.api.spi.PlacementProfileResolver;
import dev.decorate.api.spi.ProtectionHook;
import dev.decorate.api.spi.StorageProvider;
import dev.decorate.core.config.DecorateSettings;
import dev.decorate.core.geometry.PlacementGeometry;
import dev.decorate.core.profile.PaperItemInspector;
import dev.decorate.core.registry.SpatialRegistry;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core orchestration service handling placement, interaction (pickup/rotate/pose toggle),
 * limits, protection checks, support-block cleanup, and per-player placement modes.
 */
public final class PlacementService {

    public static final String PERMISSION_BYPASS_LIMIT = "decorate.bypass.limit";
    public static final String PERMISSION_BYPASS_OWNER = "decorate.bypass.owner";

    private final SpatialRegistry registry;
    private final StorageProvider storage;
    private final DisplayRenderer renderer;
    private final PlacementProfileResolver profileResolver;
    private final ItemStackCodec itemCodec;
    private final EventDispatcher eventDispatcher;
    private final List<ProtectionHook> protectionHooks = new CopyOnWriteArrayList<>();
    private final Map<UUID, PlacementMode> playerModes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerTotalCounts = new ConcurrentHashMap<>();
    private final Set<UUID> enabledPlacementPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean globalEnabled;
    private volatile DecorateSettings settings;

    public PlacementService(
            SpatialRegistry registry,
            StorageProvider storage,
            DisplayRenderer renderer,
            PlacementProfileResolver profileResolver,
            DecorateSettings settings
    ) {
        this(registry, storage, renderer, profileResolver, settings, ItemStackCodec.PAPER_BYTES, EventDispatcher.BUKKIT);
    }

    public PlacementService(
            SpatialRegistry registry,
            StorageProvider storage,
            DisplayRenderer renderer,
            PlacementProfileResolver profileResolver,
            DecorateSettings settings,
            ItemStackCodec itemCodec,
            EventDispatcher eventDispatcher
    ) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.profileResolver = Objects.requireNonNull(profileResolver, "profileResolver must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.globalEnabled = new AtomicBoolean(settings.globalPlacementEnabled());
        this.itemCodec = Objects.requireNonNull(itemCodec, "itemCodec must not be null");
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher must not be null");
    }

    public void updateSettings(DecorateSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.globalEnabled.set(settings.globalPlacementEnabled());
    }

    public DecorateSettings settings() {
        return settings;
    }

    public void registerProtectionHook(ProtectionHook hook) {
        protectionHooks.add(Objects.requireNonNull(hook, "hook must not be null"));
    }

    public PlacementMode getPlayerMode(UUID playerId) {
        return playerModes.getOrDefault(playerId, settings.defaultPlacementMode());
    }

    public void setPlayerMode(UUID playerId, PlacementMode mode) {
        playerModes.put(playerId, Objects.requireNonNull(mode, "mode must not be null"));
    }

    public boolean isGlobalEnabled() {
        return globalEnabled.get();
    }

    public void setGlobalEnabled(boolean enabled) {
        globalEnabled.set(enabled);
    }

    public boolean toggleGlobalEnabled() {
        while (true) {
            boolean current = globalEnabled.get();
            boolean next = !current;
            if (globalEnabled.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    public boolean isPlayerPlacementEnabled(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return enabledPlacementPlayers.contains(playerId);
    }

    public void setPlayerPlacementEnabled(UUID playerId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        if (enabled) {
            enabledPlacementPlayers.add(playerId);
        } else {
            enabledPlacementPlayers.remove(playerId);
        }
    }

    public boolean togglePlayerPlacementEnabled(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        synchronized (enabledPlacementPlayers) {
            if (enabledPlacementPlayers.remove(playerId)) {
                return false;
            }
            enabledPlacementPlayers.add(playerId);
            return true;
        }
    }

    public void clearPlayerSession(UUID playerId) {
        if (playerId != null) {
            enabledPlacementPlayers.remove(playerId);
        }
    }

    /**
     * Seeds or refreshes cached total placement count for an online player from persistent storage.
     *
     * @param playerId player UUID
     * @param count persisted placement count
     */
    public void setCachedOwnerCount(UUID playerId, int count) {
        playerTotalCounts.put(playerId, Math.max(0, count));
    }

    public int getOwnerCount(UUID playerId) {
        return playerTotalCounts.getOrDefault(playerId, registry.countLoadedByOwner(playerId));
    }

    /**
     * Resolves the effective placement limit for a player, checking {@code decorate.limit.<n>} permissions.
     *
     * @param player target player
     * @return effective limit ({@code <= 0} means unlimited)
     */
    public int resolvePlayerLimit(Player player) {
        if (player.hasPermission(PERMISSION_BYPASS_LIMIT)) {
            return -1;
        }
        int best = settings.defaultPlayerLimit();
        for (var perm : player.getEffectivePermissions()) {
            if (!perm.getValue()) {
                continue;
            }
            String node = perm.getPermission();
            if (node.startsWith("decorate.limit.")) {
                try {
                    int parsed = Integer.parseInt(node.substring("decorate.limit.".length()));
                    if (parsed <= 0) {
                        return -1;
                    }
                    best = Math.max(best, parsed);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return best;
    }

    /**
     * Attempts to place one unit of {@code handItem} onto the targeted block face at {@code hitPoint}.
     *
     * @param player placing player
     * @param supportBlock targeted supporting block
     * @param blockFace targeted face
     * @param hitPoint exact hit coordinates
     * @param handItem item in main hand (not mutated when failed)
     * @return placement outcome
     */
    public PlaceOutcome tryPlace(
            Player player,
            Block supportBlock,
            BlockFace blockFace,
            Vec3d hitPoint,
            ItemStack handItem
    ) {
        if (!isGlobalEnabled()) {
            return PlaceOutcome.rejected(OutcomeReason.GLOBAL_DISABLED);
        }
        if (!isPlayerPlacementEnabled(player.getUniqueId())) {
            return PlaceOutcome.rejected(OutcomeReason.PLAYER_DISABLED);
        }
        if (handItem == null || isAir(handItem.getType()) || handItem.getAmount() <= 0) {
            return PlaceOutcome.rejected(OutcomeReason.INVALID_ITEM);
        }

        SurfaceFace face;
        try {
            face = SurfaceFace.fromBlockFace(blockFace);
        } catch (IllegalArgumentException ex) {
            return PlaceOutcome.rejected(OutcomeReason.INVALID_FACE);
        }

        ItemInspection inspection = PaperItemInspector.inspect(handItem);
        PlacementProfile profile = profileResolver.resolve(inspection);
        if (!profile.placeable()) {
            return PlaceOutcome.rejected(OutcomeReason.PROFILE_NOT_PLACEABLE);
        }
        if (!profile.allowsFace(face)) {
            return PlaceOutcome.rejected(OutcomeReason.FACE_NOT_ALLOWED);
        }

        String worldName = supportBlock.getWorld().getName();
        BlockCoordinate blockCoord = new BlockCoordinate(worldName, supportBlock.getX(), supportBlock.getY(), supportBlock.getZ());

        if (settings.maxItemsPerBlock() > 0 && registry.countBySupportBlock(blockCoord) >= settings.maxItemsPerBlock()) {
            return PlaceOutcome.rejected(OutcomeReason.BLOCK_LIMIT_REACHED);
        }

        PlacementMode mode = getPlayerMode(player.getUniqueId());
        SurfacePose pose = PlacementGeometry.computeInitialPose(
                blockCoord,
                face,
                hitPoint,
                player.getLocation().getYaw(),
                profile,
                mode
        );
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromPosition(worldName, pose.exactPosition().x(), pose.exactPosition().z());

        if (settings.maxItemsPerChunk() > 0 && registry.countByChunk(chunkCoord) >= settings.maxItemsPerChunk()) {
            return PlaceOutcome.rejected(OutcomeReason.CHUNK_LIMIT_REACHED);
        }

        int playerLimit = resolvePlayerLimit(player);
        if (playerLimit > 0 && getOwnerCount(player.getUniqueId()) >= playerLimit) {
            return PlaceOutcome.rejected(OutcomeReason.PLAYER_LIMIT_REACHED);
        }

        Location targetLoc = new Location(
                supportBlock.getWorld(),
                pose.exactPosition().x(),
                pose.exactPosition().y(),
                pose.exactPosition().z()
        );
        for (ProtectionHook hook : protectionHooks) {
            if (!hook.canPlace(player, targetLoc, supportBlock)) {
                return PlaceOutcome.rejected(OutcomeReason.PROTECTED_REGION);
            }
        }

        ItemStack singleUnit = handItem.clone();
        singleUnit.setAmount(1);

        DecorPlaceEvent placeEvent = new DecorPlaceEvent(player, supportBlock, blockFace, singleUnit, profile, pose);
        eventDispatcher.callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            return PlaceOutcome.rejected(OutcomeReason.EVENT_CANCELLED);
        }

        SurfacePose finalPose = placeEvent.getPose();
        byte[] serialized = itemCodec.serialize(singleUnit);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        PlacedItem placed = new PlacedItem(
                UUID.randomUUID(),
                worldName,
                blockCoord,
                finalPose,
                player.getUniqueId(),
                player.getName(),
                creative,
                serialized,
                profile.id(),
                inspection.descriptiveLabel(),
                System.currentTimeMillis()
        );

        if (!creative) {
            int remaining = handItem.getAmount() - 1;
            handItem.setAmount(Math.max(0, remaining));
        }

        registry.put(placed);
        playerTotalCounts.merge(player.getUniqueId(), 1, Integer::sum);
        storage.save(placed);
        renderer.broadcastSpawn(placed);
        return PlaceOutcome.success(placed);
    }

    /**
     * Handles a direct interaction (pickup, rotate, or pose toggle) against a placed item.
     *
     * @param player interacting player
     * @param itemId target placed item ID
     * @param action requested action
     * @return interaction outcome
     */
    public InteractOutcome handleInteraction(Player player, UUID itemId, DecorInteractEvent.Action action) {
        if (!isGlobalEnabled()) {
            return InteractOutcome.rejected(OutcomeReason.GLOBAL_DISABLED);
        }
        Optional<PlacedItem> existingOpt = registry.findById(itemId);
        if (existingOpt.isEmpty()) {
            return InteractOutcome.rejected(OutcomeReason.NOT_FOUND);
        }
        PlacedItem item = existingOpt.get();

        if (settings.ownerOnlyInteraction()
                && !item.ownerUuid().equals(player.getUniqueId())
                && !player.hasPermission(PERMISSION_BYPASS_OWNER)) {
            return InteractOutcome.rejected(OutcomeReason.NOT_OWNER);
        }

        for (ProtectionHook hook : protectionHooks) {
            if (!hook.canInteract(player, item, action)) {
                return InteractOutcome.rejected(OutcomeReason.PROTECTED_REGION);
            }
        }

        SurfacePose nextPose = switch (action) {
            case PICKUP -> null;
            case ROTATE -> item.pose().withRotatedYaw(settings.rotationStepDegrees());
            case TOGGLE_POSE -> item.pose().withToggledPose();
        };

        DecorInteractEvent interactEvent = new DecorInteractEvent(player, item, action, nextPose);
        eventDispatcher.callEvent(interactEvent);
        if (interactEvent.isCancelled()) {
            return InteractOutcome.rejected(OutcomeReason.EVENT_CANCELLED);
        }

        if (action == DecorInteractEvent.Action.PICKUP) {
            boolean shouldReturn = shouldReturnToPlayer(player, item);
            DecorRemoveEvent removeEvent = new DecorRemoveEvent(
                    item,
                    DecorRemoveEvent.Reason.PLAYER_PICKUP,
                    player,
                    shouldReturn
            );
            eventDispatcher.callEvent(removeEvent);
            if (removeEvent.isCancelled()) {
                return InteractOutcome.rejected(OutcomeReason.EVENT_CANCELLED);
            }

            registry.remove(item.id());
            decrementOwnerCount(item.ownerUuid());
            storage.delete(item.id());
            renderer.broadcastDestroy(item.id());

            ItemStack returnedStack = removeEvent.isDropItem() ? itemCodec.deserialize(item.serializedItem()) : null;
            return InteractOutcome.pickedUp(item, returnedStack);
        }

        SurfacePose finalPose = interactEvent.getNextPose() != null ? interactEvent.getNextPose() : nextPose;
        PlacedItem updated = item.withPose(finalPose);
        registry.put(updated);
        storage.save(updated);
        renderer.broadcastUpdate(updated);
        return InteractOutcome.updated(updated);
    }

    /**
     * Handles cleanup when a supporting block is broken, exploded, or pushed by a piston.
     *
     * @param supportBlock supporting block coordinate
     * @return list of removed items together with deserialized drops (when applicable)
     */
    public List<RemovedDrop> handleSupportBlockRemoved(BlockCoordinate supportBlock) {
        if (!settings.autoCleanupOnSupportBreak()) {
            return List.of();
        }
        List<PlacedItem> attached = registry.findBySupportBlock(supportBlock);
        if (attached.isEmpty()) {
            return List.of();
        }

        List<RemovedDrop> drops = new java.util.ArrayList<>(attached.size());
        for (PlacedItem item : attached) {
            boolean drop = !item.creativePlaced() && settings.dropSurvivalOnAutoCleanup();
            DecorRemoveEvent removeEvent = new DecorRemoveEvent(
                    item,
                    DecorRemoveEvent.Reason.SUPPORT_BLOCK_BROKEN,
                    null,
                    drop
            );
            eventDispatcher.callEvent(removeEvent);
            if (removeEvent.isCancelled()) {
                continue;
            }
            registry.remove(item.id());
            decrementOwnerCount(item.ownerUuid());
            storage.delete(item.id());
            renderer.broadcastDestroy(item.id());
            ItemStack dropStack = removeEvent.isDropItem() ? itemCodec.deserialize(item.serializedItem()) : null;
            drops.add(new RemovedDrop(item, dropStack));
        }
        return drops;
    }

    /**
     * Administratively removes all loaded placed items within a spherical radius.
     *
     * @param world world name
     * @param center center position
     * @param radius radius in blocks
     * @param actor optional command sender player
     * @return number of removed items
     */
    public int clearInRadius(String world, Vec3d center, double radius, @Nullable Player actor) {
        List<PlacedItem> targets = registry.findInRadius(world, center, radius);
        return removeBatch(targets, actor);
    }

    /**
     * Administratively removes all placed items inside a chunk.
     *
     * @param chunk target chunk
     * @param actor optional command sender player
     * @return number of removed items
     */
    public int clearChunk(ChunkCoordinate chunk, @Nullable Player actor) {
        List<PlacedItem> targets = registry.findByChunk(chunk);
        return removeBatch(targets, actor);
    }

    private int removeBatch(List<PlacedItem> targets, @Nullable Player actor) {
        int removedCount = 0;
        for (PlacedItem item : targets) {
            DecorRemoveEvent removeEvent = new DecorRemoveEvent(
                    item,
                    DecorRemoveEvent.Reason.COMMAND_CLEAR,
                    actor,
                    false
            );
            eventDispatcher.callEvent(removeEvent);
            if (removeEvent.isCancelled()) {
                continue;
            }
            registry.remove(item.id());
            decrementOwnerCount(item.ownerUuid());
            storage.delete(item.id());
            renderer.broadcastDestroy(item.id());
            removedCount++;
        }
        return removedCount;
    }

    private boolean shouldReturnToPlayer(Player player, PlacedItem item) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        if (item.creativePlaced() && !settings.creativeDropOnPickup()) {
            return false;
        }
        return true;
    }

    private void decrementOwnerCount(UUID ownerUuid) {
        playerTotalCounts.computeIfPresent(ownerUuid, (k, v) -> Math.max(0, v - 1));
    }

    private static boolean isAir(org.bukkit.Material material) {
        return material == null
                || material == org.bukkit.Material.AIR
                || material == org.bukkit.Material.CAVE_AIR
                || material == org.bukkit.Material.VOID_AIR;
    }

    public enum OutcomeReason {
        OK,
        INVALID_ITEM,
        INVALID_FACE,
        PROFILE_NOT_PLACEABLE,
        FACE_NOT_ALLOWED,
        BLOCK_LIMIT_REACHED,
        CHUNK_LIMIT_REACHED,
        PLAYER_LIMIT_REACHED,
        PROTECTED_REGION,
        EVENT_CANCELLED,
        NOT_FOUND,
        NOT_OWNER,
        GLOBAL_DISABLED,
        PLAYER_DISABLED
    }

    public record PlaceOutcome(boolean accepted, OutcomeReason reason, @Nullable PlacedItem placedItem) {
        public static PlaceOutcome success(PlacedItem item) {
            return new PlaceOutcome(true, OutcomeReason.OK, item);
        }

        public static PlaceOutcome rejected(OutcomeReason reason) {
            return new PlaceOutcome(false, reason, null);
        }
    }

    public record InteractOutcome(
            boolean accepted,
            OutcomeReason reason,
            @Nullable PlacedItem item,
            @Nullable ItemStack returnedItem
    ) {
        public static InteractOutcome pickedUp(PlacedItem removed, @Nullable ItemStack returnedItem) {
            return new InteractOutcome(true, OutcomeReason.OK, removed, returnedItem);
        }

        public static InteractOutcome updated(PlacedItem updated) {
            return new InteractOutcome(true, OutcomeReason.OK, updated, null);
        }

        public static InteractOutcome rejected(OutcomeReason reason) {
            return new InteractOutcome(false, reason, null, null);
        }
    }

    public record RemovedDrop(PlacedItem item, @Nullable ItemStack dropStack) {
    }
}
