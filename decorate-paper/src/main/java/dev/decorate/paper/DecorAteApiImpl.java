package dev.decorate.paper;

import dev.decorate.api.DecorAteApi;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.api.spi.PlaceholderService;
import dev.decorate.api.spi.PlacementProfileResolver;
import dev.decorate.api.spi.ProtectionHook;
import dev.decorate.api.spi.StorageProvider;
import dev.decorate.core.registry.SpatialRegistry;
import dev.decorate.core.service.PlacementService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Runtime {@link DecorAteApi} implementation exposed via {@link dev.decorate.api.DecorAteProvider}
 * and Bukkit's {@link org.bukkit.plugin.ServicesManager}.
 */
public final class DecorAteApiImpl implements DecorAteApi {

    private final PlacementProfileResolver profileResolver;
    private final StorageProvider storageProvider;
    private final DisplayRenderer displayRenderer;
    private final PlaceholderService placeholderService;
    private final PlacementService placementService;
    private final SpatialRegistry registry;

    public DecorAteApiImpl(
            PlacementProfileResolver profileResolver,
            StorageProvider storageProvider,
            DisplayRenderer displayRenderer,
            PlaceholderService placeholderService,
            PlacementService placementService,
            SpatialRegistry registry
    ) {
        this.profileResolver = Objects.requireNonNull(profileResolver);
        this.storageProvider = Objects.requireNonNull(storageProvider);
        this.displayRenderer = Objects.requireNonNull(displayRenderer);
        this.placeholderService = Objects.requireNonNull(placeholderService);
        this.placementService = Objects.requireNonNull(placementService);
        this.registry = Objects.requireNonNull(registry);
    }

    @Override
    public PlacementProfileResolver profileResolver() {
        return profileResolver;
    }

    @Override
    public StorageProvider storageProvider() {
        return storageProvider;
    }

    @Override
    public DisplayRenderer displayRenderer() {
        return displayRenderer;
    }

    @Override
    public PlaceholderService placeholderService() {
        return placeholderService;
    }

    @Override
    public void registerProtectionHook(ProtectionHook hook) {
        placementService.registerProtectionHook(hook);
    }

    @Override
    public Optional<PlacedItem> findPlacedItem(UUID id) {
        return registry.findById(id);
    }
}
