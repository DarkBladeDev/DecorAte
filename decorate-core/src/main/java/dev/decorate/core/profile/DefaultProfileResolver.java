package dev.decorate.core.profile;

import dev.decorate.api.model.ItemInspection;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.spi.PlacementProfileResolver;
import dev.decorate.api.spi.ProfileSource;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe {@link PlacementProfileResolver} aggregating {@link ProfileSource} entries sorted by priority.
 */
public final class DefaultProfileResolver implements PlacementProfileResolver {

    private final List<ProfileSource> sources = new CopyOnWriteArrayList<>();
    private volatile PlacementProfile fallbackProfile = PlacementProfile.DEFAULT;
    private volatile List<ProfileSource.ProfileEntry> sortedEntries = List.of();
    private volatile Map<String, PlacementProfile> byId = Map.of();

    /**
     * Updates the global fallback profile returned when no registered matcher matches.
     *
     * @param fallbackProfile global default profile
     */
    public void setFallbackProfile(PlacementProfile fallbackProfile) {
        this.fallbackProfile = Objects.requireNonNull(fallbackProfile, "fallbackProfile must not be null");
    }

    @Override
    public PlacementProfile resolve(ItemStack item) {
        Objects.requireNonNull(item, "item must not be null");
        return resolveInternal(PaperItemInspector.inspect(item), item);
    }

    @Override
    public PlacementProfile resolve(ItemInspection inspection) {
        Objects.requireNonNull(inspection, "inspection must not be null");
        return resolveInternal(inspection, null);
    }

    @Override
    public Optional<PlacementProfile> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Collection<PlacementProfile> allProfiles() {
        return byId.values();
    }

    @Override
    public Collection<PlacementProfile> giveableProfiles() {
        return byId.values().stream()
                .filter(profile -> profile.optionalItemTemplate().isPresent())
                .toList();
    }

    @Override
    public void registerSource(ProfileSource source) {
        Objects.requireNonNull(source, "source must not be null");
        sources.add(source);
        reload();
    }

    @Override
    public synchronized void reload() {
        List<ProfileSource.ProfileEntry> loaded = new java.util.ArrayList<>();
        Map<String, PlacementProfile> idMap = new LinkedHashMap<>();
        for (ProfileSource source : sources) {
            for (ProfileSource.ProfileEntry entry : source.loadProfiles()) {
                loaded.add(entry);
                idMap.put(entry.profile().id(), entry.profile());
            }
        }
        loaded.sort(Comparator.comparingInt((ProfileSource.ProfileEntry e) -> e.matcher().priority()).reversed());
        this.sortedEntries = List.copyOf(loaded);
        this.byId = Map.copyOf(idMap);
    }

    private PlacementProfile resolveInternal(ItemInspection inspection, @Nullable ItemStack liveItem) {
        for (ProfileSource.ProfileEntry entry : sortedEntries) {
            if (entry.matcher().matches(inspection, liveItem)) {
                return entry.profile();
            }
        }
        return fallbackProfile;
    }
}
