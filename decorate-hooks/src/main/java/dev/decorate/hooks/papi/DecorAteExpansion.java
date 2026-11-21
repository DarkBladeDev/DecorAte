package dev.decorate.hooks.papi;

import dev.decorate.api.model.ChunkCoordinate;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.core.registry.SpatialRegistry;
import dev.decorate.core.service.PlacementService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * PlaceholderAPI 2.12.3 expansion exposing DecorAte metrics and targeted-item context
 * under the {@code %decorate_<placeholder>%} namespace.
 */
public final class DecorAteExpansion extends PlaceholderExpansion {

    private final String version;
    private final SpatialRegistry registry;
    private final PlacementService placementService;
    private final Function<Player, Optional<PlacedItem>> targetedItemLookup;

    public DecorAteExpansion(
            String version,
            SpatialRegistry registry,
            PlacementService placementService,
            Function<Player, Optional<PlacedItem>> targetedItemLookup
    ) {
        this.version = Objects.requireNonNull(version, "version must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.placementService = Objects.requireNonNull(placementService, "placementService must not be null");
        this.targetedItemLookup = Objects.requireNonNull(targetedItemLookup, "targetedItemLookup must not be null");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "decorate";
    }

    @Override
    public @NotNull String getAuthor() {
        return "DecorAte";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "count" -> String.valueOf(placementService.getOwnerCount(player.getUniqueId()));
            case "world_count" -> String.valueOf(registry.countLoadedByOwnerInWorld(
                    player.getUniqueId(),
                    player.getWorld().getName()
            ));
            case "limit" -> {
                int limit = placementService.resolvePlayerLimit(player);
                yield limit <= 0 ? "unlimited" : String.valueOf(limit);
            }
            case "remaining" -> {
                int limit = placementService.resolvePlayerLimit(player);
                if (limit <= 0) {
                    yield "unlimited";
                }
                int used = placementService.getOwnerCount(player.getUniqueId());
                yield String.valueOf(Math.max(0, limit - used));
            }
            case "chunk_count" -> {
                Location loc = player.getLocation();
                ChunkCoordinate chunk = ChunkCoordinate.fromPosition(player.getWorld().getName(), loc.getX(), loc.getZ());
                yield String.valueOf(registry.countByChunk(chunk));
            }
            case "looking_model" -> targetedItemLookup.apply(player)
                    .map(PlacedItem::itemDisplayKey)
                    .orElse("");
            case "looking_owner" -> targetedItemLookup.apply(player)
                    .map(PlacedItem::ownerName)
                    .orElse("");
            default -> null;
        };
    }
}
