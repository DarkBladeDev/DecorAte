package dev.decorate.hooks.papi;

import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.model.Vec3f;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.api.spi.StorageProvider;
import dev.decorate.core.config.DecorateSettings;
import dev.decorate.core.profile.DefaultProfileResolver;
import dev.decorate.core.registry.SpatialRegistry;
import dev.decorate.core.service.PlacementService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PapiPlaceholderTest {

    @Test
    void noOpPlaceholderServiceReturnsInputUnchanged() {
        NoOpPlaceholderService service = new NoOpPlaceholderService();
        assertFalse(service.isAvailable());
        assertEquals("Hello %player_name%", service.parse(null, "Hello %player_name%"));
    }

    @Test
    void expansionResolvesPlayerChunkAndLookingPlaceholders() {
        SpatialRegistry registry = new SpatialRegistry();
        PlacementService placementService = new PlacementService(
                registry,
                mock(StorageProvider.class),
                mock(DisplayRenderer.class),
                new DefaultProfileResolver(),
                DecorateSettings.DEFAULTS,
                new dev.decorate.core.service.ItemStackCodec() {
                    @Override
                    public byte[] serialize(org.bukkit.inventory.ItemStack stack) {
                        return new byte[] {1};
                    }

                    @Override
                    public org.bukkit.inventory.ItemStack deserialize(byte[] bytes) {
                        return null;
                    }
                },
                event -> {}
        );

        UUID ownerId = UUID.randomUUID();
        PlacedItem item = new PlacedItem(
                UUID.randomUUID(),
                "world",
                new BlockCoordinate("world", 5, 64, 5),
                new SurfacePose(new Vec3d(5.5, 65.02, 5.5), SurfaceFace.UP, 0f, PoseMode.FLAT, Vec3f.ONE, DisplayTransformType.FIXED, 0.35f, 0.35f),
                ownerId,
                "Alex",
                false,
                new byte[] {1},
                "taza_cafe",
                "decor:taza_cafe",
                100L
        );
        registry.put(item);
        placementService.setCachedOwnerCount(ownerId, 1);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 5.0, 65.0, 5.0));
        when(player.getEffectivePermissions()).thenReturn(Set.of());

        DecorAteExpansion expansion = new DecorAteExpansion(
                "0.1.0",
                registry,
                placementService,
                p -> Optional.of(item)
        );

        assertEquals("decorate", expansion.getIdentifier());
        assertEquals("1", expansion.onPlaceholderRequest(player, "count"));
        assertEquals("1", expansion.onPlaceholderRequest(player, "world_count"));
        assertEquals("200", expansion.onPlaceholderRequest(player, "limit"));
        assertEquals("199", expansion.onPlaceholderRequest(player, "remaining"));
        assertEquals("1", expansion.onPlaceholderRequest(player, "chunk_count"));
        assertEquals("decor:taza_cafe", expansion.onPlaceholderRequest(player, "looking_model"));
        assertEquals("Alex", expansion.onPlaceholderRequest(player, "looking_owner"));
        assertNull(expansion.onPlaceholderRequest(player, "unknown_key"));

        when(player.hasPermission(PlacementService.PERMISSION_BYPASS_LIMIT)).thenReturn(true);
        assertEquals("unlimited", expansion.onPlaceholderRequest(player, "limit"));
        assertEquals("unlimited", expansion.onPlaceholderRequest(player, "remaining"));

        // Personal and global toggle placeholders track state independently
        assertEquals("true", expansion.onPlaceholderRequest(player, "enabled"));
        assertEquals("true", expansion.onPlaceholderRequest(player, "enabled_global"));

        placementService.setPlayerPlacementEnabled(ownerId, false);
        assertEquals("false", expansion.onPlaceholderRequest(player, "enabled"));
        assertEquals("true", expansion.onPlaceholderRequest(player, "enabled_global"));

        placementService.setPlayerPlacementEnabled(ownerId, true);
        placementService.setGlobalEnabled(false);
        assertEquals("true", expansion.onPlaceholderRequest(player, "enabled"));
        assertEquals("false", expansion.onPlaceholderRequest(player, "enabled_global"));
        assertEquals("false", expansion.onPlaceholderRequest(null, "enabled_global"));
    }
}
