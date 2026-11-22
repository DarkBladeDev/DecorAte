# Design

## Context

Actualmente `decorate-core` declara `implementation(libs.sqlite.jdbc)` (`org.xerial:sqlite-jdbc:3.54.1.0`), lo que obliga a `shadowJar` en `decorate-paper` a empaquetar ~11.53 MB de binarios nativos dentro de un JAR total de ~12.70 MB. Además, `SqliteStorageProvider` mantiene una única `Connection` directa abierta mediante `DriverManager.getConnection(...)`. Véase `proposal.md` para la motivación completa y las capacidades especificadas en `specs/`.

## Goals / Non-Goals

**Goals:**
- Reducir drásticamente el tamaño del shadow JAR de `decorate-paper` excluyendo `sqlite-jdbc` y `HikariCP` del empaquetado estático y cargándolos en runtime con Libby (`net.byteflux:libby-bukkit`).
- Mantener la suite de tests unitarios (`./gradlew test`) 100% ejecutable sin conexión ni Libby usando `compileOnly` + `testImplementation` en `decorate-core`.
- Migrar `SqliteStorageProvider` a `HikariDataSource` configurado sobre `SQLiteDataSource`, eliminando la dependencia de `DriverManager` y preparando el patrón JDBC para futuros backends.
- Asegurar un fallo limpio y descriptivo en `DecorAtePlugin.onEnable()` si falla la resolución de librerías externas.

**Non-Goals:**
- Externalizar Incendo Cloud (`cloud-paper` / `cloud-annotations`), que permanece sombreado y relocalizado bajo `dev.decorate.libs.cloud` (~0.83 MB).
- Implementar soporte de configuración YAML o consultas para MySQL/MariaDB en este cambio (solo se deja preparada la base JDBC con HikariCP).
- Migrar de `plugin.yml` a `paper-plugin.yml` o `PaperPluginLoader`.

## Decisions

### 1. Gestor de dependencias en runtime: `net.byteflux:libby-bukkit` (`1.3.2`)
Se utiliza `BukkitLibraryManager` (proveniente de `https://repo.alessiodp.com/releases/`) sombreado en `decorate-paper` y relocalizado a `dev.decorate.libs.libby`.
- **Alternativas consideradas:**
  - `libraries:` nativo en `plugin.yml`: más liviano en el JAR pero no soporta relocalización en runtime para HikariCP ni repositorios personalizados si se agregan en el futuro.
  - `libby-paper` (`PaperLibraryManager`): exige `paper-plugin.yml` (`PaperPluginClassLoader`); como DecorAte usa `plugin.yml`, Paper asigna `PluginClassLoader` (subclase de `URLClassLoader`) y `PaperLibraryManager` falla en arranque, por lo que `BukkitLibraryManager` es la variante correcta.

### 2. Relocalización selectiva y coherencia Shadow <-> Libby
- **`HikariCP` (`com.zaxxer:HikariCP:6.3.3`) SÍ se relocaliza** a `dev.decorate.libs.hikari`:
  - En `decorate-paper/build.gradle.kts`, `shadowJar` aplica `relocate("com.zaxxer.hikari", "dev.decorate.libs.hikari")` para reescribir las referencias del bytecode de `decorate-core` aunque HikariCP no se empaquete en el JAR.
  - En runtime, `RuntimeLibraryLoader` configura `.relocate("com{}zaxxer{}hikari", "dev{}decorate{}libs{}hikari")` (usando `{}` para evitar que `shadowJar` reescriba el literal del primer argumento).
- **`sqlite-jdbc` (`org.xerial:sqlite-jdbc:3.54.1.0`) NO se relocaliza:** sus librerías nativas JNI vinculan nombres exactos bajo `org.sqlite.*`.
- **Alternativas consideradas:** Dejar HikariCP sin relocalizar (descartado para evitar colisiones con otros plugins en el mismo servidor que expongan distinta versión de HikariCP).

### 3. `HikariDataSource` sobre `SQLiteDataSource` (sin `DriverManager`)
En `SqliteStorageProvider`:
- Se construye `org.sqlite.SQLiteDataSource` con URL `jdbc:sqlite:<path>` y `SQLiteConfig` (o pragmas iniciales `journal_mode=WAL`, `synchronous=NORMAL`, `busy_timeout=5000`), y se pasa mediante `hikariConfig.setDataSource(sqliteDataSource)`.
- Se configura `maximumPoolSize = 1` y `poolName = "DecorAte-SQLite"`, manteniendo el `ExecutorService` single-thread existente para serializar operaciones asíncronas fuera del hilo principal y obtener conexiones con `try (Connection conn = requireDataSource().getConnection())`.
- **Por qué sobre `SQLiteDataSource` y no `DriverManager.getConnection(...)` / `setJdbcUrl(...)`:** `DriverManager` suele fallar con `No suitable driver found` cuando el driver JDBC se inyecta dinámicamente en el `PluginClassLoader` tras iniciar la JVM. Pasar `SQLiteDataSource` directamente evita `DriverManager` por completo.
- **Por qué `maximumPoolSize = 1`:** SQLite embebido solo admite un escritor simultáneo; limitar el pool a 1 previene errores `SQLITE_BUSY` mientras conserva la validación de salud y cierre limpio de HikariCP.

### 4. Ciclo de carga y manejo de errores en `DecorAtePlugin`
- `RuntimeLibraryLoader` se invoca al inicio de `DecorAtePlugin.onEnable()`, antes de instanciar `SqliteStorageProvider`.
- Si Libby falla al descargar o cargar `HikariCP` o `sqlite-jdbc`, `onEnable()` registra un error `SEVERE` claro indicando la causa y el directorio `plugins/DecorAte/libs`, llama a `getServer().getPluginManager().disablePlugin(this)` y retorna sin inicializar `storage`, listeners ni comandos.
- **SLF4J en runtime y tests:** HikariCP usa `org.slf4j`, ya provisto por Paper en runtime; en `decorate-core` los tests lo resuelven vía `testImplementation(libs.paper.api)` / transitivos.

## Risks / Trade-offs

- **Primer arranque sin internet o Maven Central caído** → El plugin requiere conexión en el primer arranque (o copiar manualmente los JARs a `plugins/DecorAte/libs/`). Mitigación: fallo temprano con log claro en `onEnable()` sin corromper la base de datos ni dejar listeners activos.
- **Desincronización de versiones entre Gradle y Libby** → Si se actualiza `sqlite-jdbc` o `HikariCP` en `gradle/libs.versions.toml` y no en el cargador en runtime, podría haber incompatibilidad. Mitigación: inyectar/filtrar las coordenadas o versiones desde recursos generados por Gradle (`processResources`) o centralizarlas en una única fuente de verdad durante el build.
- **Relocalización en runtime en el primer arranque** → Libby ejecuta relocalización bytecode (ASM/jar-relocator) de `HikariCP` la primera vez (~160 KB), añadiendo unos milisegundos solo en el primer inicio; los arranques siguientes reutilizan el JAR relocalizado ya cacheado en `plugins/DecorAte/libs/`.
- **Tamaño de `libby-bukkit` + relocator en el JAR** → Sombra `libby-bukkit` y sus utilidades (~200–400 KB), aún muy por debajo de los ~11.53 MB ahorrados al externalizar `sqlite-jdbc`.
