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

public class ConfigHelper {
    private static final Properties config = new Properties();

    public static void loadConfigFile(File file) throws IOException {
        config.load(new FileReader(file));
    }

    @Nonnull
    public static ConfigValue get(String name, @Nonnull String key) {
        return ConfigValue.of(name == null ? "" : name, (String) config.get(key));
    }

    public static void doForValue(@Nonnull ConfigValue configValue, Consumer<String> consumer) {
        if (configValue.isMultiple()) {
            //noinspection ConstantConditions
            configValue.getAsMultiple().forEach(consumer);
        } else {
            consumer.accept(configValue.getAsSingle());
        }
    }

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
