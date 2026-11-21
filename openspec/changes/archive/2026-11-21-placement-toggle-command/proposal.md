# Proposal

## Why

Actualmente, los jugadores que tienen el permiso `decorate.place` no pueden desactivar temporalmente el trigger de colocación (`Shift + Clic derecho` por defecto), lo que interfiere con acciones vanilla al agacharse (abrir cofres/tolvas o colocar bloques vanilla sobre bloques interactivos). Además, los administradores carecen de un interruptor en caliente para pausar globalmente tanto la colocación como la interacción con decoraciones sin modificar permisos ni reiniciar el servidor.

## What Changes

- Añadir el subcomando personal `/decorate toggle [on|off]` (`/dec toggle [on|off]`) bajo el permiso `decorate.command.toggle` (`default: true`) para activar o desactivar la mecánica de colocación para el propio jugador, manteniendo activa la interacción (`PICKUP`, `ROTATE`, `TOGGLE_POSE`) sobre items ya colocados y dejando pasar el comportamiento vanilla de `Shift + Clic derecho`.
- Añadir el subcomando administrativo `/decorate global [on|off|toggle]` (`/dec global [on|off|toggle]`) bajo el permiso `decorate.command.global` (`default: op`) para activar o desactivar globalmente tanto la colocación como la interacción con items colocados, sin bypass, manteniendo visibles los displays existentes y dejando pasar el comportamiento vanilla al hacer `Shift + Clic derecho`.
- Añadir la clave `placement.enabled` (valor por defecto `true`) en `config.yml` para definir el estado global inicial al arrancar o recargar con `/decorate reload`, manteniendo el estado personal en memoria durante la sesión del jugador.
- Exponer los placeholders `%decorate_enabled%` (estado personal del jugador) y `%decorate_enabled_global%` (estado global del servidor) en la expansión de PlaceholderAPI.

## Capabilities

### New Capabilities
<!-- Ninguna: los cambios amplían capacidades existentes -->

### Modified Capabilities
- `decorate-commands`: incorpora los subcomandos `/decorate toggle [on|off]` y `/decorate global [on|off|toggle]` con sus permisos y mensajes asociados.
- `surface-item-placement`: incorpora la habilitación/deshabilitación por jugador (solo colocación, conservando interacción) y global (inhabilitación total de colocación e interacción sin cancelar el clic vanilla).
- `placeholderapi-integration`: incorpora `%decorate_enabled%` y `%decorate_enabled_global%` en la expansión `decorate`.

## Impact

- **Comandos y configuración (`decorate-paper`)**: `DecorateCommands`, `DecorAtePlugin`, `DecorAteConfigLoader`, `plugin.yml`, `config.yml`, `messages.yml`, `PlacementTriggerListener`, `VisibilityLifecycleListener`.
- **Servicio y configuración core (`decorate-core`)**: `DecorateSettings`, `PlacementService`.
- **Hooks (`decorate-hooks`)**: `DecorAteExpansion`.
- **Pruebas**: tests unitarios en `decorate-core`, `decorate-hooks` y `decorate-paper`.
