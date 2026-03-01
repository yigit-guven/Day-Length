package com.yigitguven.daylength;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class Common {
        public final ForgeConfigSpec.IntValue customDayLength;
        public final ForgeConfigSpec.BooleanValue realTimeSync;
        public final ForgeConfigSpec.BooleanValue smoothTimeTransition;
        public final ForgeConfigSpec.IntValue smoothTransitionDuration;
        public final ForgeConfigSpec.BooleanValue useServerTime;
        public final ForgeConfigSpec.IntValue manualUtcOffset;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("general");

            customDayLength = builder
                    .comment("The length of a full day-night cycle in minutes. (Vanilla is 20). Set to 0 to freeze time.")
                    .defineInRange("customDayLength", 20, 0, Integer.MAX_VALUE);

            realTimeSync = builder
                    .comment("Sync the in-game time with real-world time. Overrides customDayLength.")
                    .define("realTimeSync", false);

            builder.pop();
            builder.push("real_time_sync");

            useServerTime = builder
                    .comment("Use UTC time for real-time sync. If false, uses the manualUtcOffset.")
                    .define("useServerTime", true);

            manualUtcOffset = builder
                    .comment("Manual UTC offset in hours. Only used if useServerTime is false.")
                    .defineInRange("manualUtcOffset", 0, -12, 14);

            smoothTimeTransition = builder
                    .comment("Gradually transition time when real-time sync is enabled or offset changes.")
                    .define("smoothTimeTransition", true);

            smoothTransitionDuration = builder
                    .comment("Duration of the smooth transition in seconds.")
                    .defineInRange("smoothTransitionDuration", 10, 1, 3600);

            builder.pop();
        }
    }
}