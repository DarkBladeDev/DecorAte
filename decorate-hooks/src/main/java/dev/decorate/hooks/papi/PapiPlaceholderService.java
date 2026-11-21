package dev.decorate.hooks.papi;

import dev.decorate.api.spi.PlaceholderService;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PlaceholderService} backed by PlaceholderAPI 2.12.3.
 */
public final class PapiPlaceholderService implements PlaceholderService {

    @Override
    public String parse(@Nullable Player player, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
