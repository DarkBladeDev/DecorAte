# Spec Delta

## MODIFIED Requirements

### Requirement: Contrato extensible de proveedor de almacenamiento
The system SHALL expose an extensible storage provider interface in the public API and provide a SQLite implementation backed by a JDBC connection pool whose runtime dependencies are resolved externally rather than bundled inside the plugin JAR.

#### Scenario: Reemplazo de backend mediante API
- **WHEN** un módulo externo o addon registra una implementación propia del proveedor de almacenamiento durante la inicialización
- **THEN** el núcleo delega todas las operaciones de lectura, escritura, actualización y borrado en el proveedor registrado sin cambios en la lógica de dominio

#### Scenario: Inicialización del backend SQLite por defecto mediante pool JDBC
- **WHEN** el plugin arranca con la configuración de almacenamiento por defecto tras resolverse las dependencias en tiempo de ejecución
- **THEN** el sistema inicializa el pool de conexiones SQLite en modo WAL sin haber empaquetado el driver SQLite dentro del JAR distribuible del plugin
