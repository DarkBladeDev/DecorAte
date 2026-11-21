package dev.decorate.paper.item;

import dev.decorate.api.model.CustomModelDataSpec;
import dev.decorate.api.model.ItemTemplate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Constructs Paper 1.21.8 {@link ItemStack} instances from {@link ItemTemplate} definitions,
 * applying {@code DataComponentTypes.ITEM_MODEL} and composite {@code CUSTOM_MODEL_DATA}.
 */
public final class ItemTemplateFactory {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ItemTemplateFactory() {
    }

    /**
     * Builds an {@link ItemStack} matching the given template and quantity.
     *
     * @param template item template definition
     * @param amount stack size (clamped to material max stack size or at least 1)
     * @return synthesized Paper item stack
     */
    public static ItemStack createItem(ItemTemplate template, int amount) {
        Objects.requireNonNull(template, "template must not be null");
        int count = Math.max(1, amount);
        ItemStack stack = ItemStack.of(template.material(), count);

        if (template.displayName() != null && !template.displayName().isBlank()) {
            stack.setData(DataComponentTypes.CUSTOM_NAME, MINI_MESSAGE.deserialize(template.displayName()));
        }
        if (!template.lore().isEmpty()) {
            List<Component> lines = new ArrayList<>(template.lore().size());
            for (String line : template.lore()) {
                lines.add(MINI_MESSAGE.deserialize(line));
            }
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        }
        if (template.itemModelKey() != null && !template.itemModelKey().isBlank()) {
            stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(template.itemModelKey()));
        }
        CustomModelDataSpec cmd = template.customModelData();
        if (!cmd.isEmpty()) {
            CustomModelData.Builder builder = CustomModelData.customModelData()
                    .addFloats(cmd.floats())
                    .addFlags(cmd.flags())
                    .addStrings(cmd.strings());
            for (Integer rgb : cmd.colors()) {
                builder.addColor(Color.fromRGB(rgb));
            }
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder.build());
        }
        return stack;
    }
}
