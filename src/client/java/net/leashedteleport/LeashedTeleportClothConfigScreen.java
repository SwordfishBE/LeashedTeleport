package net.leashedteleport;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class LeashedTeleportClothConfigScreen {
    private LeashedTeleportClothConfigScreen() {
    }

    static Screen create(Screen parent) {
        LeashedTeleportConfig config = LeashedTeleportMod.loadConfigForEditing();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Leashed Teleport Config"))
                .setSavingRunnable(() -> LeashedTeleportMod.applyEditedConfig(config));

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        ConfigCategory compatibility = builder.getOrCreateCategory(Component.literal("Compatibility"));
        ConfigCategory filters = builder.getOrCreateCategory(Component.literal("Filters"));

        general.addEntry(entries.startDoubleField(Component.literal("Leash Radius"), config.leash_radius)
                .setDefaultValue(10.0D)
                .setMin(0.0D)
                .setTooltip(Component.literal("Maximum distance in blocks from the player before a leashed mob is left behind."))
                .setSaveConsumer(value -> config.leash_radius = value)
                .build());

        general.addEntry(entries.startIntField(Component.literal("Damage Resistance Duration"), config.damage_resistance_duration_ticks)
                .setDefaultValue(100)
                .setMin(1)
                .setTooltip(Component.literal("Duration in ticks of Resistance V and Slow Falling after teleport."))
                .setSaveConsumer(value -> config.damage_resistance_duration_ticks = value)
                .build());

        compatibility.addEntry(entries.startBooleanToggle(Component.literal("Cross-Dimension Teleport"), config.cross_dimension_teleport)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Allow leashed mobs to follow between dimensions such as the Nether and End."))
                .setSaveConsumer(value -> config.cross_dimension_teleport = value)
                .build());

        compatibility.addEntry(entries.startBooleanToggle(Component.literal("Ender Pearl Teleport"), config.ender_pearl_teleport)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Allow ender pearls to teleport your leashed mobs with you. Disable this for vanilla pearl behaviour."))
                .setSaveConsumer(value -> config.ender_pearl_teleport = value)
                .build());

        compatibility.addEntry(entries.startBooleanToggle(Component.literal("Chorus Fruit Teleport"), config.chorus_fruit_teleport)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Allow chorus fruit to teleport your leashed mobs with you. Disable this for vanilla chorus fruit behaviour."))
                .setSaveConsumer(value -> config.chorus_fruit_teleport = value)
                .build());

        compatibility.addEntry(entries.startBooleanToggle(Component.literal("Use LuckPerms"), config.useLuckPerms)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Enable permission node checks when the LuckPerms mod is installed."))
                .setSaveConsumer(value -> config.useLuckPerms = value)
                .build());

        filters.addEntry(entries.startStrList(Component.literal("Entity Blacklist"), config.getBlacklist())
                .setDefaultValue(java.util.List.of())
                .setTooltip(Component.literal("Entity type IDs that should never teleport, for example minecraft:horse."))
                .setSaveConsumer(value -> config.entity_blacklist = new java.util.ArrayList<>(value))
                .build());

        return builder.build();
    }
}
