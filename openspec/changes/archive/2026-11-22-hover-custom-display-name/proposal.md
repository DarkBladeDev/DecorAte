# Proposal

## Why

Los jugadores pueden colocar items renombrados (por yunque, comandos o `ItemTemplate` con `displayName`) sobre superficies, pero actualmente el `ItemDisplay` virtual no muestra ese nombre en el mundo al acercarse o mirarlo. Añadir un label flotante contextual permite identificar decoraciones personalizadas sin saturar visualmente la escena cuando no se las está mirando.

## What Changes

- Renderizar por paquetes un `EntityTypes.TEXT_DISPLAY` virtual por jugador sobre el item colocado al que esté apuntando, **únicamente** cuando ese item tenga `DataComponentTypes.CUSTOM_NAME` definido.
- Aplicar estética de nameplate vanilla (`TEXT_DISPLAY` con billboard `CENTER`, fondo semitransparente por defecto de Minecraft, escala proporcionada y `see-through` deshabilitado) y posicionamiento adaptado a la cara de soporte (`UP`, paredes horizontales y `DOWN`).
- Incorporar chequeo de oclusión contra bloques sólidos en el raycast de detección (`TargetedItemLookup`) para evitar que se detecten o muestren nombres de items detrás de paredes.
- Cachear el `Component` del nombre custom extraído de los bytes serializados del item para evitar deserializaciones repetidas durante el sondeo periódico de mirada.
- Exponer configuración en `config.yml` (sección `visibility.hover-display-name`) para habilitar/deshabilitar, ajustar intervalo de comprobación, escala y offset.

## Capabilities

### New Capabilities
<!-- Ninguna: la capacidad de renderizado virtual por paquetes ya existe en packet-display-rendering -->

### Modified Capabilities
- `packet-display-rendering`: Añadir renderizado por paquetes de `TEXT_DISPLAY` flotante al mirar un item colocado con `DataComponentTypes.CUSTOM_NAME`, con oclusión de bloques, posicionamiento por cara, estilo nameplate vanilla y limpieza al dejar de mirar o destruirse el item.

## Impact

- **`decorate-api`**: Sin cambios incompatibles en `PlacedItem` ni en SQLite; posible extensión mínima o helper en `DisplayRenderer` (o interfaz dedicada de hover label) para gestionar el `TEXT_DISPLAY` virtual por jugador.
- **`decorate-packet`**: `PacketMetadataBuilder` (metadatos de `EntityTypes.TEXT_DISPLAY` para Minecraft 1.21.8 / PacketEvents 2.14.0) y `PacketEventsDisplayRenderer` (gestión de la entidad virtual de texto por jugador y limpieza en `broadcastDestroy` / `destroyAllForPlayer`).
- **`decorate-core`**: `DecorateSettings` (opciones de configuración del hover display name) y cálculo de posición del label en `PlacementGeometry` según `SurfaceFace` y escala/hitbox.
- **`decorate-paper`**: `TargetedItemLookup` (oclusión por bloques con raycast), extracción y cacheo de `DataComponentTypes.CUSTOM_NAME`, `DecorAteConfigLoader` / `config.yml`, y task periódico en `DecorAtePlugin` / listener de desconexión y cambio de mundo.
