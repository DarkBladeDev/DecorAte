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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
