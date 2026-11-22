# Design

## Context

DecorAte renderiza cada `PlacedItem` con un par de entidades virtuales (`EntityTypes.ITEM_DISPLAY` y `EntityTypes.INTERACTION`) en `PacketEventsDisplayRenderer` (ver `proposal.md` para la motivación). Además, `TargetedItemLookup` ya resuelve el `PlacedItem` apuntado por un jugador mediante intersección ray-AABB contra las hitboxes en `SpatialRegistry`, aunque actualmente no verifica oclusión contra bloques sólidos del mundo.

En Paper 1.21.8, `ItemTemplateFactory` y los items renombrados por jugadores utilizan `DataComponentTypes.CUSTOM_NAME`, y `PlacedItem` persiste los bytes completos del stack (`serializedItem`).

## Goals / Non-Goals

**Goals:**
- Mostrar un `EntityTypes.TEXT_DISPLAY` virtual por paquetes solo al jugador que mira directamente un `PlacedItem` cuyo stack tenga `DataComponentTypes.CUSTOM_NAME`.
- Evitar lecturas/deserializaciones repetidas de `byte[]` en cada tick mediante caché en memoria por `UUID` del `PlacedItem`.
- Impedir que el label aparezca a través de bloques sólidos recortando el alcance del raycast con la distancia al primer bloque sólido impactado.
- Posicionar el texto de forma legible para `SurfaceFace.UP`, paredes horizontales (`NORTH/SOUTH/EAST/WEST`) y `SurfaceFace.DOWN` (techo).
- Mantener compatibilidad total con el esquema SQLite existente (`decorate.db`) sin migraciones.

**Non-Goals:**
- No mostrar nombres de items sin `CUSTOM_NAME` (incluyendo items que solo tengan `ITEM_NAME` o nombre vanilla).
- No renderizar hologramas permanentes visibles sin mirar el item.
- No introducir dependencias nuevas ni alterar el formato de persistencia de `PlacedItem` en SQLite.

## Decisions

### 1. Entidad virtual de texto por jugador (`HoverLabelService` / extensión en `PacketEventsDisplayRenderer`)
- **Decisión**: Mantener como máximo **una** entidad virtual `EntityTypes.TEXT_DISPLAY` activa por jugador (`Map<UUID, ActiveHoverLabel>` indexada por `playerUuid`), gestionada con `DestroyEntities(oldEntityId)` + `SpawnEntity(newEntityId)` + `EntityMetadata` al cambiar de item mirado, y `DestroyEntities` al dejar de mirar, cambiar de mundo, desconectarse o destruirse el item objetivo.
- **Alternativas consideradas**:
  - *Spawnear un `TEXT_DISPLAY` por cada `PlacedItem` renombrado y actualizar opacidad*: descartado por multiplicar entidades virtuales en cliente y complicar el renderizado selectivo por jugador.
  - *Reutilizar el mismo `entityId` con `EntityTeleport`*: descartado porque los clientes 1.21+ pueden interpolar visualmente la traslación entre items distantes; destruir y crear con un nuevo ID garantiza aparición instantánea en la nueva coordenada.
- **Integración con `DisplayRenderer` / `decorate-packet`**: Se puede añadir una capacidad dedicada (p. ej. `HoverTextRenderer` en `decorate-api` / `decorate-packet`, o métodos default en `DisplayRenderer` junto con limpieza automática en `destroyAllForPlayer` y `broadcastDestroy`) para que `PacketEventsDisplayRenderer` siga siendo el dueño de los `entityId` virtuales y su ciclo de vida.

### 2. Extracción y caché de `DataComponentTypes.CUSTOM_NAME`
- **Decisión**: Introducir un resolver/caché (`ConcurrentHashMap<UUID, Optional<Component>>` o `CustomNameCache`) en `decorate-paper` (o alimentado vía `Function<byte[], Optional<Component>>`) que inspeccione `ItemStack.deserializeBytes(item.serializedItem())` una única vez por `PlacedItem.id()`:
  - Si `stack.hasData(DataComponentTypes.CUSTOM_NAME)`, cachea `Optional.ofNullable(stack.getData(DataComponentTypes.CUSTOM_NAME))`.
  - Si no, cachea `Optional.empty()`.
  - Se invalida la entrada de caché cuando un item es eliminado (`broadcastDestroy` / `remove`).
- **Alternativa considerada**: Añadir una columna o campo serializado en `PlacedItem` / SQLite. Descartada para evitar migraciones de base de datos cuando `serializedItem` ya contiene los Data Components completos y la caché perezosa resuelve el costo en memoria.

