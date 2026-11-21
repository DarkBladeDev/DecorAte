package dev.decorate.hooks.papi;

import dev.decorate.api.spi.PlaceholderService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Fallback {@link PlaceholderService} used when PlaceholderAPI is not installed.
 */
public final class NoOpPlaceholderService implements PlaceholderService {

    @Override
    public String parse(@Nullable Player player, String text) {
        return text;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
