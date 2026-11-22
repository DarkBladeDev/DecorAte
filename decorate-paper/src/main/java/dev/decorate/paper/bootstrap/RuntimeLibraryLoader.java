package dev.decorate.paper.bootstrap;

import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Resolves and loads external persistence libraries at runtime via Libby so heavy native drivers
 * such as {@code sqlite-jdbc} do not need to be bundled inside the plugin JAR.
 */
public final class RuntimeLibraryLoader {

    private static final String DESCRIPTOR_RESOURCE = "runtime-libraries.properties";
    private static final String HIKARI_VERSION_KEY = "hikari.version";
    private static final String SQLITE_VERSION_KEY = "sqlite.version";

    private RuntimeLibraryLoader() {
    }

    /**
     * Downloads (if missing in {@code plugins/<plugin>/libs}) and injects HikariCP and sqlite-jdbc
     * into the plugin classpath.
     *
     * @param plugin owning Paper plugin
     */
    public static void loadStorageLibraries(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        Properties properties = readDescriptorProperties(plugin.getClass().getClassLoader());
        List<Library> libraries = buildStorageLibraries(properties);
        loadWith(new BukkitLibraryManager(plugin), libraries);
    }

    static Properties readDescriptorProperties(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        try (InputStream in = classLoader.getResourceAsStream(DESCRIPTOR_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + DESCRIPTOR_RESOURCE);
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + DESCRIPTOR_RESOURCE, ex);
        }
    }

    static List<Library> buildStorageLibraries(Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        String hikariVersion = requireVersion(properties, HIKARI_VERSION_KEY);
        String sqliteVersion = requireVersion(properties, SQLITE_VERSION_KEY);

        // Use {} placeholders instead of '.' so Shadow's string relocation does not rewrite source patterns.
        Library hikari = Library.builder()
                .id("hikaricp")
                .groupId("com{}zaxxer")
                .artifactId("HikariCP")
                .version(hikariVersion)
                .relocate("com{}zaxxer{}hikari", "dev{}decorate{}libs{}hikari")
                .build();

        // sqlite-jdbc binds JNI symbols under org.sqlite.* and must never be relocated.
        Library sqlite = Library.builder()
                .id("sqlite-jdbc")
                .groupId("org{}xerial")
                .artifactId("sqlite-jdbc")
                .version(sqliteVersion)
                .build();

        return List.of(hikari, sqlite);
    }

    static void loadWith(LibraryManager libraryManager, List<Library> libraries) {
        Objects.requireNonNull(libraryManager, "libraryManager must not be null");
        Objects.requireNonNull(libraries, "libraries must not be null");
        libraryManager.addMavenCentral();
        for (Library library : libraries) {
            try {
                libraryManager.loadLibrary(library);
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "Failed to resolve runtime library " + library.getGroupId()
                                + ":" + library.getArtifactId() + ":" + library.getVersion(),
                        ex
                );
            }
        }
    }

    private static String requireVersion(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalStateException("Missing or unresolved property '" + key + "' in " + DESCRIPTOR_RESOURCE);
        }
        return value.trim();
    }
}
