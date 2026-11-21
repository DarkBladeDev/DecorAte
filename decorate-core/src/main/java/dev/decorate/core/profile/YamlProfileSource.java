package dev.decorate.core.profile;

import dev.decorate.api.model.CustomModelDataSpec;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.ItemTemplate;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.PoseMode;
import dev.decorate.api.model.SurfaceFace;
import dev.decorate.api.model.Vec3f;
import dev.decorate.api.spi.ProfileSource;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Loads Level 2 extensible {@link PlacementProfile} entries from YAML files inside a directory.
 */
public final class YamlProfileSource implements ProfileSource {

    private final Path profilesDirectory;
    private final Logger logger;

    public YamlProfileSource(Path profilesDirectory, Logger logger) {
        this.profilesDirectory = Objects.requireNonNull(profilesDirectory, "profilesDirectory must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    @Override
    public String sourceId() {
        return "yaml:" + profilesDirectory.getFileName();
    }

    @Override
    public List<ProfileEntry> loadProfiles() {
        if (!Files.isDirectory(profilesDirectory)) {
            return List.of();
        }
        List<ProfileEntry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.list(profilesDirectory)) {
            List<Path> yamlFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();

            for (Path file : yamlFiles) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                ConfigurationSection profilesSection = yaml.getConfigurationSection("profiles");
                if (profilesSection == null) {
                    profilesSection = yaml;
                }
                for (String key : profilesSection.getKeys(false)) {
                    ConfigurationSection section = profilesSection.getConfigurationSection(key);
                    if (section == null) {
                        continue;
                    }
                    try {
                        entries.add(parseProfile(key, section));
                    } catch (IllegalArgumentException ex) {
                        logger.warning("Skipping invalid profile '" + key + "' in " + file.getFileName() + ": " + ex.getMessage());
                    }
                }
            }
        } catch (IOException ex) {
            logger.warning("Failed to scan profiles directory " + profilesDirectory + ": " + ex.getMessage());
        }
        return List.copyOf(entries);
    }

    private static ProfileEntry parseProfile(String id, ConfigurationSection section) {
        ConfigurationSection matchSec = section.getConfigurationSection("match");
        Material matchMaterial = null;
        String matchItemModel = null;
        CustomModelDataSpec matchCmd = CustomModelDataSpec.EMPTY;

        if (matchSec != null) {
            String matName = matchSec.getString("material");
            if (matName != null && !matName.isBlank()) {
                matchMaterial = parseMaterial(matName);
            }
            matchItemModel = matchSec.getString("item_model");
            matchCmd = parseCustomModelData(matchSec.get("custom_model_data"));
        }

        int priority = section.contains("priority")
                ? section.getInt("priority")
                : CompositeProfileMatcher.computeDefaultPriority(matchMaterial, matchItemModel, matchCmd);

        boolean placeable = section.getBoolean("placeable", true);
        Set<SurfaceFace> allowedFaces = parseFaces(section.getStringList("allowed_faces"));
        DisplayTransformType transform = parseTransform(section.getString("display_transform", "FIXED"));
        Vec3f scale = parseScale(section.getDoubleList("scale"));
        double normalOffset = section.getDouble("normal_offset", PlacementProfile.DEFAULT.normalOffset());
        float rotationOffset = (float) section.getDouble("rotation_offset", 0.0);
        PoseMode defaultPose = parsePoseMode(section.getString("default_pose", "FLAT"));

        ConfigurationSection hitboxSec = section.getConfigurationSection("hitbox");
        float hitboxWidth = hitboxSec != null ? (float) hitboxSec.getDouble("width", 0.35) : 0.35f;
        float hitboxHeight = hitboxSec != null ? (float) hitboxSec.getDouble("height", 0.35) : 0.35f;
        Integer snapOverride = section.contains("snap_override") ? section.getInt("snap_override") : null;

        ItemTemplate itemTemplate = null;
        ConfigurationSection itemSec = section.getConfigurationSection("item");
        if (itemSec != null) {
            itemTemplate = parseItemTemplate(itemSec, matchMaterial, matchItemModel, matchCmd);
        }

        PlacementProfile profile = new PlacementProfile(
                id,
                priority,
                placeable,
                allowedFaces,
                transform,
                scale,
                normalOffset,
                rotationOffset,
                defaultPose,
                hitboxWidth,
                hitboxHeight,
                snapOverride,
                itemTemplate
        );

        CompositeProfileMatcher matcher = new CompositeProfileMatcher(
                priority,
                matchMaterial,
                matchItemModel,
                matchCmd
        );
        return new ProfileEntry(matcher, profile);
    }

