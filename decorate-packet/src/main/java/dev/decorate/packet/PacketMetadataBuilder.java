package dev.decorate.packet;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import dev.decorate.api.model.PlacedItem;
import dev.decorate.api.model.SurfacePose;
import dev.decorate.core.geometry.PlacementGeometry;
import dev.decorate.core.geometry.Quaternion4;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds Minecraft 1.21.8 protocol metadata lists for virtual {@code ItemDisplay},
 * {@code TextDisplay}, and {@code Interaction} entities.
 */
public final class PacketMetadataBuilder {

    public static final int DISPLAY_INTERPOLATION_DELAY_INDEX = 8;
    public static final int DISPLAY_TRANSFORM_DURATION_INDEX = 9;
    public static final int DISPLAY_TRANSLATION_INDEX = 11;
    public static final int DISPLAY_SCALE_INDEX = 12;
    public static final int DISPLAY_LEFT_ROTATION_INDEX = 13;
    public static final int DISPLAY_BILLBOARD_INDEX = 15;
    public static final int DISPLAY_VIEW_RANGE_INDEX = 17;
    public static final int DISPLAY_SHADOW_RADIUS_INDEX = 18;
    public static final int ITEM_DISPLAY_STACK_INDEX = 23;
    public static final int ITEM_DISPLAY_TRANSFORM_INDEX = 24;

    public static final int TEXT_DISPLAY_TEXT_INDEX = 23;
    public static final int TEXT_DISPLAY_LINE_WIDTH_INDEX = 24;
    public static final int TEXT_DISPLAY_BACKGROUND_COLOR_INDEX = 25;
    public static final int TEXT_DISPLAY_OPACITY_INDEX = 26;
    public static final int TEXT_DISPLAY_FLAGS_INDEX = 27;

    public static final byte BILLBOARD_CENTER = (byte) 3;
    public static final int TEXT_DEFAULT_LINE_WIDTH = 200;
    public static final int VANILLA_NAMEPLATE_BACKGROUND = 0x40000000;
    public static final byte TEXT_OPACITY_OPAQUE = (byte) -1;
    public static final byte TEXT_FLAGS_VANILLA_NAMEPLATE = (byte) 0x00;

    public static final int INTERACTION_WIDTH_INDEX = 8;
    public static final int INTERACTION_HEIGHT_INDEX = 9;
    public static final int INTERACTION_RESPONSIVE_INDEX = 10;

    private final Function<byte[], ItemStack> itemConverter;

    public PacketMetadataBuilder(Function<byte[], ItemStack> itemConverter) {
        this.itemConverter = Objects.requireNonNull(itemConverter, "itemConverter must not be null");
    }

    /**
     * Builds the entity metadata list for an {@code EntityTypes.ITEM_DISPLAY}.
     *
     * @param item placed item record
     * @param interpolationTicks transformation interpolation duration in ticks
     * @return metadata entries
     */
    public List<EntityData<?>> buildItemDisplayMetadata(PlacedItem item, int interpolationTicks) {
        SurfacePose pose = item.pose();
        Quaternion4 rot = PlacementGeometry.computeDisplayRotation(pose);
        ItemStack peItem = itemConverter.apply(item.serializedItem());

        List<EntityData<?>> data = new ArrayList<>(10);
        data.add(new EntityData<>(DISPLAY_INTERPOLATION_DELAY_INDEX, EntityDataTypes.INT, 0));
        data.add(new EntityData<>(DISPLAY_TRANSFORM_DURATION_INDEX, EntityDataTypes.INT, interpolationTicks));
        data.add(new EntityData<>(DISPLAY_TRANSLATION_INDEX, EntityDataTypes.VECTOR3F, Vector3f.zero()));
        data.add(new EntityData<>(DISPLAY_SCALE_INDEX, EntityDataTypes.VECTOR3F,
                new Vector3f(pose.scale().x(), pose.scale().y(), pose.scale().z())));
        data.add(new EntityData<>(DISPLAY_LEFT_ROTATION_INDEX, EntityDataTypes.QUATERNION,
                new Quaternion4f(rot.x(), rot.y(), rot.z(), rot.w())));
        data.add(new EntityData<>(DISPLAY_BILLBOARD_INDEX, EntityDataTypes.BYTE, (byte) 0));
        data.add(new EntityData<>(DISPLAY_VIEW_RANGE_INDEX, EntityDataTypes.FLOAT, 1.0f));
        data.add(new EntityData<>(DISPLAY_SHADOW_RADIUS_INDEX, EntityDataTypes.FLOAT, 0.0f));
        data.add(new EntityData<>(ITEM_DISPLAY_STACK_INDEX, EntityDataTypes.ITEMSTACK, peItem));
        data.add(new EntityData<>(ITEM_DISPLAY_TRANSFORM_INDEX, EntityDataTypes.BYTE,
                pose.displayTransform().protocolId()));
        return data;
    }

