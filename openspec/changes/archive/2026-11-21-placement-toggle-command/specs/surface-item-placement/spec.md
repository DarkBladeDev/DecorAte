# Spec Delta

## ADDED Requirements

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
