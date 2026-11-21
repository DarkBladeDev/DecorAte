# Design

## Context

Proyecto greenfield en Java 21 con Gradle Kotlin DSL sobre Paper 1.21.8 (`io.papermc.paper:paper-api`). No existe código previo en el repositorio (ver `proposal.md` para la motivación y alcance de capacidades). Las restricciones técnicas clave del ecosistema target son:
- **Entidades por paquetes:** PacketEvents 2.14.0 (`packetevents-spigot`, dependencia externa en tiempo de ejecución) se usa para enviar `EntityTypes.ITEM_DISPLAY` y `EntityTypes.INTERACTION` exclusivamente por red sin crear entidades persistentes en el servidor.
- **Componentes de items en 1.21.8:** Los modelos custom conviven entre `DataComponentTypes.ITEM_MODEL` (`NamespacedKey`) y `DataComponentTypes.CUSTOM_MODEL_DATA` compuesto (`floats`, `flags`, `strings`, `colors`), además de compatibilidad con `ItemMeta`.
- **Ciclo de carga de WorldGuard:** Las flags custom de WorldGuard 7.x deben registrarse durante `JavaPlugin#onLoad()` antes de que el `FlagRegistry` se congele en `onEnable()`.

## Goals / Non-Goals

**Goals:**
- Establecer una arquitectura modular limpia (`decorate-api`, `decorate-core`, `decorate-packet`, `decorate-hooks`, `decorate-paper`) con inversión de dependencias hacia `decorate-api`.
- Soportar múltiples items colocados por bloque con posición continua libre o sub-grid snap, alineación automática a cualquier cara (`UP`, `DOWN`, paredes) y hitbox `INTERACTION` sincronizada.
- Proveer extensibilidad real mediante SPI/interfaces públicas (`StorageProvider`, `ProfileMatcher`, `ProfileSource`, `ProtectionHook`, `PlaceholderService`, `DisplayRenderer`) y definiciones YAML declarativas en `profiles/*.yml`.
- Garantizar operaciones de persistencia asíncronas sin bloquear el tick principal de Paper.

**Non-Goals:**
- No se implementará soporte para versiones de Minecraft anteriores a 1.21.8 ni plataformas distintas a Paper/spigot-compat.
- No se incluye backend MySQL/PostgreSQL ni GUIs de inventario complejas en esta iteración (quedan habilitados vía SPI en `decorate-api` para cambios futuros).
- No se implementará física de gravedad/caída animada para items colocados ni sincronización multi-servidor en red.

## Decisions

### 1. Estructura multi-módulo Gradle Kotlin DSL
El proyecto se divide en cinco módulos con un catálogo de versiones centralizado (`gradle/libs.versions.toml`):
- `decorate-api`: Modelos inmutables (`PlacedItem`, `SurfacePose`, `PlacementProfile`, `ItemTemplate`), eventos cancelables (`DecorPlaceEvent`, `DecorInteractEvent`, `DecorRemoveEvent`), e interfaces (`StorageProvider`, `ProfileMatcher`, `ProfileSource`, `ProtectionHook`, `PlaceholderService`, `DisplayRenderer`). Solo depende de `paper-api`.
- `decorate-core`: Lógica de dominio pura e infraestructura local: `SpatialRegistry` (índice en memoria por mundo, chunk y bloque soporte), cálculo geométrico de colocación y snap, cargador/resolutor YAML de perfiles, gestor de visibilidad por distancia, y `SqliteStorageProvider`.
- `decorate-packet`: Implementación de `DisplayRenderer` y listener de paquetes con PacketEvents 2.14.0 (`EntityIdPool`, `WrapperPlayServerSpawnEntity`, `WrapperPlayServerEntityMetadata`, `WrapperPlayServerDestroyEntities`, `WrapperPlayClientInteractEntity`).
- `decorate-hooks`: Integraciones opcionales compiladas como `compileOnly` y activadas dinámicamente: `WorldGuardProtectionHook` (WorldGuard 7.0.14) y `PapiPlaceholderService` + `DecorAteExpansion` (PlaceholderAPI 2.12.3).
- `decorate-paper`: Bootstrap del plugin Paper (`plugin.yml` / `paper-plugin.yml`, `onLoad` y `onEnable`), listeners de Paper (`PlayerInteractEvent`, eventos de bloque soporte, chunk/world/player lifecycle), configuración e integración de comandos con Incendo Cloud Paper 2.0.1 + Cloud Annotations 2.1.0, empaquetando el JAR final con Shadow (relocalizando SQLite/Cloud si aplica y declarando `packetevents` como `depend` y `WorldGuard`/`PlaceholderAPI` como `softdepend`).

*Alternativa considerada:* Un único módulo plano en Gradle. Se descartó porque acopla las APIs públicas al puente de PacketEvents/SQLite y dificulta futuras extensiones o addons externos.