    /**
     * Builds the entity metadata list for an {@code EntityTypes.INTERACTION} hitbox.
     *
     * @param pose surface pose
     * @return metadata entries
     */
    public List<EntityData<?>> buildInteractionMetadata(SurfacePose pose) {
        List<EntityData<?>> data = new ArrayList<>(3);
        data.add(new EntityData<>(INTERACTION_WIDTH_INDEX, EntityDataTypes.FLOAT, pose.hitboxWidth()));
        data.add(new EntityData<>(INTERACTION_HEIGHT_INDEX, EntityDataTypes.FLOAT, pose.hitboxHeight()));
        data.add(new EntityData<>(INTERACTION_RESPONSIVE_INDEX, EntityDataTypes.BOOLEAN, true));
        return data;
    }

    /**
     * Builds the entity metadata list for an {@code EntityTypes.TEXT_DISPLAY} floating hover label
     * styled after a vanilla nameplate (center billboard, semi-transparent dark background, not see-through).
     *
     * @param text custom display name component
     * @param scale uniform text scale
     * @return metadata entries
     */
    public List<EntityData<?>> buildTextDisplayMetadata(Component text, float scale) {
        Objects.requireNonNull(text, "text must not be null");
        float clampedScale = Math.max(0.1f, scale);
        List<EntityData<?>> data = new ArrayList<>(12);
        data.add(new EntityData<>(DISPLAY_INTERPOLATION_DELAY_INDEX, EntityDataTypes.INT, 0));
        data.add(new EntityData<>(DISPLAY_TRANSFORM_DURATION_INDEX, EntityDataTypes.INT, 0));
        data.add(new EntityData<>(DISPLAY_TRANSLATION_INDEX, EntityDataTypes.VECTOR3F, Vector3f.zero()));
        data.add(new EntityData<>(DISPLAY_SCALE_INDEX, EntityDataTypes.VECTOR3F,
                new Vector3f(clampedScale, clampedScale, clampedScale)));
        data.add(new EntityData<>(DISPLAY_BILLBOARD_INDEX, EntityDataTypes.BYTE, BILLBOARD_CENTER));
        data.add(new EntityData<>(DISPLAY_VIEW_RANGE_INDEX, EntityDataTypes.FLOAT, 1.0f));
        data.add(new EntityData<>(DISPLAY_SHADOW_RADIUS_INDEX, EntityDataTypes.FLOAT, 0.0f));
        data.add(new EntityData<>(TEXT_DISPLAY_TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, text));
        data.add(new EntityData<>(TEXT_DISPLAY_LINE_WIDTH_INDEX, EntityDataTypes.INT, TEXT_DEFAULT_LINE_WIDTH));
        data.add(new EntityData<>(TEXT_DISPLAY_BACKGROUND_COLOR_INDEX, EntityDataTypes.INT, VANILLA_NAMEPLATE_BACKGROUND));
        data.add(new EntityData<>(TEXT_DISPLAY_OPACITY_INDEX, EntityDataTypes.BYTE, TEXT_OPACITY_OPAQUE));
        data.add(new EntityData<>(TEXT_DISPLAY_FLAGS_INDEX, EntityDataTypes.BYTE, TEXT_FLAGS_VANILLA_NAMEPLATE));
        return data;
    }
}
