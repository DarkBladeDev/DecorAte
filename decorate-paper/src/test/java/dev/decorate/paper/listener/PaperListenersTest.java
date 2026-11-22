package dev.decorate.paper.listener;

import dev.decorate.api.event.DecorInteractEvent;
import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.api.spi.StorageProvider;
import dev.decorate.core.config.DecorateSettings;
import dev.decorate.core.profile.DefaultProfileResolver;
import dev.decorate.core.registry.SpatialRegistry;
import dev.decorate.core.service.ItemStackCodec;
import dev.decorate.core.service.PlacementService;
import dev.decorate.core.storage.ChunkVisibilityManager;
import dev.decorate.hooks.papi.NoOpPlaceholderService;
import dev.decorate.paper.config.MessageService;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperListenersTest {

    private SpatialRegistry registry;
    private StorageProvider storage;
    private DisplayRenderer renderer;
    private PlacementService placementService;
    private World world;
    private Block block;

    @BeforeEach
    void setUp() {
        registry = new SpatialRegistry();
        storage = mock(StorageProvider.class);
        when(storage.save(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(storage.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(storage.loadChunk(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        renderer = mock(DisplayRenderer.class);

        ItemStack droppedMock = mock(ItemStack.class);
        placementService = new PlacementService(
                registry,
                storage,
                renderer,
                new DefaultProfileResolver(),
                DecorateSettings.DEFAULTS,
                new ItemStackCodec() {
                    @Override
                    public byte[] serialize(ItemStack stack) {
                        return new byte[] {1, 2};
                    }

                    @Override
                    public ItemStack deserialize(byte[] bytes) {
                        return droppedMock;
                    }
                },
                event -> {}
        );

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(4);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(8);
    }

    @Test
    void triggerPlacesItemAndSupportBlockBreakDropsIt() {
        MessageService messages = new MessageService(new YamlConfiguration(), new NoOpPlaceholderService());
        PlacementTriggerListener triggerListener = new PlacementTriggerListener(placementService, messages);
        SupportBlockListener supportListener = new SupportBlockListener(placementService);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Steve");
        when(player.isSneaking()).thenReturn(true);
        when(player.hasPermission(PlacementTriggerListener.PERMISSION_PLACE)).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(new Location(world, 4, 65, 10));
        when(player.getEffectivePermissions()).thenReturn(Set.of());

        AtomicInteger amt = new AtomicInteger(2);
        ItemStack handItem = mock(ItemStack.class);
        when(handItem.getType()).thenReturn(Material.PAPER);
        when(handItem.getAmount()).thenAnswer(inv -> amt.get());
        doAnswer(inv -> {
            amt.set(inv.getArgument(0));
            return null;
        }).when(handItem).setAmount(any(Integer.class));
        when(handItem.clone()).thenReturn(mock(ItemStack.class));

        RayTraceResult ray = new RayTraceResult(new Vector(4.35, 65.0, 8.65), block, BlockFace.UP);
        when(player.rayTraceBlocks(anyDouble(), any())).thenReturn(ray);

        placementService.setPlayerPlacementEnabled(player.getUniqueId(), true);

        PlayerInteractEvent interactEvent = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, handItem, block, BlockFace.UP, EquipmentSlot.HAND
        );
        triggerListener.onPlayerInteract(interactEvent);

        assertTrue(interactEvent.isCancelled());
        assertEquals(1, registry.countBySupportBlock(new BlockCoordinate("world", 4, 64, 8)));

        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        supportListener.onBlockBreak(breakEvent);
        assertEquals(0, registry.countBySupportBlock(new BlockCoordinate("world", 4, 64, 8)));
        verify(world).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }

    @Test
    void visibilityListenerLoadsChunksOnChunkLoadAndCrossChunkMove() {
        ChunkVisibilityManager visibility = new ChunkVisibilityManager(registry, storage, renderer, 1);
        VisibilityLifecycleListener listener = new VisibilityLifecycleListener(visibility, storage, placementService, Runnable::run);

        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);

        listener.onChunkLoad(new ChunkLoadEvent(chunk, false));
        assertTrue(registry.isChunkLoaded(new ChunkCoordinate("world", 0, 0)));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        Location from = new Location(world, 15.9, 64, 5.0);
        Location to = new Location(world, 16.1, 64, 5.0);
        when(player.getLocation()).thenReturn(to);

        listener.onPlayerMove(new PlayerMoveEvent(player, from, to));
        assertTrue(visibility.isChunkVisibleToPlayer(player.getUniqueId(), new ChunkCoordinate("world", 1, 0)));
    }

    @Test
    void disabledPlacementLeavesVanillaInteractUncancelledAndPreservesPersonalInteraction() {
        YamlConfiguration msgYaml = new YamlConfiguration();
        msgYaml.set("rejected.disabled", "<red>Disabled</red>");
        MessageService messages = new MessageService(msgYaml, new NoOpPlaceholderService());
        PlacementTriggerListener triggerListener = new PlacementTriggerListener(placementService, messages);
        DecorInteractionHandler interactionHandler = new DecorInteractionHandler(placementService, messages);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Steve");
        when(player.isSneaking()).thenReturn(true);
        when(player.hasPermission(PlacementTriggerListener.PERMISSION_PLACE)).thenReturn(true);
        when(player.hasPermission(DecorInteractionHandler.PERMISSION_INTERACT)).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(new Location(world, 4, 65, 10));
        when(player.getEffectivePermissions()).thenReturn(Set.of());

        AtomicInteger amt = new AtomicInteger(2);
        ItemStack handItem = mock(ItemStack.class);
        when(handItem.getType()).thenReturn(Material.PAPER);
        when(handItem.getAmount()).thenAnswer(inv -> amt.get());
        doAnswer(inv -> {
            amt.set(inv.getArgument(0));
            return null;
        }).when(handItem).setAmount(any(Integer.class));
        when(handItem.clone()).thenReturn(mock(ItemStack.class));
        RayTraceResult ray = new RayTraceResult(new Vector(4.35, 65.0, 8.65), block, BlockFace.UP);
        when(player.rayTraceBlocks(anyDouble(), any())).thenReturn(ray);

        // 0. Personal toggle starts disabled by default: vanilla interact is uncancelled without raytracing
        assertFalse(placementService.isPlayerPlacementEnabled(playerId));
        PlayerInteractEvent defaultOffEvent = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, handItem, block, BlockFace.UP, EquipmentSlot.HAND
        );
        triggerListener.onPlayerInteract(defaultOffEvent);
        assertFalse(defaultOffEvent.useInteractedBlock() == org.bukkit.event.Event.Result.DENY);
        assertEquals(0, registry.countBySupportBlock(new BlockCoordinate("world", 4, 64, 8)));
        assertEquals(2, amt.get());

        // Seed one placed item after enabling personal placement
        placementService.setPlayerPlacementEnabled(playerId, true);
        PlacedItem seeded = placementService.tryPlace(player, block, BlockFace.UP, new Vec3d(4.35, 65.0, 8.65), handItem)
                .placedItem();
        assertEquals(1, amt.get());

        // 1. Personal toggle OFF: placement trigger does not raytrace or cancel vanilla event
        placementService.setPlayerPlacementEnabled(playerId, false);
        PlayerInteractEvent personalOffEvent = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, handItem, block, BlockFace.UP, EquipmentSlot.HAND
        );
        triggerListener.onPlayerInteract(personalOffEvent);
        assertFalse(personalOffEvent.useInteractedBlock() == org.bukkit.event.Event.Result.DENY);
        assertEquals(1, registry.countBySupportBlock(new BlockCoordinate("world", 4, 64, 8)));
        assertEquals(1, amt.get());

        // Interaction on existing item STILL works while personal toggle is OFF
        interactionHandler.onInteract(player, seeded.id(), DecorInteractEvent.Action.ROTATE);
        assertEquals(225.0f, registry.findById(seeded.id()).orElseThrow().pose().yawDegrees(), 0.01f);

        // 2. Global toggle OFF: placement trigger leaves vanilla intact AND interaction rejects with message
        placementService.setGlobalEnabled(false);
        PlayerInteractEvent globalOffEvent = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, handItem, block, BlockFace.UP, EquipmentSlot.HAND
        );
        triggerListener.onPlayerInteract(globalOffEvent);
        assertFalse(globalOffEvent.useInteractedBlock() == org.bukkit.event.Event.Result.DENY);
        assertEquals(1, registry.countBySupportBlock(new BlockCoordinate("world", 4, 64, 8)));

        interactionHandler.onInteract(player, seeded.id(), DecorInteractEvent.Action.ROTATE);
        assertEquals(225.0f, registry.findById(seeded.id()).orElseThrow().pose().yawDegrees(), 0.01f);
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));

        // Ensure rayTraceBlocks was never invoked on disabled triggers
        verify(player, never()).rayTraceBlocks(anyDouble(), any());
    }

    @Test
    void playerQuitResetsPersonalPlacementSession() {
        ChunkVisibilityManager visibility = new ChunkVisibilityManager(registry, storage, renderer, 1);
        VisibilityLifecycleListener listener = new VisibilityLifecycleListener(visibility, storage, placementService, Runnable::run);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        placementService.setPlayerPlacementEnabled(playerId, true);
        assertTrue(placementService.isPlayerPlacementEnabled(playerId));

        listener.onPlayerQuit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertFalse(placementService.isPlayerPlacementEnabled(playerId));
    }
}
