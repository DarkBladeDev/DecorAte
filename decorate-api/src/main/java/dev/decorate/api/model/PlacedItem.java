package dev.decorate.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable persisted record of an item placed onto a block surface.
 *
 * @param id unique placement UUID
 * @param world world name
 * @param supportBlock supporting block coordinate
 * @param pose exact world pose and display transform
 * @param ownerUuid owner player UUID
 * @param ownerName cached owner name at placement time
 * @param creativePlaced whether the item was placed in Creative mode
 * @param serializedItem raw {@code ItemStack#serializeAsBytes()} payload
 * @param resolvedProfileId identifier of the profile resolved at placement
 * @param itemDisplayKey human-readable model/material label for placeholders and inspection
 * @param createdAtMillis epoch timestamp in milliseconds
 */
public record PlacedItem(
        UUID id,
        String world,
        BlockCoordinate supportBlock,
        SurfacePose pose,
        UUID ownerUuid,
        String ownerName,
        boolean creativePlaced,
        byte[] serializedItem,
        String resolvedProfileId,
        String itemDisplayKey,
        long createdAtMillis
) {

    public PlacedItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(world, "world must not be null");
        Objects.requireNonNull(supportBlock, "supportBlock must not be null");
        Objects.requireNonNull(pose, "pose must not be null");
        Objects.requireNonNull(ownerUuid, "ownerUuid must not be null");
        Objects.requireNonNull(ownerName, "ownerName must not be null");
        Objects.requireNonNull(serializedItem, "serializedItem must not be null");
        if (serializedItem.length == 0) {
            throw new IllegalArgumentException("serializedItem must not be empty");
        }
        if (!world.equals(supportBlock.world())) {
            throw new IllegalArgumentException("supportBlock world must match PlacedItem world");
        }
        serializedItem = serializedItem.clone();
        resolvedProfileId = resolvedProfileId == null ? PlacementProfile.DEFAULT.id() : resolvedProfileId;
        itemDisplayKey = itemDisplayKey == null ? "unknown" : itemDisplayKey;
    }

    @Override
    public byte[] serializedItem() {
        return serializedItem.clone();
    }

    /**
     * Returns the chunk coordinate containing this item's exact position.
     *
     * @return containing chunk coordinate
     */
    public ChunkCoordinate chunk() {
        return ChunkCoordinate.fromPosition(world, pose.exactPosition().x(), pose.exactPosition().z());
    }

    /**
     * Returns a copy of this placed item with an updated {@link SurfacePose}.
     *
     * @param updatedPose new surface pose
     * @return updated placed item
     */
    public PlacedItem withPose(SurfacePose updatedPose) {
        return new PlacedItem(
                id,
                world,
                supportBlock,
                updatedPose,
                ownerUuid,
                ownerName,
                creativePlaced,
                serializedItem,
                resolvedProfileId,
                itemDisplayKey,
                createdAtMillis
        );
    }
}
