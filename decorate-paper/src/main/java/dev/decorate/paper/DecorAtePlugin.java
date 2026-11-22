package dev.decorate.paper;

import com.github.retrooper.packetevents.PacketEvents;
import dev.decorate.api.DecorAteApi;
import dev.decorate.api.DecorAteProvider;
import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.spi.PlaceholderService;
import dev.decorate.core.config.DecorateSettings;
import dev.decorate.core.profile.DefaultProfileResolver;
import dev.decorate.core.profile.YamlProfileSource;
import dev.decorate.core.registry.SpatialRegistry;
import dev.decorate.core.service.PlacementService;
import dev.decorate.core.storage.ChunkVisibilityManager;
import dev.decorate.core.storage.SqliteStorageProvider;
import dev.decorate.hooks.papi.DecorAteExpansion;
import dev.decorate.hooks.papi.NoOpPlaceholderService;
import dev.decorate.hooks.papi.PapiPlaceholderService;
import dev.decorate.hooks.worldguard.WorldGuardProtectionHook;
import dev.decorate.packet.PacketEventsDisplayRenderer;
import dev.decorate.packet.PacketInteractionListener;
import dev.decorate.paper.bootstrap.RuntimeLibraryLoader;
import dev.decorate.paper.command.DecorateCommands;
import dev.decorate.paper.config.DecorAteConfigLoader;
import dev.decorate.paper.config.MessageService;
import dev.decorate.paper.listener.DecorInteractionHandler;
import dev.decorate.paper.listener.PlacementTriggerListener;
import dev.decorate.paper.listener.SupportBlockListener;
import dev.decorate.paper.listener.VisibilityLifecycleListener;
import dev.decorate.paper.lookup.TargetedItemLookup;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Paper 1.21.8 plugin entry point for DecorAte.
 */
public final class DecorAtePlugin extends JavaPlugin {

    private SpatialRegistry registry;
    private SqliteStorageProvider storage;
    private DefaultProfileResolver profileResolver;
    private PacketEventsDisplayRenderer renderer;
    private ChunkVisibilityManager visibilityManager;
    private PlacementService placementService;
    private MessageService messageService;
    private PacketInteractionListener packetInteractionListener;
    private DecorAteExpansion papiExpansion;
    private boolean worldGuardAvailable;

    @Override
    public void onLoad() {
        if (isClassPresent("com.sk89q.worldguard.WorldGuard")) {
            WorldGuardProtectionHook.registerFlags(getLogger());
            worldGuardAvailable = true;
        }
    }

    @Override
    public void onEnable() {
        try {
            RuntimeLibraryLoader.loadStorageLibraries(this);
        } catch (Exception ex) {
            Path libsDir = getDataFolder().toPath().resolve("libs").toAbsolutePath();
            getLogger().log(
                    Level.SEVERE,
                    "Failed to resolve runtime storage dependencies. Check network connectivity or place the required JARs in "
                            + libsDir + ": " + ex.getMessage(),
                    ex
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        extractExampleProfileIfMissing();

        DecorateSettings settings = DecorAteConfigLoader.loadSettings(getConfig());
        YamlConfiguration messagesYaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        PlaceholderService placeholderService = createPlaceholderService();
        this.messageService = new MessageService(messagesYaml, placeholderService);

        this.registry = new SpatialRegistry();
        this.storage = new SqliteStorageProvider(getDataFolder().toPath().resolve("decorate.db"));
        this.storage.init().join();

        this.profileResolver = new DefaultProfileResolver();
        this.profileResolver.setFallbackProfile(DecorAteConfigLoader.loadFallbackProfile(getConfig()));
        this.profileResolver.registerSource(new YamlProfileSource(getDataFolder().toPath().resolve("profiles"), getLogger()));

        this.renderer = new PacketEventsDisplayRenderer((player, item) ->
                player.getWorld().getName().equals(item.world())
                        && visibilityManager != null
                        && visibilityManager.isChunkVisibleToPlayer(player.getUniqueId(), item.chunk()));

        this.visibilityManager = new ChunkVisibilityManager(registry, storage, renderer, settings.renderChunkRadius());
        this.placementService = new PlacementService(registry, storage, renderer, profileResolver, settings);

        if (worldGuardAvailable && getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            placementService.registerProtectionHook(new WorldGuardProtectionHook());
            getLogger().info("WorldGuard protection hook enabled.");
        }

        TargetedItemLookup targetLookup = new TargetedItemLookup(registry, settings.maxPlaceDistance());

        if (placeholderService.isAvailable()) {
            this.papiExpansion = new DecorAteExpansion(
                    getPluginMeta().getVersion(),
                    registry,
                    placementService,
                    targetLookup::findTargetedItem
            );
            this.papiExpansion.register();
            getLogger().info("PlaceholderAPI expansion registered (%decorate_*%).");
        }

        Consumer<Runnable> mainThread = task -> Bukkit.getScheduler().runTask(this, task);
        this.packetInteractionListener = new PacketInteractionListener(
                renderer,
                mainThread,
                new DecorInteractionHandler(placementService, messageService)
        );
        PacketEvents.getAPI().getEventManager().registerListener(packetInteractionListener);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlacementTriggerListener(placementService, messageService), this);
        pm.registerEvents(new SupportBlockListener(placementService), this);
        pm.registerEvents(new VisibilityLifecycleListener(visibilityManager, storage, placementService, mainThread), this);

        registerCommands(targetLookup);

        DecorAteApi api = new DecorAteApiImpl(
                profileResolver,
                storage,
                renderer,
                placeholderService,
                placementService,
                registry
        );
        DecorAteProvider.register(api);
        getServer().getServicesManager().register(DecorAteApi.class, api, this, ServicePriority.Normal);

        preloadActiveChunksAndPlayers(mainThread);
        getLogger().info("DecorAte enabled with " + profileResolver.allProfiles().size() + " custom model profiles.");
    }

    @Override
    public void onDisable() {
        DecorAteProvider.unregister();
        getServer().getServicesManager().unregisterAll(this);

        if (papiExpansion != null) {
            papiExpansion.unregister();
        }
        if (packetInteractionListener != null && PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetInteractionListener);
        }
        if (renderer != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                renderer.destroyAllForPlayer(player);
            }
        }
        if (storage != null) {
            try {
                storage.close().get(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                getLogger().warning("Timed out waiting for SQLite storage shutdown: " + ex.getMessage());
            }
        }
    }

