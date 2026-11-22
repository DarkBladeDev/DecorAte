package dev.decorate.paper.bootstrap;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import net.byteflux.libby.relocation.Relocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class RuntimeLibraryLoaderTest {

    @Test
    void readsFilteredVersionsFromClasspathAndBuildsSelectiveRelocations() {
        Properties properties = RuntimeLibraryLoader.readDescriptorProperties(getClass().getClassLoader());
        List<Library> libraries = RuntimeLibraryLoader.buildStorageLibraries(properties);

        assertEquals(2, libraries.size());

        Library hikari = libraries.get(0);
        assertEquals("com.zaxxer", hikari.getGroupId());
        assertEquals("HikariCP", hikari.getArtifactId());
        assertEquals(properties.getProperty("hikari.version"), hikari.getVersion());
        assertTrue(hikari.hasRelocations());
        Relocation relocation = hikari.getRelocations().iterator().next();
        assertEquals("com.zaxxer.hikari", relocation.getPattern());
        assertEquals("dev.decorate.libs.hikari", relocation.getRelocatedPattern());

        Library sqlite = libraries.get(1);
        assertEquals("org.xerial", sqlite.getGroupId());
        assertEquals("sqlite-jdbc", sqlite.getArtifactId());
        assertEquals(properties.getProperty("sqlite.version"), sqlite.getVersion());
        assertFalse(sqlite.hasRelocations());
    }

    @Test
    void rejectsMissingOrUnexpandedVersionProperties() {
        Properties missing = new Properties();
        missing.setProperty("hikari.version", "6.3.3");
        assertThrows(IllegalStateException.class, () -> RuntimeLibraryLoader.buildStorageLibraries(missing));

        Properties unexpanded = new Properties();
        unexpanded.setProperty("hikari.version", "${hikariVersion}");
        unexpanded.setProperty("sqlite.version", "3.54.1.0");
        assertThrows(IllegalStateException.class, () -> RuntimeLibraryLoader.buildStorageLibraries(unexpanded));
    }

    @Test
    void loadsLibrariesThroughMavenCentralAndWrapsFailuresWithCoordinates() {
        LibraryManager manager = mock(LibraryManager.class);
        Properties properties = RuntimeLibraryLoader.readDescriptorProperties(getClass().getClassLoader());
        List<Library> libraries = RuntimeLibraryLoader.buildStorageLibraries(properties);

        RuntimeLibraryLoader.loadWith(manager, libraries);

        var order = inOrder(manager);
        order.verify(manager).addMavenCentral();
        order.verify(manager).loadLibrary(libraries.get(0));
        order.verify(manager).loadLibrary(libraries.get(1));

        LibraryManager failingManager = mock(LibraryManager.class);
        doThrow(new RuntimeException("offline")).when(failingManager).loadLibrary(any());
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> RuntimeLibraryLoader.loadWith(failingManager, libraries)
        );
        assertTrue(ex.getMessage().contains("com.zaxxer:HikariCP"));
    }
}
