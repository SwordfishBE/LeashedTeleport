# Leashed Teleport

A Fabric mod for Minecraft that brings your leashed entities along when you teleport via `/tp`.

Compatible with [Essential Commands](https://modrinth.com/mod/essential-commands) and [Fabric Essentials](https://modrinth.com/mod/melius-essentials) — any mod that uses `/tp` under the hood will trigger leash teleportation automatically.

---

## Features

- **Leash-based teleportation** — any mob leashed directly to you and within the configured radius teleports with you when you use `/tp`.
- **Fall damage protection** — teleported entities receive Damage Resistance V and Slow Falling for a configurable duration to survive the landing.
- **Tame ownership check** — tamed animals (dogs, cats, horses, etc.) only teleport if they are owned by the teleporting player. Animals tamed by other players are left behind.
- **Sitting entities supported** — sitting tamed animals teleport as long as they are leashed to and owned by the teleporting player.
- **Cross-dimension support** — entities can optionally follow you across dimensions (Overworld ↔ Nether ↔ End). Enabled by default; toggleable in config.
- **World border aware** — if the destination is outside the world border, leashed entities are not teleported.
- **Configurable blacklist** — define entity types that should never teleport, even if leashed to you.
- **In-game commands** — `/leashedteleport info` and `/leashedteleport reload` (operator level 2 required).

---

## Commands

| Command                      | Description                                        |
|------------------------------|----------------------------------------------------|
| `/leashedteleport info`      | Display the current config values                  |
| `/leashedteleport reload`    | Reload the config from disk without restart        |

---

## Configuration

Config file: `.minecraft/config/leashedteleport.json`

A default config is created automatically on first launch.

```json
{
  "leash_radius": 10.0,
  "cross_dimension_teleport": true,
  "damage_resistance_duration_ticks": 100,
  "entity_blacklist": []
}
```

| Field                              | Type    | Default | Description                                                                                   |
|------------------------------------|---------|---------|-----------------------------------------------------------------------------------------------|
| `leash_radius`                     | double  | `10.0`  | Maximum distance in blocks from the player. Mobs further away are not teleported.            |
| `cross_dimension_teleport`         | boolean | `true`  | Allow mobs to follow across dimensions. Set to `false` to restrict to the current dimension. |
| `damage_resistance_duration_ticks` | int     | `100`   | Duration of Damage Resistance V + Slow Falling after teleport (20 ticks = 1 second).         |
| `entity_blacklist`                 | list    | `[]`    | Entity type IDs that will never teleport. See example below.                                  |

### Blacklist Example

Use the blacklist to exclude specific mobs — for example animals that are too large or too dangerous to bring along:

```json
{
  "leash_radius": 15.0,
  "cross_dimension_teleport": true,
  "damage_resistance_duration_ticks": 100,
  "entity_blacklist": [
    "minecraft:cow",
    "minecraft:pig",
    "minecraft:bee",
    "minecraft:horse",
    "minecraft:llama"
  ]
}
```

> **Note:** Only mobs that can actually be leashed will ever teleport. You don't need to blacklist mobs that cannot be leashed in vanilla Minecraft (such as most hostile mobs, villagers, etc.).

---

## Compatibility

| Mod | Status |
|-----|--------|
| [Essential Commands](https://modrinth.com/mod/essential-commands) | ✅ Compatible |
| [Fabric Essentials](https://modrinth.com/mod/melius-essentials) | ✅ Compatible |

---

## Notes

- Only `/tp`-based teleportation is supported. Portals do not trigger leash teleportation.
- For cross-dimension teleports, the leash is re-attached as soon as the entity is loaded in the new dimension.
- Protection effects are applied silently (no particles, no HUD indicator).

---

## 🧱 Building from Source

```bash
git clone https://github.com/SwordfishBE/LeashedTeleport.git
cd LeashedTeleport
chmod +x gradlew
./gradlew build
# Output: build/libs/leashedteleport-<version>.jar
```

---

## License

Released under the [AGPL-3.0 License](LICENSE).
