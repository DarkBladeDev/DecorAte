# placeholderapi-integration Specification

## Purpose
Expone estadísticas y contexto en tiempo real de DecorAte hacia PlaceholderAPI 2.12.3 y provee un servicio desacoplado para resolver placeholders externos en mensajes, actionbars y vistas de inspección del plugin.

## Requirements

### Requirement: Exposición de placeholders del plugin
The system SHALL register a PlaceholderAPI expansion when PlaceholderAPI is available, exposing player, chunk, and targeted-item variables under the `decorate` identifier.

#### Scenario: Placeholders de conteo y límites del jugador
- **WHEN** un sistema externo solicita `%decorate_count%`, `%decorate_world_count%`, `%decorate_limit%`, `%decorate_remaining%` o `%decorate_chunk_count%` para un jugador conectado
- **THEN** la expansión devuelve los valores numéricos actualizados correspondientes al jugador, su límite efectivo y el chunk donde se encuentra

#### Scenario: Placeholders del item apuntado
- **WHEN** un jugador está mirando la hitbox de un item colocado y se evalúan `%decorate_looking_model%` o `%decorate_looking_owner%`
- **THEN** la expansión devuelve el identificador de modelo/material y el nombre del propietario del item apuntado, o una cadena vacía/por defecto configurada si no apunta a ninguno

### Requirement: Servicio desacoplado de parsing de placeholders
The system SHALL provide a placeholder parsing service that resolves PlaceholderAPI placeholders in plugin messages, actionbars, and inspection outputs when PlaceholderAPI is installed, and degrades gracefully to pass-through text when absent.

#### Scenario: Parsing con PlaceholderAPI presente
- **WHEN** un mensaje configurado o texto de inspección contiene placeholders externos (por ejemplo `%player_name%` o `%decorate_remaining%`) y PlaceholderAPI está activo
- **THEN** el servicio resuelve todos los placeholders antes de enviar el texto al jugador

#### Scenario: Funcionamiento sin PlaceholderAPI
- **WHEN** PlaceholderAPI no está instalado en el servidor
- **THEN** el plugin arranca normalmente usando la implementación no-op del servicio sin lanzar excepciones

### Requirement: Exposición de placeholders de estado de activación
The system SHALL expose `%decorate_enabled%` and `%decorate_enabled_global%` under the `decorate` PlaceholderAPI expansion identifier to report the player's personal placement toggle state and the server-wide global state as `"true"` or `"false"`.

#### Scenario: Consulta del estado personal con %decorate_enabled%
- **WHEN** un sistema externo solicita `%decorate_enabled%` para un jugador conectado que tiene deshabilitada su colocación personal mientras el estado global está activo
- **THEN** la expansión devuelve `"false"` reflejando el estado personal independiente del jugador (y `"true"` cuando el toggle personal está activo)

#### Scenario: Consulta del estado global con %decorate_enabled_global%
- **WHEN** un sistema externo solicita `%decorate_enabled_global%` mientras el modo global está desactivado o activado
- **THEN** la expansión devuelve `"false"` o `"true"` respectivamente según el estado global vigente en memoria
