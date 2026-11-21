package dev.decorate.api.model;

import java.util.List;

/**
 * Composite 1.21.4+ CustomModelData payload supporting floats, flags, strings, and RGB colors.
 *
 * @param floats float values (index 0 corresponds to legacy integer custom model data)
 * @param flags boolean flag values
 * @param strings string values
 * @param colors RGB integer color values
 */
public record CustomModelDataSpec(
        List<Float> floats,
        List<Boolean> flags,
        List<String> strings,
        List<Integer> colors
) {

    /** Empty specification with no values set. */
    public static final CustomModelDataSpec EMPTY = new CustomModelDataSpec(List.of(), List.of(), List.of(), List.of());

    public CustomModelDataSpec {
        floats = floats == null ? List.of() : List.copyOf(floats);
        flags = flags == null ? List.of() : List.copyOf(flags);
        strings = strings == null ? List.of() : List.copyOf(strings);
        colors = colors == null ? List.of() : List.copyOf(colors);
    }

    /**
     * Returns true when all component lists are empty.
     *
     * @return whether no custom model data fields are defined
     */
    public boolean isEmpty() {
        return floats.isEmpty() && flags.isEmpty() && strings.isEmpty() && colors.isEmpty();
    }
}
