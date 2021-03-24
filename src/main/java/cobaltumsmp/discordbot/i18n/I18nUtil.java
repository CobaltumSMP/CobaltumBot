package cobaltumsmp.discordbot.i18n;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods for i18n.
 */
public class I18nUtil {
    private static final Map<String, TranslatableString> TRANSLATABLE_STRING_POOL = new HashMap<>();

    /**
     * Get a translated string from a key. Avoids multiple instances of the same
     * {@link TranslatableString} by saving each one in a pool and using already created ones, if
     * possible.
     *
     * @param key the translation key
     * @return the translated string
     */
    public static String key(String key) {
        if (!TRANSLATABLE_STRING_POOL.containsKey(key)) {
            TRANSLATABLE_STRING_POOL.put(key, new TranslatableString(key));
        }

        return TRANSLATABLE_STRING_POOL.get(key).toString();
    }

    /**
     * Get a formatted and translated string using the provided key translation (as format string)
     * and args.
     *
     * @param key the key to translate and use as format string
     * @param args see {@link String#format} args
     * @return the formatted and translated string
     */
    public static String formatKey(String key, Object... args) {
        return String.format(key(key), args);
    }
}
