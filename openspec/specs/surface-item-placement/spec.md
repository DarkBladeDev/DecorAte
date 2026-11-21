# surface-item-placement Specification

## Purpose
Permite a los jugadores colocar múltiples items desde su mano en puntos exactos de cualquier superficie de bloque sin la restricción de una entidad por bloque, con soporte de snapping opcional, modos Supervivencia y Creativo, límites configurables e integridad frente a cambios en el bloque de soporte.

## Requirements

### Requirement: Colocación en superficie sin restricción de una entidad por bloque
The system SHALL allow players to place the item held in their main hand onto any solid block surface at the exact raycast hit position without restricting placement to one item per block.

#### Scenario: Colocación múltiple sobre un mismo bloque
- **WHEN** un jugador ejecuta el trigger de colocación (`Shift + Clic derecho` por defecto) sosteniendo un item colocable mientras apunta a un punto de la cara superior de una mesa que ya contiene otros items colocados
- **THEN** el sistema coloca el nuevo item en el punto exacto impactado sin reemplazar ni bloquear los items existentes en ese bloque

#### Scenario: Validación de límite por bloque y por chunk
- **WHEN** un jugador intenta colocar un item en un bloque o chunk que ya alcanzó el máximo configurado de items permitidos
- **THEN** el sistema cancela la colocación y notifica al jugador mediante un mensaje configurable sin consumir el item

### Requirement: Posicionamiento continuo y sub-grid snap opcional
The system SHALL default to continuous free placement on the targeted surface face and SHALL support an optional configurable sub-grid snap mode per player.

#### Scenario: Colocación en modo libre continuo
- **WHEN** el jugador tiene activo el modo libre y coloca un item sobre la cara de un bloque
- **THEN** la posición almacenada del item coincide con las coordenadas continuas del punto de impacto proyectado sobre esa cara

#### Scenario: Colocación con sub-grid snap activo
- **WHEN** el jugador activa el modo sub-grid snap (por ejemplo con granularidad `1/16` o `1/8` de bloque) y coloca un item
- **THEN** las coordenadas tangentes a la cara del bloque se redondean al punto de sub-grilla más cercano manteniendo la coordenada normal apoyada sobre la superficie

### Requirement: Manejo de inventario en Supervivencia y Creativo
The system SHALL consume one item from the player's hand on placement and return the exact item on removal in Survival or Adventure mode, and SHALL preserve the held stack in Creative mode.

#### Scenario: Colocación y recogida en modo Supervivencia
- **WHEN** un jugador en modo Supervivencia coloca un item con un stack de 5 unidades y luego lo recoge
- **THEN** el stack en su mano se reduce a 4 al colocarlo y al recogerlo recupera exactamente 1 unidad con sus metadatos y componentes intactos

#### Scenario: Colocación y recogida en modo Creativo
- **WHEN** un jugador en modo Creativo coloca un item y posteriormente lo recoge
- **THEN** el stack en la mano no se consume al colocar y al recogerlo la decoración se elimina sin duplicar recompensas en el inventario (salvo que la configuración indique lo contrario)

### Requirement: Integridad del bloque de soporte
The system SHALL track each placed item's supporting block and execute the configured cleanup policy when that block is broken, exploded, or moved.

#### Scenario: Ruptura del bloque de soporte con auto-cleanup activo
- **WHEN** la política de auto-cleanup está habilitada (valor por defecto) y el bloque de soporte es destruido por un jugador, explosión o pistón
- **THEN** el sistema elimina todos los items colocados asociados a ese bloque y los suelta en el mundo cuando correspondan a colocaciones de Supervivencia

#### Scenario: Ruptura del bloque de soporte con modo flotante configurado
- **WHEN** la política de soporte está configurada para permitir items flotantes y el bloque de soporte es destruido
- **THEN** los items colocados permanecen visibles e interactuables en sus coordenadas originales

### Requirement: Trigger y alcance configurables
The system SHALL allow server administrators to configure the placement trigger combination, maximum raycast distance, and per-player placement limits.

#### Scenario: Distancia fuera de alcance
- **WHEN** un jugador ejecuta el trigger de colocación apuntando a un bloque más allá de la distancia máxima configurada
- **THEN** el sistema ignora la acción de colocación sin consumir el item ni mostrar errores

### Requirement: Control de activación por jugador y global
The system SHALL support both a per-player session placement toggle (enabled by default) and a configurable server-wide toggle (initialized from `placement.enabled`, default `true`, and re-synchronized on configuration reload), allowing vanilla block interactions to proceed uncancelled when placement is disabled and disabling both placement and item interaction without bypass when the global toggle is off.

#### Scenario: Colocación deshabilitada a nivel personal permite interacción vanilla y mantiene interacción con decoraciones
- **WHEN** un jugador tiene su toggle personal en `off` (con el estado global en `on`) y ejecuta el trigger de colocación (`Shift + Clic derecho`) sobre un bloque sosteniendo un item
- **THEN** el sistema ignora el trigger de colocación sin cancelar el evento ni consumir el item, permitiendo que se ejecute el comportamiento vanilla normal, mientras el jugador sigue pudiendo recoger, rotar o cambiar la pose de items ya colocados

#### Scenario: Estado global deshabilitado bloquea colocación sin cancelar vanilla
- **WHEN** el estado global del servidor está en `off` y cualquier jugador ejecuta el trigger de colocación (`Shift + Clic derecho`) sobre un bloque
- **THEN** el sistema ignora el trigger de colocación sin cancelar el evento ni consumir el item, permitiendo el comportamiento vanilla y sin permisos de bypass

#### Scenario: Estado global deshabilitado bloquea interacción sobre items ya colocados
- **WHEN** el estado global del servidor está en `off` y un jugador intenta recoger, rotar o cambiar la pose de un item colocado existente
- **THEN** el sistema cancela el paquete de interacción contra la entidad virtual sin mutar ni eliminar el item, mantiene visibles los displays existentes y notifica al jugador con el mensaje configurable de mecánica deshabilitada

#### Scenario: Reinicio de sesión personal al desconectarse y recarga global
- **WHEN** un jugador con el toggle personal en `off` se desconecta del servidor y vuelve a conectarse, o un administrador ejecuta `/decorate reload`
- **THEN** el jugador reconectado inicia con su colocación personal habilitada por defecto y `/decorate reload` re-sincroniza el estado global con `placement.enabled` en `config.yml`
