package cobaltumsmp.discordbot;

import cobaltumsmp.discordbot.config.ConfigHelper;

import java.util.List;
import java.util.Locale;

/**
 * Global config for the bot.
 */
public class BotConfig {
    /**
     * The bot prefix.
     */
    public static final String PREFIX = ConfigHelper.get("BotPrefix", "PREFIX").getAsSingleOrFail();
    /**
     * The ID of the main guild.
     */
    public static final long GUILD_ID_MAIN = ConfigHelper.getIdFromConfig("MainGuildId",
            "GUILD_ID_MAIN");
    /**
     * The ID of the channel to broadcast to.
     *
     * @see cobaltumsmp.discordbot.command.BroadcastCommand
     */
    public static final List<Long> CHANNEL_ID_BROADCAST = ConfigHelper.getMultipleIdsFromConfig(
            "BroadcastChannelId(s)", "CHANNEL_ID_BROADCAST");

    public static final long CHANNEL_ID_BOT_MESSAGES = ConfigHelper.getIdFromConfig(
            "BotMessagesChannelId", "CHANNEL_ID_BOT_MSG");
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#DEV Developer} role.
     */
    public static final long ROLE_ID_DEV = ConfigHelper.getIdFromConfig("DevRoleId",
            "ROLE_ID_DEV");
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#STAFF Staff} role.
     */
    public static final long ROLE_ID_STAFF = ConfigHelper.getIdFromConfig("StaffRoleId",
            "ROLE_ID_STAFF");
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#MOD Moderator} role.
     */
    public static final long ROLE_ID_MOD = ConfigHelper.getIdFromConfig("ModRoleId",
            "ROLE_ID_MOD");
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#ADMIN Administrator} role.
     */
    public static final long ROLE_ID_ADMIN = ConfigHelper.getIdFromConfig("AdminRoleId",
            "ROLE_ID_ADMIN");
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#OWNER Owner} role.
     */
    public static final long ROLE_ID_OWNER = ConfigHelper.getIdFromConfig("OwnerRoleId",
            "ROLE_ID_OWNER");

    public static final Locale LOCALE;

    static {
        // Get locale for I18n
        Locale locale = Locale.US;
        String localeCode;
        if (!(localeCode = ConfigHelper.get("Locale", "BOT_LOCALE").getAsSingleOrFail())
                .isEmpty()) {
            try {
                locale = Locale.forLanguageTag(localeCode);
            } catch (NullPointerException e) {
                Main.LOGGER.warn("Invalid locale provided ('BOT_LOCALE')! Defaulting to 'en-US'");
            }
        }

        LOCALE = locale;
    }
}
