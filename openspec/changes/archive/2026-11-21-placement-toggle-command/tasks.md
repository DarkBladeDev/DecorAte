# Tasks

## 1. Configuración y estado de activación en `decorate-core`

- [x] 1.1 Añadir `boolean globalPlacementEnabled` a `DecorateSettings` (`DEFAULTS` con `true`) y cargar `placement.enabled` (default `true`) en `DecorAteConfigLoader` y `config.yml`, verificando con un test unitario en `DecorAteConfigAndLookupTest` que carga tanto `true` como `false` y el valor por defecto.
- [x] 1.2 Añadir en `PlacementService` el estado en memoria `globalEnabled` (inicializado y re-sincronizado en `updateSettings`), `disabledPlacementPlayers` (`Set<UUID>`), métodos de consulta/mutación (`isGlobalEnabled`, `setGlobalEnabled`, `toggleGlobalEnabled`, `isPlayerPlacementEnabled`, `setPlayerPlacementEnabled`, `togglePlayerPlacementEnabled`, `clearPlayerSession`) y guardas en `tryPlace` y `handleInteraction` (bloqueando colocación si `!globalEnabled` o `!playerEnabled`, e interacción solo si `!globalEnabled`), verificando con tests unitarios en `PlacementServiceTest`.

## 2. Listeners de colocación, interacción y ciclo de sesión en `decorate-paper`

- [x] 2.1 Actualizar `PlacementTriggerListener.onPlayerInteract` para salir tempranamente sin raycastear ni cancelar `PlayerInteractEvent` cuando `!placementService.isGlobalEnabled()` o `!placementService.isPlayerPlacementEnabled(player.getUniqueId())`, verificando en `PaperListenersTest` que el evento permanece sin cancelar y no consume el ítem.
- [x] 2.2 Actualizar `DecorAtePlugin.handlePacketInteraction` para rechazar la interacción con el mensaje `rejected.disabled` cuando `!placementService.isGlobalEnabled()` (manteniendo activa la interacción cuando solo el toggle personal del jugador está apagado) y añadir la limpieza de sesión en `VisibilityLifecycleListener.onPlayerQuit`, verificando ambos comportamientos con tests unitarios en `PaperListenersTest`.

## 3. Comandos `/decorate toggle` y `/decorate global`

- [x] 3.1 Declarar `decorate.command.toggle` (`default: true`) y `decorate.command.global` (`default: op`) en `plugin.yml` y añadir los mensajes de confirmación y `rejected.disabled` en `messages.yml`, verificando que los recursos YAML cargan sin errores.
- [x] 3.2 Implementar `@Command("decorate|dec toggle [state]")` y `@Command("decorate|dec global [state]")` con autocompletado y validaciones en `DecorateCommands`, verificando en `DecorateCommandsTest` todos los casos (`on`, `off`, sin argumento/`toggle`, rechazo en consola para `toggle` y soporte en consola para `global`).

## 4. Placeholders en `decorate-hooks` y verificación integral

- [x] 4.1 Exponer `%decorate_enabled%` (estado personal del jugador) y `%decorate_enabled_global%` (estado global del servidor) en `DecorAteExpansion`, verificando en `PapiPlaceholderTest` que reflejan de forma independiente sus respectivos estados `"true"`/`"false"`.
- [x] 4.2 Ejecutar `./gradlew test` completo en el workspace y confirmar que todas las suites compilan y pasan sin regresiones.
