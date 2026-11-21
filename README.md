# DecorAte

[![Paper](https://img.shields.io/badge/Paper-1.21.8-blue)](#requirements)
[![Java](https://img.shields.io/badge/Java-21-orange)](#requirements)
[![PacketEvents](https://img.shields.io/badge/PacketEvents-2.14.0-green)](#requirements)
[![License: GPL v3+](https://img.shields.io/badge/License-GPLv3+-blue.svg)](LICENSE)

Modular surface item-placement plugin for **Paper 1.21.8**. Place multiple items anywhere on floors, walls, and ceilings using client-side `ItemDisplay` and `Interaction` packets—without spawning persistent server entities.

## Features

- **Packet-based rendering** — Client-side `ItemDisplay` + aligned `Interaction` hitboxes via PacketEvents, streamed by chunk radius.
- **Arbitrary surface placement** — Place multiple items on the same block in `FREE` (continuous raycast) or `SNAP` (sub-grid `1–64`, default `16`) mode.
- **Direct interactions** — `Shift + Right Click` to place or toggle pose (`FLAT` / `UPRIGHT`), `Right Click` to rotate (45° default), `Left Click` to pick up.
- **1.21.8 Custom Model Profiles** — YAML profiles (`profiles/*.yml`) matching `item_model` and composite `custom_model_data` (`floats`, `flags`, `strings`, `colors`), per-face rules, transforms, and `/dec give` templates.
- **Async SQLite persistence** — Chunk-indexed storage with support-block auto-cleanup (break, explosion, piston) or floating mode.
- **Integrations** — Optional **WorldGuard** flags (`decorate-place`, `decorate-interact`) and **PlaceholderAPI** expansion (`%decorate_*%`).

## Requirements

- **Server:** Paper `1.21.8` (Java `21`)
- **Required:** [PacketEvents](https://modrinth.com/plugin/packetevents) (`2.14.0+`)
- **Optional:** [WorldGuard](https://enginehub.org/worldguard) (`7.0.14+`), [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (`2.12.3+`)

> [!NOTE]
> DecorAte registers its WorldGuard custom flags during early plugin load. Restart the server cleanly when installing alongside WorldGuard.

## Quick Reference

<details>
<summary>Commands &amp; Permissions (<code>/decorate</code> / <code>/dec</code>)</summary>

| Command | Description | Permission | Default |
| :--- | :--- | :--- | :--- |
| `/dec mode <free/snap>` | Switch placement mode | `decorate.command.mode` | `true` |
| `/dec snap <divisions>` | Set snap grid (`1–64`) | `decorate.command.mode` | `true` |
| `/dec toggle [on/off]` | Toggle personal placement | `decorate.command.toggle` | `true` |
| `/dec global [on/off/toggle]` | Toggle global placement | `decorate.command.global` | `op` |
| `/dec info` | Inspect targeted decoration | `decorate.command.info` | `true` |
| `/dec give <profile> [player] [amount]` | Give a profile item template | `decorate.command.give` | `op` |
| `/dec clear radius <radius>` | Clear decorations within radius | `decorate.command.clear` | `op` |
| `/dec clear chunk` | Clear decorations in current chunk | `decorate.command.clear` | `op` |
| `/dec reload` | Reload config, messages, profiles | `decorate.command.reload` | `op` |

Additional permissions: `decorate.place` (`true`), `decorate.interact` (`true`), `decorate.limit.<n>`, `decorate.bypass.limit` (`op`), `decorate.bypass.owner` (`op`).

</details>

<details>
<summary>PlaceholderAPI &amp; WorldGuard</summary>

**Placeholders:** `%decorate_count%`, `%decorate_world_count%`, `%decorate_limit%`, `%decorate_remaining%`, `%decorate_chunk_count%`, `%decorate_looking_model%`, `%decorate_looking_owner%`, `%decorate_enabled%`, `%decorate_enabled_global%`.

**WorldGuard Flags:** `decorate-place`, `decorate-interact`.

</details>

## Architecture & Building

| Module | Responsibility |
| :--- | :--- |
| `decorate-api` | Public domain models, events, storage, protection, and profile APIs |
| `decorate-core` | Placement math, snap grid, profile resolution, SQLite storage |
| `decorate-packet` | PacketEvents `ItemDisplay` & `Interaction` packet adapter |
| `decorate-hooks` | WorldGuard flags and PlaceholderAPI expansion |
| `decorate-paper` | Paper plugin bootstrap, listeners, and Cloud commands |

```bash
./gradlew build
```

The shaded plugin JAR is output to `decorate-paper/build/libs/DecorAte-<version>.jar`.

## License

Licensed under the [GNU General Public License v3.0 or later](LICENSE) (`GPL-3.0-or-later`).
