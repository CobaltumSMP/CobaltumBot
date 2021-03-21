package cobaltumsmp.discordbot.logging;

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

import java.util.regex.Pattern;

/**
 * The filter returns the onMatch if the logger of the message matches the regular expression.
 */
@Plugin(name = "RegexLoggerFilter", category = Node.CATEGORY, elementType = Filter.ELEMENT_TYPE)
public class RegexLoggerFilter extends AbstractFilter {
    private final Pattern pattern;

    private RegexLoggerFilter(Pattern pattern, Result onMatch, Result onMismatch) {
        super(onMatch, onMismatch);
        this.pattern = pattern;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return filter(logger.getName());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return filter(logger.getName());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return filter(logger.getName());
    }

    @Override
    public Result filter(LogEvent event) {
        return filter(event.getLoggerName());
    }

    private Result filter(String loggerName) {
        boolean matches = this.pattern.matcher(loggerName).matches();
        return matches ? onMatch : onMismatch;
    }

    /**
     * Creates a Filter that matches a regular expression over the event Logger.
     *
     * @param regex the regular expression to match
     * @param onMatch the action to perform when a match occurs
     * @param onMismatch the action to perform when a mismatch occurs
     * @return the RegexLoggerFilter
     */
    @PluginFactory
    public static RegexLoggerFilter createFilter(
            @PluginAttribute("regex") String regex,
            @PluginAttribute("onMatch") Result onMatch,
            @PluginAttribute("onMismatch") Result onMismatch
    ) {
        if (regex == null) {
            LOGGER.error("A regular expression must be provided for RegexLoggerFilter");
            return null;
        }

        return new RegexLoggerFilter(Pattern.compile(regex), onMatch, onMismatch);
    }
}
