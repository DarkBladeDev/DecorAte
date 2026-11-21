# Spec Delta

## ADDED Requirements

### Requirement: Activación y desactivación de la mecánica de colocación por comandos
The system SHALL expose `/decorate toggle [on|off]` (alias `/dec toggle [on|off]`) for players to toggle their personal surface placement state and `/decorate global [on|off|toggle]` (alias `/dec global [on|off|toggle]`) for administrators to toggle the server-wide placement and interaction state, each gated by its own permission and confirming the resulting state via configurable messages.

#### Scenario: Alternar estado personal sin argumento
- **WHEN** un jugador con el permiso `decorate.command.toggle` ejecuta `/decorate toggle` mientras su colocación personal está habilitada
- **THEN** el sistema deshabilita la colocación personal para ese jugador en la sesión actual y le envía el mensaje de confirmación indicando el nuevo estado deshabilitado

#### Scenario: Establecer estado personal explícito con on u off
- **WHEN** un jugador con el permiso `decorate.command.toggle` ejecuta `/decorate toggle on` o `/decorate toggle off`
- **THEN** el sistema fija su estado personal al valor indicado y confirma el estado resultante sin alterar su preferencia de `FREE` o `SNAP`

#### Scenario: Rechazar /decorate toggle desde consola
- **WHEN** la consola ejecuta `/decorate toggle`
- **THEN** el sistema rechaza la operación con el mensaje `command.player-only`

#### Scenario: Alternar o fijar el estado global administrativo
- **WHEN** un jugador o la consola con el permiso `decorate.command.global` ejecuta `/decorate global off`, `/decorate global on` o `/decorate global` (o `/decorate global toggle`)
- **THEN** el sistema actualiza el estado global del servidor al valor indicado (o alterna el actual si no se especifica o se indica `toggle`) y responde con el mensaje de confirmación del nuevo estado global
