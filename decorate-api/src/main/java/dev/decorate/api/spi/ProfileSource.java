package dev.decorate.api.spi;

import dev.decorate.api.model.PlacementProfile;

import java.util.List;
import java.util.Objects;

/**
 * Extensible source supplying {@link PlacementProfile} entries paired with their {@link ProfileMatcher}.
 */
public interface ProfileSource {

    /**
     * Unique identifier for this source (e.g., {@code yaml}, or an addon name).
     *
     * @return source identifier
     */
    String sourceId();

    /**
     * Loads or generates all profile entries supplied by this source.
     *
     * @return immutable or snapshot list of profile entries
     */
    List<ProfileEntry> loadProfiles();

    /**
     * Pairing of a {@link ProfileMatcher} and its resulting {@link PlacementProfile}.
     *
     * @param matcher item matcher
     * @param profile target placement profile
     */
    record ProfileEntry(ProfileMatcher matcher, PlacementProfile profile) {
        public ProfileEntry {
            Objects.requireNonNull(matcher, "matcher must not be null");
            Objects.requireNonNull(profile, "profile must not be null");
        }
    }
}
