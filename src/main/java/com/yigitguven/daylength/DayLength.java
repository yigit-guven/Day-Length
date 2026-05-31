package com.yigitguven.daylength;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.File;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Mod(DayLength.MODID)
public class DayLength {
    public static final String MODID = "daylength";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long VANILLA_DAY_LENGTH = 24000L;
    private static final Map<ServerLevel, Double> timeAccumulator = new HashMap<>();
    private static final Map<ServerLevel, Long> sleepSkipMap = new HashMap<>();

    private static class TransitionState {
        long startTime;
        long startTick;
        long targetTick;
        boolean active;
    }
    private static final TransitionState transitionState = new TransitionState();

    public DayLength(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        context.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        
        // Register the config screen factory so the "Config" button appears in the mod list
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, 
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, lastScreen) -> {
                File configFile = FMLPaths.CONFIGDIR.get().resolve(MODID + "-common.toml").toFile();
                if (configFile.exists()) {
                    Util.getPlatform().openFile(configFile);
                }
                return lastScreen;
            }));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Day Length mod version 3.0.0 initialized!");
    }

    @Mod.EventBusSubscriber(modid = DayLength.MODID)
    public static class TimeTickHandler {
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;

            // Handle vanilla daylight cycle gamerule
            // We want to control it ourselves most of the time
            boolean realTimeSync = Config.COMMON.realTimeSync.get();
            int customLength = Config.COMMON.customDayLength.get();
            boolean isModActive = realTimeSync || customLength != 20;

            for (ServerLevel level : server.getAllLevels()) {
                if (!level.dimensionType().natural()) continue;

                // If we've recently allowed vanilla to skip to morning due to sleeping,
                // avoid overriding the time until the morning tick has been reached.
                Long skipUntil = sleepSkipMap.get(level);
                if (skipUntil != null) {
                    if (level.getDayTime() < skipUntil) {
                        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
                        }
                        continue;
                    } else {
                        sleepSkipMap.remove(level);
                    }
                }

                // Handle sleep behavior
                // If enough players are sleeping (according to config) and we are NOT synced with real time,
                // let vanilla handle the jump to morning by enabling doDaylightCycle.
                long sleepingCount = level.players().stream().filter(net.minecraft.world.entity.player.Player::isSleeping).count();
                long totalCount = level.players().stream().filter(p -> !p.isSpectator() && p.isAlive()).count();
                Integer vanillaPercent = tryReadVanillaPlayersSleepingPercentage(level);
                int requiredPercent = vanillaPercent != null ? vanillaPercent : 100;

                if (sleepingCount > 0 && totalCount > 0 && !realTimeSync) {
                    int percent = (int)((sleepingCount * 100L) / totalCount);
                    if (percent >= requiredPercent) {
                        // Allow vanilla to handle the skip-to-morning. Remember the next morning
                        // tick so we don't immediately override it on the following server tick.
                        long currentDay = level.getDayTime() / VANILLA_DAY_LENGTH;
                        long nextMorning = (currentDay + 1) * VANILLA_DAY_LENGTH;
                        sleepSkipMap.put(level, nextMorning + 1L);

                        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
                        }
                        continue;
                    }
                }

                if (isModActive) {
                    // Disable vanilla for this level while we are ticking
                    if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    }
                    
                    long currentTime = level.getDayTime();
                    long nextTime = calculateNextTime(level, server);

                    // To avoid cloud/sun jitter, we only set the time if it actually changed
                    if (nextTime != currentTime) {
                        level.setDayTime(nextTime);
                    }
                } else {
                    // Ensure vanilla is enabled if mod is not actively overriding
                    if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
                    }
                }
            }
        }

        private static Integer tryReadVanillaPlayersSleepingPercentage(ServerLevel level) {
            try {
                // Search for a static GameRules field that looks like the players-sleeping-percentage rule
                for (java.lang.reflect.Field f : GameRules.class.getFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    String name = f.getName().toUpperCase();
                    if (!name.contains("SLEEP") || !name.contains("PLAYER")) continue;

                    Object key = f.get(null);
                    if (key == null) continue;

                    // Try GameRules.getRule(key)
                    try {
                        java.lang.reflect.Method getRule = GameRules.class.getMethod("getRule", key.getClass());
                        Object rule = getRule.invoke(level.getGameRules(), key);
                        if (rule == null) continue;

                        // Attempt to find a zero-arg getter that returns a number
                        for (java.lang.reflect.Method m : rule.getClass().getMethods()) {
                            if (m.getParameterCount() != 0) continue;
                            String mname = m.getName().toLowerCase();
                            if (!(mname.equals("get") || mname.equals("getvalue") || mname.equals("getint") || mname.equals("getasint"))) continue;
                            Object val = m.invoke(rule);
                            if (val instanceof Number) return ((Number) val).intValue();
                        }
                    } catch (NoSuchMethodException ignored) {
                        // try alternative: getRule to return a registered rule value via other APIs
                    }
                }
            } catch (Throwable t) {
                // ignore and fallback
            }
            return null;
        }

        private static long calculateNextTime(ServerLevel level, MinecraftServer server) {
            if (Config.COMMON.realTimeSync.get()) {
                return calculateRealTime(level, server);
            }

            int customDayLength = Config.COMMON.customDayLength.get();
            if (customDayLength <= 0) {
                return level.getDayTime(); // Frozen
            }

            // Custom day length progression
            // speedFactor = (vanilla_duration / custom_duration)
            // Vanilla is 20 minutes.
            double speedFactor = 20.0 / customDayLength; 
            double accumulated = timeAccumulator.getOrDefault(level, 0.0);
            accumulated += speedFactor;

            long ticksToAdd = (long) accumulated;
            accumulated -= ticksToAdd;

            timeAccumulator.put(level, accumulated);
            return level.getDayTime() + ticksToAdd;
        }

        private static long calculateRealTime(ServerLevel level, MinecraftServer server) {
            Config.Common config = Config.COMMON;

            ZonedDateTime now = getCurrentTime(config);
            LocalTime localTime = now.toLocalTime();
            int totalSeconds = localTime.toSecondOfDay();
            
            // Map 0-86400 seconds to 0-24000 ticks
            // In Minecraft, 0 is 6:00 AM. In real time, 0 is midnight.
            // Offset by 18000 ticks (6 hours) to align midnight.
            long targetTick = (long)((totalSeconds / 86400.0) * VANILLA_DAY_LENGTH);
            targetTick = (targetTick + 18000) % VANILLA_DAY_LENGTH;
            
            // Get current day count
            long currentDay = level.getDayTime() / VANILLA_DAY_LENGTH;
            targetTick += currentDay * VANILLA_DAY_LENGTH;

            if (config.smoothTimeTransition.get()) {
                if (transitionState.active) {
                    long currentMillis = System.currentTimeMillis();
                    long transitionDuration = config.smoothTransitionDuration.get() * 1000L;

                    if (currentMillis < transitionState.startTime + transitionDuration) {
                        double factor = (double)(currentMillis - transitionState.startTime) / transitionDuration;
                        // Smoothstep interpolation
                        factor = factor * factor * (3 - 2 * factor);
                        return (long)(transitionState.startTick + (transitionState.targetTick - transitionState.startTick) * factor);
                    } else {
                        transitionState.active = false;
                    }
                }

                // Trigger transition if difference is significant (> 5 seconds / 100 ticks)
                if (!transitionState.active && Math.abs(targetTick - level.getDayTime()) > 100) {
                    transitionState.startTime = System.currentTimeMillis();
                    transitionState.startTick = level.getDayTime();
                    transitionState.targetTick = targetTick;
                    transitionState.active = true;
                }
            }

            return transitionState.active ? level.getDayTime() : targetTick;
        }

        private static ZonedDateTime getCurrentTime(Config.Common config) {
            if (config.useServerTime.get()) {
                return ZonedDateTime.now(ZoneId.of("UTC"));
            } else {
                int utcOffset = config.manualUtcOffset.get();
                try {
                    String offsetId = String.format("%+03d:00", utcOffset);
                    return ZonedDateTime.now(ZoneId.of(offsetId));
                } catch (Exception e) {
                    return ZonedDateTime.now(ZoneId.of("UTC"));
                }
            }
        }
    }
}
