# Spec Delta

## ADDED Requirements

### Requirement: Visualización de nombre personalizado al mirar un item colocado
The system SHALL render a client-side floating text display with a vanilla nameplate-style background for an observing player only while the player has direct, unoccluded line of sight to a placed item whose item stack has an explicit custom display name component (`CUSTOM_NAME`), and SHALL destroy that text display when the player looks away, changes target, changes world, disconnects, or when the item is removed.

#### Scenario: Item con nombre personalizado al ser mirado directamente
- **WHEN** un jugador apunta dentro del alcance configurado a un item colocado que posee un nombre personalizado explícito (`CUSTOM_NAME`) sin bloques sólidos intermedios
- **THEN** el sistema envía únicamente a ese jugador los paquetes para mostrar un texto flotante con ese nombre formateado, estilo nameplate semitransparente y orientación hacia la cámara del jugador

#### Scenario: Item sin nombre personalizado modificado
- **WHEN** un jugador apunta a un item colocado con su nombre estándar por defecto (sin `CUSTOM_NAME`) o que únicamente posee nombre base de ítem sin modificación de display name
- **THEN** el sistema no envía ningún texto flotante para ese item

#### Scenario: Oclusión por bloques sólidos
- **WHEN** un jugador apunta en dirección a un item colocado con nombre personalizado pero existe un bloque sólido entre la vista del jugador y el item
- **THEN** el sistema no muestra el texto flotante del item oculto detrás del bloque

#### Scenario: Cambio de objetivo, pérdida de mirada o eliminación del item
- **WHEN** el jugador deja de apuntar al item, apunta a otro item distinto, cambia de mundo, se desconecta o el item es recogido o destruido mientras lo miraba
- **THEN** el sistema destruye inmediatamente la entidad virtual de texto activa en el cliente de ese jugador (y la reemplaza si apunta a otro item con nombre personalizado)

#### Scenario: Posicionamiento legible según la cara de soporte
- **WHEN** un jugador mira un item con nombre personalizado colocado sobre el suelo, una pared o el techo
- **THEN** el texto flotante se posiciona desplazado hacia arriba sobre suelo y paredes, y desplazado hacia abajo en techos para evitar quedar incrustado dentro del bloque de soporte
