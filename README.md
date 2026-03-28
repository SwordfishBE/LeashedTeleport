## 🪢 Leashed Teleport

A Fabric mod for Minecraft that brings your leashed entities along when you teleport via `/tp`.

Compatible with [Essential Commands](https://modrinth.com/mod/essential-commands) and [Fabric Essentials](https://modrinth.com/mod/melius-essentials) — any mod that uses `/tp` under the hood will trigger leash teleportation automatically.

---

## ✨ Features

- **Leash-based teleportation** — any mob leashed directly to you and within the configured radius teleports with you when you use `/tp`.
- **Intelligent safety checks** — the mod automatically detects unsafe destinations (lava, fire, void, suffocation hazards) and finds a nearby safe location within a 5-block radius. If no safe spot is found, the teleport is cancelled to prevent mob death.
- **Fall damage protection** — teleported entities receive Damage Resistance V and Slow Falling for a configurable duration to survive the landing.
- **Tame ownership check** — tamed animals (dogs, cats, horses, etc.) only teleport if they are owned by the teleporting player. Animals tamed by other players are left behind.
- **Sitting entities supported** — sitting tamed animals teleport as long as they are leashed to and owned by the teleporting player.
- **Cross-dimension support** — entities can optionally follow you across dimensions (Overworld ↔ Nether ↔ End). Enabled by default; toggleable in config.
- **Optional LuckPerms integration** — enable permission nodes for general use and cross-dimension teleports when LuckPerms is installed.
- **World border aware** — if the destination is outside the world border, leashed entities are not teleported.
- **Configurable blacklist** — define entity types that should never teleport, even if leashed to you.
- **In-game commands** — `/leashedteleport info` and `/leashedteleport reload` (operator level 2 required).

---

## 🔄 Commands

| Command                      | Description                                        |
|------------------------------|----------------------------------------------------|
| `/leashedteleport info`      | OP only. Display the current config values and LuckPerms status |
| `/leashedteleport reload`    | OP only. Reload the config from disk without restart |

---

## ⚙️ Configuration

Config file: `.minecraft/config/leashedteleport.json`

A default config is created automatically on first launch.

```json
{
  "leash_radius": 10.0,
  "useLuckPerms": false,
  "cross_dimension_teleport": true,
  "damage_resistance_duration_ticks": 100,
  "entity_blacklist": []
}
```

| Field                              | Type    | Default | Description                                                                                   |
|------------------------------------|---------|---------|-----------------------------------------------------------------------------------------------|
| `leash_radius`                     | double  | `10.0`  | Maximum distance in blocks from the player. Mobs further away are not teleported.            |
| `useLuckPerms`                     | boolean | `false` | When `true`, Leashed Teleport checks LuckPerms permission nodes if LuckPerms is installed.   |
| `cross_dimension_teleport`         | boolean | `true`  | Allow mobs to follow across dimensions. Set to `false` to restrict to the current dimension. |
| `damage_resistance_duration_ticks` | int     | `100`   | Duration of Damage Resistance V + Slow Falling after teleport (20 ticks = 1 second).         |
| `entity_blacklist`                 | list    | `[]`    | Entity type IDs that will never teleport. See example below.                                  |

### Blacklist Example

Use the blacklist to exclude specific mobs — for example animals that are too large or too dangerous to bring along:

```json
{
  "leash_radius": 15.0,
  "useLuckPerms": true,
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

## 🔐 LuckPerms

Leashed Teleport only uses LuckPerms when both conditions are true:

1. `useLuckPerms` is set to `true` in `config/leashedteleport.json`
2. The `luckperms` mod is actually installed on the server

If either condition is false, everyone can use Leashed Teleport and no permission plugin is required.
If LuckPerms is active, missing permission nodes default to `false`.

### Permission nodes

| Permission | Description |
|---|---|
| `leashedteleport.use` | Allow a player to bring leashed mobs along during teleports |
| `leashedteleport.crossdimensionteleport` | Allow a player to bring leashed mobs across dimensions |

### Example groups

```yaml
group.default:
  permissions:
    - leashedteleport.use

group.vip:
  permissions:
    - leashedteleport.use
    - leashedteleport.crossdimensionteleport
```

### LuckPerms quick start

```bash
/lp group default permission set leashedteleport.use true
/lp group default permission set leashedteleport.crossdimensionteleport false

/lp group vip permission set leashedteleport.use true
/lp group vip permission set leashedteleport.crossdimensionteleport true
```

Official LuckPerms docs:

- [LuckPerms Wiki](https://luckperms.net/wiki/Home)
- [LuckPerms Command Usage](https://luckperms.net/wiki/Command-Usage)

---

## 🧑‍🤝‍🧑 Compatibility

| Mod | Status |
|-----|--------|
| [Essential Commands](https://modrinth.com/mod/essential-commands) | ✅ Compatible |
| [Fabric Essentials](https://modrinth.com/mod/melius-essentials) | ✅ Compatible |
| [TpWithMe](https://github.com/SwordfishBE/TpWithMe) | ✅ Compatible |

---

## 🧾 Notes

- Only `/tp`-based teleportation is supported. Portals do not trigger leash teleportation.
- For cross-dimension teleports, the leash is re-attached as soon as the entity is loaded in the new dimension.
- Protection effects are applied silently.
- The safety system checks for hazards like lava, fire, void, and suffocation. If the exact teleport destination is unsafe, the mod searches within a 5-block radius for a safe alternative. This prevents accidental mob deaths from teleporting into dangerous locations.
- The mod must be installed server-side. Players do not need to install the mod on their client. This mod does work in single-player.
- `/leashedteleport info` is operator-only and shows whether LuckPerms is `disabled`, `configured, but mod not installed`, or `active`.

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in `mods/`.
3. Download `leashedteleport-<version>.jar` and place it in `mods/`.
4. Optional: install [LuckPerms](https://modrinth.com/mod/luckperms) if you want permission-based access control.
5. Launch Minecraft. The config is created automatically on first run.

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

## 📄 License

Released under the [AGPL-3.0 License](LICENSE).
