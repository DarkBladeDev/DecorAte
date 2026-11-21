# Spec Delta

## Purpose

Asegura que la colocación y edición de items respete los permisos de zona y eventos cancelables del ecosistema Paper/Bukkit, integrando una interfaz extensible de protección con soporte incluido para WorldGuard.

## ADDED Requirements

### Requirement: Eventos cancelables y hooks extensibles de protección
The system SHALL emit cancellable domain events before placing, rotating/editing, or removing an item, and SHALL evaluate all registered protection hooks before mutating state.

#### Scenario: Cancelación por evento o hook externo
- **WHEN** un listener externo cancela el evento de colocación o interacción, o un hook de protección deniega la acción en esa ubicación
- **THEN** el sistema aborta la operación, mantiene el estado previo sin cambios y no modifica el inventario del jugador

### Requirement: Integración nativa con WorldGuard
The system SHALL integrate with WorldGuard when present by registering custom region flags during plugin load and verifying region permissions before allowing placement, rotation, pose changes, or pickup.

#### Scenario: Registro temprano de flags custom de WorldGuard
- **WHEN** WorldGuard está instalado en el servidor y el plugin ejecuta su fase de carga previa a `onEnable`
- **THEN** las flags custom de DecorAte quedan registradas en el registro de flags de WorldGuard sin errores de registro tardío

#### Scenario: Bloqueo en región protegida de WorldGuard
- **WHEN** un jugador sin permisos de construcción o sin la flag permitida en la región intenta colocar, rotar o recoger un item dentro de una región de WorldGuard
- **THEN** el sistema deniega la acción y notifica al jugador que no tiene permisos en esa zona

#### Scenario: Ejecución sin WorldGuard instalado
- **WHEN** WorldGuard no está presente en el servidor
- **THEN** el plugin inicia con normalidad desactivando únicamente el hook de WorldGuard sin errores ni dependencias rotas
