# Tasks

## 1. Configuración y Geometría de Hover Display Name

- [x] 1.1 Añadir los campos de configuración de hover display name (`hoverDisplayNameEnabled`, `hoverCheckIntervalTicks`, `hoverDisplayScale`, `hoverVerticalOffset`) en `DecorateSettings`, `DecorAteConfigLoader` y `decorate-paper/src/main/resources/config.yml` bajo `visibility.hover-display-name`, y verificar con `DecorAteConfigAndLookupTest` la carga por defecto y overrides.
- [x] 1.2 Implementar `PlacementGeometry.computeHoverLabelPosition(SurfacePose pose, double verticalOffset)` cubriendo caras `UP`, horizontales (`NORTH`/`SOUTH`/`EAST`/`WEST`) y `DOWN`, y verificar con pruebas unitarias en `PlacementGeometryTest` que el label queda por encima en suelo/paredes y por debajo en techos.

## 2. Oclusión por Bloques en TargetedItemLookup

- [x] 2.1 Actualizar `TargetedItemLookup.findTargetedItem(Player)` para recortar la distancia efectiva al primer bloque sólido devuelto por `World.rayTraceBlocks(...)` (manteniendo comportamiento seguro cuando `rayTraceBlocks` retorna `null` en entornos sin bloque o mocks) y verificar con pruebas unitarias en `DecorAteConfigAndLookupTest` que items detrás de un bloque no se devuelven mientras los items al frente sí se detectan.

## 3. Metadatos y Renderizado Virtual de TEXT_DISPLAY

- [x] 3.1 Añadir en `PacketMetadataBuilder` la construcción de metadatos de `EntityTypes.TEXT_DISPLAY` para Minecraft 1.21.8 (escala configurable, billboard `CENTER`, `Component` del texto, estilo nameplate vanilla semitransparente y `see-through` deshabilitado) y verificar los índices y valores en `PacketEventsDisplayRendererTest`.
- [x] 3.2 Extender `DisplayRenderer` / `PacketEventsDisplayRenderer` (o añadir `HoverTextRenderer` integrado con el renderer) para spawnear, reemplazar (`DestroyEntities` + `SpawnEntity`) y destruir un `TEXT_DISPLAY` virtual por jugador, limpiándolo automáticamente en `destroyForPlayer`, `destroyAllForPlayer` y `broadcastDestroy`, y verificar todos los flujos con tests en `PacketEventsDisplayRendererTest`.

## 4. Caché de CUSTOM_NAME, Ciclo de Vida y Sondeo Periódico

- [x] 4.1 Implementar la extracción y caché por `UUID` de `DataComponentTypes.CUSTOM_NAME` desde `PlacedItem.serializedItem()` (ignorando items sin `CUSTOM_NAME`, incluso si tienen `ITEM_NAME` o nombre vanilla) con invalidación al destruir/remover el item, y verificar con tests unitarios que solo items con `CUSTOM_NAME` producen texto y no se re-deserializa en consultas repetidas.
- [x] 4.2 Integrar el controlador y task periódico en `DecorAtePlugin` (respetando `visibility.hover-display-name`, ejecutando el lookup solo si el jugador tiene items visibles y limpiando/reprogramando en `reloadPlugin()` y `onDisable()`) y verificar compilación y suite completa con `./gradlew test`.
