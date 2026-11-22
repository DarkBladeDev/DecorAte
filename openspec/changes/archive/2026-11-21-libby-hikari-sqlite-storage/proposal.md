# Proposal

## Why

El JAR distribuible de DecorAte (`DecorAte-*.jar`) pesa actualmente ~12.70 MB debido a que `shadowJar` empaqueta `org.xerial:sqlite-jdbc` (11.53 MB comprimidos por sus binarios nativos JNI multiplataforma), representando ~91% del artefacto mientras el código propio del plugin ocupa ~0.13 MB. Externalizar la carga de `sqlite-jdbc` y gestionar las conexiones mediante HikariCP descargados en tiempo de ejecución con **Libby** (`AlessioDP/libby`) reduce drásticamente el tamaño del JAR publicado y prepara el almacenamiento JDBC para futuros backends relacionales sin inflar el binario.

## What Changes

- Se integra **Libby** (`net.byteflux:libby-bukkit`, sombreado y relocalizado en `decorate-paper`) para descargar, cachear en `plugins/DecorAte/libs/` e inyectar en el classloader durante el arranque las dependencias externas de persistencia (`org.xerial:sqlite-jdbc` sin relocalizar y `com.zaxxer:HikariCP` relocalizado).
- Se reemplaza el empaquetado estático de `sqlite-jdbc` en el shadow JAR por dependencias `compileOnly` + `testImplementation` en `decorate-core` (manteniendo la suite de tests unitarios autocontenida en Gradle), conservando Incendo Cloud sombreado como hasta ahora.
- Se refactoriza `SqliteStorageProvider` para operar sobre `HikariDataSource` configurado con `SQLiteDataSource` (`maximumPoolSize = 1`, WAL, `synchronous=NORMAL`, `busy_timeout`), evitando `DriverManager` y sus fallos de classloader con librerías cargadas dinámicamente.
- Se incorpora manejo explícito de errores de descarga/carga de librerías en el arranque del plugin para fallar de forma limpia y descriptiva si no es posible resolver dependencias en un servidor sin caché previa ni red.

## Capabilities

### New Capabilities

- `runtime-dependency-loading`: Descarga, caché local en disco, relocalización opcional e inyección en el classloader en tiempo de ejecución mediante Libby antes de inicializar los servicios del plugin, con fallo limpio ante errores de resolución.

### Modified Capabilities

- `placement-persistence`: El backend SQLite por defecto deja de empaquetar `sqlite-jdbc` dentro del JAR del plugin y pasa a usar pooling JDBC con HikariCP sobre `SQLiteDataSource` cargado dinámicamente en runtime.

## Impact

- **Build & dependencias (`gradle/libs.versions.toml`, `build.gradle.kts`, `decorate-core/build.gradle.kts`, `decorate-paper/build.gradle.kts`):** Se añade repositorio de AlessioDP (`https://repo.alessiodp.com/releases/`), `libby-bukkit`, `HikariCP`, `sqlite-jdbc` en `compileOnly` + `testImplementation`, y relocalizaciones en `shadowJar` para `net.byteflux.libby` y `com.zaxxer.hikari`.
- **Runtime Paper (`decorate-paper`):** Nuevo cargador de librerías en `DecorAtePlugin.onEnable()` previo a la inicialización de almacenamiento.
- **Core de persistencia (`decorate-core`):** Actualización de `SqliteStorageProvider` (y validación de `SqliteStorageProviderTest`) para usar `HikariDataSource` + `SQLiteDataSource` en lugar de `DriverManager`.
- **Artefacto final:** Reducción estimada del JAR de ~12.70 MB a ~1.1–1.5 MB manteniendo Cloud sombreado.
