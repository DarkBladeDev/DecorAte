# Proposal

## Why

Los plugins tradicionales de exhibición o decoración basados en Armor Stands o entidades nativas de Bukkit sufren un alto costo de ticks en servidor y suelen restringirse a una entidad por bloque, lo que impide crear escenas detalladas sobre mesas, estanterías, paredes o techos. **DecorAte** resuelve esto permitiendo colocar múltiples items de la mano del jugador en puntos exactos de cualquier superficie usando `ItemDisplay` e `Interaction` enviados exclusivamente por paquetes, con soporte completo para los componentes de modelos custom de Minecraft 1.21.8 (`CustomModelData` e `ItemModelComponent`) y una arquitectura modular extensible en Java 21.

## What Changes

- Se inicializa la arquitectura multi-módulo con Gradle Kotlin DSL y Java 21 (`decorate-api`, `decorate-core`, `decorate-packet`, `decorate-hooks`, `decorate-paper`) sobre Paper 1.21.8.
- Se introduce la colocación libre continua en superficies mediante raycast (`Shift + Clic derecho` configurable), con sub-grid snap opcional, soporte para Supervivencia y Creativo, e integridad configurable ante ruptura/movimiento del bloque de soporte.
- Se implementa el motor de entidades por paquetes con PacketEvents 2.14.0 (`EntityTypes.ITEM_DISPLAY` + `EntityTypes.INTERACTION`), visibilidad por chunks/distancia de vista e interacción directa (recoger, rotar, alternar pose).
- Se agrega soporte de Nivel 2 para modelos custom (`CustomModelData` y `ItemModelComponent`) mediante perfiles YAML extensibles y SPI en `decorate-api`.
- Se incorpora persistencia asíncrona por chunks detrás de `StorageProvider` con SQLite embebido por defecto.
- Se integra un sistema extensible de protecciones (`ProtectionHook` + eventos propios cancelables) con soporte para WorldGuard 7.x.
- Se integra PlaceholderAPI 2.12.3 para exposición de variables (`PlaceholderExpansion`) y parsing desacoplado de placeholders.
- Se implementa el árbol de comandos `/decorate` (`/dec`) mediante Incendo Cloud Paper 2.0.1 y Cloud Annotations 2.1.0, incluyendo `/decorate give` para items definidos en perfiles custom.

## Capabilities

### New Capabilities

- `surface-item-placement`: Colocación libre de items en puntos exactos de superficies (sin restricción de una entidad por bloque), sub-grid snap opcional, soporte de modos Supervivencia/Creativo, límites y auto-cleanup/drop configurable al romperse o moverse el bloque soporte.
- `packet-display-rendering`: Renderizado e interacción sin entidades reales del servidor usando PacketEvents 2.14.0 (`ITEM_DISPLAY` e `INTERACTION`), tracking de visibilidad por chunks/distancia de vista y acciones directas sobre items colocados (recoger, rotar, cambiar pose).
- `custom-model-profiles`: Resolución y aplicación de perfiles YAML extensibles (y SPI en API) basados en `ItemModelComponent`, `CustomModelData` de 1.21.8 y material base para reglas de colocación, transformaciones visuales y plantillas de creación de items.
- `placement-persistence`: Almacenamiento y carga asíncrona indexada por chunk mediante la interfaz extensible `StorageProvider`, implementada con SQLite embebido por defecto y serialización íntegra de `ItemStack` de Paper 1.21.8.
- `worldguard-protection`: Pipeline extensible de validación de permisos y protecciones (`ProtectionHook` + eventos cancelables en `decorate-api`) con integración nativa de WorldGuard (flags custom en `onLoad` y checks de región).
- `placeholderapi-integration`: Exposición de métricas y contexto de DecorAte mediante `PlaceholderExpansion` de PlaceholderAPI 2.12.3 y servicio desacoplado de parsing de placeholders en textos del plugin.
- `decorate-commands`: Comandos administrativos y de jugador bajo `/decorate` (`/dec`) usando Incendo Cloud Paper 2.0.1 y Cloud Annotations 2.1.0 (`reload`, `mode`/`snap`, `info`, `clear`, `give`).

### Modified Capabilities

<!-- Ninguna: proyecto inicial sin capacidades previas -->

## Impact

- **Código nuevo (greenfield):** Proyecto completo Gradle Kotlin DSL multi-módulo (`settings.gradle.kts`, `build.gradle.kts`, `decorate-api`, `decorate-core`, `decorate-packet`, `decorate-hooks`, `decorate-paper`).
- **APIs y dependencias:** Paper API 1.21.8 (`io.papermc.paper:paper-api`), PacketEvents 2.14.0 (`com.github.retrooper:packetevents-spigot`), PlaceholderAPI 2.12.3 (`me.clip:placeholderapi`), Incendo Cloud Paper 2.0.1 + Cloud Annotations 2.1.0, WorldGuard 7.0.14 (`worldguard-bukkit`, opcional en runtime), y driver SQLite (`org.xerial:sqlite-jdbc`).
- **Superficie pública:** Contratos extensibles en `decorate-api` (modelos, eventos, SPI de perfiles, storage, hooks de protección y servicio de placeholders) y configuración YAML (`config.yml`, `profiles/*.yml`, `messages.yml`).
