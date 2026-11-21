# Spec Delta

## Purpose

Ofrece una interfaz de comandos clara y con autocompletado bajo `/decorate` (`/dec`) para recargar configuraciones, alternar el modo de colocación, inspeccionar decoraciones, limpiar zonas y entregar items custom definidos en perfiles YAML.

## ADDED Requirements

### Requirement: Gestión de configuración, modo e inspección por comandos
The system SHALL expose `/decorate` (alias `/dec`) subcommands for reloading configuration, toggling per-player placement snap mode, inspecting targeted placements, and clearing placed items within a radius or chunk, each gated by granular permissions.

#### Scenario: Recarga en caliente con /decorate reload
- **WHEN** un usuario con el permiso correspondiente ejecuta `/decorate reload`
- **THEN** el sistema recarga la configuración principal, mensajes y perfiles YAML de modelos y reporta cuántos perfiles se cargaron

#### Scenario: Alternar modo libre y sub-grid snap
- **WHEN** un jugador ejecuta `/decorate mode snap` o `/decorate mode free` (o `/decorate snap <fraccion>`)
- **THEN** el sistema actualiza la preferencia de colocación de ese jugador y confirma el nuevo estado

#### Scenario: Inspeccionar item apuntado con /decorate info
- **WHEN** un jugador ejecuta `/decorate info` mientras apunta a la hitbox de un item colocado
- **THEN** el sistema muestra los detalles del item (perfil resuelto, `item_model` o `custom_model_data`, pose, rotación, coordenadas y dueño)

#### Scenario: Limpieza administrativa con /decorate clear
- **WHEN** un administrador ejecuta `/decorate clear <radio>` o `/decorate clear chunk`
- **THEN** el sistema elimina los items colocados en el área indicada, destruye sus paquetes visibles para los observadores y reporta la cantidad removida

### Requirement: Entrega de items custom con /decorate give
The system SHALL provide `/decorate give <profileId> [player] [amount]` with tab-completion of defined custom item profiles to spawn configured custom items into a player's inventory.

#### Scenario: Entrega exitosa de item custom a un jugador
- **WHEN** un administrador ejecuta `/decorate give taza_cafe Steve 2` y el perfil `taza_cafe` define una plantilla de item
- **THEN** el sistema construye el `ItemStack` con su material, nombre, lore, `item_model` y/o `custom_model_data` configurados y lo entrega al jugador objetivo

#### Scenario: Perfil inexistente o sin plantilla de item
- **WHEN** se ejecuta `/decorate give` con un identificador inexistente o un perfil que no define plantilla de item
- **THEN** el sistema rechaza la operación con un mensaje descriptivo sin alterar inventarios
