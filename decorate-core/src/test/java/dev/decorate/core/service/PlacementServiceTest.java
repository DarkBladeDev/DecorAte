package dev.decorate.core.service;

import dev.decorate.api.event.DecorInteractEvent;
import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.ItemInspection;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.api.spi.PlacementProfileResolver;
import dev.decorate.api.spi.ProfileMatcher;
import dev.decorate.api.spi.ProfileSource;
import dev.decorate.api.spi.ProtectionHook;
import dev.decorate.api.spi.StorageProvider;
import dev.decorate.core.config.DecorateSettings;
import dev.decorate.core.profile.DefaultProfileResolver;
import dev.decorate.core.registry.SpatialRegistry;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlacementServiceTest {

    private SpatialRegistry registry;
    private StorageProvider storage;
    private DisplayRenderer renderer;
    private DefaultProfileResolver profileResolver;
    private PlacementService service;
    private World world;
    private Block supportBlock;

    @BeforeEach
    void setUp() {
        registry = new SpatialRegistry();
        storage = mock(StorageProvider.class);
        when(storage.save(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(storage.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
        renderer = mock(DisplayRenderer.class);
        profileResolver = new DefaultProfileResolver();

        ItemStackCodec dummyCodec = new ItemStackCodec() {
            @Override
            public byte[] serialize(ItemStack stack) {
                return new byte[] {1, 2, 3};
            }

            @Override
            public ItemStack deserialize(byte[] bytes) {
                return mockItem(Material.PAPER, 1);
            }
        };

        service = new PlacementService(
                registry,
                storage,
                renderer,
                profileResolver,
                DecorateSettings.DEFAULTS,
                dummyCodec,
                event -> {}
        );

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        supportBlock = mock(Block.class);
        when(supportBlock.getWorld()).thenReturn(world);
        when(supportBlock.getX()).thenReturn(10);
        when(supportBlock.getY()).thenReturn(64);
        when(supportBlock.getZ()).thenReturn(20);
    }

    @Test
    void allowsMultipleItemsPerBlockAndTracksSurvivalConsumeAndPickup() {
        Player player = mockPlayer(UUID.randomUUID(), "Steve", GameMode.SURVIVAL);
        ItemStack stack = mockItem(Material.PAPER, 3);

        var first = service.tryPlace(player, supportBlock, BlockFace.UP, new Vec3d(10.2, 65.0, 20.2), stack);
        var second = service.tryPlace(player, supportBlock, BlockFace.UP, new Vec3d(10.8, 65.0, 20.8), stack);

        assertTrue(first.accepted());
        assertTrue(second.accepted());
        assertEquals(1, stack.getAmount());
        assertEquals(2, registry.countBySupportBlock(new BlockCoordinate("world", 10, 64, 20)));
        verify(renderer, times(2)).broadcastSpawn(any());

        // Rotate and toggle pose
        UUID id = first.placedItem().id();
        var rotated = service.handleInteraction(player, id, DecorInteractEvent.Action.ROTATE);
        assertTrue(rotated.accepted());
        assertEquals(225.0f, rotated.item().pose().yawDegrees(), 0.01f); // 180 + 45

        var toggled = service.handleInteraction(player, id, DecorInteractEvent.Action.TOGGLE_POSE);
        assertTrue(toggled.accepted());
        assertEquals(PoseMode.UPRIGHT, toggled.item().pose().poseMode());

        // Pickup returns 1 item in Survival
        var picked = service.handleInteraction(player, id, DecorInteractEvent.Action.PICKUP);
        assertTrue(picked.accepted());
        assertNotNull(picked.returnedItem());
        assertEquals(1, registry.countBySupportBlock(new BlockCoordinate("world", 10, 64, 20)));
        verify(renderer).broadcastDestroy(id);
    }

    @Test
    void creativeModeDoesNotConsumeAndDoesNotDropByDefault() {
        Player creativePlayer = mockPlayer(UUID.randomUUID(), "Builder", GameMode.CREATIVE);
        ItemStack stack = mockItem(Material.PAPER, 1);

        var placed = service.tryPlace(creativePlayer, supportBlock, BlockFace.UP, new Vec3d(10.5, 65.0, 20.5), stack);
        assertTrue(placed.accepted());
        verify(stack, times(0)).setAmount(0);

        Player survivalPlayer = mockPlayer(placed.placedItem().ownerUuid(), "Builder", GameMode.SURVIVAL);
        var picked = service.handleInteraction(survivalPlayer, placed.placedItem().id(), DecorInteractEvent.Action.PICKUP);
        assertTrue(picked.accepted());
        assertNull(picked.returnedItem());
    }

    @Test
    void protectionHookBlocksPlacementAndSupportBlockRemovalCleansUp() {
        UUID owner = UUID.randomUUID();
        Player player = mockPlayer(owner, "Alex", GameMode.SURVIVAL);
        ItemStack stack = mockItem(Material.PAPER, 2);

        var ok = service.tryPlace(player, supportBlock, BlockFace.UP, new Vec3d(10.5, 65.0, 20.5), stack);
        assertTrue(ok.accepted());

        // Auto-cleanup when support block breaks
        List<PlacementService.RemovedDrop> drops = service.handleSupportBlockRemoved(new BlockCoordinate("world", 10, 64, 20));
        assertEquals(1, drops.size());
        assertNotNull(drops.getFirst().dropStack());
        assertEquals(0, registry.countByChunk(new ChunkCoordinate("world", 0, 1)));

        // Protection hook blocks placement
        service.registerProtectionHook(new ProtectionHook() {
            @Override
            public String name() {
                return "DenyHook";
            }

            @Override
            public boolean canPlace(Player p, Location location, Block block) {
                return false;
            }

            @Override
            public boolean canInteract(Player p, PlacedItem item, DecorInteractEvent.Action action) {
                return false;
            }
        });

        var denied = service.tryPlace(player, supportBlock, BlockFace.UP, new Vec3d(10.5, 65.0, 20.5), stack);
        assertFalse(denied.accepted());
        assertEquals(PlacementService.OutcomeReason.PROTECTED_REGION, denied.reason());
    }

    @Test
    void enforcesBlockLimitAndProfileBlacklist() {
        profileResolver.registerSource(new ProfileSource() {
            @Override
            public String sourceId() {
                return "test";
            }

            @Override
            public List<ProfileEntry> loadProfiles() {
                return List.of(new ProfileEntry(
                        new ProfileMatcher() {
                            @Override
                            public int priority() {
                                return 100;
                            }

                            @Override
                            public boolean matches(ItemInspection inspection, ItemStack item) {
                                return inspection.material() == Material.BEDROCK;
                            }
                        },
                        new PlacementProfile(
                                "no_bedrock", 100, false,
                                PlacementProfile.DEFAULT.allowedFaces(),
                                PlacementProfile.DEFAULT.displayTransform(),
                                PlacementProfile.DEFAULT.scale(),
                                0.02, 0f, PoseMode.FLAT, 0.35f, 0.35f, null, null
                        )
                ));
            }
        });

        Player player = mockPlayer(UUID.randomUUID(), "Steve", GameMode.CREATIVE);
        var blockedProfile = service.tryPlace(player, supportBlock, BlockFace.UP, new Vec3d(10.5, 65.0, 20.5), mockItem(Material.BEDROCK, 1));
        assertFalse(blockedProfile.accepted());
        assertEquals(PlacementService.OutcomeReason.PROFILE_NOT_PLACEABLE, blockedProfile.reason());
    }

    private Player mockPlayer(UUID id, String name, GameMode mode) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.getGameMode()).thenReturn(mode);
        when(p.getLocation()).thenReturn(new Location(world, 10, 65, 22, 0f, 0f));
        when(p.getEffectivePermissions()).thenReturn(Set.of());
        return p;
    }

    private static ItemStack mockItem(Material material, int amount) {
        java.util.concurrent.atomic.AtomicInteger amt = new java.util.concurrent.atomic.AtomicInteger(amount);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenAnswer(inv -> amt.get());
        org.mockito.Mockito.doAnswer(inv -> {
            amt.set(inv.getArgument(0));
            return null;
        }).when(stack).setAmount(org.mockito.ArgumentMatchers.anyInt());
        when(stack.clone()).thenAnswer(inv -> {
            ItemStack cloned = mock(ItemStack.class);
            when(cloned.getType()).thenReturn(material);
            when(cloned.getAmount()).thenReturn(amt.get());
            return cloned;
        });
        return stack;
    }
}
