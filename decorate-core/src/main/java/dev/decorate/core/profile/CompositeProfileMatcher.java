package dev.decorate.core.profile;

import dev.decorate.api.model.CustomModelDataSpec;
import dev.decorate.api.model.ItemInspection;
import dev.decorate.api.spi.ProfileMatcher;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Hierarchical matcher supporting exact/wildcard {@code item_model}, Paper 1.21.8 composite
 * {@code custom_model_data}, and base {@link Material}.
 */
public final class CompositeProfileMatcher implements ProfileMatcher {

    private final int priority;
    private final @Nullable Material material;
    private final @Nullable String itemModelPattern;
    private final @Nullable CustomModelDataSpec requiredCustomModelData;

    public CompositeProfileMatcher(
            int priority,
            @Nullable Material material,
            @Nullable String itemModelPattern,
            @Nullable CustomModelDataSpec requiredCustomModelData
    ) {
        this.priority = priority;
        this.material = material;
        this.itemModelPattern = itemModelPattern == null ? null : itemModelPattern.trim().toLowerCase(Locale.ROOT);
        this.requiredCustomModelData = requiredCustomModelData == null || requiredCustomModelData.isEmpty()
                ? null
                : requiredCustomModelData;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean matches(ItemInspection inspection, @Nullable ItemStack item) {
        Objects.requireNonNull(inspection, "inspection must not be null");
        if (material != null && inspection.material() != material) {
            return false;
        }
        if (itemModelPattern != null && !matchesItemModel(inspection.itemModelKey(), itemModelPattern)) {
            return false;
        }
        if (requiredCustomModelData != null && !matchesCustomModelData(inspection.customModelData(), requiredCustomModelData)) {
            return false;
        }
        return true;
    }

    /**
     * Computes default specificity priority when a profile does not override {@code priority} explicitly.
     * Exact {@code item_model} > wildcard {@code item_model} > {@code custom_model_data} > {@code material} > fallback.
     *
     * @param material optional material constraint
     * @param itemModel optional item_model constraint
     * @param cmd optional custom_model_data constraint
     * @return derived priority
     */
    public static int computeDefaultPriority(
            @Nullable Material material,
            @Nullable String itemModel,
            @Nullable CustomModelDataSpec cmd
    ) {
        int score = 0;
        if (itemModel != null && !itemModel.isBlank()) {
            score += itemModel.contains("*") ? 300 : 500;
        }
        if (cmd != null && !cmd.isEmpty()) {
            score += 200;
        }
        if (material != null) {
            score += 50;
        }
        return score;
    }

    private static boolean matchesItemModel(@Nullable String actual, String pattern) {
        if (actual == null || actual.isBlank()) {
            return false;
        }
        String normalizedActual = actual.toLowerCase(Locale.ROOT);
        if (!pattern.contains("*")) {
            return normalizedActual.equals(pattern);
        }
        if (pattern.equals("*")) {
            return true;
        }
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return normalizedActual.startsWith(prefix);
        }
        if (pattern.startsWith("*")) {
            String suffix = pattern.substring(1);
            return normalizedActual.endsWith(suffix);
        }
        String[] parts = pattern.split("\\*", 2);
        return normalizedActual.startsWith(parts[0]) && normalizedActual.endsWith(parts[1]);
    }

    private static boolean matchesCustomModelData(CustomModelDataSpec actual, CustomModelDataSpec required) {
        if (!containsAllFloats(actual.floats(), required.floats())) {
            return false;
        }
        if (!actual.flags().containsAll(required.flags())) {
            return false;
        }
        if (!actual.strings().containsAll(required.strings())) {
            return false;
        }
        return actual.colors().containsAll(required.colors());
    }

    private static boolean containsAllFloats(List<Float> actual, List<Float> required) {
        for (Float expected : required) {
            boolean found = false;
            for (Float candidate : actual) {
                if (Math.abs(candidate - expected) < 1e-4f) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
