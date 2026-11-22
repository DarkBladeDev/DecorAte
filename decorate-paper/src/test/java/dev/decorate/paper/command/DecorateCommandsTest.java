package dev.decorate.paper.command;

import dev.decorate.api.model.BlockCoordinate;
import dev.decorate.api.model.CustomModelDataSpec;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.ItemTemplate;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.PlacementMode;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.api.model.Vec3d;
import dev.decorate.api.model.Vec3f;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.api.spi.ProfileMatcher;
import dev.decorate.api.spi.ProfileSource;
import dev.decorate.api.spi.StorageProvider;
import dev.decorate.core.config.DecorateSettings;
import dev.decorate.core.profile.DefaultProfileResolver;
import dev.decorate.core.registry.SpatialRegistry;
import dev.decorate.core.service.ItemStackCodec;
import dev.decorate.core.service.PlacementService;
import dev.decorate.hooks.papi.NoOpPlaceholderService;
import dev.decorate.paper.config.MessageService;
import dev.decorate.paper.lookup.TargetedItemLookup;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecorateCommandsTest {

    private SpatialRegistry registry;
    private PlacementService placementService;
    private DefaultProfileResolver profileResolver;
    private CommandManager<CommandSender> commandManager;
    private AtomicBoolean reloaded;
    private Player player;

    @BeforeEach
    void setUp() {
        registry = new SpatialRegistry();
        StorageProvider storage = mock(StorageProvider.class);
        when(storage.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
        DisplayRenderer renderer = mock(DisplayRenderer.class);

        profileResolver = new DefaultProfileResolver();
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
                            public boolean matches(dev.decorate.api.model.ItemInspection inspection, ItemStack item) {
                                return false;
                            }
                        },
                        new PlacementProfile(
                                "coffee_mug",
                                100,
                                true,
                                Set.of(SurfaceFace.UP),
                                DisplayTransformType.FIXED,
                                Vec3f.ONE,
                                0.02,
                                0f,
                                PoseMode.UPRIGHT,
                                0.3f,
                                0.3f,
                                null,
                                new ItemTemplate(Material.PAPER, "<gold>Mug", List.of(), "decor:coffee_mug", CustomModelDataSpec.EMPTY)
                        )
                ));
            }
        });

        placementService = new PlacementService(
                registry,
                storage,
                renderer,
                profileResolver,
                DecorateSettings.DEFAULTS,
                new ItemStackCodec() {
                    @Override
                    public byte[] serialize(ItemStack stack) {
                        return new byte[] {1};
                    }

                    @Override
                    public ItemStack deserialize(byte[] bytes) {
                        return mock(ItemStack.class);
                    }
                },
                event -> {}
        );

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Steve");
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 5.0, 65.0, 5.0));
        when(player.getEyeLocation()).thenReturn(new Location(world, 5.5, 66.2, 3.5, 0f, 28.8f));
        when(player.hasPermission(any(String.class))).thenReturn(true);

        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new java.util.HashMap<>());
        when(player.getInventory()).thenReturn(inventory);

        reloaded = new AtomicBoolean(false);
        YamlConfiguration messagesYaml = new YamlConfiguration();
        messagesYaml.set("command.player-only", "<red>Player only</red>");
        messagesYaml.set("command.toggle-enabled", "<green>Enabled</green>");
        messagesYaml.set("command.toggle-disabled", "<yellow>Disabled</yellow>");
        messagesYaml.set("command.global-enabled", "<green>Global enabled</green>");
        messagesYaml.set("command.global-disabled", "<red>Global disabled</red>");
        MessageService messages = new MessageService(messagesYaml, new NoOpPlaceholderService());

        commandManager = new CommandManager<>(
                ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler()
        ) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return sender.hasPermission(permission);
            }
        };

        // Register a simple Player parser for headless tests (no Bukkit.getPlayer lookup)
        commandManager.parserRegistry().registerParserSupplier(
                io.leangen.geantyref.TypeToken.get(Player.class),
                params -> (ctx, input) -> {
                    String name = input.readString();
                    if (name.equalsIgnoreCase(player.getName())) {
                        return org.incendo.cloud.parser.ArgumentParseResult.success(player);
                    }
                    return org.incendo.cloud.parser.ArgumentParseResult.failure(new IllegalArgumentException("Unknown player"));
                }
        );

        AnnotationParser<CommandSender> parser = new AnnotationParser<>(commandManager, CommandSender.class);
        parser.parse(new DecorateCommands(
                placementService,
                profileResolver,
                new TargetedItemLookup(registry, 5.0),
                messages,
                () -> reloaded.set(true),
                (template, amt) -> mock(ItemStack.class)
        ));
    }

    @Test
    void executesReloadModeSnapClearAndGiveCommands() {
        commandManager.commandExecutor().executeCommand(player, "dec reload").join();
        assertTrue(reloaded.get());

        commandManager.commandExecutor().executeCommand(player, "decorate snap 8").join();
        assertEquals(new PlacementMode(PlacementMode.Mode.SNAP, 8), placementService.getPlayerMode(player.getUniqueId()));

        commandManager.commandExecutor().executeCommand(player, "dec mode FREE").join();
        assertEquals(PlacementMode.Mode.FREE, placementService.getPlayerMode(player.getUniqueId()).mode());

        // Populate an item and clear via command
        registry.put(new PlacedItem(
                UUID.randomUUID(),
                "world",
                new BlockCoordinate("world", 5, 64, 5),
                new SurfacePose(new Vec3d(5.5, 65.02, 5.5), SurfaceFace.UP, 0f, PoseMode.FLAT, Vec3f.ONE, DisplayTransformType.FIXED, 0.35f, 0.35f),
                player.getUniqueId(),
                "Steve",
                false,
                new byte[] {1},
                "coffee_mug",
                "decor:coffee_mug",
                100L
        ));
        commandManager.commandExecutor().executeCommand(player, "decorate clear radius 5").join();
        assertEquals(0, registry.allLoadedItems().size());

        // Give custom item template (omitted optional args + explicit args)
        commandManager.commandExecutor().executeCommand(player, "decorate give coffee_mug").join();
        commandManager.commandExecutor().executeCommand(player, "decorate give coffee_mug Steve").join();
        commandManager.commandExecutor().executeCommand(player, "decorate give coffee_mug Steve 2").join();
        verify(player.getInventory(), org.mockito.Mockito.times(3)).addItem(any(ItemStack.class));
    }

    @Test
    void executesPersonalAndGlobalToggleCommands() {
        // Personal placement starts disabled by default; preserve player SNAP mode across toggles
        commandManager.commandExecutor().executeCommand(player, "decorate snap 8").join();
        assertFalse(placementService.isPlayerPlacementEnabled(player.getUniqueId()));

        commandManager.commandExecutor().executeCommand(player, "dec toggle").join();
        assertTrue(placementService.isPlayerPlacementEnabled(player.getUniqueId()));
        assertEquals(new PlacementMode(PlacementMode.Mode.SNAP, 8), placementService.getPlayerMode(player.getUniqueId()));

        commandManager.commandExecutor().executeCommand(player, "decorate toggle off").join();
        assertFalse(placementService.isPlayerPlacementEnabled(player.getUniqueId()));

        commandManager.commandExecutor().executeCommand(player, "dec toggle on").join();
        assertTrue(placementService.isPlayerPlacementEnabled(player.getUniqueId()));

        commandManager.commandExecutor().executeCommand(player, "dec toggle").join();
        assertFalse(placementService.isPlayerPlacementEnabled(player.getUniqueId()));

        // Console cannot execute personal toggle, but can execute global toggle
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission(any(String.class))).thenReturn(true);
        commandManager.commandExecutor().executeCommand(console, "decorate toggle").join();
        verify(console).sendMessage(any(net.kyori.adventure.text.Component.class));

        assertTrue(placementService.isGlobalEnabled());
        commandManager.commandExecutor().executeCommand(console, "decorate global off").join();
        assertFalse(placementService.isGlobalEnabled());

        commandManager.commandExecutor().executeCommand(player, "dec global toggle").join();
        assertTrue(placementService.isGlobalEnabled());

        commandManager.commandExecutor().executeCommand(player, "dec global").join();
        assertFalse(placementService.isGlobalEnabled());

        commandManager.commandExecutor().executeCommand(console, "dec global on").join();
        assertTrue(placementService.isGlobalEnabled());
    }
}
