package cobaltumsmp.discordbot.config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConfigValue {
    protected static ConfigValue of(@Nonnull String name, @Nullable String value) {
        if (value == null) {
            return new ConfigValue(name, "");
        }

        value = value.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            List<String> list = Arrays.asList(value.replaceAll("[\\[\\] ]", "").split(","));
            return new ConfigValue(name, new ArrayList<>(list));
        } else {
            return new ConfigValue(name, value);
        }
    }

    private final String name;
    private ArrayList<String> array;
    private String string;

    private ConfigValue(String name, String value) {
        this.name = name;
        this.string = value;
    }

    private ConfigValue(String name, ArrayList<String> value) {
        this.name = name;
        this.array = value;
    }

    public boolean isSingle() {
        return this.string != null;
    }

    public boolean isMultiple() {
        return this.array != null;
    }

    @Nullable
    public String getAsSingle() {
        return this.string;
    }

    @Nonnull
    public String getAsSingleOrFail() {
        if (!this.isSingle()) {
            throw new IllegalStateException("Config value "
                    + (this.name.isEmpty() ? "" : this.name + " ") + " must be single");
        } else {
            return this.string;
        }
    }

    @Nullable
    public ArrayList<String> getAsMultiple() {
        return this.array;
    }

    @Nonnull
    public ArrayList<String> getAsMultipleOrFail() {
        if (!this.isMultiple()) {
            throw new IllegalStateException("Config value "
                    + (this.name.isEmpty() ? "" : this.name + " ") + " must be multiple");
        } else {
            return this.array;
        }
    }

    @Override
    public String toString() {
        if (this.isSingle()) {
            return "ConfigValue[type=Single"
                    + (this.name.isEmpty() ? "" : ", name=" + this.name)
                    + ", value=\"" + this.getAsSingle() + "\"]";
        } else {
            return "ConfigValue[type=Multiple"
                    + (this.name.isEmpty() ? "" : ", name=" + this.name)
                    + ", values=" + this.getAsMultiple() + "]";
        }
    }
}
