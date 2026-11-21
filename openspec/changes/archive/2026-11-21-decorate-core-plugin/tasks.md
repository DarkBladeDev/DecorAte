# Tasks

## 1. Gradle Multi-Module & Build Setup

- [x] 1.1 Initialize the root Gradle Kotlin DSL build (`settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, Gradle wrapper) targeting Java 21 with subprojects `decorate-api`, `decorate-core`, `decorate-packet`, `decorate-hooks`, and `decorate-paper`, and verify `./gradlew projects` resolves all five modules.
- [x] 1.2 Configure repositories and dependencies in `gradle/libs.versions.toml` and module build scripts for Paper 1.21.8 (`paper-api`), PacketEvents `2.14.0`, PlaceholderAPI `2.12.3`, Incendo Cloud Paper `2.0.1` + Cloud Annotations `2.1.0`, WorldGuard `7.0.14`, SQLite JDBC, and JUnit 5, and verify `./gradlew compileJava` succeeds on empty module scaffolds.

## 2. Public API Contracts (`decorate-api`)

- [x] 2.1 Define immutable domain models (`PlacedItem`, `SurfacePose`, `PlacementMode`, `PlacementProfile`, `ItemTemplate`, `BlockCoordinate`, `ChunkCoordinate`) and cancellable Bukkit events (`DecorPlaceEvent`, `DecorInteractEvent`, `DecorRemoveEvent`) with unit tests verifying model invariants and coordinate helpers (`./gradlew :decorate-api:test`).
- [x] 2.2 Define extensible service and SPI contracts (`StorageProvider`, `ProfileMatcher`, `ProfileSource`, `PlacementProfileResolver`, `ProtectionHook`, `PlaceholderService`, `DisplayRenderer`) with Javadocs documenting lifecycle, thread-safety expectations, and extension points (`./gradlew :decorate-api:javadoc`).

## 3. Core Geometry, Spatial Registry & YAML Profiles (`decorate-core`)

- [x] 3.1 Implement `SpatialRegistry` (indexed by world, chunk coordinates, owner UUID, and supporting block coordinates) and placement geometry calculator supporting continuous surface coordinates, optional configurable sub-grid snap quantization, normal offsets, and pose quaternion math, and verify all face projections (`UP`, `DOWN`, `NORTH`, `SOUTH`, `EAST`, `WEST`) and snap rounding with unit tests (`./gradlew :decorate-core:test`).
- [x] 3.2 Implement Level 2 YAML profile loader and `PlacementProfileResolver` supporting specificity ordering (`item_model` exact/wildcard, Paper 1.21.8 composite `custom_model_data`, base `material`, global fallback), `placeable` allow/deny rules, display defaults, and optional `item` templates for instantiation, with unit tests covering profile precedence and hot-reload (`./gradlew :decorate-core:test`).
- [x] 3.3 Implement `PlacementService` orchestrating validation (limits per block/chunk/player, face restrictions, protection hooks, cancellable events), Survival/Creative inventory semantics, step rotation, pose toggling (`FLAT`/`UPRIGHT`), and player placement mode preferences, verified via mocked unit tests (`./gradlew :decorate-core:test`).

## 4. Asynchronous Persistence (`decorate-core`)

- [x] 4.1 Implement `SqliteStorageProvider` with WAL mode, single-thread write executor, `(world, chunk_x, chunk_z)` and `owner_uuid` indexes, and `ItemStack#serializeAsBytes()` / `deserializeBytes()` round-tripping, plus `ChunkVisibilityCache` lifecycle hooks, and verify CRUD, chunk queries, and restart recovery against a temporary SQLite file in tests (`./gradlew :decorate-core:test`).

## 5. PacketEvents Rendering & Interaction (`decorate-packet`)

- [x] 5.1 Implement `PacketEventsDisplayRenderer` using PacketEvents `2.14.0` (`EntityTypes.ITEM_DISPLAY` + `EntityTypes.INTERACTION`) with virtual entity ID allocation, normal-aware hitbox offset calculation across all block faces, chunk/view-distance observer tracking (`spawn`, `updateMetadata`, `destroy`), and unit tests verifying metadata indices, quaternion/scale transforms, and `INTERACTION` bounding-box offsets (`./gradlew :decorate-packet:test`).
- [x] 5.2 Implement `PacketInteractionListener` intercepting `WrapperPlayClientInteractEntity`, resolving virtual entity IDs in O(1), dispatching `ATTACK` (pickup), `INTERACT` (rotate), and `INTERACT + sneak` (pose toggle) safely to the server main thread, and verify packet-to-action mapping and thread dispatch in unit tests (`./gradlew :decorate-packet:test`).

## 6. External Integrations (`decorate-hooks`)

- [x] 6.1 Implement `WorldGuardProtectionHook` registering custom flags (`decorate-place`, `decorate-interact`) during `onLoad` with safe `FlagConflictException` handling and fallback to `BUILD`/`INTERACT` region checks, plus unit/mocked tests verifying allow/deny behavior and graceful absence when WorldGuard is not loaded (`./gradlew :decorate-hooks:test`).
- [x] 6.2 Implement `PapiPlaceholderService` (with no-op fallback) and `DecorAteExpansion` (`2.12.3`) exposing `%decorate_count%`, `%decorate_world_count%`, `%decorate_limit%`, `%decorate_remaining%`, `%decorate_chunk_count%`, `%decorate_looking_model%`, and `%decorate_looking_owner%`, verified with unit tests covering both registered values and no-op pass-through (`./gradlew :decorate-hooks:test`).

## 7. Paper Bootstrap, Listeners, Config & Cloud Commands (`decorate-paper`)

- [x] 7.1 Implement `DecorAtePlugin` (`onLoad` WorldGuard hook registration, `onEnable` wiring, `onDisable` async storage flush), `plugin.yml` (`depend: [packetevents]`, `softdepend: [PlaceholderAPI, WorldGuard]`), default `config.yml`, `messages.yml`, and example `profiles/example.yml` documenting YAML custom model schema and MiniMessage/PAPI usage (`./gradlew :decorate-paper:processResources`).
- [x] 7.2 Implement Paper listeners for configurable placement trigger (`Shift + Right Click` raycast), chunk load/unload, player join/quit/world-change/move visibility tracking, and supporting-block integrity (`BlockBreakEvent`, `BlockExplodeEvent`, `EntityExplodeEvent`, piston extend/retract with configurable auto-cleanup/drop), verified via listener unit tests (`./gradlew :decorate-paper:test`).
- [x] 7.3 Implement `/decorate` (`/dec`) command suite using Incendo Cloud Paper `2.0.1` and Cloud Annotations `2.1.0` (`reload`, `mode`, `snap`, `info`, `clear`, and `give <profileId> [player] [amount]` with custom item template synthesis and tab-completion), verified with Cloud command parsing/execution unit tests (`./gradlew :decorate-paper:test`).

## 8. Build Packaging & Verification

- [x] 8.1 Configure Shadow JAR packaging in `decorate-paper` (relocating bundled non-provided dependencies without breaking PacketEvents/Paper/PAPI/WorldGuard linkage), run the full Gradle build and test suite (`./gradlew clean build`), and run `openspec validate decorate-core-plugin --strict` to confirm all checks pass.