### 2. Renderizado e Interacción mediante Dual Entity Packet (`ITEM_DISPLAY` + `INTERACTION`)
Cada `PlacedItem` asigna dos IDs de entidad virtuales (generados desde un rango alto seguro o `AtomicInteger` decreciente / `SpigotReflectionUtil.generateEntityId()`):
1. `EntityTypes.ITEM_DISPLAY`: Recibe la posición exacta `(x, y, z)`, escala (`Vector3f`), rotación (`Quaternion4f` compuesta por la normal de la cara, `yaw` paso/continuo y modo de pose `FLAT` vs `UPRIGHT`), `ItemDisplayTransform` y `ItemStack` convertido vía `SpigotConversionUtil.fromBukkitItemStack(...)`.
2. `EntityTypes.INTERACTION`: Se posiciona desplazada en `-(height / 2)` respecto al centro visual proyectado hacia la normal de la cara de soporte, ya que el origen de la hitbox de `Interaction` está en su base inferior-central mientras `ItemDisplay` rota sobre su centro. Un mapa concurrente `entityId -> PlacedItemId` permite resolver O(1) los paquetes entrantes `WrapperPlayClientInteractEntity`.

Los clics sobre `Interaction` se traducen a acciones de dominio en el hilo principal del servidor (`BukkitScheduler` / region scheduler) tras validar throttling/rango:
- `ATTACK` (clic izquierdo): Recoger/eliminar item.
- `INTERACT` (clic derecho sin agacharse): Rotar un paso configurable (ej. `45°`).
- `INTERACT` + `player.isSneaking()` (`Shift + Clic derecho`): Alternar pose `FLAT` <-> `UPRIGHT`.

*Alternativa considerada:* Raycast puramente matemático desde `PlayerInteractEvent`. Se descartó como estrategia primaria porque la entidad `Interaction` delega la detección de colisión exacta al cliente incluso con múltiples decoraciones apretadas en un mismo bloque; el diseño mantiene `DisplayRenderer` desacoplado por si en el futuro se agrega una estrategia raycast-only.

### 3. Geometría de Superficie, Modo Libre y Sub-Grid Snap
Al producirse el trigger de colocación (`Shift + Clic derecho` por defecto, configurable):
1. Se ejecuta `player.rayTraceBlocks(maxDistance, FluidCollisionMode.NEVER)`.
2. A partir de `hitPosition` y `hitBlockFace`, se proyecta el punto en coordenadas locales `[0, 1]` de los dos ejes tangentes a la cara:
   - En modo **libre**, se conservan las coordenadas continuas exactas del impacto más `normalOffset` del perfil.
   - En modo **sub-grid snap** (configurable por jugador mediante `/decorate mode` o `/decorate snap <n>`), los ejes tangentes se cuantizan a múltiplos de `1.0 / gridDivisions` (ej. `1/16` o `1/8`), manteniendo la coordenada normal intacta.
3. Para el bloque de soporte (`supportBlockPos`), se almacena la posición del bloque impactado junto al `PlacedItem` en un índice secundario `supportBlockKey -> Set<UUID>` para O(1) cleanup en `BlockBreakEvent`, `BlockExplodeEvent`, `EntityExplodeEvent` y eventos de pistón cuando `auto-cleanup` esté activo.

### 4. Soporte Nivel 2 de Modelos Custom (`ItemModelComponent` + `CustomModelData` 1.21.8)
Los perfiles YAML viven en `plugins/DecorAte/profiles/*.yml` (más una sección de defaults en `config.yml`). Cada definición soporta:
- **Criterios de matching** (evaluados por especificidad descendente: `item_model` exacto > `item_model` wildcard > `custom_model_data` > `material` > `default`):
  - `match.item_model`: `NamespacedKey` exacto o patrón con `*` (ej. `decor:taza_cafe`, `decor:*`), leído desde `ItemStack#getData(DataComponentTypes.ITEM_MODEL)` (Paper 1.21.8).
  - `match.custom_model_data`: Criterios compatibles con el componente `DataComponentTypes.CUSTOM_MODEL_DATA` de 1.21.8 (`floats`, `strings`, `flags`, `colors`) y fallback a entero/float único.
  - `match.material`: Material base opcional.
- **Propiedades de colocación:** `placeable`, `allowed_faces`, `display_transform` (`FIXED`, `GROUND`, `HEAD`, `NONE`, etc.), `scale`, `normal_offset`, `rotation_offset`, `default_pose`, `hitbox` (`width`, `height`), `snap_override`.
- **Plantilla opcional `item`:** `material`, `display_name` (MiniMessage), `lore` (MiniMessage), `item_model`, `custom_model_data`, que registra el perfil como instanciable para `/decorate give <profileId> [player] [amount]`.

