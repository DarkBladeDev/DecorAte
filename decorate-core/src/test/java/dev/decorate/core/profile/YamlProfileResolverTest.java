package dev.decorate.core.profile;

import dev.decorate.api.model.CustomModelDataSpec;
import dev.decorate.api.model.DisplayTransformType;
import dev.decorate.api.model.ItemInspection;
import dev.decorate.api.model.PlacementProfile;
import dev.decorate.api.model.SurfaceFace;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlProfileResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesSpecificityPrecedenceAndItemTemplatesFromYaml() throws IOException {
        Path yamlFile = tempDir.resolve("custom.yml");
        Files.writeString(yamlFile, """
                profiles:
                  decor_wildcard:
                    match:
                      item_model: "decor:*"
                    display_transform: GROUND
                    scale: [0.6, 0.6, 0.6]
                  taza_cafe:
                    match:
                      item_model: "decor:taza_cafe"
                    allowed_faces: [UP]
                    display_transform: FIXED
                    scale: [0.75, 0.75, 0.75]
                    hitbox:
                      width: 0.3
                      height: 0.35
                    item:
                      material: PAPER
                      display_name: "<gold>Taza de Cafe"
                      lore:
                        - "<gray>Decoracion"
                      item_model: "decor:taza_cafe"
                      custom_model_data:
                        floats: [101.0]
                        strings: ["coffee"]
                  cmd_banned:
                    match:
                      material: DIAMOND_SWORD
                      custom_model_data:
                        floats: [999.0]
                        flags: [true]
                    placeable: false
                """);

        DefaultProfileResolver resolver = new DefaultProfileResolver();
        resolver.registerSource(new YamlProfileSource(tempDir, Logger.getLogger("Test")));

        // Exact item_model beats wildcard
        PlacementProfile taza = resolver.resolve(new ItemInspection(Material.PAPER, "decor:taza_cafe", CustomModelDataSpec.EMPTY));
        assertEquals("taza_cafe", taza.id());
        assertEquals(DisplayTransformType.FIXED, taza.displayTransform());
        assertEquals(0.75f, taza.scale().x());
        assertTrue(taza.allowsFace(SurfaceFace.UP));
        assertFalse(taza.allowsFace(SurfaceFace.NORTH));
        assertTrue(taza.optionalItemTemplate().isPresent());

        // Wildcard matches other items in namespace
        PlacementProfile plate = resolver.resolve(new ItemInspection(Material.PAPER, "decor:plate", CustomModelDataSpec.EMPTY));
        assertEquals("decor_wildcard", plate.id());

        // Composite CustomModelData match
        PlacementProfile banned = resolver.resolve(new ItemInspection(
                Material.DIAMOND_SWORD,
                null,
                new CustomModelDataSpec(List.of(999.0f), List.of(true), List.of(), List.of())
        ));
        assertEquals("cmd_banned", banned.id());
        assertFalse(banned.placeable());

        // Giveable profiles include taza_cafe only
        assertEquals(1, resolver.giveableProfiles().size());
        assertTrue(resolver.findById("taza_cafe").isPresent());
    }

    @Test
    void hotReloadPicksUpNewYamlProfiles() throws IOException {
        DefaultProfileResolver resolver = new DefaultProfileResolver();
        resolver.registerSource(new YamlProfileSource(tempDir, Logger.getLogger("Test")));
        assertTrue(resolver.allProfiles().isEmpty());

        Files.writeString(tempDir.resolve("added.yml"), """
                profiles:
                  lantern_mini:
                    match:
                      item_model: "decor:mini_lantern"
                    allowed_faces: [UP, DOWN]
                """);
        resolver.reload();

        PlacementProfile resolved = resolver.resolve(new ItemInspection(Material.LANTERN, "decor:mini_lantern", CustomModelDataSpec.EMPTY));
        assertEquals("lantern_mini", resolved.id());
        assertTrue(resolved.allowsFace(SurfaceFace.DOWN));
    }
}
