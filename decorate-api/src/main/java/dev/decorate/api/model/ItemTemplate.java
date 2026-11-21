package dev.decorate.api.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

/**
 * Template describing how to construct a custom decorative {@code ItemStack} for commands such as {@code /decorate give}.
 *
 * @param material base Bukkit material
 * @param displayName optional MiniMessage display name
 * @param lore optional MiniMessage lore lines
 * @param itemModelKey optional namespaced {@code minecraft:item_model} key
 * @param customModelData optional 1.21.8 composite custom model data
 */
public record ItemTemplate(
        Material material,
        String displayName,
        List<String> lore,
        String itemModelKey,
        CustomModelDataSpec customModelData
) {

    public ItemTemplate {
        Objects.requireNonNull(material, "material must not be null");
        lore = lore == null ? List.of() : List.copyOf(lore);
        customModelData = customModelData == null ? CustomModelDataSpec.EMPTY : customModelData;
    }
}
