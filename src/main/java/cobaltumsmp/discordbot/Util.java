package cobaltumsmp.discordbot;

import java.util.Map;

/**
 * Utility methods.
 */
public class Util {
    public static boolean isSystemEnvEmpty(String name) {
        return isMapValueEmpty(System.getenv(), name);
    }

    public static boolean isMapValueEmpty(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null || value.trim().isEmpty();
    }
}
