package cobaltumsmp.discordbot.misc;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * The filter returns the onMatch if the provided system property is the provided value or "true" if
 * no value was provided.
 */
@Plugin(name = "RegexPropertyFilter", category = Node.CATEGORY, elementType = Filter.ELEMENT_TYPE)
public class SystemPropertyFilter extends AbstractFilter {
    private final String propertyKey;
    private final String value;

    private SystemPropertyFilter(String propertyKey, String value, Result onMatch,
                                 Result onMismatch) {
        super(onMatch, onMismatch);
        this.propertyKey = propertyKey;
        this.value = value == null ? "true" : value;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return filter();
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return filter();
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return filter();
    }

    @Override
    public Result filter(LogEvent event) {
        return filter();
    }

    private Result filter() {
        String value = System.getProperty(this.propertyKey);
        return value != null && value.equalsIgnoreCase(this.value) ? this.onMatch : this.onMismatch;
    }

    /**
     * Creates a filter that checks a system property.
     *
     * @param propertyKey the property to match
     * @param value the value to match, otherwise "true"
     * @param onMatch the action to perform when a match occurs
     * @param onMismatch the action to perform when a mismatch occurs
     * @return The SystemPropertyFilter
     */
    @PluginFactory
    public static SystemPropertyFilter createFilter(
            @PluginAttribute("propertyKey") String propertyKey,
            @PluginAttribute("value") String value,
            @PluginAttribute("onMatch") Result onMatch,
            @PluginAttribute("onMismatch") Result onMismatch
    ) {
        if (propertyKey == null) {
            LOGGER.error("A property key must be provided for SystemPropertyFilter");
            return null;
        }

        return new SystemPropertyFilter(propertyKey, value, onMatch, onMismatch);
    }
}
