## 🪢 Leashed Teleport

A Fabric mod for Minecraft that brings your leashed entities along when you teleport via `/tp`, ender pearls, and chorus fruit.

[![GitHub Release](https://img.shields.io/github/v/release/SwordfishBE/LeashedTeleport?display_name=release&logo=github)](https://github.com/SwordfishBE/LeashedTeleport/releases)
[![GitHub Downloads](https://img.shields.io/github/downloads/SwordfishBE/LeashedTeleport/total?logo=github)](https://github.com/SwordfishBE/LeashedTeleport/releases)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/G12zLjMK?logo=modrinth&logoColor=white&label=Modrinth%20downloads)](https://modrinth.com/mod/leashed-teleport)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1491827?logo=curseforge&logoColor=white&label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/undead-riders)

---

## ✨ Features

- **Leash-based teleportation** — any mob leashed directly to you and within the configured radius can follow your `/tp`, ender pearl, and chorus fruit teleports.
- **Intelligent safety checks** — the mod automatically detects unsafe destinations (lava, fire, void, suffocation hazards) and finds a nearby safe location within a 5-block radius. If no safe spot is found, the teleport is cancelled to prevent mob death.
- **Fall damage protection** — teleported entities receive Damage Resistance V and Slow Falling for a configurable duration to survive the landing.
- **Tame ownership check** — tamed animals (dogs, cats, horses, etc.) only teleport if they are owned by the teleporting player. Animals tamed by other players are left behind.
- **Sitting entities supported** — sitting tamed animals teleport as long as they are leashed to and owned by the teleporting player.
- **Cross-dimension support** — entities can optionally follow you across dimensions (Overworld ↔ Nether ↔ End). Enabled by default; toggleable in config.
- **Optional LuckPerms integration** — enable permission nodes for general use, cross-dimension teleports, ender pearls, and chorus fruit when LuckPerms is installed.
- **Optional Open Parties and Claims integration** — when OPAC is installed, leashed mobs respect claim access plus OPAC's teleport, ender pearl, and chorus fruit protection rules.
- **World border aware** — if the destination is outside the world border, leashed entities are not teleported.
- **Configurable blacklist** — define entity types that should never teleport, even if leashed to you.
- **Optional Mod Menu integration** — when Mod Menu and Cloth Config are present on the client, you can edit the same config through an in-game screen.
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

A default config is created automatically on first launch, with inline comments that explain each option.

If Mod Menu is installed on the client, Leashed Teleport exposes a config screen there too. Cloth Config is optional: without it, the mod still works normally, but no config GUI is shown.

```jsonc
{
  "leash_radius": 10.0, // Maximum distance in blocks from the player. Mobs further away are left behind.
  "useLuckPerms": false, // When true, Leashed Teleport checks LuckPerms permission nodes if LuckPerms is installed.
  "cross_dimension_teleport": true, // Allow leashed mobs to follow you between dimensions such as the Overworld, Nether, and End.
  "ender_pearl_teleport": true, // Allow ender pearls to teleport your leashed mobs with you. Disable for vanilla pearl behaviour.
  "chorus_fruit_teleport": true, // Allow chorus fruit to teleport your leashed mobs with you. Disable for vanilla chorus fruit behaviour.
  "respectOpenPartiesAndClaims": true, // Respect Open Parties and Claims protections when OPAC is installed.
  "damage_resistance_duration_ticks": 100, // Duration of Damage Resistance V and Slow Falling after teleport. 20 ticks = 1 second.
  "entity_blacklist": [] // Entity type IDs that should never teleport, even when leashed to you.
}
```

| Field                              | Type    | Default | Description                                                                                   |
|------------------------------------|---------|---------|-----------------------------------------------------------------------------------------------|
| `leash_radius`                     | double  | `10.0`  | Maximum distance in blocks from the player. Mobs further away are not teleported.            |
| `useLuckPerms`                     | boolean | `false` | When `true`, Leashed Teleport checks LuckPerms permission nodes if LuckPerms is installed.   |
| `cross_dimension_teleport`         | boolean | `true`  | Allow mobs to follow across dimensions. Set to `false` to restrict to the current dimension. |
| `ender_pearl_teleport`             | boolean | `true`  | Allow leashed mobs to follow ender pearl teleports. Set to `false` for vanilla pearl behaviour. |
| `chorus_fruit_teleport`            | boolean | `true`  | Allow leashed mobs to follow chorus fruit teleports. Set to `false` for vanilla chorus fruit behaviour. |
| `respectOpenPartiesAndClaims`      | boolean | `true`  | Respect OPAC claim protections when Open Parties and Claims is installed. |
| `damage_resistance_duration_ticks` | int     | `100`   | Duration of Damage Resistance V + Slow Falling after teleport (20 ticks = 1 second).         |
| `entity_blacklist`                 | list    | `[]`    | Entity type IDs that will never teleport. See example below.                                  |

### Blacklist Example

Use the blacklist to exclude specific mobs — for example animals that are too large or too dangerous to bring along:

```json
{
  "leash_radius": 15.0,
  "useLuckPerms": true,
  "cross_dimension_teleport": true,
  "ender_pearl_teleport": true,
  "chorus_fruit_teleport": true,
  "respectOpenPartiesAndClaims": true,
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
| `leashedteleport.enderpearlteleport` | Allow a player to bring leashed mobs along with ender pearl teleports |
| `leashedteleport.chorusfruitteleport` | Allow a player to bring leashed mobs along with chorus fruit teleports |

### Example groups

```yaml
group.default:
  permissions:
    - leashedteleport.use

group.vip:
  permissions:
    - leashedteleport.use
    - leashedteleport.crossdimensionteleport
    - leashedteleport.enderpearlteleport
    - leashedteleport.chorusfruitteleport
```

### LuckPerms quick start

```bash
/lp group default permission set leashedteleport.use true
/lp group default permission set leashedteleport.crossdimensionteleport false
/lp group default permission set leashedteleport.enderpearlteleport false
/lp group default permission set leashedteleport.chorusfruitteleport false

/lp group vip permission set leashedteleport.use true
/lp group vip permission set leashedteleport.crossdimensionteleport true
/lp group vip permission set leashedteleport.enderpearlteleport true
/lp group vip permission set leashedteleport.chorusfruitteleport true
```

Official LuckPerms docs:

- [LuckPerms Wiki](https://luckperms.net/wiki/Home)
- [LuckPerms Command Usage](https://luckperms.net/wiki/Command-Usage)

## 🛡️ Open Parties and Claims

Open Parties and Claims is optional. Leashed Teleport does not depend on it directly, but when OPAC is installed and `respectOpenPartiesAndClaims` is `true`, leashed mobs will not be carried into claims that block the matching action.

- `/tp` and other general player teleports require normal claim access.
- Ender pearl mob-follow checks OPAC's Ender Pearls entity barrier group when that dynamic option exists.
- Chorus fruit mob-follow respects OPAC's chorus fruit claim exception/protection option.

Set `respectOpenPartiesAndClaims` to `false` if you want Leashed Teleport to ignore OPAC claim rules.

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
- Supported player teleport types are `/tp`, ender pearls, and chorus fruit. Portals do not trigger leash teleportation.
- For cross-dimension teleports, the leash is re-attached as soon as the entity is loaded in the new dimension.
- Protection effects are applied silently.
- The safety system checks for hazards like lava, fire, void, and suffocation. If the exact teleport destination is unsafe, the mod searches within a 5-block radius for a safe alternative. This prevents accidental mob deaths from teleporting into dangerous locations.
- The mod must be installed server-side. Players do not need to install the mod on their client. This mod does work in single-player.
- Mod Menu and Cloth Config are purely optional client-side extras. A dedicated server does not need them.
- `/leashedteleport info` is operator-only and shows whether LuckPerms is `disabled`, `configured, but mod not installed`, or `active`, plus whether OPAC checks are enabled.

---

## 📦 Installation

| Platform   | Link |
|------------|------|
| GitHub     | [Releases](https://github.com/SwordfishBE/LeashedTeleport/releases) |
| Modrinth   | [Leashed Teleport](https://modrinth.com/mod/leashed-teleport) |
| CurseForge | [Leashed Teleport](https://www.curseforge.com/minecraft/mc-mods/leashed-teleport) |

1. Download the latest JAR from your preferred platform above.
2. Place the JAR in your server's `mods/` folder.
3. Make sure [Fabric API](https://modrinth.com/mod/fabric-api) is also installed.
4. Optional for single-player or client config GUI: also install [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config).
5. Start Minecraft — the config file will be created automatically.

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
