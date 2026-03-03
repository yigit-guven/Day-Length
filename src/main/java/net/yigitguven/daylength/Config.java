package net.yigitguven.daylength;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("daylength");
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("daylength.properties");

    public static int customDayLength = 20;
    public static boolean realTimeSync = false;
    public static boolean useServerTime = true;
    public static int manualUtcOffset = 0;
    public static boolean smoothTimeTransition = true;
    public static int smoothTransitionDuration = 10;

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save(); // Create with defaults
            return;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE.toFile())) {
            props.load(in);
            
            customDayLength = parseInt(props, "customDayLength", customDayLength);
            realTimeSync = parseBoolean(props, "realTimeSync", realTimeSync);
            useServerTime = parseBoolean(props, "useServerTime", useServerTime);
            manualUtcOffset = parseInt(props, "manualUtcOffset", manualUtcOffset);
            smoothTimeTransition = parseBoolean(props, "smoothTimeTransition", smoothTimeTransition);
            smoothTransitionDuration = parseInt(props, "smoothTransitionDuration", smoothTransitionDuration);
            
        } catch (IOException e) {
            LOGGER.error("Failed to load configs", e);
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("customDayLength", String.valueOf(customDayLength));
        props.setProperty("realTimeSync", String.valueOf(realTimeSync));
        props.setProperty("useServerTime", String.valueOf(useServerTime));
        props.setProperty("manualUtcOffset", String.valueOf(manualUtcOffset));
        props.setProperty("smoothTimeTransition", String.valueOf(smoothTimeTransition));
        props.setProperty("smoothTransitionDuration", String.valueOf(smoothTransitionDuration));

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE.toFile())) {
            props.store(out, "Day Length Mod Configuration\n" +
                    "customDayLength: The length of a full day-night cycle in minutes. (Vanilla is 20). Set to 0 to freeze time.\n" +
                    "realTimeSync: Sync the in-game time with real-world time. Overrides customDayLength.\n" +
                    "useServerTime: Use UTC time for real-time sync. If false, uses the manualUtcOffset.\n" +
                    "manualUtcOffset: Manual UTC offset in hours. Only used if useServerTime is false.\n" +
                    "smoothTimeTransition: Gradually transition time when real-time sync is enabled or offset changes.\n" +
                    "smoothTransitionDuration: Duration of the smooth transition in seconds.");
        } catch (IOException e) {
            LOGGER.error("Failed to save configs", e);
        }
    }

    private static int parseInt(Properties props, String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }
}
