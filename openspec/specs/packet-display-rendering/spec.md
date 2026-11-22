# packet-display-rendering Specification

## Purpose
Renderiza los items colocados como displays visuales e hitboxes interactuables enviadas exclusivamente por paquetes al cliente, gestionando su visibilidad por distancia de vista/chunks y procesando acciones directas de recogida, rotación y cambio de pose.

## Requirements

### Requirement: Renderizado por paquetes sin entidades reales del servidor
The system SHALL render each placed item using a client-side item display entity and an aligned client-side interaction hitbox transmitted exclusively via network packets, without spawning persistent server-side entities in the world.

#### Scenario: Visibilidad al entrar en rango de chunk
- **WHEN** un jugador entra en un mundo o se acerca a un chunk cargado dentro de la distancia de renderizado configurada que contiene items colocados
- **THEN** el sistema envía al jugador los paquetes de creación y metadatos del display visual y de su hitbox interactuable alineada

#### Scenario: Limpieza al salir de rango o cambiar de mundo
- **WHEN** un jugador se aleja más allá de la distancia configurada, cambia de mundo o se desconecta
- **THEN** el sistema envía los paquetes de destrucción correspondientes para liberar esas entidades en el cliente del jugador

### Requirement: Alineación de hitbox según cara y escala
The system SHALL offset the client-side interaction hitbox relative to the placement surface normal and item scale so that targeting the visual display reliably hits the interaction hitbox on floors, walls, and ceilings.

#### Scenario: Interacción en pared o techo
- **WHEN** un item está colocado sobre una cara lateral o sobre la cara inferior (techo) de un bloque y el jugador apunta directamente al centro visual del item
- **THEN** el cliente detecta la entidad de interacción asociada permitiendo ejecutar acciones sobre ese item concreto

### Requirement: Acciones directas sobre items colocados
The system SHALL process client entity-interaction packets on placed items to support pickup, step rotation, and pose toggling according to configured permissions and ownership rules.

#### Scenario: Recoger item mediante clic izquierdo
- **WHEN** un jugador autorizado hace clic izquierdo sobre la hitbox de un item colocado
- **THEN** el sistema elimina el item, transmite los paquetes de destrucción a los observadores activos y aplica la devolución correspondiente al modo de juego

#### Scenario: Rotar item mediante clic derecho
- **WHEN** un jugador autorizado hace clic derecho sobre la hitbox de un item colocado
- **THEN** el sistema incrementa el ángulo de rotación del item en el paso configurado (por ejemplo 45 grados) y actualiza en vivo los metadatos para todos los observadores cercanos

#### Scenario: Alternar pose mediante Shift + Clic derecho
- **WHEN** un jugador autorizado realiza `Shift + Clic derecho` sobre la hitbox de un item colocado
- **THEN** el sistema alterna la pose del item (entre apoyado plano sobre la cara de soporte y erguido) y sincroniza la transformación actualizada con todos los observadores cercanos

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
