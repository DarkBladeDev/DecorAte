package dev.decorate.paper;

import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PlacementMode;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.model.Vec3f;
import dev.decorate.core.config.DecorateSettings;
import dev.decorate.core.registry.SpatialRegistry;
import dev.decorate.paper.config.DecorAteConfigLoader;
import dev.decorate.paper.lookup.TargetedItemLookup;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecorAteConfigAndLookupTest {

    @Test
    void loadsDefaultConfigResource() {
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        DecorateSettings settings = DecorAteConfigLoader.loadSettings(yaml);
        assertTrue(settings.globalPlacementEnabled());
        assertTrue(settings.requireSneakForPlace());
        assertEquals(5.0, settings.maxPlaceDistance());
        assertEquals(32, settings.maxItemsPerBlock());
        assertEquals(PlacementMode.Mode.FREE, settings.defaultPlacementMode().mode());
        assertEquals(16, settings.defaultPlacementMode().gridDivisions());
        assertTrue(settings.hoverDisplayNameEnabled());
        assertEquals(2, settings.hoverCheckIntervalTicks());
        assertEquals(0.75f, settings.hoverDisplayScale());
        assertEquals(0.28, settings.hoverVerticalOffset(), 1e-6);
    }

    @Test
    void loadsHoverDisplayNameOverridesAndClampsInvalidValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("visibility.hover-display-name.enabled", false);
        yaml.set("visibility.hover-display-name.check-interval-ticks", 0);
        yaml.set("visibility.hover-display-name.scale", -1.0);
        yaml.set("visibility.hover-display-name.vertical-offset", -0.5);

        DecorateSettings clamped = DecorAteConfigLoader.loadSettings(yaml);
        assertFalse(clamped.hoverDisplayNameEnabled());
        assertEquals(1, clamped.hoverCheckIntervalTicks());
        assertEquals(0.1f, clamped.hoverDisplayScale());
        assertEquals(0.0, clamped.hoverVerticalOffset(), 1e-6);

        YamlConfiguration custom = new YamlConfiguration();
        custom.set("visibility.hover-display-name.enabled", true);
        custom.set("visibility.hover-display-name.check-interval-ticks", 4);
        custom.set("visibility.hover-display-name.scale", 0.9);
        custom.set("visibility.hover-display-name.vertical-offset", 0.42);

        DecorateSettings loaded = DecorAteConfigLoader.loadSettings(custom);
        assertTrue(loaded.hoverDisplayNameEnabled());
        assertEquals(4, loaded.hoverCheckIntervalTicks());
        assertEquals(0.9f, loaded.hoverDisplayScale());
        assertEquals(0.42, loaded.hoverVerticalOffset(), 1e-6);
    }

    @Test
    void loadsGlobalPlacementEnabledFlagAndDefault() {
        YamlConfiguration emptyYaml = new YamlConfiguration();
        assertTrue(DecorAteConfigLoader.loadSettings(emptyYaml).globalPlacementEnabled());

        YamlConfiguration disabledYaml = new YamlConfiguration();
        disabledYaml.set("placement.enabled", false);
        assertFalse(DecorAteConfigLoader.loadSettings(disabledYaml).globalPlacementEnabled());

        YamlConfiguration enabledYaml = new YamlConfiguration();
        enabledYaml.set("placement.enabled", true);
        assertTrue(DecorAteConfigLoader.loadSettings(enabledYaml).globalPlacementEnabled());
    }

    @Test
    void loadsMessagesAndPluginResourcesWithToggleEntries() {
        var messagesStream = getClass().getClassLoader().getResourceAsStream("messages.yml");
        assertNotNull(messagesStream);
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new InputStreamReader(messagesStream, StandardCharsets.UTF_8));
        assertNotNull(messages.getString("rejected.disabled"));
        assertNotNull(messages.getString("command.toggle-enabled"));
        assertNotNull(messages.getString("command.toggle-disabled"));
        assertNotNull(messages.getString("command.global-enabled"));
        assertNotNull(messages.getString("command.global-disabled"));

        var pluginStream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(pluginStream);
        YamlConfiguration pluginYml = YamlConfiguration.loadConfiguration(new InputStreamReader(pluginStream, StandardCharsets.UTF_8));
        assertEquals(true, pluginYml.get("permissions.decorate\\.command\\.toggle.default") != null
                || pluginYml.getConfigurationSection("permissions").getConfigurationSection("decorate.command.toggle").getBoolean("default"));
        assertEquals("op", pluginYml.getConfigurationSection("permissions").getConfigurationSection("decorate.command.global").getString("default"));
    }

    @Test
    void targetedItemLookupHitsItemInLineOfSight() {
        SpatialRegistry registry = new SpatialRegistry();
        PlacedItem item = new PlacedItem(
                UUID.randomUUID(),
                "world",
                new BlockCoordinate("world", 10, 64, 10),
                new SurfacePose(
                        new Vec3d(10.5, 65.02, 10.5),
                        SurfaceFace.UP,
                        0.0f,
                        PoseMode.FLAT,
                        Vec3f.ONE,
                        DisplayTransformType.FIXED,
                        0.4f,
                        0.4f
                ),
                UUID.randomUUID(),
                "Steve",
                false,
                new byte[] {1, 2},
                "default",
                "decor:mug",
                100L
        );
        registry.put(item);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        // Eye at (10.5, 66.2, 8.5) looking straight toward (10.5, 65.1, 10.5)
        Location eye = new Location(world, 10.5, 66.2, 8.5, 0.0f, 28.8f);
        when(player.getEyeLocation()).thenReturn(eye);

        TargetedItemLookup lookup = new TargetedItemLookup(registry, 5.0);
        assertEquals(item.id(), lookup.findTargetedItem(player).orElseThrow().id());

        // Looking away should return empty
        when(player.getEyeLocation()).thenReturn(new Location(world, 10.5, 66.2, 8.5, 180.0f, 0.0f));
        assertTrue(lookup.findTargetedItem(player).isEmpty());
    }

    @Test
    void targetedItemLookupRespectsSolidBlockOcclusion() {
        SpatialRegistry registry = new SpatialRegistry();
        PlacedItem item = new PlacedItem(
                UUID.randomUUID(),
                "world",
                new BlockCoordinate("world", 10, 64, 10),
                new SurfacePose(
                        new Vec3d(10.5, 65.02, 10.5),
                        SurfaceFace.UP,
                        0.0f,
                        PoseMode.FLAT,
                        Vec3f.ONE,
                        DisplayTransformType.FIXED,
                        0.4f,
                        0.4f
                ),
                UUID.randomUUID(),
                "Steve",
                false,
                new byte[] {1, 2},
                "default",
                "decor:mug",
                100L
        );
        registry.put(item);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        Location eye = new Location(world, 10.5, 66.2, 8.5, 0.0f, 28.8f);
        when(player.getEyeLocation()).thenReturn(eye);

        // Block hit between eye (z=8.5) and item (z=10.5) at z=9.5 occludes the item
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class), anyBoolean()))
                .thenReturn(new RayTraceResult(new Vector(10.5, 65.65, 9.5), mock(Block.class), BlockFace.NORTH));

        TargetedItemLookup lookup = new TargetedItemLookup(registry, 5.0);
        assertTrue(lookup.findTargetedItem(player).isEmpty(), "Item behind a solid block must not be targeted");

        // Block hit behind the item (at y=65.0, z=10.5) allows targeting the item in front of it
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class), anyBoolean()))
                .thenReturn(new RayTraceResult(new Vector(10.5, 65.0, 10.5), mock(Block.class), BlockFace.UP));

        assertEquals(item.id(), lookup.findTargetedItem(player).orElseThrow().id(),
                "Item in front of support block must still be targeted");
    }
}
