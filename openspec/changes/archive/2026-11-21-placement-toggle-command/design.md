# Design

## Context

Actualmente, `PlacementTriggerListener` intercepta todo `PlayerInteractEvent` (`RIGHT_CLICK_BLOCK` con mano principal y sneak si `requireSneakForPlace()` está activo) para jugadores con `decorate.place`, ejecutando `PlacementService.tryPlace(...)` y cancelando el evento incluso en varios casos de rechazo. Por su parte, `PacketInteractionListener` intercepta `INTERACT_ENTITY` contra entidades virtuales y delega `PICKUP`, `ROTATE` y `TOGGLE_POSE` a `DecorAtePlugin.handlePacketInteraction(...)` -> `PlacementService.handleInteraction(...)`.

Las preferencias por jugador (`playerModes` con `FREE`/`SNAP` en `PlacementService`) residen exclusivamente en memoria, mientras que `SqliteStorageProvider` solo almacena la tabla `placed_items`. Véase `proposal.md` para la motivación completa.

## Goals / Non-Goals

**Goals:**
- Permitir a cada jugador activar/desactivar en memoria su propia capacidad de colocar items (`/decorate toggle [on|off]`) sin perder su preferencia `FREE`/`SNAP` ni bloquear sus interacciones (`PICKUP`/`ROTATE`/`TOGGLE_POSE`) sobre items ya colocados.
- Permitir a administradores activar/desactivar globalmente toda la mecánica (`/decorate global [on|off|toggle]`), bloqueando colocación e interacción sin bypass y conservando la visibilidad de los displays ya renderizados.
- Garantizar que, cuando la colocación esté desactivada (personal o global), `PlacementTriggerListener` salga antes de raycastear o cancelar `PlayerInteractEvent`, preservando intacto el `Shift + Clic derecho` vanilla.
- Exponer `%decorate_enabled%` (estado personal) y `%decorate_enabled_global%` (estado global) en `DecorAteExpansion`.

**Non-Goals:**
- Persistir el toggle personal o el estado global en SQLite o escribir automáticamente sobre `config.yml` (evitando alterar comentarios del YAML mediante `saveConfig()`).
- Modificar las flags existentes de WorldGuard (`decorate-place` y `decorate-interact`) o su flujo de cancelación actual.
- Añadir un permiso de bypass para el bloqueo global.

## Decisions

### 1. Estado en memoria en `PlacementService`
Se centralizan tanto el estado global como el personal en `PlacementService`:
- `private volatile boolean globalEnabled`: inicializado en el constructor desde `settings.globalPlacementEnabled()` y re-sincronizado en `updateSettings(DecorateSettings)` al ejecutar `/decorate reload`.
- `private final Set<UUID> disabledPlacementPlayers = ConcurrentHashMap.newKeySet()`: almacenamiento por ausencia (todo jugador conectado inicia habilitado por defecto; estar en el set equivale a `enabled = false`).
- `VisibilityLifecycleListener.onPlayerQuit(PlayerQuitEvent)` invocará `placementService.clearPlayerSession(player.getUniqueId())` (limpiando el toggle de sesión para no retener UUIDs desconectados en memoria).

*Alternativa descartada*: Persistir en una nueva tabla `player_preferences` de SQLite o sobrescribir `config.yml`. Se descartó por simplicidad y consistencia con `playerModes` actual (estado efímero por sesión) y para no perder comentarios de `config.yml` con `saveConfig()`. Es fácilmente extensible a SQLite en el futuro si se requiere.

### 2. Puntos de intercepción y preservación del flujo vanilla
- **Colocación (`PlacementTriggerListener.onPlayerInteract`)**: antes de validar ítem en mano o ejecutar `rayTraceBlocks`, se comprueba `if (!placementService.isGlobalEnabled() || !placementService.isPlayerPlacementEnabled(player.getUniqueId())) return;`. Al retornar temprano sin llamar a `event.setCancelled(true)`, Paper procesa el `Shift + Clic derecho` vanilla (abrir contenedores, colocar bloques, etc.) de forma transparente y sin spam de mensajes.
- **Defensa en profundidad (`PlacementService.tryPlace`)**: también validará `globalEnabled` y `isPlayerPlacementEnabled(player.getUniqueId())` devolviendo `OutcomeReason.DISABLED` (o `GLOBAL_DISABLED` / `PLAYER_DISABLED`) por si un caller interno o test invoca `tryPlace` directamente.
- **Interacción sobre items colocados (`DecorAtePlugin.handlePacketInteraction` + `PlacementService.handleInteraction`)**: `PacketInteractionListener` sigue cancelando el paquete `INTERACT_ENTITY` porque el `entityId` es virtual y no existe en el servidor. Luego, en `handlePacketInteraction` (y en `PlacementService.handleInteraction`), si `!placementService.isGlobalEnabled()` se rechaza la acción sin mutar estado y se envía el mensaje `rejected.disabled`. Si el estado global está en `true`, la interacción procede normalmente aunque el jugador tenga su toggle personal en `off`.

*Alternativa descartada*: Cancelar `PlayerInteractEvent` y mostrar un mensaje de error cuando el global está en `off`. Se descartó explícitamente porque impediría el `Shift + Clic derecho` vanilla en todo el servidor mientras esté pausado.

### 3. Estructura de comandos en `DecorateCommands`
Se añaden dos subcomandos separados con Cloud Annotations:
- `@Command("decorate|dec toggle [state]")` con `@Permission("decorate.command.toggle")` (`default: true` en `plugin.yml`). Acepta un enum o argumento opcional (`on`/`off` o `ToggleAction`) que sin argumento invierte el estado actual del jugador. Rechaza consola con `command.player-only`.
- `@Command("decorate|dec global [state]")` con `@Permission("decorate.command.global")` (`default: op` en `plugin.yml`). Acepta argumento opcional (`on`/`off`/`toggle`; sin argumento actúa como `toggle`) y funciona tanto para jugador como para consola.

### 4. Configuración, mensajes y placeholders
- `config.yml` (`DecorAteConfigLoader` / `DecorateSettings`): nueva clave `placement.enabled` (boolean, por defecto `true`).
- `messages.yml`: claves nuevas para confirmar `/dec toggle` (activado/desactivado), `/dec global` (activado/desactivado) y rechazo por estado global en interacción (`rejected.disabled`).
- `DecorAteExpansion`:
  - `enabled` (`%decorate_enabled%`): devuelve `String.valueOf(placementService.isPlayerPlacementEnabled(player.getUniqueId()))`.
  - `enabled_global` (`%decorate_enabled_global%`): devuelve `String.valueOf(placementService.isGlobalEnabled())`.

## Risks / Trade-offs

- **Pérdida del estado tras reinicio o `/dec reload`**: Un administrador que ejecutó `/dec global off` volverá al valor de `config.yml` (`placement.enabled`) tras `/dec reload` o reinicio -> Mitigación: documentar el comportamiento esperado en `config.yml` y en los mensajes de confirmación; si se requiere persistencia prolongada, `config.yml` ofrece `placement.enabled: false`.
- **Diferencia silenciosa al colocar con global OFF**: Un jugador intentando colocar con `Shift + Clic derecho` mientras el global está apagado ejecutará la acción vanilla sin aviso en chat -> Mitigación: comportamiento explícitamente deseado para no romper interacciones vanilla; el feedback claro ocurre al intentar editar/recoger una decoración existente (`rejected.disabled`) o al consultar `%decorate_enabled_global%`.
