package dev.decorate.api.spi;

import dev.decorate.api.model.ItemInspection;
import dev.decorate.api.model.PlacementProfile;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;

/**
 * Resolves {@link PlacementProfile} instances for items and manages registered {@link ProfileSource} providers.
 */
public interface PlacementProfileResolver {

    /**
     * Resolves the highest-priority profile matching the live {@link ItemStack}.
     *
     * @param item live Bukkit item stack
     * @return resolved profile or {@link PlacementProfile#DEFAULT}
     */
    PlacementProfile resolve(ItemStack item);

    /**
     * Resolves the highest-priority profile matching the extracted {@link ItemInspection}.
     *
     * @param inspection item snapshot
     * @return resolved profile or {@link PlacementProfile#DEFAULT}
     */
    PlacementProfile resolve(ItemInspection inspection);

    /**
     * Looks up a registered profile by its identifier.
     *
     * @param id profile identifier
     * @return matching profile if present
     */
    Optional<PlacementProfile> findById(String id);

    /**
     * Returns all currently loaded profiles.
     *
     * @return unmodifiable collection of profiles
     */
    Collection<PlacementProfile> allProfiles();

    /**
     * Returns all profiles defining an {@link dev.decorate.api.model.ItemTemplate} suitable for {@code /decorate give}.
     *
     * @return giveable profiles
     */
    Collection<PlacementProfile> giveableProfiles();

    /**
     * Registers an additional external {@link ProfileSource}.
     *
     * @param source profile source to register
     */
    void registerSource(ProfileSource source);

    /**
     * Reloads all registered sources and rebuilds the resolution index.
     */
    void reload();
}
