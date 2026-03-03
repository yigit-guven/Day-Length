package net.yigitguven.daylength.integration;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.yigitguven.daylength.Config;

public class DayLengthConfigScreen {

    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Day Length Configuration"));

        builder.setSavingRunnable(() -> {
            Config.save();
            // In a singleplayer world, settings are applied on the next tick
        });

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General Options"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        general.addEntry(entryBuilder.startIntField(Component.literal("Custom Day Length (Minutes)"), Config.customDayLength)
                .setDefaultValue(20)
                .setTooltip(Component.literal("The length of a full day-night cycle in minutes. (Vanilla is 20). Set to 0 to freeze time."))
                .setSaveConsumer(newValue -> Config.customDayLength = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Real Time Sync"), Config.realTimeSync)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Sync the in-game time with real-world time. Overrides customDayLength."))
                .setSaveConsumer(newValue -> Config.realTimeSync = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Use Server/Local Time (UTC)"), Config.useServerTime)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Use local system UTC time for real-time sync. If false, uses the Manual UTC Offset below."))
                .setSaveConsumer(newValue -> Config.useServerTime = newValue)
                .build());

        general.addEntry(entryBuilder.startIntField(Component.literal("Manual UTC Offset"), Config.manualUtcOffset)
                .setDefaultValue(0)
                .setMin(-12)
                .setMax(14)
                .setTooltip(Component.literal("Manual UTC offset in hours. Only used if 'Use Server Time' is false."))
                .setSaveConsumer(newValue -> Config.manualUtcOffset = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Smooth Time Transition"), Config.smoothTimeTransition)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Gradually transition time when real-time sync is enabled or offset changes."))
                .setSaveConsumer(newValue -> Config.smoothTimeTransition = newValue)
                .build());

        general.addEntry(entryBuilder.startIntSlider(Component.literal("Smooth Transition Duration (Seconds)"), Config.smoothTransitionDuration, 1, 60)
                .setDefaultValue(10)
                .setTooltip(Component.literal("Duration of the smooth transition in seconds."))
                .setSaveConsumer(newValue -> Config.smoothTransitionDuration = newValue)
                .build());

        return builder.build();
    }
}
