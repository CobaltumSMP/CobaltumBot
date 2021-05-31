package cobaltumsmp.discordbot;

import cobaltumsmp.discordbot.config.ConfigHelper;
import cobaltumsmp.discordbot.i18n.I18nUtil;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;

/**
 * Utility methods.
 */
public class Util {
    // Time units in seconds
    private static final long MINUTE = 60;
    private static final long HOUR = MINUTE * 60;
    private static final long DAY = HOUR * 24;
    private static final long MONTH = DAY * 30;
    private static final long YEAR = DAY * 365;

    public static boolean isSystemEnvEmpty(String name) {
        return isMapValueEmpty(System.getenv(), name);
    }

    public static boolean isMapValueEmpty(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null || value.trim().isEmpty();
    }

    /**
     * Checks if the {@linkplain Message#getUserAuthor() message user author}
     * {@linkplain java.util.Optional#isEmpty() is empty}.
     */
    public static boolean isMessageUserAuthorEmpty(@Nonnull Message message) {
        if (message.getUserAuthor().isEmpty()) {
            Main.LOGGER.error(new IllegalStateException(
                    "Message#getUserAuthor was empty"));
            return true;
        }
        return false;
    }

    /**
     * Checks if {@link org.javacord.api.DiscordApi#getChannelById}
     * {@linkplain java.util.Optional#isEmpty() is empty}.
     */
    public static boolean isChannelByIdEmpty(Optional<Channel> channel) {
        if (channel.isEmpty()) {
            Main.LOGGER.error(new IllegalStateException("DiscordApi#getChannelById was empty"));
            return true;
        }
        return false;
    }

    /**
     * Checks if {@link Channel#asTextChannel()}
     * {@linkplain java.util.Optional#isEmpty() is empty}.
     */
    public static boolean isTextChannelEmpty(@Nonnull Channel channel) {
        if (channel.asTextChannel().isEmpty()) {
            Main.LOGGER.error(new IllegalStateException("Channel#asTextChannel was empty"));
            return true;
        }
        return false;
    }

    /**
     * Sends "There was an unexpected error." to the channel of the provided message.
     */
    public static void unexpectedErrorMessageResponse(@Nonnull Message message) {
        unexpectedErrorMessage(message.getChannel());
    }

    /**
     * Sends "There was an unexpected error." to the provided channel.
     */
    public static void unexpectedErrorMessage(@Nonnull TextChannel channel) {
        channel.sendMessage(I18nUtil.key("unexpected_error"));
    }

    /**
     * Write an exception's stack trace to a String.
     *
     * @param e the exception
     * @return the stack trace as printed with {@link Exception#printStackTrace()}
     */
    public static String getFullStackTrace(Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /**
     * Parse a human readable string like {@code "15m 2h"} to seconds.
     * No suffix for a single number is translated to seconds.
     *
     * @param duration the duration string
     * @return duration in seconds
     * @throws NumberFormatException if the provided duration is invalid
     */
    public static long parseHumanReadableDuration(String duration) throws NumberFormatException {
        duration = duration.toLowerCase().replaceAll(" +", "");

        if (duration.isEmpty()) {
            return 0;
        }

        try {
            return Long.parseLong(duration);
        } catch (NumberFormatException e) {
            long seconds = 0;
            String[] durationByUnits = duration.replaceAll("([a-z]+)", "$1/").split("/");
            for (String unitDuration : durationByUnits) {
                // Remove the numbers
                String unit = unitDuration.replaceAll("([^a-z])", "");
                // Remove the letters
                String time = unitDuration.replaceAll("([a-z])", "");

                switch (unit) {
                    case "y":
                        seconds += Long.parseLong(time) * YEAR;
                        break;
                    case "mo":
                        seconds += Long.parseLong(time) * MONTH;
                        break;
                    case "d":
                        seconds += Long.parseLong(time) * DAY;
                        break;
                    case "h":
                        seconds += Long.parseLong(time) * HOUR;
                        break;
                    case "m":
                        // 60 seconds/minute
                        seconds += Long.parseLong(time) * MINUTE;
                        break;
                    case "s":
                    default:
                        seconds += Long.parseLong(time);
                        break;
                }
            }

            return seconds;
        }
    }

    /**
     * Translate a duration to a human readable String.
     *
     * @param duration the duration in seconds
     * @return the human readable String
     */
    public static String durationToHumanReadable(long duration) {
        if (duration <= 0) {
            return I18nUtil.formatKey("time_unit.seconds", 0);
        } else {
            StringBuilder result = new StringBuilder();
            long tmp = duration / YEAR;
            if (tmp > 0) {
                duration -= tmp * YEAR;
                result.append(I18nUtil.formatKey("time_unit.years", tmp))
                        .append(duration >= MONTH ? ", " : "");
            }

            tmp = duration / MONTH;
            if (tmp > 0) {
                duration -= tmp * MONTH;
                result.append(I18nUtil.formatKey("time_unit.months", tmp))
                        .append(duration >= DAY ? ", " : "");
            }

            tmp = duration / DAY;
            if (tmp > 0) {
                duration -= tmp * DAY;
                result.append(I18nUtil.formatKey("time_unit.days", tmp))
                        .append(duration >= HOUR ? ", " : "");
            }

            tmp = duration / HOUR;
            if (tmp > 0) {
                duration -= tmp * HOUR;
                result.append(I18nUtil.formatKey("time_unit.days", tmp))
                        .append(duration >= MINUTE ? ", " : "");
            }

            tmp = duration / MINUTE;
            if (tmp > 0) {
                duration -= tmp * MINUTE;
                result.append(I18nUtil.formatKey("time_unit.minutes", tmp))
                        .append(duration > 0 ? ", " : "");
            }

            if (duration > 0) {
                result.append(I18nUtil.formatKey("time_unit.seconds", tmp));
            }

            return result.toString();
        }
    }

    /**
     * Load properties from a {@code .env} file in the directory from where the bot was launched.
     *
     * @throws IOException if an error occurs during the file reading
     */
    public static void loadDotEnv() throws IOException {
        File dotEnvFile = new File(".env");
        if (dotEnvFile.exists() && !dotEnvFile.isDirectory()) {
            ConfigHelper.loadConfigFile(dotEnvFile);
        }
    }
}
