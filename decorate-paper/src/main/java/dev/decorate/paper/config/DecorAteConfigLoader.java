package dev.decorate.paper.config;

import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.PlacementMode;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.Vec3f;
import dev.decorate.core.config.DecorateSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Loads {@link DecorateSettings} and the fallback {@link PlacementProfile} from {@code config.yml}.
 */
public final class DecorAteConfigLoader {

    private DecorAteConfigLoader() {
    }

    public static DecorateSettings loadSettings(FileConfiguration config) {
        boolean globalEnabled = config.getBoolean("placement.enabled", true);
        boolean requireSneak = config.getBoolean("placement.require-sneak", true);
        double maxDistance = config.getDouble("placement.max-distance", 5.0);

        String modeRaw = config.getString("placement.default-mode.type", "FREE").toUpperCase(Locale.ROOT);
        PlacementMode.Mode modeType;
        try {
            modeType = PlacementMode.Mode.valueOf(modeRaw);
        } catch (IllegalArgumentException ex) {
            modeType = PlacementMode.Mode.FREE;
        }
        int gridDivisions = Math.clamp(config.getInt("placement.default-mode.grid-divisions", 16), 1, 64);

        int perBlock = config.getInt("limits.per-block", 32);
        int perChunk = config.getInt("limits.per-chunk", 256);
        int perPlayer = config.getInt("limits.per-player", 200);

        float rotationStep = (float) config.getDouble("interaction.rotation-step-degrees", 45.0);
        boolean ownerOnly = config.getBoolean("interaction.owner-only", true);
        boolean creativeDrop = config.getBoolean("interaction.creative-drop-on-pickup", false);

        boolean autoCleanup = config.getBoolean("support-block.auto-cleanup", true);
        boolean dropSurvival = config.getBoolean("support-block.drop-survival-items", true);
        int renderChunkRadius = Math.max(1, config.getInt("visibility.render-chunk-radius", 4));
        boolean hoverEnabled = config.getBoolean("visibility.hover-display-name.enabled", true);
        int hoverIntervalTicks = Math.max(1, config.getInt("visibility.hover-display-name.check-interval-ticks", 2));
        float hoverScale = Math.max(0.1f, (float) config.getDouble("visibility.hover-display-name.scale", 0.75));
        double hoverVerticalOffset = Math.max(0.0, config.getDouble("visibility.hover-display-name.vertical-offset", 0.28));

        return new DecorateSettings(
                maxDistance,
                perBlock,
                perChunk,
                perPlayer,
                rotationStep,
                autoCleanup,
                dropSurvival,
                creativeDrop,
                ownerOnly,
                renderChunkRadius,
                new PlacementMode(modeType, gridDivisions),
                requireSneak,
                globalEnabled,
                hoverEnabled,
                hoverIntervalTicks,
                hoverScale,
                hoverVerticalOffset
        );
    }

    public static PlacementProfile loadFallbackProfile(FileConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("defaults");
        if (sec == null) {
            return PlacementProfile.DEFAULT;
        }
        DisplayTransformType transform = DisplayTransformType.valueOf(
                sec.getString("display_transform", "FIXED").trim().toUpperCase(Locale.ROOT)
        );
        List<Double> scaleList = sec.getDoubleList("scale");
        Vec3f scale = scaleList.size() >= 3
                ? new Vec3f(scaleList.get(0).floatValue(), scaleList.get(1).floatValue(), scaleList.get(2).floatValue())
                : PlacementProfile.DEFAULT.scale();
        double normalOffset = sec.getDouble("normal_offset", PlacementProfile.DEFAULT.normalOffset());
        PoseMode defaultPose = PoseMode.valueOf(
                sec.getString("default_pose", "FLAT").trim().toUpperCase(Locale.ROOT)
        );
        float width = (float) sec.getDouble("hitbox.width", 0.35);
        float height = (float) sec.getDouble("hitbox.height", 0.35);

        return new PlacementProfile(
                "default",
                Integer.MIN_VALUE,
                true,
                EnumSet.allOf(SurfaceFace.class),
                transform,
                scale,
                normalOffset,
                0.0f,
                defaultPose,
                width,
                height,
                null,
                null
        );
    }
}
