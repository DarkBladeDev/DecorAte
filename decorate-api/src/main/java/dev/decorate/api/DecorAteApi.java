package dev.decorate.api;

import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.spi.DisplayRenderer;
import dev.decorate.api.spi.PlaceholderService;
import dev.decorate.api.spi.PlacementProfileResolver;
import dev.decorate.api.spi.ProtectionHook;
import dev.decorate.api.spi.StorageProvider;

import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point exposing DecorAte services and extension registries for external plugins.
 */
public interface DecorAteApi {

    /**
     * Returns the active profile resolver.
     *
     * @return profile resolver
     */
    PlacementProfileResolver profileResolver();

    /**
     * Returns the active storage provider.
     *
     * @return storage provider
     */
    StorageProvider storageProvider();

    /**
     * Returns the active packet display renderer.
     *
     * @return display renderer
     */
    DisplayRenderer displayRenderer();

    /**
     * Returns the active placeholder service.
     *
     * @return placeholder service
     */
    PlaceholderService placeholderService();

    /**
     * Registers an external {@link ProtectionHook}.
     *
     * @param hook protection hook instance
     */
    void registerProtectionHook(ProtectionHook hook);

    /**
     * Looks up a currently loaded placed item by its unique ID.
     *
     * @param id placed item UUID
     * @return placed item if loaded
     */
    Optional<PlacedItem> findPlacedItem(UUID id);
}
