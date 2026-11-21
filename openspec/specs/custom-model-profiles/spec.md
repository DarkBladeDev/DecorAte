# custom-model-profiles Specification

## Purpose
Provee soporte de Nivel 2 para items con modelos custom de Minecraft 1.21.8 (`CustomModelData` e `ItemModelComponent`) mediante perfiles YAML extensibles y puntos de extensión en la API, permitiendo validar colocabilidad, aplicar transformaciones visuales por defecto y generar items custom desde plantillas.

## Requirements

### Requirement: Preservación íntegra de componentes de modelo custom
The system SHALL preserve all item data components—including `ItemModelComponent`, composite `CustomModelData` values (`floats`, `flags`, `strings`, `colors`), display name, lore, and custom tags—when placing, rendering, persisting, and returning items.

#### Scenario: Item con ItemModelComponent y CustomModelData compuesto
- **WHEN** un jugador coloca un item que posee un `item_model` con namespace propio y campos en `custom_model_data` y posteriormente lo recoge
- **THEN** el display muestra el modelo custom exacto en el mundo y el item devuelto conserva idénticos todos sus componentes de datos

### Requirement: Resolución jerárquica de perfiles YAML extensibles
The system SHALL resolve a placement profile for any held or placed item using configurable YAML definitions ordered by specificity (`item_model`, `custom_model_data`, base material, and global fallback) and allow external code to register custom matchers or profile sources.

#### Scenario: Coincidencia por item_model con reglas de transformación
- **WHEN** existe un perfil YAML que coincide con `item_model: "decor:taza_cafe"` con escala `[0.75, 0.75, 0.75]`, `display_transform: FIXED` y caras permitidas `[UP]`
- **THEN** al colocar ese item sobre la cara superior de un bloque se aplican automáticamente dicha escala y transformación, y se rechaza colocarlo en paredes o techos

#### Scenario: Coincidencia por CustomModelData compuesto o legado
- **WHEN** un perfil YAML especifica un criterio sobre `custom_model_data` (por ejemplo un valor numérico/float, cadena o flag)
- **THEN** el resolutor empareja únicamente los items cuyos componentes `custom_model_data` satisfacen el criterio configurado

#### Scenario: Lista negra o blanca de modelos colocables
- **WHEN** un perfil resuelto marca `placeable: false` para un modelo o material específico
- **THEN** el sistema impide su colocación y comunica al jugador que ese item no puede colocarse como decoración

### Requirement: Plantillas de creación de items en perfiles YAML
The system SHALL allow YAML profiles to define an item template (base material, display name, lore, `item_model`, and `custom_model_data`) so custom decorative items can be instantiated by identifier.

#### Scenario: Recarga en caliente de perfiles YAML
- **WHEN** un administrador modifica o añade un archivo de perfil en el directorio de perfiles y ejecuta la recarga del plugin
- **THEN** los nuevos perfiles y reglas quedan activos sin reiniciar el servidor
