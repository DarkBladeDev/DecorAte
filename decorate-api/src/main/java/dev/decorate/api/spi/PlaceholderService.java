package dev.decorate.api.spi;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Decoupled placeholder parsing service resolving external placeholders when a provider such as PlaceholderAPI is active.
 */
public interface PlaceholderService {

    /**
     * Resolves placeholders within {@code text} for the given player, or returns {@code text} unchanged when unavailable.
     *
     * @param player target player context (may be null)
     * @param text raw text possibly containing placeholders
     * @return resolved text
     */
    String parse(@Nullable Player player, String text);

    /**
     * Indicates whether an external placeholder backend is currently active.
     *
     * @return true when placeholders are actively resolved
     */
    boolean isAvailable();
}
