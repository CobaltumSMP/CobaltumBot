package cobaltumsmp.discordbot.config;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A class to help with the bot configuration.
 */
public class ConfigHelper {
    private static final Properties config = new Properties();

    static {
        config.putAll(System.getenv());
    }

    public static void loadConfigFile(File file) throws IOException {
        config.load(new FileReader(file));
    }

    @Nonnull
    public static ConfigValue get(String name, @Nonnull String key) {
        return ConfigValue.of(name == null ? "" : name, (String) config.get(key));
    }

    @Nonnull
    public static ConfigValue get(String name, @Nonnull String key, boolean forceMultiple) {
        return ConfigValue.of(name == null ? "" : name, (String) config.get(key), forceMultiple);
    }

    /**
     * Run a consumer for each of the value(s) of any {@link ConfigValue}.
     *
     * @param configValue the config value to use
     * @param consumer what to do with the value(s)
     */
    public static void doForEachValue(@Nonnull ConfigValue configValue, Consumer<String> consumer) {
        if (configValue.isMultiple()) {
            //noinspection ConstantConditions
            configValue.getAsMultiple().forEach(consumer);
        } else {
            consumer.accept(configValue.getAsSingle());
        }
    }

    /**
     * Map any {@link ConfigValue} value(s) to a list.
     *
     * @param configValue the config value to use
     * @param mapper the function to map the values
     * @param <T> the type of the list
     * @return a list with the mapped values
     */
    public static <T> List<T> mapToList(@Nonnull ConfigValue configValue,
                                     @Nonnull Function<String, T> mapper) {
        if (configValue.isMultiple()) {
            return configValue.getAsMultiple().stream().map(mapper).collect(Collectors.toList());
        } else {
            return new ArrayList<>(
                    Collections.singletonList(mapper.apply(configValue.getAsSingle())));
        }
    }

    public static Long getIdFromConfig(String name, @Nonnull String key) {
        ConfigValue val = get(name, key);
        return Long.parseLong(val.getAsSingleOrFail());
    }

    /**
     * Get multiple ids from the config.
     *
     * @param name the name of the config value to get the value from
     * @param key the key of the config value to get the value from
     * @return a list containing the values as longs
     */
    public static List<Long> getMultipleIdsFromConfig(String name, @Nonnull String key) {
        ConfigValue val = get(name, key);
        if (val.isSingle()) {
            return List.of(Long.parseLong(val.getAsSingleOrFail()));
        } else {
            return val.getAsMultipleOrFail().stream().map(Long::parseLong)
                    .collect(Collectors.toList());
        }
    }
}
