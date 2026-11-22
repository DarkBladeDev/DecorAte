package dev.decorate.core.config;

import dev.decorate.api.model.PlacementMode;

/**
 * Immutable runtime configuration for placement limits, interaction rules, and visibility.
 *
 * @param maxPlaceDistance maximum raycast reach distance in blocks
 * @param maxItemsPerBlock maximum items per supporting block ({@code <= 0} for unlimited)
 * @param maxItemsPerChunk maximum items per chunk ({@code <= 0} for unlimited)
 * @param defaultPlayerLimit default per-player placement limit ({@code <= 0} for unlimited)
 * @param rotationStepDegrees degrees added on each right-click rotation
 * @param autoCleanupOnSupportBreak whether breaking/moving support blocks removes attached items
 * @param dropSurvivalOnAutoCleanup whether auto-cleanup drops Survival items into the world
 * @param creativeDropOnPickup whether Creative-placed items drop/return when picked up in Survival
 * @param ownerOnlyInteraction whether non-owners without bypass permission are blocked from editing
 * @param renderChunkRadius view distance in chunks for packet display tracking
 * @param defaultPlacementMode default player placement mode (FREE vs SNAP)
 * @param requireSneakForPlace whether placing requires sneaking (Shift + Right Click)
 * @param globalPlacementEnabled whether placement and item interaction are globally enabled on startup/reload
 * @param hoverDisplayNameEnabled whether custom-named placed items display a floating label when looked at
 * @param hoverCheckIntervalTicks interval in server ticks between targeted-item hover checks
 * @param hoverDisplayScale uniform scale applied to the virtual hover text display
 * @param hoverVerticalOffset minimum vertical offset applied when positioning the hover text display
 */
public record DecorateSettings(
        double maxPlaceDistance,
        int maxItemsPerBlock,
        int maxItemsPerChunk,
        int defaultPlayerLimit,
        float rotationStepDegrees,
        boolean autoCleanupOnSupportBreak,
        boolean dropSurvivalOnAutoCleanup,
        boolean creativeDropOnPickup,
        boolean ownerOnlyInteraction,
        int renderChunkRadius,
        PlacementMode defaultPlacementMode,
        boolean requireSneakForPlace,
        boolean globalPlacementEnabled,
        boolean hoverDisplayNameEnabled,
        int hoverCheckIntervalTicks,
        float hoverDisplayScale,
        double hoverVerticalOffset
) {

    public DecorateSettings(
            double maxPlaceDistance,
            int maxItemsPerBlock,
            int maxItemsPerChunk,
            int defaultPlayerLimit,
            float rotationStepDegrees,
            boolean autoCleanupOnSupportBreak,
            boolean dropSurvivalOnAutoCleanup,
            boolean creativeDropOnPickup,
            boolean ownerOnlyInteraction,
            int renderChunkRadius,
            PlacementMode defaultPlacementMode,
            boolean requireSneakForPlace,
            boolean globalPlacementEnabled
    ) {
        this(
                maxPlaceDistance,
                maxItemsPerBlock,
                maxItemsPerChunk,
                defaultPlayerLimit,
                rotationStepDegrees,
                autoCleanupOnSupportBreak,
                dropSurvivalOnAutoCleanup,
                creativeDropOnPickup,
                ownerOnlyInteraction,
                renderChunkRadius,
                defaultPlacementMode,
                requireSneakForPlace,
                globalPlacementEnabled,
                true,
                2,
                0.75f,
                0.28
        );
    }

    /** Sensible production defaults. */
    public static final DecorateSettings DEFAULTS = new DecorateSettings(
            5.0,
            32,
            256,
            200,
            45.0f,
            true,
            true,
            false,
            true,
            4,
            PlacementMode.DEFAULT_FREE,
            true,
            true,
            true,
            2,
            0.75f,
            0.28
    );
}