En `decorate-api`, `ProfileMatcher` y `ProfileSource` permiten que plugins externos inyecten matchers o perfiles dinámicos sin editar YAML.

### 5. Persistencia SQLite Asíncrona e Indexada por Chunk
`StorageProvider` define operaciones asíncronas (`CompletableFuture`) por mundo/chunk (`loadChunk`, `save`, `delete`, `deleteByChunk`, `countByOwner`):
- La implementación por defecto `SqliteStorageProvider` utiliza una tabla `placed_items` con índice compuesto `(world, chunk_x, chunk_z)` e índice por `owner_uuid`.
- El `ItemStack` completo se serializa con `ItemStack#serializeAsBytes()` en columna `BLOB`, preservando el 100% de los Data Components de Paper 1.21.8 (`item_model`, `custom_model_data`, encantamientos, PDC, etc.).
- Para evitar lecturas redundantes o race conditions entre descargas/cargas rápidas de chunks, un `ChunkCache` en memoria mantiene estado de carga por `(world, chunkX, chunkZ)` y serializa escrituras mediante un executor dedicado de un solo hilo (SQLite WAL mode).

### 6. Protecciones (Eventos Propios + WorldGuard `onLoad`) y PlaceholderAPI 2.12.3
- **WorldGuard:** En `JavaPlugin#onLoad()`, si el plugin `WorldGuard` está presente o cargado en el classpath, `WorldGuardProtectionHook` registra las flags custom `decorate-place` y `decorate-interact` (con fallback a `Flags.BUILD` / `Flags.INTERACT` o comportamiento configurable cuando no estén explícitamente definidas en la región). Si WorldGuard falla o no está instalado, se omite limpiamente.
- **PlaceholderAPI 2.12.3:** `DecorAteExpansion` (`identifier = "decorate"`, `persist() = true`) resuelve `%decorate_count%`, `%decorate_world_count%`, `%decorate_limit%`, `%decorate_remaining%`, `%decorate_chunk_count%`, `%decorate_looking_model%`, `%decorate_looking_owner%`. Para los placeholders `looking_*`, un raycast ligero o caché corta de último objetivo consultado verifica si el jugador apunta a una `Interaction` registrada (reutilizando la geometría de hitbox). `PlaceholderService` envuelve `PlaceholderAPI.setPlaceholders(player, text)` cuando PAPI existe y retorna el texto intacto si no.

### 7. Comandos con Incendo Cloud Paper 2.0.1 + Cloud Annotations 2.1.0
Se utiliza `LegacyPaperCommandManager` / `PaperCommandManager` de `org.incendo:cloud-paper:2.0.1` junto a `AnnotationParser` de `org.incendo:cloud-annotations:2.1.0` para declarar de forma limpia y tipada `/decorate` (alias `/dec`) y sus subcomandos (`reload`, `mode`, `snap`, `info`, `clear`, `give`), con sugerencias asíncronas/dinámicas para `profileId` (filtrando únicamente perfiles con plantilla `item` en `/decorate give`).

## Risks / Trade-offs

- **[Serialización de Data Components en PacketEvents 2.14.0 sobre 1.21.8]** → Realizar una prueba temprana de integración/spike verificando que `SpigotConversionUtil.fromBukkitItemStack(...)` conserva `ITEM_MODEL` y `CUSTOM_MODEL_DATA` compuesto al enviarse en `EntityData(23, EntityDataTypes.ITEMSTACK, ...)`, y aislar el adaptador en `decorate-packet` por si requiere ajustes de índice de metadata.
- **[Desfase visual entre hitbox `INTERACTION` y `ITEM_DISPLAY` rotado en paredes/techos]** → Calcular el centro efectivo de la caja `Interaction` aplicando la normal de la cara (`face.getDirection() * (hitboxDepth / 2)`) y restando `hitboxHeight / 2` en Y, y calibrar las dimensiones de hitbox en `profiles/*.yml` para modelos grandes o pequeños.
- **[Orden de carga con WorldGuard y `paper-plugin.yml` vs `plugin.yml`]** → Usar `plugin.yml` estándar (o `bootstrapper` explícito en Paper) verificando que `onLoad()` se ejecute antes de que WorldGuard congele el `FlagRegistry`, con captura defensiva de `FlagConflictException` ante recargas o plugins duplicados.
- **[Concurrencia entre packet listener (hilo Netty) y estado del mundo (hilo servidor)]** → Todo paquete entrante `WrapperPlayClientInteractEntity` que coincida con un ID virtual registrado despacha la mutación al hilo principal del servidor antes de consultar bloques, modificar inventario o disparar eventos Bukkit.
- **[Altas densidades de items en un chunk]** → Combinar envío diferido/batch de spawns al entrar en chunk con límites configurables por bloque, por chunk y por jugador para proteger ancho de banda del cliente.
