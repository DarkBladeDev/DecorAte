# placement-persistence Specification

## Purpose
Garantiza que todos los items colocados persistan entre reinicios del servidor y cargas/descargas de chunks de forma asíncrona mediante una interfaz extensible de almacenamiento con backend SQLite por defecto.

## Requirements

### Requirement: Persistencia asíncrona indexada por chunk
The system SHALL persist placed items asynchronously indexed by world and chunk coordinates so that chunk loading, unloading, and server restarts never block the main server thread.

#### Scenario: Persistencia entre reinicios del servidor
- **WHEN** un jugador coloca items en un mundo y el servidor se detiene limpiamente y vuelve a iniciarse
- **THEN** al cargarse el chunk correspondiente los items reaparecen exactamente con su posición, rotación, escala, dueño e `ItemStack` original

#### Scenario: Descarga y recarga de chunk en ejecución
- **WHEN** todos los jugadores abandonan un chunk y este se descarga, y posteriormente un jugador regresa
- **THEN** el sistema libera de memoria los displays si aplica según la estrategia de caché y vuelve a renderizarlos desde el almacenamiento persistente al recargar el chunk

### Requirement: Contrato extensible de proveedor de almacenamiento
The system SHALL expose an extensible storage provider interface in the public API and bundle an embedded SQLite implementation as the default backend.

#### Scenario: Reemplazo de backend mediante API
- **WHEN** un módulo externo o addon registra una implementación propia del proveedor de almacenamiento durante la inicialización
- **THEN** el núcleo delega todas las operaciones de lectura, escritura, actualización y borrado en el proveedor registrado sin cambios en la lógica de dominio
