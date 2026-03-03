package net.yigitguven.daylength;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class DayLength implements ModInitializer {
	public static final String MOD_ID = "day-length";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final long VANILLA_DAY_LENGTH = 24000L;
	private static final Map<ServerLevel, Double> timeAccumulator = new HashMap<>();

	private static class TransitionState {
		long startTime;
		long startTick;
		long targetTick;
		boolean active;
	}
	private static final TransitionState transitionState = new TransitionState();

	@Override
	public void onInitialize() {
		Config.load();
		LOGGER.info("Day Length mod initialized!");

		ServerTickEvents.END_SERVER_TICK.register(DayLength::onServerTick);
	}

	private static void onServerTick(MinecraftServer server) {
		boolean realTimeSync = Config.realTimeSync;
		int customLength = Config.customDayLength;
		boolean isModActive = realTimeSync || customLength != 20;

		for (ServerLevel level : server.getAllLevels()) {
			if (!level.dimensionType().natural()) continue;

			// Handle sleep behavior
			if (level.players().stream().anyMatch(net.minecraft.world.entity.player.Player::isSleeping)) {
				if (level.players().stream().allMatch(net.minecraft.world.entity.player.Player::isSleeping) && !realTimeSync) {
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

	private static long calculateNextTime(ServerLevel level, MinecraftServer server) {
		if (Config.realTimeSync) {
			return calculateRealTime(level, server);
		}

		int customDayLength = Config.customDayLength;
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
		ZonedDateTime now = getCurrentTime();
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

		if (Config.smoothTimeTransition) {
			if (transitionState.active) {
				long currentMillis = System.currentTimeMillis();
				long transitionDuration = Config.smoothTransitionDuration * 1000L;

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

	private static ZonedDateTime getCurrentTime() {
		if (Config.useServerTime) {
			return ZonedDateTime.now(ZoneId.of("UTC"));
		} else {
			int utcOffset = Config.manualUtcOffset;
			try {
				String offsetId = String.format("%+03d:00", utcOffset);
				return ZonedDateTime.now(ZoneId.of(offsetId));
			} catch (Exception e) {
				return ZonedDateTime.now(ZoneId.of("UTC"));
			}
		}
	}
}