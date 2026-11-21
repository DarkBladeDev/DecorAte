package dev.decorate.api.spi;

import dev.decorate.api.model.ItemInspection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Predicate matching an item's identity and custom model components against a placement profile rule.
 */
public interface ProfileMatcher {

    /**
     * Priority used to sort competing matchers; higher values are evaluated first.
     *
     * @return matcher priority
     */
    int priority();

    /**
     * Evaluates whether the provided item inspection (and optional live {@link ItemStack}) matches.
     *
     * @param inspection extracted item components snapshot
     * @param item optional live Bukkit ItemStack (may be null in unit tests)
     * @return true if this matcher accepts the item
     */
    boolean matches(ItemInspection inspection, @Nullable ItemStack item);
}