    private void reloadPlugin() {
        reloadConfig();
        DecorateSettings settings = DecorAteConfigLoader.loadSettings(getConfig());
        placementService.updateSettings(settings);
        visibilityManager.setChunkRadius(settings.renderChunkRadius());
        profileResolver.setFallbackProfile(DecorAteConfigLoader.loadFallbackProfile(getConfig()));
        profileResolver.reload();

        YamlConfiguration messagesYaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        messageService.reload(messagesYaml);
    }

    private void registerCommands(TargetedItemLookup targetLookup) {
        LegacyPaperCommandManager<CommandSender> commandManager = LegacyPaperCommandManager.createNative(
                this,
                ExecutionCoordinator.simpleCoordinator()
        );
        try {
            commandManager.registerBrigadier();
        } catch (Exception ex) {
            getLogger().fine("Brigadier registration skipped: " + ex.getMessage());
        }
        AnnotationParser<CommandSender> parser = new AnnotationParser<>(commandManager, CommandSender.class);
        parser.parse(new DecorateCommands(
                placementService,
                profileResolver,
                targetLookup,
                messageService,
                this::reloadPlugin
        ));
    }

    private void preloadActiveChunksAndPlayers(Consumer<Runnable> mainThread) {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                visibilityManager.ensureChunkLoaded(
                        new ChunkCoordinate(world.getName(), chunk.getX(), chunk.getZ()),
                        mainThread
                );
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            storage.countByOwner(player.getUniqueId()).whenComplete((count, err) -> {
                if (err == null && count != null) {
                    mainThread.accept(() -> placementService.setCachedOwnerCount(player.getUniqueId(), count));
                }
            });
            var loc = player.getLocation();
            visibilityManager.updatePlayerVisibility(
                    player,
                    ChunkCoordinate.fromPosition(player.getWorld().getName(), loc.getX(), loc.getZ()),
                    mainThread
            );
        }
    }

    private PlaceholderService createPlaceholderService() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")
                && isClassPresent("me.clip.placeholderapi.PlaceholderAPI")) {
            return new PapiPlaceholderService();
        }
        return new NoOpPlaceholderService();
    }

    private void saveResourceIfMissing(String resourcePath) {
        if (!new File(getDataFolder(), resourcePath).exists()) {
            saveResource(resourcePath, false);
        }
    }

    private void extractExampleProfileIfMissing() {
        Path profilesDir = getDataFolder().toPath().resolve("profiles");
        if (Files.notExists(profilesDir)) {
            saveResource("profiles/example.yml", false);
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, DecorAtePlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
