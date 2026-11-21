package dev.decorate.core.service;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/**
 * Abstraction over Bukkit plugin event dispatch so core services can be unit tested headlessly.
 */
@FunctionalInterface
public interface EventDispatcher {

    /** Dispatches via {@link org.bukkit.plugin.PluginManager#callEvent(Event)}. */
    EventDispatcher BUKKIT = event -> Bukkit.getPluginManager().callEvent(event);

    void callEvent(Event event);
}
