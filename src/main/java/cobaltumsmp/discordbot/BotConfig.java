package cobaltumsmp.discordbot;

import java.util.function.Function;

/**
 * Global config for the bot.
 */
public class BotConfig {
    /**
     * The bot prefix.
     */
    public static final String PREFIX = System.getenv("BOT_PREFIX");
    /**
     * The ID of the main guild.
     */
    public static final long GUILD_ID_MAIN;
    /**
     * The ID of the channel to broadcast to.
     *
     * @see cobaltumsmp.discordbot.command.BroadcastCommand
     */
    public static final long CHANNEL_ID_BROADCAST;
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#DEV Developer} role.
     */
    public static final long ROLE_ID_DEV;
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#STAFF Staff} role.
     */
    public static final long ROLE_ID_STAFF;
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#MOD Moderator} role.
     */
    public static final long ROLE_ID_MOD;
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#ADMIN Administrator} role.
     */
    public static final long ROLE_ID_ADMIN;
    /**
     * The ID of the {@linkplain cobaltumsmp.discordbot.Roles#OWNER Owner} role.
     */
    public static final long ROLE_ID_OWNER;

    static {
        Function<String, Long> idProvider = s -> {
            long id;
            try {
                id = Long.parseLong(System.getenv(s));
            } catch (NumberFormatException e) {
                id = 0L;
            }

            return id;
        };

        GUILD_ID_MAIN = idProvider.apply("GUILD_ID_MAIN");
        CHANNEL_ID_BROADCAST = idProvider.apply("CHANNEL_ID_BROADCAST");
        ROLE_ID_DEV = idProvider.apply("ROLE_ID_DEV");
        ROLE_ID_STAFF = idProvider.apply("ROLE_ID_STAFF");
        ROLE_ID_MOD = idProvider.apply("ROLE_ID_MOD");
        ROLE_ID_ADMIN = idProvider.apply("ROLE_ID_ADMIN");
        ROLE_ID_OWNER = idProvider.apply("ROLE_ID_OWNER");
    }
}
