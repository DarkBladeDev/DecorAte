package dev.decorate.api.model;

import org.bukkit.Material;

import java.util.Objects;

/**
 * Pure value snapshot of an item's identity and 1.21.8 custom model components used for profile matching.
 *
 * @param material base material
 * @param itemModelKey optional {@code minecraft:item_model} namespaced key string
 * @param customModelData optional composite custom model data
 */
public record ItemInspection(
        Material material,
        String itemModelKey,
        CustomModelDataSpec customModelData
) {

    public ItemInspection {
        Objects.requireNonNull(material, "material must not be null");
        customModelData = customModelData == null ? CustomModelDataSpec.EMPTY : customModelData;
    }

    /**
     * Returns the most descriptive label for placeholders and inspection output.
     *
     * @return item model key, formatted custom model data, or material key
     */
    public String descriptiveLabel() {
        if (itemModelKey != null && !itemModelKey.isBlank()) {
            return itemModelKey;
        }
        if (!customModelData.floats().isEmpty()) {
            return material.getKey() + "#cmd=" + customModelData.floats().getFirst();
        }
        if (!customModelData.strings().isEmpty()) {
            return material.getKey() + "#cmd=" + customModelData.strings().getFirst();
        }
        return material.getKey().toString();
    }
}
