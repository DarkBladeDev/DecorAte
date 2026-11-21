# Spec Delta

## ADDED Requirements

### Requirement: Exposición de placeholders de estado de activación
The system SHALL expose `%decorate_enabled%` and `%decorate_enabled_global%` under the `decorate` PlaceholderAPI expansion identifier to report the player's personal placement toggle state and the server-wide global state as `"true"` or `"false"`.

#### Scenario: Consulta del estado personal con %decorate_enabled%
- **WHEN** un sistema externo solicita `%decorate_enabled%` para un jugador conectado que tiene deshabilitada su colocación personal mientras el estado global está activo
- **THEN** la expansión devuelve `"false"` reflejando el estado personal independiente del jugador (y `"true"` cuando el toggle personal está activo)

#### Scenario: Consulta del estado global con %decorate_enabled_global%
- **WHEN** un sistema externo solicita `%decorate_enabled_global%` mientras el modo global está desactivado o activado
- **THEN** la expansión devuelve `"false"` o `"true"` respectivamente según el estado global vigente en memoria
