package net.leashedteleport.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.leashedteleport.LeashedTeleportMod;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.permission.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class LeashedTeleportCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("leashedteleport")

                    .then(Commands.literal("info")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(ctx -> {
                                LeashedTeleportConfig cfg = LeashedTeleportConfig.get();
                                String blacklistInfo = cfg.getBlacklist().isEmpty()
                                        ? "none"
                                        : String.join(", ", cfg.getBlacklist());
                                String luckPermsStatus = PermissionManager.isLuckPermsActive()
                                        ? "active"
                                        : (cfg.isUseLuckPerms() ? "configured, but mod not installed" : "disabled");
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§6=== Leashed Teleport v" + LeashedTeleportMod.VERSION + " ===\n" +
                                        "§eLuckPerms:                  §f" + luckPermsStatus + "\n" +
                                        "§eUse LuckPerms:              §f" + cfg.isUseLuckPerms() + "\n" +
                                        "§eLeash radius:                §f" + cfg.getLeashRadius() + " blocks\n" +
                                        "§eCross-dimension teleport:    §f" + cfg.isCrossDimensionTeleport() + "\n" +
                                        "§eUse permission:              §f" + PermissionManager.USE_PERMISSION + "\n" +
                                        "§eCross-dim permission:        §f" + PermissionManager.CROSS_DIMENSION_TELEPORT_PERMISSION + "\n" +
                                        "§eDamage resistance duration:  §f" + cfg.getDamageResistanceDuration() + " ticks\n" +
                                        "§eBlacklisted entities:        §f" + blacklistInfo
                                ), false);
                                return 1;
                            }))

                    .then(Commands.literal("reload")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(ctx -> {
                                LeashedTeleportConfig.load();
                                PermissionManager.refreshState();
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§a[LeashedTeleport] Config reloaded successfully."
                                ), true);
                                LeashedTeleportMod.LOGGER.info("[LeashedTeleport] Config reloaded by {}.",
                                        ctx.getSource().getTextName());
                                return 1;
                            }));

            dispatcher.register(root);
        });
    }
}
