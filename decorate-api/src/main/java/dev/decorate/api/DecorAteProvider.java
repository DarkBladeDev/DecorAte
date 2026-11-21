package dev.decorate.api;

import java.util.Objects;

/**
 * Static holder providing access to the active {@link DecorAteApi} instance at runtime.
 */
public final class DecorAteProvider {

    private static volatile DecorAteApi instance;

    private DecorAteProvider() {
    }

    /**
     * Returns the registered {@link DecorAteApi} instance.
     *
     * @return active API instance
     * @throws IllegalStateException if DecorAte has not finished initializing
     */
    public static DecorAteApi get() {
        DecorAteApi api = instance;
        if (api == null) {
            throw new IllegalStateException("DecorAte API is not initialized yet");
        }
        return api;
    }

    /**
     * Registers the active {@link DecorAteApi} instance. Intended for internal plugin bootstrap only.
     *
     * @param api API implementation
     */
    public static void register(DecorAteApi api) {
        instance = Objects.requireNonNull(api, "api must not be null");
    }

    /**
     * Clears the registered API instance on plugin shutdown.
     */
    public static void unregister() {
        instance = null;
    }
}