    private static ItemTemplate parseItemTemplate(
            ConfigurationSection itemSec,
            Material fallbackMaterial,
            String fallbackItemModel,
            CustomModelDataSpec fallbackCmd
    ) {
        String matName = itemSec.getString("material");
        Material material = (matName != null && !matName.isBlank()) ? parseMaterial(matName) : fallbackMaterial;
        if (material == null) {
            throw new IllegalArgumentException("item template requires a valid material");
        }
        String displayName = itemSec.getString("display_name");
        List<String> lore = itemSec.getStringList("lore");
        String itemModel = itemSec.getString("item_model", fallbackItemModel);
        CustomModelDataSpec cmd = itemSec.contains("custom_model_data")
                ? parseCustomModelData(itemSec.get("custom_model_data"))
                : fallbackCmd;
        return new ItemTemplate(material, displayName, lore, itemModel, cmd);
    }

    private static CustomModelDataSpec parseCustomModelData(Object raw) {
        if (raw == null) {
            return CustomModelDataSpec.EMPTY;
        }
        if (raw instanceof Number number) {
            return new CustomModelDataSpec(List.of(number.floatValue()), List.of(), List.of(), List.of());
        }
        if (raw instanceof String str) {
            return new CustomModelDataSpec(List.of(), List.of(), List.of(str), List.of());
        }
        if (raw instanceof ConfigurationSection cmdSec) {
            List<Float> floats = new ArrayList<>();
            for (Double d : cmdSec.getDoubleList("floats")) {
                floats.add(d.floatValue());
            }
            if (cmdSec.contains("legacy_int")) {
                floats.add((float) cmdSec.getInt("legacy_int"));
            }
            List<Boolean> flags = cmdSec.getBooleanList("flags");
            List<String> strings = cmdSec.getStringList("strings");
            List<Integer> colors = cmdSec.getIntegerList("colors");
            return new CustomModelDataSpec(floats, flags, strings, colors);
        }
        return CustomModelDataSpec.EMPTY;
    }

    private static Set<SurfaceFace> parseFaces(List<String> rawFaces) {
        if (rawFaces == null || rawFaces.isEmpty()) {
            return EnumSet.allOf(SurfaceFace.class);
        }
        EnumSet<SurfaceFace> result = EnumSet.noneOf(SurfaceFace.class);
        for (String token : rawFaces) {
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            if (normalized.equals("WALLS")) {
                result.add(SurfaceFace.NORTH);
                result.add(SurfaceFace.SOUTH);
                result.add(SurfaceFace.EAST);
                result.add(SurfaceFace.WEST);
            } else if (normalized.equals("ALL")) {
                result.addAll(EnumSet.allOf(SurfaceFace.class));
            } else {
                result.add(SurfaceFace.valueOf(normalized));
            }
        }
        return result;
    }

    private static Material parseMaterial(String name) {
        Material mat = Material.matchMaterial(name.trim());
        if (mat == null) {
            throw new IllegalArgumentException("Unknown material: " + name);
        }
        return mat;
    }

    private static DisplayTransformType parseTransform(String value) {
        return DisplayTransformType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static PoseMode parsePoseMode(String value) {
        return PoseMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static Vec3f parseScale(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return PlacementProfile.DEFAULT.scale();
        }
        if (values.size() == 1) {
            float s = values.getFirst().floatValue();
            return new Vec3f(s, s, s);
        }
        if (values.size() >= 3) {
            return new Vec3f(values.get(0).floatValue(), values.get(1).floatValue(), values.get(2).floatValue());
        }
        throw new IllegalArgumentException("scale must contain 1 or 3 numbers");
    }
}
