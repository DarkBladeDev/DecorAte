# Tasks

## 1. Configuración de Gradle y catálogo de dependencias

- [x] 1.1 Añadir el repositorio `https://repo.alessiodp.com/releases/` en `build.gradle.kts` y registrar `libby-bukkit` (`1.3.2`) y `hikaricp` (`6.3.3`) en `gradle/libs.versions.toml`, verificando que Gradle resuelva las dependencias con `./gradlew dependencies --configuration compileClasspath`.
- [x] 1.2 Cambiar `sqlite-jdbc` en `decorate-core/build.gradle.kts` a `compileOnly` + `testImplementation` y añadir `hikaricp` como `compileOnly` + `testImplementation`, verificando que `:decorate-core:compileJava` y `:decorate-core:compileTestJava` compilen sin errores.
- [x] 1.3 Añadir `implementation(libs.libby.bukkit)` en `decorate-paper/build.gradle.kts` y configurar en `shadowJar` las relocalizaciones `net.byteflux.libby` -> `dev.decorate.libs.libby` y `com.zaxxer.hikari` -> `dev.decorate.libs.hikari` (además de propagar las versiones de `sqlite-jdbc` y `HikariCP` mediante `processResources` para mantener una única fuente de verdad), verificando que `:decorate-paper:shadowJar` genere el artefacto.

## 2. Migración de `SqliteStorageProvider` a HikariCP + `SQLiteDataSource`

- [x] 2.1 Refactorizar `SqliteStorageProvider` en `decorate-core` para reemplazar `DriverManager.getConnection(...)` y la conexión única manual por `HikariDataSource` configurado sobre `SQLiteDataSource` (`maximumPoolSize = 1`, `PRAGMA journal_mode=WAL`, `PRAGMA synchronous=NORMAL`, `PRAGMA busy_timeout=5000`), obteniendo conexiones por operación en `try-with-resources` sobre su executor single-thread y cerrando el pool limpiamente en `close()`; verificar revisando que no queden llamadas a `DriverManager` en `decorate-core`.
- [x] 2.2 Ejecutar y ampliar (si hace falta cubrir reapertura/cierre del pool o concurrencia secuencial) `SqliteStorageProviderTest` en `decorate-core`, verificando que `./gradlew :decorate-core:test` pase en verde en todas las pruebas de persistencia.

## 3. Carga en tiempo de ejecución con Libby en `decorate-paper`

- [x] 3.1 Implementar el cargador `RuntimeLibraryLoader` en `decorate-paper` usando `BukkitLibraryManager` (Maven Central), cargando `com.zaxxer:HikariCP` relocalizado (`com{}zaxxer{}hikari` -> `dev{}decorate{}libs{}hikari`) y `org.xerial:sqlite-jdbc` sin relocalizar a partir de las versiones resueltas desde build/recursos; verificar mediante tests unitarios del parsing/construcción de descriptores de librerías o validación de carga sin excepciones en compilación.
- [x] 3.2 Invocar `RuntimeLibraryLoader` al inicio de `DecorAtePlugin.onEnable()` antes de inicializar `SqliteStorageProvider`, capturando fallos de resolución para registrar un mensaje `SEVERE` descriptivo (indicando `plugins/DecorAte/libs` y el error) y deshabilitar limpiamente el plugin (`disablePlugin(this)`); verificar que `:decorate-paper:compileJava` y `:decorate-paper:test` pasen en verde.

## 4. Verificación integral del artefacto final

- [x] 4.1 Ejecutar `./gradlew clean test shadowJar` y verificar que toda la suite de tests pase y que el JAR resultante en `decorate-paper/build/libs/DecorAte-*.jar` pese menos de 2 MB, no contenga entradas `org/sqlite/*` ni `com/zaxxer/hikari/*` sin relocalizar, incluya `dev/decorate/libs/libby/*` y mantenga las referencias de `SqliteStorageProvider` apuntando a `dev/decorate/libs/hikari/*`.