### 3. Sondeo periódico ligero y oclusión por bloques
- **Decisión**:
  - En `TargetedItemLookup.findTargetedItem(Player)`, antes (o como cota superior) del bucle sobre candidatos, realizar `player.getWorld().rayTraceBlocks(eye, dir, maxDistance, FluidCollisionMode.NEVER, true)` (en entornos con `World` mockeado sin raytrace o `null`, mantener `maxDistance` como cota). La distancia efectiva máxima pasa a ser `Math.min(maxDistance, blockHitDistance + 1e-4)` para que los items pegados a la cara frontal del bloque sigan siendo alcanzables mientras los ocultos detrás de un bloque queden descartados.
  - Ejecutar el controlador de hover mediante un task síncrono periódico en `DecorAtePlugin` cada `hoverCheckIntervalTicks` (por defecto `2` ticks = 100 ms), omitiendo jugadores que no tengan items visibles en rango (`renderer` / `visibilityManager`) o cuando `visibility.hover-display-name.enabled` sea `false`.
- **Alternativa considerada**: Usar `PlayerMoveEvent`. Descartada como único mecanismo porque el evento se dispara en micro-movimientos por tick y no detecta cuando un item desaparece mientras el jugador está inmóvil.

### 4. Metadatos de `EntityTypes.TEXT_DISPLAY` (1.21.8) y geometría por cara
- **Decisión**:
  - `PacketMetadataBuilder` construirá la lista de `EntityData<?>` para `EntityTypes.TEXT_DISPLAY` (verificando contra los índices del protocolo 1.21.8 en PacketEvents 2.14.0):
    - `Display`: scale configurable (por defecto `0.75f` en los 3 ejes para mantener proporción con items de escala `0.5`), `billboard = CENTER` (`(byte) 3`), `view_range = 1.0f`, `shadow_radius = 0.0f`.
    - `TextDisplay`: texto (`EntityDataTypes.ADV_COMPONENT` con el `Component` de `CUSTOM_NAME`), `line_width = 200`, `background_color = 0x40000000` (fondo semitransparente estilo nameplate vanilla / flag `default_background = false` o `0x40000000` explícito según estilo vanilla en 1.21.8) y `flags` con sombra desactivada o estándar sin `see_through` (`0x00` o `0x04` si usa default background).
  - `PlacementGeometry.computeHoverLabelPosition(SurfacePose pose, double verticalOffset)` calculará el ancla:
    - `UP`: `exactPosition + (0, max(verticalOffset, hitboxHeight * 0.65 + 0.12), 0)`
    - `NORTH/SOUTH/EAST/WEST`: `exactPosition + (normalX * normalGap, max(verticalOffset, hitboxHeight * 0.5 + 0.12), normalZ * normalGap)` para separar el texto de la pared.
    - `DOWN`: `exactPosition - (0, max(verticalOffset, hitboxHeight * 0.65 + 0.18), 0)` para ubicarlo debajo del item y fuera del bloque del techo.

### 5. Configuración en `config.yml` y `DecorateSettings`
- **Decisión**: Añadir bajo `visibility.hover-display-name` en `config.yml` (y en `DecorateSettings` / `DecorAteConfigLoader`):
  - `enabled`: `true`
  - `check-interval-ticks`: `2`
  - `scale`: `0.75`
  - `vertical-offset`: `0.28`
  - Reutilizar `placement.max-distance` como alcance máximo de mirada (manteniendo coherencia con `TargetedItemLookup`).

## Risks / Trade-offs

- **[Índices de protocolo `TEXT_DISPLAY` en PacketEvents 2.14.0 / MC 1.21.8]** → Verificar en compilación/tests unitarios con `PacketEvents` los índices exactos de `TextDisplay` (23-27) y el tipo `EntityDataTypes.ADV_COMPONENT` para evitar desalineación de metadatos en cliente.
- **[Mocks en tests existentes de `TargetedItemLookup`]** → En `DecorAteConfigAndLookupTest`, `World` es un mock de Mockito donde `rayTraceBlocks(...)` retorna `null` por defecto; tratar `null` como "sin bloqueo" preserva compatibilidad con pruebas headless existentes y permite testear oclusión mockeando un `RayTraceResult`.
- **[Recarga con `/decorate reload`]** → Al recargar configuración (`DecorAtePlugin.reloadPlugin()`), si `hover-display-name.enabled` cambia a `false` o cambia `check-interval-ticks`, limpiar labels activos y reprogramar el task de hover.
