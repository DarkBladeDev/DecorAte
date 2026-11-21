package dev.decorate.core.profile;

import dev.decorate.api.model.CustomModelDataSpec;
import dev.decorate.api.model.ItemInspection;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Extracts {@link ItemInspection} snapshots from live Paper 1.21.8 {@link ItemStack} instances,
 * supporting both {@link DataComponentTypes#ITEM_MODEL} and composite {@link DataComponentTypes#CUSTOM_MODEL_DATA}.
 */
public final class PaperItemInspector {

    private PaperItemInspector() {
    }

    /**
     * Inspects a live Bukkit/Paper {@link ItemStack}.
     *
     * @param stack item stack to inspect
     * @return immutable inspection snapshot
     */
    public static ItemInspection inspect(ItemStack stack) {
        String itemModelKey = null;
        CustomModelDataSpec cmdSpec = CustomModelDataSpec.EMPTY;

        if (org.bukkit.Bukkit.getServer() != null) {
            try {
                if (stack.hasData(DataComponentTypes.ITEM_MODEL)) {
                    Key key = stack.getData(DataComponentTypes.ITEM_MODEL);
                    if (key != null) {
                        itemModelKey = key.asString();
                    }
                }
                if (stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
                    CustomModelData cmd = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
                    if (cmd != null) {
                        List<Integer> colors = cmd.colors().stream().map(Color::asRGB).toList();
                        cmdSpec = new CustomModelDataSpec(cmd.floats(), cmd.flags(), cmd.strings(), colors);
                    }
                }
            } catch (UnsupportedOperationException | NoClassDefFoundError | NullPointerException ignored) {
                // Fallback for headless test environments without a running Paper server registry
            }
        }

        if (cmdSpec.isEmpty() && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) {
                cmdSpec = new CustomModelDataSpec(
                        List.of((float) meta.getCustomModelData()),
                        List.of(),
                        List.of(),
                        List.of()
                );
            }
        }

        return new ItemInspection(stack.getType(), itemModelKey, cmdSpec);
    }
}
