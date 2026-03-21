package net.leashedteleport.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.leashedteleport.LeashedTeleportMod;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public class LeashedTeleportCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("leashedteleport")

                    .then(Commands.literal("info")
                            .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .executes(ctx -> {
                                LeashedTeleportConfig cfg = LeashedTeleportConfig.get();
                                String blacklistInfo = cfg.getBlacklist().isEmpty()
                                        ? "none"
                                        : String.join(", ", cfg.getBlacklist());
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§6=== Leashed Teleport v" + LeashedTeleportMod.VERSION + " ===\n" +
                                        "§eLeash radius:                §f" + cfg.getLeashRadius() + " blocks\n" +
                                        "§eCross-dimension teleport:    §f" + cfg.isCrossDimensionTeleport() + "\n" +
                                        "§eDamage resistance duration:  §f" + cfg.getDamageResistanceDuration() + " ticks\n" +
                                        "§eBlacklisted entities:        §f" + blacklistInfo
                                ), false);
                                return 1;
                            }))

                    .then(Commands.literal("reload")
                            .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .executes(ctx -> {
                                LeashedTeleportConfig.load();
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
